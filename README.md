# Grocery Platform

A multi-vendor grocery platform built as **Spring Boot microservices**. It has five panels: Customer, Store/Vendor, Delivery Partner, Admin and Super Admin.

- **Architecture and plan:** [docs/architecture-and-development-plan.md](docs/architecture-and-development-plan.md) (start here)
- **Coding conventions:** [docs/conventions.md](docs/conventions.md)
- **Frontend (Fresh Grocery):** [docs/frontend-development-plan.md](docs/frontend-development-plan.md)
- **Live delivery tracking (OpenStreetMap map, partner location, Server-Sent Events):** [docs/delivery-tracking.md](docs/delivery-tracking.md)
- **AI shopping assistant (Spring AI + MCP + Ollama):** [docs/ai-assistant-plan.md](docs/ai-assistant-plan.md)
- **Ports and URLs:** [docs/ports.md](docs/ports.md) · **Seed data:** [docs/seed-data.md](docs/seed-data.md) · **Events:** [docs/event-catalog.md](docs/event-catalog.md)
- **Running it:** [docs/observability.md](docs/observability.md) (metrics, traces, logs, dashboards, alerts) · [docs/resilience.md](docs/resilience.md) (what happens when something fails) · [docs/kibana.md](docs/kibana.md) (look into Elasticsearch) · [docs/redis-insight.md](docs/redis-insight.md) (look into Redis) · [deployment/kubernetes/README.md](deployment/kubernetes/README.md) (Kubernetes: two kind clusters behind NGINX)
- **Original requirements:** [app-prompt.txt](app-prompt.txt)

## Progress

| Phase | Scope | Status |
|---|---|---|
| 0 | Foundations: Docker Compose infrastructure, scripts, docs | ✅ Done |
| 1 | auth-service (reference service) | ✅ Done |
| 2 | api-gateway: routing, JWT check, role rules, rate limiting, central Swagger | ✅ Done |
| 3 | customer-service + store-service; first events (outbox → Debezium → Kafka/Avro → consumers) | ✅ Done |
| 4 | product-service + product-read-service: catalog CQRS (MySQL → Debezium CDC → Elasticsearch) | ✅ Done |
| 5 | inventory-service: stock, low-stock alerts, stock shown in search; first service-to-service REST call | ✅ Done |
| 6 | cart-service: one-store cart, platform and store coupons, checkout summary | ✅ Done |
| 7 | order-service + payment-service (mock gateway): the order saga (choreography) with compensation | ✅ Done |
| 8 | notification-service (emails via Mailpit + in-app notifications): **MVP complete** | ✅ Done |
| 9 | delivery-service: partner KYC, online/offline (Redis), assignment, delivery steps, proof photo, earnings | ✅ Done |
| 10 | Razorpay payment gateway (checkout, signature, webhook), refunds, online payment deadline, payment summary | ✅ Done |
| 11 | report-service (dashboard, sales, top lists, commission) + platform-service (settings, flags, banners, audit log); reviews, delivery ratings, PDF invoices | ✅ Done |
| 12 | Production readiness: Prometheus + Grafana dashboard, tracing (Zipkin), resilience review, Docker images, Kubernetes manifests | ✅ Done |
| 13 | "Fresh Grocery" storefront: React + TypeScript + Tailwind ([plan](docs/frontend-development-plan.md); phases F1–F8): home, shop, product, cart, wishlist, login; checkout and online payment, order tracking and history, cancel, invoice, ratings; account, addresses, password reset, notifications. Vendor panel at `/vendor` (phases V1–V4): store registration and settings, order queue (accept, reject, pack, ready for pickup), products with stock and photos, coupons, reviews, sales dashboard. Admin panel at `/admin` (phases A1–A4): dashboard, store approval, orders (cancel), deliveries (assign a partner), payments, accounts, KYC review, delivery zones, product and review moderation, categories, platform coupons, banners, reports, notification log. Delivery partner panel at `/delivery`, made for a phone (phases D1–D3): partner sign-up and KYC, online switch (kept online while the panel is open), deliveries step by step (accept or reject, picked up, on the way, delivered with the cash collected, proof photo, report a problem, returned to the store), history, earnings. Super admin: the admin panel with an Owner menu (phases S1–S3): admins and roles, platform settings, feature flags, commission per store and report, audit log, system status from Prometheus. Completing the shop (phases C1–C4): the AI shopping assistant's chat in every shop page, Razorpay Checkout on the order page (when payment-service runs Razorpay), stores list, store pages and the shop's store filter, the PRODUCT_REVIEWS flag. Modern screens (phases M1–M3): full-screen login and sign-up pages with illustrations for the shop and every panel, and a redesigned admin dashboard. Themes (phases B1–B3): one page background for every screen, and a light and a dark theme with a switch on the right edge of every page. UI/UX (phases U1–U5): page controls with a page size above and below every paged table, a profile popup for every role, a notification popup with icons, times, unread filter and "mark as read" plus a notifications page per role, and charts where they fit the data (sales trend as an area chart, orders by outcome and payments by status as donuts, the customer's spending per month as bars). Phases R1–R4: one loading spinner in the whole app with a "Loading…" sign for slow pages, loads and saves; panel menus that collapse to icons with a button on their edge; the delivery panel redesigned (bottom bar on phones, today's numbers, a step bar and trip route per delivery, history, earnings, profile and joining steps). Phases E1–E4: our own drop-downs, date picker (a calendar) and time pickers instead of the browser's, the same in every panel and usable with the keyboard. Phases W1–W2: the AI chat as a full-height panel at the right edge, 599 px wide. Phase F1: page and section titles in Fraunces (self-hosted), the interface stays Quicksand and the text Lato. Phases G1–G5: a new product page (photo thumbnails and zoom, Compare and Add to wishlist, tabs for the description, details and reviews, the shop's sidebar), a product comparison of up to 4 products (`/compare`, a bar at the bottom while products are chosen), a wishlist pop-up when a heart is clicked, and a new wishlist page with the day added, the stock and a link to share it | ✅ Done |
| 14 | AI shopping assistant: mcp-client-service + mcp-server-service (Spring AI, MCP), Ollama + Qwen ([plan](docs/ai-assistant-plan.md); phases A1–A5): chat API for guests and customers, 10 read-only tools, answer check, golden-question evaluation; the chat in the Fresh Grocery shop ("Ask our assistant"). Phase A6: a work assistant in the vendor, delivery and admin panels, 11 more read-only tools used with the user's own login, rules per role, 33 golden questions. Notifications ([section 19](docs/ai-assistant-plan.md#19-notifications-2026-10-28)): every logged-in user can ask about their own notifications (unread count, newest, unread, by topic) through two read-only tools on notification-service; 40 golden questions. Shopping by chat ([section 20](docs/ai-assistant-plan.md#20-shopping-by-chat-the-cart-orders-and-cancellations-2026-10-2829)): a customer changes their cart in the chat ("add 2 kg of tomatoes", "remove the onions", "make it 3 kg", "empty my cart"), and the chat prepares every change, orders and cancellations as cards that only the customer's Confirm click carries out: no tool changes data (`Idempotency-Key` and checkout fingerprint in order-service); 73 golden questions and a Playwright flow | ✅ Done |
| 15 | Centralized logging: Spring AOP `LoggingAspect` in every service (outcome and duration of API calls, events, jobs, slow calls), logs pushed to Grafana Loki, Grafana logs dashboard ([observability](docs/observability.md)) | ✅ Done |
| 16 | Gateway resilience, central for all services: rate limiting per user / IP in Redis (login, chat, default policies), throttling (bulkhead), circuit breaker per route, timeouts, safe retries (GET/HEAD only, with backoff), consistent fallback answers with `Retry-After` ([api-gateway](api-gateway/README.md#resilience-and-traffic-control), [resilience](docs/resilience.md)) | ✅ Done |
| 17 | Monitoring and tracing for all services: Prometheus (requests, errors, response-time percentiles, health checks, database pool, JVM, CPU, Kafka), Jaeger traces through OpenTelemetry (replaces Zipkin), Grafana dashboards (overview + per service, linked to traces and logs), 18 alert rules with unit tests, Alertmanager emails ([observability](docs/observability.md)) | ✅ Done |
| 18 | Live delivery tracking ([delivery-tracking](docs/delivery-tracking.md)): the customer follows the partner on an OpenStreetMap map (React Leaflet) with live updates (Server-Sent Events through the gateway), status, partner details and connection states; the partner's phone shares its position from the browser (Geolocation API) while delivering; delivery-service keeps only the last position in Redis and fans out through Redis Pub/Sub; optional route and arrival time from an OSRM server; simulation script and Playwright flow with two browsers | ✅ Done |
| 19 | Production readiness II, step by step with a review after each: **19a** Kafka and Debezium monitoring (consumer lag from the broker with kafka-exporter, connector and binlog state with the JMX exporter, Grafana *Grocery events*, lag alerts with limits from who is waiting) ([observability](docs/observability.md#events-and-debezium)) · **19b** MySQL, Redis and Elasticsearch monitoring (one exporter each with monitoring-only rights, Grafana *Grocery infrastructure*, alerts for down stores, connections, slow queries, memory, heap, disk and rejected requests; one email for the cause, not for every service that depends on it) ([observability](docs/observability.md#data-stores-mysql-redis-elasticsearch)) · **19c** messaging reliability (idempotent acks=all producers, topic retention and production durability settings, Debezium heartbeats, hourly cleanup of the outbox and processed-event tables, dead-letter inspect and replay for the super admin, failure drills `scripts\failure-drills.ps1`, a runbook) ([resilience](docs/resilience.md#events-what-is-guaranteed)) · **19d** traces, logs and SLOs (one trace from a request through every Kafka event it causes: outbox `traceparent` column, Debezium header, Spring Kafka observation; correlation and trace id in the gateway's logs; `LOG_FORMAT=json` for log collectors; `scripts\check-log-secrets.ps1` for secrets and personal data in the logs; SLOs at the gateway with error budgets, burn-rate alerts and Grafana *Grocery SLOs*; `scripts\check-metrics-cardinality.ps1`; KafkaDown alert) ([observability](docs/observability.md#traces-through-kafka)) · **19e** MySQL and Redis hardening (database pools within a connection budget, 5 s pool and 30 s transaction timeouts with a 503 "busy" answer; no calls to other services inside transactions; missing indexes found with EXPLAIN; Redis users with only the keys and commands they need, a memory limit, and no 60 s hang when Redis is down; `scripts\backup-databases.ps1` / `restore-database.ps1`; drills `dbpool` and `redis`) ([resilience](docs/resilience.md#database-connections)) · **19f** Elasticsearch (versioned indexes behind aliases created at start-up, strict explicit mappings, `scripts\reindex-products.ps1` for a new index version while search keeps working, nightly snapshots with `scripts\search-snapshots.ps1` to list, take and restore them, 2 s / 5 s client timeouts) ([resilience](docs/resilience.md#search-indexes-elasticsearch)) · **19g** virtual threads, measured (k6 load tests `perf/k6` for search and cart add at 50, 200 and 400 users: no gain, slower and more heap at 400, because the database pool and Elasticsearch are the limit, not threads, so they stay off; `WebThreadsBusy` and the thread panel made safe for a service that switches them on) ([performance](docs/performance.md)) | ✅ |
| 21 | Login sessions ([authentication](docs/authentication.md)): access tokens of 15 minutes, a rotating refresh token in an HttpOnly cookie (7 days, at most 30), reuse detection (a stolen old token ends the session), logout and revocation on password change, reset, disabled account and role change; expired vs invalid tokens told apart at the gateway. Step 1 backend (auth-service, api-gateway), step 2 the React app (access token in memory, silent refresh with one shared request, the login restored after a reload, logout on the server and in every tab). Password-reset tokens: only their hash stored, encrypted (AES-256-GCM) in their Kafka event, only the newest one works | ✅ |
| 22 | Social login ([social-login](docs/social-login.md)), off until `SOCIAL_LOGIN_ENABLED=true` (then a button per provider with credentials; each provider can be switched off alone): "Continue with Google / Microsoft / Facebook / Apple" on the shop's, vendor and delivery login and sign-up pages (not admin), Spring Security OAuth2 / OpenID Connect in auth-service (state, nonce, PKCE, ID token check), then the shop's own JWT and refresh cookie, never the provider's tokens; a new person adds the mobile number once; an existing account is connected automatically only when both sides verified the email, otherwise with its password once, and an email tells its owner; admin and disabled accounts refused; client secrets only in the environment; a local fake provider (mock-oauth2-server) for development and tests, and the console setup of all four providers. **Step 1** Google and the React app; **step 2** Microsoft (the issuer checked against the person's directory, the tenant setting, email verified only with `xms_edov`); **step 3** Facebook (Graph API with `appsecret_proof`, never linked automatically); **step 4** Apple (a client secret signed with the app's key, the answer as a form post, the name from the first login) | ✅ |
| 23 | Kubernetes on two kind clusters ([plan](docs/kubernetes-deployment-plan.md), [how to run it](deployment/kubernetes/README.md)): `grocery-cluster-a` and `grocery-cluster-b` (1 control plane + 2 workers each) with the same manifests in `deployment/kubernetes/` (one folder per service: Deployment, Service, and for api-gateway, product-read-service and the frontend a HorizontalPodAutoscaler 3–5 and a PodDisruptionBudget), the React shop as an NGINX image, rolling updates without downtime, probes, CPU and memory limits, a NetworkPolicy for the namespace, the Gateway API with NGINX Gateway Fabric in each cluster (ingress-nginx was retired), the Metrics Server, an entry NGINX on http://localhost that skips a cluster that does not answer; both clusters share the Docker Compose data tier and one folder for photos and invoices; scripts to build, create, deploy and drill (`k8s-*.ps1`). Cluster A ran completely (20 Spring Boot pods), cluster B with the gateway and the shop; running both completely needs more memory than this machine has free | ✅ (both clusters complete: needs memory) |

## Prerequisites

- Java 21, Maven 3.9+ (or use the `mvnw` wrapper inside each service)
- Docker Desktop with at least 8 GB of memory
- An HTTP client for `.http` files: the VS Code **REST Client** extension or IntelliJ IDEA

## Quick start

```powershell
# 1. Start the infrastructure (MySQL, Kafka, Schema Registry, Kafka Connect + Debezium,
#    Elasticsearch, Redis, Mailpit, Kafka UI, Redis Insight) and create the Kafka topics
.\scripts\start-infra.ps1

# 2. Start the services, each in its own terminal (or press Run on the *Application class in the IDE)
cd auth-service;         .\mvnw spring-boot:run
cd customer-service;     .\mvnw spring-boot:run
cd store-service;        .\mvnw spring-boot:run
cd product-service;      .\mvnw spring-boot:run
cd product-read-service; .\mvnw spring-boot:run
cd inventory-service;    .\mvnw spring-boot:run
cd cart-service;         .\mvnw spring-boot:run
cd order-service;        .\mvnw spring-boot:run
cd payment-service;      .\mvnw spring-boot:run
cd notification-service; .\mvnw spring-boot:run
cd api-gateway;          .\mvnw spring-boot:run

# 3. Once (after the services have created their tables): start the Debezium connectors
#    (outbox tables -> domain events, product tables -> change events for Elasticsearch)
.\scripts\register-connectors.ps1
#    ...and publish the seed stock once, so search shows it (seed rows are inserted by SQL, without events)
Invoke-RestMethod -Method Post http://localhost:8106/internal/inventory/stock-snapshot

# 4. Try it through the gateway (the single entry point, port 8080):
#    - e2e\phase3-onboarding.http    (vendor + customer onboarding, using events)
#    - e2e\phase4-catalog-cqrs.http  (write a product, find it in search a second later)
#    - e2e\phase5-inventory.http     (set stock to 0 -> "out of stock" in search)
#    - e2e\phase6-cart.http          (cart, store coupon, checkout summary)
#    - e2e\phase7-order-saga.http    (COD to delivered, online paid + refunded, payment declined, store rejects)
#    - e2e\phase9-delivery.http      (partner KYC, reject + reassign, failed delivery, admin assignment, proof photo)
#    - e2e\phase10-payments.http     (an unpaid online order fails at its deadline; admin payment summary)
#    - e2e\phase11-reports-and-platform.http (settings -> cart, banners, invoice, reviews, ratings, reports)
#    - e2e\mvp-order-flow.http       (the MVP: a new customer from registration to delivery; emails in Mailpit)
#    - api-gateway\http\api-gateway.http, or the central Swagger UI at http://localhost:8080/swagger-ui.html

# 5. The website (Fresh Grocery), in its own terminal: http://localhost:5173 (shop), /vendor (vendor panel),
#    /delivery (delivery partner panel), /admin (admin panel; the super admin also gets the Owner pages there)
cd fresh-grocery; npm install; npm run dev
#    (without a backend: npm run dev:mock - see fresh-grocery\README.md;
#     delivery ratings on the order page, the delivery panel and the admin panel's deliveries and partners also need
#     delivery-service, the dashboards report-service: step 2 does not list them; the super admin's System page
#     reads Prometheus: start-infra.ps1 -Profiles tools,monitoring)

# 6. Optional: the AI shopping assistant (docs\ai-assistant-plan.md), each service in its own terminal
.\scripts\start-infra.ps1 -Profiles ai          # + Ollama and the Qwen model (a 2.5 GB download the first time)
cd mcp-server-service; .\mvnw spring-boot:run   # the tools (port 8115, internal)
cd mcp-client-service; .\mvnw spring-boot:run   # the chat API (port 8114), used through the gateway:
#    POST http://localhost:8080/api/v1/chat/messages {"message": "How much is Amul butter?"} (mcp-client-service\http\)
.\scripts\evaluate-assistant.ps1                # the 73 golden questions against the real model (about 60 minutes)
```

You don't need every service for every task. Catalog work, for example, only needs product-service, product-read-service (and the gateway).

Clients always call the **gateway on port 8080**. Each service can also be called directly on its own port with its `http/*.http` file. That is how a service is tested on its own.

Spring Boot services run **directly on your machine**. Only the infrastructure runs in Docker.
Each service uses the `local` profile by default, which loads sample data. All seed users have the password `Password@123`;
every demo account, service address and tool login is listed in [docs/local-accounts-and-tools.md](docs/local-accounts-and-tools.md).

## Scripts

| Script | What it does |
|---|---|
| `scripts\start-infra.ps1 [-Profiles tools,monitoring,ai]` | Start the infrastructure, wait until it is healthy, make sure every database exists, create Kafka topics (`tools` = Kibana with data views for the product indexes; `monitoring` = Prometheus, Alertmanager, Grafana, Loki, Jaeger; `ai` = Ollama + the Qwen model) |
| `scripts\check-health.ps1` | Show container status, endpoint health and useful URLs |
| `scripts\check-ai-model.ps1 [-Model qwen3:8b]` | Check that Ollama answers and that the model calls tools (AI assistant) |
| `scripts\evaluate-assistant.ps1 [-Only price,coupons]` | Ask the AI assistant the golden questions (`mcp-client-service\evaluation`) and check every answer |
| `scripts\register-connectors.ps1` | Register every `<service>/debezium/*.json` Debezium connector |
| `scripts\rebuild-product-index.ps1` | Rebuild the Elasticsearch product and stock indexes from Kafka (stop product-read-service first) |
| `scripts\build-images.ps1 [-Services ...] [-KindCluster grocery-cluster-a,...]` | Build the Docker image of every service and of the frontend (and load them into kind clusters) |
| `scripts\k8s-create-clusters.ps1 [-Clusters a,b]` | Create the kind clusters with the Metrics Server and NGINX Gateway Fabric ([Kubernetes](deployment/kubernetes/README.md)) |
| `scripts\k8s-deploy.ps1 [-Clusters a,b] [-SkipImageLoad] [-Restart]` | Load the images into the clusters, apply `deployment/kubernetes`, wait until every pod is ready |
| `scripts\k8s-entry-nginx.ps1 [-Stop]` | The entry NGINX in front of both clusters on http://localhost |
| `scripts\k8s-drills.ps1 [-Drill pod,rolling,...]` | The Kubernetes test scenarios: access, pod loss, rolling update, a service down, autoscaling, Redis down, a cluster down, backup restore |
| `scripts\k8s-delete-clusters.ps1 [-Clusters a,b]` | Delete the kind clusters (the data stays in Docker Compose) |
| `scripts\simulate-delivery.ps1 [-NoDeliver] [-OrderId 42]` | A delivery partner "driving" an order through the real API, to watch the live tracking map ([delivery-tracking](docs/delivery-tracking.md)) |
| `scripts\stop-infra.ps1` | Stop the infrastructure (data is kept) |
| `scripts\reset-infra.ps1` | Delete all infrastructure data and start fresh |

## Repository layout

```text
grocery-app/
├── docs/                 architecture plan, conventions, ports, seed data, event catalog, observability, resilience
├── deployment/kubernetes/ Kubernetes: kind configs of the two clusters, one folder per service, frontend, Gateway, entry NGINX
├── infrastructure/       docker-compose.yml, .env (versions), MySQL init, Kafka topics, Connect image
├── scripts/              PowerShell helper scripts
├── e2e/                  end-to-end scenarios through the gateway (.http)
├── api-gateway/          ✅ single entry point: routing, JWT check, role rules, rate limits, circuit breakers, retries, Swagger UI
├── auth-service/         ✅ users, roles, JWT (reference service)
├── customer-service/     ✅ customer profiles and delivery addresses
├── store-service/        ✅ stores, approval, opening hours, delivery areas, commission
├── product-service/      ✅ catalog WRITES: categories, products, prices, images (MySQL)
├── product-read-service/ ✅ catalog READS: search, filters, sorting, stock (Elasticsearch, fed by Debezium)
├── inventory-service/    ✅ stock per product, low-stock alerts, reserve/release rules for orders
├── cart-service/         ✅ one-store cart, platform and store coupons, checkout summary
├── order-service/        ✅ orders, state machine, store workflow, tracking timeline, the saga
├── payment-service/      ✅ payments and refunds: mock gateway or Razorpay, payment deadline, summary
├── delivery-service/     ✅ delivery partners, KYC, zones, assignment, delivery steps, earnings
├── notification-service/ ✅ in-app notifications and emails from every service's events
├── report-service/       ✅ dashboard, sales reports, top lists, commission (read models built from events)
├── platform-service/     ✅ platform settings, feature flags, banners, audit log
├── mcp-client-service/   ✅ AI assistant (shop and panels): chat API, Ollama (Qwen) + MCP tools, answer check, conversations in MySQL
├── mcp-server-service/   ✅ AI assistant tools over MCP: read-only shop facts and, with the user's login, a customer's cart and orders (and summaries to confirm: changes of the cart, orders and cancellations; no tool changes data), a vendor's store, a partner's deliveries, the admins' numbers (internal, API key)
├── fresh-grocery/        ✅ the website: shop + vendor, delivery, admin and super admin panels, React + TypeScript + Tailwind (its own npm project, see its README)
└── <more services>       added phase by phase - each is a standalone Maven project
```

Each service folder is an independent Spring Boot project with its own `pom.xml`, Maven wrapper, `Dockerfile`, `README.md` and `http/` test requests.
There is no parent POM: every service builds, tests and deploys on its own.
