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
RUN addgroup -S timeline && adduser -S timeline -G timeline
COPY --from=build /src/app.jar app.jar
USER timeline
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
