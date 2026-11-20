# Build context is the monorepo root (see docker-compose.yml).
# ---- Build stage ----
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

COPY backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/gradle backend/gradle
COPY backend/gradlew backend/gradlew.bat ./
RUN chmod +x gradlew

COPY backend/src src
RUN ./gradlew --no-daemon buildFatJar -x test || ./gradlew --no-daemon fatJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -r -u 1001 aprax \
    && mkdir -p /opt/admin \
    && chown -R aprax /app /opt/admin

COPY --from=build --chown=aprax:aprax /app/build/libs/*.jar /app/app.jar
COPY --chown=aprax:aprax admin/frontend /opt/admin/frontend

USER aprax

ENV PORT=8080 \
    APP_ENV=production \
    ADMIN_DIR=/opt/admin/frontend

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -fsS http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
