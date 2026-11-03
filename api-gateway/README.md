# api-gateway

The **single entry point** for every client (React app, mobile app, Postman). Clients only ever call `http://localhost:8080`.
It is also the **one place for traffic control and resilience**: rate limiting, throttling, circuit breakers, timeouts,
retries and fallback answers are configured here, for every service, so no service needs its own copy.

| | |
|---|---|
| Port | `8080` |
| Technology | Spring Cloud Gateway 5 (WebFlux, non-blocking), Spring Security (JWT resource server), Resilience4j, Redis |
| Central Swagger UI | http://localhost:8080/swagger-ui.html (pick a service in the top-right dropdown) |
| Routes | http://localhost:8080/actuator/gateway/routes |
| Health | http://localhost:8080/actuator/health (includes Redis) |
| Needs | Redis (rate-limit counters, shared by all gateway instances), plus the services it routes to |

## What happens to a request

```text
Client ──► CorrelationIdFilter     keeps or creates X-Correlation-Id
       ──► RequestLoggingFilter    one log line per request: GET /api/v1/auth/me -> 200 OK (12 ms)
       ──► Spring Security         JWT valid? (signature, expiry, issuer) -> else 401
                                   role allowed for this path? (SecurityConfig) -> else 403
       ──► UserHeadersFilter       removes client X-User-* headers, adds X-User-Id / X-User-Role / X-Store-Id from the JWT
       ──► RateLimitFilter         1. RATE LIMIT: requests of this client (user / IP) in Redis -> else 429
       ──► CircuitBreaker          2. CIRCUIT BREAKER of the route: open -> 503 at once, the service is not called
                                   3. THROTTLING (bulkhead): too many requests in progress for the route -> 503 busy
       ──► Retry                   4. RETRY: GET/HEAD only, after a refused connection or 502/503, 2x (100 ms, 200 ms)
       ──► route (application.yml) 5. TIMEOUTS: connect 2 s, answer 10 s (chat 180 s) -> else 504
       ◄── GlobalErrorHandler      6. FALLBACK: every failure above as a clear ApiResponse (+ Retry-After),
                                      or an empty list for decoration (banners)
```

The services behind the gateway never read JWTs. They trust the `X-User-*` headers.

## Resilience and traffic control

| | Where | What it protects | Answer |
|---|---|---|---|
| **Rate limiting** | `ratelimit/RateLimitFilter`, `app.rate-limit` | Fair use and abuse: how many requests ONE client may send. Counted in Redis, so all gateway instances share the counters | `429` + `Retry-After` |
| **Throttling** | Resilience4j bulkhead, `resilience4j.bulkhead` | A slow service from a pile-up: at most 100 requests in progress per route and gateway instance (chat: 20) | `503` "busy" + `Retry-After: 1` |
| **Circuit breaker** | `CircuitBreaker` default filter, `resilience4j.circuitbreaker` | A broken service from more calls, and clients from waiting for it: one breaker per route | `503` + `Retry-After` (while open) |
| **Timeouts** | `spring.cloud.gateway...httpclient`, route `metadata.response-timeout` | Clients and gateway threads from hanging calls | `504` |
| **Retry** | `Retry` default filter | Short glitches (a pod restarting, a dropped connection) | – (the client never notices) |
| **Fallback** | `exception/GlobalErrorHandler`, `app.fallback` | Clients from confusing errors; pages from failing because of decoration | consistent JSON, or `200` with an empty list |

### The limits

| Policy (`app.rate-limit`) | Applies to | Limit (production) | Local (`application-local.yml`) |
|---|---|---|---|
| `login` | `POST /api/v1/auth/login`, `register`, `forgot-password`, `reset-password`, `social/session`, `social/signup`, `social/link` | 10 per minute, 10 in a row, per IP | 120 per minute, 60 in a row (website tests log in often) |
| `social-login` | `/api/v1/auth/oauth2/authorization/*` (the social login button), `/api/v1/auth/login/oauth2/code/*` (the provider's answer) | 20 per minute, 10 in a row, per IP | 120 per minute, 60 in a row |
| `chat` | `POST /api/v1/chat/messages` | 10 per minute, 10 in a row, per user / IP | same |
| `voice` | `POST /api/v1/chat/transcriptions`, `/api/v1/chat/speech` | 20 per minute, 10 in a row, per user / IP (both endpoints together) | same |
| `default` | every other request | 20 per second, 40 in a row, per user / IP | same |

A client is the logged-in user (`user:4`) or, for guests, the IP address (`ip:203.0.113.7`). Behind a proxy such as a
Kubernetes Ingress, set `RATE_LIMIT_TRUSTED_PROXIES=1`: the address then comes from the `X-Forwarded-For` entry the proxy
added (entries further left can be faked by the client, so they are never used). Every answer carries
`X-RateLimit-Policy` and `X-RateLimit-Remaining`.

How it counts: Spring Cloud Gateway's `RedisRateLimiter`, a **token bucket** in Redis (one Lua script, so it is exact
with any number of gateway instances). Each client has a bucket per policy that refills at the policy's rate and holds
at most "burst" requests. **If Redis is down, requests are let through** ("fail open": the shop keeps working without
limits) and every such request logs an error, so the problem is visible. The buckets can be seen in Redis Insight
(http://localhost:5540, keys `request_rate_limiter.{<policy>.<client>}.tokens`, see
[docs/redis-insight.md](../docs/redis-insight.md)).

### How the parts work together without getting in each other's way

- **One timeout.** Resilience4j's time limiter (1 s by default!) is switched off
  (`spring.cloud.circuitbreaker.resilience4j.disable-time-limiter`): only the HTTP client's timeouts apply, so the 180 s
  chat route is never cut off by a second, shorter timeout.
- **Retry inside the circuit breaker** (order of `default-filters`): one client request counts once for the breaker,
  however often it was retried. While the circuit is open, nothing is retried.
- **Retries only where they are safe:** only GET and HEAD (a repeated POST could place an order or a payment twice),
  only after a refused/reset connection or a 502/503, never after a timeout (that would multiply the wait), never after a
  500 (a bug does not go away). At most 2 retries, 100 ms and 200 ms apart.
- **What counts as a failure for the circuit breaker:** calls that got no answer (connection errors, timeouts). Not 4xx,
  and not a service's own error answer: when cart-service answers 503 with a message, the client gets exactly that
  message. A full bulkhead does not count either (it is our throttling, not the service's fault).
- **Rate limiting comes first**, so rejected requests cost nothing downstream and never reach a circuit breaker.
- **Per instance or shared:** rate limits are shared (Redis). Bulkheads and circuit breakers are per gateway instance
  (with 3 pods: up to 3 x 100 requests in progress per route); each instance notices a broken service by itself within
  a few requests.

### Fallback answers (`exception/GlobalErrorHandler`)

| What happened | Status | `Retry-After` | Message |
|---|---|---|---|
| Rate limit reached | 429 | until the next request is allowed (1 s; 6 s for 10/min) | "Too many requests. Please wait 6 seconds and try again." |
| Circuit breaker open | 503 | the open time of the breaker (15 s) | "The service is temporarily unavailable. Please try again in a moment." |
| Service not reachable (after retries) | 503 | 15 s | same |
| Too many requests in progress (bulkhead) | 503 | 1 s | "The service is busy right now. Please try again in a moment." |
| Service too slow | 504 | – | "The service took too long to respond. Please try again." |
| `GET /api/v1/banners` and the service is down / slow / busy | **200** | – | `data: []`, "Shown without this content: ...", header `X-Fallback: empty-list` |

No answer contains an exception text, a class name or an internal service name. Graceful degradation is only used for
**decoration** (`app.fallback.empty-list-paths`): the home page shows its own artwork instead of banners. Prices, stock,
carts, orders and payments are never replaced by substitute data: there an honest 503 is better than a wrong answer.

### Logs and metrics

| Event | Log | Metric (`/actuator/prometheus`) |
|---|---|---|
| Rate limit reached | INFO `Rate limit 'login' reached by ip:... on POST /api/v1/auth/login` | `gateway_rate_limited_total{policy}` |
| Circuit breaker opens / half-opens / closes | WARN `Circuit breaker 'cart-service' is OPEN for 15 s ...` / INFO `... HALF_OPEN` / INFO `... CLOSED again` | `resilience4j_circuitbreaker_state{name,state}` |
| Answer while open, busy, unreachable, timeout, degraded | DEBUG / INFO / WARN / WARN / WARN | `gateway_fallback_total{reason,route}` |
| Bulkhead usage | – | `resilience4j_bulkhead_available_concurrent_calls{name}` |

Grafana: dashboard "Grocery services", panels "Gateway: circuit breakers" and "Gateway: requests answered by the gateway".

## Access rules (`config/SecurityConfig.java`)

| Path | Who |
|---|---|
| `POST /api/v1/auth/register`, `login`, `refresh`, `logout`, `forgot-password`, `reset-password` | everyone; an `Authorization` header sent to these is **ignored**, so an old or expired access token never blocks a login or a refresh ([authentication.md](../docs/authentication.md)) |
| `POST /api/v1/auth/social/session`, `signup`, `link`; `GET /api/v1/auth/social/providers`, `/api/v1/auth/oauth2/authorization/*`; any method on `/api/v1/auth/login/oauth2/code/*` | everyone (social login, [social-login.md](../docs/social-login.md)); the three POSTs ignore an `Authorization` header like the login. The provider's answer has **no CORS check**: Apple posts it from its own site |
| `POST /api/v1/payments/webhooks/**` | everyone (Razorpay; payment-service checks the signature) |
| `GET /api/v1/products/**` (incl. reviews), `/categories/**`, `/stores/**`, `/coupons/**`, `/platform/**`, `/banners/**` | everyone (browsing, settings, banners) |
| `/api/v1/chat/**` (AI shopping assistant) | everyone; a token, if sent, is still checked and passed on |
| `/api/v1/auth/me/**`, `/api/v1/notifications/**`, `GET /delivery-media/**` (proof photos) | any logged-in user |
| `/api/v1/super-admin/**` | SUPER_ADMIN |
| `/api/v1/admin/**` | ADMIN, SUPER_ADMIN |
| `/api/v1/vendor/**` | VENDOR |
| `/api/v1/delivery/**` | DELIVERY_PARTNER |
| any other `/api/v1/**` | CUSTOMER |
| `/internal/**` and everything else | nobody (service-to-service only) |

## Error responses

The gateway's own errors use the same `ApiResponse` JSON as every service:

| Status | When |
|---|---|
| 401 | no token, or the token is invalid or expired. Expired: "Your login has expired ..." and `WWW-Authenticate: Bearer error="invalid_token", error_description="The access token expired"` (the website then refreshes); invalid: "... invalid" |
| 403 | the user's role may not call this path |
| 404 | no route matches the path |
| 429 | rate limit reached (`Retry-After`) |
| 503 | the service is not reachable, its circuit breaker is open, or it is busy (`Retry-After`) |
| 504 | the service did not answer in time (10 s; chat 180 s, voice 60 s) |

Errors returned BY a service (its own 400, 404, 409, 503 ...) are passed through unchanged.

## Routes (`application.yml`)

| Path | Service |
|---|---|
| `/api/v1/auth/**`, `/api/v1/admin/users/**`, `/api/v1/super-admin/admins/**`, `/api/v1/super-admin/users/**` | auth-service (8101) |
| `/api/v1/customers/**`, `/api/v1/admin/customers/**` | customer-service (8102) |
| `/api/v1/stores/**`, `/api/v1/vendor/store/**`, `/api/v1/admin/stores/**`, `/api/v1/super-admin/stores/**` | store-service (8103) |
| **GET** `/api/v1/products/**`, `/api/v1/categories/**`, `/api/v1/vendor/products/**`, `/api/v1/admin/products/**`, `/api/v1/admin/categories/**` | product-read-service (8105), Elasticsearch |
| **POST/PUT/PATCH/DELETE** `/api/v1/vendor/products/**`, `/api/v1/admin/products/**`, `/api/v1/admin/categories/**` | product-service (8104), MySQL |
| **GET** `/media/**` (public product images) | product-service (8104) |
| `/api/v1/vendor/inventory/**`, `/api/v1/admin/inventory/**` | inventory-service (8106) |
| `/api/v1/cart/**`, `/api/v1/coupons/**` (GET public), `/api/v1/vendor/coupons/**`, `/api/v1/admin/coupons/**` | cart-service (8107) |
| **GET** `/api/v1/vendor/orders/stream` (the vendor panel's live orders, Server-Sent Events, [order-service README](../order-service/README.md#live-order-updates-server-sent-events)) | order-service (8108): like the tracking stream below, **no response timeout**, its own circuit breaker settings and bulkhead (`GATEWAY_VENDOR_ORDER_MAX_STREAMS`, 2000) |
| `/api/v1/orders/**`, `/api/v1/vendor/orders/**`, `/api/v1/admin/orders/**` | order-service (8108) |
| `/api/v1/payments/**`, `/api/v1/admin/payments/**` | payment-service (8109) |
| **GET** `/api/v1/delivery-tracking/orders/*/stream` (live tracking, Server-Sent Events, [docs](../docs/delivery-tracking.md)) | delivery-service (8110): **no response timeout** (a stream stays open for minutes; the service sends a keep-alive every 15 s), its own circuit breaker settings (not "slow") and bulkhead (`GATEWAY_TRACKING_MAX_STREAMS`, 2000) |
| `/api/v1/delivery/**`, `/api/v1/delivery-tracking/**`, `/api/v1/admin/delivery-partners/**`, `/api/v1/admin/delivery-zones/**`, `/api/v1/admin/deliveries/**` | delivery-service (8110) |
| **GET** `/delivery-media/**` (proof-of-delivery photos, logged-in users) | delivery-service (8110) |
| `/api/v1/notifications/**` (any logged-in user), `/api/v1/admin/notifications/**` | notification-service (8111) |
| `/api/v1/products/*/reviews` (all methods, listed before product-read), `/api/v1/vendor/reviews/**`, `/api/v1/admin/reviews/**` | product-service (8104) |
| `/api/v1/delivery-ratings/**` | delivery-service (8110) |
| `/api/v1/admin/reports/**`, `/api/v1/vendor/reports/**`, `/api/v1/super-admin/reports/**` | report-service (8112) |
| `/api/v1/platform/**`, `/api/v1/banners/**`, `/api/v1/admin/banners/**`, `/api/v1/super-admin/settings/**`, `/api/v1/super-admin/feature-flags/**`, `/api/v1/super-admin/audit-log/**` | platform-service (8113) |
| `POST /api/v1/chat/messages` | mcp-client-service (8114): rate-limit policy `chat`, bulkhead 20, 180 s timeout |
| `POST /api/v1/chat/transcriptions`, `/api/v1/chat/speech` (voice) | mcp-client-service (8114): rate-limit policy `voice`, bulkhead 10, 60 s timeout |
| `/api/v1/chat/**` (conversations) | mcp-client-service (8114) |
| `/<service>/v3/api-docs` | each service's OpenAPI document, for the central Swagger UI |

mcp-server-service (the assistant's MCP tools) is deliberately **not** routed: only mcp-client-service may call it.

## Adding a new service

1. Add two routes in `application.yml`: the API paths, plus `/<service>/v3/api-docs` for Swagger. Copy the auth-service
   routes. The new routes get the circuit breaker, the bulkhead, the retry and the timeouts automatically
   (`default-filters`); a slow route can set `metadata: response-timeout: <ms>`.
2. Add the service to `springdoc.swagger-ui.urls`.
3. Only if the service introduces a new path prefix, add an access rule to `SecurityConfig`.
4. Only if it needs other limits: a rate-limit policy in `app.rate-limit.policies`, or a
   `resilience4j.bulkhead.instances.<route id>` / `resilience4j.circuitbreaker.instances.<route id>` entry.

## Run it

```powershell
.\scripts\start-infra.ps1            # from the repo root (Redis must be running)
cd auth-service; .\mvnw spring-boot:run     # in one terminal
cd api-gateway;  .\mvnw spring-boot:run     # in another terminal
```

Then open [http/api-gateway.http](http/api-gateway.http) (VS Code REST Client) and run requests 1–4 first. They log in and save the tokens.

## Test it

```powershell
.\mvnw verify     # Docker must be running (Redis via Testcontainers)
```

| Test | What it proves |
|---|---|
| `GatewayRoutingAndSecurityTest` | 34 checks: routing of every service, 401/403 rules, user headers from the JWT, header spoofing blocked, correlation id, CORS, 404 shape, Swagger route, health |
| `RateLimitTest` | the default limit gives 429 in the standard shape with `Retry-After`; a logged-in user has a limit of their own; the login policy is per minute |
| `ChatRateLimitTest` | the chat policy limits questions, but not reading conversations; voice has its own limit for both voice endpoints |
| `ResilienceTest` | a GET is retried after two 503s; a POST and a 500 are never retried; a slow service gives 504 without a retry; the circuit opens after repeated failures, answers 503 at once with `Retry-After`, and closes again when the service is back; a second request in progress gets 503 busy; banners degrade to an empty list, settings do not |
| `ServiceUnavailableTest` | a stopped service gives a 503 in the standard shape with `Retry-After` |

The tests run the real gateway in front of `FakeDownstreamService` (echoes what it received) or
`ControllableDownstreamService` (a test sets it to fail, fail a few times, or answer slowly, and reads how many requests
it got). `TestTokens` signs JWTs with the same library and claims as auth-service.

### Try it by hand

`curl.exe` is part of Windows 10/11 and works in every PowerShell version (`-i` shows the headers, `-w` prints the
status and the time).

```powershell
# 1. RATE LIMITING: 120 requests at the same time (-Z), count the answers
curl.exe -s -Z --parallel-max 120 -o NUL -w "%{http_code}`n" "http://localhost:8080/api/v1/categories?n=[1-120]" |
    Group-Object -NoElement
#    -> 40 to 60 x 200 (40 saved up, plus 20 per second refilled while they run), the rest 429. Right after it, one 429 in full:
curl.exe -s -i http://localhost:8080/api/v1/categories      # 429, Retry-After: 1, X-RateLimit-Policy: default
#    Wait one second: 200 again. The gateway log shows "Rate limit 'default' reached by ip:127.0.0.1 ..."

# 2. RETRY + CIRCUIT BREAKER: stop store-service (Ctrl+C in its terminal), then call it 12 times
1..12 | ForEach-Object { curl.exe -s -o NUL -w "$_  %{http_code}  %{time_total}s`n" http://localhost:8080/api/v1/stores }
#    -> the first answers: 503 after ~0.33 s (the gateway tried 3 times: 100 ms and 200 ms apart)
#       then the log shows "Circuit breaker 'store-service' is OPEN for 15 s", and the next answers come in ~5 ms:
#       the service is not called any more. Every answer: 503, Retry-After: 15, "The service is temporarily unavailable ..."

# 3. RECOVERY: start store-service again, wait until it is up, then call it a few times
1..4 | ForEach-Object { curl.exe -s -o NUL -w "$_  %{http_code}`n" http://localhost:8080/api/v1/stores }
#    -> 200. The log shows "... is HALF_OPEN: a few test requests go through", then "... is CLOSED again".
#       (Test requests while the service is still starting fail: the circuit opens again for 15 s. That is expected.)

# 4. FALLBACK / GRACEFUL DEGRADATION: stop platform-service, then
curl.exe -s -i http://localhost:8080/api/v1/banners          # 200, X-Fallback: empty-list, data: [] (decoration only)
curl.exe -s -i http://localhost:8080/api/v1/platform/settings   # an honest 503 with Retry-After

# 5. TIMEOUT: a service slower than 10 s gives 504 "The service took too long to respond" and is not retried.
#    To see it without a slow service: start a second gateway with a 5 ms timeout and call it -
#    $env:SERVER_PORT = "8099"; $env:GATEWAY_RESPONSE_TIMEOUT = "5ms"; java -jar target\api-gateway-0.0.1-SNAPSHOT.jar
#    curl.exe -s -i http://localhost:8099/api/v1/categories   -> most calls: 504, log "... timed out: TimeoutException"

# 6. The state of every circuit breaker (1.0 = the current state) and the counters
(curl.exe -s http://localhost:8080/actuator/prometheus) -split "`n" |
    Select-String 'circuitbreaker_state.* 1\.0$|gateway_rate_limited_total|gateway_fallback_total'
```

Throttling (the bulkhead) needs 100 requests to one route in progress at the same time; `ResilienceTest` proves it
with a limit of 1. The other things to check are:
- logs in Grafana → Explore → Loki: `{app="api-gateway"} |= "Circuit breaker"`;
- metrics: the dashboard panels named above.

## Configuration

| Property | Env variable | Default | |
|---|---|---|---|
| `server.port` | `SERVER_PORT` | `8080` | |
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | `local` (IDE / mvnw), `prod` (Docker image) | `local` relaxes the login limit |
| `app.jwt.secret` | `JWT_SECRET` | local-only value | **Must equal auth-service's secret** |
| `app.cors.allowed-origins` | `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | React dev server |
| route targets | `AUTH_SERVICE_URL` ... `PLATFORM_SERVICE_URL`, `MCP_CLIENT_SERVICE_URL` | `http://localhost:8101` ... `:8114` | |
| `spring.data.redis.host` / `port` | `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | shared by all gateway instances |
| connect / response timeout | `GATEWAY_CONNECT_TIMEOUT_MS` / `GATEWAY_RESPONSE_TIMEOUT` | `2000` / `10s` | per route: `metadata.response-timeout` |
| retries (GET/HEAD) | `GATEWAY_RETRIES` | `2` | |
| default rate limit | `RATE_LIMIT_REQUESTS_PER_SECOND` / `RATE_LIMIT_BURST` | `20` / `40` | per client |
| login limit | `RATE_LIMIT_LOGIN_PER_MINUTE` / `RATE_LIMIT_LOGIN_BURST` | `10` / `10` (local `120` / `60`) | per IP |
| chat limit | `RATE_LIMIT_CHAT_PER_MINUTE` / `RATE_LIMIT_CHAT_BURST` | `10` / `10` | per user / IP |
| voice limit | `RATE_LIMIT_VOICE_PER_MINUTE` / `RATE_LIMIT_VOICE_BURST` | `20` / `10` | per user / IP |
| trusted proxies | `RATE_LIMIT_TRUSTED_PROXIES` | `0` | `1` behind a Kubernetes Ingress |
| circuit breaker | `GATEWAY_CB_WINDOW_SIZE` / `GATEWAY_CB_MINIMUM_CALLS` / `GATEWAY_CB_FAILURE_RATE` / `GATEWAY_CB_WAIT_IN_OPEN_STATE` | `20` / `10` / `50` % / `15s` | per route |
| bulkhead | `GATEWAY_MAX_CONCURRENT_CALLS` / `GATEWAY_CHAT_MAX_CONCURRENT_CALLS` / `GATEWAY_TRACKING_MAX_STREAMS` / `GATEWAY_VENDOR_ORDER_MAX_STREAMS` | `100` / `20` / `2000` / `2000` | per route and instance (the last two: open live streams, tracking and vendor orders) |
| degraded GET paths | (`app.fallback.empty-list-paths`) | `/api/v1/banners` | decoration only |

## Package layout

```text
com.grocery.gateway
├── config/      SecurityConfig (access rules), JwtConfig, CorsConfig, CircuitBreakerLoggingConfig, typed properties
├── constant/    HeaderNames, JwtClaims, Roles
├── dto/         ApiResponse (+ fallback answer), ErrorDetail (same shape as the services)
├── exception/   GlobalErrorHandler (all fallback answers), ErrorResponseWriter, FallbackProperties
├── filter/      CorrelationIdFilter, RequestLoggingFilter, UserHeadersFilter
├── ratelimit/   RateLimitFilter, RateLimitProperties, ClientKeyResolver
└── security/    JsonAuthenticationEntryPoint (401), JsonAccessDeniedHandler (403)
```
