package com.ifelio.ijob.plugin.etl.extractor.excel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractStreamingExcelReader implements ExcelStreamReader {
    // Identity must differ from an actual empty row emitted by the parser.
    private static final List<String> END = new ArrayList<>();
    private static final long CLOSE_WAIT_MILLIS = 2_000;

    private final BlockingQueue<List<String>> queue;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Exception error;
    private volatile Thread worker;
    private List<String> current;
    private boolean finished;

    protected AbstractStreamingExcelReader(int capacity) {
        queue = new ArrayBlockingQueue<>(capacity);
    }

    protected final void startReading() {
        if (worker != null) {
            throw new IllegalStateException("Excel reader is already started");
        }
        Thread thread = new Thread(this::runParser);
        thread.setName("etl-excel-reader");
        thread.setDaemon(true);
        worker = thread;
        thread.start();
    }

    private void runParser() {
        try {
            parse();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (!closed.get()) {
                error = exception;
            }
        } catch (Exception exception) {
            if (!closed.get()) {
                error = exception;
            }
        } finally {
            while (!closed.get()) {
                try {
                    if (queue.offer(END, 100, TimeUnit.MILLISECONDS)) {
                        break;
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    protected abstract void parse() throws Exception;

    protected final void emit(List<String> row) {
        try {
            while (!closed.get()) {
                if (queue.offer(List.copyOf(row), 100, TimeUnit.MILLISECONDS)) {
                    return;
                }
            }
            throw new IllegalStateException("Excel reader was closed");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Excel row emission interrupted", exception);
        }
    }

    @Override
    public boolean hasNext() {
        if (finished || closed.get()) {
            return false;
        }
        if (current != null) {
            return true;
        }
        try {
            current = queue.take();
            if (current == END) {
                current = null;
                finished = true;
                if (error != null) {
                    throw new IllegalStateException("ETL Excel source read failed", error);
                }
                return false;
            }
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ETL Excel source read interrupted", exception);
        }
    }

    @Override
    public List<String> next() {
        if (!hasNext()) {
            return null;
        }
        List<String> result = current;
        current = null;
        return result;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Thread thread = worker;
        if (thread != null) {
            thread.interrupt();
            if (thread != Thread.currentThread()) {
                try {
                    thread.join(CLOSE_WAIT_MILLIS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        queue.clear();
        queue.offer(END);
    }
}
