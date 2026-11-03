# notification-service

Turns the platform's events into **in-app notifications** (the bell icon in every panel) and **emails** (Mailpit locally). It only listens: it publishes nothing and no service calls it to send something.

| | |
|---|---|
| Port | `8111` |
| Database | `notification_db` (MySQL, user `notification_user`) |
| Swagger UI | http://localhost:8111/swagger-ui.html (or the central one at http://localhost:8080/swagger-ui.html) |
| Publishes events | none |
| Consumes events | users, stores, orders, payments, stock and deliveries (see the table below) |
| Emails | SMTP; locally Mailpit: every email shows up at http://localhost:8025, nothing leaves your machine |

## Who gets what

| Event | Receiver | Notification |
|---|---|---|
| `UserRegistered` | the new user | Welcome (and the user is added to the contact book) |
| `PasswordResetRequested` | the user | **Email only** with the reset code, decrypted from the event (`security/ResetTokenCipher`; a secret is never stored as an in-app message or logged) |
| `SocialLoginLinked` | the user | "Google was connected to your account" (not after a sign-up with Google); not them? "Forgot password" ([social login](../docs/social-login.md)) |
| `StoreRegistered`, `StoreApproved`, `StoreStatusChanged` | the vendor | Store received / approved / rejected, disabled or enabled |
| `OrderConfirmed` | customer **and** vendor | "Order confirmed" / "New order - accept or reject it" |
| `OrderAccepted`, `OrderPacked`, `OrderOutForDelivery`, `OrderDelivered`, `OrderRejected` | customer | The order's progress |
| `OrderCancelled` | customer, and the vendor unless the system cancelled it | Cancelled + reason |
| `OrderDeliveryFailed` | customer **and** vendor | Not delivered + reason (refund / items come back) |
| `DeliveryAssigned` | customer **and** delivery partner | Who brings the order / "New delivery" |
| `PartnerKycReviewed` | the delivery partner | KYC approved (go online) / needs a correction + reason |
| `PaymentFailed`, `RefundCompleted` | customer | Payment declined / money refunded |
| `LowStockDetected` | vendor | Restock product #… |

## How it works

```text
event (Kafka) ──► XxxEventsConsumer (one per topic) ──► EventNotificationService (skip duplicates)
                    ──► NotificationTemplates (the text) ──► NotificationDispatcher
                          ├── save the in-app Notification
                          └── EmailSender (when the contact book has the address) → emailStatus SENT / FAILED / NOT_SENT
```

- **Contact book** (`contacts`): user id → name and email, filled from `UserRegistered`. Events only carry user ids; this way the service never asks auth-service for an email address.
- **Store owners** (`store_contacts`): store id → vendor, filled from `StoreRegistered` / `StoreApproved`. Order and stock events only carry the store id.
- The **seed users and stores** were inserted by SQL elsewhere, so no events exist for them: `db/seed/R__insert_default_contacts.sql` adds them locally.
- A mail server problem never blocks the consumer: the email is marked `FAILED` (see the log and the admin endpoint) and the in-app notification is still saved.
- Duplicate events never send a second email (`processed_event` table).
- On its first start the service reads every topic from the beginning, so it also creates notifications for older events.

## Endpoints

| Method | Path | Who (enforced by the gateway) | What |
|---|---|---|---|
| GET | `/api/v1/notifications?unreadOnly=` | any logged-in user | My notifications, newest first |
| GET | `/api/v1/notifications/unread-count` | any logged-in user | The badge number |
| PATCH | `/api/v1/notifications/{id}/read` | any logged-in user | Mark one as read |
| POST | `/api/v1/notifications/read-all` | any logged-in user | Mark all as read |
| GET | `/api/v1/admin/notifications?userId=&type=&emailStatus=` | ADMIN, SUPER_ADMIN | Everything sent, e.g. `emailStatus=FAILED` |

## Run it

```powershell
cd notification-service; .\mvnw spring-boot:run   # Flyway creates the tables + the contact book of the seed users
```

Open Mailpit at http://localhost:8025, then run [http/notification-service.http](http/notification-service.http) or the MVP flow [e2e/mvp-order-flow.http](../e2e/mvp-order-flow.http).

## Test it

```powershell
.\mvnw verify      # Docker must be running (MySQL + Kafka via Testcontainers)
```

| Test | Kind | What it proves |
|---|---|---|
| `EventNotificationServiceImplTest` | Mockito | Welcome + contact book, customer and store owner on OrderConfirmed, system cancellations not sent to the store, password reset email only (decrypted; an old event without an encrypted token is skipped when expired, otherwise to the DLT; nothing sent when it cannot be decrypted), duplicates |
| `ResetTokenCipherTest` | plain JUnit | Decrypts a token that auth-service's code encrypted; another user id, a changed text or another key is refused |
| `EmailSenderTest` | Mockito | Sender address; a mail server problem is FAILED, not an exception |
| `NotificationFlowIntegrationTest` | `@SpringBootTest` + MySQL + Kafka containers | Events → contacts → notifications → emails; the user reads them in the API |

The integration test mocks `JavaMailSender`, so it switches off the mail health check (`management.health.mail.enabled=false`).

## Configuration

| Property | Env variable | Local default |
|---|---|---|
| `server.port` | `SERVER_PORT` | `8111` |
| `spring.datasource.url` / `username` / `password` | `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:mysql://localhost:3307/notification_db?...` / `notification_user` / `notification_pass` |
| `spring.mail.host` / `port` | `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025` (Mailpit) |
| `app.notification.from-email` | `MAIL_FROM` | `no-reply@grocery.local` |
| `app.password-reset.event-key` | `PASSWORD_RESET_EVENT_KEY` | local-only key: AES-256, 32 bytes in Base64, **the same as auth-service's**. A wrong key sends reset events straight to `grocery.user.events.DLT` ([password-reset tokens](../docs/authentication.md#password-reset-tokens)) |
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `spring.kafka.properties.schema.registry.url` | `SCHEMA_REGISTRY_URL` | `http://localhost:8081` |
