// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.framework.runtime;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.RuntimeMode;
import com.ifielsc.ijob.common.runtime.ApplicationRuntimeState;
import com.ifielsc.ijob.common.runtime.RuntimeFenceListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

@Component
public class RuntimeSafetyCoordinator {
    private static final Logger log = LoggerFactory.getLogger(RuntimeSafetyCoordinator.class);
    private final ApplicationRuntimeState runtimeState;
    private final AppProperties appProperties;
    private final ConfigurableApplicationContext applicationContext;
    private final ObjectProvider<RuntimeFenceListener> listeners;

    public RuntimeSafetyCoordinator(
            ApplicationRuntimeState runtimeState,
            AppProperties appProperties,
            ConfigurableApplicationContext applicationContext,
            ObjectProvider<RuntimeFenceListener> listeners) {
        this.runtimeState = runtimeState;
        this.appProperties = appProperties;
        this.applicationContext = applicationContext;
        this.listeners = listeners;
    }

    public void fence(String reason, Throwable cause) {
        if (!runtimeState.markFenced(reason)) {
            return;
        }
        if (cause == null) {
            log.error("application runtime fenced, reason={}", reason);
        } else {
            log.error("application runtime fenced, reason={}", reason, cause);
        }
        for (RuntimeFenceListener listener : listeners.orderedStream().toList()) {
            try {
                listener.onRuntimeFenced(reason);
            } catch (RuntimeException exception) {
                log.error(
                        "runtime fence listener failed, listener={}",
                        listener.getClass().getName(),
                        com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
            }
        }
        if (appProperties.getRuntime().getMode() == RuntimeMode.STANDALONE) {
            closeStandaloneAfter(appProperties.getExecution().getShutdownTimeout());
        }
    }

    private void closeStandaloneAfter(Duration delay) {
        long delayNanos = Math.max(0L, delay.toNanos());
        Thread.ofPlatform()
                .daemon()
                .name("ijob-fenced-shutdown")
                .start(
                        () -> {
                            LockSupport.parkNanos(delayNanos);
                            if (applicationContext.isActive()) {
                                log.error("closing fenced standalone application context");
                                applicationContext.close();
                            }
                        });
    }
}
