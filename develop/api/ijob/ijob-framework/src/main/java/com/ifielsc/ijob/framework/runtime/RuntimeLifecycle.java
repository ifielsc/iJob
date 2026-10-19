// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.framework.runtime;

import com.ifielsc.ijob.common.runtime.ApplicationRuntimeState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

@Component
public class RuntimeLifecycle implements ApplicationListener<ApplicationReadyEvent> {
    private static final Logger log = LoggerFactory.getLogger(RuntimeLifecycle.class);
    private final ApplicationRuntimeState runtimeState;

    public RuntimeLifecycle(ApplicationRuntimeState runtimeState) {
        this.runtimeState = runtimeState;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("application started, runtimePhase={}", runtimeState.phase());
    }

    @Component
    static class CloseListener implements ApplicationListener<ContextClosedEvent> {
        private static final Logger log = LoggerFactory.getLogger(CloseListener.class);
        private final ApplicationRuntimeState runtimeState;
        private final RuntimeRunRegistry runtimeRunRegistry;

        CloseListener(ApplicationRuntimeState runtimeState, RuntimeRunRegistry runtimeRunRegistry) {
            this.runtimeState = runtimeState;
            this.runtimeRunRegistry = runtimeRunRegistry;
        }

        @Override
        public void onApplicationEvent(ContextClosedEvent event) {
            runtimeState.markDraining();
            try {
                runtimeRunRegistry.markDraining();
            } catch (RuntimeException exception) {
                log.warn(
                        "application run draining state update failed",
                        com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
            }
            log.info("application runtime is draining");
        }
    }
}
