# Сборка выполняется внутри образа, поэтому локальный JDK для запуска не нужен.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY libs libs
COPY services services

ARG SERVICE
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :services:${SERVICE}:bootJar

RUN cp services/${SERVICE}/build/libs/*.jar /src/app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# JAR агента лежит в образе; подключение — только через entrypoint при
# OTEL_JAVAAGENT_ENABLED=true (см. docker-compose.observability.yml).
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.12.0/opentelemetry-javaagent.jar \
    /otel/opentelemetry-javaagent.jar
COPY deploy/docker-entrypoint.sh /app/docker-entrypoint.sh
RUN addgroup -S timeline && adduser -S timeline -G timeline \
    && chown -R timeline:timeline /otel /app/docker-entrypoint.sh \
    && chmod +x /app/docker-entrypoint.sh
COPY --from=build /src/app.jar app.jar
USER timeline
ENTRYPOINT ["/app/docker-entrypoint.sh"]
