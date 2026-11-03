# Performance: virtual threads, measured

**Decision (Phase 19g, 2026-10-28): virtual threads stay off in every service.** They were measured where they
should help most, and gave no gain. At 400 users they were slower and used more memory. Below: what they are, what
was measured, why the result is what it is, and how to measure again.

## What virtual threads change

A Spring MVC service handles each request on a thread. By default Tomcat has a pool of **200 platform threads**:
request 201 waits until one is free. A virtual thread is a cheap thread managed by Java instead of the operating
system, so with `spring.threads.virtual.enabled=true` (environment variable `SPRING_THREADS_VIRTUAL_ENABLED=true`)
there is no pool and no limit: every request gets its own.

What Spring Boot switches to virtual threads with that one setting:
- Tomcat's request handling;
- `@Async` and the application task executor;
- `@Scheduled` jobs;
- the Kafka listener threads (seen in the test: the consumer thread `kafka-1` became a virtual thread).

Not affected: the API gateway and every WebClient (Netty, non-blocking already), and HikariCP's own housekeeping threads.

**What does not change:** everything the request waits for. A request still needs one of the 10 database connections
(Phase 19e: pools within a connection budget), a connection of the Elasticsearch client, or an answer from another
service. Virtual threads only help when the **thread pool** is the limit, not when something behind it is.

## What was measured

k6 2.3.0 in Docker, calling the service directly, not through the gateway (its rate limit would stop the test). Each
user sends its next request as soon as the answer arrives. Every level ran 30 s after a 10 s warm-up, twice per mode.
The machine was the usual laptop with every service, Kafka, MySQL and Elasticsearch running. These numbers are
**comparisons on one noisy machine, not capacity figures**: the same setting varied up to 2.5× between rounds, so only
differences that showed in both rounds count. CPU, heap, threads and waiting database requests came from each
service's own `/actuator/prometheus`, once a second.

**Search** (`perf/k6/search.js`): product-read-service, Elasticsearch only, no database. Requests per second
(round 1 · round 2) and p95:

| Users | Platform threads | Virtual threads | p95 platform | p95 virtual | Heap (max) platform / virtual |
|---|---|---|---|---|---|
| 50 | 1,349 · 532 | 1,049 · 895 | 72 · 94 ms | 92 · 66 ms | 91–97 / 105–229 MB |
| 200 | 808 · 1,216 | 1,368 · 951 | 180 · 265 ms | 213 · 341 ms | 122–126 / 177–221 MB |
| 400 | 1,317 · 1,424 | 1,097 · 1,065 | 443 · 390 ms | **620 · 584 ms** | 129–150 / **227–229 MB** |

**Cart add** (`perf/k6/cart-add.js`): cart-service. Each add calls product-read-service over HTTP between two short
MySQL transactions. Requests per second for the add (round 1 · round 2), p95, and requests waiting for a database
connection:

| Users | Platform threads | Virtual threads | p95 platform | p95 virtual | Waiting for a DB connection platform / virtual |
|---|---|---|---|---|---|
| 50 | 271 · 158 | 209 · 275 | 303 · 681 ms | 392 · 281 ms | 39 / 40 |
| 200 | 333 · 332 | 277 · 282 | 998 · 1,064 ms | 892 · 923 ms | 189 / 190 |
| 400 | 335 · 422 | 246 · 319 | 1,609 · 1,071 ms | **1,911 · 1,659 ms** | 189 / **390** |

No request failed in the cart test. In the search test 0.01–0.1 % failed in both modes: connection errors on the
client side (Docker's port forwarding under load). The service logged none of them.

**Pinning** (`-Djdk.tracePinnedThreads=short`, Java 21): a virtual thread "pins" its carrier when it waits inside
`synchronized`. It happened only in the Kafka consumer while it joined its group, 2–8 times per run. It never happened
in the request path: Tomcat, HikariCP, the MySQL driver, the HTTP and Elasticsearch clients and logging did not pin.

## Why there is no gain

- **Cart add: the 10 database connections are the limit.** With platform threads, 200 requests run and the rest wait
  in Tomcat's queue; 189 of the 200 wait for a connection. With virtual threads, all 400 run and **390 wait for a
  connection**. The same requests wait, just somewhere else: the throughput is the same, the heap is fuller (every
  waiting request holds its objects), and the p95 at 400 users was worse in both rounds. More connections are not the
  answer either: the pool sizes are part of MySQL's connection budget (docs/resilience.md, "Database connections").
- **Search: Elasticsearch and its client are the limit.** The service spends its time waiting for the
  single-node Elasticsearch through the client's small connection pool. The throughput stayed at about the same level
  at 50, 200 and 400 users in both modes, which is the sign of a limit behind the threads, not in them.
- **Where virtual threads would win:** a service with many requests that each wait a long time for something with no
  small limit behind it. For example, thousands of concurrent slow HTTP calls to an outside API that has no connection
  pool cap, or long polling. None of the services works like that today. The assistant's long LLM calls are limited
  by the language model and by the gateway's chat rate limit, not by threads.

## If you switch them on for a service

- Set `SPRING_THREADS_VIRTUAL_ENABLED=true` for that service only, and measure it with the k6 scripts before and after.
- **Monitoring:**
  - Tomcat then has no thread pool and reports `-1` for its busy and maximum threads.
  - The `WebThreadsBusy` alert leaves such a service out (`> 0` guard, tested in `grocery-alerts-test.yml`). Without
    the guard, -1 / -1 would look like 100 % busy and the alert would fire all the time.
  - The "Web threads" panel of *Grocery service details* hides the -1 lines and shows **requests in progress**
    (`http_server_requests_active_seconds_gcount`), which works in both modes.
  - Watch `hikaricp_connections_pending` instead: with virtual threads, that is where requests wait.
- **Limits stay important:** without a thread limit, the pools (database, HTTP clients) and their timeouts (5 s for a
  database connection, Phase 19e) are what protect the service. Never remove them because "threads are free now".
- **Java 24 and later** no longer pin on `synchronized` (JEP 491), so `-Djdk.tracePinnedThreads` is gone there; on
  Java 21 use it to find pinning before switching a service on.

## Measure it yourself

```powershell
# Search (product-read-service on 8105) and cart add (cart-service on 8107), 200 users for 30 s:
Get-Content perf\k6\search.js | docker run --rm -i -e VUS=200 grafana/k6:2.3.0 run -
Get-Content perf\k6\cart-add.js | docker run --rm -i -e VUS=200 grafana/k6:2.3.0 run -
```

To compare, start the service once without and once with `SPRING_THREADS_VIRTUAL_ENABLED=true` (and
`-Djdk.tracePinnedThreads=short` in the java command to see pinning), with the same settings otherwise, and run each
level at least twice. The cart test uses made-up customers 900001 and up and empties their carts at the end.
