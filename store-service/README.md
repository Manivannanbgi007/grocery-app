# store-service

Owns **stores**: registration by vendors, admin approval, opening hours, delivery areas (PIN codes) and the platform commission per store.

| | |
|---|---|
| Port | `8103` |
| Database | `store_db` (MySQL, user `store_user`) |
| Swagger UI | http://localhost:8103/swagger-ui.html (or the central one at http://localhost:8080/swagger-ui.html) |
| Publishes events | `StoreRegistered`, `StoreApproved`, `StoreStatusChanged` on `grocery.store.events` (outbox + Debezium) |
| Consumes events | none yet |

## Store lifecycle

```text
 register ──► PENDING_APPROVAL ──approve──► ACTIVE ◄──enable── DISABLED
                   │    ▲                     │                   ▲
                reject  │ vendor edits        └─────disable───────┘
                   ▼    │ the store
                REJECTED
```

The rules live in the `Store` entity (`approve()`, `reject()`, `disable()`, `enable()`, `resubmitIfRejected()`).
Only ACTIVE stores are shown to customers. A vendor owns exactly one store.
When a store is approved, **auth-service** receives `StoreApproved` and the vendor's next JWT contains `storeId`.

## Endpoints

| Method | Path | Who (enforced by the gateway) | What |
|---|---|---|---|
| GET | `/api/v1/stores?city=&pincode=` | public | Active stores, filtered by city or delivery PIN code |
| GET | `/api/v1/stores/{id}` | public | One active store, with opening hours and `openNow` |
| POST | `/api/v1/vendor/store` | VENDOR | Register my store (PENDING_APPROVAL) |
| GET / PUT | `/api/v1/vendor/store` | VENDOR | My store / update it (a rejected store goes back for approval) |
| PUT | `/api/v1/vendor/store/opening-hours` | VENDOR | Weekly hours (days not listed = closed) |
| PUT | `/api/v1/vendor/store/delivery-areas` | VENDOR | PIN codes the store delivers to |
| PATCH | `/api/v1/vendor/store/accepting-orders` | VENDOR | Pause or resume new orders |
| GET | `/api/v1/admin/stores?status=&city=&keyword=` | ADMIN, SUPER_ADMIN | Search (e.g. `status=PENDING_APPROVAL` = approval queue) |
| GET | `/api/v1/admin/stores/{id}` | ADMIN, SUPER_ADMIN | One store |
| POST | `/api/v1/admin/stores/{id}/approve` | ADMIN, SUPER_ADMIN | Approve (publishes `StoreApproved`) |
| POST | `/api/v1/admin/stores/{id}/reject` · `/disable` · `/enable` | ADMIN, SUPER_ADMIN | Status changes (publish `StoreStatusChanged`) |
| PATCH | `/api/v1/super-admin/stores/{id}/commission` | SUPER_ADMIN | Commission percentage |
| GET | `/internal/stores/{id}` | other services only | Any store in any status (for order-service) |

Vendor endpoints find the store by the vendor's **user id** (`X-User-Id`), because the JWT only contains `storeId` after approval.

## Run it

```powershell
.\scripts\start-infra.ps1                   # from the repo root, once
cd store-service; .\mvnw spring-boot:run    # Flyway creates the tables + 2 sample stores
.\scripts\register-connectors.ps1           # from the repo root, once: publishes the outbox to Kafka
```

Then open [http/store-service.http](http/store-service.http). "Is the store open now?" uses the India time zone (`app.store.time-zone`).

## Test it

```powershell
.\mvnw verify      # Docker must be running (MySQL via Testcontainers)
```

| Test | Kind | What it proves |
|---|---|---|
| `StoreTest` | plain JUnit | Status rules, opening hours, delivery area |
| `OpeningHoursValidatorTest` | plain JUnit | Duplicate days, missing times, closing before opening |
| `VendorStoreServiceImplTest`, `AdminStoreServiceImplTest` | Mockito | Defaults, one store per vendor, which events are published |
| `VendorStoreControllerTest` | `@WebMvcTest` | Validation, 401 without the gateway header, optional `closed` flag |
| `StoreRepositoryTest` | `@DataJpaTest` + MySQL container | City / PIN code / status queries, schema matches entities |
| `OpeningHoursStorageTest` | `@DataJpaTest` + MySQL container, JVM in India time | 07:00 is stored and read back as 07:00 (no time-zone shift) |
| `StoreFlowIntegrationTest` | `@SpringBootTest` + MySQL container | Register → approve → visible to customers, events in the outbox |

## Configuration

| Property | Env variable | Local default |
|---|---|---|
| `server.port` | `SERVER_PORT` | `8103` |
| `spring.datasource.url` / `username` / `password` | `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:mysql://localhost:3307/store_db?...` / `store_user` / `store_pass` |
| `spring.kafka.properties.schema.registry.url` | `SCHEMA_REGISTRY_URL` | `http://localhost:8081` |
| `app.store.time-zone` | – | `Asia/Kolkata` |
| `app.store.default-commission-percentage` | – | `10.00` |
| `app.store.default-opening-time` / `default-closing-time` | – | `07:00` / `22:00` |

## Opening hours and time zones

Opening hours are a **time of day** (`LocalTime`), not a moment. `OpeningHours` maps them with
`@JdbcType(LocalTimeJdbcType.class)`, so Hibernate hands them to MySQL unchanged. Without it, the setting
`hibernate.jdbc.time_zone: UTC` (right for timestamps) also converted these times from the JVM's time zone to UTC: on a PC
set to India time, 07:00 was stored as 01:30, and the seed's 07:00 was read as 12:30, so `openNow` was wrong. Containers
run in UTC, where the conversion changed nothing. `openNow` itself is calculated in `app.store.time-zone` (Asia/Kolkata).

Hours saved through the API on a PC in India time **before this fix (2026-10-26)** are 5:30 too early in the database.
Check them and, if needed, correct them (example for store 3):

```sql
SELECT store_id, day_of_week, opens_at, closes_at FROM store_opening_hours;
UPDATE store_opening_hours SET opens_at = ADDTIME(opens_at, '05:30:00'), closes_at = ADDTIME(closes_at, '05:30:00')
WHERE store_id = 3 AND closed = false;
```
