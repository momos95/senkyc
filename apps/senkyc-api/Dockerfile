# Contexte de build : racine du monorepo (docker compose build senkyc-api)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY packages/senkyc-registry-spi packages/senkyc-registry-spi
COPY apps/senkyc-api apps/senkyc-api
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl apps/senkyc-api -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 senkyc && adduser -S -u 10001 -G senkyc senkyc
WORKDIR /app
COPY --from=build /workspace/apps/senkyc-api/target/senkyc-api.jar app.jar
USER 10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -q --spider http://127.0.0.1:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
