package com.ifelio.ijob.spi.localjar;

public interface LocalJarContext {
    long instanceId();

    String resolve(String text);

    Object resolveObject(Object value);

    Object variable(String name);

    /**
     * Publishes bounded JSON-compatible data. Supported values are null, strings, booleans, finite
     * standard numbers, enums, lists and maps with string keys. Values are copied so extension
     * classes cannot escape the execution ClassLoader.
     */
    void putVariable(String name, Object value);

    void removeVariable(String name);

    /**
     * Throws when stop, timeout, interruption or pause control requires the extension to yield.
     * Call this from every bounded unit of long work.
     */
    void checkExecution();

    /**
     * Registers a resource whose close operation can unblock running work. The close operation must
     * be idempotent and thread-safe.
     */
    void registerResource(AutoCloseable resource);

    void progress(int progress);

    void logInfo(String message);

    void logWarn(String message);

    void logError(String message);
}
