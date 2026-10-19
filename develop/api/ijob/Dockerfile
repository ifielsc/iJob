# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

ARG MAVEN_IMAGE
ARG JRE_IMAGE
FROM ${MAVEN_IMAGE} AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,id=ijob-maven-v1,target=/root/.m2/repository \
    --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml \
    mvn -B -ntp -Pquality -DskipTests package \
    && javac -d /health tools/docker/HealthProbe.java

FROM ${JRE_IMAGE} AS runtime
RUN groupadd --gid 10001 ijob \
    && useradd --uid 10001 --gid 10001 --no-create-home ijob \
    && mkdir -p /opt/ijob /var/lib/ijob \
    && chown 10001:10001 /var/lib/ijob
COPY --from=build /build/ijob-starter/target/ijob-starter-1.0.0.jar /opt/ijob/app.jar
COPY --from=build /build/ijob-setup-tool/target/ijob-setup-tool-1.0.0.jar /opt/ijob/setup.jar
COPY --from=build /health /opt/ijob/health
USER 10001:10001
WORKDIR /var/lib/ijob
ENV SPRING_PROFILES_ACTIVE=container \
    SPRING_CONFIG_ADDITIONAL_LOCATION=file:/run/secrets/ijob.yaml \
    IJOB_SERVER_PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Duser.timezone=UTC"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=6s --start-period=90s --retries=3 \
    CMD ["java", "-cp", "/opt/ijob/health", "HealthProbe", "http://127.0.0.1:8080/actuator/health/readiness"]
ENTRYPOINT ["java", "-jar", "/opt/ijob/app.jar"]
