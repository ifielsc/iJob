package com.ifelio.ijob.core.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.runtime.ApplicationRuntimeState;
import com.ifelio.ijob.core.trigger.TriggerService;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

class ScheduleScannerConditionTest {
    @Test
    void shouldNotRegisterScannerByDefault() {
        try (AnnotationConfigApplicationContext context = context(false)) {
            assertEquals(0, context.getBeansOfType(ScheduleScanner.class).size());
        }
    }

    @Test
    void shouldRegisterScannerWhenExplicitlyEnabled() {
        try (AnnotationConfigApplicationContext context = context(true)) {
            assertEquals(1, context.getBeansOfType(ScheduleScanner.class).size());
        }
    }

    private AnnotationConfigApplicationContext context(boolean enabled) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (enabled) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(
                            new MapPropertySource(
                                    "schedule-test", Map.of("app.schedule.enabled", "true")));
        }
        context.getBeanFactory()
                .registerSingleton(
                        "scheduleService", new ScheduleService(null, null, null, null, null));
        context.getBeanFactory()
                .registerSingleton(
                        "triggerService", new TriggerService(null, null, null, null, null));
        context.getBeanFactory()
                .registerSingleton("applicationRuntimeState", new ApplicationRuntimeState());
        context.getBeanFactory().registerSingleton("appProperties", new AppProperties());
        context.register(ScheduleScanner.class);
        context.refresh();
        return context;
    }
}
