# syntax=docker/dockerfile:1

# ---- build -----------------------------------------------------------------
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /build

# Dependency layer first so source edits do not re-download the world.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests \
    && cp target/backend-*.jar app.jar

# ---- runtime ---------------------------------------------------------------
FROM eclipse-temurin:21-jre-noble AS runtime

# FFmpeg assembles the multi-speaker listening audio and measures speaking
# delivery. ffprobe ships in the same package.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --uid 10001 app
WORKDIR /app

COPY --from=build --chown=app:app /build/app.jar /app/app.jar

USER app
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD ["/bin/sh", "-c", "curl -fsS http://localhost:8080/actuator/health || exit 1"]

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
