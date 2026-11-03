# delivery-service

Owns **delivery partners and deliveries**: partner profiles and KYC, online/offline, delivery zones and payouts, assigning orders to partners, the delivery steps, proof of delivery, issues and earnings.

| | |
|---|---|
| Port | `8110` |
| Database | `delivery_db` (MySQL, user `delivery_user`) + Redis (who is online; the partners' live positions) |
| Swagger UI | http://localhost:8110/swagger-ui.html (or the central one at http://localhost:8080/swagger-ui.html) |
| Publishes events | `DeliveryAssigned`, `DeliveryPickedUp`, `DeliveryCompleted`, `DeliveryFailed`, `PartnerKycReviewed` on `grocery.delivery.events` (outbox + Debezium) |
| Consumes events | `UserRegistered` (user), `OrderReadyForPickup`, `OrderCancelled` (order) |

## How an order gets delivered

```text
OrderReadyForPickup ─► Delivery WAITING_FOR_PARTNER (zone = the zone that covers the store's PIN code)
                         │ first free partner of the zone: approved KYC, online, no other delivery
                         ▼
                      ASSIGNED ──reject──► next free partner (or WAITING_FOR_PARTNER again)
                         │ accept                                     DeliveryAssigned ─► order shows the partner
                         ▼
                      ACCEPTED ──pick-up──► PICKED_UP ──start──► OUT_FOR_DELIVERY ──deliver──► DELIVERED
                                            DeliveryPickedUp                                   DeliveryCompleted
                                            ─► order OUT_FOR_DELIVERY                          ─► order DELIVERED
                                                  │ issue (customer unavailable, wrong address, damaged ...)
                                                  ▼
                                                FAILED ──return to store──► RETURNED
                                                DeliveryFailed ─► order DELIVERY_FAILED
                                                (stock back, refund or cancel the payment, coupon freed)
OrderCancelled before pick-up ─► CANCELLED (the partner is free again)
```

**Assignment rule (`DeliveryAssignmentService`):** the first partner (lowest user id) of the delivery's zone who is approved, online and not busy, and who has not rejected this delivery before. It runs when a delivery is created, when a partner rejects one, and when a partner becomes free (goes online, delivers, returns a failed delivery). A partner has one delivery at a time. An admin can assign a delivery by hand.

**Online presence lives in Redis**, not MySQL: going online writes the key `delivery:partner-online:<userId>` that expires after `app.delivery.online-for` (30 minutes). The partner app repeats the call to stay online; a phone that dies simply drops offline.
See who is online, and for how long, in Redis Insight (http://localhost:5540, [docs/redis-insight.md](../docs/redis-insight.md)).

**Live tracking** ([docs/delivery-tracking.md](../docs/delivery-tracking.md)): from accepting until delivering, the
partner's phone sends its position (`PUT .../location`). Only the last one is kept, in Redis (key
`delivery:location:<deliveryId>`, deleted when tracking ends), never in MySQL. Every new position and every status change
goes out as a snapshot on the Redis channel `delivery-tracking`. Each instance forwards it to the customers that follow
that order on it (Server-Sent Events, `LiveTrackingStreams`). The store's and the customer's coordinates come with
`OrderReadyForPickup` and are saved on the delivery. With `TRACKING_ROUTING_URL` (an OSRM server), the snapshot also has
the route and the arrival time.

**Ratings:** after delivery the customer rates the delivery once (1–5 stars). The partner keeps `ratingCount` and `ratingTotal`, so the average (`ratingAverage`, one decimal) is shown in the profile and the admin list without extra queries.

**Partners come from auth-service:** `UserRegistered` with role `DELIVERY_PARTNER` creates a profile with KYC `NOT_SUBMITTED`. The partner sends vehicle and licence details (`PENDING`), an admin approves or rejects them (`PartnerKycReviewed`: the partner is notified, reports count approved partners). Only approved partners can go online.

## Endpoints

| Method | Path | Who (enforced by the gateway) | What |
|---|---|---|---|
| GET | `/api/v1/delivery/profile` | DELIVERY_PARTNER | My profile, KYC status and zone |
| PUT | `/api/v1/delivery/profile/kyc` | DELIVERY_PARTNER | Send KYC details (vehicle, licence, zone) |
| GET / POST | `/api/v1/delivery/availability` · `/online` · `/offline` | DELIVERY_PARTNER | Online status |
| GET | `/api/v1/delivery/earnings?from=&to=` | DELIVERY_PARTNER | Deliveries and payout per day (India time) |
| GET | `/api/v1/delivery/deliveries?active=true\|false` · `/{id}` | DELIVERY_PARTNER | My current or completed deliveries |
| POST | `/api/v1/delivery/deliveries/{id}/accept` · `/reject` · `/pick-up` · `/start` · `/deliver` · `/return` | DELIVERY_PARTNER | The delivery steps |
| POST | `/api/v1/delivery/deliveries/{id}/issue` | DELIVERY_PARTNER | Could not deliver: `{"issue": "CUSTOMER_UNAVAILABLE", "note": "..."}` |
| POST | `/api/v1/delivery/deliveries/{id}/proof` | DELIVERY_PARTNER | Proof photo (multipart `photo`, JPEG/PNG/WEBP, max 5 MB) |
| PUT | `/api/v1/delivery/deliveries/{id}/location` | DELIVERY_PARTNER | My position while delivering: `{"latitude", "longitude", "accuracyMeters", "recordedAt"}` |
| GET | `/api/v1/delivery/deliveries/{id}/tracking` | DELIVERY_PARTNER | My delivery on the map (store, customer, my last position, route) |
| GET | `/api/v1/delivery-tracking/orders/{orderId}` · `/stream` | CUSTOMER | The delivery of my order now · live (`text/event-stream`) |
| GET | `/delivery-media/**` | any logged-in user | The saved proof photos |
| GET / POST | `/api/v1/admin/delivery-partners?kycStatus=` · `/{userId}/approve` · `/{userId}/reject` | ADMIN, SUPER_ADMIN | KYC review |
| GET / POST / PUT | `/api/v1/admin/delivery-zones` · `/{id}` | ADMIN, SUPER_ADMIN | Zones (PIN codes) and the payout per delivery |
| GET / POST | `/api/v1/admin/deliveries?status=&partnerUserId=` · `/{id}/assign` | ADMIN, SUPER_ADMIN | All deliveries, manual assignment |
| POST · GET | `/api/v1/delivery-ratings` · `/orders/{orderId}` | CUSTOMER | Rate the delivery of a delivered order (1–5 stars, once) · my rating |
| GET | `/internal/deliveries/orders/{orderId}` | other services only | The delivery of an order |

## Seed data (local profile)

| Zone | PIN codes | Payout | Partner |
|---|---|---|---|
| 1 Indiranagar | 560038, 560008, 560071 | 40.00 | delivery1 (user 5, Arjun Singh), APPROVED |
| 2 Koramangala | 560034, 560095, 560047 | 40.00 | delivery2 (user 8, Karthik Reddy), APPROVED |

Fresh Mart (store 1, PIN 560038) is in zone 1, Green Basket (store 2, PIN 560034) in zone 2.

## Run it

```powershell
cd delivery-service; .\mvnw spring-boot:run "-Dspring-boot.run.profiles=local"   # Flyway creates the tables + seed data
.\scripts\register-connectors.ps1            # from the repo root, once: publishes the outbox to Kafka
```

Then use `http/delivery-service.http` (35 requests; 29–35 are the live tracking). Run `order-service/http/order-service.http` requests 1–12 first so an order is ready for pickup.

## Test it

```powershell
.\mvnw verify      # Docker must be running (MySQL + Kafka + Redis via Testcontainers)
```

| Test | Kind | What it proves |
|---|---|---|
| `DeliveryTest` | plain JUnit | Status rules: steps cannot be skipped, only the assigned partner can act, reject → back in the queue, failed → returned, cancel only before pick-up |
| `DeliveryPartnerTest` | plain JUnit | KYC: submit and approve, submit again after a rejection, only a pending KYC can be decided |
| `DeliveryAssignmentServiceTest` | Mockito | Offline, busy and rejecting partners are skipped; nobody online → the delivery waits; no zone → never assigned automatically |
| `DeliveryFlowIntegrationTest` | `@SpringBootTest` + MySQL + Kafka + Redis containers | UserRegistered → KYC → approve → online (until when) → OrderReadyForPickup → assigned → accept, pick-up, start, deliver → the three outbox events, today's earnings, the customer's rating in the partner's average |
| `DeliveryTrackingIntegrationTest` | `@SpringBootTest` + MySQL + Kafka + Redis containers | Live tracking: positions and status changes reach the customer's stream (through Redis Pub/Sub), the final state, the position deleted after delivery; only their own order / delivery; sharing starts at accept; refused and skipped positions |
| `RoutingClientTest` | JUnit + a fake OSRM (JDK HTTP server) | The route and arrival time from OSRM's answer; "NoRoute"; a router that is down (no error, logged once without positions); routing off |
| `GeoPointTest` | plain JUnit | Distances between two points |

## Configuration

| Property | Env variable | Local default |
|---|---|---|
| `server.port` | `SERVER_PORT` | `8110` |
| `spring.datasource.url` / `username` / `password` | `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:mysql://localhost:3307/delivery_db?...` / `delivery_user` / `delivery_pass` |
| `spring.data.redis.host` / `port` | `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` |
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `spring.kafka.properties.schema.registry.url` | `SCHEMA_REGISTRY_URL` | `http://localhost:8081` |
| `app.delivery.online-for` | – | `30m` |
| `app.tracking.routing.url` | `TRACKING_ROUTING_URL` | empty = no routes or arrival times (an OSRM server, e.g. `http://localhost:5000`) |
| `app.tracking.max-accuracy-meters` | `TRACKING_MAX_ACCURACY_METERS` | `1000` (less precise positions are refused) |
| `app.tracking.max-streams` | `TRACKING_MAX_STREAMS` | `2000` open live streams per instance |
| `app.tracking.*` (more) | – | see [docs/delivery-tracking.md](../docs/delivery-tracking.md#step-4-the-settings) |
| `app.media.upload-dir` | `MEDIA_UPLOAD_DIR` | `./uploads` |
| `app.media.public-base-url` | `MEDIA_PUBLIC_BASE_URL` | `http://localhost:8080/delivery-media` |
