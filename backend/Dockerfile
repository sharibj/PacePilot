# syntax=docker/dockerfile:1

# ---- dev: hot reload via bootRun, source mounted by compose ----
FROM eclipse-temurin:21-jdk-alpine AS dev
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies || true
COPY . .
EXPOSE 8080
CMD ["./gradlew", "--no-daemon", "bootRun"]

# ---- build: produce the executable jar ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- prod: slim JRE runtime ----
FROM eclipse-temurin:21-jre-alpine AS prod
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
