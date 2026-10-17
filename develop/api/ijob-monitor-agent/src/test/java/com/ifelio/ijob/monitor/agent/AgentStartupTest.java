package com.ifelio.ijob.monitor.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
