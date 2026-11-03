# Ports and URLs (local development)

## Microservices (run on your machine)

| Service | Port | Database | Swagger UI | Status |
|---|---|---|---|---|
| api-gateway | 8080 | – (Redis) | http://localhost:8080/swagger-ui.html (all services) | ✅ Phase 2 |
| auth-service | 8101 | auth_db | http://localhost:8101/swagger-ui.html | ✅ Phase 1 |
| customer-service | 8102 | customer_db | http://localhost:8102/swagger-ui.html | ✅ Phase 3 |
| store-service | 8103 | store_db | http://localhost:8103/swagger-ui.html | ✅ Phase 3 |
| product-service | 8104 | product_db | http://localhost:8104/swagger-ui.html | ✅ Phase 4 |
| product-read-service | 8105 | Elasticsearch | http://localhost:8105/swagger-ui.html | ✅ Phase 4 |
| inventory-service | 8106 | inventory_db | http://localhost:8106/swagger-ui.html | ✅ Phase 5 |
| cart-service | 8107 | cart_db | http://localhost:8107/swagger-ui.html | ✅ Phase 6 |
| order-service | 8108 | order_db | http://localhost:8108/swagger-ui.html | ✅ Phase 7 |
| payment-service | 8109 | payment_db | http://localhost:8109/swagger-ui.html | ✅ Phase 7 |
| delivery-service | 8110 | delivery_db + Redis | http://localhost:8110/swagger-ui.html | ✅ Phase 9 |
| notification-service | 8111 | notification_db | http://localhost:8111/swagger-ui.html | ✅ Phase 8 |
| report-service | 8112 | report_db | http://localhost:8112/swagger-ui.html | ✅ Phase 11 |
| platform-service | 8113 | platform_db | http://localhost:8113/swagger-ui.html | ✅ Phase 11 |
| mcp-client-service | 8114 | chat_db | http://localhost:8114/swagger-ui.html | ✅ Phase 14 (A3-A5, AI assistant) |
| mcp-server-service | 8115 | – (calls the gateway) | – (MCP endpoint `POST /mcp`, internal only, header `X-MCP-API-Key`) | ✅ Phase 14 (A2, A5, AI assistant) |
| fresh-grocery (frontend) | 5173 | – | | ✅ Phase 13 |

In containers and Kubernetes every service listens on **8080** (`SERVER_PORT=8080`).

## Kubernetes (two kind clusters, Phase 23)

| What | Address | Notes |
|---|---|---|
| Entry NGINX (both clusters) | http://localhost (port **80**) | Docker container `grocery-entry-nginx`, [nginx.conf](../deployment/kubernetes/nginx/nginx.conf) |
| Cluster A directly | http://localhost:**8281** | kind maps it to node port 30080 (the cluster's NGINX Gateway) |
| Cluster B directly | http://localhost:**8282** | the same for cluster B. Not 8081/8082 as in the deployment guide: Schema Registry uses 8081 |
| Inside a cluster | `http://<service>` (port 80 -> pod port 8080), `http://frontend` | Kubernetes Services in the namespace `grocery` |

[How to run it](../deployment/kubernetes/README.md).

## Infrastructure (Docker Compose)

| Component | Host port | URL / connection | Login |
|---|---|---|---|
| MySQL 8.4 | **3307** | `jdbc:mysql://localhost:3307/<service>_db` | `root` / `root`, or `<service>_user` / `<service>_pass` |
| Kafka | 9092, 9094 | `localhost:9092` from your machine; `host.docker.internal:9094` from other containers or Kubernetes pods (containers of the compose file use `kafka:29092`) | – |
| Schema Registry | 8081 | http://localhost:8081/subjects | – |
| Kafka Connect + Debezium | 8083 | http://localhost:8083/connectors | – |
| Kafka UI | 8090 | http://localhost:8090 | – |
| Elasticsearch | 9200 | http://localhost:9200 | security off |
| Redis | 6379 | `localhost:6379` | – |
| Mailpit (fake email) | 1025 SMTP, 8025 UI | http://localhost:8025 | – |
| Redis Insight | 5540 | http://localhost:5540 ([guide](redis-insight.md)) | – |
| Kibana *(profile tools)* | 5601 | http://localhost:5601/app/discover ([guide](kibana.md)) | – |
| Fake social login provider *(profile tools)* | 8099 | http://127.0.0.1:8099/test/.well-known/openid-configuration ([guide](social-login.md)) | any user, any claims |
| Prometheus *(profile monitoring)* | 9090 | http://localhost:9090 (targets, alert rules; [guide](observability.md)) | – |
| Alertmanager *(profile monitoring)* | 9093 | http://localhost:9093 (firing alerts, silences; the emails go to Mailpit) | – |
| Blackbox exporter *(profile monitoring)* | 9115 | http://localhost:9115 (health checks for Prometheus) | – |
| Kafka exporter *(profile monitoring)* | – (Docker network: `kafka-exporter:9308`) | Consumer-group lag for Prometheus; no port on your machine ([guide](observability.md#events-and-debezium)) | – |
| Kafka Connect metrics | – (Docker network: `kafka-connect:9404`) | Connector states and Debezium metrics for Prometheus (JMX exporter agent) | – |
| MySQL, Redis, Elasticsearch exporters *(profile monitoring)* | – (Docker network: `mysqld-exporter:9104`, `redis-exporter:9121`, `elasticsearch-exporter:9114`) | Data store metrics for Prometheus; no port on your machine ([guide](observability.md#data-stores-mysql-redis-elasticsearch)) | – |
| Grafana *(profile monitoring)* | 3000 | http://localhost:3000 (dashboards Grocery services, Grocery service details, Grocery events, Grocery logs; Alerting) | `admin` / `admin` |
| Loki *(profile monitoring)* | 3100 | http://localhost:3100/ready (logs in Grafana; start the services with `LOKI_ENABLED=true`) | – |
| Jaeger *(profile monitoring)* | 16686 UI, 4317/4318 OTLP | http://localhost:16686 (start the services with `TRACING_EXPORT_ENABLED=true`; they send to `http://localhost:4318/v1/traces`) | – |
| Ollama *(profile ai)* | 11434 | http://localhost:11434/api/tags (downloaded models); model set in `infrastructure/.env` (`OLLAMA_CHAT_MODEL`) | – |
| speaches *(profile ai, voice)* | 8000 | http://localhost:8000/v1/models (downloaded voice models), http://localhost:8000/docs (its API); speech to text and text to speech for the assistant | – |

MySQL uses host port **3307** (not 3306) so it never clashes with a MySQL server installed directly on your machine.
