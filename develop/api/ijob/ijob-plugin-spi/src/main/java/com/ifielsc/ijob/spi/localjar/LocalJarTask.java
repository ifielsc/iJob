package com.ifielsc.ijob.spi.localjar;

/**
 * Stable entry point implemented by a trusted local JAR extension.
 *
 * <p>The extension runs inside the iJob JVM with the process permissions. It is not a security
 * sandbox. Long-running work must call {@link LocalJarContext#checkExecution()} regularly and
 * register blocking resources with {@link LocalJarContext#registerResource(AutoCloseable)}.
 * Extension-owned threads, executors and static state remain the extension's responsibility and
 * must not outlive the execution.
 */
public interface LocalJarTask {
    LocalJarResult run(LocalJarExecution execution, LocalJarContext context) throws Exception;

    /**
     * Requests cooperative cancellation. Implementations must be idempotent, non-blocking and safe
     * to call concurrently with {@link #run}. Registered resources are closed after this hook
     * returns; the hook must not wait indefinitely for {@code run} to finish.
     */
    default void stop(LocalJarExecution execution, LocalJarContext context) throws Exception {}
}
