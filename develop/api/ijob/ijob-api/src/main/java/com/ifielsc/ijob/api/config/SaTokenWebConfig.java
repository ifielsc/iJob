package com.ifielsc.ijob.api.config;

import com.ifielsc.ijob.api.security.ApiSecurityInterceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenWebConfig implements WebMvcConfigurer {
    private final ApiSecurityInterceptor security;

    public SaTokenWebConfig(ApiSecurityInterceptor security) {
        this.security = security;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(security).addPathPatterns("/api/**");
    }
}
