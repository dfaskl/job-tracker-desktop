FROM node:22.22.0-alpine AS frontend
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-21 AS backend
WORKDIR /workspace/backend
COPY backend/pom.xml ./
COPY backend/src ./src
COPY --from=frontend /workspace/frontend/dist ../frontend/dist
RUN mvn -B -Dfrontend.skip=true clean verify

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 careerflow \
    && useradd --uid 10001 --gid 10001 --no-create-home careerflow
WORKDIR /app
RUN mkdir -p data backups logs && chown -R 10001:10001 /app
COPY --from=backend /workspace/backend/target/job-tracker.jar app.jar
ENV TZ=Asia/Shanghai \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Duser.timezone=Asia/Shanghai"
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=4 \
    CMD curl -fsS --max-time 2 http://127.0.0.1:8080/healthz || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
