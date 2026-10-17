# syntax=docker/dockerfile:1

# --- Build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -q -DskipTests package

# --- Runtime stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --no-create-home --shell /usr/sbin/nologin nexus
COPY --from=build /workspace/target/nexus-*.jar app.jar
USER nexus

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
