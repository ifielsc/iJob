package com.ifelio.ijob.framework.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.config.AppProperties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

class HttpClientConfigTest {
    @Test
    void shouldBuildJsonCapableRestTemplateFromConfiguredTimeouts() {
        AppProperties properties = new AppProperties();
        properties.getHttp().setConnectTimeout(Duration.ofSeconds(2));
        properties.getHttp().setReadTimeout(Duration.ofSeconds(7));
        HttpClientConfig config = new HttpClientConfig(properties);

        RestTemplate restTemplate = config.restTemplate(new RestTemplateBuilder());

        assertEquals(Duration.ofSeconds(2), properties.getHttp().getConnectTimeout());
        assertEquals(Duration.ofSeconds(7), properties.getHttp().getReadTimeout());
        assertTrue(
                restTemplate.getMessageConverters().stream()
                        .anyMatch(MappingJackson2HttpMessageConverter.class::isInstance));
    }
}
