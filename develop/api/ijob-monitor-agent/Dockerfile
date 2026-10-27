# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

ARG MAVEN_IMAGE
ARG JRE_IMAGE
FROM ${MAVEN_IMAGE} AS build-base
ARG JDK_ARCHIVE_URL=https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz
ADD --checksum=sha256:d7f5d5cf865a2902b70ff549b97586a7d3f234e016f041ab447ea740c7d66d1c ${JDK_ARCHIVE_URL} /tmp/ijob-java.tar.gz
RUN rm -rf /opt/java/openjdk \
    && mkdir -p /opt/java/openjdk \
    && tar -xzf /tmp/ijob-java.tar.gz -C /opt/java/openjdk --strip-components=1 \
    && rm /tmp/ijob-java.tar.gz \
    && apt-get update \
    && apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/*
ENV JAVA_VERSION=jdk-21.0.13+9

ARG PLEXUS_UTILS_URL=https://repo.maven.apache.org/maven2/org/codehaus/plexus/plexus-utils/3.6.1/plexus-utils-3.6.1.jar
ADD --checksum=sha256:05a63effd67e2d6b9d610cc82e2bd7473289d34802e57a529b28110f28af5679 ${PLEXUS_UTILS_URL} /usr/share/maven/lib/plexus-utils-3.6.1.jar
RUN rm /usr/share/maven/lib/plexus-utils-3.6.0.jar

FROM build-base AS build
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

FROM ${JRE_IMAGE} AS runtime-base
ARG JRE_ARCHIVE_URL=https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jre_x64_linux_hotspot_21.0.13_9.tar.gz
ADD --checksum=sha256:a535a1df9c6284d9618872984e026ae0352d1f7e9e0605425c0e6114f47351dd ${JRE_ARCHIVE_URL} /tmp/ijob-java.tar.gz
RUN rm -rf /opt/java/openjdk \
    && mkdir -p /opt/java/openjdk \
    && tar -xzf /tmp/ijob-java.tar.gz -C /opt/java/openjdk --strip-components=1 \
    && rm /tmp/ijob-java.tar.gz \
    && apt-get update \
    && apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/*
ENV JAVA_VERSION=jdk-21.0.13+9

FROM runtime-base AS runtime
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
