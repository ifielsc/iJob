package com.ifelio.ijob.framework.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class GracefulThreadPoolExecutor extends ThreadPoolExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(GracefulThreadPoolExecutor.class);

    private final String poolName;
    private final Duration shutdownTimeout;

    public GracefulThreadPoolExecutor(
            String poolName,
            int corePoolSize,
            int maximumPoolSize,
            Duration keepAlive,
            BlockingQueue<Runnable> workQueue,
            ThreadFactory threadFactory,
            RejectedExecutionHandler rejectedExecutionHandler,
            Duration shutdownTimeout) {
        super(
                corePoolSize,
                maximumPoolSize,
                keepAlive.toMillis(),
                TimeUnit.MILLISECONDS,
                workQueue,
                threadFactory,
                rejectedExecutionHandler);
        this.poolName = poolName;
        this.shutdownTimeout = shutdownTimeout;
    }

    public void shutdownGracefully() {
        shutdown();
        try {
            if (awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return;
            }
            int cancelledTasks = shutdownNow().size();
            LOG.warn(
                    "execution pool forced shutdown, pool={}, cancelledTasks={}",
                    poolName,
                    cancelledTasks);
        } catch (InterruptedException exception) {
            int cancelledTasks = shutdownNow().size();
            Thread.currentThread().interrupt();
            LOG.warn(
                    "execution pool shutdown interrupted, pool={}, cancelledTasks={}",
                    poolName,
                    cancelledTasks);
        }
    }

    @Override
    public void execute(Runnable task) {
        super.execute(com.ifelio.ijob.common.diagnostic.DiagnosticContext.wrap(task));
    }
}
