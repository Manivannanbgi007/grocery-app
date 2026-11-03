# Coding Conventions

Every microservice follows these rules, so code looks the same in every service.
**auth-service is the reference implementation**: when in doubt, look at how it does it.

## 1. Creating a new service

1. Generate the project on [start.spring.io](https://start.spring.io): Maven, Java 21, Spring Boot 4.1.2, group `com.grocery`, package `com.grocery.<name>`.
   Dependencies: Spring Web, Spring Data JPA, Validation, Actuator, Prometheus, Flyway, MySQL Driver, Lombok, Testcontainers.
2. Add `springdoc-openapi-starter-webmvc-ui` (see auth-service `pom.xml`).
3. Copy these classes from auth-service and change only the package name:

   | Copy from auth-service | Purpose |
   |---|---|
   | `dto/common/ApiResponse`, `PageResponse`, `ErrorDetail` | Standard response shape |
   | `exception/GlobalExceptionHandler` + the 5 exception classes | Standard error handling |
   | `filter/CorrelationIdFilter`, `filter/UserContextFilter` | Correlation id, gateway user headers |
   | `context/UserContext`, `context/UserContextHolder` | "Who is calling?" |
   | `constant/HeaderNames` | Shared header names |
   | `config/OpenApiConfig`, `config/JpaAuditingConfig` | Swagger, `createdAt`/`updatedAt`, `createdBy`/`updatedBy` |
   | `entity/BaseEntity` | id, audit columns, `@Version` |
   | `TestcontainersConfiguration` (test) | Real MySQL in tests |
   | `logging/LoggingAspect`, `logging/Loggable`, `src/main/resources/logback-spring.xml` + `logging/LoggingAspectTest` and `logging/sample/` (test) | Centralized logging (Spring AOP) and logs to Grafana Loki ([observability.md](observability.md)); needs `spring-boot-starter-aspectj`, `loki-logback-appender` and the `app.logging` block of `application.yml` |

   If the service **publishes** or **consumes** events, also copy the classes listed in [section 9](#9-events-kafka--avro).
4. Copy `application.yml` / `application-local.yml` and change the port, database and user.
5. Add `http/<service>.http` (at least 10 requests), `README.md`, `Dockerfile`, `.dockerignore`.
6. Register the service in **api-gateway** (see "Adding a new service" in [api-gateway/README.md](../api-gateway/README.md)):
   two routes in `application.yml` (API paths + `/<service>/v3/api-docs`) and one entry in `springdoc.swagger-ui.urls`.

## 2. Package structure

```text
com.grocery.<service>
├── client/       REST calls to other services (only when needed)
├── config/       @Configuration classes and typed @ConfigurationProperties records
├── constant/     constants (header names, patterns, topic names)
├── context/      UserContext + UserContextHolder
├── controller/   REST controllers - HTTP only, no business logic
├── dto/          common/, request/, response/  (Java records)
├── entity/       JPA entities + business rules that belong to them
├── enums/        enums (statuses, roles, types)
├── exception/    GlobalExceptionHandler + exception classes
├── filter/       servlet filters
├── logging/      LoggingAspect (logs controller / service / client / consumer / @Scheduled calls) + @Loggable
├── mapper/       hand-written entity <-> DTO mappers
├── messaging/    Kafka outbox publisher and consumers (from Phase 3)
├── repository/   Spring Data JPA repositories
├── service/      interfaces;  service/impl/ = implementations
└── validator/    business checks that need the database
```

**Request flow:** `Filter → Controller (@Valid DTO) → Service interface → ServiceImpl (@Transactional) → Repository → Entity`.
Controllers never return entities. They always return `ResponseEntity<ApiResponse<T>>`.

## 3. Naming

| Thing | Rule | Example |
|---|---|---|
| Controller | `<Panel><Resource>Controller` | `AdminUserController`, `VendorOrderController` |
| Service | `<Resource>Service` + `<Resource>ServiceImpl` | `UserAdminService`, `UserAdminServiceImpl` |
| Request DTO | verb + resource + `Request` | `CreateAdminRequest`, `UpdateUserStatusRequest` |
| Response DTO | resource + `Response` | `UserResponse` |
| Table | snake_case, plural | `users`, `password_reset_tokens` |
| Column | snake_case | `full_name`, `created_at` |
| Migration | `V<n>__<what>.sql` | `V2__create_password_reset_tokens_table.sql` |
| Seed data | `R__<what>.sql` in `db/seed` | `R__insert_default_users.sql` |
| Kafka topic | `grocery.<aggregate>.events` | `grocery.order.events` |
| Event | past tense | `OrderCreated`, `StockReserved` |

## 4. REST API rules

- Every path starts with `/api/v1`.
- The **panel prefix** tells the gateway which role is required:

  | Prefix | Roles |
  |---|---|
  | `/api/v1/auth/**` | public (except `/me`) |
  | `/api/v1/vendor/**` | VENDOR |
  | `/api/v1/delivery/**` | DELIVERY_PARTNER |
  | `/api/v1/admin/**` | ADMIN, SUPER_ADMIN |
  | `/api/v1/super-admin/**` | SUPER_ADMIN |
  | other `/api/v1/**` (orders, cart, customers/me) | CUSTOMER |
  | `/internal/**` | service-to-service only, never routed by the gateway |

- Use plural nouns (`/users/{userId}`). Use `PATCH /{id}/status` for state changes and `POST` for create.
- Return `201 Created` for create, `200 OK` for everything else that succeeds.
- Paging uses `page` (from 0), `size` (max 100) and `sort=field,asc|desc`, and returns a `PageResponse`.

**Response shape** (every endpoint, every service):

```json
{ "success": true, "message": "User fetched successfully", "data": { },
  "errors": null, "timestamp": "2026-10-25T10:15:30Z", "path": null }
```

```json
{ "success": false, "message": "Validation failed", "data": null,
  "errors": [ { "field": "email", "message": "must be a well-formed email address" } ],
  "timestamp": "2026-10-25T10:15:30Z", "path": "/api/v1/auth/register" }
```

**Status codes:**

| Code | Meaning | Exception |
|---|---|---|
| 400 | Invalid request or broken business rule | validation errors, `BusinessException` |
| 401 | Not logged in or wrong credentials | `UnauthorizedException` |
| 403 | Logged in, but not allowed | `ForbiddenException` |
| 404 | Record or endpoint not found | `ResourceNotFoundException` |
| 409 | Duplicate record or concurrent update | `DuplicateResourceException`, optimistic lock |
| 500 | Unexpected error (details only in the log) | anything else |

## 5. Java style

- **DTOs** are Java `record`s with Jakarta Validation annotations (`@NotBlank`, `@Email`, `@Size`, `@Pattern`).
- **Optional true/false fields in requests are `Boolean`, never `boolean`.** Jackson 3 (Spring Boot 4) rejects a request that
  leaves out a primitive `boolean` with a 400. Add a small helper on the record, for example
  `public boolean wantsDefault() { return Boolean.TRUE.equals(setAsDefault); }` (see customer-service `AddressRequest`).
- **Entities** use Lombok `@Getter @Setter @NoArgsConstructor`, **never `@Data`** (it breaks JPA `equals`/`hashCode`).
  Put business rules on the entity (`user.isActive()`, `token.isUsable(now)`).
- **Services** use constructor injection with `@RequiredArgsConstructor` and `@Slf4j`. `@Transactional` goes on service methods, with `readOnly = true` for reads.
- **Mappers** are hand-written `@Component` classes that only copy fields. Business decisions stay in the service.
- **No magic numbers or strings**: put them in `constant/` or in `application.yml` (typed with a `@ConfigurationProperties` record).
- **Log** with ids, never with passwords or tokens: `log.info("User id={} logged in", id)`.
- **Do not log "started / finished / failed" or durations by hand**: `LoggingAspect` already logs every controller,
  service, client, Kafka consumer and `@Scheduled` call with its outcome and time. Keep the package names of section 2
  (`controller`, `service`, `client`, `messaging/consumer`), or put `@Loggable` on a bean elsewhere. Write your own log
  lines only for business facts with their ids ("Order id=18 placed"). Unexpected errors are logged with their stack
  trace by `GlobalExceptionHandler` - do not log and rethrow.

## 6. Database

- Flyway owns the schema (`ddl-auto: validate`). **Never edit a migration that has already run**. Add a new `V<n+1>__...sql` instead.
- Every table has `id BIGINT AUTO_INCREMENT`, `created_at`, `updated_at` (`DATETIME(6)`, UTC), `created_by`,
  `updated_by` (`VARCHAR(50)`) and `version BIGINT`. These come from `BaseEntity`; child rows that are only saved with
  their parent (order items, reservation items, opening hours) and the technical tables (`outbox_event`,
  `processed_event`, report facts) do not extend it.
- **Who** added a row and who changed it last: `created_by` / `updated_by`, filled by Spring Data auditing
  (`@CreatedBy` / `@LastModifiedBy` in `BaseEntity`, the `auditorAware` bean in `JpaAuditingConfig`). Never set them
  by hand.
  - A logged-in user is saved as role and id from the gateway headers: `ADMIN:1`, `VENDOR:12`, `CUSTOMER:4`,
    `DELIVERY_PARTNER:5` (see `UserContextHolder.currentAuditor()`).
  - `SYSTEM` when nobody is logged in: Kafka consumers, `@Scheduled` jobs, and public endpoints (registration, login,
    forgot password, the Razorpay webhook).
  - Empty (`NULL`) for rows older than the columns, and for demo seed rows.
  - Only the **last** change is kept. A login updates `last_login_at`, so the user's `updated_by` becomes `SYSTEM`. A
    review updates the product's rating, so the product's `updated_by` becomes that customer. The history of events is
    in the audit log (`GET /api/v1/super-admin/audit-log`) and an order's timeline in `order_status_history`.
  - Example, in the MySQL container (see [local-accounts-and-tools.md](local-accounts-and-tools.md)):
    `SELECT id, name, created_by, updated_by, updated_at FROM product_db.products WHERE updated_by LIKE 'ADMIN:%';`
- Money is `DECIMAL(10,2)` in SQL and `BigDecimal` in Java. Time is `Instant` (UTC).
- Enums are stored as `VARCHAR` with `@Enumerated(EnumType.STRING)`.
- Seed data lives in `db/seed/R__*.sql`, uses `INSERT IGNORE`, and is loaded only by the `local` profile.
- A service connects **only to its own database**, with its own user.

## 7. Configuration

- Every environment-specific value is written as `${ENV_VAR:local-default}`, so the service runs locally with zero setup and Kubernetes overrides values with environment variables.
- `spring.profiles.default: local` means pressing Run in the IDE uses the local profile. Containers set `SPRING_PROFILES_ACTIVE=prod`.
- Ports: see [ports.md](ports.md).

## 8. Testing

| Layer | Tool | Example in auth-service |
|---|---|---|
| Entity rules | plain JUnit | `PasswordResetTokenTest` |
| Service logic | JUnit + Mockito (real mapper/validator) | `AuthServiceImplTest` |
| Controller | `@WebMvcTest` + `@MockitoBean` | `AuthControllerTest` |
| Repository | `@DataJpaTest` + Testcontainers MySQL | `UserRepositoryTest` |
| Whole service | `@SpringBootTest` + `@AutoConfigureMockMvc` + Testcontainers | `AuthFlowIntegrationTest` |

- Test names describe behaviour: `login_fails_forDisabledAccount`.
- Tests use the `test` profile (`@ActiveProfiles("test")`), so no seed data is loaded.
  `src/test/resources/application-test.yml` sets `schema.registry.url: mock://grocery-tests` (an in-memory Schema Registry).
- Kafka tests use `KafkaTestcontainersConfiguration`. It also declares the topics as `NewTopic` beans, because a consumer
  only notices a topic created after it started several minutes later.
- `.\mvnw verify` must pass before a change is merged (Docker must be running).

### `.http` files

- One request per block. A block starts with `### <number>. <title>`.
- A line starting with `###` **always starts a new request**. So a second title line must start with a single `#`.
- `# @name login` names a request, and `{{login.response.body.$.data.accessToken}}` reuses its answer (VS Code REST Client).

## 9. Events (Kafka + Avro)

How events work end to end: [event-catalog.md](event-catalog.md). **auth-service** is the reference: it both publishes and consumes.

**Publishing** (transactional outbox, never `KafkaTemplate.send`):

| Copy from auth-service | Purpose |
|---|---|
| `entity/OutboxEvent`, `repository/OutboxEventRepository`, `db/migration/V?__create_outbox_event_table.sql` | The outbox table |
| `messaging/OutboxEventPublisher`, `messaging/AvroEventSerializer` | Save an Avro event in the same transaction |
| `debezium/<service>-outbox-connector.json` | Change `name`, `database.server.id` (10000 + port), `topic.prefix`, database and table names; add the topic `__debezium-heartbeat.<topic.prefix>` to `create-topics.sh` |
| `scheduling/HousekeepingJob`, `config/SchedulingConfig`, the `app.housekeeping` block of `application.yml` | Deletes outbox rows after 14 days ([resilience.md](resilience.md#event-tables)) |

1. Write the schema in `src/main/avro/produced/<EventName>.avsc`: namespace `com.grocery.events.<aggregate>`, first fields `eventId` and `occurredAt`.
2. Add a mapper that builds the event with a new `eventId`, for example `StoreEventMapper`.
3. Inside the `@Transactional` service method: `outboxEventPublisher.publish(KafkaTopics.X_AGGREGATE, entity.getId(), event)`.

**Consuming** (idempotent, with retries and a dead-letter topic):

| Copy from auth-service | Purpose |
|---|---|
| `config/KafkaConsumerConfig` | Retries 1 s / 2 s / 4 s, then `<topic>.DLT` |
| `messaging/consumer/KnownEventsAvroDeserializer`, `IgnoredEvent` | Decode the event types we have classes for and skip the others |
| `entity/ProcessedEvent`, `repository/ProcessedEventRepository`, `service/ProcessedEventService` (+ impl), `V?__create_processed_event_table.sql`, `V?__add_processed_event_processed_at_index.sql` | Skip duplicates |
| `scheduling/HousekeepingJob`, `config/SchedulingConfig`, the `app.housekeeping` block | Deletes `processed_event` rows after 30 days (auth-service's job does both tables; keep the part your service needs) |
| the `spring.kafka` block of `application.yml` | Consumer settings |

1. Copy the producer's schema into `src/main/avro/consumed/`, and say in its `doc` which service owns it.
2. Write a thin `@KafkaListener(topics = ...)` class with one `@KafkaHandler` per event type, plus `@KafkaHandler(isDefault = true)` to ignore the rest.
3. Put the logic in a `@Transactional` service method that starts with `if (!processedEventService.markAsProcessed(eventId, type)) return;`.

**Rules**

- One topic per aggregate (`grocery.store.events`), several event types per topic. The key is the aggregate id.
- **Vendor endpoints** get the store with `UserContextHolder.getRequiredStoreId()` (403 without an approved store) and always query with it (`findByIdAndStoreId...`). Another store's id therefore looks like "not found".
- The same for every **"my ..." endpoint**: query with the owner (`findByIdAndCustomerUserId`, `findByIdAndPartnerUserId`). Another user's record is a 404, so ids of other people's data are never confirmed.
- Schemas only change in **backward-compatible** ways: add fields with a default, never rename or remove. Schema Registry enforces this.
- **Amounts** travel as decimal text (`"201.00"`), never as float/double. Build them with `amount.setScale(2, HALF_UP).toPlainString()`, read them with `new BigDecimal(text)`.
- **One listener class per topic** (`OrderEventsConsumer`, `InventoryEventsConsumer` ...): each gets its own consumer thread, so retrying an event on one topic never blocks another topic.
- **An event can arrive before the data it needs** when it comes from another topic (StockReserved before OrderCreated). Then throw (e.g. `ResourceNotFoundException`): the transaction rolls back, the processed-event mark with it, and the error handler retries a few seconds later. Raise the retry count for such consumers (payment-service: 5 retries, about 30 s).
- **An event that no longer fits is ignored, not an error**: check the status first (`order.getStatus().canMoveTo(...)`), log, and return. Example: PaymentConfirmed for an order the customer already cancelled.
- A service that consumes events must not start its listeners in tests that do not need Kafka: `@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")`.
- To give an existing service consumers, copy the classes in the table above and the `spring.kafka` block, and merge auth-service's Kafka/Avro dependencies into its pom.
- Never put secrets you would not log into an event. Events are kept and can be read by every consumer.
- Delivery is **at least once**: a consumer can always get an event twice (after a Debezium restart, a dead-letter
  replay), so every handler must be safe to run twice ([event-catalog.md](event-catalog.md#topics-settings-and-delivery-guarantees)).

## 10. Read models (CQRS with Elasticsearch)

The reference implementation is **product-read-service**.

- MySQL is the source of truth. A read model is only a copy: only its own consumer writes to it, and it can always be thrown away and rebuilt.
- Documents carry the source row's `version` and use `@Document(versionType = EXTERNAL_GTE)`. An older change is rejected (`VersionConflictException`) and skipped, so replays and duplicates are harmless.
- Elasticsearch remembers deleted documents' versions for 60 seconds. In tests, **recreate the index** in `@BeforeEach` instead of deleting documents.
- Change topics that feed a read model are **compacted**, so replaying them from the beginning rebuilds everything.
- Elasticsearch makes new documents searchable about once a second. In tests call `indexOps(...).refresh()`; in the UI accept that search is "eventually consistent".
- Integration tests that do not need Kafka set `spring.kafka.listener.auto-startup=false`. Otherwise they could join the real local consumer group.
- **Several sources, one document:** each source keeps its own document with its own version (product rows → `ProductDocument`, `StockChanged` → `StockDocument`). Its values are then copied onto the target document, keeping the target's version (EXTERNAL_GTE accepts the same version). Whichever arrives first, the other picks it up later.
- One `@KafkaListener` for all topics of a read model, with `concurrency = "1"`, so two changes of the same document never run at the same time.
- A read model built from **domain events** reads them as `GenericRecord` by field name (no generated classes needed). Domain topics are not compacted, so the producer offers a snapshot endpoint that publishes its current state again (`POST /internal/inventory/stock-snapshot`).
- **Never drop a database whose versions are copied into a read model** (product_db, inventory_db): its versions would start again at 0, and the read model would ignore every new change as "older". Remove or update rows instead (see [seed-data.md](seed-data.md)).

## 11. Calling another service (REST)

Use events by default. Call another service synchronously only when you need the answer **now** (see the table in the architecture plan, section 2.3). The reference is **inventory-service** → product-read-service.

| Copy from inventory-service | Purpose |
|---|---|
| `config/ServiceUrlsProperties` + `app.services.*` in `application.yml` | Base URL per service: `${PRODUCT_READ_SERVICE_URL:http://localhost:8105}` |
| `config/RestClientConfig` | One `RestClient` bean per called service |
| `client/CorrelationIdForwardingInterceptor` | Sends `X-Correlation-Id` along, so the logs of both services can be matched |
| `client/ProductCatalogClient`, `client/dto/CatalogProduct` | The call itself, and a record with only the fields we need (`@JsonIgnoreProperties(ignoreUnknown = true)`) |
| `exception/ExternalServiceException` + its handler | Answer **503** when the other service fails |
| the `spring-boot-starter-restclient` dependency | `RestClient` (Spring Boot 4 has a separate starter for it) |

- **Always set timeouts:** `spring.http.clients.connect-timeout: 2s` and `read-timeout: 5s`. Without them a hanging service makes this one hang too.
- Only call `/internal/**` endpoints of other services; the gateway never exposes them.
- **Translate errors in the client class:** 404 → `Optional.empty()` (the caller decides what "not found" means), anything else (down, timeout, 5xx) → `ExternalServiceException`.
- The answer can be slightly out of date when the other service is a read model (product-read-service learns about a new product about a second later). Say so in the error message ("try again in a few seconds").
- Test the client with `MockRestServiceServer` bound to a `RestClient.Builder` (no network, no Spring context); in service tests mock the client class.
- **Why `RestClient`** (not `RestTemplate`, Feign or `WebClient`): it is part of Spring Framework, so the services need no Spring Cloud; the `RestClient.Builder` from Spring Boot already has the timeouts, tracing and `http.client.requests` metrics; and it works with `MockRestServiceServer`. `RestTemplate` is the older client with the same blocking style. [Spring Cloud OpenFeign](https://docs.spring.io/spring-cloud-openfeign/reference/) is feature-complete (bug fixes only), and Spring suggests HTTP Service Clients instead. `WebClient` is for reactive code, which the services don't use. If a service ever calls many endpoints of another, prefer Spring's `@HttpExchange` interfaces (built into Spring Boot 4, on top of the same `RestClient`) over Feign.

## 12. Short-lived state in Redis

MySQL is the source of truth. Redis holds only data that may disappear without harm and should expire by itself.
The example is delivery-service's **online presence** (`presence/PartnerPresenceService`):

- One key per partner, `delivery:partner-online:<userId>`, written with an expiry (`app.delivery.online-for`, 30 minutes). The value is the time it expires.
- The app repeats "go online" to stay online. When it stops (phone off, no network) the key expires and the partner gets no new work. No clean-up job is needed.
- Use `StringRedisTemplate` (plain text keys and values, readable with `redis-cli`), and prefix every key with the service name.
- Tests: a Redis Testcontainer (`RedisTestcontainersConfiguration`) in integration tests; mock the presence class in unit tests.

## 13. Scheduled jobs

Some work is not started by a request or an event but by time, e.g. failing online payments nobody paid in time
(payment-service: `scheduling/PaymentTimeoutJob`).

- `@EnableScheduling` in a small `config/SchedulingConfig`, the job in a `scheduling/` package, the interval in `application.yml`
  (`@Scheduled(fixedDelayString = "${app.payment.timeout-check-interval}")`, e.g. `60s`).
- The job only finds the work (a list of ids) and hands **each item to a service method with its own transaction**.
  One failing item is logged and tried again at the next run; it never stops the others.
- The service method checks the state again before changing anything (`payment.isOverdue(now)`): the data may have
  changed since the ids were read.
- With several instances, two may pick the same row; the entity's `@Version` makes the second one fail harmlessly.
  (For jobs that must run exactly once, a lock library such as ShedLock is the next step.)
- Take "now" from a `Clock` bean (`config/ClockConfig`), so unit tests can use `Clock.fixed(...)`.

## 14. Calling an outside provider (Razorpay)

- Hide the provider behind an interface (`PaymentGateway`) and pick the implementation with a property
  (`@ConditionalOnProperty(name = "app.payment.gateway", havingValue = "razorpay")`). A mock implementation keeps local
  development and tests free of real money and network.
- Call its REST API with a `RestClient` (timeouts from `spring.http.clients`), read only the fields you need into records with
  `@JsonIgnoreProperties(ignoreUnknown = true)`, and turn every failure into `ExternalServiceException` (503).
- **Never trust a message that claims "paid"** without checking its signature (HMAC with the shared secret,
  compared with `MessageDigest.isEqual`). Webhooks: read the body as a `String`, because the signature covers the exact bytes.
- Providers repeat webhooks until they get a 2xx: skip repeats by their event id, and answer 2xx for events you ignore.
- Test the client with `MockRestServiceServer`, and the signature check against values calculated outside Java.
