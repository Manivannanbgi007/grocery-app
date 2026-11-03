# ---------------------------------------------------------------------
# store-service container image (multi-stage build)
#   1. "build" stage: compiles the jar with the Maven wrapper
#   2. final stage:   runs the jar on a small JRE image as a non-root user
#
# Build:  docker build -t grocery/store-service:0.0.1 .
# Run against the local Docker infrastructure (local profile = same seed data as when run from the IDE):
#         docker run -p 8103:8080 -e SPRING_PROFILES_ACTIVE=local \
#           -e DB_URL="jdbc:mysql://host.docker.internal:3307/store_db?allowPublicKeyRetrieval=true&useSSL=false" \
#           -e SCHEMA_REGISTRY_URL=http://host.docker.internal:8081 \
#           grocery/store-service:0.0.1
# Tests are not run here (they need Docker for Testcontainers); run "mvnw verify" before building.
# ---------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Download dependencies first: this layer is cached until pom.xml changes.
# The Maven repository (/root/.m2) is a BuildKit cache shared by every service build,
# so each library is downloaded once for all 16 images, not once per image.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests


FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring
USER spring

COPY --from=build /workspace/target/*.jar app.jar

# In containers the app always listens on 8080, and the "local" profile (seed data) is switched off.
ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
