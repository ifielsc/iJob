package com.ifielsc.ijob.plugin.servermonitor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.contract.monitor.CollectType;
import com.ifielsc.ijob.contract.monitor.application.ApplicationCollectConfig;
import com.ifielsc.ijob.contract.monitor.application.ProcessMatchType;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorCollectConfig;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorNotifyConfig;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorRuleConfig;
import com.ifielsc.ijob.plugin.servermonitor.rule.ApplicationMonitorRule;
import com.ifielsc.ijob.plugin.servermonitor.rule.CpuMonitorRule;
import com.ifielsc.ijob.plugin.servermonitor.rule.DiskMonitorRule;
import com.ifielsc.ijob.plugin.servermonitor.rule.Threshold;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class ServerMonitorTaskConfigValidatorTest {
    @Test
    void rejectsReservedMatchTypeWithoutAgentImplementation() {
        var config = applicationConfig("orders", "orders.*");
        config.getCollect().getApplications().getFirst().setMatchType(ProcessMatchType.CONTAINS);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServerMonitorTaskConfigValidator().validate(config));
    }

    private final ServerMonitorTaskConfigValidator validator =
            new ServerMonitorTaskConfigValidator();

    @Test
    void validCpuConfigShouldPass() {
        assertDoesNotThrow(() -> validator.validate(baseCpuConfig()));
    }

    @Test
    void resourceAndCollectItemsAreRequired() {
        MonitorConfig invalidResource = baseCpuConfig();
        invalidResource.setResourceId(0L);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(invalidResource));

        MonitorConfig emptyItems = baseCpuConfig();
        emptyItems.getCollect().setItems(List.of());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(emptyItems));
    }

    @Test
    void collectItemsMustBeUniqueAndSupported() {
        MonitorConfig duplicate = baseCpuConfig();
        duplicate.getCollect().setItems(List.of(CollectType.CPU, CollectType.CPU));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(duplicate));

        MonitorConfig unsupported = baseCpuConfig();
        unsupported.getCollect().setItems(List.of(CollectType.PROCESS));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(unsupported));
    }

    @Test
    void enabledRuleRequiresMatchingCollectItem() {
        MonitorConfig config = baseCpuConfig();
        config.getCollect().setItems(List.of(CollectType.MEMORY));

        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
    }

    @Test
    void thresholdsMustBeFiniteOrderedAndWithinRange() {
        MonitorConfig reversed = baseCpuConfig();
        reversed.getRules()
                .getCpu()
                .setUsage(Threshold.builder().warning(90D).critical(80D).build());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(reversed));

        MonitorConfig nonFinite = baseCpuConfig();
        nonFinite.getRules().getCpu().setUsage(Threshold.builder().warning(Double.NaN).build());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(nonFinite));

        MonitorConfig percentage = baseCpuConfig();
        percentage.getRules().getCpu().setUsage(Threshold.builder().critical(101D).build());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(percentage));
    }

    @Test
    void diskRuleRequiresDefaultThresholdForUnlistedDisks() {
        MonitorConfig config = baseCpuConfig();
        config.getCollect().setItems(List.of(CollectType.DISK));
        config.getRules().setCpu(null);
        config.getRules()
                .setDisk(
                        DiskMonitorRule.builder()
                                .paths(Map.of("/data", Threshold.builder().critical(90D).build()))
                                .build());

        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));

        config.getRules().getDisk().setUsage(Threshold.builder().critical(95D).build());
        assertDoesNotThrow(() -> validator.validate(config));
    }

    @Test
    void diskPathsShouldNormalizeAndRemainUnique() {
        MonitorConfig config =
                diskConfig(Map.of("c:\\Data\\", Threshold.builder().critical(90D).build()));

        assertDoesNotThrow(() -> validator.validate(config));
        assertTrue(config.getRules().getDisk().getPaths().containsKey("C:/data"));

        MonitorConfig duplicate = diskConfig(new java.util.LinkedHashMap<>());
        duplicate
                .getRules()
                .getDisk()
                .getPaths()
                .put("C:\\Data", Threshold.builder().critical(90D).build());
        duplicate
                .getRules()
                .getDisk()
                .getPaths()
                .put("c:/data/", Threshold.builder().critical(91D).build());

        assertThrows(IllegalArgumentException.class, () -> validator.validate(duplicate));
    }

    @Test
    void applicationRuleRequiresValidMatchingCollection() {
        MonitorConfig config = applicationConfig("worker", "worker.*");
        assertDoesNotThrow(() -> validator.validate(config));

        config.getRules().getApplications().getFirst().setName("other");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));

        MonitorConfig invalidRegex = applicationConfig("worker", "[");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(invalidRegex));

        MonitorConfig whitespace = applicationConfig(" worker ", "worker");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(whitespace));

        MonitorConfig inconsistent = applicationConfig("worker", "worker");
        inconsistent.getRules().getApplications().getFirst().setRequired(false);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(inconsistent));
    }

    @Test
    void applicationMatcherShouldRejectUnsafeRegexAndOversizedPid() {
        MonitorConfig unsafeRegex = applicationConfig("worker", "(a+)+$");
        MonitorConfig oversizedPid = applicationConfig("worker", "worker");
        oversizedPid.getCollect().getApplications().getFirst().setMatchType(ProcessMatchType.PID);
        oversizedPid
                .getCollect()
                .getApplications()
                .getFirst()
                .setMatchValue(Long.toString((long) Integer.MAX_VALUE + 1));

        assertThrows(IllegalArgumentException.class, () -> validator.validate(unsafeRegex));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(oversizedPid));
    }

    @Test
    void notificationRequiresIdAndRecoveryIsExplicitlyUnsupported() {
        MonitorConfig missingId = baseCpuConfig();
        missingId.setNotify(MonitorNotifyConfig.builder().enabled(true).build());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(missingId));

        MonitorConfig recovery = baseCpuConfig();
        recovery.setNotify(MonitorNotifyConfig.builder().recovery(true).build());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(recovery));
    }

    @Test
    void atLeastOneRuleMustRemainEnabled() {
        MonitorConfig config = baseCpuConfig();
        config.getRules().getCpu().setEnabled(false);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
    }

    private MonitorConfig baseCpuConfig() {
        return MonitorConfig.builder()
                .resourceId(12L)
                .collect(MonitorCollectConfig.builder().items(List.of(CollectType.CPU)).build())
                .rules(
                        MonitorRuleConfig.builder()
                                .cpu(
                                        CpuMonitorRule.builder()
                                                .usage(
                                                        Threshold.builder()
                                                                .warning(70D)
                                                                .critical(90D)
                                                                .build())
                                                .build())
                                .build())
                .build();
    }

    private MonitorConfig applicationConfig(String name, String regex) {
        ApplicationCollectConfig collect =
                ApplicationCollectConfig.builder()
                        .name(name)
                        .matchType(ProcessMatchType.REGEX)
                        .matchValue(regex)
                        .required(true)
                        .allowMultiple(false)
                        .build();
        ApplicationMonitorRule rule =
                ApplicationMonitorRule.builder()
                        .name(name)
                        .required(true)
                        .cpu(Threshold.builder().critical(90D).build())
                        .build();
        return MonitorConfig.builder()
                .resourceId(12L)
                .collect(
                        MonitorCollectConfig.builder()
                                .items(List.of(CollectType.APPLICATION))
                                .applications(List.of(collect))
                                .build())
                .rules(MonitorRuleConfig.builder().applications(List.of(rule)).build())
                .build();
    }

    private MonitorConfig diskConfig(Map<String, Threshold> paths) {
        MonitorConfig config = baseCpuConfig();
        config.getCollect().setItems(List.of(CollectType.DISK));
        config.getRules().setCpu(null);
        config.getRules()
                .setDisk(
                        DiskMonitorRule.builder()
                                .usage(Threshold.builder().critical(95D).build())
                                .paths(paths)
                                .build());
        return config;
    }
}
