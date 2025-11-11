# Multi-stage build for sms-archive backend
# 1. Build stage
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Leverage Gradle wrapper; copy only necessary files first for dependency layer caching
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew

# Pre-fetch dependencies (will run tasks but skip tests)
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Copy source
COPY src src
COPY frontend frontend
COPY README.md README.md

# Build the boot jar (skip tests for faster image build; adjust if you want tests in pipeline)
RUN ./gradlew --no-daemon clean bootJar -x test

# 2. Runtime stage (slim JRE)
FROM eclipse-temurin:25-jre
ENV APP_HOME=/app \
    JAVA_OPTS="" \
    SPRING_DATASOURCE_URL="jdbc:postgresql://db:5432/sms_archive" \
    SPRING_DATASOURCE_USERNAME="sms_user" \
    SPRING_DATASOURCE_PASSWORD="sms_pass" \
    SPRING_PROFILES_ACTIVE="" \
    SMSARCHIVE_MEDIA_ROOT="/app/media/messages"
WORKDIR ${APP_HOME}

# Install wget + gosu (for privilege drop) before creating user
RUN apt-get update && apt-get install -y wget gosu && rm -rf /var/lib/apt/lists/*

# Copy the built jar
COPY --from=build /workspace/build/libs/*SNAPSHOT.jar app.jar

# Create dedicated group/user with stable UID/GID 10110 (avoid collisions)
RUN groupadd -r -g 10110 appgroup && \
    useradd -r -u 10110 -g appgroup appuser && \
    mkdir -p ${SMSARCHIVE_MEDIA_ROOT} && \
    chown -R appuser:appgroup ${APP_HOME}

# Copy entrypoint script (will fix media dir ownership then exec as appuser)
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod 0755 /usr/local/bin/docker-entrypoint.sh

# Expose HTTP port
EXPOSE 8080

# Healthcheck (basic)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
 CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run as root initially so entrypoint can adjust permissions, then gosu to appuser
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
