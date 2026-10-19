package com.ifielsc.ijob.core.resource.factory.file.client;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Same-directory publication. Rename acknowledgement is required; no delete-and-replace fallback.
 * No-clobber against independent writers requires server support or a shared external lock.
 */
public interface StagedFileClient extends FileClient {
    void renameFile(String from, String to) throws IOException;

    /** Delete only this exact file, never recurse into directories. */
    void deleteTemporaryFile(String path) throws IOException;

    static boolean isTemporaryName(String name) {
        return name != null && name.matches("\\.ijob-transfer-[0-9a-f-]{36}\\.part");
    }

    @Override
    default boolean uploadPublished(
            String path, InputStream input, long length, boolean overwrite, Runnable beforePublish)
            throws IOException {
        if (length < 0) throw new IOException("Upload length is required");
        beforePublish.run();
        if (!overwrite && exists(path)) return false;
        String portable = path.replace('\\', '/');
        int slash = portable.lastIndexOf('/');
        String temp =
                portable.substring(0, slash + 1) + ".ijob-transfer-" + UUID.randomUUID() + ".part";
        Throwable failure = null;
        boolean published = false;
        try {
            var counted =
                    new FilterInputStream(input) {
                        long count;

                        @Override
                        public int read() throws IOException {
                            int value = in.read();
                            if (value >= 0) count++;
                            return value;
                        }

                        @Override
                        public int read(byte[] bytes, int offset, int size) throws IOException {
                            int read = in.read(bytes, offset, size);
                            if (read > 0) count += read;
                            return read;
                        }
                    };
            upload(temp, counted, length);
            if (counted.count != length) throw new IOException("Upload length mismatch");
            beforePublish.run();
            if (!overwrite && exists(path)) return false;
            renameFile(temp, path);
            published = true;
            return true;
        } catch (IOException | RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            if (!published) {
                try {
                    deleteTemporaryFile(temp);
                } catch (IOException | RuntimeException cleanup) {
                    if (failure != null) failure.addSuppressed(cleanup);
                    else throw cleanup;
                }
            }
        }
    }
}
