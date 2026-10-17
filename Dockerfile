# syntax=docker/dockerfile:1.7
# One image: Spring Boot serves the API and the Angular build from the same origin.
# Dependency manifests are copied before sources so code changes don't re-download npm/Maven dependencies.
# Tests are not run here; CI runs them before Render deploys (autoDeployTrigger: checksPass).

# --- 1. Angular production build ---------------------------------------------------------------
FROM node:22-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npx ng build --configuration production

# --- 2. Spring Boot jar with the Angular build inside -------------------------------------------
# Official Gradle image pinned to the wrapper's version (gradle-wrapper.properties), so no distribution download
# at build time and the same Gradle as local and CI builds.
FROM gradle:9.8.1-jdk21 AS backend
WORKDIR /app/backend
COPY backend/settings.gradle backend/build.gradle ./
RUN gradle --no-daemon --quiet dependencies > /dev/null
COPY backend/src ./src
COPY --from=frontend /app/frontend/dist/frontend/browser ./src/main/resources/static
RUN gradle --no-daemon --quiet bootJar -x test \
    && cp build/libs/salary-*-SNAPSHOT.jar /app/app.jar

# --- 3. Small runtime image ---------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app && mkdir /data && chown app:app /data
WORKDIR /app
COPY --from=backend /app/app.jar app.jar
USER app

# SQLite lives in /data. On Render's free tier the disk is ephemeral: the demo re-seeds 10k employees on start.
# JVM sized from the container limit (512 MB on Render free); SerialGC has the smallest footprint.
ENV SALARY_DB_PATH=/data/salary.db \
    SEED_ENABLED=true \
    PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Xss512k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
