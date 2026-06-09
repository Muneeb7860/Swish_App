# ─── Stage 1: build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# Cache the dependency layer separately from source code.
# This avoids re-downloading the internet on every source change.
COPY pom.xml .
RUN mvn -q dependency:resolve -DincludeScope=runtime

# Compile and package — skip tests (run by CI before this Docker build step)
COPY src ./src
RUN mvn -q clean package -DskipTests

# ─── Stage 2: runtime ────────────────────────────────────────────────────────
# eclipse-temurin JRE 17 on Alpine: ~200 MB vs ~500 MB for the full JDK image.
FROM eclipse-temurin:17-jre-alpine

# Non-root user (Cloud Run supports non-root containers)
RUN addgroup -S swish && adduser -S swish -G swish
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
RUN chown swish:swish app.jar
USER swish

# Cloud Run sets PORT=8080; application.properties uses ${PORT:8083}.
# docker-compose does not set PORT, so the app falls back to 8083 locally.
EXPOSE 8080

# JVM flags for containerised environments:
#   UseContainerSupport    — honour cgroup CPU/memory limits, not physical host
#   MaxRAMPercentage=75    — allow heap to grow to 75% of the container limit
#   ExitOnOutOfMemoryError — crash-fast so the orchestrator can restart cleanly
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", \
  "exec java $JAVA_OPTS \
   -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} \
   -jar app.jar"]
