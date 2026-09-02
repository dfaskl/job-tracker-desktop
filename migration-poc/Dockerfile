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
RUN mvn -B -DskipTests -Dfrontend.skip=true clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /workspace/backend/target/job-tracker-migration-poc.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
