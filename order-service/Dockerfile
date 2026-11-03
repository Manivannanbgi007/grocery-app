# ---------------------------------------------------------------------
# order-service container image (multi-stage build)
#   1. "build" stage: compiles the jar with the Maven wrapper
#   2. final stage:   runs the jar on a small JRE image as a non-root user
#
# Build:  docker build -t grocery/order-service:0.0.1 .
# Run against the local Docker infrastructure (local profile = same seed data as when run from the IDE):
#         docker run -p 8108:8080 -e SPRING_PROFILES_ACTIVE=local \
#           -e DB_URL="jdbc:mysql://host.docker.internal:3307/order_db?allowPublicKeyRetrieval=true&useSSL=false" \
#           -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9094 \
#           -e SCHEMA_REGISTRY_URL=http://host.docker.internal:8081 \
#           -e CART_SERVICE_URL=http://host.docker.internal:8107 \
#           -e CUSTOMER_SERVICE_URL=http://host.docker.internal:8102 \
#           -e STORE_SERVICE_URL=http://host.docker.internal:8103 \
#           grocery/order-service:0.0.1
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

# A fixed user id (10001), so the Kubernetes volume for the invoices can be given to it (fsGroup in the manifest).
# /app/invoices is where the invoice PDFs are stored (INVOICE_STORAGE_DIR); mount a volume there to keep them.
RUN groupadd --system --gid 10001 spring && useradd --system --uid 10001 --gid spring spring \
    && mkdir -p /app/invoices && chown spring:spring /app/invoices
USER spring

COPY --from=build /workspace/target/*.jar app.jar

# In containers the app always listens on 8080, and the "local" profile (seed data) is switched off.
ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    INVOICE_STORAGE_DIR=/app/invoices
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
