# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY modules modules
# Build only the ui app (and its deps: core). -am builds required modules.
RUN mvn -q -B -pl modules/ui -am -DskipTests package

# ---- runtime stage: JRE + X virtual display + noVNC ----
FROM eclipse-temurin:17-jre-jammy
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        xvfb x11vnc fluxbox x11-utils \
        novnc websockify \
        libgtk-3-0 libxext6 libxrender1 libxtst6 libxi6 libfreetype6 fontconfig fonts-dejavu \
        curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Docker CLI (client only) to provision sibling MySQL containers via the mounted host socket (DooD).
ARG DOCKER_CLI_VERSION=26.1.3
RUN curl -fsSL "https://download.docker.com/linux/static/stable/x86_64/docker-${DOCKER_CLI_VERSION}.tgz" -o /tmp/docker.tgz \
    && tar -xzf /tmp/docker.tgz -C /usr/local/bin --strip-components=1 docker/docker \
    && rm /tmp/docker.tgz \
    && docker --version

WORKDIR /app
COPY --from=build /src/modules/ui/target/sd-runner-app.jar /app/sd-runner-app.jar
COPY docker/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENV GEOMETRY=1280x800x24 \
    STATE_DIR=/app/state
EXPOSE 6080
CMD ["/entrypoint.sh"]
