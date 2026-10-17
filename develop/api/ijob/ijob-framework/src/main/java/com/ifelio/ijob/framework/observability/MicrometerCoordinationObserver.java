package com.ifelio.ijob.framework.observability;

import com.ifelio.ijob.common.observability.CoordinationObserver;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MicrometerCoordinationObserver implements CoordinationObserver {
    private final MeterRegistry registry;
    private final Map<StateKey, AtomicLong> states = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> leaseActive = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> leaseFence = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> delays = new ConcurrentHashMap<>();
    private final AtomicLong permitWaiting = new AtomicLong();

    public MicrometerCoordinationObserver(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("ijob.concurrency.permit.waiting", permitWaiting, AtomicLong::get)
                .description("Tasks currently waiting for concurrency permits")
                .register(registry);
    }

    @Override
    public void recordState(String component, String state, long value) {
        StateKey key = new StateKey(component, state);
        states.computeIfAbsent(key, this::registerState).set(value);
    }

    @Override
    public void recordLease(String lease, boolean active, long fenceToken) {
        leaseActive.computeIfAbsent(lease, this::registerLeaseActive).set(active ? 1 : 0);
        if (fenceToken > 0) {
            leaseFence.computeIfAbsent(lease, this::registerLeaseFence).set(fenceToken);
        }
    }

    @Override
    public void recordEvent(String component, String event) {
        registry.counter("ijob.coordination.events", "component", component, "event", event)
                .increment();
    }

    @Override
    public void recordDelay(String component, Duration delay) {
        delays.computeIfAbsent(component, this::registerDelay).set(Math.max(0, delay.toMillis()));
    }

    @Override
    public void recordPermitWaiting(long value) {
        permitWaiting.set(Math.max(0, value));
    }

    @Override
    public void recordPermitWait(Duration duration, String outcome) {
        Timer.builder("ijob.concurrency.permit.wait.duration")
                .description("Concurrency permit acquisition wait duration")
                .tag("outcome", outcome)
                .register(registry)
                .record(duration);
    }

    private AtomicLong registerState(StateKey key) {
        AtomicLong value = new AtomicLong();
        Gauge.builder("ijob.coordination.items", value, AtomicLong::get)
                .description("Current database coordination item count")
                .tag("component", key.component())
                .tag("state", key.state())
                .register(registry);
        return value;
    }

    private AtomicLong registerLeaseActive(String lease) {
        AtomicLong value = new AtomicLong();
        Gauge.builder("ijob.coordination.lease.active", value, AtomicLong::get)
                .description("Whether the named coordination lease is active")
                .tag("lease", lease)
                .register(registry);
        return value;
    }

    private AtomicLong registerLeaseFence(String lease) {
        AtomicLong value = new AtomicLong();
        Gauge.builder("ijob.coordination.lease.fence", value, AtomicLong::get)
                .description("Latest observed maintenance lease fence")
                .tag("lease", lease)
                .register(registry);
        return value;
    }

    private AtomicLong registerDelay(String component) {
        AtomicLong value = new AtomicLong();
        Gauge.builder("ijob.coordination.delay.millis", value, AtomicLong::get)
                .description("Oldest visible coordination item delay in milliseconds")
                .tag("component", component)
                .register(registry);
        return value;
    }

    private record StateKey(String component, String state) {}
}
