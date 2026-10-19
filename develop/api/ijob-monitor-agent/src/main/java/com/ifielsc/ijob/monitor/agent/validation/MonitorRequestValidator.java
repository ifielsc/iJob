package com.ifielsc.ijob.monitor.agent.validation;

import com.ifielsc.ijob.contract.monitor.CollectType;
import com.ifielsc.ijob.contract.monitor.MonitorCollectRequest;
import com.ifielsc.ijob.contract.monitor.application.ApplicationCollectConfig;
import com.ifielsc.ijob.contract.monitor.application.ProcessMatchPolicy;
import com.ifielsc.ijob.contract.monitor.application.ProcessMatchType;

import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class MonitorRequestValidator {

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

    public void validate(MonitorCollectRequest request) {
        if (request == null) {
            throw invalid("monitor request is required");
        }
        Set<CollectType> items = validateItems(request.getCollectItems());
        validateApplications(request.getApplications(), items);
    }

    private Set<CollectType> validateItems(List<CollectType> collectItems) {
        if (collectItems == null || collectItems.isEmpty()) {
            throw invalid("monitor collect items are required");
        }
        Set<CollectType> items = EnumSet.noneOf(CollectType.class);
        for (CollectType type : collectItems) {
            if (type == null || !SUPPORTED_TYPES.contains(type)) {
                throw invalid("monitor collect type is not supported");
            }
            if (!items.add(type)) {
                throw invalid("monitor collect items must be unique");
            }
        }
        return items;
    }

    private void validateApplications(
            List<ApplicationCollectConfig> applications, Set<CollectType> items) {
        if (applications == null || applications.isEmpty()) {
            return;
        }
        if (!items.contains(CollectType.APPLICATION)) {
            throw invalid("applications require APPLICATION collect item");
        }
        if (applications.size() > MAX_APPLICATIONS) {
            throw invalid("monitor applications cannot exceed 64");
        }
        Set<String> names = new HashSet<>();
        for (ApplicationCollectConfig application : applications) {
            validateApplication(application, names);
        }
    }

    private void validateApplication(ApplicationCollectConfig application, Set<String> names) {
        if (application == null) {
            throw invalid("application collection config is required");
        }
        String name = requiredText(application.getName(), "application name is required");
        if (!application.getName().equals(name)) {
            throw invalid("application name cannot have surrounding whitespace");
        }
        requireLength(name, MAX_NAME_LENGTH, "application name is too long");
        if (!names.add(name)) {
            throw invalid("application names must be unique");
        }
        ProcessMatchType matchType = application.getMatchType();
        if (matchType == null) {
            throw invalid("application matchType is required");
        }
        String matchValue =
                requiredText(application.getMatchValue(), "application matchValue is required");
        requireLength(matchValue, MAX_MATCH_VALUE_LENGTH, "application matchValue is too long");
        if (application.getRequired() == null || application.getAllowMultiple() == null) {
            throw invalid("application boolean options are required");
        }
        ProcessMatchPolicy.validate(matchType, application.getMatchValue());
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

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
