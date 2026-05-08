# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM gradle:9.4.1-jdk21-alpine AS build

WORKDIR /app

# Copy dependency descriptors first for layer caching
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle

# Download dependencies - cached
RUN gradle dependencies --no-daemon --quiet || true

# Copy source and build the JAR
COPY src ./src
RUN gradle bootJar --no-daemon --quiet

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="Data Aggregation Service" \
      org.opencontainers.image.description="IoT Edge Data Aggregation Service" \
      org.opencontainers.image.version="1.0.0" \
      org.opencontainers.image.authors="Avijit Jana j_avijit1@yahoo.com"

WORKDIR /app

# Non-root user for security best practice
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build --chown=appuser:appgroup /app/build/libs/*.jar app.jar

USER appuser

# JVM tuning for container awareness And Added JST time
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Duser.timezone=JST", \
  "-jar", "app.jar"]