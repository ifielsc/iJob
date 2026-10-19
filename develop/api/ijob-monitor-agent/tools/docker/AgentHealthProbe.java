// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public final class AgentHealthProbe {
    public static void main(String[] args) {
        try {
            String token = Files.readString(Path.of("/run/secrets/agent.token")).strip();
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            var request =
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:8081/api/monitor/ping"))
                            .header("Authorization", "Bearer " + token)
                            .timeout(Duration.ofSeconds(3))
                            .GET()
                            .build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            System.exit(status == 200 ? 0 : 1);
        } catch (Exception ignored) {
            System.exit(1);
        }
    }
}
