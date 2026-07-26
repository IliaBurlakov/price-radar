# syntax=docker/dockerfile:1.7

FROM maven:3.9.12-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress -DskipTests package \
    && cp target/price-radar-*.jar application.jar

FROM eclipse-temurin:21-jre-alpine-3.23 AS runtime

RUN addgroup -S -g 10001 priceradar \
    && adduser -S -D -u 10001 -G priceradar -h /app priceradar

WORKDIR /app

COPY --from=build --chown=priceradar:priceradar /workspace/application.jar application.jar

USER priceradar:priceradar

EXPOSE 8080
STOPSIGNAL SIGTERM

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/application.jar"]
