package com.ifelio.ijob.framework.observability;

import com.ifelio.ijob.common.observability.ScannerObserver;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MicrometerScannerObserver implements ScannerObserver {
    private final MeterRegistry meterRegistry;
    private final Map<QueueKey, AtomicInteger> batchSizes = new ConcurrentHashMap<>();
    private final Map<QueueKey, AtomicInteger> saturated = new ConcurrentHashMap<>();

    public MicrometerScannerObserver(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordBatch(String scanner, String queue, int batchSize, int batchLimit) {
        QueueKey key = new QueueKey(scanner, queue);
        batchSizes.computeIfAbsent(key, this::registerBatchSize).set(batchSize);
        saturated
                .computeIfAbsent(key, this::registerSaturation)
                .set(batchSize >= batchLimit ? 1 : 0);
    }

    @Override
    public void recordItem(String scanner, String queue, boolean success) {
        meterRegistry
                .counter(
                        "ijob.scanner.items",
                        "scanner",
                        scanner,
                        "queue",
                        queue,
                        "outcome",
                        success ? "success" : "failure")
                .increment();
    }

    @Override
    public void recordRun(String scanner, boolean success, Duration duration) {
        Timer.builder("ijob.scanner.run")
                .description("Scanner run duration")
                .tag("scanner", scanner)
                .tag("outcome", success ? "success" : "failure")
                .register(meterRegistry)
                .record(duration);
    }

    private AtomicInteger registerBatchSize(QueueKey key) {
        AtomicInteger value = new AtomicInteger();
        Gauge.builder("ijob.scanner.batch.size", value, AtomicInteger::get)
                .description("Items discovered in the most recent scanner batch")
                .tag("scanner", key.scanner())
                .tag("queue", key.queue())
                .register(meterRegistry);
        return value;
    }

    private AtomicInteger registerSaturation(QueueKey key) {
        AtomicInteger value = new AtomicInteger();
        Gauge.builder("ijob.scanner.batch.saturated", value, AtomicInteger::get)
                .description("Whether the latest batch reached its configured limit")
                .tag("scanner", key.scanner())
                .tag("queue", key.queue())
                .register(meterRegistry);
        return value;
    }

    private record QueueKey(String scanner, String queue) {}
}
