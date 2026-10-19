package com.ifielsc.ijob.framework.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.LongAdder;

@Component
public class ExecutionPoolMonitor {
    private final Map<String, ThreadPoolExecutor> pools = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> rejectedTasks = new ConcurrentHashMap<>();
    private final Set<String> boundPools = ConcurrentHashMap.newKeySet();
    private volatile MeterRegistry meterRegistry;

    @Autowired(required = false)
    void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        pools.forEach(this::bindMetrics);
    }

    public void register(String poolName, ThreadPoolExecutor executor) {
        if (poolName == null || poolName.isBlank() || executor == null) {
            throw new IllegalArgumentException("Execution pool registration is invalid");
        }
        ThreadPoolExecutor existing = pools.putIfAbsent(poolName, executor);
        if (existing != null && existing != executor) {
            throw new IllegalStateException("Execution pool is already registered: " + poolName);
        }
        rejectedTasks.computeIfAbsent(poolName, ignored -> new LongAdder());
        bindMetrics(poolName, executor);
    }

    public RejectedExecutionHandler rejectionHandler(String poolName) {
        if (poolName == null || poolName.isBlank()) {
            throw new IllegalArgumentException("Execution pool name is required");
        }
        LongAdder rejected = rejectedTasks.computeIfAbsent(poolName, ignored -> new LongAdder());
        return (task, executor) -> {
            rejected.increment();
            throw new RejectedExecutionException("Execution pool rejected task: " + poolName);
        };
    }

    public List<ExecutionPoolSnapshot> snapshots() {
        return pools.entrySet().stream()
                .map(entry -> snapshot(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ExecutionPoolSnapshot::name))
                .toList();
    }

    public ExecutionPoolSnapshot snapshot(String poolName) {
        ThreadPoolExecutor executor = pools.get(poolName);
        if (executor == null) {
            throw new IllegalArgumentException("Execution pool is not registered: " + poolName);
        }
        return snapshot(poolName, executor);
    }

    private ExecutionPoolSnapshot snapshot(String poolName, ThreadPoolExecutor executor) {
        LongAdder rejected = rejectedTasks.get(poolName);
        return new ExecutionPoolSnapshot(
                poolName,
                executor.getCorePoolSize(),
                executor.getMaximumPoolSize(),
                executor.getPoolSize(),
                executor.getActiveCount(),
                executor.getQueue().size(),
                executor.getQueue().remainingCapacity(),
                executor.getCompletedTaskCount(),
                rejected == null ? 0L : rejected.sum(),
                executor.isShutdown(),
                executor.isTerminated());
    }

    private void bindMetrics(String poolName, ThreadPoolExecutor executor) {
        MeterRegistry registry = meterRegistry;
        if (registry == null || !boundPools.add(poolName)) {
            return;
        }
        gauge(
                registry,
                "ijob.execution.pool.size",
                poolName,
                executor,
                ThreadPoolExecutor::getPoolSize);
        gauge(
                registry,
                "ijob.execution.pool.active",
                poolName,
                executor,
                ThreadPoolExecutor::getActiveCount);
        Gauge.builder("ijob.execution.pool.queued", executor, value -> value.getQueue().size())
                .tag("pool", poolName)
                .register(registry);
        Gauge.builder(
                        "ijob.execution.pool.queue.remaining",
                        executor,
                        value -> value.getQueue().remainingCapacity())
                .tag("pool", poolName)
                .register(registry);
        Gauge.builder(
                        "ijob.execution.pool.completed",
                        executor,
                        ThreadPoolExecutor::getCompletedTaskCount)
                .tag("pool", poolName)
                .register(registry);
        LongAdder rejected = rejectedTasks.get(poolName);
        Gauge.builder("ijob.execution.pool.rejected", rejected, LongAdder::sum)
                .tag("pool", poolName)
                .register(registry);
    }

    private void gauge(
            MeterRegistry registry,
            String name,
            String poolName,
            ThreadPoolExecutor executor,
            java.util.function.ToDoubleFunction<ThreadPoolExecutor> value) {
        Gauge.builder(name, executor, value).tag("pool", poolName).register(registry);
    }
}
