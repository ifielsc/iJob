package com.ifelio.ijob.plugin;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.plugin")
public class PluginAvailabilityProperties {
    private Switch event = Switch.enabled();
    private Switch script = Switch.enabled();
    private Switch sql = Switch.enabled();
    private Switch fileTransfer = Switch.enabled();
    private Switch apiCall = Switch.enabled();
    private Switch push = Switch.enabled();
    private Switch dataCheck = Switch.enabled();
    private Switch serverMonitor = Switch.enabled();
    private Switch etl = Switch.enabled();
    private Switch batchEtl = Switch.enabled();
    private Switch localJar = Switch.disabled();

    @Data
    public static class Switch {
        private boolean enabled;

        static Switch enabled() {
            Switch value = new Switch();
            value.setEnabled(true);
            return value;
        }

        static Switch disabled() {
            return new Switch();
        }
    }
}
