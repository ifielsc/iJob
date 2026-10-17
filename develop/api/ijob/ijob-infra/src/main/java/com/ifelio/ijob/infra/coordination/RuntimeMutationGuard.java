package com.ifelio.ijob.infra.coordination;

import com.ifelio.ijob.infra.mapper.coordination.RuntimeGuardMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Short transaction barriers, never held while executing a plugin or waiting for a permit. */
@Component
@RequiredArgsConstructor
public class RuntimeMutationGuard {
    public static final String CONCURRENCY = "concurrency-policy";
    public static final String LIFECYCLE = "runtime-lifecycle";
    public static final String AUTH_ACCOUNTS = "auth-accounts";
    private final RuntimeGuardMapper mapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public long lock(String key) {
        if (mapper.lockAndAdvance(key) != 1) {
            throw new IllegalStateException("Runtime guard is missing or exhausted: " + key);
        }
        return mapper.selectRevision(key);
    }
}
