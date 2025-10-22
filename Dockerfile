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
    DB_URL="jdbc:postgresql://db:5432/sms_archive" \
    DB_USER="sms_user" \
    DB_PASS="sms_pass" \
    SPRING_PROFILES_ACTIVE=""
WORKDIR ${APP_HOME}

# Copy the built jar
COPY --from=build /workspace/build/libs/*SNAPSHOT.jar app.jar

# Create a non-root user for security
RUN useradd -r -u 1001 appuser && chown -R appuser:appuser ${APP_HOME}
USER appuser

# Expose HTTP port
EXPOSE 8080

# Healthcheck (basic)
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=5 \
 CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
