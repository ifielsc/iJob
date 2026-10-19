// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.servermonitor;

import com.ifielsc.ijob.contract.monitor.CollectType;
import com.ifielsc.ijob.contract.monitor.application.ApplicationCollectConfig;
import com.ifielsc.ijob.contract.monitor.application.ProcessMatchPolicy;
import com.ifielsc.ijob.contract.monitor.application.ProcessMatchType;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorCollectConfig;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorNotifyConfig;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorRuleConfig;
import com.ifielsc.ijob.plugin.servermonitor.rule.ApplicationMonitorRule;
import com.ifielsc.ijob.plugin.servermonitor.rule.DiskMonitorRule;
import com.ifielsc.ijob.plugin.servermonitor.rule.NetworkMonitorRule;
import com.ifielsc.ijob.plugin.servermonitor.rule.Threshold;

import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ServerMonitorTaskConfigValidator {
    private static final int MAX_APPLICATIONS = 64;
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_MATCH_VALUE_LENGTH = 1024;
    private static final Set<CollectType> SUPPORTED_TYPES =
            EnumSet.of(
                    CollectType.CPU,
                    CollectType.MEMORY,
                    CollectType.DISK,
                    CollectType.NETWORK,
                    CollectType.APPLICATION);

    public void validate(MonitorConfig config) {
        if (config == null) {
            throw invalid("monitor config is required");
        }
        if (config.getResourceId() == null || config.getResourceId() <= 0) {
            throw invalid("monitor resourceId must be positive");
        }

        MonitorCollectConfig collect = config.getCollect();
        if (collect == null || collect.getItems() == null || collect.getItems().isEmpty()) {
            throw invalid("monitor collect items are required");
        }
        Set<CollectType> items = EnumSet.noneOf(CollectType.class);
        for (CollectType type : collect.getItems()) {
            if (type == null || !SUPPORTED_TYPES.contains(type)) {
                throw invalid("monitor collect type is not supported");
            }
            if (!items.add(type)) {
                throw invalid("monitor collect items must be unique");
            }
        }

        Map<String, ApplicationCollectConfig> collectedApplications =
                validateApplications(collect.getApplications(), items);
        int enabledRules = validateRules(config.getRules(), items, collectedApplications);
        if (enabledRules == 0) {
            throw invalid("at least one monitor rule must be enabled");
        }
        validateNotify(config.getNotify());
    }

    private Map<String, ApplicationCollectConfig> validateApplications(
            List<ApplicationCollectConfig> applications, Set<CollectType> items) {
        if (applications == null || applications.isEmpty()) {
            return Map.of();
        }
        if (!items.contains(CollectType.APPLICATION)) {
            throw invalid("application collection requires APPLICATION item");
        }
        if (applications.size() > MAX_APPLICATIONS) {
            throw invalid("monitor applications cannot exceed 64");
        }

        Map<String, ApplicationCollectConfig> applicationsByName = new java.util.HashMap<>();
        for (ApplicationCollectConfig application : applications) {
            if (application == null) {
                throw invalid("application collection config is required");
            }
            String name = requiredText(application.getName(), "application name is required");
            requireNormalizedName(
                    application.getName(),
                    name,
                    "application name cannot have surrounding whitespace");
            requireLength(name, MAX_NAME_LENGTH, "application name is too long");
            if (applicationsByName.putIfAbsent(name, application) != null) {
                throw invalid("application names must be unique");
            }
            if (application.getMatchType() == null
                    || application.getMatchType() == ProcessMatchType.CONTAINS) {
                throw invalid("application matchType is required");
            }
            String matchValue =
                    requiredText(application.getMatchValue(), "application matchValue is required");
            requireLength(matchValue, MAX_MATCH_VALUE_LENGTH, "application matchValue is too long");
            if (application.getRequired() == null || application.getAllowMultiple() == null) {
                throw invalid("application boolean options are required");
            }
            ProcessMatchPolicy.validate(application.getMatchType(), application.getMatchValue());
        }
        return applicationsByName;
    }

    private int validateRules(
            MonitorRuleConfig rules,
            Set<CollectType> items,
            Map<String, ApplicationCollectConfig> collectedApplications) {
        if (rules == null) {
            throw invalid("monitor rules are required");
        }
        int enabled = 0;
        if (rules.getCpu() != null && rules.getCpu().isEnabled()) {
            requireCollected(items, CollectType.CPU);
            validateThreshold(rules.getCpu().getUsage(), true, "cpu usage threshold");
            enabled++;
        }
        if (rules.getMemory() != null && rules.getMemory().isEnabled()) {
            requireCollected(items, CollectType.MEMORY);
            validateThreshold(rules.getMemory().getUsage(), true, "memory usage threshold");
            enabled++;
        }
        if (rules.getDisk() != null && rules.getDisk().isEnabled()) {
            requireCollected(items, CollectType.DISK);
            validateDiskRule(rules.getDisk());
            enabled++;
        }
        if (rules.getNetwork() != null && rules.getNetwork().isEnabled()) {
            requireCollected(items, CollectType.NETWORK);
            validateNetworkRule(rules.getNetwork());
            enabled++;
        }

        List<ApplicationMonitorRule> applicationRules = rules.getApplications();
        if (applicationRules == null) {
            return enabled;
        }
        if (applicationRules.size() > MAX_APPLICATIONS) {
            throw invalid("monitor application rules cannot exceed 64");
        }
        Set<String> names = new HashSet<>();
        for (ApplicationMonitorRule rule : applicationRules) {
            if (rule == null) {
                throw invalid("application monitor rule is required");
            }
            String name = requiredText(rule.getName(), "application rule name is required");
            requireNormalizedName(
                    rule.getName(),
                    name,
                    "application rule name cannot have surrounding whitespace");
            requireLength(name, MAX_NAME_LENGTH, "application rule name is too long");
            if (!names.add(name)) {
                throw invalid("application rule names must be unique");
            }
            if (!rule.isEnabled()) {
                continue;
            }
            requireCollected(items, CollectType.APPLICATION);
            ApplicationCollectConfig collection = collectedApplications.get(name);
            if (collection == null) {
                throw invalid("application rule requires matching collection");
            }
            if (rule.getRequired() != null
                    && !rule.getRequired().equals(collection.getRequired())) {
                throw invalid("application required flags must be consistent");
            }
            if (rule.getCpu() == null
                    && rule.getMemory() == null
                    && !Boolean.TRUE.equals(collection.getRequired())) {
                throw invalid("application rule has no effective check");
            }
            validateOptionalThreshold(rule.getCpu(), true, "application cpu threshold");
            validateOptionalThreshold(rule.getMemory(), false, "application memory threshold");
            enabled++;
        }
        return enabled;
    }

    private void validateDiskRule(DiskMonitorRule rule) {
        Map<String, Threshold> paths = rule.getPaths();
        validateThreshold(rule.getUsage(), true, "disk usage threshold");
        if (paths == null) {
            return;
        }
        if (paths.size() > 128) {
            throw invalid("disk path rules cannot exceed 128");
        }
        Map<String, Threshold> normalizedPaths = new LinkedHashMap<>();
        for (Map.Entry<String, Threshold> entry : paths.entrySet()) {
            String path = requiredText(entry.getKey(), "disk rule path is required");
            requireNormalizedName(
                    entry.getKey(), path, "disk rule path cannot have surrounding whitespace");
            requireLength(path, 512, "disk rule path is too long");
            validateThreshold(entry.getValue(), true, "disk path threshold");
            String normalizedPath = DiskPathNormalizer.normalize(path);
            if (normalizedPaths.putIfAbsent(normalizedPath, entry.getValue()) != null) {
                throw invalid("disk rule paths must be unique");
            }
        }
        rule.setPaths(normalizedPaths);
    }

    private void validateNetworkRule(NetworkMonitorRule rule) {
        if (rule.getReceiveRate() == null && rule.getSendRate() == null) {
            throw invalid("network rule has no threshold");
        }
        validateOptionalThreshold(rule.getReceiveRate(), false, "network receive threshold");
        validateOptionalThreshold(rule.getSendRate(), false, "network send threshold");
    }

    private void validateNotify(MonitorNotifyConfig notify) {
        if (notify == null) {
            return;
        }
        if (notify.isRecovery()) {
            throw invalid("monitor recovery notification is not supported");
        }
        if (notify.isEnabled()
                && (notify.getNotifyConfigId() == null || notify.getNotifyConfigId() <= 0)) {
            throw invalid("monitor notifyConfigId must be positive");
        }
    }

    private void validateOptionalThreshold(Threshold threshold, boolean percentage, String name) {
        if (threshold != null) {
            validateThreshold(threshold, percentage, name);
        }
    }

    private void validateThreshold(Threshold threshold, boolean percentage, String name) {
        if (threshold == null
                || (threshold.getWarning() == null && threshold.getCritical() == null)) {
            throw invalid(name + " is required");
        }
        validateThresholdValue(threshold.getWarning(), percentage, name);
        validateThresholdValue(threshold.getCritical(), percentage, name);
        if (threshold.getWarning() != null
                && threshold.getCritical() != null
                && threshold.getWarning() > threshold.getCritical()) {
            throw invalid(name + " warning cannot exceed critical");
        }
    }

    private void validateThresholdValue(Double value, boolean percentage, String name) {
        if (value == null) {
            return;
        }
        if (!Double.isFinite(value) || value < 0 || (percentage && value > 100)) {
            throw invalid(name + " is out of range");
        }
    }

    private void requireCollected(Set<CollectType> items, CollectType type) {
        if (!items.contains(type)) {
            throw invalid(type + " rule requires matching collect item");
        }
    }

    private String requiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalid(message);
        }
        return value.trim();
    }

    private void requireLength(String value, int max, String message) {
        if (value.length() > max) {
            throw invalid(message);
        }
    }

    private void requireNormalizedName(String original, String normalized, String message) {
        if (!original.equals(normalized)) {
            throw invalid(message);
        }
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
