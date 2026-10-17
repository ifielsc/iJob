package com.ifelio.ijob.plugin.filetransfer;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Synchronous transfer scope; counts every staging write, including archive headers and rewrites.
 */
public final class StagingBudget implements AutoCloseable {
    private static final ThreadLocal<StagingBudget> CURRENT = new ThreadLocal<>();
    private final StagingBudget previous;
    private long remaining;

    private StagingBudget(long limit) {
        previous = CURRENT.get();
        remaining = limit;
        CURRENT.set(this);
    }

    public static StagingBudget open(long limit) {
        if (limit < 1 || limit > 20L * 1024 * 1024 * 1024)
            throw new IllegalArgumentException("File transfer staging limit is invalid");
        return new StagingBudget(limit);
    }

    private synchronized void reserve(int bytes) throws IOException {
        if (bytes > remaining) throw new IOException("File transfer staging quota exceeded");
        remaining -= bytes;
    }

    public static OutputStream newOutputStream(Path path, OpenOption... options)
            throws IOException {
        StagingBudget budget = CURRENT.get();
        OutputStream output = Files.newOutputStream(path, options);
        if (budget == null) return output;
        return new FilterOutputStream(output) {
            @Override
            public void write(int value) throws IOException {
                budget.reserve(1);
                out.write(value);
            }

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
                budget.reserve(length);
                out.write(bytes, offset, length);
            }
        };
    }

    public static SeekableByteChannel newByteChannel(Path path) throws IOException {
        StagingBudget budget = CURRENT.get();
        SeekableByteChannel channel =
                Files.newByteChannel(
                        path,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE);
        if (budget == null) return channel;
        return new SeekableByteChannel() {
            public int write(ByteBuffer source) throws IOException {
                budget.reserve(source.remaining());
                return channel.write(source);
            }

            public int read(ByteBuffer target) throws IOException {
                return channel.read(target);
            }

            public long position() throws IOException {
                return channel.position();
            }

            public SeekableByteChannel position(long value) throws IOException {
                channel.position(value);
                return this;
            }

            public long size() throws IOException {
                return channel.size();
            }

            public SeekableByteChannel truncate(long value) throws IOException {
                channel.truncate(value);
                return this;
            }

            public boolean isOpen() {
                return channel.isOpen();
            }

            public void close() throws IOException {
                channel.close();
            }
        };
    }

    @Override
    public void close() {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
