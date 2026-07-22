# ---- Build stage -----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Cache dependencies separately from source so code-only changes don't re-download the world.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package && \
    mv target/realtime-chat-service.jar target/app.jar

# ---- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S chatapp && adduser -S chatapp -G chatapp
COPY --from=build /build/target/app.jar ./app.jar
RUN chown chatapp:chatapp /app/app.jar
USER chatapp

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
