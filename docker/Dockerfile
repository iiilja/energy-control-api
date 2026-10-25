# Multi-stage Dockerfile for Energy Control API
# Stage 1: Build the application using Gradle
# Stage 2: Run the application using JRE

# ===========================================================================
# Build Stage
# ===========================================================================
FROM gradle:8.5-jdk17-alpine AS build

WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradle/ gradle/
COPY build.gradle .
COPY settings.gradle .

# Copy source code
COPY src/ src/

# Build the application (skip tests for faster builds)
RUN gradle build -x test --no-daemon

# ===========================================================================
# Runtime Stage
# ===========================================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Create logs directory
RUN mkdir -p /app/logs && chown spring:spring /app/logs

# Copy the built JAR from build stage
# --chown in COPY: a separate `RUN chown` would duplicate the whole jar in a new layer
COPY --from=build --chown=spring:spring /app/build/libs/*.jar app.jar

# Switch to non-root user
USER spring:spring

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
# Default JVM options, override with -e JAVA_OPTS=...
ENV JAVA_OPTS="-Xms256m -Xmx512m"

# Shell form so JAVA_OPTS is expanded; exec keeps java as PID 1 to receive SIGTERM (graceful shutdown).
# "$@" passes `docker run <image> <args>` through to the app.
# JAVA_OPTS is split on spaces without quote handling: use it for plain flags (-Xmx512m),
# put quoted values (-Dfoo="a b") into JAVA_TOOL_OPTIONS instead.
# JAVA_OPTS is on the command line, so its -Xmx wins over -Xmx from JAVA_TOOL_OPTIONS.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar \"$@\"", "--"]
