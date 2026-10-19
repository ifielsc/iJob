package com.ifielsc.ijob.plugin.etl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfigValidator;
import com.ifielsc.ijob.plugin.etl.incremental.EtlIncrementalCoordinator;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

class EtlTaskRunnerConditionTest {
    @Test
    void shouldNotRegisterRunnerWhenEtlIsDisabled() {
        try (AnnotationConfigApplicationContext context = context(false)) {
            assertEquals(0, context.getBeansOfType(EtlTaskRunner.class).size());
        }
    }

    @Test
    void shouldRegisterRunnerWhenEtlIsExplicitlyEnabled() {
        try (AnnotationConfigApplicationContext context = context(true)) {
            assertEquals(1, context.getBeansOfType(EtlTaskRunner.class).size());
        }
    }

    @Test
    void shouldRegisterRunnerByDefault() {
        try (AnnotationConfigApplicationContext context = context(null)) {
            assertEquals(1, context.getBeansOfType(EtlTaskRunner.class).size());
        }
    }

    private AnnotationConfigApplicationContext context(Boolean enabled) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (enabled != null) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(
                            new MapPropertySource(
                                    "etl-test",
                                    Map.of("app.plugin.etl.enabled", enabled.toString())));
        }
        context.register(TestConfiguration.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    @Import(EtlTaskRunner.class)
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
        EtlTaskConfigValidator etlTaskConfigValidator() {
            return new EtlTaskConfigValidator();
        }

        @Bean
        EtlPipelineFactory etlPipelineFactory() {
            return new EtlPipelineFactory(null, null, null, null);
        }

        @Bean
        com.ifielsc.ijob.plugin.etl.incremental.EtlFileIncrementalRunner fileIncrementalRunner() {
            return new com.ifielsc.ijob.plugin.etl.incremental.EtlFileIncrementalRunner(
                    null, null, null, null, null);
        }

        @Bean
        com.ifielsc.ijob.plugin.etl.message.EtlMessageRunner messageRunner() {
            return new com.ifielsc.ijob.plugin.etl.message.EtlMessageRunner(null, null, null, null);
        }

        @Bean
        EtlIncrementalCoordinator etlIncrementalCoordinator() {
            return new EtlIncrementalCoordinator(null, null);
        }
    }
}
