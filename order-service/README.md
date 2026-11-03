# order-service

Owns **orders**: placing them from the cart, the order **state machine**, the store's workflow, cancellation and the tracking timeline. It starts the **order saga** and reacts to the stock, payment and delivery events.

| | |
|---|---|
| Port | `8108` |
| Database | `order_db` (MySQL, user `order_user`) |
| Swagger UI | http://localhost:8108/swagger-ui.html (or the central one at http://localhost:8080/swagger-ui.html) |
| Publishes events | `OrderCreated`, `OrderConfirmed`, `OrderCancelled`, `OrderAccepted`, `OrderRejected`, `OrderPacked`, `OrderReadyForPickup`, `OrderOutForDelivery`, `OrderDelivered`, `OrderDeliveryFailed` on `grocery.order.events` (outbox + Debezium) |
| Consumes events | `StockReservationFailed` (inventory), `PaymentConfirmed`, `PaymentFailed`, `RefundCompleted` (payment), `DeliveryAssigned`, `DeliveryPickedUp`, `DeliveryCompleted`, `DeliveryFailed` (delivery) |
| Calls (REST, only when placing an order) | cart-service (checkout summary), customer-service (address), store-service (store) |

## Order states (`OrderStatus`, `Order` entity)

```text
 PENDING ──► CONFIRMED ──► ACCEPTED ──► PACKED ──► READY_FOR_PICKUP ──► OUT_FOR_DELIVERY ──► DELIVERED
    │            │  └──► REJECTED (store)                                     └──► DELIVERY_FAILED (partner)
    └────────────┴──────────┴───────────┴──────────────┴──► CANCELLED (customer, admin or system)
```

- **PENDING**: placed; inventory is reserving the stock and payment-service is settling the payment.
- **CONFIRMED**: stock reserved **and** payment settled (paid online, or cash on delivery accepted). Only now does the store see the order as new.
- The customer can cancel until the order leaves the store (`OUT_FOR_DELIVERY`).
- Every change is a method on `Order` (`confirm`, `accept`, `reject`, `pack`, `markReadyForPickup`, `markOutForDelivery`, `markDelivered`, `failDelivery`, `cancel`). It refuses a transition that `OrderStatus` does not allow (400) and adds a step to the timeline (`order_status_history`).
- OUT_FOR_DELIVERY, DELIVERED and DELIVERY_FAILED come only from delivery-service's events: the delivery partner's actions move the order. The order also shows the partner's name and phone once one is assigned (`deliveryPartnerName`, `deliveryPartnerPhone`).

`paymentStatus` on the order: `PENDING` → `CASH_ON_DELIVERY` (COD) or `PAID` (online) → `PAID` when COD is delivered; `FAILED`; `CANCELLED` (cancelled or not delivered before anything was paid, e.g. COD); `REFUNDED`.

`paymentUrl` on the order (`OrderResponse`): where the customer pays an **online order that is not paid yet**
(`Order.awaitsOnlinePayment()`: ONLINE and `paymentStatus` PENDING), for example `"/orders/12"`. That is the website's
order page, where the payment window opens (Razorpay, or the test gateway). It is a path, not a full address, so it
works on every address the website runs on (5173, the kind clusters, NGINX). It is `null` for cash on delivery and once
the order is paid, its payment failed, or it was cancelled. The chat's "Pay Now" button uses it after an order placed in
the chat (see [the assistant plan](../docs/ai-assistant-plan.md), section 20).

## Placing an order

`POST /api/v1/orders {addressId, paymentMethod: COD|ONLINE, note, expectedCheckoutFingerprint?}`, optional header
`Idempotency-Key` (8-64 letters, digits, `-` or `_`):

0. **A repeat of the same request** (same customer, same `Idempotency-Key`: a retry after a timeout, a double click):
   the order it placed is returned (201), and nothing else happens. This is checked **before** the cart is read: after a
   timeout the order may exist while its cart is already empty. A malformed key gives 400.
1. cart-service: the checkout summary (today's prices, coupon, totals and its `checkoutFingerprint`). If it is not
   `readyForCheckout` → **409** with every problem in `errors`.
   - With `expectedCheckoutFingerprint` (the chat's Confirm button sends the one of the summary the customer saw): when
     the cart or its prices changed since → **409** "Your cart or its prices changed since the summary. Please check the
     new total." Nobody gets an order they did not see.
   - **The same cart again:** an order of exactly this cart (the same fingerprint) placed in the last 2 minutes and not
     cancelled or rejected → **409** "You placed this order a moment ago (order #N)." (also without a key: a second tab).
2. customer-service: the delivery address (404 if it is not the customer's).
3. store-service: the store must be ACTIVE, accepting orders, open (`app.order.require-store-open`, off in the local profile) and deliver to the address's PIN code.
4. Save the order **and** its `OrderCreated` event in one transaction (outbox), with the key and the fingerprint
   (`orders.idempotency_key`, unique per customer; `orders.checkout_fingerprint`; Flyway V13). Two requests with the same
   key at the same moment: the unique key lets one of them save, and the other one answers that order. The three calls
   happen **before** the transaction, so a slow service never holds a database connection.

The website's checkout page sends neither the key nor a fingerprint and works as before (only the 2-minute rule
applies to it too).

The order keeps **copies** of the store details, the address, the items and all amounts, and the store's **commission rate** at that moment (sent in `OrderCreated` for the commission report): later changes elsewhere do not change a placed order.

## Invoices

**The PDF** (`invoice/InvoicePdfGenerator`, OpenPDF) contains:
- the number `INV-000123`, the seller and the customer;
- every line with the GST inside its price (`tax = amount × GST% / (100 + GST%)`);
- the coupon, the delivery fee and the total;
- the payment line, for example "Payment: paid online (payment #42)" or "Payment: cash on delivery, paid (payment #43)".

It is built from the order's own copies. The payment id comes from `PaymentConfirmed` (`orders.payment_id`; empty for orders from before it was kept).

**When there is one:** once the store accepted the order (ACCEPTED to DELIVERED), as before. Otherwise the answer is 400.

**Stored once paid** (`service/impl/InvoiceServiceImpl`, `invoice/InvoiceStorage`). A paid order means paid online, or cash on delivery collected at the door.
1. The first download generates the PDF and stores it as a file, `<app.invoice.storage-dir>/2026/10/INV-000123.pdf`.
2. It also saves a row in `invoices`:
   - the order, customer, store and payment;
   - the file path (relative to the folder);
   - the size and SHA-256.
3. Every later download reads that file. It loads only the order row (for the access check) and the `invoices` row, never the items. It builds no PDF and calls no other service.

Unpaid cash on delivery (the vendor prints it for the bag) is generated for every download and **not stored**, so the stored invoice always shows the final, paid state. A stored invoice never changes.

| Situation | What happens |
|---|---|
| Two first downloads at the same time (double click, two replicas) | Step B locks the order row (`SELECT ... FOR UPDATE`, about 0.1 s). The second waits, then reads the file the first stored. `order_id` is unique, so there is never a second row |
| Generating fails (PDF library error) | 503 "The invoice could not be created right now. Please try again in a moment." Nothing is stored, so the next try generates again |
| Writing the file fails (disk full, no permission) | ERROR log `could not be stored`. The user still gets the PDF; nothing is recorded, so the next download tries to store it again |
| The stored file is missing, or its size or SHA-256 changed | WARN log `is missing` / `is damaged`. It is generated and stored again, and the row is updated |
| The folder cannot be written at start-up | ERROR log `Invoices cannot be stored in ...`. The service still starts; invoices are generated but not stored until the folder is fixed |

The file is written under a temporary name in the same folder, flushed to the disk, and then renamed in one step (atomic move), so a half-written invoice is never read.

**Keep the folder.** Invoices are documents (tax rules: keep them for years); back the folder up with the database. In Docker the folder is `/app/invoices`. In the kind clusters it is this folder on your machine (`data/invoices`), mounted into every pod of both clusters, so every replica finds every invoice; production needs shared storage (object storage or ReadWriteMany), see [deployment/kubernetes/README.md](../deployment/kubernetes/README.md).

## The saga (choreography)

```text
order PENDING ──OrderCreated──► inventory reserves ──StockReserved──► payment: COD accepted / ONLINE customer pays
                                          └──StockReservationFailed──► order CANCELLED (SYSTEM)
payment ──PaymentConfirmed──► order CONFIRMED ──OrderConfirmed
payment ──PaymentFailed────► order CANCELLED (SYSTEM)
OrderReadyForPickup ──► delivery assigns a partner ──DeliveryAssigned──► the order shows the partner
DeliveryPickedUp ──► order OUT_FOR_DELIVERY ──OrderOutForDelivery
DeliveryCompleted ──► order DELIVERED ──OrderDelivered ──► inventory commits the stock, payment marks COD cash collected
DeliveryFailed ──► order DELIVERY_FAILED ──OrderDeliveryFailed
OrderCancelled / OrderRejected / OrderDeliveryFailed ──► inventory releases the stock, payment refunds a paid order (RefundCompleted)
                                                         or cancels a COD one, cart frees the coupon
```

Handlers are idempotent (`processed_event` table) and ignore events that no longer fit, e.g. `PaymentConfirmed` for an order the customer already cancelled (payment-service refunds it when `OrderCancelled` arrives). There is one listener class per topic, so a retry on one topic never holds up the other.

## Live order updates (Server-Sent Events)

The vendor panel shows a new order the moment it arrives, without asking every few seconds. The browser opens one
request that stays open, and the server writes an event into it whenever an order of the store changes:

```text
GET /api/v1/vendor/orders/stream        (VENDOR; Accept: text/event-stream, application/json)

event: ready                            once, when the stream is open
data: {}

event: order                            an order of the store changed (new, accepted, cancelled ...)
data: {"orderId":194,"status":"CONFIRMED"}

: keep-alive                            every 15 s, so proxies keep the connection open
```

How a change gets there:

```text
any order change ──outbox──► Debezium ──► Kafka grocery.order.events (key = order id)
  ──► OrderChangeListener on EVERY instance ──► reads the order (its store and status)
  ──► VendorOrderStreams ──► the open streams of that store on this instance
```

- **The stream says which order changed, not the data.** The page then loads what it shows with the normal endpoints
  below, so the lists, pages and counters have one source.
- **A consumer group per instance** (`order-service-live-<random>`): a vendor's stream is open on one instance, and a
  shared group would hand each event to only one instance. With its own group, every instance sees every event. It
  starts at the **newest** event (the other listeners start at the oldest): a live stream only needs what happens from
  now on. It never saves its place (`ackMode = "MANUAL"`, nothing acknowledged), so a stopped instance leaves no group
  behind with a growing lag (Kafka drops it within minutes; the `KafkaConsumerNoProgress` alert stays quiet).
- **Never an error for Kafka:** the listener does nothing while no stream is open, and logs a problem instead of throwing
  it (a live update is not worth a retry or a dead letter; the page also checks once a minute).
- **Limits:** a stream closes after 30 minutes and the browser opens a new one; at most 2,000 streams per instance (one
  more gets 503, the browser tries again). On shutdown every stream is closed first, so the browsers move to another
  instance at once. The gauge `order_vendor_streams` (Prometheus) counts the open ones. Serve the site over HTTP/2:
  over HTTP/1.1 a browser allows 6 connections per site, and every open vendor tab keeps one (as for tracking,
  [docs/delivery-tracking.md](../docs/delivery-tracking.md)).
- **Errors need JSON in Accept:** asked for `text/event-stream` alone, Spring cannot write an error answer (403, 404)
  and sends 500 instead. The frontend's `apiStream()` accepts both.
- **A closed page is normal:** Tomcat reports it as `AsyncRequestNotUsableException`; the stream is forgotten at once
  (`emitter.onError`), and `GlobalExceptionHandler` logs it at DEBUG instead of as an "Unexpected error".
- The gateway gives the stream its own route without the 10 s response timeout (`order-service-vendor-order-stream`).
- Code: `live/VendorOrderStreams.java`, `live/OrderChangeListener.java`, `VendorOrderController.streamStoreOrders()`.
  The customer's tracking page works the same way ([docs/delivery-tracking.md](../docs/delivery-tracking.md)).

## Endpoints

| Method | Path | Who (enforced by the gateway) | What |
|---|---|---|---|
| POST | `/api/v1/orders` | CUSTOMER | Place an order from my cart |
| GET | `/api/v1/orders?status=` · `/{id}` | CUSTOMER | My order history · one order with its timeline |
| POST | `/api/v1/orders/{id}/cancel` | CUSTOMER | Cancel (optional `{reason}`) |
| GET | `/api/v1/vendor/orders?status=` · `/{id}` | VENDOR | My store's orders (`status=CONFIRMED` = new ones) |
| GET | `/api/v1/vendor/orders/stream` | VENDOR | Live updates of my store's orders (`text/event-stream`, see [Live order updates](#live-order-updates-server-sent-events)) |
| POST | `/api/v1/vendor/orders/{id}/accept` · `/reject` · `/pack` · `/ready-for-pickup` | VENDOR | The store workflow (`reject` needs `{reason}`) |
| GET | `/api/v1/admin/orders?customerUserId=&storeId=&status=` · `/{id}` | ADMIN, SUPER_ADMIN | All orders |
| POST | `/api/v1/admin/orders/{id}/cancel` | ADMIN, SUPER_ADMIN | Cancel a problem order `{reason}` |
| GET | `/api/v1/orders/{id}/invoice` · `/api/v1/vendor/orders/{id}/invoice` · `/api/v1/admin/orders/{id}/invoice` | CUSTOMER · VENDOR · ADMIN | The tax invoice as a PDF (once the store accepted the order; stored and reused once paid, see [Invoices](#invoices)) |
| GET | `/internal/orders/{id}` | other services only | Any order |

## Run it

```powershell
cd order-service; .\mvnw spring-boot:run     # Flyway creates the tables (no seed orders)
.\scripts\register-connectors.ps1            # from the repo root, once: publishes the outbox to Kafka
```

Placing an order needs cart-service, customer-service and store-service; the saga needs inventory-service and payment-service.
The whole saga through the gateway is in [e2e/phase7-order-saga.http](../e2e/phase7-order-saga.http).

## Test it

```powershell
.\mvnw verify      # Docker must be running (MySQL + Kafka via Testcontainers)
```

| Test | Kind | What it proves |
|---|---|---|
| `OrderTest` | plain JUnit | State machine, timeline, cancel until it leaves the store, payment status on cancel |
| `CustomerOrderServiceImplTest` | Mockito | Order + OrderCreated from the checkout summary, cart problems → 409, delivery area, store hours |
| `OrderSagaServiceImplTest` | Mockito | Confirm, ignore a late PaymentConfirmed, duplicates, out-of-stock names, payment failure |
| `CustomerOrderControllerTest` | `@WebMvcTest` | Validation, 409 with every problem, 503 (checkout, invoice), cancel without a body |
| `OrderRepositoryTest` | `@DataJpaTest` + MySQL container | Items and timeline saved, search filters, own orders only |
| `OrderSagaIntegrationTest` | `@SpringBootTest` + MySQL + Kafka containers | PaymentConfirmed (sent twice) → CONFIRMED → accepted; StockReservationFailed → CANCELLED |
| `VendorOrderControllerTest` | `@WebMvcTest` + the real `VendorOrderStreams` | The live stream: `ready` at once, then only its own store's changes; no-cache and no proxy buffering; a browser that left is forgotten at once and not logged as an error; 503 when the instance is full; 403 without a store |
| `OrderChangeListenerTest` | Mockito | An order event reaches the order's store; nothing is read while no stream is open; a problem is logged, never thrown |
| `VendorOrderStreamIntegrationTest` | `@SpringBootTest` + MySQL + Kafka containers | An order event on Kafka reaches the stream of that order's store and no other (the listener's group, newest events only, value as bytes) |
| `InvoicePdfGeneratorTest` | plain JUnit (+ OpenPDF's text extractor) | The PDF contains the invoice number, store, customer, items and total; the payment line; no invoice for a pending order; tax inside a price |
| `InvoiceStorageTest` | plain JUnit + `@TempDir` | Year/month folder in Indian time, read back, replace, no temp file left, paths outside the folder refused, unwritable folder |
| `InvoiceServiceIntegrationTest` | `@SpringBootTest` + MySQL container + a temp folder | Generated once and then read from the file; 5 parallel first downloads → 1 PDF and 1 row; deleted or damaged file → generated again; unpaid COD not stored, stored after delivery; generation failure → 503 then a retry works; disk failure → PDF still sent |

## Configuration

| Property | Env variable | Local default |
|---|---|---|
| `server.port` | `SERVER_PORT` | `8108` |
| `spring.datasource.url` / `username` / `password` | `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:mysql://localhost:3307/order_db?...` / `order_user` / `order_pass` |
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `spring.kafka.properties.schema.registry.url` | `SCHEMA_REGISTRY_URL` | `http://localhost:8081` |
| `app.services.cart-service` / `customer-service` / `store-service` | `CART_SERVICE_URL` / `CUSTOMER_SERVICE_URL` / `STORE_SERVICE_URL` | `http://localhost:8107` / `:8102` / `:8103` |
| `app.order.require-store-open` | – | `true` (`false` in the local profile) |
| `app.vendor-order-stream.heartbeat-interval` / `stream-timeout` | – | `15s` / `30m` (a keep-alive this often; a stream closes after this and the browser opens a new one) |
| `app.vendor-order-stream.max-streams` | `VENDOR_ORDER_MAX_STREAMS` | `2000` live streams per instance (one more gets 503) |
| `app.invoice.storage-dir` | `INVOICE_STORAGE_DIR` | `./data/invoices` (relative to where the service runs, ignored by git; `/app/invoices` in the image) |
