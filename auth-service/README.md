# auth-service

Owns **users, roles, credentials, JWT login and login sessions (refresh tokens)** for the grocery platform
([docs/authentication.md](../docs/authentication.md)).
It is also the **reference service**: every other microservice copies its structure and its standard classes.

| | |
|---|---|
| Port | `8101` |
| Database | `auth_db` (MySQL, user `auth_user`) |
| Swagger UI | http://localhost:8101/swagger-ui.html |
| Health | http://localhost:8101/actuator/health |
| Publishes events | `UserRegistered`, `PasswordResetRequested`, `SocialLoginLinked` on `grocery.user.events` (outbox + Debezium, see `debezium/`) |
| Consumes events | `StoreApproved` from `grocery.store.events` → links the store to its vendor (next JWT contains `storeId`) |

## Endpoints

| Method | Path | Who (enforced by the gateway) | What |
|---|---|---|---|
| POST | `/api/v1/auth/register` | public | Register as CUSTOMER, VENDOR or DELIVERY_PARTNER: access token (JSON) + refresh cookie |
| POST | `/api/v1/auth/login` | public | Log in: access token (15 min) in the JSON, refresh token in an HttpOnly cookie |
| POST | `/api/v1/auth/refresh` | public (refresh cookie) | A new access token and a new refresh cookie (rotation) |
| POST | `/api/v1/auth/logout` | public (refresh cookie) | Ends the session, removes the cookie (204) |
| POST | `/api/v1/auth/forgot-password` | public | Create a one-time reset token (only its hash is stored; earlier tokens stop working; emailed encrypted in the event) |
| POST | `/api/v1/auth/reset-password` | public | Set a new password with the token |
| GET | `/api/v1/auth/me` | any logged-in user | My account |
| PUT | `/api/v1/auth/me/password` | any logged-in user | Change my password (every other session ends) |
| GET | `/api/v1/auth/social/providers` | public | The social login providers configured here (`[{id, name}]`); none without credentials |
| GET | `/api/v1/auth/oauth2/authorization/{provider}` | public | "Continue with Google": Spring Security redirects to the provider |
| GET, POST | `/api/v1/auth/login/oauth2/code/{provider}` | public | The provider's answer; redirects to the shop's `/login/social#code=...` |
| POST | `/api/v1/auth/social/session`, `/social/signup`, `/social/link` | public (`social_flow` cookie) | Exchange the one-time code: logged in, or finish the sign-up, or connect an existing account with its password ([docs/social-login.md](../docs/social-login.md)) |
| GET | `/api/v1/admin/users` | ADMIN, SUPER_ADMIN | Search users (role, status, keyword, paging) |
| GET | `/api/v1/admin/users/{id}` | ADMIN, SUPER_ADMIN | One user |
| PATCH | `/api/v1/admin/users/{id}/status` | ADMIN, SUPER_ADMIN | Enable or disable an account |
| POST | `/api/v1/super-admin/admins` | SUPER_ADMIN | Create an admin |
| PATCH | `/api/v1/super-admin/users/{id}/role` | SUPER_ADMIN | Change a user's role |

**JWT:** HS256, signed with `app.jwt.secret`; the API gateway verifies tokens with the same secret.
Claims: `sub` (user id), `email`, `name`, `role`, and `storeId` (vendors only).

## Run it

1. Start the infrastructure once: `.\scripts\start-infra.ps1` (from the repo root).
2. Start the service. Either press **Run** on `AuthServiceApplication` in your IDE, or:
   ```powershell
   .\mvnw spring-boot:run
   ```
   The `local` profile is active by default. Flyway creates the tables and loads the seed users from `db/seed`.
3. Open [http/auth-service.http](http/auth-service.http) and send the requests. Every seed user's password is `Password@123`.

## Test it

```powershell
.\mvnw verify      # unit + web + repository + end-to-end tests (Docker must be running for Testcontainers)
```

| Test | Kind | What it proves |
|---|---|---|
| `PasswordResetTokenTest` | plain JUnit | Entity rules (expiry, one-time use) |
| `AuthServiceImplTest`, `UserAdminServiceImplTest` | Mockito | Business rules in the services |
| `JwtServiceImplTest` | plain JUnit | Token claims and signature |
| `AuthControllerTest` | `@WebMvcTest` | Status codes, validation errors, `ApiResponse` shape, filters; the refresh token only in `Set-Cookie`, `no-store`, a dead session clears the cookie, cross-site refresh/logout refused |
| `SessionFlowIntegrationTest` | `@SpringBootTest` + MySQL container, a controllable `Clock` | Cookie attributes; rotation; the 10-s grace; reuse ends the session; two parallel refreshes rotate once; logout; expiry after 7 and 30 days; disable, role change, password change and re-login end sessions; tokens never in the log |
| `UserRepositoryTest` | `@DataJpaTest` + MySQL container | Queries, Flyway migrations, entity ↔ schema match |
| `AuthFlowIntegrationTest` | `@SpringBootTest` + MySQL + Kafka containers | Register → login → reset password; `UserRegistered` saved in the outbox; the reset token only hashed in the database and encrypted in the outbox event; a newer token makes the first one useless |
| `ResetTokenCipherTest` | plain JUnit | The reset token in the event: decrypts with the key and the user id only, a new IV every time, a key that is not 32 bytes stops the start |
| `StoreEventServiceImplTest` | Mockito | Vendor gets the store; duplicates and non-vendors are skipped |
| `KnownEventsAvroDeserializerTest` | plain JUnit, in-memory Schema Registry | Known event types are decoded; unknown ones become `IgnoredEvent` |
| `StoreEventsConsumerIntegrationTest` | `@SpringBootTest` + MySQL + Kafka containers | Real Kafka: `StoreApproved` handled once, unknown types skipped, unreadable messages go to `.DLT` |

## Configuration

| Property | Env variable | Local default |
|---|---|---|
| `server.port` | `SERVER_PORT` | `8101` |
| `spring.datasource.url` | `DB_URL` | `jdbc:mysql://localhost:3307/auth_db?...` |
| `spring.datasource.username` / `password` | `DB_USERNAME` / `DB_PASSWORD` | `auth_user` / `auth_pass` |
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `spring.kafka.properties.schema.registry.url` | `SCHEMA_REGISTRY_URL` | `http://localhost:8081` |
| `app.jwt.secret` | `JWT_SECRET` | local-only value (at least 32 characters) |
| `app.jwt.access-token-lifetime` | `JWT_ACCESS_TOKEN_LIFETIME` | `15m` (replaces `JWT_EXPIRATION_MINUTES`) |
| `app.session.refresh-token-lifetime` | `REFRESH_TOKEN_LIFETIME` | `7d` (renewed on every refresh) |
| `app.session.max-lifetime` | `SESSION_MAX_LIFETIME` | `30d` (log in again after this) |
| `app.session.reuse-grace` | – | `10s` (two tabs refreshing at once) |
| `app.session.cookie-secure` | `REFRESH_COOKIE_SECURE` | `true` |
| `app.housekeeping.session-retention` | `HOUSEKEEPING_SESSION_RETENTION` | `1d` (ended sessions are deleted after this) |
| `app.housekeeping.reset-token-retention` | `HOUSEKEEPING_RESET_TOKEN_RETENTION` | `1d` (expired reset tokens are deleted after this) |
| `app.password-reset.event-key` | `PASSWORD_RESET_EVENT_KEY` | local-only key: AES-256, 32 bytes in Base64, **the same in notification-service** ([password-reset tokens](../docs/authentication.md#password-reset-tokens)) |
| `app.password-reset.expose-token-in-response` | – | `true` in `local` only (dev convenience) |
| `app.social.enabled` | `SOCIAL_LOGIN_ENABLED` | `false`: social login off (no buttons, its endpoints 404); `true`: every provider with credentials is offered ([switching it on](../docs/social-login.md#switching-it-on)) |
| `app.social.providers.<provider>.enabled` | `GOOGLE_LOGIN_ENABLED`, `MICROSOFT_LOGIN_ENABLED`, `FACEBOOK_LOGIN_ENABLED`, `APPLE_LOGIN_ENABLED` | `true`; `false` switches one provider off and keeps its credentials |
| `app.social.public-url` | `APP_PUBLIC_URL` | `http://localhost:5173`: the shop's address; every provider's redirect address is built from it |
| `app.social.providers.google.client-id` / `client-secret` | `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | empty: no Google button ([provider setup](../docs/social-login.md#set-up-a-provider)) |
| `app.social.providers.microsoft.client-id` / `client-secret` / `tenant` | `MICROSOFT_CLIENT_ID` / `MICROSOFT_CLIENT_SECRET` / `MICROSOFT_TENANT` | empty: no Microsoft button; tenant `common` |
| `app.social.providers.facebook.client-id` / `client-secret` / `graph-version` | `FACEBOOK_CLIENT_ID` / `FACEBOOK_CLIENT_SECRET` / `FACEBOOK_GRAPH_VERSION` | empty: no Facebook button; `v23.0` |
| `app.social.providers.apple.client-id` / `team-id` / `key-id` / `private-key` / `pkce` | `APPLE_CLIENT_ID` / `APPLE_TEAM_ID` / `APPLE_KEY_ID` / `APPLE_PRIVATE_KEY` / `APPLE_PKCE` | empty: no Apple button (with a client id, the other three are required); PKCE `true` |
| `app.social.providers.test.client-id` | `SOCIAL_TEST_CLIENT_ID` | `local` profile only, empty: set `grocery-local` (with `SOCIAL_LOGIN_ENABLED=true`) for the fake provider ([try it locally](../docs/social-login.md#try-it-locally-with-the-fake-provider)) |
| `app.housekeeping.social-login-retention` | `HOUSEKEEPING_SOCIAL_LOGIN_RETENTION` | `1d` (finished social login attempts are deleted after this) |

## Build the container image

```powershell
docker build -t grocery/auth-service:0.0.1 .
```

## Package layout

```text
com.grocery.auth
├── config/       Swagger, JPA auditing, password encoder, KafkaConsumerConfig (retries + DLT), typed properties
├── constant/     header names, JWT claim names, Kafka topics, validation patterns
├── context/      UserContext + UserContextHolder (who is calling)
├── controller/   AuthController, AdminUserController, SuperAdminController
├── dto/          common/ (ApiResponse, PageResponse, ErrorDetail), request/, response/
├── entity/       BaseEntity, User, UserSession, PasswordResetToken, OutboxEvent, ProcessedEvent
├── enums/        Role, UserStatus, SessionEndReason
├── exception/    GlobalExceptionHandler + one exception per HTTP error type
├── filter/       CorrelationIdFilter, UserContextFilter
├── mapper/       UserMapper (entity <-> DTO), UserEventMapper (entity -> Avro event)
├── messaging/    OutboxEventPublisher, AvroEventSerializer
│   └── consumer/ StoreEventsConsumer, KnownEventsAvroDeserializer, IgnoredEvent
├── repository/   Spring Data JPA repositories
├── security/     RefreshTokens (random token + SHA-256), RefreshTokenCookies (the HttpOnly cookie)
├── service/      interfaces + impl/ (business logic, @Transactional); SessionService = login sessions
└── validator/    UserValidator (checks that need the database)

src/main/avro/produced/   schemas this service owns (UserRegistered, PasswordResetRequested)
src/main/avro/consumed/   copies of schemas it reads (StoreApproved)
debezium/                 Debezium connector that publishes the outbox table
```
