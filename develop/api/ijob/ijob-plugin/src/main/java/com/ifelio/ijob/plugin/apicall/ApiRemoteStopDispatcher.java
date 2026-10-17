package com.ifelio.ijob.plugin.apicall;

import com.ifelio.ijob.core.resource.factory.service.client.ServiceInvocation;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded control traffic, isolated from task and DAG execution pools. */
@Component
public class ApiRemoteStopDispatcher
        implements DisposableBean, com.ifelio.ijob.common.runtime.RuntimeFenceListener {
    private final ThreadPoolExecutor executor;
    private final Set<ServiceInvocation> active = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    public ApiRemoteStopDispatcher(MeterRegistry registry) {
        executor =
                new ThreadPoolExecutor(
                        2,
                        2,
                        0,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(32),
                        Thread.ofPlatform().daemon().name("api-remote-stop-", 0).factory(),
                        new ThreadPoolExecutor.AbortPolicy());
        new ExecutorServiceMetrics(executor, "api.remote.stop", Tags.empty()).bindTo(registry);
    }

    public boolean submit(Runnable work, Runnable release) {
        Work item = new Work(work, release);
        try {
            executor.execute(item);
            return true;
        } catch (RejectedExecutionException failure) {
            item.cancel();
            return false;
        }
    }

    public AutoCloseable register(ServiceInvocation invocation) {
        active.add(invocation);
        if (closed.get()) invocation.close();
        return () -> active.remove(invocation);
    }

    @Override
    public void onRuntimeFenced(String reason) {
        destroy();
    }

    @Override
    public void destroy() {
        closed.set(true);
        executor.shutdownNow().forEach(item -> ((Work) item).cancel());
        active.forEach(ServiceInvocation::close);
    }

    private static final class Work implements Runnable {
        private final Runnable work, release;
        private final AtomicBoolean claimed = new AtomicBoolean();

        Work(Runnable work, Runnable release) {
            this.work = work;
            this.release = release;
        }

        public void run() {
            if (!claimed.compareAndSet(false, true)) return;
            try {
                work.run();
            } finally {
                release.run();
            }
        }

        void cancel() {
            if (claimed.compareAndSet(false, true)) release.run();
        }
    }
}
