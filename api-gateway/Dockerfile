# ---------------------------------------------------------------------
# api-gateway container image (multi-stage build)
#   1. "build" stage: compiles the jar with the Maven wrapper
#   2. final stage:   runs the jar on a small JRE image as a non-root user
#
# Build:  docker build -t grocery/api-gateway:0.0.1 .
# Run against services on your machine:
#         docker run -p 8080:8080 -e REDIS_HOST=host.docker.internal \
#           -e AUTH_SERVICE_URL=http://host.docker.internal:8101 \
#           grocery/api-gateway:0.0.1
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

# "prod" switches off the relaxed limits of application-local.yml (the production values are in application.yml)
ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
