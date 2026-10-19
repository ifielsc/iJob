package com.ifielsc.ijob.framework.config;

import com.ifielsc.ijob.common.config.AppProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;

@Configuration(proxyBeanMethods = false)
public class AsyncConfig {
    private final AppProperties appProperties;
    private final ExecutionPoolMonitor executionPoolMonitor;

    public AsyncConfig(AppProperties appProperties, ExecutionPoolMonitor executionPoolMonitor) {
        this.appProperties = appProperties;
        this.executionPoolMonitor = executionPoolMonitor;
    }

    @Bean(name = "triggerExecutor")
    @DependsOn({
        "singletonDatabaseLock",
        "runtimeRunRegistry",
        "jobExecutor",
        "dagNodeExecutor",
        "taskExecutor",
        "scriptExecutorPool"
    })
    public ThreadPoolTaskExecutor triggerExecutor() {
        AppProperties.Pool properties = appProperties.getExecution().getTrigger();
        validatePool("trigger", properties);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(com.ifielsc.ijob.common.diagnostic.DiagnosticContext::wrap);
        executor.setCorePoolSize(properties.getCoreSize());
        executor.setMaxPoolSize(properties.getMaxSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setKeepAliveSeconds(Math.toIntExact(properties.getKeepAlive().toSeconds()));
        executor.setThreadNamePrefix("ijob-trigger-");
        executor.setRejectedExecutionHandler(executionPoolMonitor.rejectionHandler("trigger"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationMillis(shutdownTimeout().toMillis());
        executor.initialize();
        executionPoolMonitor.register("trigger", executor.getThreadPoolExecutor());
        return executor;
    }

    @Bean(name = "jobExecutor", destroyMethod = "shutdownGracefully")
    @DependsOn({"singletonDatabaseLock", "runtimeRunRegistry"})
    public GracefulThreadPoolExecutor jobExecutor() {
        return createExecutor("job", "ijob-job-", appProperties.getExecution().getJob());
    }

    @Bean(name = "dagNodeExecutor", destroyMethod = "shutdownGracefully")
    @DependsOn({"singletonDatabaseLock", "runtimeRunRegistry"})
    public GracefulThreadPoolExecutor dagNodeExecutor() {
        return createExecutor("dag", "ijob-dag-", appProperties.getExecution().getDag());
    }

    @Bean(name = "taskExecutor", destroyMethod = "shutdownGracefully")
    @DependsOn({"singletonDatabaseLock", "runtimeRunRegistry"})
    public GracefulThreadPoolExecutor taskExecutor() {
        return createExecutor("task", "ijob-task-", appProperties.getExecution().getTask());
    }

    @Bean(name = "scriptExecutorPool", destroyMethod = "shutdownGracefully")
    @DependsOn({"singletonDatabaseLock", "runtimeRunRegistry"})
    public GracefulThreadPoolExecutor scriptExecutorPool() {
        return createExecutor("script", "ijob-script-", appProperties.getExecution().getScript());
    }

    private GracefulThreadPoolExecutor createExecutor(
            String poolName, String threadNamePrefix, AppProperties.Pool properties) {
        validatePool(poolName, properties);
        GracefulThreadPoolExecutor executor =
                new GracefulThreadPoolExecutor(
                        poolName,
                        properties.getCoreSize(),
                        properties.getMaxSize(),
                        properties.getKeepAlive(),
                        workQueue(properties.getQueueCapacity()),
                        new CustomizableThreadFactory(threadNamePrefix),
                        executionPoolMonitor.rejectionHandler(poolName),
                        shutdownTimeout());
        executionPoolMonitor.register(poolName, executor);
        return executor;
    }

    private BlockingQueue<Runnable> workQueue(int queueCapacity) {
        if (queueCapacity == 0) {
            return new SynchronousQueue<>();
        }
        return new LinkedBlockingQueue<>(queueCapacity);
    }

    private Duration shutdownTimeout() {
        Duration timeout = appProperties.getExecution().getShutdownTimeout();
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Execution shutdown timeout must be positive");
        }
        return timeout;
    }

    private void validatePool(String poolName, AppProperties.Pool properties) {
        if (properties == null
                || properties.getCoreSize() <= 0
                || properties.getMaxSize() < properties.getCoreSize()
                || properties.getQueueCapacity() < 0
                || properties.getKeepAlive() == null
                || properties.getKeepAlive().isNegative()) {
            throw new IllegalArgumentException(
                    "Execution pool configuration is invalid: " + poolName);
        }
    }
}
