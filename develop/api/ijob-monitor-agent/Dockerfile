# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

ARG MAVEN_IMAGE
ARG JRE_IMAGE
FROM ${MAVEN_IMAGE} AS build
WORKDIR /build
COPY ijob ./ijob
RUN --mount=type=cache,id=ijob-maven-v1,target=/root/.m2/repository \
    --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml \
    mvn -B -ntp -f ijob/pom.xml -pl ijob-common,ijob-contract -am -DskipTests install
COPY ijob-monitor-agent ./agent
RUN --mount=type=cache,id=ijob-maven-v1,target=/root/.m2/repository \
    --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml \
    mvn -B -ntp -f agent/pom.xml -Pquality -DskipTests package \
    && javac -d /health agent/tools/docker/AgentHealthProbe.java

FROM ${JRE_IMAGE} AS runtime
RUN groupadd --gid 10001 ijob \
    && useradd --uid 10001 --gid 10001 --no-create-home ijob
COPY --from=build /build/agent/target/ijob-monitor-agent-1.0.0.jar /opt/ijob/agent.jar
COPY --from=build /health /opt/ijob/health
USER 10001:10001
ENV SPRING_CONFIG_IMPORT=configtree:/run/secrets/ \
    SERVER_PORT=8081 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Duser.timezone=UTC"
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=6s --start-period=30s --retries=3 \
    CMD ["java", "-cp", "/opt/ijob/health", "AgentHealthProbe"]
ENTRYPOINT ["java", "-jar", "/opt/ijob/agent.jar"]
