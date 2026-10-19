// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.contract.monitor.CollectType;
import com.ifielsc.ijob.contract.monitor.MonitorCollectRequest;
import com.ifielsc.ijob.contract.monitor.application.ApplicationCollectConfig;
import com.ifielsc.ijob.contract.monitor.application.ProcessMatchType;

import org.junit.jupiter.api.Test;

import java.util.List;

class MonitorRequestValidatorTest {

    private final MonitorRequestValidator validator = new MonitorRequestValidator();

    @Test
    void shouldAcceptBoundedValidRequest() {
        MonitorCollectRequest request =
                MonitorCollectRequest.builder()
                        .collectItems(List.of(CollectType.CPU, CollectType.APPLICATION))
                        .applications(List.of(application("java")))
                        .build();

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    void shouldRejectUnsupportedOrDuplicateCollectItems() {
        MonitorCollectRequest unsupported =
                MonitorCollectRequest.builder().collectItems(List.of(CollectType.PROCESS)).build();
        MonitorCollectRequest duplicate =
                MonitorCollectRequest.builder()
                        .collectItems(List.of(CollectType.CPU, CollectType.CPU))
                        .build();

        assertThrows(IllegalArgumentException.class, () -> validator.validate(unsupported));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(duplicate));
    }

    @Test
    void shouldRejectApplicationsWithoutApplicationCollectItem() {
        MonitorCollectRequest request =
                MonitorCollectRequest.builder()
                        .collectItems(List.of(CollectType.CPU))
                        .applications(List.of(application("java")))
                        .build();

        assertThrows(IllegalArgumentException.class, () -> validator.validate(request));
    }

    @Test
    void shouldRejectOversizedOrInvalidRegex() {
        ApplicationCollectConfig oversized = application("a".repeat(257));
        ApplicationCollectConfig invalid = application("[");

        assertThrows(IllegalArgumentException.class, () -> validator.validate(request(oversized)));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(request(invalid)));
    }

    @Test
    void shouldRejectUnsafeRegexAndPidOutsideRuntimeRange() {
        ApplicationCollectConfig unsafe = application("(a+)+$");
        ApplicationCollectConfig pid =
                ApplicationCollectConfig.builder()
                        .name("sample")
                        .matchType(ProcessMatchType.PID)
                        .matchValue(Long.toString((long) Integer.MAX_VALUE + 1))
                        .required(true)
                        .allowMultiple(false)
                        .build();

        assertThrows(IllegalArgumentException.class, () -> validator.validate(request(unsafe)));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(request(pid)));
    }

    private MonitorCollectRequest request(ApplicationCollectConfig application) {
        return MonitorCollectRequest.builder()
                .collectItems(List.of(CollectType.APPLICATION))
                .applications(List.of(application))
                .build();
    }

    private ApplicationCollectConfig application(String matchValue) {
        return ApplicationCollectConfig.builder()
                .name("sample")
                .matchType(ProcessMatchType.REGEX)
                .matchValue(matchValue)
                .required(true)
                .allowMultiple(false)
                .build();
    }
}
