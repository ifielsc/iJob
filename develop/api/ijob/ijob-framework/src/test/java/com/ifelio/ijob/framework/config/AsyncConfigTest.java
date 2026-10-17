package com.ifelio.ijob.framework.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.config.AppProperties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class AsyncConfigTest {
    private final List<ExecutorService> executors = new ArrayList<>();
    private ThreadPoolTaskExecutor triggerExecutor;

    @AfterEach
    void shutdownExecutors() {
        executors.forEach(ExecutorService::shutdownNow);
        if (triggerExecutor != null) {
            triggerExecutor.shutdown();
        }
    }

    @Test
    void shouldCreateSeparateBoundedExecutionPools() {
        AppProperties properties = properties();
        ExecutionPoolMonitor monitor = new ExecutionPoolMonitor();
        AsyncConfig config = new AsyncConfig(properties, monitor);

        triggerExecutor = config.triggerExecutor();
        ThreadPoolExecutor job = register(config.jobExecutor());
        ThreadPoolExecutor dag = register(config.dagNodeExecutor());
        ThreadPoolExecutor task = register(config.taskExecutor());
        ThreadPoolExecutor script = register(config.scriptExecutorPool());

        assertAll(
                () -> assertPool(job, 2, 5, 0),
                () -> assertPool(dag, 4, 7, 10),
                () -> assertPool(task, 3, 6, 8),
                () -> assertPool(script, 1, 4, 9),
                () -> assertEquals(1, triggerExecutor.getCorePoolSize()),
                () -> assertEquals(3, triggerExecutor.getMaxPoolSize()),
                () ->
                        assertEquals(
                                6,
                                triggerExecutor
                                        .getThreadPoolExecutor()
                                        .getQueue()
                                        .remainingCapacity()),
                () -> assertNotSame(job, task),
                () -> assertNotSame(job, dag),
                () -> assertNotSame(dag, task),
                () -> assertNotSame(task, script),
                () -> assertEquals(5, monitor.snapshots().size()));
    }

    @Test
    void jobPoolShouldRejectInsteadOfQueueingNestedWork() throws InterruptedException {
        AppProperties properties = properties();
        properties.getExecution().setJob(pool(1, 1, 0));
        ExecutionPoolMonitor monitor = new ExecutionPoolMonitor();
        AsyncConfig config = new AsyncConfig(properties, monitor);
        ThreadPoolExecutor job = register(config.jobExecutor());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        job.execute(
                () -> {
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });

        assertTrue(started.await(2, TimeUnit.SECONDS));
        try {
            assertThrows(RejectedExecutionException.class, () -> job.execute(() -> {}));
            ExecutionPoolSnapshot snapshot = monitor.snapshot("job");
            assertAll(
                    () -> assertEquals(0, snapshot.queuedTaskCount()),
                    () -> assertEquals(1L, snapshot.rejectedTaskCount()),
                    () -> assertEquals(1, snapshot.activeCount()));
        } finally {
            release.countDown();
        }
    }

    @Test
    void saturatedJobPoolShouldNotBlockDagNodePool() throws InterruptedException {
        AppProperties properties = properties();
        properties.getExecution().setJob(pool(1, 1, 0));
        properties.getExecution().setDag(pool(1, 1, 1));
        ExecutionPoolMonitor monitor = new ExecutionPoolMonitor();
        AsyncConfig config = new AsyncConfig(properties, monitor);
        ThreadPoolExecutor job = register(config.jobExecutor());
        ThreadPoolExecutor dag = register(config.dagNodeExecutor());
        CountDownLatch jobStarted = new CountDownLatch(1);
        CountDownLatch releaseJob = new CountDownLatch(1);
        CountDownLatch dagCompleted = new CountDownLatch(1);
        job.execute(
                () -> {
                    jobStarted.countDown();
                    try {
                        releaseJob.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });

        assertTrue(jobStarted.await(2, TimeUnit.SECONDS));
        try {
            assertThrows(RejectedExecutionException.class, () -> job.execute(() -> {}));
            dag.execute(dagCompleted::countDown);

            assertTrue(dagCompleted.await(2, TimeUnit.SECONDS));
            assertAll(
                    () -> assertEquals(1L, monitor.snapshot("job").rejectedTaskCount()),
                    () -> assertEquals(0L, monitor.snapshot("dag").rejectedTaskCount()));
        } finally {
            releaseJob.countDown();
        }
    }

    @Test
    void shouldForceStopTaskAfterGracefulShutdownTimeout() throws InterruptedException {
        AppProperties properties = properties();
        properties.getExecution().setShutdownTimeout(Duration.ofMillis(50));
        AsyncConfig config = new AsyncConfig(properties, new ExecutionPoolMonitor());
        GracefulThreadPoolExecutor task =
                (GracefulThreadPoolExecutor) register(config.taskExecutor());
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        task.execute(
                () -> {
                    started.countDown();
                    try {
                        new CountDownLatch(1).await();
                    } catch (InterruptedException exception) {
                        interrupted.set(true);
                        Thread.currentThread().interrupt();
                    }
                });

        assertTrue(started.await(2, TimeUnit.SECONDS));
        task.shutdownGracefully();

        assertAll(
                () -> assertTrue(interrupted.get()),
                () -> assertTrue(task.isShutdown()),
                () -> assertTrue(task.awaitTermination(2, TimeUnit.SECONDS)));
    }

    private AppProperties properties() {
        AppProperties properties = new AppProperties();
        AppProperties.Execution execution = properties.getExecution();
        execution.setShutdownTimeout(Duration.ofSeconds(2));
        execution.setTrigger(pool(1, 3, 6));
        execution.setJob(pool(2, 5, 0));
        execution.setDag(pool(4, 7, 10));
        execution.setTask(pool(3, 6, 8));
        execution.setScript(pool(1, 4, 9));
        return properties;
    }

    private AppProperties.Pool pool(int coreSize, int maxSize, int queueCapacity) {
        return new AppProperties.Pool(coreSize, maxSize, queueCapacity, Duration.ofSeconds(30));
    }

    private ThreadPoolExecutor register(ExecutorService executor) {
        executors.add(executor);
        return (ThreadPoolExecutor) executor;
    }

    private void assertPool(
            ThreadPoolExecutor executor, int coreSize, int maxSize, int queueCapacity) {
        assertEquals(coreSize, executor.getCorePoolSize());
        assertEquals(maxSize, executor.getMaximumPoolSize());
        assertEquals(queueCapacity, executor.getQueue().remainingCapacity());
        assertEquals(
                30_000L, executor.getKeepAliveTime(java.util.concurrent.TimeUnit.MILLISECONDS));
    }
}
