package com.ifelio.ijob.framework.config;

import com.ifelio.ijob.common.config.AppProperties;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
public class HttpClientConfig {
    private final AppProperties appProperties;

    public HttpClientConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        AppProperties.Http http = appProperties.getHttp();
        return builder.connectTimeout(http.getConnectTimeout())
                .readTimeout(http.getReadTimeout())
                .build();
    }
}
