# report-service

Builds **read models for reports** from the events of the other services (CQRS: the read side is separate from the services that own the data) and answers the dashboard and report requests of admins, vendors and the super admin.

| | |
|---|---|
| Port | `8112` |
| Database | `report_db` (MySQL, user `report_user`) |
| Swagger UI | http://localhost:8112/swagger-ui.html (or the central one at http://localhost:8080/swagger-ui.html) |
| Publishes events | – |
| Consumes events | `UserRegistered`; `StoreRegistered`, `StoreApproved`, `StoreStatusChanged`; `OrderCreated`, `OrderConfirmed`, `OrderDelivered`, `OrderCancelled`, `OrderRejected`, `OrderDeliveryFailed`; `PaymentConfirmed`, `RefundCompleted`; `StockChanged`; `PartnerKycReviewed` |

## The read model

```text
events ──► XxxEventsConsumer (one per topic) ──► ReportProjectionService ──► order_facts, order_item_facts, store_facts,
                                                                             user_facts, stock_facts, partner_facts
reports ──► ReportQueryService ──► simple queries on those tables
```

| Table | Built from | Used for |
|---|---|---|
| `order_facts` | OrderCreated, OrderConfirmed, OrderDelivered, OrderCancelled / Rejected / DeliveryFailed, PaymentConfirmed, RefundCompleted | orders, sales, revenue, refunds, delivery time, commission |
| `order_item_facts` | OrderCreated | top products |
| `store_facts` | StoreRegistered, StoreApproved, StoreStatusChanged | active stores, store names |
| `user_facts` | UserRegistered | customers |
| `stock_facts` | StockChanged (newest `stockVersion` wins) | out-of-stock products |
| `partner_facts` | PartnerKycReviewed | active delivery partners |

- **`sale`** and **`cancelled`** are columns on `order_facts`: an order is a sale once it is confirmed, and stops being one when it is cancelled, rejected or not delivered. Queries then simply say `WHERE o.sale = true`.
- Payment events come on another topic and can arrive **before** `OrderCreated`: the handler throws, the transaction rolls back, and Kafka retries (up to 5 times) until the order is known.
- Every handler is idempotent (`processed_event`; stock uses its version instead).
- The commission rate is the store's rate **when the order was placed** (`commissionPercentage` in `OrderCreated`; orders placed before Phase 11 count 0 %).
- **Rebuild:** everything here can be rebuilt by replaying the topics with a new consumer group (Kafka keeps events 7 days by default; longer retention is a production setting).
- Local seed: the seed users, stores and partners were inserted by SQL in their services (no events), so `db/seed/R__insert_seed_facts.sql` adds them here (local profile only). Seed **stock** arrives as events after `POST /internal/inventory/stock-snapshot`.

## Metrics (`GET /api/v1/admin/reports/dashboard`)

| Field | Meaning |
|---|---|
| `totalOrders`, `ordersToday` | orders placed (all of them / today, India date) |
| `salesToday` | total of today's orders that count as sales |
| `revenueToday` | total of the orders delivered today |
| `activeStores` | stores with status ACTIVE |
| `activeDeliveryPartners` | partners with approved KYC |
| `totalCustomers`, `activeCustomers` | registered customers / customers who ordered in the last 30 days |
| `pendingRefunds` | orders that ended without delivery while paid online, refund not done yet |
| `outOfStockProducts` | products with 0 available |
| `averageDeliveryMinutes` | placing → delivery, orders delivered in the last 30 days |

## Endpoints

Dates are India dates (`yyyy-MM-dd`), both days included, at most 366 days; `limit` 1–50 (default 10).

| Method | Path | Who (enforced by the gateway) | What |
|---|---|---|---|
| GET | `/api/v1/admin/reports/dashboard` | ADMIN, SUPER_ADMIN | The dashboard |
| GET | `/api/v1/admin/reports/sales/daily?from=&to=&storeId=` | ADMIN, SUPER_ADMIN | Sales per day (all stores or one) |
| GET | `/api/v1/admin/reports/top-stores?from=&to=&limit=` | ADMIN, SUPER_ADMIN | Stores with the highest sales |
| GET | `/api/v1/admin/reports/top-products?from=&to=&storeId=&limit=` | ADMIN, SUPER_ADMIN | Best-selling products |
| GET | `/api/v1/vendor/reports/sales/daily?from=&to=` | VENDOR | My store's sales per day |
| GET | `/api/v1/vendor/reports/top-products?from=&to=&limit=` | VENDOR | My best sellers |
| GET | `/api/v1/super-admin/reports/commissions?from=&to=` | SUPER_ADMIN | Commission and store payout per store (delivered orders) |

## Run it

```powershell
cd report-service; .\mvnw spring-boot:run   # Flyway creates the tables (+ the seed facts locally); the consumers read all events from the start
```

Then use `http/report-service.http` (12 requests).

## Test it

```powershell
.\mvnw verify      # Docker must be running (MySQL + Kafka via Testcontainers)
```

| Test | Kind | What it proves |
|---|---|---|
| `OrderFactTest` | plain JUnit | When an order is a sale, refund pending, COD paid at delivery; older stock versions are ignored |
| `ReportFlowIntegrationTest` | `@SpringBootTest` + MySQL + Kafka containers | Events of six topics (a payment event before its order) → dashboard, daily sales, top products and stores, vendor report, commission; invalid periods and limits → 400 |

## Configuration

| Property | Env variable | Local default |
|---|---|---|
| `server.port` | `SERVER_PORT` | `8112` |
| `spring.datasource.url` / `username` / `password` | `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:mysql://localhost:3307/report_db?...` / `report_user` / `report_pass` |
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `spring.kafka.properties.schema.registry.url` | `SCHEMA_REGISTRY_URL` | `http://localhost:8081` |
| `app.report.time-zone` | – | `Asia/Kolkata` |
