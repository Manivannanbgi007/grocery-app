# Grocery Platform: Architecture & Phased Development Plan

## Context

`app-prompt.txt` (repo root) holds the original requirements for a multi-vendor grocery platform. It has five panels (Customer, Store/Vendor, Delivery Partner, Admin, Super Admin) and is built as Spring Boot microservices with MySQL, Elasticsearch, Redis, Confluent Kafka + Avro, Debezium CDC and Razorpay. A React + TypeScript + Tailwind frontend comes after the backend. **Status:** approved on 2026-10-25. Work follows the phases in section 4, and progress is tracked in the root `README.md`.

**Decisions confirmed during review**
- One Git repo with a separate folder for each service. Every service is a standalone Maven project (own `pom.xml`, `mvnw`, `Dockerfile`) with no root or parent POM.
- One store per order: a cart only holds items from one store.
- The common classes (`ApiResponse`, `GlobalExceptionHandler`, filters, Swagger config) are copied into each service, not shared as a library.

**Reference development machine:** Java 21.0.12 LTS, Maven 3.9.16, Docker Desktop 29.9 (about 15.5 GB RAM given to Docker, 12 CPUs), Compose v5.6, Node 24, 32 GB RAM. Everything in this plan fits on a machine like this.


---

## 0. Requirements analysis: key findings and interpretations

| # | Finding in `app-prompt.txt` | How this plan handles it |
|---|---|---|
| 1 | The service list has `catalog-service`, but §7 asks for separate write and read services | Split it into **product-service** (MySQL writes) and **product-read-service** (Elasticsearch reads) |
| 2 | Admin and Super Admin are panels, not services | They are **roles**. Their endpoints live in the service that owns the data (for example `/api/v1/admin/stores/**` is in store-service) |
| 3 | The suggested build order puts Kafka at step 6, but §7 needs Debezium + Kafka for product search | Kafka, Schema Registry and Debezium go into the **Phase 0 infrastructure**. Order-saga events arrive with the order phase |
| 4 | Some features have no owning service: platform settings, feature flags, audit log, banners, reviews, support tickets | Product reviews go in product-service and delivery ratings in delivery-service. A new **platform-service** (settings, feature flags, commission, banners, audit log) is added in Phase 11. Support tickets are deferred |
| 5 | "MySQL is the single source of truth" | Every write goes to MySQL. Elasticsearch and report tables are **derived** and can be rebuilt from MySQL or events |
| 6 | "Don't focus on security" | JWT is checked **only at the gateway**, with path-based role rules. Services trust the `X-User-*` headers the gateway adds. There is no service-to-service authentication |
| 7 | Store multi-tenancy rule (every product, order item and coupon has a `store_id`) | The vendor's `storeId` goes into the JWT. The gateway forwards it as `X-Store-Id`, and every vendor query filters by it (`findByIdAndStoreId`) |
| 8 | MVP scope | Phases 0–8 deliver the MVP. Delivery, Razorpay, reports and Kubernetes come after it |

**Assumptions:** INR only, one country; images stored on local disk in development behind an `ImageStorageService` interface (S3/MinIO later); fixed delivery slots behind a feature flag; logout is client-side (stateless JWT).

---

## 1. Development approach (step by step)

1. **Infrastructure first.** One `docker-compose.yml` brings up MySQL, Kafka, Schema Registry, Kafka Connect + Debezium, Elasticsearch, Redis, Mailpit and Kafka UI. Nothing else starts until these are healthy.
2. **Build one reference service ("golden path").** auth-service is built first and sets the standard: package layout, `ApiResponse`, exception handling, filters, Swagger, Flyway + seed data, tests, `.http` file, Dockerfile. **It is reviewed before any other service is built.**
3. **Copy the pattern to each following service.** New services start from start.spring.io plus the standard classes copied from the reference, so every service looks the same.
4. **Make each service work alone before integrating it.** Each service is run and tested directly on its own port using its `.http` file, with manual `X-User-Id`, `X-User-Role` and `X-Store-Id` headers instead of the gateway. Only after that is it added to the gateway and connected to other services through events and REST.
5. **Deliver the MVP first.** Order: auth, gateway, customer/store, catalog, inventory, cart, order + mock payment, notification. Then delivery, Razorpay, reports, Kubernetes, frontend.
6. **Explain decisions before building.** Each phase begins with a short note on what is being built, why, and how it connects. Each phase ends with a **demo checklist** you can run.
7. **Definition of Done for every service:** builds with `mvnw verify`; unit, slice and integration tests are green; Flyway migrations and seed data are in place; the `.http` file has at least 10 working requests; Swagger appears in the gateway UI; README explains how to run it; `docker build` succeeds.

---

## 2. Architecture overview

### 2.1 Runtime topology

```text
        React app (Phase 13) / .http files / Swagger UI
                              │  HTTPS/JSON
                              ▼
               ┌──────────────────────────────┐
               │  api-gateway :8080           │  routing · JWT check · role rules
               │  (Spring Cloud Gateway)      │  rate limit (Redis) · CORS · Swagger UI
               └──────────────┬───────────────┘
    REST + headers: X-User-Id, X-User-Role, X-Store-Id, X-Correlation-Id
  ┌──────┬────────┬───────┬───────┬─────────┬─────────┬──────┬───────┬───────┬────────┬──────┬───────┐
  auth  customer  store  product  product-  inventory  cart  order  payment delivery notif. report
                                   read
  └──┬───┴────┬───┴───┬───┴───┬────┘  │     └────┬────┴───┬──┴───┬───┴───┬───┴────┬───┴──┬───┴───┬───┘
     │ each service has its own DB    │ ES       │        │      │       │        │      │       │
     ▼                                ▼          ▼        ▼      ▼       ▼        ▼      ▼       ▼
  ┌──────────────── MySQL container: auth_db, customer_db, store_db, product_db, … ────────────────┐
  └───────────────────────────────────────────┬────────────────────────────────────────────────────┘
                                              │ binlog
                                              ▼
                     Kafka Connect + Debezium  ──►  Kafka (Avro, Schema Registry)  ──►  consumers
```

### 2.2 Service catalogue

| Service | Port | Store | Owns | Publishes | Consumes |
|---|---|---|---|---|---|
| api-gateway | 8080 | Redis | Routing, JWT check, rate limiting, Swagger UI | – | – |
| auth-service | 8101 | auth_db | Users, roles, credentials, JWT, password reset | UserRegistered, PasswordResetRequested | StoreApproved (sets the vendor's `storeId`) |
| customer-service | 8102 | customer_db | Customer profiles, addresses | – | UserRegistered (creates the profile) |
| store-service | 8103 | store_db | Stores, opening hours, delivery areas, approval, commission | StoreRegistered, StoreApproved, StoreStatusChanged | – |
| product-service | 8104 | product_db | Categories, products, images (writes only) | *table CDC* on `products`, `categories` | – |
| product-read-service | 8105 | Elasticsearch | Product search, filter, sort, paging, details | – | product CDC topics, StockChanged |
| inventory-service | 8106 | inventory_db | Stock, reservations, low-stock alerts | StockChanged, StockReserved, StockReservationFailed, StockReleased, LowStockDetected | OrderCreated, OrderCancelled, OrderRejected |
| cart-service | 8107 | cart_db | Carts, coupons | – | OrderCreated (clears cart, counts coupon usage) |
| order-service | 8108 | order_db | Orders, items, status history, tracking | Order* events (Created, Confirmed, Cancelled, Accepted, Rejected, Packed, ReadyForPickup, OutForDelivery, Delivered) | Stock*, Payment*, Delivery* |
| payment-service | 8109 | payment_db | Payments, refunds, gateway integration | PaymentConfirmed, PaymentFailed, RefundCompleted | OrderCreated, StockReserved, StockReservationFailed, OrderCancelled, OrderRejected, OrderDelivered |
| delivery-service | 8110 | delivery_db | Partners, KYC, availability, zones, charges, assignments, earnings | DeliveryAssigned, DeliveryPickedUp, DeliveryCompleted, DeliveryFailed | UserRegistered, OrderReadyForPickup, OrderCancelled |
| notification-service | 8111 | notification_db | Notification log, in-app messages, contact book | – | Most events (email via Mailpit, console, in-app) |
| report-service | 8112 | report_db + ES | Dashboard metrics, sales reports, admin order search | – | Order, payment, delivery, store and inventory events |
| platform-service *(Phase 11)* | 8113 | platform_db | Settings, feature flags, banners, audit log | SettingsChanged | All events (audit log) |

### 2.3 How services communicate

**A. Client to services:** always through the gateway. Only `/api/v1/**` is exposed. The gateway routes by path prefix:
- `/api/v1/auth/**` goes to auth-service.
- `/api/v1/orders/**`, `/api/v1/vendor/orders/**` and `/api/v1/admin/orders/**` go to order-service.
- `/api/v1/products/**` and `/api/v1/categories/**` are routed by **HTTP method**: `GET` goes to product-read-service; `POST`, `PUT`, `PATCH` and `DELETE` go to product-service. Clients see one URL, and the backend applies CQRS behind it.

Path convention: `/api/v1/vendor/**` → VENDOR, `/api/v1/delivery/**` → DELIVERY_PARTNER, `/api/v1/admin/**` → ADMIN or SUPER_ADMIN, `/api/v1/super-admin/**` → SUPER_ADMIN. Plain customer paths (`/orders`, `/cart`, `/customers/me`) → CUSTOMER.

**B. Synchronous service-to-service calls (REST):** only used when the caller needs an answer right now. They go to `/internal/**` endpoints, which the gateway never exposes. Each call is made through Spring `RestClient`, wrapped in a small `XxxClient` class with timeouts. The base URL comes from config (`localhost:81xx` locally, `http://cart-service` in Kubernetes). These are the only synchronous calls:

| Caller → Callee | Why |
|---|---|
| order → cart | Get the checkout summary (items, prices, coupon discount) when placing an order |
| order → customer | Copy the delivery address into the order |
| order → store | Copy the store name and address; check the store is open |
| cart → product-read | Get the current price and availability when adding an item |
| inventory → product-read | Check that a product belongs to the vendor's store |

**C. Asynchronous events (Kafka + Avro), used everywhere else.** There are two Debezium mechanisms, each used where it fits best:

1. **Transactional outbox + Debezium Outbox Event Router** for **domain events between services**. Inside the same MySQL transaction as the business change, the service inserts a row into its `outbox_event` table. The payload is an Avro-serialized event (Confluent wire format, schema registered in Schema Registry). Debezium reads the binlog and publishes the row to `grocery.<aggregate>.events`. The service never talks to Kafka directly, so a business change and its event can never get out of sync ("dual write" problem solved).
2. **Plain table CDC** for **product → Elasticsearch** (as §7 requires). Debezium captures the `product_db.products` and `product_db.categories` tables, flattens each change with the `ExtractNewRecordState` SMT, and writes Avro through the AvroConverter. product-read-service consumes those changes and indexes them. Debezium's **initial snapshot** also loads the seed data into Elasticsearch, so the index can be rebuilt at any time by re-snapshotting.

**Rule:** table CDC is used only inside one bounded context (product write → product read, which are one "catalog" context). Between contexts, services publish named domain events. That way other teams never depend on another service's table design.

**Kafka conventions**
- Topics: `grocery.user.events`, `grocery.store.events`, `grocery.inventory.events`, `grocery.order.events`, `grocery.payment.events`, `grocery.delivery.events`; `cdc.product_db.products`, `cdc.product_db.categories`; plus one `<topic>.DLT` (dead-letter topic) for each.
- Message key is the aggregate id (for example `orderId`). This keeps all events for one order in order.
- One topic holds several event types (`TopicRecordNameStrategy`). The consumer uses a class-level `@KafkaListener` with one `@KafkaHandler` method per event type, which is easy to read.
- Every event has `eventId` (UUID), `occurredAt` and the aggregate id. Events are named in the past tense.
- Avro `.avsc` files live in the **producer** (`src/main/avro/produced`). Consumers keep a copy in `src/main/avro/consumed`. `avro-maven-plugin` generates the Java classes. Schema Registry enforces **BACKWARD** compatibility, so an incompatible change fails registration.

**Reliability (retries, duplicates, failures)**
- **Idempotent consumers:** each consuming service has a `processed_event(event_id PK)` table. The event id is inserted in the same transaction as the business change, so a duplicate delivery hits the primary key and is skipped.
- **Elasticsearch idempotency:** the document version is the product's JPA `@Version`, written with Elasticsearch external versioning. Older or duplicate changes are rejected (409) and safely ignored, so out-of-order or replayed events never overwrite newer data.
- **Retries:** Spring Kafka `DefaultErrorHandler` with exponential backoff for temporary failures (database or Elasticsearch down). Errors that will never succeed (bad data, deserialization failure via `ErrorHandlingDeserializer`) go straight to the `.DLT` topic. DLT messages can be inspected in Kafka UI and replayed.
- **State guards:** entity methods ignore events that no longer apply (for example `PaymentConfirmed` arriving for an order that is already `CANCELLED` triggers a refund instead of confirming it).

### 2.4 Order saga (choreography, no central orchestrator)

```text
Customer POST /api/v1/orders ─► order-service: Order PENDING + OrderCreated (outbox)
OrderCreated ─► inventory: reserve stock ─► StockReserved | StockReservationFailed
OrderCreated ─► payment:   create Payment(PENDING, amount, method)
OrderCreated ─► cart:      clear cart, count coupon usage
StockReserved ─► payment:  COD → PaymentConfirmed(COD)
                           ONLINE → customer pays (mock in MVP, Razorpay in Phase 10)
                                    → PaymentConfirmed | PaymentFailed
PaymentConfirmed ─► order: CONFIRMED ─► OrderConfirmed ─► notification (customer + store)
StockReservationFailed / PaymentFailed ─► order: CANCELLED ─► OrderCancelled ─► inventory releases stock
Store: accept → ACCEPTED | reject → REJECTED ─► OrderRejected ─► release stock + refund
Store: pack → PACKED → READY_FOR_PICKUP ─► OrderReadyForPickup ─► delivery assigns a partner
DeliveryPickedUp ─► OUT_FOR_DELIVERY;  DeliveryCompleted ─► DELIVERED ─► OrderDelivered
Customer cancel (allowed until OUT_FOR_DELIVERY) ─► CANCELLED ─► release stock + refund if paid
```

- The order lifecycle is a **state machine inside the `Order` entity** (DDD aggregate). Methods like `confirm()`, `accept()` and `cancel()` check the allowed transitions defined in the `OrderStatus` enum. Every change is recorded in `order_status_history`, which also drives the tracking timeline.
- Saga timeout (later phase): an online payment still pending after 15 minutes becomes `PaymentFailed(EXPIRED)`, which runs the compensation steps.
- Before delivery-service exists (MVP), an admin moves orders to OUT_FOR_DELIVERY and DELIVERED by hand.

### 2.5 CQRS and DDD: where they are used

- **CQRS:** fully applied to products (two services). report-service builds read models for dashboards and admin order search. Not applied to "my orders" or "my cart": those read from MySQL because users expect to see their own writes immediately.
- **DDD:** one bounded context per service. Aggregates have behaviour (`Order` state machine, `Cart` single-store rule, `Stock.reserve()` and `Stock.release()`). Snapshots are value objects (`DeliveryAddress` `@Embeddable`). Domain events are named in business language. No hexagonal architecture: the layered MVC structure you asked for stays, because juniors find it easier to follow.

### 2.6 Cross-cutting

- **Gateway:** Spring Cloud Gateway (WebFlux). Spring Security resource server validates the JWT (HS256 shared secret from an environment variable). Path-to-role rules live in one `SecurityConfig`. A `GlobalFilter` adds the `X-User-*` headers and `X-Correlation-Id`. It also handles CORS and `RedisRateLimiter`. The Swagger UI has a dropdown of every service's `/v3/api-docs` (springdoc).
- **Redis:** gateway rate limiting; Spring Cache for slow-changing lookups (store details, platform settings); delivery-partner online presence (with TTL); password-reset tokens (with TTL). The cart stays in MySQL, because MySQL is the source of truth. *As built:* Redis holds only the gateway's rate-limit counters and the partner presence; password-reset tokens went to MySQL (`auth_db.password_reset_tokens`) and no Redis cache was needed (see [redis-insight.md](redis-insight.md)).
- **No Eureka, no Config Server.** URLs and settings come from `${ENV_VAR:local-default}` placeholders. Kubernetes later supplies them through ConfigMaps and Secrets, and its own DNS replaces service discovery. This means no code changes when moving to Kubernetes.
- **Observability:** Actuator health with liveness and readiness groups (ready for Kubernetes probes), a Prometheus endpoint, and a correlation id in every log line (MDC). Prometheus and Grafana run in an optional Compose profile.

### 2.7 Tech stack (versions pinned in Phase 0, 2026-10-25)

| Area | Choice and version |
|---|---|
| Language and framework | Java 21 LTS · Spring Boot **4.1.2** · Spring Cloud **2025.1.4** |
| Service libraries | Spring Web MVC, Data JPA, Validation, Actuator · Flyway 12 (MySQL module) · Lombok · springdoc-openapi **3.2.0** · jjwt **0.13.0** |
| Messaging libraries | Spring Kafka 4.1 (Kafka client 4.2.2) · Confluent `kafka-avro-serializer` **8.2.4** · Avro 1.12 + `avro-maven-plugin` |
| Read and cache libraries | Spring Data Elasticsearch (client 9.4.8) · Spring Data Redis |
| Testing | JUnit 5, Mockito, AssertJ · Testcontainers **2.0.5** |
| Infrastructure images | MySQL **8.4.13** LTS · Confluent Platform **8.2.4** (Kafka 4.2 in KRaft mode, Schema Registry, Connect) · Debezium MySQL connector **3.7.0.Final** · Elasticsearch / Kibana **9.4.8** (same version as the client) · Redis 8 · Mailpit · Kafka UI (kafbat) |

Exact image tags are in `infrastructure/.env`; library versions are in each service's `pom.xml`. Confluent Platform 8.2.x is used (not 8.3) because it ships Kafka 4.2, the client version Spring Boot 4.1 manages.

---

## 3. Project structure: one complete microservice (order-service)

order-service is shown because it uses **every** building block (REST, validation, mapper, Flyway, outbox, Kafka consumers, REST clients, state machine). Simpler services use a subset of the same packages. For example, product-service has no `messaging/` or `client/` package.

```text
order-service/
├── pom.xml                                   # parent = spring-boot-starter-parent (no shared parent POM)
├── mvnw, mvnw.cmd, .mvn/                     # Maven wrapper: builds without a local Maven install
├── Dockerfile                                # multi-stage: build jar → run on a slim JRE 21 image
├── README.md                                 # what this service owns, how to run and test it
├── debezium/
│   └── order-outbox-connector.json           # Debezium connector that publishes this service's outbox
├── http/
│   └── order-service.http                    # 10+ ready-to-run requests using the seed data
└── src/
    ├── main/
    │   ├── avro/
    │   │   ├── produced/                     # contracts this service owns
    │   │   │   ├── OrderCreated.avsc
    │   │   │   ├── OrderConfirmed.avsc
    │   │   │   └── OrderCancelled.avsc …
    │   │   └── consumed/                     # copies of other services' contracts
    │   │       ├── StockReserved.avsc
    │   │       └── PaymentConfirmed.avsc …
    │   ├── java/com/grocery/order/
    │   │   ├── OrderServiceApplication.java
    │   │   ├── client/                       # synchronous REST calls to other services
    │   │   │   ├── CartClient.java
    │   │   │   ├── CustomerClient.java
    │   │   │   ├── StoreClient.java
    │   │   │   └── dto/                      # CartSummaryDto, AddressDto, StoreDto
    │   │   ├── config/
    │   │   │   ├── OpenApiConfig.java        # Swagger title, version, servers
    │   │   │   ├── RestClientConfig.java     # base URLs + timeouts
    │   │   │   ├── KafkaConsumerConfig.java  # retry backoff + dead-letter topic
    │   │   │   ├── AvroSerializerConfig.java # serializer used to fill the outbox payload
    │   │   │   └── JpaAuditingConfig.java    # fills createdAt / updatedAt and createdBy / updatedBy automatically
    │   │   ├── constant/                     # ApiPaths, KafkaTopics, AppConstants
    │   │   ├── context/                      # UserContext + UserContextHolder (userId, role, storeId)
    │   │   ├── controller/                   # HTTP only: validate input, call service, wrap in ApiResponse
    │   │   │   ├── CustomerOrderController.java   # /api/v1/orders          (CUSTOMER)
    │   │   │   ├── VendorOrderController.java     # /api/v1/vendor/orders   (VENDOR)
    │   │   │   ├── AdminOrderController.java      # /api/v1/admin/orders    (ADMIN, SUPER_ADMIN)
    │   │   │   └── InternalOrderController.java   # /internal/orders        (other services)
    │   │   ├── dto/
    │   │   │   ├── common/                   # ApiResponse, PageResponse, ErrorDetail (same in every service)
    │   │   │   ├── request/                  # PlaceOrderRequest, CancelOrderRequest, RejectOrderRequest …
    │   │   │   └── response/                 # OrderResponse, OrderSummaryResponse, OrderTrackingResponse …
    │   │   ├── entity/
    │   │   │   ├── BaseEntity.java           # id, createdAt, updatedAt, createdBy, updatedBy, version (@Version)
    │   │   │   ├── Order.java                # aggregate root + rules: confirm(), accept(), cancel() …
    │   │   │   ├── OrderItem.java
    │   │   │   ├── OrderStatusHistory.java
    │   │   │   ├── DeliveryAddress.java      # @Embeddable snapshot of the address
    │   │   │   ├── OutboxEvent.java
    │   │   │   └── ProcessedEvent.java       # idempotency record
    │   │   ├── enums/                        # OrderStatus (with allowed transitions), PaymentMethod
    │   │   ├── exception/
    │   │   │   ├── GlobalExceptionHandler.java    # @RestControllerAdvice → ApiResponse errors
    │   │   │   ├── ResourceNotFoundException.java # 404
    │   │   │   ├── BusinessException.java         # 409 / 422 business rule broken
    │   │   │   ├── InvalidOrderStateException.java
    │   │   │   └── ExternalServiceException.java  # 503 when a called service fails
    │   │   ├── filter/
    │   │   │   ├── CorrelationIdFilter.java  # reads or creates X-Correlation-Id, puts it in the log MDC
    │   │   │   └── UserContextFilter.java    # reads the X-User-* headers into UserContextHolder
    │   │   ├── mapper/
    │   │   │   ├── OrderMapper.java          # entity ↔ DTO (hand-written, easy to debug)
    │   │   │   └── OrderEventMapper.java     # entity → Avro event
    │   │   ├── messaging/
    │   │   │   ├── outbox/OutboxEventPublisher.java   # saves the event to the outbox table (same transaction)
    │   │   │   └── consumer/                          # InventoryEventsConsumer, PaymentEventsConsumer, DeliveryEventsConsumer
    │   │   ├── repository/                   # OrderRepository, OutboxEventRepository, ProcessedEventRepository
    │   │   └── service/
    │   │       ├── OrderService.java         # interfaces: what the service can do
    │   │       ├── VendorOrderService.java
    │   │       ├── OrderSagaService.java     # reactions to events from other services
    │   │       └── impl/                     # OrderServiceImpl … (@Transactional business logic)
    │   └── resources/
    │       ├── application.yml               # defaults with ${ENV:localDefault} placeholders
    │       ├── application-local.yml         # local-only: seed data, debug logging
    │       └── db/
    │           ├── migration/                # V1__create_order_tables.sql, V2__create_outbox_event_table.sql …
    │           └── seed/                     # R__insert_sample_orders.sql (repeatable, local profile only)
    └── test/java/com/grocery/order/
        ├── entity/OrderTest.java                       # status-transition rules (plain JUnit)
        ├── service/OrderServiceImplTest.java           # business logic (Mockito)
        ├── controller/CustomerOrderControllerTest.java # @WebMvcTest: validation, status codes, response shape
        ├── repository/OrderRepositoryTest.java         # @DataJpaTest + Testcontainers MySQL + Flyway
        └── messaging/PaymentEventsConsumerIT.java      # Kafka + MySQL Testcontainers, duplicate handling
```

**How a request flows (same in every service):** `Filter → Controller (@Valid DTO) → Service interface → ServiceImpl (@Transactional, rules, Mapper) → Repository → Entity`. The response is always `ApiResponse<T>`. Controllers never return entities.

**Coding conventions (written down in `docs/conventions.md`)**
- **DTOs** are Java `record`s with Jakarta Validation annotations. **Entities** use Lombok `@Getter @Setter @NoArgsConstructor`, never `@Data`. Services use `@RequiredArgsConstructor` and `@Slf4j`.
- **Mappers** are hand-written `@Component` classes, so juniors can step through them in a debugger.
- **Common response shape**, the same in all services:
  ```json
  { "success": true, "message": "Order placed successfully", "data": { … },
    "errors": null, "timestamp": "2026-10-25T10:15:30Z", "path": "/api/v1/orders" }
  ```
  On errors: `"success": false`, `"data": null`, `"errors": [{ "field": "quantity", "message": "must be at least 1" }]`. Paged data uses `PageResponse { content, page, size, totalElements, totalPages, last }`.
- **Seed data:** local-only seed data lives in `db/seed/R__*.sql` *repeatable* migrations using `INSERT IGNORE`. They always run after the versioned schema migrations, so new `V__` files can be added at any time.
- **Database:** snake_case tables; `BIGINT` ids; `DECIMAL(10,2)` for money (`BigDecimal` in Java); UTC timestamps (`Instant`); `ddl-auto: validate` (only Flyway changes the schema); applied migrations are never edited.
- **Config:** every environment-specific value is `${ENV_VAR:local-default}`, for example `url: ${DB_URL:jdbc:mysql://localhost:3307/order_db}` and `port: ${SERVER_PORT:8108}`.
- **`.http` files:** at least 10 requests per service, with `@baseUrl` and header variables at the top, using ids from the shared seed data listed in `docs/seed-data.md`. Example: user 1 = super admin, user 3 = vendor of store 1, user 4 = customer, password `Password@123`.

---

## 4. Implementation plan (phase by phase)

Every phase ends with a **review checkpoint**: the phase is demoed and approved before the next one starts.

**Repository layout**
```text
grocery-app/
├── README.md                  # start here: how to run everything
├── docs/                      # architecture.md, conventions.md, event-catalog.md, seed-data.md, ports.md
├── infrastructure/            # docker-compose.yml, .env, mysql/init, kafka/topics, kafka-connect/Dockerfile, prometheus/, grafana/
├── scripts/                   # start-infra.ps1, stop-infra.ps1, reset-infra.ps1, register-connectors.ps1, check-health.ps1
├── e2e/                       # cross-service scenarios through the gateway (mvp-order-flow.http …)
├── api-gateway/  auth-service/  customer-service/  store-service/  product-service/  product-read-service/
├── inventory-service/  cart-service/  order-service/  payment-service/  delivery-service/
├── notification-service/  report-service/  platform-service/
└── grocery-frontend/          # Phase 13
```

| Phase | Scope | Key deliverables | Done when |
|---|---|---|---|
| **0. Foundations** | No business code | Repo skeleton, `docs/`, Compose infrastructure, MySQL init script (one DB and one user per service with grants only on its own DB, plus a Debezium user), topic-creation script, Connect image with the Debezium MySQL plugin, PowerShell scripts, pinned versions | `start-infra.ps1` shows everything healthy; Kafka UI shows the cluster; Connect lists the MySQL plugin; Elasticsearch responds |
| **1. auth-service (reference)** | Register, login, JWT, `/me`, forgot and reset password, roles; seed users for all 5 roles | The standard package (ApiResponse, exception handler, filters, Swagger, Flyway seed, tests, `.http`, Dockerfile) | **Code style reviewed; all later services copy it** |
| **2. api-gateway** | Routes, JWT validation, path-to-role rules, header propagation, correlation id, CORS, Redis rate limit, aggregated Swagger UI | `SecurityConfig`, route YAML, `UserHeadersFilter` | Login through the gateway; a role-protected call gets 401/403 or 200 as expected |
| **3. customer + store services** | Profiles and addresses; store registration, admin approval, opening hours, delivery areas, enable/disable | **First outbox + Debezium flow:** UserRegistered → customer-service; StoreApproved → auth-service (vendor `storeId` added to the JWT) | Register a vendor → admin approves → vendor's new token contains `storeId` |
| **4. Catalog CQRS** | product-service (categories, products, image upload, price, discount, tax, unit, status, soft delete). Product CDC connector. product-read-service (index mapping, CDC consumer, search, filters, sort, paging, details, vendor list) | Method-based gateway routes; Elasticsearch idempotency through external versions; DLT | Create or update a product → searchable within about 1–2 s; replaying the topic causes no duplicates; seed products are searchable after the first snapshot |
| **5. inventory-service** | Vendor stock management, low-stock threshold and alerts, `reserve()` and `release()` logic (tested now, wired in Phase 7) | StockChanged → product-read updates `inStock` in Elasticsearch | Setting stock to 0 shows "out of stock" in search |
| **6. cart-service** | Cart (single-store rule, price snapshot from product-read), coupons (platform and store, percent or flat, minimum order, expiry, usage limit), internal checkout summary | Coupon management for vendors and admins | Add items, apply a coupon, totals are correct; adding from another store returns a clear 409 |
| **7. order + payment (mock) + saga** | Place order, store accept/reject/pack/ready, customer cancel, admin order view and override, tracking timeline. payment-service with a `PaymentGateway` interface → `MockPaymentGateway` | Full choreography from §2.4 including the compensation steps; idempotent consumers | Happy path, out-of-stock path, payment-failure path and cancel path all end in the correct state in every service |
| **8. notification-service → MVP done** | Consumes events; email through Mailpit, console log, in-app notifications; local contact book built from UserRegistered | `e2e/mvp-order-flow.http` | **MVP:** the whole flow works through the gateway, and emails appear in Mailpit |
| **9. delivery-service** | Partner KYC (admin approval), online/offline (Redis presence), assignment on OrderReadyForPickup (first available online partner in the zone), accept/reject/reassign, status flow, proof-of-delivery upload, issue reporting, earnings, delivery zones and charges | order-service consumes delivery events; the manual MVP status override is removed | Order goes from READY_FOR_PICKUP to DELIVERED, driven by the partner's actions |
| **10. Razorpay + refunds** | `RazorpayPaymentGateway` (create order, verify signature, webhook), refunds on cancel or reject, payment timeout, settlement report | Gateway chosen with `payment.gateway=mock\|razorpay` | Razorpay test-mode payment and refund work end to end |
| **11. Reports + admin/super-admin extras** | report-service (dashboard metrics from the prompt, sales by day/store/product, Elasticsearch order search). platform-service (settings, feature flags, commission, banners, audit log). Product reviews, delivery ratings, invoice PDF | Admin and super-admin endpoints complete | Dashboard numbers match the source data |
| **12. Production readiness** | Prometheus/Grafana dashboards, tracing, resilience review (timeouts, retries, circuit breakers only where needed), Dockerfiles checked, `k8s/` manifests per service (Deployment with probes, Service, ConfigMap, Secret) + Ingress | – | Images build; manifests apply to a local cluster (kind or Docker Desktop Kubernetes) |
| **13. Frontend** | React + TypeScript + Vite + Tailwind; one app with role-based panels in this order: customer → vendor → admin → delivery → super admin; calls go only to the gateway (Vite proxy) | A separate detailed frontend plan for review first | Every MVP flow works from the UI |

**How each microservice is built (same order every time)**
1. Generate the project on start.spring.io and copy in the standard classes.
2. Flyway schema and seed data.
3. Entities and repositories, with repository tests.
4. DTOs, validation and mapper.
5. Service interface and implementation, with unit tests.
6. Controllers, with web-slice tests and Swagger annotations.
7. Events (Avro schemas, outbox, consumers), with integration tests.
8. `.http` file and README.
9. Gateway route and Swagger entry.
10. Integration check with the other services.
11. Dockerfile.

**Testing layers**
- Unit tests: entities, services and mappers.
- `@WebMvcTest`: controllers.
- `@DataJpaTest` + Testcontainers MySQL: repositories and migrations.
- `@SpringBootTest` + Testcontainers (MySQL, Kafka, Elasticsearch; Schema Registry replaced by `mock://`): event consumers and idempotency.
- Manual `.http` files.
- End-to-end `e2e/*.http` through the gateway at each milestone.

The Debezium pipeline itself is checked in the local environment. The tests check that the correct outbox row is written.

**Independent deployment:** each service has its own version in its `pom.xml`, its own Dockerfile (`docker build -t grocery/order-service:<ver> .`), and later its own CI workflow (triggered only by changes in `order-service/**`) and its own Kubernetes manifests (`deployment/kubernetes/services/order-service/`). No service build depends on any other folder.

---

## 5. Local development environment

**Docker Compose** (`infrastructure/docker-compose.yml`) runs only the infrastructure:

| Container | Host port | Profile | Notes |
|---|---|---|---|
| mysql (8.4) | 3307 (container port 3306) | core | Init script creates `auth_db … platform_db` and one user per DB; binlog in ROW format for Debezium. Host port 3307 avoids clashing with a locally installed MySQL |
| kafka (Confluent, KRaft) | 9092 | core | Single broker; `kafka-init` creates the topics and DLTs |
| schema-registry | 8081 | core | BACKWARD compatibility |
| kafka-connect + Debezium | 8083 | core | Custom image: Confluent Connect + Debezium MySQL plugin |
| elasticsearch | 9200 | core | Single node, security off, 512 MB heap |
| redis | 6379 | core | – |
| mailpit | 1025 (SMTP) / 8025 (UI) | core | Fake inbox for emails |
| kafka-ui | 8090 | core | Browse topics, messages, DLTs, schemas, connectors |
| redisinsight | 5540 | core | Browse Redis keys, values and TTLs ([guide](redis-insight.md)) |
| kibana | 5601 | `tools` | Optional Elasticsearch browser |
| prometheus / alertmanager / blackbox-exporter / grafana / loki / jaeger | 9090 / 9093 / 9115 / 3000 / 3100 / 16686 | `monitoring` | Optional: metrics, alerts, health checks, dashboards, logs, traces ([observability](observability.md)) |

Every container has a healthcheck and a named volume. `reset-infra.ps1` wipes the volumes for a clean start. The core stack needs about 5–6 GB, which fits within the 15.5 GB Docker currently has.

**Spring Boot apps run directly on your machine** (not in Docker), on ports 8080–8113:
- From VS Code (Spring Boot Dashboard) or IntelliJ, with the `local` profile, or
- `.\mvnw spring-boot:run -Dspring-boot.run.profiles=local` inside a service folder.

They connect to `localhost` infrastructure through the defaults in `application.yml`, so no setup is needed. The `local` profile also loads the `db/seed` migrations. You only run the services the current task needs: for example, catalog work needs only product-service and product-read-service.

**Daily workflow**
1. `scripts\start-infra.ps1`: `docker compose up -d`, then wait until everything is healthy.
2. Start the services you need. Flyway creates their schema and seed data on startup.
3. `scripts\register-connectors.ps1`: registers every `*/debezium/*.json` connector through the Connect REST API. It is idempotent (`PUT /connectors/{name}/config`), so it can be re-run safely.
4. Test with each service's `http/*.http` file directly, or through the gateway at `http://localhost:8080`. Swagger UI is at `http://localhost:8080/swagger-ui.html`, Kafka UI at `:8090`, Mailpit at `:8025`.
5. `scripts\stop-infra.ps1` when you are done; data is kept.

**Moving to Kubernetes later:** the same jars run in containers. Kubernetes only changes the `${ENV}` values (ConfigMaps and Secrets), sets `SERVER_PORT=8080`, and replaces the infrastructure with managed or Helm-installed equivalents. No code changes are needed.

---

## Verification (how each phase is proven to work)

- **Per service:** `.\mvnw verify` is green (unit, slice and Testcontainers tests). The app starts with the `local` profile. All requests in its `.http` file return the expected `ApiResponse`. Its Swagger appears in the gateway UI.
- **Event flows:** in Kafka UI, check the event on the expected topic with a registered schema. Replay a message (or restart a consumer from an old offset) and confirm no duplicate side effects. Send a bad message and confirm it lands in the `.DLT` topic.
- **CQRS:** after a product write, `GET /api/v1/products?q=…` reflects it within seconds. Deleting the Elasticsearch index and re-snapshotting the connector rebuilds it from MySQL.
- **MVP milestone (end of Phase 8):** run `e2e/mvp-order-flow.http` through the gateway: customer registers, logs in, searches, adds to cart, applies a coupon, places a COD order → vendor accepts and packs → admin marks it delivered → customer sees the tracking timeline → emails appear in Mailpit. Also run the out-of-stock, payment-failure and cancel scenarios and confirm the compensation steps restore stock.
