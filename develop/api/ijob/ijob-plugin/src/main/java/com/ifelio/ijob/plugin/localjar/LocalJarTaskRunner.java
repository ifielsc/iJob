package com.ifelio.ijob.plugin.localjar;

import com.ifelio.ijob.common.exception.StopException;
import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.task.TaskResult;
import com.ifelio.ijob.core.execute.task.TaskRunner;
import com.ifelio.ijob.core.task.definition.TaskExecutionSpec;
import com.ifelio.ijob.plugin.common.config.PluginConfigCodec;
import com.ifelio.ijob.plugin.common.result.PluginMessage;
import com.ifelio.ijob.spi.localjar.LocalJarExecution;
import com.ifelio.ijob.spi.localjar.LocalJarResult;
import com.ifelio.ijob.spi.localjar.LocalJarStoppedException;
import com.ifelio.ijob.spi.localjar.LocalJarTask;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "app.plugin.local-jar",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class LocalJarTaskRunner implements TaskRunner {
    private static final int MAX_RESULT_MESSAGE = 1024;
    private static final long STOP_HOOK_WAIT_MILLIS = 2_000;

    private final PluginConfigCodec configCodec;
    private final LocalJarArtifactValidator artifactValidator;
    private final LocalJarClassLoaderCache classLoaderCache;
    private final ConcurrentMap<Long, ActiveSession> activeSessions = new ConcurrentHashMap<>();

    @Override
    public PluginType supportType() {
        return PluginType.LOCAL_JAR;
    }

    @Override
    public TaskResult run(TaskExecutionSpec spec, TaskContext context) {
        LocalJarContextAdapter adapter = new LocalJarContextAdapter(context);
        LocalJarTaskConfig config;
        LocalJarArtifact artifact;
        try {
            adapter.checkExecution();
            config = parseSnapshot(spec.getPluginConfig());
            context.logInfo("Local JAR artifact verification started");
            artifact =
                    artifactValidator.validate(
                            config.getJarFile(),
                            config.getSha256(),
                            config.getEntryClass(),
                            adapter::checkExecution);
            verifySnapshot(config, artifact);
            context.logInfo(
                    "Local JAR artifact verified, sha256=" + shortDigest(artifact.sha256()));
            adapter.checkExecution();
        } catch (LocalJarStoppedException exception) {
            return TaskResult.stop();
        } catch (IllegalArgumentException exception) {
            log.warn(
                    "Local JAR artifact rejected, instanceId={}, reason={}",
                    context.getInstanceId(),
                    com.ifelio.ijob.common.diagnostic.SafeDiagnostic.describe(exception));
            return TaskResult.failure(exception, PluginMessage.LOCAL_JAR_FAILED);
        }
        try (LocalJarClassLoaderCache.Lease lease = classLoaderCache.acquire(artifact)) {
            LocalJarTask task = instantiate(lease.loader(), artifact.entryClass());
            LocalJarExecution execution =
                    new LocalJarExecution(spec.getName(), userConfiguration(config));
            ActiveSession session = new ActiveSession(task, execution, adapter, lease.loader());
            if (activeSessions.putIfAbsent(context.getInstanceId(), session) != null) {
                throw new IllegalStateException("Local JAR task session already exists");
            }
            ClassLoader original = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(lease.loader());
                adapter.checkExecution();
                context.logInfo("Local JAR entry execution started");
                TaskResult result = map(task.run(execution, adapter));
                context.logInfo(
                        "Local JAR entry execution completed, status=" + result.getStatus());
                return result;
            } catch (LocalJarStoppedException | StopException exception) {
                return TaskResult.stop();
            } finally {
                Thread.currentThread().setContextClassLoader(original);
                if (!session.finishRun()) {
                    log.warn(
                            "Local JAR stop hook did not finish before "
                                    + "class loader release, instanceId={}",
                            context.getInstanceId());
                }
                activeSessions.remove(context.getInstanceId(), session);
                context.cleanupResources();
            }
        } catch (LocalJarStoppedException | StopException exception) {
            return TaskResult.stop();
        } catch (Exception exception) {
            try {
                context.checkStop();
            } catch (StopException stopped) {
                return TaskResult.stop();
            }
            log.error(
                    "Local JAR task failed, instanceId={}, sha256={}, entryClass={}",
                    context.getInstanceId(),
                    shortDigest(artifact.sha256()),
                    artifact.entryClass(),
                    com.ifelio.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
            context.cleanupResources();
            return TaskResult.fail(PluginMessage.LOCAL_JAR_FAILED);
        }
    }

    @Override
    public void stop(TaskExecutionSpec spec, TaskContext context) {
        ActiveSession session = activeSessions.get(context.getInstanceId());
        try {
            if (session != null) {
                session.stop(context.getInstanceId());
            }
        } finally {
            context.cleanupResources();
        }
    }

    private LocalJarTaskConfig parseSnapshot(String value) {
        try {
            LocalJarTaskConfig config =
                    configCodec.read(value, LocalJarTaskConfig.class, "Local JAR");
            if (config == null
                    || config.getFormatVersion() == null
                    || config.getFormatVersion() != LocalJarTaskConfig.FORMAT_VERSION) {
                throw new IllegalArgumentException("invalid format version");
            }
            return config;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Local JAR definition snapshot is invalid", exception);
        }
    }

    private void verifySnapshot(LocalJarTaskConfig config, LocalJarArtifact artifact) {
        if (config.getJarSize() == null
                || config.getJarSize() != artifact.size()
                || !artifact.sha256().equals(config.getSha256())
                || !artifact.implementationVersion().equals(config.getImplementationVersion())
                || !artifact.spiVersion().equals(config.getSpiVersion())
                || !artifact.entryClass().equals(config.getEntryClass())) {
            throw new IllegalArgumentException(
                    "Local JAR definition snapshot does not match artifact");
        }
    }

    private LocalJarTask instantiate(LocalJarClassLoader loader, String entryClass) {
        try {
            return LocalJarTask.class.cast(
                    loader.loadClass(entryClass).getConstructor().newInstance());
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException(
                    "Local JAR entry constructor failed", exception.getTargetException());
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("Local JAR entry could not be instantiated", exception);
        }
    }

    private String userConfiguration(LocalJarTaskConfig config) {
        return config.getUserConfig() == null
                ? "null"
                : configCodec.write(config.getUserConfig(), "Local JAR");
    }

    private TaskResult map(LocalJarResult result) {
        if (result == null) {
            return TaskResult.fail(PluginMessage.LOCAL_JAR_NO_RESULT);
        }
        String message = safeResultMessage(result.message());
        return switch (result.status()) {
            case SUCCESS -> TaskResult.success(message);
            case FAILED ->
                    message == null
                            ? TaskResult.fail(PluginMessage.LOCAL_JAR_REPORTED_FAILURE)
                            : TaskResult.fail(message);
            case STOPPED -> TaskResult.stop(message);
            case TIMED_OUT ->
                    message == null
                            ? TaskResult.timeout(PluginMessage.LOCAL_JAR_TIMEOUT)
                            : TaskResult.timeout(message);
            case SKIPPED ->
                    TaskResult.builder()
                            .status(com.ifelio.ijob.contract.enums.InstanceStatus.SKIPPED)
                            .message(message)
                            .build();
        };
    }

    private String safeResultMessage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        StringBuilder result = new StringBuilder(Math.min(value.length(), MAX_RESULT_MESSAGE));
        for (int index = 0;
                index < value.length() && result.length() < MAX_RESULT_MESSAGE;
                index++) {
            char character = value.charAt(index);
            result.append(Character.isISOControl(character) ? ' ' : character);
        }
        return result.toString();
    }

    private String shortDigest(String digest) {
        return digest.substring(0, Math.min(12, digest.length()));
    }

    private static final class ActiveSession {
        private final LocalJarTask task;
        private final LocalJarExecution execution;
        private final LocalJarContextAdapter context;
        private final ClassLoader classLoader;
        private final AtomicBoolean stopped = new AtomicBoolean();
        private final Object lifecycle = new Object();
        private boolean acceptingStops = true;
        private boolean stopRunning;

        private ActiveSession(
                LocalJarTask task,
                LocalJarExecution execution,
                LocalJarContextAdapter context,
                ClassLoader classLoader) {
            this.task = task;
            this.execution = execution;
            this.context = context;
            this.classLoader = classLoader;
        }

        private void stop(Long instanceId) {
            synchronized (lifecycle) {
                if (!acceptingStops || !stopped.compareAndSet(false, true)) {
                    return;
                }
                stopRunning = true;
            }
            ClassLoader original = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(classLoader);
                task.stop(execution, context);
            } catch (Exception exception) {
                log.warn(
                        "Local JAR stop hook failed, instanceId={}, type={}",
                        instanceId,
                        exception.getClass().getSimpleName());
            } finally {
                Thread.currentThread().setContextClassLoader(original);
                synchronized (lifecycle) {
                    stopRunning = false;
                    lifecycle.notifyAll();
                }
            }
        }

        private boolean finishRun() {
            long deadline =
                    System.nanoTime()
                            + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(
                                    STOP_HOOK_WAIT_MILLIS);
            synchronized (lifecycle) {
                acceptingStops = false;
                while (stopRunning) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        return false;
                    }
                    try {
                        java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(lifecycle, remaining);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
                return true;
            }
        }
    }
}
