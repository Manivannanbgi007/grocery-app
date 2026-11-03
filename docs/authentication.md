# Authentication: login, access tokens, refresh tokens

How users log in, how every request is checked, and how a login lasts days without the user typing the password again,
while a stolen token is of little use. A login with Google, Microsoft, Facebook or Apple ends in the same session:
see [social-login.md](social-login.md).

```text
 React app                          API gateway (Spring Security)            auth-service                 MySQL
 ─────────                          ─────────────────────────────            ────────────                 ─────
 POST /auth/login {email,password} ───────────────────────────────────────► BCrypt check
                                   ◄─ { accessToken (15 min), user }  +  Set-Cookie: refresh_token  ───► user_sessions
 GET /api/v1/cart                                                                                      (hash of the token)
   Authorization: Bearer <access>  ─► checks signature, expiry, role ─► cart-service (X-User-* headers)
 ... 15 min later: 401 "expired"
 POST /auth/refresh  (cookie only) ───────────────────────────────────────► rotation: new token
                                   ◄─ { new accessToken }  +  Set-Cookie: NEW refresh_token
 POST /auth/logout   (cookie)      ───────────────────────────────────────► session ended, cookie removed
```

## The two tokens

| | Access token | Refresh token |
|---|---|---|
| What | A signed JWT (HS256): `sub` = user id, `role`, `email`, `name`, `storeId` (vendors), `iss`, `iat`, `exp` | 32 random bytes (`SecureRandom`), 43 URL-safe characters; means nothing by itself |
| Lifetime | **15 minutes** | **7 days**, renewed on every use, but at most **30 days** after the login |
| Sent with | Every API call: `Authorization: Bearer <token>` | Only `POST /api/v1/auth/refresh` and `/logout` (and the password change), as a cookie |
| Checked by | The API gateway, by its signature alone: no database call | auth-service, against the table `user_sessions` |
| In the browser | In memory only (JavaScript variable) | `HttpOnly` cookie: JavaScript cannot read it |
| In the database | Not stored | Only its **SHA-256 hash**: a copy of the database contains no usable token |
| Can be withdrawn | No: it stays valid until it expires (that is why it lives only 15 minutes) | Yes: logout, password change ... end the session at once |

Why two tokens? A token that can be checked without a database call (the JWT) is fast but cannot be withdrawn, so it
must be short-lived. A token that can be withdrawn (the refresh token) needs a database lookup, so it is used only
every 15 minutes. Together: fast checks, and a login that can be ended at any time.

## The refresh cookie

```text
Set-Cookie: refresh_token=<43 characters>; Path=/api/v1/auth; Max-Age=604800; HttpOnly; Secure; SameSite=Strict
```

| Attribute | Why |
|---|---|
| `HttpOnly` | A script injected into the page (XSS) cannot read it, so it cannot steal it |
| `Secure` | Only sent over HTTPS (browsers make an exception for `http://localhost`). Off in the **local profile** only, so curl, PowerShell and the REST Client work on a developer machine (`REFRESH_COOKIE_SECURE`) |
| `SameSite=Strict` | Not sent with a request that another web site started: protects `/refresh` and `/logout` against CSRF |
| `Path=/api/v1/auth` | Sent only to the account endpoints, not with every API call |
| `Max-Age` | Until the session's current token expires |

The refresh token is **never** in the JSON. Login, register and refresh answer with `Cache-Control: no-store`.

## Rotation and reuse detection

Every refresh **replaces** the refresh token ("rotation"). The session row keeps the hash of the token before it, so
an old token that comes back is recognised:

| The cookie holds ... | Result |
|---|---|
| the session's **current** token | New access token + new refresh token (the old one becomes "previous") |
| the **previous** token, replaced **less than 10 s** ago | New access token, no new cookie: a second browser tab refreshed at the same moment (the cookie is shared by all tabs) |
| the **previous** token, replaced **earlier** | **Reuse**: someone else has an old copy (stolen?). The **whole session ends**, a WARN is logged, 401. The thief is out, and so is the real user, who simply logs in again |
| an unknown, expired or ended session's token | 401, the cookie is removed |

Two refreshes with the same token at the same moment are handled one after the other: the session row is locked
(`SELECT ... FOR UPDATE`) during the refresh. Measured in `SessionFlowIntegrationTest`: one rotation, both get an
access token, no error.

## When a session ends

| Event | Sessions ended | `revoked_reason` |
|---|---|---|
| Logout (`POST /auth/logout`) | this one | `LOGOUT` |
| An old refresh token comes back (above) | this one | `REUSE_DETECTED` |
| The user changes the password | all **other** sessions; this browser stays logged in | `PASSWORD_CHANGED` |
| Password reset (forgot password) | all | `PASSWORD_RESET` |
| An admin disables the account | all | `USER_DISABLED` |
| A super admin changes the role | all (log in again with the new rights) | `ROLE_CHANGED` |
| The same browser logs in again | its old session | `SUPERSEDED` |
| 7 days without a refresh, or 30 days after the login | that one (expired, not "revoked") | – |

An access token that was already issued stays valid until it expires: **at most 15 minutes** after a session ended
(plus the gateway's 60 s clock tolerance). That is the accepted trade-off of JWTs. Ended sessions are deleted one day
later by the hourly `HousekeepingJob`.

A refresh builds the new access token from the **current** user row: a changed role or a newly approved store
(`storeId`) is in the next access token, at most 15 minutes later. The vendor panel does not wait for that: when it sees
the store approved but no store id in the token, it asks for a new token at once and opens (`StoreGate`).

## Password-reset tokens

"Forgot password" creates a one-time token (32 hex characters, 122 random bits) that notification-service emails. It is
as powerful as the password for 30 minutes, so it is protected like the refresh token:

```text
auth-service                                    outbox_event, Kafka, .DLT                notification-service
token ─► SHA-256 ─► password_reset_tokens       "v1.<AES-256-GCM text>"  ──────────►     decrypt ─► email (Mailpit locally)
     └─► AES-256-GCM (PASSWORD_RESET_EVENT_KEY) ─► PasswordResetRequested.encryptedResetToken
```

| Rule | How |
|---|---|
| The database keeps only the **hash** | `password_reset_tokens.token_hash` = SHA-256 (`V9` hashed the tokens that existed, so they kept working) |
| The event carries it **encrypted** | The outbox row, the Kafka topic and a dead-letter copy keep events for days, so they only see `encryptedResetToken`: AES-256-GCM with a key that only auth-service and notification-service have, the user id bound to it (it does not decrypt in another user's event), a new random IV every time. The format starts with `v1.`, so a new key or algorithm can come as `v2.` |
| Only the **newest** token works | A new request, a reset and a password change mark every other open token of the user as used |
| Used once, 30 minutes | As before; two resets with the same token at the same moment: one wins (`@Version`), the other gets 409 |
| Gone after a day | `HousekeepingJob` deletes tokens that expired more than a day ago |
| A wrong key | notification-service refuses to start with a key that is not 32 bytes; a key that differs from auth-service's sends the event straight to `grocery.user.events.DLT` (no retries): fix the key, then replay it |

**The schema's versions** (Schema Registry, BACKWARD: each new version reads the older events):

| Version | The token |
|---|---|
| 1 | `resetToken`, in plain text |
| 2 | `encryptedResetToken` added; `resetToken` optional and always empty |
| 3 | `resetToken` removed (2026-10-28) |

An event without `encryptedResetToken` can only come from before the change. Its token stopped working 30 minutes after
it was issued, so notification-service skips it with an INFO line. One that has not expired yet would be a bug and goes
to the dead-letter topic.

The events written before the change still hold their token in plain text in `outbox_event` until `HousekeepingJob`
deletes them (14 days); those tokens no longer work.

The local key is `local-dev-only-change-me-0123456` in Base64; every other environment needs its own:
`openssl rand -base64 32`.

## Where Spring Security runs

| Where | What |
|---|---|
| **api-gateway** (`config/SecurityConfig.java`) | The only place that checks access tokens: Spring Security's OAuth2 resource server (`spring-boot-starter-security-oauth2-resource-server`) verifies the signature (shared `JWT_SECRET`), the issuer and the expiry; the `role` claim becomes the authority `ROLE_<role>`. Path rules: public (register, login, refresh, logout, password reset, the catalog), any logged-in user (`/api/v1/auth/me/**`, notifications), `/api/v1/admin/**` ADMIN or SUPER_ADMIN, `/api/v1/vendor/**` VENDOR, `/api/v1/delivery/**` DELIVERY_PARTNER, other `/api/v1/**` CUSTOMER, everything else (`/internal/**`) denied. Then it forwards `X-User-Id`, `X-User-Role`, `X-Store-Id` |
| **api-gateway**, public account endpoints | An `Authorization` header sent to the six public `POST /api/v1/auth/*` endpoints is **ignored** (`bearerTokenConverter`), so an old or expired access token can never block login, refresh or logout |
| **auth-service** | Passwords with BCrypt (`spring-security-crypto`), tokens with jjwt, sessions in MySQL. Its only filter chains are for social login ([social-login.md](social-login.md)): one runs Spring Security's `oauth2Login` on the two OAuth paths, the other lets everything else through unchanged (the gateway already checked the request) |
| **the other services** | Trust the `X-User-*` headers (they are reachable only through the gateway) and check ownership themselves ("your order", "your store") |

What each part uses (versions from the Spring Boot 4.1 parent unless given):

| Where | Dependency | For |
|---|---|---|
| auth-service | `io.jsonwebtoken:jjwt-api` 0.13.0 (+ `jjwt-impl`, `jjwt-jackson` at runtime) | signing the access token (HS256) |
| auth-service | `org.springframework.security:spring-security-crypto` | `BCryptPasswordEncoder` for passwords |
| auth-service | `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `flyway-mysql`, `mysql-connector-j` | the `user_sessions` table (`V8`) |
| auth-service | `spring-boot-starter-security-oauth2-client` | social login only: the OAuth 2.0 / OpenID Connect client and the ID token check ([social-login.md](social-login.md)) |
| api-gateway | `spring-boot-starter-security-oauth2-resource-server` (tests: `...-resource-server-test`) | checking the access token, the role rules |
| fresh-grocery | none | `fetch` sends the cookie by itself (same origin); `BroadcastChannel` is built into browsers |

The refresh token needs no library: `java.security.SecureRandom` makes it and `MessageDigest` (SHA-256) hashes it.

## Configuration

auth-service `application.yml` (environment variables in brackets):

| Property | Default | |
|---|---|---|
| `app.jwt.secret` (`JWT_SECRET`) | local-only value | At least 32 characters, the same in auth-service and the gateway. A real secret in every other environment |
| `app.jwt.access-token-lifetime` (`JWT_ACCESS_TOKEN_LIFETIME`) | `15m` | Replaces `JWT_EXPIRATION_MINUTES` |
| `app.session.refresh-token-lifetime` (`REFRESH_TOKEN_LIFETIME`) | `7d` | Renewed on every refresh |
| `app.session.max-lifetime` (`SESSION_MAX_LIFETIME`) | `30d` | Log in again after this, however active |
| `app.session.reuse-grace` | `10s` | Two tabs refreshing at once |
| `app.session.cookie-secure` (`REFRESH_COOKIE_SECURE`) | `true` (`false` in the local profile) | |
| `app.housekeeping.session-retention` (`HOUSEKEEPING_SESSION_RETENTION`) | `1d` | Ended sessions are deleted after this |
| `app.housekeeping.reset-token-retention` (`HOUSEKEEPING_RESET_TOKEN_RETENTION`) | `1d` | Expired password-reset tokens are deleted after this |
| `app.password-reset.event-key` (`PASSWORD_RESET_EVENT_KEY`) | local-only key | AES-256 key (32 bytes, Base64) of the reset token in its event. **The same value in notification-service** |

## Endpoints

All through the gateway (`http://localhost:8080`).

| Method | Path | Who | Answer |
|---|---|---|---|
| POST | `/api/v1/auth/register` | anyone | 201, access token + user; refresh cookie |
| POST | `/api/v1/auth/login` | anyone | 200, access token + user; refresh cookie |
| POST | `/api/v1/auth/refresh` | anyone with the cookie | 200, new access token + user; new refresh cookie |
| POST | `/api/v1/auth/logout` | anyone with the cookie | 204, cookie removed |
| GET | `/api/v1/auth/me` | logged in | 200, my account |
| PUT | `/api/v1/auth/me/password` | logged in | 200; every other session ends |
| POST | `/api/v1/auth/forgot-password`, `/reset-password` | anyone | as before; a new token makes the earlier ones useless, and a reset ends every session ([password-reset tokens](#password-reset-tokens)) |
| GET, POST | `/api/v1/auth/social/**`, `/api/v1/auth/oauth2/**`, `/api/v1/auth/login/oauth2/**` | anyone | "Continue with Google / Microsoft / Facebook / Apple": the same access token and refresh cookie as `/login` ([social-login.md](social-login.md)) |

**Login** (real answer, tokens shortened):

```http
POST /api/v1/auth/login
Content-Type: application/json

{ "email": "customer1@grocery.com", "password": "Password@123" }
```

```http
HTTP/1.1 200
Cache-Control: no-store
Set-Cookie: refresh_token=<43 random characters>; Path=/api/v1/auth; Max-Age=604799; Expires=Tue, 03 Nov 2026 20:58:31 GMT; Secure; HttpOnly; SameSite=Strict

{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJ...",
    "tokenType": "Bearer",
    "expiresInSeconds": 900,
    "user": { "id": 4, "fullName": "Priya Sharma", "email": "customer1@grocery.com", "phone": "9000000004",
              "role": "CUSTOMER", "status": "ACTIVE", "storeId": null, "lastLoginAt": "...", "createdAt": "..." }
  },
  "errors": null, "timestamp": "2026-10-27T20:58:31.847Z", "path": null
}
```

**Refresh**: no body, only the cookie. The answer has the same shape (`"message": "Token refreshed"`) and a **new**
`Set-Cookie`. Do not send an `Authorization` header (it would be ignored anyway).

```http
POST /api/v1/auth/refresh
Cookie: refresh_token=<the current token>
```

**Logout**:

```http
POST /api/v1/auth/logout
Cookie: refresh_token=<the current token>

HTTP/1.1 204
Set-Cookie: refresh_token=; Path=/api/v1/auth; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Secure; HttpOnly; SameSite=Strict
```

## Errors

Every error has the usual JSON shape `{ "success": false, "message": "...", "errors": ..., "path": "..." }`.

| Case | Status | Message |
|---|---|---|
| Wrong email or password | 401 | "Invalid email or password" (the same for both: nobody can find out which emails exist) |
| Account disabled | 403 | "Your account is disabled. Please contact support." |
| No access token | 401 | "Please log in to access this resource" (`WWW-Authenticate: Bearer`) |
| Access token **expired** | 401 | "Your login has expired. Please refresh the access token or log in again." (`WWW-Authenticate: Bearer error="invalid_token", error_description="The access token expired"`) |
| Access token invalid (wrong signature, other issuer, damaged) | 401 | "Your login token is invalid. Please log in again." |
| Logged in, but the wrong role | 403 | "You do not have permission to access this resource" |
| Refresh: no, unknown, expired, ended or reused refresh token | 401 | "Your session has ended. Please log in again." + the cookie is removed |
| Refresh or logout started by another web site (`Sec-Fetch-Site: cross-site`) | 403 | "This request must come from the shop's own pages" |
| Too many login attempts | 429 | gateway rate limit (10 per minute per IP) |

## The React app (fresh-grocery)

```text
app starts / reload ─► memory is empty ─► POST /auth/refresh (the browser adds the cookie)
                                              200 ─► logged in again (pages that need a login wait: "Checking your login…")
                                              401 ─► a guest, without a message
every API call ──────► token due for renewal? ─► POST /auth/refresh first (one request for all calls at that moment)
                       answer 401 ───────────► one shared refresh, then the call is sent once more
                                              refresh answers 401 ─► logged out: "Your session has expired"
log out ─────────────► memory cleared at once, the other tabs told ─► POST /auth/logout (keepalive) ─► cookie removed
```

| File | What it does |
|---|---|
| `src/api/session.ts` | The login **in memory**: access token, `expiresAt`, `renewAt`, user. `subscribeSession` lets React follow it (`useSyncExternalStore`); a generation number goes up on every change |
| `src/api/client.ts` | `send()` adds the token, renews it when it is due, and after a 401 refreshes and sends the call once more. `refreshSession()`: never more than one `/refresh` at a time |
| `src/api/auth.ts` | `logout()` (`keepalive`: finishes even when the page is closed right after) |
| `src/features/auth/AuthProvider.tsx` | Restores the login after a reload (`restoring`), the renewal timer, `logOut()`, the tabs (`BroadcastChannel("fresh-grocery-auth")`) |
| `src/features/auth/RestoringLogin.tsx` | "Checking your login…", shown by `RequireCustomer`, `RequireVendor`, `RequirePartner` and `RequireAdmin` while restoring, instead of sending a logged-in user to the login page |
| `src/mocks/mockAuth.ts`, `handlers/auth.ts` | The mock API (`npm run dev:mock`): `/refresh` and `/logout`, mock tokens that expire after 15 minutes, the "cookie" kept in `localStorage` (mock mode only) |

The rules:

- **When to renew:** 3 minutes before the token expires: an assistant answer can take up to 3 minutes, and its tools use
  the customer's token all that time. A token that lives less than 6 minutes (a test setting) is renewed halfway.
- **Only a 401 from `/refresh` ends the login.** A network error, 429 or 5xx on the refresh keeps it; the next call tries
  again. A 401 from any other call only triggers a refresh.
- **The public auth calls** (login, register, refresh, logout, forgot and reset password) never get the access token,
  and their 401 ("Invalid email or password") is an answer, not an expired token.
- **Late answers:** a refresh that comes back after the user logged out (or in) is thrown away. A 401 for a call sent
  with an old token, after another call has renewed it, is sent again with the new token, without another refresh.
- **Logout** clears the login at once and tells the server in the background. Until the server has answered, a flag in
  `localStorage` (`fresh-grocery.logout-pending`) makes a reload send the logout again instead of restoring the login.
  The shop's logout buttons first show the home page (`navigate('/', { flushSync: true })`), then log out: otherwise a
  page that needs a login (My orders) would see the guest first and send it to the login page. `flushSync` works
  only with the DOM `RouterProvider` (`react-router/dom`, in `App.tsx`); the tests keep the plain one.
- **Logout in a panel** (vendor, delivery, admin; also "Log out" on "This panel is for vendors" and the vendor's "Log in
  again") ends on the panel's plain login page, e.g. `/vendor/login`, never `/vendor/login?next=%2Fvendor%2Forders`.
  The panel's login page itself cannot be shown first (it sends a logged-in vendor back into the panel), so
  `useLogOutToLogin()` (`features/auth/loginRedirect.ts`) marks the current page in the router's history state, then
  logs out. The page's guard (`RequireVendor` ...) sees the mark and leaves out `?next=`. A guest who opens a panel page,
  or whose session expired, still gets `?next=` and comes back to that page after logging in.
- **Tabs** share the cookie, so they tell each other:
  - a logout in one tab logs out the others;
  - a login with **another account** logs out the tabs of the old one;
  - a guest tab takes over a new login.
  
  As a safety net, a refresh that brings back another user than the one in memory ends the login too.
- **A login on the wrong panel** (a vendor on the shop's login page) ends the server session it started.

**Deployment:** the app and the API must be served from **one origin**, e.g. `https://shop.example.com` for the app and
`https://shop.example.com/api/...` forwarded to the gateway by the web server, as the Vite proxy does in development.
With the API on another host (`VITE_API_BASE_URL`), the browser would not send the cookie (`fetch` sends cookies to its
own origin only, and `SameSite=Strict`), and `/refresh` refuses `Sec-Fetch-Site: same-site`.

## Project structure

```text
auth-service/src/main/java/com/grocery/auth/
├── controller/AuthController.java            login/register set the cookie; refresh and logout (new)
├── service/AuthService.java, impl/AuthServiceImpl.java   start, refresh, end sessions
├── service/SessionService.java, impl/SessionServiceImpl.java   rotation, reuse detection, revocation (new)
├── service/AuthTokens.java                    JSON answer + refresh token for the cookie (new)
├── service/impl/UserAdminServiceImpl.java     disable / role change end the sessions
├── service/impl/JwtServiceImpl.java           access token lifetime as a Duration, time from the Clock
├── entity/UserSession.java, enums/SessionEndReason.java    (new)
├── repository/UserSessionRepository.java      locked lookups, revoke all, delete ended (new)
├── security/RefreshTokens.java, Sha256.java   random token + SHA-256 (new)
├── security/ResetTokenCipher.java             the reset token encrypted for its event (new)
├── repository/PasswordResetTokenRepository.java   by hash, end the other tokens, delete expired
├── security/RefreshTokenCookies.java          the HttpOnly cookie (new)
├── exception/SessionEndedException.java       401 + cookie removed (new); GlobalExceptionHandler
├── config/SessionProperties.java, ClockConfig.java   (new); JwtProperties
└── scheduling/HousekeepingJob.java            deletes ended sessions and expired reset tokens
auth-service/src/main/resources/db/migration/V8__create_user_sessions_table.sql   (new)
auth-service/src/main/resources/db/migration/V9__hash_password_reset_tokens.sql  (new)
auth-service/src/main/avro/produced/PasswordResetRequested.avsc               + encryptedResetToken (copy in notification-service)
notification-service/src/main/java/com/grocery/notification/security/ResetTokenCipher.java   decrypts it (new)
api-gateway/src/main/java/com/grocery/gateway/config/SecurityConfig.java          refresh/logout public, stale tokens ignored
api-gateway/src/main/java/com/grocery/gateway/security/JsonAuthenticationEntryPoint.java   expired vs invalid
fresh-grocery/src/api/session.ts, client.ts, auth.ts                              see "The React app"
fresh-grocery/src/features/auth/AuthProvider.tsx, RestoringLogin.tsx (new), Require*.tsx
fresh-grocery/e2e/session.spec.ts                                                 (new)
```

## Test it

```powershell
# auth-service and api-gateway: unit, web and integration tests (Docker must be running)
cd auth-service; .\mvnw verify      # SessionFlowIntegrationTest: the whole session life cycle on a real MySQL
cd api-gateway; .\mvnw verify       # refresh with an expired token still reaches auth-service; cookies pass through
cd fresh-grocery; npm test          # client.test.ts and auth.test.tsx: the React side, with the mock API
npx playwright test e2e/session.spec.ts   # in fresh-grocery: the real browser against the real backend
```

By hand, through the gateway, with a cookie jar (curl keeps the cookie between calls):

```bash
curl -c cookies.txt -H "Content-Type: application/json" \
     -d '{"email":"customer1@grocery.com","password":"Password@123"}' http://localhost:8080/api/v1/auth/login
curl -b cookies.txt -c cookies.txt -X POST http://localhost:8080/api/v1/auth/refresh    # 200, new cookie
curl -b cookies.txt -c cookies.txt -X POST http://localhost:8080/api/v1/auth/logout     # 204
curl -b cookies.txt -c cookies.txt -X POST http://localhost:8080/api/v1/auth/refresh    # 401: the session has ended
```

Or `auth-service/http/auth-service.http` requests 4, 4a, 4b, 4c (the REST Client keeps cookies too).

What the tests check (`SessionFlowIntegrationTest`, `AuthControllerTest`, `GatewayRoutingAndSecurityTest`,
`HousekeepingJobTest`):

- **The cookie:** login and register set it with all its attributes; the token is never in the JSON or in the log; `no-store`.
- **Rotation:** refresh rotates the token. The old token within 10 s gets an access token only; after 10 s it is reuse, so the session ends and the newest token dies too.
- **Concurrency:** two parallel refreshes with one cookie rotate once.
- **Ending sessions:**
  - logout;
  - expiry after 7 days unused, and after 30 days at most;
  - a disabled account and a role change end every session;
  - a password change ends the others;
  - logging in again replaces the old session.
- **Housekeeping** deletes only sessions that ended, and reset tokens that expired, more than a day ago.
- **Password-reset tokens** (`AuthFlowIntegrationTest`, `AuthServiceImplTest`, both `ResetTokenCipherTest`s, `EventNotificationServiceImplTest`):
  - only the hash is in the database;
  - the outbox event holds no plain token, and the encrypted one decrypts to it with the key and the user id;
  - another user id, a changed text or another key is refused, and notification-service then sends nothing;
  - notification-service decrypts a value that auth-service's code encrypted (a fixed test value);
  - a newer token, a reset and a password change make the open tokens useless.
- **Refusals:** cross-site refresh and logout are refused.
- **Gateway:** an expired Bearer header on `/refresh` is ignored and the request reaches auth-service; `Cookie` and `Set-Cookie` pass the gateway unchanged; expired and invalid tokens get different answers; logout is open to every role.
- **React (`client.test.ts`, `auth.test.tsx`):**
  - 15 calls refused at once share one refresh, and each is sent again;
  - a late 401 uses the new token without a second refresh;
  - a 503 on the refresh keeps the login, and a 401 ends it;
  - a refresh is thrown away after a logout, and another user ends the login;
  - a token due for renewal is renewed first, by a call or by the timer;
  - FormData uploads and spoken answers are sent again;
  - login and the other public calls never get the token;
  - the login comes back after a reload without a detour to the login page, and a guest goes to the login page without a message;
  - logout tells the server, and an unfinished logout is finished after a reload;
  - the three tab messages.
- **Browser (`e2e/session.spec.ts`, real backend):**
  - the cookie's attributes, unreadable by the page, and no token in `localStorage`;
  - a reload keeps the login, and after a logout it does not;
  - a 401 recovers through the real `/refresh`;
  - logout in one tab logs out the other.

## Security notes

- **HTTPS in production.** Tokens and passwords are only protected in transit by TLS; the cookie's `Secure` flag makes the browser refuse to send it otherwise.
- **The access token lives in memory in the browser,** not in `localStorage`, so an injected script cannot copy it for later. A page reload gets a new one from the refresh cookie.
- **CSRF:**
  - `SameSite=Strict` keeps the cookie away from requests started by other sites.
  - `/refresh` and `/logout` also refuse `Sec-Fetch-Site: cross-site` and `same-site`.
  - The other endpoints do not use cookies at all; they need the `Authorization` header, which another site cannot add.
- **Logging:** refresh tokens are never logged. The logs show session ids and user ids only.
- **Secrets:** `JWT_SECRET` and `PASSWORD_RESET_EVENT_KEY` must be random values from a secret store in every environment but local (`deployment/kubernetes/base/secret.yaml` has the local placeholders).
- **Password-reset tokens** are hashed in the database and encrypted in their event (see above); they appear in plain text only in the email.
- **Not covered (yet):** ending access tokens before they expire would need a deny-list of token ids (e.g. in Redis), checked by the gateway on every request. 15 minutes is the accepted window.
