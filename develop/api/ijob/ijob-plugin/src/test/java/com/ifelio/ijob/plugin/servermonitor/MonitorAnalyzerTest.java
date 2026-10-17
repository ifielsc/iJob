package com.ifelio.ijob.plugin.servermonitor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.contract.monitor.MonitorReport;
import com.ifelio.ijob.contract.monitor.application.ApplicationCollectConfig;
import com.ifelio.ijob.contract.monitor.application.ApplicationMetric;
import com.ifelio.ijob.contract.monitor.application.ProcessMatchType;
import com.ifelio.ijob.contract.monitor.metric.CpuMetric;
import com.ifelio.ijob.contract.monitor.metric.DiskMetric;
import com.ifelio.ijob.contract.monitor.metric.MemoryMetric;
import com.ifelio.ijob.contract.monitor.metric.NetworkMetric;
import com.ifelio.ijob.plugin.servermonitor.analyzer.ApplicationMonitorAnalyzer;
import com.ifelio.ijob.plugin.servermonitor.analyzer.CompositeMonitorAnalyzer;
import com.ifelio.ijob.plugin.servermonitor.analyzer.CpuMonitorAnalyzer;
import com.ifelio.ijob.plugin.servermonitor.analyzer.DiskMonitorAnalyzer;
import com.ifelio.ijob.plugin.servermonitor.analyzer.MemoryMonitorAnalyzer;
import com.ifelio.ijob.plugin.servermonitor.analyzer.NetworkMonitorAnalyzer;
import com.ifelio.ijob.plugin.servermonitor.analyzer.ThresholdEvaluator;
import com.ifelio.ijob.plugin.servermonitor.config.MonitorCollectConfig;
import com.ifelio.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifelio.ijob.plugin.servermonitor.config.MonitorRuleConfig;
import com.ifelio.ijob.plugin.servermonitor.rule.ApplicationMonitorRule;
import com.ifelio.ijob.plugin.servermonitor.rule.CpuMonitorRule;
import com.ifelio.ijob.plugin.servermonitor.rule.DiskMonitorRule;
import com.ifelio.ijob.plugin.servermonitor.rule.MemoryMonitorRule;
import com.ifelio.ijob.plugin.servermonitor.rule.NetworkMonitorRule;
import com.ifelio.ijob.plugin.servermonitor.rule.Threshold;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class MonitorAnalyzerTest {
    private final ThresholdEvaluator evaluator = new ThresholdEvaluator();

    @Test
    void shouldAnalyzeEnabledMemoryDiskNetworkAndApplicationRules() {
        CpuMonitorRule cpu = CpuMonitorRule.builder().usage(threshold(50D, 80D)).build();
        cpu.setEnabled(false);
        MonitorConfig config =
                MonitorConfig.builder()
                        .collect(
                                MonitorCollectConfig.builder()
                                        .applications(
                                                List.of(
                                                        ApplicationCollectConfig.builder()
                                                                .name("worker")
                                                                .matchType(
                                                                        ProcessMatchType
                                                                                .PROCESS_NAME)
                                                                .matchValue("worker")
                                                                .required(true)
                                                                .allowMultiple(false)
                                                                .build()))
                                        .build())
                        .rules(
                                MonitorRuleConfig.builder()
                                        .cpu(cpu)
                                        .memory(
                                                MemoryMonitorRule.builder()
                                                        .usage(threshold(50D, 80D))
                                                        .build())
                                        .disk(
                                                DiskMonitorRule.builder()
                                                        .usage(threshold(70D, 90D))
                                                        .build())
                                        .network(
                                                NetworkMonitorRule.builder()
                                                        .receiveRate(threshold(100D, 200D))
                                                        .sendRate(threshold(200D, 300D))
                                                        .build())
                                        .applications(
                                                List.of(
                                                        ApplicationMonitorRule.builder()
                                                                .name("worker")
                                                                .required(true)
                                                                .build()))
                                        .build())
                        .build();
        MonitorReport report =
                MonitorReport.builder()
                        .cpu(CpuMetric.builder().usage(99D).build())
                        .memory(MemoryMetric.builder().usage(60D).build())
                        .disks(List.of(DiskMetric.builder().path("/").usage(40D).build()))
                        .networks(
                                List.of(
                                        NetworkMetric.builder()
                                                .name("eth0")
                                                .receiveRate(150L)
                                                .sendRate(350L)
                                                .build()))
                        .applications(
                                List.of(
                                        ApplicationMetric.builder()
                                                .name("worker")
                                                .found(false)
                                                .build()))
                        .build();

        MonitorCheckResult result = analyzer().analyze(report, config);

        assertFalse(
                result.getItems().stream().anyMatch(item -> item.getType() == MonitorItemType.CPU));
        assertTrue(
                result.getItems().stream().anyMatch(item -> item.getLevel() == AlarmLevel.WARNING));
        assertTrue(
                result.getItems().stream()
                        .anyMatch(
                                item ->
                                        item.getType() == MonitorItemType.NETWORK
                                                && item.getLevel() == AlarmLevel.CRITICAL));
        assertTrue(
                result.getItems().stream()
                        .anyMatch(
                                item ->
                                        item.getType() == MonitorItemType.APPLICATION
                                                && item.getLevel() == AlarmLevel.CRITICAL));
    }

    @Test
    void missingRulesShouldBeIgnoredWithoutNullPointer() {
        MonitorReport report =
                MonitorReport.builder()
                        .cpu(CpuMetric.builder().usage(1D).build())
                        .memory(MemoryMetric.builder().usage(1D).build())
                        .disks(List.of())
                        .networks(List.of())
                        .applications(List.of())
                        .build();

        MonitorCheckResult result =
                assertDoesNotThrow(
                        () -> analyzer().analyze(report, MonitorConfig.builder().build()));

        assertTrue(result.getItems().isEmpty());
    }

    @Test
    void optionalMissingApplicationShouldNotAlarm() {
        ApplicationMonitorRule rule =
                ApplicationMonitorRule.builder().name("optional").required(false).build();
        MonitorConfig config =
                MonitorConfig.builder()
                        .collect(
                                MonitorCollectConfig.builder()
                                        .applications(
                                                List.of(
                                                        ApplicationCollectConfig.builder()
                                                                .name("optional")
                                                                .matchType(
                                                                        ProcessMatchType
                                                                                .PROCESS_NAME)
                                                                .matchValue("optional")
                                                                .required(false)
                                                                .allowMultiple(false)
                                                                .build()))
                                        .build())
                        .rules(MonitorRuleConfig.builder().applications(List.of(rule)).build())
                        .build();
        MonitorReport report =
                MonitorReport.builder()
                        .applications(
                                List.of(
                                        ApplicationMetric.builder()
                                                .name("optional")
                                                .found(false)
                                                .build()))
                        .build();

        MonitorCheckResult result = new MonitorCheckResult();
        new ApplicationMonitorAnalyzer(evaluator).analyze(report, config, result);

        assertFalse(result.hasAlarm());
    }

    @Test
    void disallowedMultipleApplicationMatchesShouldBeCritical() {
        ApplicationCollectConfig collection =
                ApplicationCollectConfig.builder()
                        .name("worker")
                        .matchType(ProcessMatchType.PROCESS_NAME)
                        .matchValue("worker")
                        .required(true)
                        .allowMultiple(false)
                        .build();
        MonitorConfig config =
                MonitorConfig.builder()
                        .collect(
                                MonitorCollectConfig.builder()
                                        .applications(List.of(collection))
                                        .build())
                        .rules(
                                MonitorRuleConfig.builder()
                                        .applications(
                                                List.of(
                                                        ApplicationMonitorRule.builder()
                                                                .name("worker")
                                                                .required(true)
                                                                .build()))
                                        .build())
                        .build();
        MonitorReport report =
                MonitorReport.builder()
                        .applications(
                                List.of(
                                        ApplicationMetric.builder()
                                                .name("worker")
                                                .found(true)
                                                .processCount(2)
                                                .build()))
                        .build();

        MonitorCheckResult result = new MonitorCheckResult();
        new ApplicationMonitorAnalyzer(evaluator).analyze(report, config, result);

        assertEquals(1, result.getItems().size());
        assertEquals(AlarmLevel.CRITICAL, result.getItems().getFirst().getLevel());
    }

    @Test
    void thresholdBoundariesShouldPreferCritical() {
        Threshold threshold = threshold(70D, 90D);

        assertEquals(AlarmLevel.NORMAL, evaluator.evaluate(69.9D, threshold));
        assertEquals(AlarmLevel.WARNING, evaluator.evaluate(70D, threshold));
        assertEquals(AlarmLevel.CRITICAL, evaluator.evaluate(90D, threshold));
    }

    @Test
    void diskPathRuleShouldMatchWindowsPathVariants() {
        MonitorConfig config =
                MonitorConfig.builder()
                        .rules(
                                MonitorRuleConfig.builder()
                                        .disk(
                                                DiskMonitorRule.builder()
                                                        .usage(threshold(95D, 99D))
                                                        .paths(
                                                                Map.of(
                                                                        "c:\\Data\\",
                                                                        threshold(70D, 90D)))
                                                        .build())
                                        .build())
                        .build();
        MonitorReport report =
                MonitorReport.builder()
                        .disks(List.of(DiskMetric.builder().path("C:/data").usage(75D).build()))
                        .build();
        MonitorCheckResult result = new MonitorCheckResult();

        new DiskMonitorAnalyzer(evaluator).analyze(report, config, result);

        assertEquals(AlarmLevel.WARNING, result.getItems().getFirst().getLevel());
    }

    private CompositeMonitorAnalyzer analyzer() {
        return new CompositeMonitorAnalyzer(
                List.of(
                        new CpuMonitorAnalyzer(evaluator),
                        new MemoryMonitorAnalyzer(evaluator),
                        new DiskMonitorAnalyzer(evaluator),
                        new NetworkMonitorAnalyzer(evaluator),
                        new ApplicationMonitorAnalyzer(evaluator)));
    }

    private Threshold threshold(Double warning, Double critical) {
        return Threshold.builder().warning(warning).critical(critical).build();
    }
}
