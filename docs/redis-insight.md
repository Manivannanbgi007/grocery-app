# Redis Insight: look into Redis

Redis holds the platform's **short-lived data**: the API gateway's rate-limit counters and which delivery partners are
online. **Redis Insight** is Redis's own free web UI for it: browse the keys, see their values and how long they still
live (TTL), watch the commands the services send, and run Redis commands.

| | |
|---|---|
| URL | http://localhost:5540 → click **grocery-redis** (already connected, no login) |
| Start | always running: `.\scripts\start-infra.ps1` starts it with the rest (about 115 MB) |
| Version | `REDISINSIGHT_VERSION` in `infrastructure/.env` |
| Redis | `localhost:6379` from your machine, `redis:6379` inside Docker; one database (db 0), no password locally |

## What is in Redis

| Key | Written by | Value | Lives | Meaning |
|---|---|---|---|---|
| `request_rate_limiter.{<policy>.<client>}.tokens` | api-gateway (`RateLimitFilter`, Spring Cloud Gateway's `RedisRateLimiter`) | a number: requests (tokens) the client has left | 4 s (`default`), up to 2 min (`login`, `chat`) | the client's [rate limit](../api-gateway/README.md#resilience-and-traffic-control) |
| `request_rate_limiter.{<policy>.<client>}.timestamp` | same | when the tokens were last counted (seconds since 1970) | same | |
| `delivery:partner-online:<userId>` | delivery-service (`PartnerPresenceService`) | "online until", e.g. `2026-10-26T18:20:18Z` (UTC) | 30 min (`app.delivery.online-for`) | the partner is online and can get deliveries; no key = offline |
| `delivery:location:<deliveryId>` | delivery-service (`LiveLocationStore`) | JSON: the partner's last position (latitude, longitude, accuracy, when) and the route, if routing is on | deleted when the delivery is delivered / cancelled; at most 2 h after the last update (`app.tracking.location-ttl`) | the live tracking map ([delivery-tracking.md](delivery-tracking.md)) |

delivery-service also uses the **Pub/Sub channel** `delivery-tracking` (not a key: nothing is stored) to send every
tracking update to all its instances. Watch it: the **Pub/Sub** tab (next to Browse) → subscribe to `delivery-tracking`, then run
`scripts\simulate-delivery.ps1`.

`<policy>.<client>` is for example `default.user:4` (logged-in user 4), `login.ip:203.0.113.7` (a guest's IP address)
or `chat.user:4`. Locally your IP is often `0:0:0:0:0:0:0:1` (IPv6 for localhost).

**An empty Redis is normal**: rate-limit keys disappear a few seconds after the last request, partners are only
there while online, and positions only while a delivery is on its way. Nothing else uses Redis: carts and password-reset tokens are in MySQL, and mcp-server-service
caches in its own memory (Caffeine).

## Browse keys

1. Open http://localhost:5540 and click **grocery-redis**. The **Browse** tab lists the keys.
2. Pick the view above the list: **tree** (keys grouped into folders at every `:`) or **list** (≡, full key names).
   Use the list view for the gateway's keys: IPv6 addresses contain `:` too, so the tree cuts them into odd folders.
3. Filter with a pattern: `delivery:*`, `request_rate_limiter.{login*` (`*` = any text, `?` = one character).
4. Click a key: its type, value, **TTL** (seconds left) and size. Click refresh (↻) to see a changed value; rate-limit
   tokens change with every request.

To see something, make data: log in through the gateway (creates `login` keys for 60 s locally), or let a delivery
partner go online (`POST /api/v1/delivery/availability/online`, e2e\phase9-delivery.http).

## Watch it live: the Profiler

Bottom bar → **Profiler** → Start: every command Redis receives, as it happens. One request through the gateway looks
like this:

```text
"EVALSHA" "0ad5736e..." "2" "request_rate_limiter.{default.ip:...}.tokens" "request_rate_limiter.{default.ip:...}.timestamp" "20" "40" ...
[lua] "get" "request_rate_limiter.{default.ip:...}.tokens"
[lua] "get" "request_rate_limiter.{default.ip:...}.timestamp"
[lua] "setex" "request_rate_limiter.{default.ip:...}.tokens" "4" "39"
```

The gateway runs its rate-limit Lua script (`EVALSHA`) with rate 20 per second and burst 40; the script reads the
client's tokens and writes back 39, valid for 4 seconds. Because it is one script, it is exact even with many gateway
instances. Stop the Profiler when done: it slows Redis down a little.

## Run commands: CLI and Workbench

Redis Insight logs in as the Redis user `admin` (docker-compose.yml; the services use their own, restricted users,
[resilience.md](resilience.md#redis)). From a terminal: `docker exec -it grocery-redis redis-cli --user admin`.

Bottom bar → **CLI** runs one command at a time (like `redis-cli`); the **Workbench** tab runs several and keeps the
results. **Command Helper** (bottom bar) explains any command.

```text
SCAN 0 MATCH delivery:partner-online:* COUNT 100   # who is online (SCAN, not KEYS *: KEYS blocks a big Redis)
GET delivery:partner-online:5                       # online until
TTL delivery:partner-online:5                       # seconds left (-1 = never expires, -2 = no such key)
GET request_rate_limiter.{login.ip:0:0:0:0:0:0:0:1}.tokens
INFO keyspace                                       # number of keys, and how many of them expire
MEMORY USAGE delivery:partner-online:5              # bytes
```

## Changing data (locally)

Everything in Redis is short-lived and recreated by the services, so locally you may change it:

| You want | Do |
|---|---|
| Unblock yourself after hitting a rate limit (e.g. many logins in a test) | Delete the client's `request_rate_limiter.{login.…}` keys: the next request starts with a full bucket |
| Take a partner offline | Delete `delivery:partner-online:<userId>` (the same as the partner's "go offline") |

Never use `FLUSHALL` / `FLUSHDB`: they also take every delivery partner offline. To delete many keys, filter by a
pattern first, then use **Bulk actions** → delete (it asks before deleting).

## Configuration

[infrastructure/docker-compose.yml](../infrastructure/docker-compose.yml), service `redisinsight`:

| Setting | Why |
|---|---|
| `RI_REDIS_HOST: redis`, `RI_REDIS_PORT`, `RI_REDIS_ALIAS: grocery-redis` | the connection is ready on first start; nobody has to add it by hand |
| `RI_ACCEPT_TERMS_AND_CONDITIONS: "true"` | no licence screen on first start; usage analytics and notifications stay **off** |
| volume `redisinsight-data` | keeps your settings and command history across restarts |
| healthcheck `http://127.0.0.1:5540/api/health` | `start-infra.ps1` waits for it; `127.0.0.1` because the app only listens on IPv4 |

**Another Redis** (for example a Testcontainers Redis on a random port): **+ Add Redis database**, host
`host.docker.internal` (Redis Insight runs in Docker, so `localhost` would be the container itself), and the port.

## Troubleshooting

| Symptom | What to do |
|---|---|
| http://localhost:5540 does not open | `docker ps --filter name=grocery-redisinsight` should say healthy; `docker logs grocery-redisinsight` |
| grocery-redis cannot connect | Is Redis running? `.\scripts\check-health.ps1` |
| No keys | Normal when nobody is online and no request came in the last seconds (see "Browse keys") |
| A key disappears while you look at it | Its TTL ran out: that is the point of these keys |
| Gateway keys in strange folders | Switch to the list view (≡) |

## In production

- Redis has a password or ACL users. Give Redis Insight its own user that may only read (an ACL user with `+@read`).
- Redis Insight is reachable only from the internal network or a VPN, never from the internet. Set
  `RI_ENCRYPTION_KEY` so the saved connection passwords are encrypted.
- Don't run the Profiler (`MONITOR`) on a busy production Redis: it costs performance.
- On Kubernetes the services reach Redis from outside the cluster (`REDIS_HOST` in `deployment/kubernetes/base/configmap.yaml`),
  like MySQL and Kafka; Redis Insight is not part of the `deployment/kubernetes/` manifests.
