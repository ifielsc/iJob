package com.ifelio.ijob.monitor.agent.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.monitor.agent.config.AgentProperties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class MonitorCollectionExecutorTest {

    private MonitorCollectionExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    void shouldReturnCollectionResult() {
        executor = executor(1_000, 1, 1);

        assertEquals("ok", executor.execute(() -> "ok"));
    }

    @Test
    void shouldCancelCollectionAfterConfiguredTimeout() {
        executor = executor(100, 1, 1);
        CountDownLatch interrupted = new CountDownLatch(1);

        assertThrows(
                MonitorCollectionTimeoutException.class,
                () ->
                        executor.execute(
                                () -> {
                                    try {
                                        Thread.sleep(10_000L);
                                    } catch (InterruptedException e) {
                                        interrupted.countDown();
                                        Thread.currentThread().interrupt();
                                    }
                                    return null;
                                }));

        try {
            assertEquals(true, interrupted.await(1, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    @Test
    void shouldRejectInvalidExecutorConfiguration() {
        AgentProperties properties = properties(99, 1, 1);

        assertThrows(IllegalStateException.class, () -> new MonitorCollectionExecutor(properties));
    }

    private MonitorCollectionExecutor executor(int timeout, int concurrency, int queueCapacity) {
        return new MonitorCollectionExecutor(properties(timeout, concurrency, queueCapacity));
    }

    private AgentProperties properties(int timeout, int concurrency, int queueCapacity) {
        AgentProperties properties = new AgentProperties();
        properties.setCollectTimeout(timeout);
        properties.setCollectConcurrency(concurrency);
        properties.setCollectQueueCapacity(queueCapacity);
        return properties;
    }
}
