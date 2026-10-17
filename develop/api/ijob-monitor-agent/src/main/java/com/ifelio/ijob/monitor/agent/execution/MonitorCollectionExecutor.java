package com.ifelio.ijob.monitor.agent.execution;

import com.ifelio.ijob.monitor.agent.config.AgentProperties;

import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MonitorCollectionExecutor {

    private static final int MIN_TIMEOUT_MILLIS = 100;
    private static final int MAX_TIMEOUT_MILLIS = 300_000;
    private static final int MAX_CONCURRENCY = 16;
    private static final int MAX_QUEUE_CAPACITY = 64;

    private final long timeoutMillis;
    private final ThreadPoolExecutor executor;

    public MonitorCollectionExecutor(AgentProperties properties) {
        timeoutMillis =
                requireRange(
                        properties.getCollectTimeout(),
                        MIN_TIMEOUT_MILLIS,
                        MAX_TIMEOUT_MILLIS,
                        "agent collectTimeout is out of range");
        int concurrency =
                requireRange(
                        properties.getCollectConcurrency(),
                        1,
                        MAX_CONCURRENCY,
                        "agent collectConcurrency is out of range");
        int queueCapacity =
                requireRange(
                        properties.getCollectQueueCapacity(),
                        1,
                        MAX_QUEUE_CAPACITY,
                        "agent collectQueueCapacity is out of range");
        executor =
                new ThreadPoolExecutor(
                        concurrency,
                        concurrency,
                        0L,
                        TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(queueCapacity),
                        threadFactory(),
                        new ThreadPoolExecutor.AbortPolicy());
    }

    public <T> T execute(Callable<T> action) {
        Future<T> future;
        try {
            future = executor.submit(action);
        } catch (RejectedExecutionException e) {
            throw new MonitorCollectionBusyException();
        }
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            executor.purge();
            throw new MonitorCollectionTimeoutException();
        } catch (InterruptedException e) {
            future.cancel(true);
            executor.purge();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("monitor collection interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("monitor collection failed", cause);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private ThreadFactory threadFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return action -> {
            Thread thread = new Thread(action, "monitor-collector-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private int requireRange(Integer value, int min, int max, String message) {
        if (value == null || value < min || value > max) {
            throw new IllegalStateException(message);
        }
        return value;
    }
}
