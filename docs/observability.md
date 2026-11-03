# Observability: metrics, traces, logs, health, alerts

How to see what the platform is doing, and where to look when something goes wrong.

```text
16 services ── /actuator/prometheus (every 15 s) ──────────► Prometheus ── alert rules ──► Alertmanager ──► email (Mailpit)
     │      ── /actuator/health (via blackbox exporter) ──►      ▲
     │   Kafka broker ── consumer lag (kafka-exporter) ──────────┤
     │   Kafka Connect + Debezium ── JMX exporter agent ─────────┤
     │   MySQL, Redis, Elasticsearch ── one exporter each ───────┘
     ├───── spans (OTLP, http://localhost:4318) ─────────────► Jaeger
     └───── log lines (loki4j) ──────────────────────────────► Loki
                                                       Grafana reads all of them: dashboards, Explore, Alerting
```

| Question | Answered by |
|---|---|
| Is every service up and healthy? How many requests, errors, how fast? Memory, CPU, database pool, Kafka? | **Metrics**: Prometheus, Grafana dashboards |
| Are the events moving? How far behind is every consumer, do the outbox events reach Kafka? | **Metrics** from the broker and Debezium: [Events and Debezium](#events-and-debezium) |
| Are MySQL, Redis and Elasticsearch up? Connections, slow queries, memory, heap, disk? | **Metrics** from their exporters: [Data stores](#data-stores-mysql-redis-elasticsearch) |
| What happened to *this one request* across the gateway and the services, and where did the time go? | **Traces**: Jaeger |
| What exactly did a service do or report (errors, stack traces)? | **Logs**: Loki |
| Does someone need to act? | **Alerts**: Prometheus rules → Alertmanager → email |

## Start the monitoring tools

```powershell
.\scripts\start-infra.ps1 -Profiles monitoring     # Prometheus, Alertmanager, the exporters, Grafana, Loki, Jaeger
```

| Tool | URL | What for |
|---|---|---|
| Grafana | http://localhost:3000 (admin / see `infrastructure/.env`) | Dashboards **Grocery services**, **Grocery service details**, **Grocery events**, **Grocery infrastructure**, **Grocery logs**; Explore (Prometheus, Loki, Jaeger); Alerting |
| Jaeger | http://localhost:16686 | Traces: one request through the gateway and every service it called |
| Prometheus | http://localhost:9090 | Raw metrics (Graph), targets (Status → Targets), alert rules and their state (Alerts) |
| Alertmanager | http://localhost:9093 | Firing alerts, silences |
| Loki | http://localhost:3100 (no UI: use Grafana) | The log lines of all services, 7 days |
| Blackbox exporter | http://localhost:9115 | Checks `/actuator/health` of every service, and whether the Kafka broker accepts connections, for Prometheus |
| Kafka exporter | `kafka-exporter:9308` (Docker network only) | Consumer-group lag and topic offsets, read from the Kafka broker |
| Kafka Connect metrics | `kafka-connect:9404` (Docker network only) | Connector and task states, Debezium binlog connection (JMX exporter agent in the Connect image) |
| MySQL, Redis, Elasticsearch exporters | `mysqld-exporter:9104`, `redis-exporter:9121`, `elasticsearch-exporter:9114` (Docker network only) | Connections, slow queries, memory, keys, cluster health, heap, disk ([Data stores](#data-stores-mysql-redis-elasticsearch)) |
| Mailpit | http://localhost:8025 | The alert emails (and the emails notification-service sent) |
| Kafka UI | http://localhost:8090 | Topics, messages, consumer lag, dead-letter topics, connectors |

Metrics and health checks need nothing in the services. Traces and logs are **sent** by the services, so switch that
on where you start them (off by default, so nothing complains when the tools are not running):

```powershell
$env:TRACING_EXPORT_ENABLED = "true"   # spans to Jaeger
$env:LOKI_ENABLED = "true"             # log lines to Loki
.\mvnw spring-boot:run
```

## Logs: correlation id and trace id

Every log line has two ids:

```text
2026-10-26T10:15:30 INFO ... [3f2a9c1e-...] [69f1c2a8d3e4b5f6] c.g.o.s.i.CustomerOrderServiceImpl : Order id=18 placed
                              correlation id  trace id
```

- **Correlation id** (`X-Correlation-Id`): created by the gateway (or taken from the client), sent to every service and back in the response, also through `RestClient` calls (`CorrelationIdForwardingInterceptor`). Search all service logs for it to follow one user request.
- **Trace id**: from Micrometer Tracing with OpenTelemetry (`spring-boot-starter-opentelemetry`). The gateway and every service that handled the request share it (W3C `traceparent` header); in Jaeger you see them as one timeline ([Traces](#traces-jaeger)). **Kafka events carry it too**: the services that handle the events of a request log the same trace id ([Traces through Kafka](#traces-through-kafka)).

The gateway is reactive: one request runs on several threads, so a value in the MDC (a thread-local) would get lost.
`spring.reactor.context-propagation: auto` makes Reactor copy the trace id onto whichever thread runs the request,
and `CorrelationIdContextConfig` does the same for the correlation id (`CorrelationIdFilter` puts it into the request's
Reactor context). So the gateway's lines have both ids in the same place as every other service.

### Log format: text or JSON

| `LOG_FORMAT` (`app.logging.format`) | Console output | For |
|---|---|---|
| `text` (default) | Spring Boot's normal lines, as above | People: the IDE, a terminal |
| `json` | One JSON object per line in the [Elastic Common Schema](https://www.elastic.co/guide/en/ecs/current/) (ECS): `@timestamp`, `log.level`, `service.name`, `log.logger`, `message`, the MDC values (`correlationId`, `traceId`, `spanId`) and the key-value pairs (`kind`, `outcome`, `durationMs` ...) as fields, `error.*` with the stack trace | A log collector that reads the console (Kubernetes: Grafana Alloy, Fluent Bit, Elastic Agent): it gets fields instead of parsing text |

```json
{"@timestamp":"2026-10-28T06:12:03.511Z","log":{"level":"INFO","logger":"com.grocery.order.messaging.OutboxEventPublisher"},"process":{"pid":24368,"thread":{"name":"http-nio-8108-exec-3"}},"service":{"name":"order-service"},"message":"Outbox: saved OrderCreated eventId=... for order id=175","correlationId":"3f2a9c1e-...","traceId":"69f1c2a8d3e4b5f6...","spanId":"...","ecs":{"version":"8.11"}}
```

Both formats come from `logback-spring.xml` (Spring Boot's `StructuredLogEncoder` for JSON). The push to Loki
([below](#logs-in-grafana-loki)) is the same in both.

## Centralized logging with Spring AOP (`LoggingAspect`)

Every Spring MVC service has `logging/LoggingAspect.java` (the API gateway is reactive and logs each request in its
`RequestLoggingFilter` instead). It is the one place that logs **what happened and how long it took**, so controllers,
services and Kafka consumers need no "started / finished / failed" lines of their own.

| Kind | Which methods (package or annotation) | OK | SLOW | FAILED |
|---|---|---|---|---|
| `API` | classes in a `controller` package | INFO | WARN | WARN |
| `JOB` | methods with `@Scheduled` | INFO | WARN | WARN |
| `EVENT` | classes in `messaging.consumer` (Kafka) | DEBUG | WARN | WARN |
| `INTERNAL` | classes in `service` or `client`, and anything marked `@Loggable` | DEBUG | WARN | – (the API / JOB / EVENT line reports it) |

SLOW = longer than `app.logging.slow-call-threshold` (`LOG_SLOW_CALL_THRESHOLD`, 1 s; 60 s in mcp-client-service,
where a model answer takes seconds). Examples from cart-service:

```text
INFO ... LoggingAspect : API POST /api/v1/cart/items CartController.addItem OK in 35 ms
WARN ... LoggingAspect : SLOW INTERNAL ProductCatalogClient.getProduct took 1240 ms (limit 1000 ms)
WARN ... LoggingAspect : API POST /api/v1/cart/items CartController.addItem FAILED in 14 ms: CartStoreConflictException: Your cart has items from another store. ...
```

How it works, in three ideas:

- **Pointcut** = *where*: `within(com.grocery..controller..*)` means "every method of every class in a package called
  controller". The aspect matches the standard package names of this project, so new controllers, services and
  consumers are logged without any extra code.
- **Advice** = *what*: `@Around` runs the method (`call.proceed()`), measures it, writes one line, and passes the result
  or the exception on unchanged. The exception handler still decides the HTTP status; Kafka still retries.
- **Proxy**: Spring wraps each bean in a proxy that calls the advice. So only calls from *outside* the bean are seen,
  only public methods, and only Spring beans. A method calling another method of the same class is not logged again.

Rules (production):

- **Never** log method arguments or return values: they contain passwords, tokens, addresses and payment details.
  Write the ids you need yourself ("Order id=18 placed").
- Each line carries `kind`, `method`, `outcome` (`OK` / `SLOW` / `FAILED`) and `durationMs` as SLF4J key-value pairs:
  the console shows only the text; in Loki they are fields to filter and measure.
- Exceptions appear once as a short WARN line (class + message, max. 300 characters). The full stack trace of an
  unexpected error is logged by `GlobalExceptionHandler` (ERROR), by Spring Kafka, or by the scheduler.
- The aspect runs outermost (`@Order(HIGHEST_PRECEDENCE)`), so the time of a `@Transactional` service includes the commit.
- To log another bean, put `@Loggable` on the class or on a method. It is on the beans outside the standard packages
  that do slow or outside work: `RazorpayPaymentGateway` / `MockPaymentGateway` (payment), `EmailSender`
  (notification), `InvoicePdfGenerator` (order), `ProofPhotoStorage` and `DeliveryAssignmentService` (delivery).
- See the DEBUG lines locally: `logging.level.com.grocery: DEBUG` is set in every `application-local.yml`.

The aspect is copied into each service (like `CorrelationIdFilter`): the services build and deploy on their own, with no
shared library. `LoggingAspectTest` in each service checks it with small sample classes (`src/test/.../logging/sample`).

## Logs in Grafana Loki

With `LOKI_ENABLED=true`, every service **also** pushes its log lines to Loki (`logback-spring.xml`, the
[loki4j](https://loki4j.github.io/loki-logback-appender/) Logback appender). The console output stays exactly as before.

```powershell
.\scripts\start-infra.ps1 -Profiles monitoring   # Loki + Grafana (once)
$env:LOKI_ENABLED = "true"                       # in the terminal / IDE run configuration that starts a service
.\mvnw spring-boot:run
# Grafana http://localhost:3000 -> Dashboards -> Grocery -> Grocery logs, or Explore -> Loki
```

| Property (`application.yml`) | Env variable | Default | |
|---|---|---|---|
| `app.logging.loki.enabled` | `LOKI_ENABLED` | `false` | off: nothing complains when Loki is not running |
| `app.logging.loki.url` | `LOKI_URL` | `http://localhost:3100/loki/api/v1/push` | Kubernetes: `http://host.docker.internal:3100/...` (ConfigMap) |
| `app.logging.loki.environment` | `LOKI_ENVIRONMENT` | `local` | the `env` label: `local`, `kind`, `test`, `prod` ... |
| `app.logging.format` | `LOG_FORMAT` | `text` | console: `text` or `json` ([Log format](#log-format-text-or-json)) |
| `app.logging.slow-call-threshold` | `LOG_SLOW_CALL_THRESHOLD` | `1s` (`60s` in mcp-client-service) | for LoggingAspect |

What Loki receives for each line:

| | Values | Why |
|---|---|---|
| **Labels** | `app` (service name), `env`, `level` | Few different values: Loki keeps one stream per combination and finds them fast |
| **Structured metadata** | `logger`, `thread`, the MDC (`correlationId`, `traceId`, `spanId`) and the key-value pairs (`kind`, `method`, `outcome`, `durationMs`) | Many different values: stored with the line, filterable, but not indexed as streams |
| **Line** | `logger - message` + stack trace | What you read |

Production behaviour of the appender: lines are sent **in batches from a background thread** (every 2 s or 1000 lines),
so logging never waits for Loki. When Loki is slow or down, a batch is retried, then dropped; at most 10 MB wait in
memory, newer lines are dropped beyond that (the service never runs out of memory because of logging). On shutdown the
remaining lines are still sent. loki4j's own metrics (`loki4j_*`, e.g. dropped lines and failed requests) are in
`/actuator/prometheus`. Loki keeps the logs 7 days (`infrastructure/loki/loki-config.yml`).

Useful queries (Grafana → Explore → Loki, LogQL):

```logql
{app="order-service"}                                         # all lines of one service
{app=~".+", level="ERROR"}                                    # every error of every service
{app=~".+"} | correlationId="3f2a9c1e-..."                    # one user request through all services (the gateway too)
{app=~".+"} | traceId="69f1c2a8d3e4b5f6..."                   # one trace: the request AND the events it caused
{app="cart-service"} | outcome="FAILED"                       # failed API calls, events, jobs
{app=~".+"} | outcome="SLOW"                                  # slow calls everywhere
{app=~".+"} |= "orderId=18"                                   # every line mentioning order 18
quantile_over_time(0.95, {app=~".+"} | kind="API" | unwrap durationMs [5m]) by (app)   # API time per service
```

A line with a `traceId` has a link **Open trace in Jaeger** (open the line in Explore): logs and the trace of the same
request side by side. The dashboard **Grocery logs** has log volume and errors per service, the API time, failed and
slow calls, and a log list with filters (service, level, text, correlation id).

In production Loki runs as a cluster (or Grafana Cloud) with object storage; only `LOKI_URL` changes. In Kubernetes the
alternative is to collect `stdout` with Grafana Alloy (no appender in the services, `LOG_FORMAT=json`); the push
appender was chosen so the services on a developer machine and in the cluster send their logs the same way.

### Sensitive data in logs

Logs are kept for days, copied to other systems and read by many people. They must not contain **secrets** (passwords,
tokens, keys) or **personal data** (email addresses, phone numbers, addresses, card numbers). Rules:

- Log ids, not data: `Order id=18 placed`, not the order. `LoggingAspect` never logs arguments or return values.
- Where a value helps support, mask it: notification-service logs `sent to c***@grocery.com` (`EmailSender.maskEmails`,
  also applied to mail-server error messages, which often quote the address).
- The AI assistant logs a tool call as `Tool getMyOrders [] -> OK: 2 orders. (812 characters)`: the tool, the argument
  **names** and the result's status and message, never the result's data (orders, addresses, phone numbers) or what the
  customer typed (`RecordingToolCallback`).

Check the logs in Loki:

```powershell
.\scripts\check-log-secrets.ps1                # the last 24 hours; exit code 1 when something is found
.\scripts\check-log-secrets.ps1 -Since 30m     # only the last 30 minutes, e.g. after a fix
```

It counts, per service, the lines that look like an access token (JWT), an `Authorization: Bearer` header, a password
(`password=...`, and the local seed password), a secret or API key, a Razorpay key, a card number, an email address or
an Indian mobile number, and shows up to 3 of them with the match hidden. Numbers must stand alone, so the digits inside
ids and UUIDs do not count. Kafka's own configuration dump (`ssl.key.password = null`) is skipped: Kafka hides real
passwords there itself. The first run found two real problems, both fixed: notification-service logged every
recipient's email address, and the assistant logged the tools' full results (with phone numbers) at DEBUG.

## Metrics (Prometheus)

Every service exposes `/actuator/prometheus`: Spring Boot and Micrometer measure the service and its libraries
automatically, there is **no metric code** in the services (the gateway's rate-limit and fallback counters and
delivery-service's live-tracking metrics are the exceptions). Prometheus reads every service every 15 s and keeps 7 days.

| What | Metrics (Prometheus names) |
|---|---|
| Up / health | `up` (Prometheus could read the service), `probe_success` (its `/actuator/health` is UP, see [Health](#health)) |
| API requests: count, errors, response time | `http_server_requests_seconds_count`, `_sum`, `_bucket` (labels `uri`, `method`, `status`, `outcome`, `exception`) |
| Calls to other services | `http_client_requests_seconds_*` (RestClient; the gateway's forwarded requests per route) |
| Gateway routes, rate limits, fallbacks, circuit breakers | `spring_cloud_gateway_requests_seconds_*`, `gateway_rate_limited_total`, `gateway_fallback_total`, `resilience4j_circuitbreaker_state` |
| Database connection pool (HikariCP) | `hikaricp_connections_active`, `_idle`, `_pending`, `_max`, `_acquire_seconds_*` (waiting for a connection), `_timeout_total` |
| Database calls | `spring_data_repository_invocations_seconds_*` (per repository method) |
| JVM memory, garbage collection, threads | `jvm_memory_used_bytes`, `jvm_memory_max_bytes`, `jvm_gc_pause_seconds_*`, `jvm_threads_*` |
| CPU | `process_cpu_usage` (this service), `system_cpu_usage` (the machine) |
| Web threads | `tomcat_threads_busy_threads`, `_current_threads`, `_config_max_threads` (not the gateway: Netty) |
| Kafka (in the service) | `spring_kafka_listener_seconds_*` (events handled; `result="failure"` = failed attempt), `spring_kafka_template_seconds_count{name="deadLetterKafkaTemplate"}` (events moved to a `.DLT` topic), `kafka_consumer_coordinator_rebalance_total` (rebalances per consumer) |
| Kafka (from the broker, [kafka-exporter](#events-and-debezium)) | `kafka_consumergroup_lag` (events a group still has to read, labels `consumergroup`, `topic`, `partition`; `-1` = never committed there), `kafka_consumergroup_current_offset`, `kafka_consumergroup_members`, `kafka_topic_partition_current_offset` / `_oldest_offset` (messages on a topic, also the `.DLT` topics) |
| Kafka Connect and Debezium ([JMX exporter](#events-and-debezium)) | `kafka_connect_connector_status` / `_tasks` (state), `debezium_mysql_connected` (reading the binlog), `debezium_mysql_behind_source_milliseconds`, `kafka_connect_source_records_written_total` (events published), `kafka_connect_task_record_failures_total` |
| MySQL ([mysqld-exporter](#data-stores-mysql-redis-elasticsearch)) | `mysql_up`, `mysql_global_status_threads_connected` / `mysql_global_variables_max_connections`, `mysql_info_schema_processlist_processes_by_user` (connections per service user), `mysql_global_status_slow_queries` (queries over 1 s), `mysql_global_status_connection_errors_total` (label `error`, e.g. `max_connections`), `mysql_global_status_innodb_row_lock_waits` |
| Redis ([redis_exporter](#data-stores-mysql-redis-elasticsearch)) | `redis_up`, `redis_memory_used_bytes` / `redis_memory_max_bytes`, `redis_db_keys` / `_expiring`, `redis_evicted_keys_total`, `redis_rejected_connections_total`, `redis_connected_clients`, `redis_keyspace_hits_total` / `_misses_total` |
| Elasticsearch ([elasticsearch-exporter](#data-stores-mysql-redis-elasticsearch)) | `elasticsearch_cluster_health_status` (label `color`; missing = unreachable), `elasticsearch_jvm_memory_used_bytes` / `_max_bytes`, `elasticsearch_filesystem_data_available_bytes` / `_size_bytes`, `elasticsearch_thread_pool_rejected_count` (label `type`), `elasticsearch_indices_docs_primary` (per index) |
| Logs | `logback_events_total` (lines per level), `loki4j_*` (delivery to Loki) |
| Vendors' live orders ([order-service README](../order-service/README.md#live-order-updates-server-sent-events)) | `order_vendor_streams` (vendor panels following their store's orders right now, per instance) |
| Live delivery tracking ([delivery-tracking.md](delivery-tracking.md)) | `delivery_tracking_streams` (customers following a delivery right now, per instance), `delivery_tracking_location_updates_total` (partner positions, label `result`: `stored`, `skipped` = too soon or not newer, `refused` = too old, in the future, imprecise, or the delivery is not being tracked) |

A live stream (delivery tracking, the vendors' orders) stays open for minutes on purpose, so the response-time panels and
the SlowResponses alert leave out `.../stream` requests, and the gateway's latency SLO leaves out the routes named
`*-stream` (and the AI chat, whose answers take seconds by design): they would hide the real response times. A new live
stream keeps these names, so it is left out without changing a rule.

Two settings in every `application.yml` add what Spring Boot does not measure by default:

```yaml
management:
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true   # response-time buckets: 95th / 99th percentile per endpoint
server:
  tomcat:
    mbeanregistry:
      enabled: true                  # Tomcat's thread metrics
```

Percentiles come from the **histogram buckets** (`_bucket`): "how many requests took at most 5 ms, 10 ms, ... 30 s".
`histogram_quantile(0.95, ...)` in PromQL turns them into "95 % of the requests were faster than X", for any group of
services or endpoints. That is better than the average (one slow request hides in it) and the maximum (one outlier).

Where Prometheus finds the services: `infrastructure/prometheus/targets/services.yml` (a new service = one entry there;
Prometheus notices it by itself). Keep the job name `grocery-services` and the `service` label: the dashboards, the
alert rules and the website's System page (super admin) use them.

**Your own metric**, e.g. orders placed: inject `MeterRegistry` and count with a clear name and few label values:

```java
meterRegistry.counter("grocery.orders.placed", "payment", paymentMethod.name()).increment();   // -> grocery_orders_placed_total
```

Never use ids, emails or free text as label values: every different value is a new time series in Prometheus.

### Cardinality

Every combination of label values is a separate **time series**. One label that gets a new value per request (an id,
a user, a raw URL) turns one metric into millions of series: Prometheus gets slow and runs out of memory, for all
services at once. The classic mistake is a request metric with `uri="/api/v1/orders/174"` instead of the route template
`uri="/api/v1/orders/{orderId}"`. Spring fills `uri` with the template as long as the URL is built from a template:
`restClient.get().uri("/internal/orders/{id}", id)`, not `.uri("/internal/orders/" + id)`.

```powershell
.\scripts\check-metrics-cardinality.ps1      # exit code 1 when a limit is exceeded
```

It reads Prometheus' own statistics (`/api/v1/status/tsdb`) and shows the 10 metrics with the most series (limit
30,000 each), the labels with the most values (limit 1,000) and any request metric whose `uri` contains a number or a
UUID as a path segment. Today: about 36,000 series in total, the biggest metric `http_server_requests_seconds_bucket`
(about 13,000: histogram buckets × endpoints × status codes), `uri` about 100 values, no `uri` with an id. The alert
**TooManyMetricSeries** fires when Prometheus holds more than 200,000 series for 15 minutes.

## Dashboards (Grafana)

Grafana → Dashboards → folder **Grocery** (files in `infrastructure/grafana/provisioning/dashboards`, loaded on start and
re-read every 30 s):

| Dashboard | Shows |
|---|---|
| **Grocery services** | Every service at a glance: up / down / unhealthy, firing alerts, client requests and errors at the gateway; a table with one row per service (up, health, requests, errors, p95, heap, CPU, database pool, uptime; click a name for its details or its traces); traffic, response times, gateway routes and resilience, resources, Kafka, logs, firing alerts |
| **Grocery service details** | One service (pick it at the top): health, requests and response time per endpoint, the slowest endpoints, server errors with their exception, calls to other services, database pool and repository calls, heap, garbage collection, CPU, threads, Kafka (events, failures, lag), log levels. Links to its logs and its traces |
| **Grocery events** | Kafka and Debezium ([below](#events-and-debezium)): events waiting per consumer group, topic and partition, events handled and published per second, consumers per group, failed attempts, dead-letter topics, rebalances, connector states, Debezium's binlog connection and delay |
| **Grocery infrastructure** | MySQL, Redis and Elasticsearch ([below](#data-stores-mysql-redis-elasticsearch)): up or down, connections (also per service), failed connections, queries, slow queries, row lock waits; Redis memory, keys, clients, commands, keys found, evicted keys; Elasticsearch heap, searches and indexing, rejected requests, documents per index, disk |
| **Grocery logs** | Log volume and errors per service, API time from the logs, failed and slow calls, the log lines ([Logs in Grafana Loki](#logs-in-grafana-loki)) |
| **Grocery SLOs** | The two service level objectives over the last 7 days and the error budget left, the burn rates with the alert lines, server errors and slow requests per gateway route ([SLOs](#slos-service-level-objectives)) |

The dots on the response-time charts are **exemplars**: sample requests with their trace id (Prometheus keeps them
with `--enable-feature=exemplar-storage`). Hover over one and click **Open trace in Jaeger**: from "p95 went up" to the
trace of one slow request in two clicks.

To change a dashboard: edit it in Grafana, then **Export → JSON** and replace the file in the repository. Changes
saved only in Grafana are overwritten the next time the file is read.

## Events and Debezium

The order saga, the search index and the notifications all run on Kafka events, so "are the events moving?" is as
important as "is the service up?". Two places answer it:

```text
MySQL outbox row ──binlog──► Debezium (Kafka Connect) ──► topic ──► consumer group (= the service's name)
                             └ JMX exporter agent:              └ kafka-exporter asks the broker: published offset
                               connected? tasks running?          minus committed offset = lag, per partition
```

- **Consumer lag from the broker** (`kafka-exporter`, `docker-compose.yml`). The broker knows, for every consumer group,
  the last offset it committed, and for every partition the last offset written. The difference is the **lag**: events
  the group still has to handle. Read from the broker, it is still correct when the service is stopped, crashed or stuck.
  The services' own lag metric disappears exactly then, which is why the broker's view is used for the alerts.
  `--kafka.version=4.2.0` is needed: Kafka 4 refuses the exporter's old default protocol version. A lag of `-1` means
  the group never committed on that partition (no event arrived there yet); the queries count it as 0.
- **Debezium and Kafka Connect** (Prometheus JMX exporter agent 1.7.0 inside our Connect image,
  [kafka-connect/Dockerfile](../infrastructure/kafka-connect/Dockerfile), rules in
  [kafka-connect/jmx-exporter.yml](../infrastructure/kafka-connect/jmx-exporter.yml)): the state of every connector
  and task, whether Debezium is connected to the MySQL binlog (`server` label = the connector's `topic.prefix`), and how
  many records each connector wrote to Kafka.

**A connector can say RUNNING while publishing nothing.** Found while setting this up: MySQL ran out of connections
("Too many connections"); three connectors kept retrying every 10 s, their tasks still showed RUNNING, and their
Debezium metrics simply disappeared. So the alert **DebeziumNotReadingBinlog** compares two counts: running connector
tasks and connectors connected to the binlog. Nothing is lost while it fires: the outbox rows wait in MySQL and are
published once Debezium reads again (the binlog is kept 7 days).

**Alert thresholds come from who is waiting**, not from a round number. *KafkaConsumerNoProgress* = events are waiting
and the group committed nothing for its time limit (label `max_wait`):

| Limit | Severity | Consumer groups | Why |
|---|---|---|---|
| 2 min | critical | inventory, payment, order, cart, delivery, customer, auth | A customer or vendor waits on screen: the order saga, a new customer's profile, the store link in a vendor's token |
| 5 min | warning | notification, product-read | Nobody waits on screen, but late emails or old prices/stock in search are noticed soon |
| 15 min | warning | report, platform, any new group | Reports and the audit log may be minutes old |

*KafkaConsumerLagGrowing* covers a group that works, but slower than events arrive: more than 100 waiting and still
growing for 10 minutes. 100 = the expected peak (about 20 events per minute on one topic) × the delay we accept
(5 minutes); normal lag is 0-2. When the expected traffic changes, change it: *peak events per minute × minutes of
delay*. Each limit is one line in [grocery-alerts.yml](../infrastructure/prometheus/alerts/grocery-alerts.yml).

The alerts carry the `consumergroup`, `topic` and `service` labels. A stopped service also fires ServiceDown, and
Alertmanager then holds back its lag alerts (inhibit rule): one email says what is wrong. A service that runs but
does not commit gets the lag alert itself.

Check by hand: Grafana → **Grocery events**; Kafka UI → Consumers; `GET http://localhost:8083/connectors?expand=status`.
The exporters have no port on your machine (only Prometheus needs them); to look at their raw metrics:
`docker run --rm --network grocery_default curlimages/curl:8.16.0 -s http://kafka-exporter:9308/metrics`
(or `kafka-connect:9404`).

## Data stores: MySQL, Redis, Elasticsearch

Every service depends on MySQL, the gateway and delivery-service on Redis, and the shop's search on Elasticsearch. Each
store gets one **exporter** (`docker-compose.yml`, profile `monitoring`): a small program that asks the store for its
statistics and shows them to Prometheus. The exporters have no port on your machine, and they get only the rights
they need:

| Exporter | Version (`.env`) | Logs in as | Reads |
|---|---|---|---|
| `prom/mysqld-exporter` | 0.20.0 (0.17.2+ knows MySQL 8.4) | MySQL user `exporter`: `PROCESS`, `REPLICATION CLIENT`, `SELECT` on `performance_schema` only, **at most 3 connections**; no access to any service's data ([init script](../infrastructure/mysql/init/01-create-databases-and-users.sql), password `MYSQL_EXPORTER_PASSWORD`) | Status and variables, connections per user (= per service, every service has its own user) |
| `oliver006/redis_exporter` | 1.93.0 | no login yet (Redis has no users locally) | `INFO`: memory, keys, clients, evictions, commands |
| `prometheuscommunity/elasticsearch-exporter` | 1.11.0 (needs `--es.uri` since 1.10) | no login (security is off locally) | Cluster health, node stats (heap, disk, thread pools), per-index stats (`--es.indices`) |

`start-infra.ps1` runs the MySQL init script on every start, so an existing MySQL volume gets the `exporter` user too.

**MySQL's slow queries.** MySQL counts a query as slow above `long_query_time`, 10 s by default, which practically
never happens. It is set to **1 s** (`docker-compose.yml`), and the slow log is on, so every slow query is counted
(MySQLSlowQueries) and written with its text and duration:
`docker exec grocery-mysql tail -n 40 /var/lib/mysql/slow.log`. Then `EXPLAIN` the query: usually an index is missing.
The alert wants slow queries that **keep coming** (more than 2 per 5 minutes, for 15 minutes), not a burst: see the
Debezium finding below.

**Connections.** `max_connections` is 300 (13 services × a pool of 10, 8 Debezium connectors, the exporter, tools).
*MySQLConnectionsNearLimit* warns above 80%; *Grocery infrastructure → Connections per service* shows who holds them.
At 100% MySQL answers "Too many connections": services cannot start, and Debezium stops reading the binlog (that
happened once, see [Events and Debezium](#events-and-debezium)).

**Found while setting this up** (measured on the local stack):

- **After a MySQL restart Debezium needs about 80 s to read the binlog again** (it retries by itself). Until then the
  connectors still say RUNNING and *DebeziumNotReadingBinlog* is pending; its 3-minute limit means a short restart
  sends no email.
- **MySQL counts every Debezium binlog connection that ends as a slow query.** The connection "runs" for as long as
  the connector reads, so when Kafka Connect or Kafka restarts, the counter jumps by 8 (one per connector), while the
  slow log stays empty: such replication commands are not logged. Measured in Phase 19c: 58 counted, 9 in the slow log,
  49 finished `Binlog Dump GTID` commands in `performance_schema`; restarting one connector added exactly 1. The first
  version of *MySQLSlowQueries* (more than 5 in 10 minutes) fired after the failure drills because of this, so the
  alert now needs slow queries that keep coming. The 9 real ones came from a busy minute (13 test suites with their own
  MySQL containers plus service restarts): even `COMMIT` took up to 6 s; a burst like that is not an alert either.
- **Elasticsearch counts the old event versions product-read-service skips as failed writes.** product-read-service
  sends every change with its version (`version_type=external`), so a duplicate or older event is refused as a version
  conflict, on purpose. Elasticsearch counts that refusal in `index_failed`, and the exporter cannot tell the two
  apart. So there is **no alert on Elasticsearch's failed writes**; the panel shows them for information. A real
  indexing error fails product-read-service's listener and ends in its dead-letter topic, which
  *KafkaEventsMovedToDeadLetterTopic* reports.
- **Elasticsearch's heap goes up and down** between about 30% and 80% with every garbage collection. *ElasticsearchHeapHigh*
  needs 15 minutes **without a drop** below 85%, so the saw-tooth never fires it.
- **The exporter has no "Elasticsearch is up" value.** When Elasticsearch cannot be reached, the cluster health
  values are simply missing. *ElasticsearchDown* = the exporter answers, but there is no cluster health.
- **Redis keeps state here, not a cache**: rate-limit counters, partner presence and locations. A key that is not found
  is normal (a new client has no counter yet, an offline partner has no presence key), so "reads that found their key"
  is a panel, not an alert. Losing keys is what matters: *RedisEvictingKeys*. Redis has no memory limit yet
  (`maxmemory 0`), so *RedisMemoryHigh* stays silent until one is set.

**One email for the cause.** When a store is down, every service that uses it reports itself unhealthy, answers with
errors and logs errors. Alertmanager holds those symptoms back while *MySQLDown*, *RedisDown* or *ElasticsearchDown*
fires ([alertmanager.yml](../infrastructure/alertmanager/alertmanager.yml)): MySQL for every service; Redis for the
gateway and delivery-service; Elasticsearch for product-read-service. If an **exporter** is down, *MonitoringToolDown*
fires instead: the store may be fine, we just cannot see it.

Look at the raw metrics: `docker exec grocery-prometheus wget -qO- http://mysqld-exporter:9104/metrics`
(or `redis-exporter:9121`, `elasticsearch-exporter:9114`).

## Traces (Jaeger)

A **trace** is one request on its way through the platform; each step is a **span** (the gateway forwarding, a
service's endpoint, a Redis call, a call to another service) with its duration. In Jaeger you see where the time went
and which service failed.

```text
api-gateway   http post                              236 ms
  api-gateway   evalsha  (rate limit in Redis)         3 ms
  api-gateway   HTTP POST (forward to auth-service)  155 ms
    auth-service  http post /api/v1/auth/login        84 ms
```

How it works: each service runs Micrometer Tracing with the OpenTelemetry bridge (`spring-boot-starter-opentelemetry`).
The gateway starts a trace (or continues the client's), passes it on in the W3C `traceparent` header, and every service
adds its spans and sends them in batches (every 5 s) to Jaeger over OTLP (`http://localhost:4318/v1/traces`). The
trace id is also in every log line ([above](#logs-correlation-id-and-trace-id)).

| Property (`application.yml`) | Env variable | Default | |
|---|---|---|---|
| `management.tracing.export.otlp.enabled` | `TRACING_EXPORT_ENABLED` | `false` | send spans (trace ids exist in the logs either way) |
| `management.opentelemetry.tracing.export.otlp.endpoint` | `OTLP_TRACING_ENDPOINT` | `http://localhost:4318/v1/traces` | Kubernetes: `host.docker.internal` (ConfigMap) |
| `management.tracing.sampling.probability` | `TRACING_SAMPLING_PROBABILITY` | `1.0` | share of requests traced; production e.g. `0.1` |

Use it: open http://localhost:16686, pick a service, **Find Traces**. Or from Grafana: a log line's **Open trace in
Jaeger**, an exemplar dot, or Explore → Jaeger. In Grafana a span (click its bar) has **Related logs** (every log line of the
trace, from all services) and the request rate / p95 of its service.

Configuration ([infrastructure/jaeger/jaeger-config.yml](../infrastructure/jaeger/jaeger-config.yml)):

- Traces are kept **in memory** (at most 50,000; lost when Jaeger restarts). Production: a persistent storage
  (Elasticsearch/OpenSearch, Cassandra, ClickHouse).
- **Monitoring noise is dropped**: Prometheus and the health checks call `/actuator/*` of every service every 15 s.
  Jaeger's tail sampling drops every trace containing such a call (with the spans inside it), so a trace shows up
  about **10 s** after the request.
- The gateway does not record a span per security filter (`management.observations.enable."[spring.security]": false`).
- Jaeger is pinned to **2.20** (`infrastructure/.env`): 2.21 removed the HTTP API that Grafana's Jaeger data source uses
  to search traces. Upgrade when Grafana searches through Jaeger's v3 API (test: Explore → Jaeger → Search).

Not in the traces: **SQL queries** are not separate spans (the endpoint's span includes their time; the
repository panel of *Grocery service details* shows database time per method).

### Traces through Kafka

One trace shows a request **and everything it caused through events**. Placing order 176 (cash on delivery) gave one
trace of 39 spans from 11 services in Jaeger (shortened; the start is the time after the click):

```text
     0 ms  api-gateway        http post                                   131 ms
    10 ms    order-service      http post /api/v1/orders                  117 ms   saves the order + OrderCreated in the outbox
   429 ms      inventory-service  grocery.order.events process             72 ms   OrderCreated (Debezium -> Kafka took ~0.3 s)
   428 ms      payment-service    grocery.order.events process             43 ms   ... and 5 more services read OrderCreated
   850 ms        payment-service    grocery.inventory.events process       70 ms   StockReserved -> COD accepted
  1150 ms          order-service      grocery.payment.events process       41 ms   PaymentConfirmed -> CONFIRMED
  1428 ms            notification-service grocery.order.events process     54 ms   OrderConfirmed -> the emails
```

How the trace gets through the outbox:

1. `OutboxEventPublisher` stores the current trace in the outbox row: column `traceparent`, a W3C value such as
   `00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01` (version, trace id, the span that published, sampled).
   Outside a trace (none today) it stays empty.
2. Debezium's `EventRouter` copies the column into the Kafka header `traceparent`
   (`table.fields.additional.placement` in each `*/debezium/*-outbox-connector.json`, next to `eventType`).
3. Spring Kafka in the consumer (`spring.kafka.listener.observation-enabled: true`) reads the header and starts the
   handling of the event as a child span of that trace, so its log lines, database calls, calls to other services and
   the events **it** publishes belong to the same trace.

Checked on order 175: OrderCreated, StockReserved, PaymentConfirmed and OrderConfirmed in three outbox tables all carry
trace id `ceb2a8d7...`, and 8 services (gateway, order, inventory, payment, notification, delivery, report, platform)
logged lines with it. Only the **trace id** travels with the events: in a Kafka listener the correlation id is empty
(`[] [ceb2a8d7...]`), so to follow a request into the events, search for its trace id
(`{app=~".+"} | traceId="..."`).

The consumer span starts a moment after the request span has ended (the event waits for Debezium): that is normal for
messaging. Jaeger keeps such late spans with their trace (tail sampling caches its decision per trace). The
product-cdc connector reads the product **tables**, not an outbox, so product-read-service still starts a new trace
for a product change. Events without the header (published before this change) also start a new trace.

## Health

Every service has `/actuator/health` (UP = 200, DOWN = 503; it checks the database, Redis, Elasticsearch, the mail
server ... that the service uses), plus the Kubernetes groups `/actuator/health/liveness` and `/readiness`.

Prometheus checks it every 15 s through the **blackbox exporter** ([infrastructure/blackbox/blackbox.yml](../infrastructure/blackbox/blackbox.yml)):
`probe_success` = 1 when the answer is 200 with `"status":"UP"`. "Up but unhealthy" (`up == 1`, `probe_success == 0`)
means the service runs but something it needs is down: alert **ServiceUnhealthy**. Try one check by hand:
http://localhost:9115/probe?module=spring_health&debug=true&target=http://host.docker.internal:8101/actuator/health

`scripts\check-health.ps1` checks the infrastructure and the monitoring tools.

## Alerts

Prometheus evaluates the rules in [infrastructure/prometheus/alerts/grocery-alerts.yml](../infrastructure/prometheus/alerts/grocery-alerts.yml)
every 15 s. An alert is *pending* while its condition is true and *firing* once it stayed true for its `for` time.
Firing alerts go to Alertmanager ([infrastructure/alertmanager/alertmanager.yml](../infrastructure/alertmanager/alertmanager.yml)),
which groups them (one email per alert and service), waits 30 s for related ones, and sends an email - locally to
**Mailpit** (http://localhost:8025), with a second email when it is resolved. When a service is down, its other alerts
are held back (inhibit rule): you get one "ServiceDown", not five. The same for a data store: while MySQL, Redis or
Elasticsearch is down, the alerts of the services that use it are held back ([Data stores](#data-stores-mysql-redis-elasticsearch)).

See them: Grafana → **Alerting → Alert rules** (all rules and their state), the *Firing alerts* table on *Grocery
services*, http://localhost:9090/alerts, http://localhost:9093 (silence an alert during planned work).

| Alert | Severity | Fires when | What to do |
|---|---|---|---|
| ServiceDown | critical | Prometheus cannot read a service for 1 min | Start it / look at its last log lines (crash, out of memory) |
| ServiceUnhealthy | critical | It runs, but `/actuator/health` is not UP for 2 min | Open its `/actuator/health`: which dependency is DOWN? Is that container running (`check-health.ps1`)? |
| ServiceRestarting | warning | Started more than twice in 15 min | It crashes on start or runs out of memory: the log lines before each start |
| HighErrorRate | critical | > 5 % of its requests answered 5xx for 5 min (with real traffic) | *Service details* → errors by endpoint and exception; ERROR lines in Loki; a failing trace in Jaeger |
| SlowResponses | warning | p95 > 1 s for 10 min (not the gateway, not the AI chat, not the live streams) | *Service details* → slowest endpoints, repository calls, calls to other services; a slow trace via its exemplar |
| DatabaseConnectionTimeouts | critical | A request waited 5 s for a DB connection (`hikari.connection-timeout`) and got 503 | Slow queries or long transactions; is MySQL OK? |
| DatabasePoolExhausted | warning | Requests waited for a DB connection for 2 min | As above, before it turns into timeouts |
| HighHeapUsage | warning | Heap > 90 % for 10 min | More memory (`-Xmx` / Kubernetes limit) or a leak (heap dump) |
| HighGcTime | warning | > 10 % of the time in GC pauses for 10 min | Usually too little heap |
| HighCpuUsage | warning | > 80 % CPU for 10 min | Which endpoints get the most requests? A loop? More replicas |
| WebThreadsBusy | warning | > 90 % of Tomcat's request threads busy for 5 min (services on virtual threads are left out: Tomcat reports -1 there, [performance](performance.md)) | Something it waits for (database, another service) is slow |
| KafkaEventsMovedToDeadLetterTopic | warning | An event failed after all retries and went to `<topic>.DLT` | Kafka UI shows the event and the error; fix the cause, then replay it ([resilience.md](resilience.md)) |
| KafkaConsumerNoProgress | critical / warning | Events wait and the group committed nothing for 2, 5 or 15 min (label `max_wait`, [table above](#events-and-debezium)) | Is the service running? Its log shows the failing event and the retries; Grocery events → lag per partition |
| KafkaConsumerLagGrowing | warning | > 100 events waiting and still growing for 10 min | The handler or its database is too slow (*Service details* → Events); more partitions + instances help only if the handler is fine |
| KafkaConsumerRebalancing | warning | One consumer rebalanced > 3 times in 15 min | It keeps leaving its group: an event takes longer than `max.poll.interval.ms` (5 min), or the service restarts |
| KafkaConnectDown | critical | Kafka Connect unreachable for 2 min | `docker logs grocery-kafka-connect`; nothing is lost, the outbox rows wait ([resilience.md](resilience.md)) |
| KafkaConnectorNotRunning | critical | A connector is paused, failed or unassigned for 2 min | `GET :8083/connectors/<name>/status`; after the fix `POST .../restart?includeTasks=true` |
| KafkaConnectTaskFailed | critical | A connector task failed (1 min) | Same: the status shows the stack trace; `POST .../restart?includeTasks=true&onlyFailed=true` |
| DebeziumNotReadingBinlog | critical | Running connectors not connected to the MySQL binlog for 3 min | `docker logs grocery-kafka-connect` (e.g. "Too many connections", access denied); Grocery events → Debezium shows which |
| MySQLDown | critical | The exporter cannot reach MySQL for 1 min | `docker logs grocery-mysql`; `docker compose -f infrastructure/docker-compose.yml up -d mysql`. The services' own alerts are held back meanwhile |
| MySQLConnectionsNearLimit | warning | > 80 % of `max_connections` open for 5 min | Grocery infrastructure → Connections per service: which pool grew? More replicas need a bigger `max_connections` or smaller pools |
| MySQLSlowQueries | warning | > 2 queries over 1 s per 5 min, for 15 min | `docker exec grocery-mysql tail -n 40 /var/lib/mysql/slow.log`, then `EXPLAIN` the query (missing index?). Nothing new in the slow log: Kafka Connect keeps restarting its connectors |
| RedisDown | critical | The exporter cannot reach Redis for 1 min | `docker logs grocery-redis`. Meanwhile no rate limits at the gateway, partners look offline ([resilience.md](resilience.md)) |
| RedisMemoryHigh | warning | > 80 % of `maxmemory` for 5 min (only when a limit is set) | Grocery infrastructure → Redis → Keys: what grows? RedisInsight shows the keys |
| RedisEvictingKeys | warning | Redis dropped keys because its memory was full | Raise `maxmemory` or find the growing keys; partners may need to go online again |
| RedisRejectedConnections | warning | Redis refused connections (`maxclients`) | Grocery infrastructure → Redis → Clients: a client that leaks connections? |
| ElasticsearchDown | critical | The exporter gets no cluster health for 1 min | `docker logs grocery-elasticsearch`; search fails meanwhile, product changes wait in Kafka and are indexed afterwards |
| ElasticsearchClusterRed | critical | A primary shard is unavailable for 1 min | `GET :9200/_cluster/allocation/explain` |
| ElasticsearchClusterYellow | warning | Replica shards without a node for 10 min | `GET :9200/_cat/shards?v`: on one node, an index that asks for replicas |
| ElasticsearchHeapHigh | warning | Heap > 85 % for 15 min without a drop | More heap (`ES_JAVA_OPTS`) or very large queries |
| ElasticsearchDiskHigh | warning | Data disk > 85 % for 10 min | Free disk; in production at 95 % the indexes become read-only |
| ElasticsearchRejectedRequests | warning | Search or write requests refused (queue full) | Elasticsearch is overloaded: Grocery infrastructure → Searches and indexing |
| CircuitBreakerOpen | warning | A gateway route's circuit breaker is open for 1 min | The service behind the route fails or is too slow (its own alerts say why) |
| ManyRequestsRateLimited | warning | > 30 requests per minute rejected with 429 for 5 min | Gateway log: which client? A script or login guessing, or a limit too low |
| ManyErrorLogs | warning | > 10 ERROR lines in 5 min | Grocery logs, level ERROR |
| LogsNotDeliveredToLoki | warning | A service dropped log lines | Loki was down too long; is it running? |
| MonitoringToolDown | warning | Jaeger, Loki, Grafana, Alertmanager, one of the exporters ... unreachable for 5 min | Start it: `start-infra.ps1 -Profiles monitoring` (for an exporter: the store itself may be fine) |
| TooManyMetricSeries | warning | Prometheus holds > 200,000 series for 15 min (normally about 36,000) | `scripts\check-metrics-cardinality.ps1` shows the metric and label that grow ([Cardinality](#cardinality)) |
| KafkaDown | critical | The broker accepts no TCP connection for 1 min (blackbox exporter, job `kafka-broker`) | `docker logs grocery-kafka`; nothing is lost: events wait in the outbox tables, consumers continue when it is back. The consumer, Connect and Debezium alerts are held back meanwhile |
| ErrorBudgetBurningFast | critical | > 7.2 % of the gateway's requests failed with a 5xx in the last hour and still fail | [SLOs](#slos-service-level-objectives): Grocery SLOs → server errors by route names the service; its own alerts say why |
| ErrorBudgetBurningSlowly | warning | > 3 % failed for 6 hours (and the last 30 min) | As above; not sent while the fast one fires |
| LatencyBudgetBurning | warning | > 6 % of the requests took longer than 1 s for 6 hours (and the last 30 min) | Grocery SLOs → slow requests by route, then *Service details* of that service |

The rules have **unit tests** ([infrastructure/prometheus/tests/grocery-alerts-test.yml](../infrastructure/prometheus/tests/grocery-alerts-test.yml)):
they feed made-up metric values and check which alert fires when. Run them, and check the configuration, after
changing a rule:

```powershell
docker compose -f infrastructure/docker-compose.yml --profile monitoring run --rm --entrypoint promtool prometheus test rules /etc/prometheus/tests/grocery-alerts-test.yml
docker compose -f infrastructure/docker-compose.yml --profile monitoring run --rm --entrypoint promtool prometheus check config /etc/prometheus/prometheus.yml
Invoke-RestMethod -Method Post http://localhost:9090/-/reload     # load the changed rules without a restart
```

Production: Alertmanager sends to the team's channel instead (Slack, Microsoft Teams, PagerDuty, a real mail server:
one `receivers` entry), critical alerts page someone, warnings go to a channel.

## SLOs (service level objectives)

An SLO is a promise about what **users** experience, measured where their requests arrive: the API gateway (metric
`spring_cloud_gateway_requests_seconds`, per route). Rules and alerts: [infrastructure/prometheus/alerts/slo-rules.yml](../infrastructure/prometheus/alerts/slo-rules.yml);
dashboard **Grocery SLOs**.

| SLO | Objective (rolling 7 days) | Error budget | Not counted |
|---|---|---|---|
| Availability | 99.5 % of the requests answered without a server error (5xx) | 0.5 % may fail (about 50 of every 10,000) | Swagger UI routes. 4xx answers are the client's mistake (a wrong password), not counted as failures |
| Latency | 99 % answered within 1 s | 1 % may be slower | Also the AI chat and voice (seconds by design) and the live streams, routes named `*-stream` (open for minutes) |

The **error budget** is the part that may go wrong. While plenty is left, the team can ship features and take risks;
when it runs low, reliability work comes first. The window is 7 days because Prometheus keeps 7 days here; in
production a 30-day window with a longer retention is usual (the same rules with `[30d]`). The latency SLO uses two
exact buckets that the gateway publishes for this (`management.metrics.distribution.slo` in its `application.yml`:
`le="0.5"` and `le="1.0"`).

**Alerts on the burn rate.** The burn rate is how many times faster than allowed the budget is used up: 1 means it
lasts exactly 7 days. Each alert looks at a long and a short window (Google's SRE workbook, "multi-window,
multi-burn-rate"): the long window proves that enough budget is gone to matter, the short one that it is **still**
burning, so the alert ends soon after the fix. A minimum of one request per minute keeps a quiet night with 2 failed
requests out of 5 from counting as "40 % errors".

| Alert | Burn rate | Windows | Meaning |
|---|---|---|---|
| ErrorBudgetBurningFast (critical) | 14.4 × (7.2 % errors) | 1 h and 5 min | The week's budget is gone in about half a day: act now |
| ErrorBudgetBurningSlowly (warning) | 6 × (3 % errors) | 6 h and 30 min | Gone in about a day |
| LatencyBudgetBurning (warning) | 6 × (6 % slower than 1 s) | 6 h and 30 min | Users wait noticeably, for hours |

The SLO alerts overlap with ServiceDown, HighErrorRate ... on purpose: those say **which** service is broken, the SLO
alerts say that **users** are affected and how much. The unit tests in `grocery-alerts-test.yml` start each case
with healthy hours and check that the alert fires only once enough budget is used.

## Try it: see every part working

```powershell
.\scripts\start-infra.ps1 -Profiles monitoring
# start the services with TRACING_EXPORT_ENABLED=true and LOKI_ENABLED=true (above), then:

# 1. Metrics: Grafana -> Grocery services: 16 services up, the table full. Prometheus -> Status -> Targets: all UP.
# 2. A traced request: log in through the gateway, then Jaeger -> service api-gateway -> Find Traces (after ~10 s)
Invoke-RestMethod -Method Post -ContentType 'application/json' -Body '{"email":"customer1@grocery.com","password":"Password@123"}' http://localhost:8080/api/v1/auth/login
#    The trace has the gateway, its Redis rate-limit call and auth-service's login. In Grafana (Explore -> Jaeger),
#    click the auth-service span: "Related logs" shows the log lines of the request.
# 3. An alert: stop one service (Ctrl+C). After ~1 min Grafana -> Alerting shows ServiceDown firing; ~30 s later the
#    email "[FIRING:1] ServiceDown ..." is in Mailpit. Start the service again: a "[RESOLVED]" email follows.
# 4. Health: stop Mailpit's container (docker stop grocery-mailpit): notification-service turns unhealthy
#    (its /actuator/health checks the mail server) -> ServiceUnhealthy after 2 min. docker start grocery-mailpit.
#    (Alertmanager also mails through Mailpit, so that email arrives once Mailpit is back.)
# 5. Consumer lag: stop notification-service, then create events (.\scripts\simulate-delivery.ps1 -Steps 4).
#    Grafana -> Grocery events: notification-service's lag rises on the order, payment, inventory and delivery topics;
#    KafkaConsumerNoProgress fires after 5 min (its email is held back by ServiceDown). Start the service again: the
#    lag drops to 0 within seconds and the alerts resolve.
# 6. A data store: docker stop grocery-redis. Grafana -> Grocery infrastructure shows Redis DOWN; after ~1 min
#    RedisDown fires and its email arrives. The gateway and delivery-service turn unhealthy too, but Alertmanager holds
#    their ServiceUnhealthy back (no email). docker start grocery-redis: the alert resolves, a "[RESOLVED]" email follows.
#    The same with docker stop grocery-elasticsearch (ElasticsearchDown; product-read-service's alerts held back).
```

## In production

- **Prometheus**: one per cluster (or a managed one: Grafana Cloud, Amazon/Azure/Google managed Prometheus), finding
  the pods through their `prometheus.io/*` annotations ([deployment/kubernetes/README.md](../deployment/kubernetes/README.md#the-ai-shopping-assistant-and-monitoring)) or a
  Prometheus Operator `PodMonitor`; the alert rules and dashboards stay the same.
- **Tracing**: services send to an OpenTelemetry Collector in the cluster (it can sample, add Kubernetes labels and
  forward), Jaeger with persistent storage; `TRACING_SAMPLING_PROBABILITY` around `0.1`.
- **Grafana, Alertmanager, Jaeger, Prometheus** behind login, never public. Alertmanager → the team's channel.
- **Exporters** inside the cluster network only, each with a monitoring-only login: the MySQL `exporter` user as here
  (with a real password from a secret), a Redis ACL user that may only run `INFO`-like commands, an Elasticsearch user
  with the `monitor` cluster and index privileges. A managed database (Amazon RDS, Azure, Google Cloud SQL) brings
  its own metrics; the alert limits stay the same.
- Keep labels low-cardinality (above), and look at the dashboards before an incident, not only during one.

## Where to look when ...

| Symptom | Look at |
|---|---|
| A request fails with 503 | The gateway log: `Route <service>: ... service not reachable`, `Circuit breaker '<service>' is OPEN`, or `busy (bulkhead full)` names the service. Grafana "Services up", "Gateway: circuit breakers not closed" and "Gateway: requests answered by the gateway" |
| Clients get 429 | Grafana "Gateway: requests answered by the gateway" (which policy); gateway log `Rate limit '<policy>' reached by <user or ip>`. Many guests behind one proxy share an IP: is `RATE_LIMIT_TRUSTED_PROXIES` set? Redis Insight shows each client's bucket (`request_rate_limiter.*`, [redis-insight.md](redis-insight.md)) |
| An order stays PENDING | Grafana *Grocery events*: lag of inventory-service / payment-service, Debezium connected (order-outbox)? Events in dead-letter topics; the service's log for the order id |
| Search shows old data | Grafana *Grocery events*: lag of product-read-service, product-cdc-connector running and reading the binlog (server `cdc`)? Kibana Discover → `id : <product id>`: compare its `version` with MySQL ([kibana.md](kibana.md#recipe-my-product-does-not-show-up-in-the-shop)) |
| An email did not arrive | `GET /api/v1/admin/notifications?emailStatus=FAILED`; Mailpit |
| "Too many connections" in a log | Grafana *Grocery infrastructure* → Connections per service (who holds them) and Failed connection attempts (`max_connections`) |
| The database is slow | Grafana *Grocery infrastructure* → Slow queries and Row lock waits; the slow log `docker exec grocery-mysql tail -n 40 /var/lib/mysql/slow.log` |
| Search fails or shop pages answer 503 | Grafana *Grocery infrastructure* → Elasticsearch (DOWN, RED, heap, rejected requests); then the row "Search shows old data" above for the events |
| Slow requests | Grafana *Grocery service details* → "Endpoints in the selected time range" (slowest first) and the repository / outgoing-call panels; click an exemplar dot for the Jaeger trace of a slow request; Loki `{app=~".+"} \| outcome="SLOW"` shows which method was slow |
| An alert email arrived | The alert table in [Alerts](#alerts) says what to do; Grafana → Alerting shows since when it fires |
| A service seems down | Grafana *Grocery services*: down (Prometheus cannot reach it) or unhealthy (runs, but a dependency is down: its `/actuator/health`) |
| A user reports an error | Ask for the time; Grafana → Grocery logs → level ERROR/WARN, or the correlation id from the response header `X-Correlation-Id` |
| No logs in Loki | The service runs with `LOKI_ENABLED=true`? Loki ready (`http://localhost:3100/ready`)? A `loki4j` error on the service's console names the cause (wrong `LOKI_URL`, Loki down) |
