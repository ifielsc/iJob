package com.ifelio.ijob.core.resource.factory.file.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface FileClient extends AutoCloseable {
    List<FileInfo> list(String path) throws IOException;

    default List<FileInfo> list(String path, int maxEntries) throws IOException {
        List<FileInfo> result = list(path);
        if (result != null && result.size() > maxEntries)
            throw new IOException("Directory entry quota exceeded");
        return result;
    }

    InputStream download(String path) throws IOException;

    void upload(String path, InputStream input) throws IOException;

    default void upload(String path, InputStream input, long contentLength) throws IOException {
        upload(path, input);
    }

    /**
     * Publish using the protocol's native upload semantics unless staged publishing is supported.
     */
    default boolean uploadPublished(
            String path, InputStream input, long length, boolean overwrite, Runnable beforePublish)
            throws IOException {
        beforePublish.run();
        if (!overwrite && exists(path)) return false;
        upload(path, input, length);
        return true;
    }

    void mkdirs(String path) throws IOException;

    boolean exists(String path) throws IOException;

    void delete(String path) throws IOException;

    @Override
    default void close() {}
}
