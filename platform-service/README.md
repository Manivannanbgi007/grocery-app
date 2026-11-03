# platform-service

Owns the **platform-wide configuration** and the **audit log**: settings (currency, delivery charge, minimum order value, support contact), feature flags, home page banners, and a log of everything that happens on the platform.

| | |
|---|---|
| Port | `8113` |
| Database | `platform_db` (MySQL, user `platform_user`) |
| Swagger UI | http://localhost:8113/swagger-ui.html (or the central one at http://localhost:8080/swagger-ui.html) |
| Publishes events | `PlatformSettingsChanged` on `grocery.platform.events` (outbox + Debezium) |
| Consumes events | **every** event of `grocery.user/store/inventory/order/payment/delivery.events`, for the audit log |

## Settings

One row (`platform_settings`, id 1), created by the V1 migration in every environment. The super admin changes it; the change is published as `PlatformSettingsChanged`, and cart-service keeps a copy of the delivery fee, the free delivery amount and the minimum order value (its `pricing_settings` table). So a new delivery fee is used for every cart a moment later, without restarting anything.

Rule: the minimum order value cannot be higher than the free delivery amount (400).

## Feature flags

A switch per feature that is built but not open to everybody yet (`feature_flags`): `DELIVERY_SLOTS`, `CITY_HYDERABAD`, `PRODUCT_REVIEWS`, `RAZORPAY_PAYMENTS` (seed). The apps read `GET /api/v1/platform/feature-flags` (`{"DELIVERY_SLOTS": false, ...}`) and show or hide the feature. Keys are `UPPER_SNAKE_CASE`.

## Banners

Promotional banners for the home page, managed by admins. A banner is shown (`live`) while it is `active` and inside its optional `startsAt` / `endsAt` window, in `position` order.

## Audit log

```text
every domain topic ──► AuditEventsConsumer (GenericRecord) ──► audit_entries: event type, topic, key, user, store, the event as JSON
changes made here (settings, flags, banners) ──────────────────► audit_entries: source "platform-service", who made the change
```

- The consumer reads **any** event as an Avro `GenericRecord` (`specific.avro.reader: false`): it needs no generated classes, so new event types are audited without code changes.
- `userId` is the first of `customerUserId`, `partnerUserId`, `ownerUserId`, `userId`, `changedByUserId`, `approvedByUserId` the event has; `storeId` likewise. That makes "everything about user 4" or "about store 1" one query.
- Duplicates are skipped (`processed_event`).

## Endpoints

| Method | Path | Who (enforced by the gateway) | What |
|---|---|---|---|
| GET | `/api/v1/platform/settings` | everyone | Currency, delivery fee, free delivery amount, minimum order, support contact |
| GET | `/api/v1/platform/feature-flags` | everyone | `{flag: enabled}` |
| GET | `/api/v1/banners` | everyone | Banners shown right now |
| GET / PUT | `/api/v1/super-admin/settings` | SUPER_ADMIN | Read / change the settings |
| GET / POST | `/api/v1/super-admin/feature-flags` | SUPER_ADMIN | All flags / add one |
| PATCH | `/api/v1/super-admin/feature-flags/{key}` | SUPER_ADMIN | Switch on or off `{"enabled": true}` |
| GET | `/api/v1/super-admin/audit-log?eventType=&source=&userId=&storeId=&aggregateId=&from=&to=` | SUPER_ADMIN | The audit log, newest first |
| GET | `/api/v1/super-admin/dead-letters` | SUPER_ADMIN | Every dead-letter topic and how many messages it keeps |
| GET | `/api/v1/super-admin/dead-letters/{topic}?limit=50` | SUPER_ADMIN | Its newest messages: the event, why it failed, replayed or not |
| POST | `/api/v1/super-admin/dead-letters/{topic}/replay` | SUPER_ADMIN | `{"partition": 0, "offset": 12}` or `{"all": true}`: send back to the original topic; audited as `DeadLetterReplayed` ([resilience.md](../docs/resilience.md#dead-letters)) |
| GET / POST / PUT / DELETE | `/api/v1/admin/banners` · `/{id}` | ADMIN, SUPER_ADMIN | Manage banners |

## Run it

```powershell
cd platform-service; .\mvnw spring-boot:run   # Flyway creates the tables, the settings row, the flags and (local) 3 banners
.\scripts\register-connectors.ps1             # from the repo root, once: publishes the outbox to Kafka
```

Then use `http/platform-service.http` (20 requests).

## Test it

```powershell
.\mvnw verify      # Docker must be running (MySQL + Kafka via Testcontainers)
```

| Test | Kind | What it proves |
|---|---|---|
| `BannerTest` | plain JUnit | The time window, active flag, end must be after start |
| `PlatformFlowIntegrationTest` | `@SpringBootTest` + MySQL + Kafka containers | Settings update writes the outbox event and an audit entry; flags and banners; a generic event from another topic is audited exactly once |

## Configuration

| Property | Env variable | Local default |
|---|---|---|
| `server.port` | `SERVER_PORT` | `8113` |
| `spring.datasource.url` / `username` / `password` | `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:mysql://localhost:3307/platform_db?...` / `platform_user` / `platform_pass` |
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `spring.kafka.properties.schema.registry.url` | `SCHEMA_REGISTRY_URL` | `http://localhost:8081` |
