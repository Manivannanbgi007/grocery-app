# customer-service

Owns **customer profiles and delivery addresses**. Login data (email, password, role) stays in auth-service.

| | |
|---|---|
| Port | `8102` |
| Database | `customer_db` (MySQL, user `customer_user`) |
| Swagger UI | http://localhost:8102/swagger-ui.html (or the central one at http://localhost:8080/swagger-ui.html) |
| Publishes events | none yet |
| Consumes events | `UserRegistered` from `grocery.user.events` → creates the profile for new CUSTOMERs |

## How a profile is created

```text
customer registers ─► auth-service saves the user + UserRegistered in its outbox (one transaction)
                   ─► Debezium publishes it to grocery.user.events
                   ─► customer-service (UserEventsConsumer) creates the profile
```

Usually this takes about a second. If `/me` is called before the event has arrived, the answer is a 404 with a "try again in a moment" message.

## Address rules (in the `Customer` entity)

- At most 10 addresses per customer.
- Exactly one default address. The first address becomes the default automatically.
- Deleting the default address makes the oldest remaining address the default.
- A customer can only see or change **their own** addresses. Someone else's address id gives a 404.

## Endpoints

| Method | Path | Who (enforced by the gateway) | What |
|---|---|---|---|
| GET / PUT | `/api/v1/customers/me` | CUSTOMER | My profile / update it |
| GET / POST | `/api/v1/customers/me/addresses` | CUSTOMER | My addresses / add one (`setAsDefault` optional) |
| PUT / DELETE | `/api/v1/customers/me/addresses/{id}` | CUSTOMER | Update / delete one of my addresses |
| PATCH | `/api/v1/customers/me/addresses/{id}/default` | CUSTOMER | Make it my default address |
| GET | `/api/v1/admin/customers?keyword=` | ADMIN, SUPER_ADMIN | Search by name, email or phone |
| GET | `/api/v1/admin/customers/{id}` · `/{id}/addresses` | ADMIN, SUPER_ADMIN | One customer / their addresses |
| GET | `/internal/customers/{userId}/addresses/{addressId}` | other services only | An address of a user (order-service copies it into the order) |

## Run it

```powershell
.\scripts\start-infra.ps1                      # from the repo root, once
cd customer-service; .\mvnw spring-boot:run    # Flyway creates the tables + 3 sample customers
```

Then open [http/customer-service.http](http/customer-service.http). To see a profile being created by an event, register a customer through auth-service; see [e2e/phase3-onboarding.http](../e2e/phase3-onboarding.http).

## Test it

```powershell
.\mvnw verify      # Docker must be running (MySQL + Kafka via Testcontainers)
```

| Test | Kind | What it proves |
|---|---|---|
| `CustomerTest` | plain JUnit | Address rules (default, maximum, removing the default) |
| `CustomerServiceImplTest`, `UserEventServiceImplTest` | Mockito | Ownership checks, only CUSTOMERs get a profile, duplicates skipped |
| `CustomerAddressControllerTest` | `@WebMvcTest` | Validation, optional `setAsDefault`, 404 for someone else's address |
| `CustomerFlowIntegrationTest` | `@SpringBootTest` + MySQL + Kafka containers | A real `UserRegistered` event creates the profile; other event types are skipped |

## Configuration

| Property | Env variable | Local default |
|---|---|---|
| `server.port` | `SERVER_PORT` | `8102` |
| `spring.datasource.url` / `username` / `password` | `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:mysql://localhost:3307/customer_db?...` / `customer_user` / `customer_pass` |
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `spring.kafka.properties.schema.registry.url` | `SCHEMA_REGISTRY_URL` | `http://localhost:8081` |
