package com.ifelio.ijob.plugin.batchetl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.plugin.batchetl.config.BatchEtlTaskConfigValidator;
import com.ifelio.ijob.plugin.common.config.PluginConfigCodec;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

class BatchEtlTaskRunnerConditionTest {
    @Test
    void shouldNotRegisterRunnerWhenDisabled() {
        try (AnnotationConfigApplicationContext context = context(false)) {
            assertEquals(0, context.getBeansOfType(BatchEtlTaskRunner.class).size());
        }
    }

    @Test
    void shouldRegisterRunnerWhenExplicitlyEnabled() {
        try (AnnotationConfigApplicationContext context = context(true)) {
            assertEquals(1, context.getBeansOfType(BatchEtlTaskRunner.class).size());
        }
    }

    @Test
    void shouldRegisterRunnerByDefault() {
        try (AnnotationConfigApplicationContext context = context(null)) {
            assertEquals(1, context.getBeansOfType(BatchEtlTaskRunner.class).size());
        }
    }

    private AnnotationConfigApplicationContext context(Boolean enabled) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (enabled != null) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(
                            new MapPropertySource(
                                    "batch-etl-test",
                                    Map.of("app.plugin.batch-etl.enabled", enabled.toString())));
        }
        context.register(TestConfiguration.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    @Import(BatchEtlTaskRunner.class)
    static class TestConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        PluginConfigCodec pluginConfigCodec(ObjectMapper objectMapper) {
            return new PluginConfigCodec(objectMapper);
        }

        @Bean
        BatchEtlTaskConfigValidator validator() {
            return new BatchEtlTaskConfigValidator();
        }

        @Bean
        BatchEtlExecutor executor() {
            return new BatchEtlExecutor(null, null, null);
        }
    }
}
