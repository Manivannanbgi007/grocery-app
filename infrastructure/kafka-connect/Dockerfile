# ---------------------------------------------------------------------
# Kafka Connect (Confluent) + the Debezium MySQL connector plugin
# + the Prometheus JMX exporter (metrics of the connectors for Prometheus).
#
# The Confluent image already contains the Avro converter that talks to
# Schema Registry; we only add Debezium and the metrics agent. Versions come
# from ../.env through docker-compose build args (the defaults below are fallbacks).
# ---------------------------------------------------------------------
ARG CONFLUENT_VERSION=8.2.4

# Stage 1: download and unpack the Debezium plugin, and download the JMX exporter agent.
# (The Confluent image has no 'tar', so we unpack in a small Alpine image.)
FROM alpine:3.22 AS downloads
ARG DEBEZIUM_VERSION=3.7.0.Final
ARG JMX_EXPORTER_VERSION=1.7.0
RUN mkdir /plugins /jmx \
 && wget -qO- "https://repo1.maven.org/maven2/io/debezium/debezium-connector-mysql/${DEBEZIUM_VERSION}/debezium-connector-mysql-${DEBEZIUM_VERSION}-plugin.tar.gz" \
    | tar -xz -C /plugins \
 && wget -qO /jmx/jmx_prometheus_javaagent.jar \
    "https://github.com/prometheus/jmx_exporter/releases/download/${JMX_EXPORTER_VERSION}/jmx_prometheus_javaagent-${JMX_EXPORTER_VERSION}.jar"

# Stage 2: Kafka Connect with the plugin copied into its plugin path, and the agent in /opt/jmx.
# docker-compose.yml mounts the agent's rules (jmx-exporter.yml) as /opt/jmx/connect.yml and starts the agent with
# KAFKA_OPTS=-javaagent:... (metrics on port 9404, Docker network only).
FROM confluentinc/cp-kafka-connect:${CONFLUENT_VERSION}
COPY --from=downloads --chown=appuser:appuser /plugins/ /usr/share/confluent-hub-components/
COPY --from=downloads --chown=appuser:appuser /jmx/jmx_prometheus_javaagent.jar /opt/jmx/jmx_prometheus_javaagent.jar
