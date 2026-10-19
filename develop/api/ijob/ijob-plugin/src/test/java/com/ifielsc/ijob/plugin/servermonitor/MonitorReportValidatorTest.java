package com.ifielsc.ijob.plugin.servermonitor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.contract.monitor.AgentInfo;
import com.ifielsc.ijob.contract.monitor.CollectType;
import com.ifielsc.ijob.contract.monitor.MonitorCollectRequest;
import com.ifielsc.ijob.contract.monitor.MonitorReport;
import com.ifielsc.ijob.contract.monitor.OSType;
import com.ifielsc.ijob.contract.monitor.application.ApplicationCollectConfig;
import com.ifielsc.ijob.contract.monitor.application.ApplicationMetric;
import com.ifielsc.ijob.contract.monitor.metric.CpuMetric;
import com.ifielsc.ijob.contract.monitor.metric.DiskMetric;
import com.ifielsc.ijob.contract.monitor.metric.NetworkMetric;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorRuleConfig;
import com.ifielsc.ijob.plugin.servermonitor.rule.ApplicationMonitorRule;
import com.ifielsc.ijob.plugin.servermonitor.rule.NetworkMonitorRule;
import com.ifielsc.ijob.plugin.servermonitor.rule.Threshold;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

class MonitorReportValidatorTest {
    private final MonitorReportValidator validator = new MonitorReportValidator();

    @Test
    void configuredMountMustBeReportedUnlessRuleIsDisabled() {
        MonitorConfig config = config();
        var diskRule = new com.ifielsc.ijob.plugin.servermonitor.rule.DiskMonitorRule();
        diskRule.setPaths(java.util.Map.of("C:/data/", Threshold.builder().warning(80D).build()));
        config.getRules().setDisk(diskRule);
        MonitorReport missing =
                report().disks(List.of(DiskMetric.builder().path("D:\\").usage(20D).build()))
                        .build();
        assertThrows(
                IllegalStateException.class,
                () -> validator.validate(missing, request(CollectType.DISK), config));
        MonitorReport present =
                report().disks(List.of(DiskMetric.builder().path("c:\\Data").usage(20D).build()))
                        .build();
        assertDoesNotThrow(() -> validator.validate(present, request(CollectType.DISK), config));
        diskRule.setEnabled(false);
        assertDoesNotThrow(() -> validator.validate(missing, request(CollectType.DISK), config));
    }

    @Test
    void validCpuReportShouldPass() {
        MonitorReport report = report().cpu(CpuMetric.builder().usage(42D).build()).build();

        assertDoesNotThrow(() -> validator.validate(report, request(CollectType.CPU), config()));
    }

    @Test
    void failedReportShouldNotExposeAgentMessage() {
        MonitorReport report =
                MonitorReport.builder().success(false).message("token=secret").build();

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> validator.validate(report, request(CollectType.CPU), config()));

        assertFalse(exception.getMessage().contains("secret"));
    }

    @Test
    void missingOrInvalidCpuMetricShouldFail() {
        assertThrows(
                IllegalStateException.class,
                () -> validator.validate(report().build(), request(CollectType.CPU), config()));

        MonitorReport invalid = report().cpu(CpuMetric.builder().usage(101D).build()).build();
        assertThrows(
                IllegalStateException.class,
                () -> validator.validate(invalid, request(CollectType.CPU), config()));
    }

    @Test
    void duplicateDiskOrInvalidNetworkMetricShouldFail() {
        DiskMetric disk = DiskMetric.builder().path("/").usage(20D).build();
        MonitorReport duplicateDisk = report().disks(List.of(disk, disk)).build();
        assertThrows(
                IllegalStateException.class,
                () -> validator.validate(duplicateDisk, request(CollectType.DISK), config()));

        MonitorReport invalidNetwork =
                report().networks(
                                List.of(
                                        NetworkMetric.builder()
                                                .name("eth0")
                                                .receiveRate(-1L)
                                                .build()))
                        .build();
        assertThrows(
                IllegalStateException.class,
                () -> validator.validate(invalidNetwork, request(CollectType.NETWORK), config()));

        MonitorConfig enabledNetwork = config();
        enabledNetwork
                .getRules()
                .setNetwork(
                        NetworkMonitorRule.builder()
                                .receiveRate(Threshold.builder().warning(1D).build())
                                .build());
        assertThrows(
                IllegalStateException.class,
                () ->
                        validator.validate(
                                report().build(), request(CollectType.NETWORK), enabledNetwork));
    }

    @Test
    void requestedApplicationMustBePresentWithRequiredMetrics() {
        ApplicationCollectConfig collection =
                ApplicationCollectConfig.builder().name("worker").build();
        MonitorCollectRequest request =
                MonitorCollectRequest.builder()
                        .collectItems(List.of(CollectType.APPLICATION))
                        .applications(List.of(collection))
                        .build();
        ApplicationMonitorRule rule =
                ApplicationMonitorRule.builder()
                        .name("worker")
                        .required(true)
                        .cpu(Threshold.builder().critical(90D).build())
                        .build();
        MonitorConfig config =
                MonitorConfig.builder()
                        .rules(MonitorRuleConfig.builder().applications(List.of(rule)).build())
                        .build();

        assertThrows(
                IllegalStateException.class,
                () -> validator.validate(report().build(), request, config));

        MonitorReport missingCpu =
                report().applications(
                                List.of(
                                        ApplicationMetric.builder()
                                                .name("worker")
                                                .found(true)
                                                .build()))
                        .build();
        assertThrows(
                IllegalStateException.class, () -> validator.validate(missingCpu, request, config));
    }

    @Test
    void missingOrStaleMetadataShouldFail() {
        MonitorReport missing =
                MonitorReport.builder()
                        .success(true)
                        .cpu(CpuMetric.builder().usage(20D).build())
                        .build();
        MonitorReport stale =
                report().collectTime(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(6))
                        .cpu(CpuMetric.builder().usage(20D).build())
                        .build();

        assertThrows(
                IllegalStateException.class,
                () -> validator.validate(missing, request(CollectType.CPU), config()));
        assertThrows(
                IllegalStateException.class,
                () -> validator.validate(stale, request(CollectType.CPU), config()));
    }

    @Test
    void oversizedMetricListsShouldFail() {
        List<DiskMetric> disks =
                java.util.stream.IntStream.range(0, 257)
                        .mapToObj(
                                index ->
                                        DiskMetric.builder()
                                                .path("/disk-" + index)
                                                .usage(20D)
                                                .build())
                        .toList();

        assertThrows(
                IllegalStateException.class,
                () ->
                        validator.validate(
                                report().disks(disks).build(),
                                request(CollectType.DISK),
                                config()));
    }

    @Test
    void missingRequestShouldFailExplicitly() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> validator.validate(report().build(), null, config()));

        assertEquals("monitor collect request is required", exception.getMessage());
    }

    @Test
    void equivalentWindowsDiskPathsShouldBeDuplicates() {
        MonitorReport duplicate =
                report().disks(
                                List.of(
                                        DiskMetric.builder().path("C:\\Data\\").usage(20D).build(),
                                        DiskMetric.builder().path("c:/data").usage(21D).build()))
                        .build();

        assertThrows(
                IllegalStateException.class,
                () -> validator.validate(duplicate, request(CollectType.DISK), config()));
    }

    private MonitorReport.MonitorReportBuilder report() {
        return MonitorReport.builder()
                .success(true)
                .agent(
                        AgentInfo.builder()
                                .version("1.0.0")
                                .hostname("test-host")
                                .osType(OSType.LINUX)
                                .startTime(LocalDateTime.now())
                                .build())
                .collectTime(LocalDateTime.now(ZoneOffset.UTC))
                .elapsed(10L);
    }

    private MonitorCollectRequest request(CollectType type) {
        return MonitorCollectRequest.builder().collectItems(List.of(type)).build();
    }

    private MonitorConfig config() {
        return MonitorConfig.builder().rules(MonitorRuleConfig.builder().build()).build();
    }
}
