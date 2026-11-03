# payment-service

Owns **payments and refunds**. One payment per order, created from `OrderCreated`. Online payments go through a `PaymentGateway`: a **mock** gateway (no real money) or **Razorpay**.

| | |
|---|---|
| Port | `8109` |
| Database | `payment_db` (MySQL, user `payment_user`) |
| Swagger UI | http://localhost:8109/swagger-ui.html (or the central one at http://localhost:8080/swagger-ui.html) |
| Publishes events | `PaymentConfirmed`, `PaymentFailed`, `RefundCompleted` on `grocery.payment.events` (outbox + Debezium) |
| Consumes events | `OrderCreated`, `OrderCancelled`, `OrderRejected`, `OrderDeliveryFailed`, `OrderDelivered` (order), `StockReserved` (inventory) |
| Calls (REST) | Razorpay's API, in razorpay mode only |

## Payment states (`PaymentStatus`, `Payment` entity)

```text
PENDING ──StockReserved──► CASH_ON_DELIVERY (COD) ──OrderDelivered──► COLLECTED
   │              └──────► AWAITING_PAYMENT (ONLINE, until payBefore) ──customer pays──► PAID ──order cancelled──► REFUNDED
   │                                              └──declined / not paid in time──► FAILED
   └── order cancelled before anything was paid ──► CANCELLED

FAILED / CANCELLED ──the money arrives anyway (late)──► PAID ──refunded at once──► REFUNDED
```

| Event in | What happens | Event out |
|---|---|---|
| `OrderCreated` | create the PENDING payment for the order total | – |
| `StockReserved`, COD | `CASH_ON_DELIVERY` | `PaymentConfirmed` |
| `StockReserved`, ONLINE | `AWAITING_PAYMENT`: the gateway opens a payment (`gatewayOrderId`), the customer must pay before `payBefore` (15 minutes) | – |
| customer pays (mock, or Razorpay Checkout / webhook) | `PAID` | `PaymentConfirmed` |
| mock: the bank declines | `FAILED` | `PaymentFailed` |
| Razorpay: an attempt fails (`payment.failed`) | stays `AWAITING_PAYMENT`, `failureReason` shows why; the customer can try again | – |
| the deadline passes (`PaymentTimeoutJob`) | first ask the gateway: paid after all → `PAID`; otherwise `FAILED` "Not paid in time" | `PaymentConfirmed` / `PaymentFailed` |
| `OrderCancelled`, `OrderRejected`, `OrderDeliveryFailed` | PAID → refund through the gateway → `REFUNDED`; otherwise `CANCELLED` | `RefundCompleted` (refund only) |
| money arrives after FAILED / CANCELLED | refunded at once → `REFUNDED` | `RefundCompleted` |
| `OrderDelivered` | COD → `COLLECTED` | – |

**Why the payment waits for `StockReserved`:** a customer is never asked to pay for items that are not available.

**Why a deadline:** an unpaid order keeps its stock reserved. After `app.payment.online-payment-timeout` the payment fails, `PaymentFailed` cancels the order and inventory releases the stock.

**Events from two topics arrive in any order.** `StockReserved` (inventory topic) can come before `OrderCreated` (order topic). The handler then throws, the transaction rolls back, and the Kafka error handler retries (1 s, 2 s, 4 s, 8 s, 16 s) until the payment exists. Each topic has its own listener class (its own thread), so these retries never block the order topic.

## The payment gateway

`PaymentGateway` is an interface: `createGatewayOrder`, `refund`, `findCapturedPayment`. `app.payment.gateway` chooses the implementation:

| Value | Class | How the customer pays |
|---|---|---|
| `mock` (default) | `MockPaymentGateway` | No network, no money: `POST /api/v1/payments/orders/{orderId}/mock-pay {"success": true\|false}` (`MockPaymentController` exists in mock mode only) |
| `razorpay` | `RazorpayPaymentGateway` (RestClient, Basic auth with key id + secret) | Razorpay Checkout in the browser, confirmed by `razorpay-verify` and/or the webhook |

What happens to an online payment (paid, failed, late payment refunded) is in one class, `OnlinePaymentRecorder`, used by the mock gateway, both Razorpay paths and the timeout job.

### Razorpay step by step

```text
StockReserved ──► POST https://api.razorpay.com/v1/orders {amount in paise} ──► order_XXXX (gatewayOrderId)
frontend:  GET  /api/v1/payments/orders/{id}/razorpay-checkout  ──► keyId, razorpayOrderId, amountInPaise
           Razorpay Checkout (checkout.js) ──► the customer pays ──► razorpay_payment_id + razorpay_signature
           POST /api/v1/payments/orders/{id}/razorpay-verify  ──► signature OK? ──► PAID (PaymentConfirmed)
Razorpay:  POST /api/v1/payments/webhooks/razorpay (payment.captured / payment.failed, header X-Razorpay-Signature)
```

- **Settings check at start-up** (`RazorpaySettingsCheck`, razorpay mode only, before the Kafka listeners start). The
  service does not start, with a message that names the environment variable, when:
  - `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` or `RAZORPAY_WEBHOOK_SECRET` is missing or still the placeholder
    (`replace_me`). The webhook secret matters most: the webhook URL is public, so with the placeholder anyone could
    sign a fake `payment.captured`;
  - the key id does not fit `RAZORPAY_MODE` (`test` takes only `rzp_test_` keys, `live` only `rzp_live_`), so test and
    live keys are never mixed up;
  - Razorpay answers 401 to one read-only call (`GET /orders?count=1`): the key id and secret are wrong.

  When Razorpay cannot be reached, it only logs a warning (opening a payment is retried). It never logs a key or secret.
- **Signatures** (`RazorpaySignatureVerifier`, HMAC-SHA256): Checkout's signature is over `order_id|payment_id` with the key secret; a webhook's is over the raw body with the webhook secret. A wrong signature → 400 and nothing changes.
- **Amount and currency:** a `payment.captured` webhook marks the payment paid only when its `amount` (paise) and
  `currency` (`INR`) are exactly the order's; otherwise it is logged as an error and ignored. (Razorpay takes only the
  order's amount, so this is a last line of defence.) `razorpay-verify` checks that the signed `razorpay_order_id` is
  this payment's own `gatewayOrderId`, so the signature of another, cheaper order cannot pay it.
- **Verify and webhook may both arrive.** Whichever comes second finds the payment PAID and does nothing. A webhook Razorpay repeats is skipped by its `X-Razorpay-Event-Id` (`processed_event` table).
- The webhook path is public at the gateway (no JWT): Razorpay cannot log in, the signature proves the sender.
- **Lost webhook:** before failing an overdue payment, the job asks Razorpay `GET /orders/{id}/payments`. A captured payment there → PAID.
- **Try it:** create test keys in the Razorpay dashboard (Test mode), put them in `payment-service/.env` (copy `.env.example`: `PAYMENT_GATEWAY=razorpay`, `RAZORPAY_MODE=test`, `RAZORPAY_KEY_ID=rzp_test_...`, `RAZORPAY_KEY_SECRET=...`, `RAZORPAY_WEBHOOK_SECRET=...`) and restart the service (the log then says "Razorpay test mode: Razorpay accepted the key id and secret") and use requests 16–19 of `http/payment-service.http`. To receive webhooks on your machine, expose port 8080 with a tunnel (e.g. ngrok) and register `https://<tunnel>/api/v1/payments/webhooks/razorpay` for the events `payment.captured` and `payment.failed`.

## Endpoints

| Method | Path | Who (enforced by the gateway) | What |
|---|---|---|---|
| GET | `/api/v1/payments/orders/{orderId}` | CUSTOMER | The payment of my order (`canPayNow`, `payBefore`, `failureReason`) |
| POST | `/api/v1/payments/orders/{orderId}/mock-pay` | CUSTOMER | Mock mode: pay or be declined |
| GET | `/api/v1/payments/orders/{orderId}/razorpay-checkout` | CUSTOMER | Razorpay mode: what Checkout needs |
| POST | `/api/v1/payments/orders/{orderId}/razorpay-verify` | CUSTOMER | Razorpay mode: confirm the payment (signature checked) |
| POST | `/api/v1/payments/webhooks/razorpay` | Razorpay (no login) | Razorpay mode: payment events |
| GET | `/api/v1/admin/payments?status=&method=&storeId=` · `/{id}` | ADMIN, SUPER_ADMIN | All payments, e.g. `status=REFUNDED` |
| GET | `/api/v1/admin/payments/summary?from=&to=` | ADMIN, SUPER_ADMIN | Online paid, cash collected, refunded, and every method/status total (India time, max. 366 days) |
| GET | `/internal/payments/orders/{orderId}` | other services only | The payment of an order |

## Run it

```powershell
cd payment-service; .\mvnw spring-boot:run   # Flyway creates the tables (no seed data)
.\scripts\register-connectors.ps1            # from the repo root, once: publishes the outbox to Kafka
```

To see the deadline without waiting 15 minutes: `$env:ONLINE_PAYMENT_TIMEOUT="1m"; $env:PAYMENT_TIMEOUT_CHECK_INTERVAL="10s"` before starting it, then run `e2e\phase10-payments.http`.

## Test it

```powershell
.\mvnw verify      # Docker must be running (MySQL + Kafka via Testcontainers)
```

| Test | Kind | What it proves |
|---|---|---|
| `PaymentTest` | plain JUnit | Status rules: paid and refunded, COD collected, deadline, failed attempt keeps the payment open, late money only to be refunded |
| `PaymentSagaServiceImplTest` | Mockito | Payment from OrderCreated, COD vs ONLINE (with deadline) on StockReserved, retry when too early, refund vs cancel, duplicates |
| `PaymentTimeoutServiceImplTest` | Mockito | Overdue → FAILED; paid at the gateway without a webhook → PAID; paid meanwhile → untouched |
| `RazorpayPaymentServiceImplTest` | Mockito | Checkout details, verify (right/forged signature, another order's signature, sent twice), webhook captured / failed / late / repeated / forged / wrong amount / wrong currency |
| `RazorpaySettingsCheckTest` | `MockRestServiceServer` | Start-up check: placeholders and empty settings, a live key in test mode and the other way round, keys Razorpay rejects (401), Razorpay down (only a warning); no secret in a message |
| `RazorpaySignatureVerifierTest` | plain JUnit | HMAC-SHA256 against signatures calculated outside Java |
| `RazorpayPaymentGatewayTest` | `MockRestServiceServer` | Requests to Razorpay: paise, Basic auth, refund, captured payment lookup, errors → 503 |
| `RazorpayWebhookControllerTest` | `@WebMvcTest` | Raw body and headers reach the service; wrong signature → 400 |
| `PaymentRepositoryTest` | `@DataJpaTest` + MySQL container | Migration V4, the deadline query, the summary query |
| `PaymentFlowIntegrationTest` | `@SpringBootTest` + MySQL + Kafka containers | OrderCreated → StockReserved → mock pay → OrderCancelled → REFUNDED; StockReserved before OrderCreated still works |

## Configuration

Locally (profile `local`, the default) the service also reads **`payment-service/.env`**, if it exists: `KEY=value`
lines with the env variable names below (`spring.config.import` in `application-local.yml`). Copy `.env.example` to
`.env` and put your Razorpay test keys there; `.env` is in `.gitignore` and `.dockerignore`, so the keys stay on your
machine. A real environment variable still wins over the file. The tests, Docker and Kubernetes do not read it.

| Property | Env variable | Local default |
|---|---|---|
| `server.port` | `SERVER_PORT` | `8109` |
| `spring.datasource.url` / `username` / `password` | `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:mysql://localhost:3307/payment_db?...` / `payment_user` / `payment_pass` |
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `spring.kafka.properties.schema.registry.url` | `SCHEMA_REGISTRY_URL` | `http://localhost:8081` |
| `app.payment.gateway` | `PAYMENT_GATEWAY` | `mock` |
| `app.payment.online-payment-timeout` | `ONLINE_PAYMENT_TIMEOUT` | `15m` |
| `app.payment.timeout-check-interval` | `PAYMENT_TIMEOUT_CHECK_INTERVAL` | `60s` |
| `app.payment.razorpay.mode` | `RAZORPAY_MODE` | `test` (`live` takes only `rzp_live_` keys) |
| `app.payment.razorpay.base-url` | `RAZORPAY_BASE_URL` | `https://api.razorpay.com/v1` |
| `app.payment.razorpay.key-id` / `key-secret` / `webhook-secret` | `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` / `RAZORPAY_WEBHOOK_SECRET` | placeholders (set your test keys) |
| `spring.http.clients.connect-timeout` / `read-timeout` | – | `2s` / `10s` |
