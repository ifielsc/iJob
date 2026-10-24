// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.contract.pack;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

public final class JobPackageDTO {
    private JobPackageDTO() {}

    public enum Kind {
        TASK,
        JOB,
        RESOURCE,
        CONCURRENCY_RESOURCE,
        PARAMETER,
        CALENDAR,
        SCHEDULE,
        NOTIFY,
        PARAMETER_BINDING,
        NOTIFY_BINDING
    }

    public enum Action {
        CREATE,
        REUSE,
        UPDATE
    }

    public record Selection(Kind kind, String id) {}

    public record ExportRequest(
            List<Selection> roots,
            boolean includeResources,
            boolean includeSchedules,
            boolean includeNotifications) {}

    public record Entry(
            String key,
            Kind kind,
            String name,
            ObjectNode body,
            Map<String, String> references,
            List<String> requiredSecrets,
            boolean referenceOnly) {}

    public record Bundle(
            int formatVersion,
            String productVersion,
            String businessTimezone,
            List<String> roots,
            List<Entry> objects) {}

    public record File(String filename, String content, String sha256) {}

    public record Upload(String content) {}

    public record Choice(
            Action action, String targetId, ObjectNode body, Map<String, String> secrets) {
        @Override
        public String toString() {
            return "Choice[redacted]";
        }
    }

    public record PlanRequest(
            String content, Map<String, Choice> choices, boolean reviewedEnvironment) {
        @Override
        public String toString() {
            return "PlanRequest[redacted]";
        }
    }

    public record Step(
            String key,
            Kind kind,
            String name,
            Action action,
            String targetId,
            String status,
            List<String> issues,
            ObjectNode before,
            ObjectNode after) {}

    public record Plan(
            String id,
            String status,
            String createdAt,
            List<Step> steps,
            String sourceTimezone,
            String targetTimezone,
            String actor,
            String startedAt,
            String completedAt) {
        public Plan(
                String id,
                String status,
                String createdAt,
                List<Step> steps,
                String sourceTimezone,
                String targetTimezone) {
            this(id, status, createdAt, steps, sourceTimezone, targetTimezone, null, null, null);
        }
    }

    public record Record(
            String id,
            String status,
            String createdAt,
            int total,
            int completed,
            String actor,
            String startedAt,
            String completedAt) {}

    public record Verification(String id, String status, List<String> changedKeys) {}
}
