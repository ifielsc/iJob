package com.ifelio.ijob.core.notify.channel.email;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

class EmailNotifyChannelConditionTest {
    @Test
    void shouldNotRegisterEmailChannelWhenEmailIsDisabled() {
        try (AnnotationConfigApplicationContext context = context(false)) {
            assertEquals(0, context.getBeansOfType(EmailNotifyChannel.class).size());
        }
    }

    @Test
    void shouldRegisterEmailChannelWhenEmailIsEnabled() {
        try (AnnotationConfigApplicationContext context = context(true)) {
            assertEquals(1, context.getBeansOfType(EmailNotifyChannel.class).size());
        }
    }

    private AnnotationConfigApplicationContext context(boolean enabled) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(
                            new MapPropertySource(
                                    "email-test",
                                    Map.of("app.notify.email.enabled", String.valueOf(enabled))));
        }
        context.register(TestConfiguration.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    @Import(EmailNotifyChannel.class)
    static class TestConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        MailChannelService mailChannels() {
            return MailChannelTestSupport.channel("sender@example.test");
        }
    }
}
