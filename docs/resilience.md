# Resilience review

What happens when a part of the platform fails, and what protects the rest. Reviewed in Phase 12.

## Principles

1. **Events by default.** Only five calls are synchronous (see the architecture plan, 2.3). Everything else goes through Kafka, so a service that is down only delays work; nothing is lost.
2. **Every call has a timeout** (`spring.http.clients.connect-timeout: 2s`, `read-timeout: 5s`; Razorpay 10 s; gateway 10 s). A slow service can never hang its callers.
3. **Failures become clear answers**: `ExternalServiceException` → 503 with a readable message; the gateway answers 503 (service down, circuit open, busy) with `Retry-After`, or 504 (too slow), in the standard `ApiResponse` shape. Never a made-up success.
4. **At-least-once delivery + idempotent handlers**: Kafka may deliver twice; `processed_event` (or a version check) makes the second time a no-op.
5. **No dual writes**: a business change and its event are saved in one transaction (outbox); Debezium publishes later, even after a crash.
6. **Traffic control in one place**: rate limits, throttling, circuit breakers, timeouts and retries for client requests live in the API gateway only (see below), not in every service.

## Failure modes

| What fails | What happens | Protection |
|---|---|---|
| A service is down | Gateway: 503 + `Retry-After` for its paths (GET requests are first retried twice), every other path keeps working. After a few failures its circuit opens: answers come at once without calling it, and the circuit closes by itself when the service is back. Home-page banners show the page's own artwork instead. Its Kafka events wait in the topic | Gateway circuit breaker, retry and fallback per route; Kafka keeps events (7 days); consumer group offsets |
| A service is slow or overloaded | Gateway: 504 after 10 s; at most 100 requests per route wait for it at the same time (chat: 20), more get 503 "busy"; repeated timeouts open its circuit | Gateway timeouts, bulkhead, circuit breaker |
| A client sends too many requests (a script, a login-guessing attempt) | 429 + `Retry-After` for that client only; the request never reaches a service | Gateway rate limits in Redis (login 10/min per IP, chat 10/min, everything else 20/s) |
| A consumer cannot handle an event (bug, bad data) | Retried with back-off (1 s, 2 s, 4 s ...), then sent to `<topic>.DLT` (kept 30 days); the partition goes on. If even the DLT cannot be written, the event is not skipped: it is read and tried again | `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`; inspect and replay after the fix: [Dead letters](#dead-letters) |
| An event arrives before the data it needs (StockReserved before OrderCreated) | The handler throws, everything rolls back, Kafka retries a few seconds later | 5 attempts for those consumers (payment-service, report-service) |
| The same event arrives twice | Skipped | `processed_event` table (event id as primary key), stock versions, Elasticsearch external versions |
| MySQL down | Requests that need the database fail; `/actuator/health` shows `db: DOWN`. Nothing half-done is left: a change and its outbox row are one transaction. Measured after a 16 s MySQL restart: every service was healthy again by itself, and Debezium read the binlog again after about 80 s (alerts MySQLDown, DebeziumNotReadingBinlog, [observability.md](observability.md#data-stores-mysql-redis-elasticsearch)) | Transactions. Note: Spring Boot's readiness probe does **not** include the database by default (add `db` to `management.endpoint.health.group.readiness.include` if Kubernetes should stop sending traffic) |
| Kafka down | Writes still work (outbox rows wait in MySQL); consumers pause. Measured: everything continued by itself when Kafka was back ([drills](#failure-drills)) | Outbox: Debezium publishes when Kafka is back |
| Debezium / Kafka Connect down | Same: rows wait in the outbox table, published when the connector runs again. Alerts KafkaConnectDown, KafkaConnectTaskFailed, DebeziumNotReadingBinlog ([observability.md](observability.md#events-and-debezium)) | Connector offsets in Kafka; heartbeats keep them inside the binlog; [runbook](#runbook-events) for a FAILED task and for an outage longer than the binlog's 7 days |
| A consumer is stopped or stuck | Its events wait on the topic; nothing is lost | Alert KafkaConsumerNoProgress (2, 5 or 15 min depending on who waits), lag read from the broker |
| Elasticsearch down | Search and product pages answer with an error; writes to products still work (MySQL); CDC events wait in Kafka and are indexed when it is back | Kafka keeps the changes (compacted CDC topics); `scripts\rebuild-product-index.ps1` rebuilds the index from Kafka |
| Speech server (speaches) down or busy | Voice answers 503 "Voice is not available right now. Please type your question." within milliseconds (measured); typing and the rest of the assistant work as before. Busy: 503 + `Retry-After` | `app.voice.read-timeout` 45 s under the gateway's 60 s; at most 2 voice jobs per replica ([ai-assistant-plan.md](ai-assistant-plan.md#16-voice-phase-20)) |
| Redis down | Gateway rate limiting fails open at once (requests pass without limits); delivery partners count as offline (no new assignments until it is back, nothing fails); going online and live tracking answer 503 | Commands fail at once while Redis is down instead of waiting 60 s ([Redis](#redis)); presence keys expire by themselves; admins can assign by hand |
| A service's database pool is used up (slow queries, a long lock) | Requests that get no connection within 5 s answer 503 "busy"; the others finish; the pool recovers by itself | Pool sizes within MySQL's budget, 5 s connection timeout, 30 s transaction timeout ([Database connections](#database-connections)) |
| Invoice PDF cannot be generated or stored (order-service) | Generation fails: 503 "Please try again in a moment", nothing stored. Disk full or folder not writable: the PDF is still sent (ERROR log), stored on a later download. A lost or damaged stored file is generated again | Stored only after success, size + SHA-256 check, order row lock against double generation ([order-service README](../order-service/README.md#invoices)) |
| A called service is slow or down at checkout (cart, customer, store) | Placing the order fails with 503 and a clear message; nothing was saved (the calls happen before the transaction) | Timeouts, `ExternalServiceException`, calls outside the transaction |
| Razorpay down | Opening a payment fails → `StockReserved` is retried, then DLT; refunds the same | Kafka retries + DLT; the payment deadline cancels orders that can never be paid |
| A payment webhook is lost | The deadline job asks Razorpay before failing the payment | `findCapturedPayment` in `PaymentTimeoutJob` |
| Money arrives after the payment failed | Refunded at once, the customer is told | `OnlinePaymentRecorder.refundLatePayment` |
| Email server down | The in-app notification is still saved; the email is marked FAILED | `EmailSender` catches `MailException` |
| Two instances of a service | Kafka splits partitions; the payment timeout job may run twice, `@Version` lets one win | Optimistic locking |

## Events: what is guaranteed

- **No event is lost, none is invented.** A change and its event are one database transaction (outbox). A test proves
  it: a change that fails after writing its event leaves neither (inventory-service, `OrderEventsConsumerIntegrationTest`).
- **At least once, never "exactly once".** Debezium can publish an event again after a crash (it continues from its
  last saved binlog position), so every consumer skips duplicates (`processed_event`, versions).
- **In order per key** on one topic (all events of one order on one partition); not across topics.
- **Producers wait for every copy**: Debezium and the dead-letter producers use `acks=all` and are idempotent (a
  retry cannot write an event twice). Topic durability for production (3 copies, at least 2 written): [event-catalog.md](event-catalog.md#topics-settings-and-delivery-guarantees).
- **A failing event is parked, not dropped**: after its retries it goes to `<topic>.DLT`; if even that fails, it is
  read again (inventory-service, `KafkaConsumerConfigTest`).

### Event tables

Two tables per service grow with every event, so `HousekeepingJob` (every hour) deletes old rows, 1000 per transaction:

| Kept | What | Why that long |
|---|---|---|
| 7 days | MySQL binlog, Kafka topics | `--binlog-expire-logs-seconds`, `retention.ms` |
| 14 days | `outbox_event` rows | Debezium publishes from the binlog, not from the table; the rows are needed only to publish again after a Kafka Connect outage longer than the binlog (runbook below), so twice the binlog |
| 30 days | `processed_event` rows | Must outlive every way an old event can arrive again: republished outbox rows (up to 14 days old) and dead-letter replays |
| 30 days | dead letters (`<topic>.DLT`) | Time to notice, fix and replay |

Deleting outbox rows publishes nothing: Debezium's outbox router drops DELETE changes. Settings:
`app.housekeeping.*` in each service's `application.yml` (env `HOUSEKEEPING_OUTBOX_RETENTION`, ...).

### Dead letters

An event a consumer still cannot handle after its retries is copied to `<topic>.DLT`, with the reason in Kafka
headers (`kafka_dlt-exception-message`, the original topic, partition and offset). The super admin API in
platform-service shows and replays them ([platform-service.http](../platform-service/http/platform-service.http), requests 21-25):

```text
GET  /api/v1/super-admin/dead-letters                          every DLT and how many messages it keeps
GET  /api/v1/super-admin/dead-letters/grocery.order.events.DLT  the newest messages: event, error, replayed or not
POST /api/v1/super-admin/dead-letters/grocery.order.events.DLT/replay   { "partition": 0, "offset": 12 }  or  { "all": true }
```

1. Read the error (`exceptionClass`, `exceptionMessage`) and the event (`payload`); the service's log has the stack
   trace (search Loki for the `eventId`).
2. Fix the cause (a bug: deploy the fix; missing data: add it).
3. Replay. The message goes back to its original topic with its original key, bytes and headers (plus
   `x-replayed-from`); every consumer group of that topic reads it again, and the ones that had handled it skip it.
4. The replay is in the audit log (`DeadLetterReplayed`, who and when) and the message shows `replayedAt`; replaying
   it again is refused. If it fails again, it lands in the DLT as a new message.

**One DLT per topic, shared by its consumer groups**: a message every group fails on (unreadable bytes) is there once
per group. Kafka UI (http://localhost:8090) shows the same messages, read-only.

## Runbook: events

| Situation | What to do |
|---|---|
| A connector or task is FAILED (alert KafkaConnectTaskFailed) | `GET :8083/connectors/<name>/status` shows the error. Fix the cause, then `POST :8083/connectors/<name>/restart?includeTasks=true&onlyFailed=true`. Nothing is lost: it continues where it stopped |
| Kafka Connect was down for less than 7 days | Nothing: started again, every connector continues from its saved binlog position (drill "connect" below) |
| Kafka Connect was down for **more** than 7 days | The saved position is no longer in the binlog; the task fails ("...no longer available on the server"). See below |
| An event is in a dead-letter topic (alert KafkaEventsMovedToDeadLetterTopic) | [Dead letters](#dead-letters) |
| A consumer group does not move (alert KafkaConsumerNoProgress) | Is the service running? Its log shows the event it fails on and the retries; Grafana *Grocery events* → lag per partition. A service down longer than 7 days loses events (topic retention) |

**Connect down for more than 7 days** (tested on store-outbox-connector, 27 Oct 2026):

1. Start the connector from the current end of the binlog: `PUT :8083/connectors/<name>/stop`, then
   `DELETE :8083/connectors/<name>/offsets`, then `PUT :8083/connectors/<name>/resume`. It takes a schema-only snapshot
   (`snapshot.mode: no_data`) and runs; new events are published again.
2. Publish the rows that were written while it was down. Find the last event that reached the topic (Kafka UI → topic →
   newest message → its `id` header → that outbox row's `created_at`), then delete and insert again every row from
   that time on, in one transaction. The outbox router drops the deletes and publishes the inserts, with their
   original event ids, so consumers skip any that they already had:

   ```sql
   USE store_db;
   START TRANSACTION;
   CREATE TEMPORARY TABLE outbox_to_republish AS
       SELECT * FROM outbox_event WHERE created_at >= '2026-10-27 10:50:00' ORDER BY created_at;   -- a little earlier is fine
   DELETE FROM outbox_event WHERE id IN (SELECT id FROM outbox_to_republish);
   INSERT INTO outbox_event SELECT * FROM outbox_to_republish ORDER BY created_at;
   COMMIT;
   ```

   The rows are there for 14 days (housekeeping), so this works for an outage of up to two weeks; after that, the
   events must be rebuilt from the business tables.

## Failure drills

`scripts\failure-drills.ps1` stops a piece of the running platform, checks that nothing is lost, starts it again and
prints PASS or FAIL (`-Drill connect`, `kafka`, `deadletter`, `dbpool` or `redis` for one). Last runs, local
stack: connect, kafka and deadletter on 27 Oct 2026, **13 passed, 0 failed**; dbpool and redis (Phase 19e) on
28 Oct 2026, **8 passed, 0 failed**.

| Drill | What it showed |
|---|---|
| Kafka Connect down | An order placed meanwhile stayed PENDING with its OrderCreated in `order_db.outbox_event`; Connect started again: published, and the saga confirmed the order **20 s** later |
| Kafka down | Registration answered normally (the UserRegistered event waited in the outbox); Kafka healthy again after 11 s, the customer profile was there at once, every connector RUNNING without help |
| Dead-letter round trip | An unreadable message on `grocery.platform.events` was in the DLT after 3 s (`DeserializationException`); the replay sent it back, cart-service read it again (and, still unreadable, put it in the DLT again); the message showed as replayed by user 1, and a second replay was refused (409) |
| Database pool used up | A 20 s row lock on customer1's profile, 12 updates at once to customer-service (pool of 5): the pool was full ("total=5, active=5"), the other **7 got 503 "busy" after 5.1 s**; when the lock ended (17 s) one update was saved and 4 got 409 (the profile had changed: optimistic locking, nothing overwritten); the next request took 0.02 s; `hikaricp_connections_timeout_total` +7 |
| Redis down | The gateway answered in **0.05 s** (before 19e: 60 s per request), "go online" answered 503 in 0.12 s; Redis healthy again after 11 s, rate limiting counted again at once, the partner could go online |

Also measured: the cleanup job deleted 834 outbox rows in 203 ms (order-service, run with a 1-hour retention) and
Kafka got no message for them; the runbook above (reset the connector, republish by re-inserting) worked on
store-outbox-connector, and every consumer skipped the republished event as a duplicate.

## Gateway resilience (client → service)

Every client request passes the API gateway, so that is where traffic control lives, once, for all 16 services. Details,
settings and a testing guide: [api-gateway/README.md](../api-gateway/README.md#resilience-and-traffic-control).

| Mechanism | Setting | Answer |
|---|---|---|
| Rate limiting (per user or IP, counted in Redis, shared by all gateway pods) | login 10/min, chat 10/min, rest 20/s (burst 40) | 429 + `Retry-After` |
| Throttling (Resilience4j bulkhead, per route and pod) | 100 requests in progress (chat 20) | 503 "busy" + `Retry-After: 1` |
| Circuit breaker (per route) | opens at 50 % failures of the last 20 calls (at least 10), stays open 15 s, closes after 3 good test calls | 503 + `Retry-After: 15` |
| Timeouts | connect 2 s, answer 10 s (chat 180 s) | 504 |
| Retry | GET/HEAD only, after a refused connection or 502/503, 2 retries (100 ms, 200 ms) | – |
| Fallback | one handler (`GlobalErrorHandler`); an empty list only for decoration (banners) | standard `ApiResponse` |

Rules that keep it safe: POST/PUT/PATCH/DELETE are never retried (no double orders or payments), timeouts and 500s are
never retried, a service's own error answer is passed through unchanged and does not open its circuit, and prices,
stock, carts, orders and payments are never replaced by substitute data.

## Circuit breakers between services: not added (on purpose)

The gateway's circuit breakers protect client → service traffic. The five synchronous service → service calls (e.g.
order-service → cart-service at checkout) have none: each already has a short timeout and answers the user with a
clear 503 after at most a few seconds, and a breaker in every calling service would repeat configuration the gateway
already has. **Add one** (Resilience4j, on that call) when traffic grows so much that waiting for timeouts uses up
threads or connections; Grafana's "Slowest request" and "Database connections in use" panels show when that happens.

## Database connections

MySQL allows **300** connections (`--max-connections` in docker-compose.yml) for everything together. Each service
has a pool (HikariCP, `spring.datasource.hikari` in its `application.yml`):

| | Per instance | 2 instances each |
|---|---|---|
| order, cart, inventory, payment, product, auth, delivery: `maximum-pool-size` 10 | 70 | 140 |
| customer, store, notification, report, platform, mcp-client (chat): 5 | 30 | 60 |
| Debezium (8 connectors, 3 connections each), the MySQL exporter, a few for people and tools | 36 | 36 |
| **Total** | **136** | **236** (alert MySQLConnectionsNearLimit fires at 240 = 80 %) |

- `minimum-idle: 2`: an idle service keeps only 2 connections open (before: all 10). Measured: open connections went
  from 156 to **56** with every service running (24 of them Debezium's).
- `connection-timeout: 5000`: a request waits at most **5 s** for a free connection, then gets **503** "The service is
  busy right now. Please try again in a moment." (`CannotCreateTransactionException` in each `GlobalExceptionHandler`)
  instead of tying up a request thread for 30 s. The drill `-Drill dbpool` shows it ([Failure drills](#failure-drills)).
- `spring.transaction.default-timeout: 30s`: a transaction that runs longer is rolled back and its query cancelled, so a
  stuck query cannot hold a connection for ever. `socketTimeout` (60 s) catches a dead connection;
  `leak-detection-threshold` (20 s) logs a WARN with the stack trace of code that holds a connection that long.
- Pool sizes come from `DB_POOL_MAX_SIZE` / `DB_POOL_MIN_IDLE` when a service needs more. Before adding replicas,
  check the budget: MySQL's `max_connections` must stay above the sum.
- Flyway migrations use the same connections: an index on a very large table that takes more than 60 s to build must
  be created beforehand with an online tool (pt-online-schema-change, gh-ost), not at service start.

**No calls to other services inside a transaction.** A database connection is held for the whole transaction, so a
slow service behind a call would hold it too, and the pool runs out. Cart (add item, change quantity, checkout) and
inventory (first stock of a product) now ask product-read-service **before** their short transaction, like placing an
order always did (`TransactionTemplate` around the database work only). `CartFlowIntegrationTest` checks that no
transaction is open during those calls. delivery-service's location update (every 2 s per partner) no longer holds
a transaction while it talks to Redis and the routing server.

## Database indexes

`EXPLAIN` on the lists that grow with the business (with real values: the MySQL driver sends the parameters inside
the SQL text, so the "optional filter" pattern `(:storeId IS NULL OR o.storeId = :storeId)` is simplified by MySQL and
uses indexes normally). Missing indexes, added by Flyway:

| List | Problem | Index |
|---|---|---|
| A store's orders (vendor), newest first | read all orders backwards to find the store's | `orders (store_id, id)` |
| Deliveries by status (admin) | no index starting with status: every delivery read | `deliveries (status, id)` |
| My notifications, newest first | all of the user's notifications sorted for every page | `notifications (user_id, id)` |
| Audit log by store, event type or user, newest first | no index for store; the others sorted all matches | `audit_entries (store_id / event_type / user_id, occurred_at)` |
| Payments of a store (admin) | no index on store_id | `payments (store_id, id)` |

The query statistics behind it: `performance_schema.events_statements_summary_by_digest` (columns
`sum_no_index_used`, `sum_rows_examined`) lists every kind of query with how often it ran without an index.

## Redis

Redis holds only temporary data (rate-limit counters, partner presence, live locations), so it is not backed up.

- **Memory**: `--maxmemory 256mb --maxmemory-policy allkeys-lru`: Redis cannot take the machine's memory; when full it
  drops the least recently used key (alerts RedisMemoryHigh, RedisEvictingKeys).
- **Users** (Redis ACL, the `--user` lines in docker-compose.yml; passwords in `infrastructure/.env`, in Kubernetes in
  the Secret): `gateway` may only use the rate-limit keys and the commands of the rate-limit script, `delivery` only
  the `delivery:*` keys and the tracking channel, `exporter` only statistics, `admin` everything (Redis Insight,
  `docker exec grocery-redis redis-cli --user admin ...`). The passwordless default user is off.
- **Redis down**: the Redis client waited up to **60 s** for each command while Redis was down (measured in
  api-gateway's `RedisDownTest`: one request took 60,093 ms). Now commands fail at once while the connection is down
  (`RedisClientConfig`: REJECT_COMMANDS) and after 500 ms / 1 s otherwise (`spring.data.redis.timeout`): the same
  request takes about 50 ms. The gateway then lets requests through without a rate limit; delivery-service counts every
  partner as offline (no automatic assignment, nothing fails, one WARN), and going online or live tracking answer 503.

## Search indexes (Elasticsearch)

product-read-service keeps a copy of the catalog in Elasticsearch (the read side of CQRS). MySQL stays the source of
truth, so the indexes can always be rebuilt from Kafka; the points below make changing and restoring them safe and quick.

- **Aliases.** The service reads and writes the aliases `products`, `categories` and `product_stock`; each points to a
  versioned index (`products_v1`). `SearchIndexes` creates `<name>_v1` and its alias at start-up, before the Kafka
  listener runs, and adds new fields of the mapping file to an existing index. A plain index from before aliases keeps
  working (the start-up log says so); `scripts\reindex-products.ps1` moves it behind an alias once.
- **Explicit, strict mappings.** `product-read-service/src/main/resources/elasticsearch/*-mapping.json` (and
  `index-settings.json`) define every field; `"dynamic": "strict"` refuses a field that is not in the file, so a typo or
  a forgotten mapping fails loudly instead of creating a field with a guessed type. **A new field** = the field in the
  document class + a line in the mapping file (added to the live index at the next start). **A field with another
  type** = a new index version (below).
- **New index version without downtime:** `.\scripts\reindex-products.ps1 [-Index products]`. It:
  1. creates `<name>_v2` from the files;
  2. pauses the service's listener (`POST /internal/catalog/indexing/pause`: changes wait in Kafka);
  3. copies the documents with their versions (`_reindex`, `version_type: external`);
  4. compares the counts;
  5. switches the alias in one `_aliases` request;
  6. resumes the listener.

  The old index is kept for a rollback (the script prints the command).
  - Measured with searches running all the time, about 30 per second: moving the three plain indexes behind aliases
    (1,227 searches) and `products_v1` → `_v2`, the rollback, and deleting `_v2` (1,287 searches). Neither had an error
    or a changed total, and the versions survived the copy.
- **Snapshots.** Every night at 01:30 UTC the policy `daily-search` takes a snapshot of the three indexes and their
  aliases, keeping 7 (none older than 30 days). The repository is `grocery-search`, in the Docker volume
  `es-snapshots`; it is registered by the `elasticsearch-setup` container (`infrastructure/elasticsearch/setup-snapshots.sh`).
  - `.\scripts\search-snapshots.ps1` lists them, `-Create` takes one now, and `-Restore [-Snapshot <name>]` puts one back.
  - A restore:
    1. deletes the current indexes;
    2. restores the snapshot;
    3. moves the service's Kafka position back to one minute before the snapshot, so its next start applies every change
       since then again (each write carries the row's version, so a change already in the index is skipped);
    4. asks inventory-service to publish the current stock.
  - Drill (2026-10-28):
    1. took a snapshot;
    2. hid a product as its vendor;
    3. stopped the service and restored: the index showed the product visible again, the snapshot's state;
    4. started the service: within seconds it was hidden again, replayed from Kafka. The counts were the same as before:
       110 products, 22 categories, 140 stock rows.
- **Which recovery when?** A snapshot is faster (minutes, whatever the size). A rebuild from Kafka
  (`scripts\rebuild-product-index.ps1`) always works, because the cdc topics are compacted. Use the rebuild when no
  snapshot exists, or when the snapshot is older than the inventory topic's retention.
- **Client timeouts:** `spring.elasticsearch.connection-timeout: 2s` and `socket-timeout: 5s` (the defaults are 1 s and
  30 s), so an Elasticsearch that hangs no longer holds a web thread for 30 s.
- **Production:**
  - One shard per index is right for a catalog this size (a shard holds tens of millions of such documents).
  - With two or more nodes, set one replica: `PUT products_v1/_settings {"index":{"number_of_replicas":1}}`. The
    reindex script keeps the replica count of the old index.
  - Snapshots go to an object store (`repository-s3`, GCS, Azure) that every node can reach, not to a local folder.

## Backups

```powershell
.\scripts\backup-databases.ps1                     # every *_db database + the stored invoices -> backups\<date-time>\
.\scripts\restore-database.ps1 -Database order_db  # newest backup -> order_db_restored, row counts compared with live
```

- `mysqldump --single-transaction`: a consistent snapshot of each database while the services keep working (InnoDB,
  nothing locked). Each database is dumped on its own, like each service owns its own database.
- With the dumps: the stored invoice PDFs (they belong to `order_db.invoices`) and the binlog position at the time of
  the backup. MySQL keeps 7 days of binlog, so changes after a backup can be replayed (point-in-time restore,
  `mysqlbinlog --start-position=...`).
- The newest 7 backups are kept (`-Keep`). Measured locally: 13 databases in about 7 s, 0.7 MB compressed.
- A restore is tested by restoring into a copy (`<database>_restored`, the default): order_db came back with exactly the
  live row counts in all 7 tables. Replacing a live database needs `-Into <name> -Force` and a stopped service.
- Not in these dumps: Redis (temporary, not backed up), Elasticsearch (daily snapshots and a rebuild from the events,
  see [Search indexes](#search-indexes-elasticsearch)), Kafka (the events come from the outbox tables, which are in
  the dumps).
- Production: a managed MySQL (RDS, Cloud SQL, Azure) takes daily snapshots and keeps the binlog for point-in-time
  recovery; copy backups to another region, and restore one regularly.

## Not covered yet

- A saga "watchdog" for orders stuck in PENDING because of a bug (today: the DLT shows the failed event; an admin can cancel the order).
- Multi-zone infrastructure (a production infrastructure topic, outside the services).
- Locally there is one Kafka broker: the replication settings for production (3 copies) cannot be tested here.
