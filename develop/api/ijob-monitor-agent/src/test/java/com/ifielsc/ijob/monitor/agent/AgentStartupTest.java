// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import javax.sql.DataSource;

class AgentStartupTest {
    @Test
    void standaloneAgentStartsWithAuthenticationAndWithoutDatabaseOrPlatformStarters()
            throws Exception {
        for (String type :
                new String[] {
                    "cn.dev33.satoken.stp.StpUtil",
                    "com.github.pagehelper.PageHelper",
                    "org.apache.ibatis.session.SqlSession"
                }) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(type));
        }
        String token = UUID.randomUUID().toString();
        try (var context =
                        new SpringApplicationBuilder(MonitorAgentApplication.class)
                                .run(
                                        "--spring.config.location=optional:classpath:/a22-isolated-no-config/",
                                        "--server.address=127.0.0.1",
                                        "--server.port=0",
                                        "--agent.hostname=a22-isolated-test",
                                        "--agent.token=" + token,
                                        "--spring.main.banner-mode=off",
                                        "--logging.level.root=ERROR");
                var client =
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
            assertTrue(
                    context.getBeansOfType(org.springframework.web.servlet.view.xslt.XsltView.class)
                            .isEmpty());
            assertTrue(
                    context.getBeansOfType(
                                    org.springframework.web.servlet.view.xslt.XsltViewResolver
                                            .class)
                            .isEmpty());
            var mappings =
                    context.getBean(
                                    "requestMappingHandlerMapping",
                                    org.springframework.web.servlet.mvc.method.annotation
                                            .RequestMappingHandlerMapping.class)
                            .getHandlerMethods();
            long applicationRoutes = 0;
            for (var entry : mappings.entrySet()) {
                var method = entry.getValue();
                if (!method.getBeanType().getName().startsWith("com.ifielsc.ijob.")) continue;
                applicationRoutes++;
                assertTrue(
                        org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(
                                method.getBeanType(),
                                org.springframework.web.bind.annotation.RestController.class),
                        method.toString());
                String returns = method.getMethod().getGenericReturnType().getTypeName();
                assertFalse(
                        returns.contains("FragmentsRendering")
                                || returns.contains("SseEmitter")
                                || returns.contains("ServerSentEvent")
                                || returns.contains("ModelAndView"),
                        method.toString());
                assertFalse(
                        entry.getKey().getProducesCondition().getProducibleMediaTypes().stream()
                                .anyMatch(
                                        type ->
                                                type.isCompatibleWith(
                                                        org.springframework.http.MediaType
                                                                .TEXT_EVENT_STREAM)),
                        method.toString());
            }
            assertTrue(applicationRoutes > 0);
            int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
            URI ping = URI.create("http://127.0.0.1:" + port + "/api/monitor/ping");
            var anonymous =
                    client.send(
                            HttpRequest.newBuilder(ping)
                                    .timeout(Duration.ofSeconds(5))
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(401, anonymous.statusCode());
            var authenticated =
                    client.send(
                            HttpRequest.newBuilder(ping)
                                    .timeout(Duration.ofSeconds(5))
                                    .header("Authorization", "Bearer " + token)
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(200, authenticated.statusCode());
        }
    }
}
