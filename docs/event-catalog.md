# Event Catalog

All Kafka messages are **Avro**, with their schemas in Schema Registry (BACKWARD compatibility, subject = `<topic>-<record name>`).
Topics are created by `infrastructure/kafka/create-topics.sh`, because auto topic creation is off.
Every topic has a `<topic>.DLT` dead-letter topic. Settings, retention and delivery guarantees: [Topics](#topics-settings-and-delivery-guarantees).

## How an event travels

```text
service (one DB transaction)              Debezium (Kafka Connect)                 consumer service
┌───────────────────────────────┐         ┌──────────────────────────┐         ┌──────────────────────────────┐
│ save business change          │ binlog  │ Outbox Event Router:     │  Kafka  │ KnownEventsAvroDeserializer   │
│ save OutboxEvent (Avro bytes) ├────────►│ aggregate_type -> topic  ├────────►│ @KafkaHandler(EventClass)     │
└───────────────────────────────┘         │ aggregate_id   -> key    │         │ ProcessedEventService (dedup) │
                                          │ id, event_type -> headers│         │ retries -> <topic>.DLT        │
                                          └──────────────────────────┘         └──────────────────────────────┘
```

Every message has:
- **Key:** the aggregate id (for example the storeId). All events of one store therefore land in the same partition, in order.
- **Headers:** `id` (= `eventId`) and `eventType` (for example `StoreApproved`).
- **Value:** the Avro event. Every event has `eventId` (UUID, used to skip duplicates) and `occurredAt`.

Producers own their `.avsc` files (`src/main/avro/produced/`). Consumers keep a copy (`src/main/avro/consumed/`).

## Implemented events

| Topic | Event | Producer | Consumers | Key fields |
|---|---|---|---|---|
| `grocery.user.events` | **UserRegistered** | auth-service | customer-service (creates the profile for CUSTOMERs), delivery-service (partner profile for DELIVERY_PARTNERs), notification-service (contact book + welcome), report-service (customers) | userId, fullName, email, phone, role |
| `grocery.user.events` | **SocialLoginLinked** | auth-service | notification-service ("Google was connected to your account") | userId, fullName, email, provider (the provider's name, e.g. "Google"); sent when an existing account gets a social login, not at a sign-up with one ([social login](social-login.md)) |
| `grocery.user.events` | **PasswordResetRequested** | auth-service | notification-service (emails the token; email only) | userId, fullName, email, encryptedResetToken (AES-256-GCM, see [authentication](authentication.md#password-reset-tokens); the plain resetToken of version 1 was removed in version 3), expiresAt |
| `grocery.store.events` | **StoreRegistered** | store-service | notification-service (store owner + message to the vendor) | storeId, ownerUserId, storeName, city |
| `grocery.store.events` | **StoreApproved** | store-service | auth-service (links the store to the vendor → `storeId` in the JWT), notification-service | storeId, ownerUserId, storeName, approvedByUserId |
| `grocery.store.events` | **StoreStatusChanged** | store-service | notification-service | storeId, ownerUserId, status (REJECTED, DISABLED, ACTIVE), reason |
| `grocery.inventory.events` | **StockChanged** | inventory-service (after every stock change) | product-read-service (stock in search: `availableQuantity`, `inStock`, `lowStock`) | productId, storeId, quantityOnHand, reservedQuantity, availableQuantity, lowStockThreshold, lowStock, stockVersion |
| `grocery.inventory.events` | **LowStockDetected** | inventory-service (only when the stock *becomes* low) | notification-service (alerts the vendor) | productId, storeId, availableQuantity, lowStockThreshold |
| `grocery.inventory.events` | **StockReserved** | inventory-service (OrderCreated, enough stock) | payment-service (COD accepted / online payment opened) | orderId, storeId, items (productId, quantity) |
| `grocery.inventory.events` | **StockReservationFailed** | inventory-service (OrderCreated, not enough stock; nothing reserved) | order-service (order CANCELLED) | orderId, storeId, reason, items (productId, requestedQuantity, availableQuantity) |
| `grocery.inventory.events` | **StockReleased** | inventory-service (OrderCancelled / OrderRejected / OrderDeliveryFailed) | (report later) | orderId, storeId |
| `grocery.order.events` | **OrderCreated** | order-service | inventory (reserve), payment (create payment), cart (empty the cart, count the coupon), report (order facts) | orderId, customerUserId, storeId, items (productId, productName, quantity, unitPrice), itemsTotal, couponCode, couponDiscount, deliveryFee, totalAmount, paymentMethod, commissionPercentage (store's rate at order time; optional field added in Phase 11) |
| `grocery.order.events` | **OrderConfirmed** | order-service (PaymentConfirmed) | notification (customer + "new order" to the vendor) | orderId, customerUserId, storeId, totalAmount, paymentMethod |
| `grocery.order.events` | **OrderCancelled** | order-service (customer, admin or system) | inventory (release), payment (refund or cancel), cart (free the coupon), delivery (cancel the delivery if not picked up yet), notification | orderId, customerUserId, storeId, reason, cancelledBy |
| `grocery.order.events` | **OrderAccepted**, **OrderPacked**, **OrderOutForDelivery** | order-service (OutForDelivery: after DeliveryPickedUp) | notification | orderId, customerUserId, storeId |
| `grocery.order.events` | **OrderRejected** | order-service (store) | inventory (release), payment (refund or cancel), cart (free the coupon), notification | orderId, customerUserId, storeId, reason |
| `grocery.order.events` | **OrderReadyForPickup** | order-service (store) | delivery-service (creates the delivery, assigns a partner) | orderId, customerUserId, storeId, store name/phone/address/PIN, delivery contact/address/PIN, paymentMethod, amountToCollect; optional storeLatitude/Longitude, deliveryLatitude/Longitude (null without coordinates; for the [tracking map](delivery-tracking.md)) |
| `grocery.order.events` | **OrderDelivered** | order-service (DeliveryCompleted) | inventory (commit), payment (COD collected), notification | orderId, customerUserId, storeId, totalAmount, paymentMethod |
| `grocery.order.events` | **OrderDeliveryFailed** | order-service (DeliveryFailed) | inventory (release), payment (refund or cancel), cart (free the coupon), notification (customer + vendor) | orderId, customerUserId, storeId, reason |
| `grocery.payment.events` | **PaymentConfirmed** | payment-service (COD accepted after StockReserved, or paid online) | order-service (CONFIRMED) | paymentId, orderId, customerUserId, amount, paymentMethod |
| `grocery.payment.events` | **PaymentFailed** | payment-service (online payment declined, or not paid before its deadline) | order-service (CANCELLED), notification | paymentId, orderId, customerUserId, reason |
| `grocery.payment.events` | **RefundCompleted** | payment-service (paid order cancelled, rejected or not delivered; money that arrived after the payment had ended) | order-service (paymentStatus REFUNDED), notification | paymentId, orderId, customerUserId, amount, refundReference |
| `grocery.delivery.events` | **DeliveryAssigned** | delivery-service (automatic or by an admin; again after a partner rejects) | order-service (partner name and phone on the order), notification (customer + partner) | deliveryId, orderId, customerUserId, storeId, partnerUserId, partnerName, partnerPhone |
| `grocery.delivery.events` | **DeliveryPickedUp** | delivery-service | order-service (OUT_FOR_DELIVERY) | deliveryId, orderId, customerUserId, storeId, partnerUserId |
| `grocery.delivery.events` | **DeliveryCompleted** | delivery-service | order-service (DELIVERED) | deliveryId, orderId, customerUserId, storeId, partnerUserId, amountCollected, proofPhotoUrl |
| `grocery.delivery.events` | **DeliveryFailed** | delivery-service (partner reports an issue after pick-up) | order-service (DELIVERY_FAILED) | deliveryId, orderId, customerUserId, storeId, partnerUserId, issue, note |
| `grocery.delivery.events` | **PartnerKycReviewed** | delivery-service (admin approves or rejects a partner's KYC; key = partnerUserId) | notification (tells the partner), report (active partners) | partnerUserId, fullName, status (APPROVED / REJECTED), note |
| `grocery.platform.events` | **PlatformSettingsChanged** | platform-service (super admin changes the settings; key = "settings") | cart-service (delivery fee, free delivery amount, minimum order) | currency, deliveryFee, freeDeliveryFrom, minimumOrderValue, changedByUserId |

**Amounts** in events are decimal text, e.g. `"201.00"`: exact (no floating point), and readable in Kafka UI.
**Keys**: order, payment and delivery events use the orderId; inventory's stock events use the productId, its reservation events the orderId.

**Read models built from events (Phase 11):** report-service reads the user, store, order, payment, inventory and delivery topics
(see its README for the tables). platform-service reads **every** domain topic as Avro `GenericRecord` for the audit log, so
new event types are audited without a code change.

**Live updates:** order-service also reads **every** `grocery.order.events` event itself, for the vendors' live order
streams (`OrderChangeListener`). It uses only the key (the orderId), not the event, with its own consumer group on each
instance (`order-service-live-<random>`), from the newest event on and without saving offsets. A new order event type
reaches the vendor panel without a code change. See the [order-service README](../order-service/README.md#live-order-updates-server-sent-events).

## The order saga (choreography)

No service coordinates the others; each reacts to events and publishes its own:

```text
customer POST /orders ──► order PENDING ──OrderCreated──┬──► inventory: reserve all items ──StockReserved──► payment
                                                        │                └──StockReservationFailed──► order CANCELLED
                                                        ├──► payment: create the PENDING payment
                                                        └──► cart: remove the ordered items, count the coupon
payment, after StockReserved:  COD ──PaymentConfirmed──► order CONFIRMED (OrderConfirmed)
                               ONLINE: customer pays ──PaymentConfirmed / PaymentFailed──► order CONFIRMED / CANCELLED
                               ONLINE, not paid before the deadline (15 min) ──PaymentFailed──► order CANCELLED
store: accept ─ pack ─ ready for pickup ──OrderReadyForPickup──► delivery: assign a partner ──DeliveryAssigned──► order shows the partner
partner: accept ─ pick up ──DeliveryPickedUp──► order OUT_FOR_DELIVERY ─ deliver ──DeliveryCompleted──► order DELIVERED
         ──OrderDelivered──► inventory commits, COD collected
partner: issue after pick-up ──DeliveryFailed──► order DELIVERY_FAILED ──OrderDeliveryFailed──► (compensation below)
compensation: OrderCancelled / OrderRejected / OrderDeliveryFailed ──► inventory releases, payment refunds a paid order
              (RefundCompleted) or cancels a COD one, cart frees the coupon; OrderCancelled also cancels a delivery not yet picked up
```

Events of one order share the key (orderId), so on one topic they arrive in order (OrderCreated before OrderCancelled).
Across topics there is no order: payment-service may see StockReserved before OrderCreated; it then fails on purpose and the Kafka error handler retries until the payment exists.

**StockChanged notes**
- It carries the **full current stock**, not the difference, so a consumer never has to ask inventory-service. `stockVersion` (the row's JPA version) increases with every change; product-read-service ignores an event older than the stock it already has.
- product-read-service reads inventory events as `GenericRecord` by field name, so it has no copy of the schema: the contract is [inventory-service/src/main/avro/produced/StockChanged.avsc](../inventory-service/src/main/avro/produced/StockChanged.avsc).
- The topic is not compacted. To send the current stock again (seed data, rebuilding the search index), call `POST http://localhost:8106/internal/inventory/stock-snapshot`: it publishes one StockChanged per product, with unchanged versions.

## Change Data Capture (Debezium table CDC, catalog CQRS) - implemented in Phase 4

| Topic | Source table | Consumer |
|---|---|---|
| `cdc.product_db.products` | `product_db.products` | product-read-service → Elasticsearch index `products` |
| `cdc.product_db.categories` | `product_db.categories` | product-read-service → Elasticsearch index `categories` |

Connector: [product-service/debezium/product-cdc-connector.json](../product-service/debezium/product-cdc-connector.json).

- **One message per changed row.** The key is the row id (text). The value is the row after the change, with field names exactly as the MySQL columns. Debezium generates the Avro schema from the table, so consumers read it as `GenericRecord`.
- Column types: DECIMAL → text (`"45.00"`), DATETIME → epoch millis, JSON → text, BOOLEAN → `1`/`0`. A deleted row arrives with `__deleted = "true"`.
- On its first start the connector takes an **initial snapshot** (every existing row), then streams new changes.
- The topics are **compacted**: Kafka keeps the latest change of every row forever, so the read model can always be rebuilt (`scripts\rebuild-product-index.ps1`).

Table CDC is used only inside the catalog bounded context (product write → product read): the read side is allowed to know the write side's table layout.
Between bounded contexts, services publish the named domain events above.

## Topics: settings and delivery guarantees

Set by [create-topics.sh](../infrastructure/kafka/create-topics.sh); every run applies the settings again, so existing
topics change too.

| Topic | Owner (producer) | Partitions | Key | Kept | Cleanup |
|---|---|---|---|---|---|
| `grocery.user.events` | auth-service | 3 | userId | 7 days | delete |
| `grocery.store.events` | store-service | 3 | storeId | 7 days | delete |
| `grocery.inventory.events` | inventory-service | 3 | productId (stock), orderId (reservations) | 7 days | delete |
| `grocery.order.events` | order-service | 3 | orderId | 7 days | delete |
| `grocery.payment.events` | payment-service | 3 | orderId | 7 days | delete |
| `grocery.delivery.events` | delivery-service | 3 | orderId | 7 days | delete |
| `grocery.platform.events` | platform-service | 3 | `settings` | 7 days | delete |
| `cdc.product_db.products`, `cdc.product_db.categories` | product-service (Debezium table CDC) | 3 | row id | the latest change of every row, for ever | compact |
| `<each topic above>.DLT` | the consumers' error handlers | 3 | the original key | 30 days | delete |
| `__debezium-heartbeat.<topic.prefix>` (one per connector) | Kafka Connect | 1 | the connector | the latest heartbeat | compact |

**Durability:** replication factor 1 and `min.insync.replicas` 1 locally (one broker). Production: 3 and 2
(`REPLICATION_FACTOR=3 MIN_ISR=2` for create-topics.sh), so one broker can fail without losing an event or stopping the
producers. Every producer of events uses `acks=all` and is idempotent: Debezium (`CONNECT_PRODUCER_ENABLE_IDEMPOTENCE`)
and the dead-letter producers (`KafkaConsumerConfig`).

**Delivery: at least once, in order per key.**
- The outbox makes a business change and its event one database transaction: no event is lost, and none is published
  for a change that was rolled back.
- Debezium publishes from the binlog. After a crash Kafka Connect continues from its last saved position, so it can
  publish an event a **second time**. Every consumer therefore skips duplicates (`processed_event`, versions); never
  rely on "exactly once".
- Order is kept per key within one topic (all events of one order are on one partition), not across topics (see the saga).
- An event a consumer still cannot handle after its retries goes to `<topic>.DLT`. If even the DLT cannot be written,
  the event is not skipped: Kafka delivers it again. Inspect and replay: [resilience.md](resilience.md#dead-letters).
- How long each piece is kept, from short to long: binlog 7 days, topics 7 days, outbox rows 14 days, `processed_event`
  30 days, dead letters 30 days ([why](resilience.md#event-tables)). A consumer more than 7 days behind loses events,
  so its alert fires after minutes (KafkaConsumerNoProgress).

## Looking at events locally

- **Kafka UI:** http://localhost:8090 → Topics → `grocery.store.events` → Messages (shows key, headers, decoded Avro).
- **Command line:**
  ```powershell
  docker exec grocery-schema-registry kafka-avro-console-consumer --bootstrap-server kafka:29092 `
    --topic grocery.store.events --from-beginning --property schema.registry.url=http://localhost:8081 `
    --property print.key=true --property key.deserializer=org.apache.kafka.common.serialization.StringDeserializer
  ```
- **Schemas:** http://localhost:8081/subjects
- **Connectors:** http://localhost:8083/connectors?expand=status
