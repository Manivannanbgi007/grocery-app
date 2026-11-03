# Live delivery tracking (OpenStreetMap)

The customer follows their delivery on a map, live: where the delivery partner is, the store (pickup) and their own
address (destination), the delivery status, the partner's name and phone, and when the position was last updated. The
partner's phone shares its position from the browser while the partner delivers.

- **Customer:** Fresh Grocery → My orders → an order → **Track on the map** (`/orders/:orderId/track`). Shown from
  "ready for pickup" until the order is delivered.
- **Partner:** the delivery panel (`/delivery`) → the current delivery → **Live location** → **Share my location**.
  Sharing runs from accepting the delivery until it is delivered (or cancelled, or failed).
- **Map:** [Leaflet](https://leafletjs.com) + [React Leaflet](https://react-leaflet.js.org) with OpenStreetMap tiles.
  **Backend:** delivery-service (Spring Boot), MySQL as the source of truth, Redis for the live position.
  **Live updates:** Server-Sent Events (SSE).

Contents: [How it works](#1-how-it-works) · [Folder structure](#2-folder-structure) · [API contracts](#3-api-contracts) ·
[Who may see what](#4-who-may-see-what) · [Step by step](#5-step-by-step) ·
[OpenStreetMap tiles](#6-openstreetmap-tiles-attribution-and-usage-policy) · [Routes and arrival times](#7-routes-and-arrival-times-optional) ·
[Location in the background](#8-location-in-the-background-what-a-web-page-cannot-do) ·
[Production](#9-production-considerations) · [Limitations](#10-limitations) · [Tests](#11-tests)

## 1. How it works

```text
 Partner's phone (browser)                 delivery-service (any instance)                       Customer's browser
 ─────────────────────────                 ───────────────────────────────                       ──────────────────
 navigator.geolocation.watchPosition
   │ only after 25 m or 15 s, at most every 4 s
   ▼
 PUT /api/v1/delivery/deliveries/{id}/location ──► gateway (role DELIVERY_PARTNER)
                                                     │
                                                     ▼
                                            own delivery? being tracked?
                                            position recent and precise enough?
                                                     │ yes
                                                     ├──► Redis key  delivery:location:<deliveryId>   (last position only, TTL 2 h)
                                                     └──► Redis channel "delivery-tracking"  (the new snapshot)
                                                                │  every instance hears it
 status change (accept, pick-up, deliver ...) ──► MySQL ──►     ▼
   after the commit, the same snapshot ────────────────► open streams of that order ──► SSE "tracking" event ──► map + texts
                                                                                          GET /api/v1/delivery-tracking/orders/{orderId}/stream
```

1. The partner's browser measures positions (`watchPosition`) and sends **some** of them with a plain HTTP `PUT`: after
   moving 25 m, or after 15 s anyway, never more often than every 4 s, never an imprecise one (`TRACKING_CONFIG`).
2. delivery-service checks the delivery belongs to this partner and is being tracked (accepted, picked up or on the
   way), and that the position is usable (see [the rules](#the-rules-for-a-position)). It keeps **only the last position**,
   in Redis, and publishes the new snapshot on a Redis channel.
3. Every delivery-service instance listens to that channel and sends the snapshot to the customers that follow this
   order **on that instance** (each browser has one open SSE stream, to one instance). So it works with any number of
   instances behind the gateway, without sticky sessions.
4. A status change (accept, pick up, start, deliver, cancel ...) is saved in MySQL first. After the transaction has
   committed, the same kind of snapshot goes out (`DeliveryStatusListener` → `TrackingStatusPublisher`). When tracking
   ends, the position is deleted from Redis.
5. The customer's page opens the stream once. It receives the full current state at once, then a new snapshot after
   every change, and a keep-alive comment every 15 s. When the delivery is over, the server sends the final state and
   closes the stream.

### What is stored where

| Where | What | How long |
|---|---|---|
| MySQL `delivery_db.deliveries` (source of truth) | The delivery and its status; the store's and the customer's coordinates (`pickup_latitude` ... `drop_longitude`, copied from the `OrderReadyForPickup` event) | Like every delivery |
| Redis key `delivery:location:<deliveryId>` | The partner's **last** position (+ the route, when routing is on), JSON | Deleted when tracking ends; expires 2 h after the last update anyway |
| Redis channel `delivery-tracking` | Snapshots on their way to the instances | Not stored (Pub/Sub) |
| The browser | Nothing (no local storage) | – |

**Why not every position in MySQL?** A partner sends a position every few seconds: thousands of writes per hour for
one busy shop, for data nobody needs a day later. Only the last position matters to the customer, so it lives in Redis
(in memory, with an expiry). The business facts (the statuses and their times) stay in MySQL, as before. The trip is
**not** recorded as a history (see [Limitations](#10-limitations)).

**Why Server-Sent Events and not WebSockets?** The live updates go one way (server → customer). SSE is plain HTTP: it
passes the gateway, its JWT check and rate limits like any request, and needs no extra protocol or message broker in
the browser. The partner's positions go the other way as normal `PUT` requests, which are easy to check, rate-limit and
test. WebSockets would be the choice for two-way chat-like traffic, which tracking does not need.

The browser's own `EventSource` cannot send an `Authorization` header, so the page reads the stream with `fetch`
(`src/api/eventStream.ts`, ~50 lines) and reconnects by itself (`useTrackingStream`).

### Who sees what, when

| Delivery status | Partner shares location | Customer's page |
|---|---|---|
| (order not ready yet) | – | "Live tracking starts soon" + the order's progress; the order is refreshed every 30 s |
| `WAITING_FOR_PARTNER` | – | "Finding a delivery partner", the store and the address on the map |
| `ASSIGNED` | – | "Arjun will bring your order" |
| `ACCEPTED` | **yes** | "Arjun is on the way to Fresh Mart": the partner on the map, the line to the store |
| `PICKED_UP`, `OUT_FOR_DELIVERY` | **yes** | "Arjun is on the way to you": the line to the customer's address |
| `DELIVERED`, `FAILED`, `RETURNED`, `CANCELLED` | stops by itself | The final state; no partner on the map; the stream is closed |

## 2. Folder structure

```text
delivery-service/src/main/java/com/grocery/delivery/
├── controller/
│   ├── CustomerTrackingController.java     GET /api/v1/delivery-tracking/orders/{orderId} (+ /stream)
│   └── PartnerTrackingController.java      PUT /api/v1/delivery/deliveries/{id}/location, GET .../{id}/tracking
├── service/DeliveryTrackingService.java    the rules (impl/DeliveryTrackingServiceImpl.java)
├── tracking/
│   ├── LiveLocationStore.java              the last position in Redis
│   ├── TrackingSnapshotFactory.java        delivery (+ position) -> TrackingResponse
│   ├── TrackingBroadcaster.java            publishes a snapshot on the Redis channel
│   ├── LiveTrackingStreams.java            the open SSE streams of this instance; keep-alives; Redis listener
│   ├── DeliveryStatusListener.java         JPA listener: a delivery's status changed ...
│   ├── TrackingStatusPublisher.java        ... after the commit: publish it, forget the position when tracking ends
│   └── RoutingClient.java                  optional OSRM routing (route line + arrival time)
├── config/TrackingProperties.java          app.tracking.* settings; RedisTrackingConfig.java (the channel listener)
├── dto/request/LocationUpdateRequest.java  dto/response/TrackingResponse.java, LiveLocation, TrackingRoute, ...
└── resources/db/migration/V5__add_coordinates_to_deliveries.sql
order-service: V6__add_store_coordinates_to_orders.sql; OrderReadyForPickup.avsc carries the coordinates (optional fields)
api-gateway:   application.yml route "delivery-service-tracking-stream" (no response timeout, own breaker and bulkhead)

fresh-grocery/src/
├── config/tracking.ts                      MAP_CONFIG (tiles, attribution, default centre) + TRACKING_CONFIG (timings)
├── api/tracking.ts, api/eventStream.ts     the API calls; the SSE reader (fetch + Authorization)
├── api/types.ts                            TrackingSnapshot, LiveLocation, TrackingRoute, LocationUpdate ...
├── components/map/                         reusable map parts
│   ├── TrackingMap.tsx                     MapContainer + OSM tile layer + markers + line (imports Leaflet's CSS)
│   ├── LazyTrackingMap.tsx                 loads the map on demand (Leaflet is a separate 46 kB gzip download)
│   ├── markers.tsx                         store / home / partner markers (HTML "divIcon"s), accuracy circle
│   ├── RouteLine.tsx                       the route along the streets, or a straight dashed line
│   └── FitToPoints.tsx                     fits the view to the points, follows the partner
├── features/tracking/                      the customer's page
│   ├── OrderTrackingPage.tsx               /orders/:orderId/track
│   ├── useTrackingStream.ts                the live connection: reconnect, waiting, silence watchdog
│   ├── ConnectionStatus.tsx                "Live" / "Reconnecting…" / "Finished" badge
│   └── trackingTexts.ts                    every text of the page (headline, arrival, order step)
├── features/delivery/tracking/             the partner's side
│   ├── useLocationSharing.ts               Geolocation watch, sending rules, permission, wake lock
│   └── LiveLocationPanel.tsx               "Live location" box in the delivery screen
├── mocks/mockTracking.ts, mocks/handlers/tracking.ts   the same API in the browser (npm run dev:mock, tests)
└── e2e/tracking.spec.ts                    Playwright: two browsers against the real backend
scripts/simulate-delivery.ps1               a partner "driving" through the real API
```

## 3. API contracts

All through the gateway (`http://localhost:8080`), with `Authorization: Bearer <token>`. The answers use the platform's
`ApiResponse` envelope (`success`, `message`, `data`, `errors`, `timestamp`, `path`). The samples below are real answers
of the local backend (addresses shortened).

| Method | Path | Who | What |
|---|---|---|---|
| GET | `/api/v1/delivery-tracking/orders/{orderId}` | CUSTOMER (own order) | The delivery's state now (snapshot) |
| GET | `/api/v1/delivery-tracking/orders/{orderId}/stream` | CUSTOMER (own order) | Live: `text/event-stream`, a snapshot after every change |
| PUT | `/api/v1/delivery/deliveries/{deliveryId}/location` | DELIVERY_PARTNER (assigned partner) | The partner's position |
| GET | `/api/v1/delivery/deliveries/{deliveryId}/tracking` | DELIVERY_PARTNER (assigned partner) | The same snapshot, for the partner's map |

`GET /api/v1/delivery/deliveries/{id}` (the partner's delivery) also has `storeLocation` and `customerLocation`
(`{latitude, longitude}` or `null`).

### The snapshot: `GET /api/v1/delivery-tracking/orders/103`

```json
{
  "success": true,
  "message": "Tracking fetched successfully",
  "data": {
    "orderId": 103,
    "deliveryId": 56,
    "status": "OUT_FOR_DELIVERY",
    "live": true,
    "partner": { "name": "Arjun Singh", "phone": "9000000005", "vehicleType": "SCOOTER" },
    "pickup": {
      "name": "Fresh Mart",
      "address": "45, 100 Feet Road, Indiranagar, ...",
      "location": { "latitude": 12.9784, "longitude": 77.6408 }
    },
    "destination": {
      "name": "Priya Sharma",
      "address": "Flat 302, Lakeview Apartments, ...",
      "location": { "latitude": 12.9719, "longitude": 77.6412 }
    },
    "location": {
      "latitude": 12.97195,
      "longitude": 77.64118,
      "accuracyMeters": 6.5,
      "recordedAt": "2026-10-26T20:28:28.059Z",
      "receivedAt": "2026-10-26T20:28:28.085581300Z"
    },
    "route": null,
    "generatedAt": "2026-10-26T20:28:28.240802900Z"
  },
  "errors": null,
  "timestamp": "2026-10-26T20:28:28.242872400Z",
  "path": null
}
```

| Field | Meaning |
|---|---|
| `status` | The delivery's status (`WAITING_FOR_PARTNER` ... `DELIVERED`, `FAILED`, `RETURNED`, `CANCELLED`) |
| `live` | `true` while the partner shares the position (accepted, picked up, on the way) |
| `partner` | `null` until a partner is assigned |
| `pickup.location`, `destination.location` | `null` when the store or the address has no coordinates (then that marker is missing, and the page says so) |
| `location` | The partner's last position, `null` before the first one and when not `live`. `recordedAt` = measured by the phone, `receivedAt` = arrived at the server (the page shows "updated 20 s ago" from it) |
| `route` | `null` without a routing service; see [Routes and arrival times](#7-routes-and-arrival-times-optional) |

With routing switched on, `route` looks like this (the page then shows "Arrives around 10:42 (in about 7 min)" and
draws the line along the streets; `points` are `[latitude, longitude]`):

```json
"route": {
  "target": "DESTINATION",
  "distanceMeters": 735.7,
  "durationSeconds": 47.0,
  "estimatedArrivalAt": "2026-10-27T05:07:47Z",
  "points": [[12.978401, 77.640814], [12.978424, 77.640913], [12.971902, 77.641245]],
  "from": { "latitude": 12.9784, "longitude": 77.6408 },
  "computedAt": "2026-10-27T05:07:00Z"
}
```

### The live stream: `GET /api/v1/delivery-tracking/orders/103/stream`

```text
HTTP/1.1 200
Content-Type: text/event-stream
Cache-Control: no-cache
X-Accel-Buffering: no

event:tracking
data:{"orderId":103,"deliveryId":56,"status":"OUT_FOR_DELIVERY","live":true,"partner":{...},"pickup":{...},...}

:keep-alive

event:tracking
data:{...the next snapshot, after the next position or status change...}
```

- The first event comes at once: the page needs no separate `GET` first.
- `:keep-alive` (an SSE comment) every 15 s, so proxies keep the connection open and the page can tell a quiet
  connection from a dead one (it reconnects after 45 s of silence).
- After a final status (`DELIVERED`, `FAILED`, `RETURNED`, `CANCELLED`) the server sends that state and ends the stream.
- After 30 minutes the server ends the stream; the page opens a new one (and gets the current state again).

Try it by hand (`-N` = print as it arrives):

```powershell
curl.exe -N -H "Authorization: Bearer <customer token>" http://localhost:8080/api/v1/delivery-tracking/orders/103/stream
```

### Sending a position: `PUT /api/v1/delivery/deliveries/56/location`

```json
{ "latitude": 12.97195, "longitude": 77.64118, "accuracyMeters": 6.5, "recordedAt": "2026-10-26T20:28:28.059Z" }
```

| Field | Rules |
|---|---|
| `latitude` | required, −90 … 90 |
| `longitude` | required, −180 … 180 |
| `accuracyMeters` | optional, ≥ 0: the browser's `coords.accuracy` |
| `recordedAt` | required: when the phone measured it (`GeolocationPosition.timestamp`) |

Stored (`200`): the customers following the order get it at once.

```json
{ "success": true, "message": "Location updated", "data": { "stored": true, "route": null }, ... }
```

Skipped (`200`, not an error): it came less than 2 s after the last stored one, or a newer one is already stored (a
phone that was offline sends old positions late).

```json
{ "success": true, "message": "Location skipped", "data": { "stored": false, "route": null }, ... }
```

#### The rules for a position

| Case | Answer | Message |
|---|---|---|
| Not this partner's delivery (or no such delivery) | `404` | `Delivery not found with id: 56` |
| Delivery not accepted yet | `400` | `Location sharing starts when you accept the delivery` |
| Delivery over (delivered, cancelled ...) | `400` | `This delivery is DELIVERED: location sharing has stopped` (the phone then stops sharing) |
| `recordedAt` more than 1 min in the future | `400` | `The location's time is in the future: please check the phone's date and time` |
| `recordedAt` older than 10 min | `400` | `This location is older than 10 minutes and was not used` |
| `accuracyMeters` above 1000 (`TRACKING_MAX_ACCURACY_METERS`) | `400` | `The location is not precise enough (about 4800 m). Please turn on GPS / precise location` |
| Missing / out-of-range fields | `400` | `Validation failed` with `errors: [{"field": "latitude", "message": "must be less than or equal to 90.0"}, ...]` |
| Less than 2 s after the last one, or not newer | `200` | `Location skipped` (`stored: false`) |

### Other answers

| Answer | When |
|---|---|
| `401` | No or expired token (gateway) |
| `403` | Wrong role, e.g. a partner calling the customer's path (gateway) |
| `404` `No delivery for order 103 yet: tracking starts when the order is ready for pickup` | The order has no delivery yet, **or it is not the caller's order** (the same answer on purpose, see below). The page keeps waiting and asks again every 15 s |
| `503` `Live tracking is very busy right now ...` | The instance already has `TRACKING_MAX_STREAMS` open streams; the page tries again |

## 4. Who may see what

Two checks, like everywhere on the platform:

1. **The gateway checks the role** (`api-gateway/.../SecurityConfig.java`): `/api/v1/delivery/**` is for
   `DELIVERY_PARTNER`; `/api/v1/delivery-tracking/**` falls under "any other `/api/v1/**`" = `CUSTOMER`.
2. **delivery-service checks ownership in the query itself:**
   - customer: `findByOrderIdAndCustomerUserId(orderId, <user id from the token>)`
   - partner: `findByIdAndPartnerUserId(deliveryId, <user id from the token>)`

   Someone else's order or delivery is simply "not found" (`404`). The answer does not even reveal that the order
   exists. The user id comes from the gateway (`X-User-Id`, taken from the verified JWT), never from the request body.

A partner can only send positions for **their own** delivery, and only **while** it is tracked. Once delivered or
cancelled, the server refuses positions and has already deleted the last one.

## 5. Step by step

How the feature was built, in the order you would build it again. Every step names the files to look at.

### Step 1: install the map packages

```powershell
cd fresh-grocery
npm install leaflet@1.9.4 react-leaflet@5.0.0
npm install -D @types/leaflet@1.9.22
```

React Leaflet 5 needs React 19. Leaflet is the map engine; React Leaflet wraps it in components (`<MapContainer>`,
`<TileLayer>`, `<Marker>` ...). There is no API key: OpenStreetMap needs none.

### Step 2: include Leaflet's CSS

`components/map/TrackingMap.tsx` starts with `import 'leaflet/dist/leaflet.css'`. Without it the map tiles appear
scattered over the page. The import is in the map component (not in `main.tsx`), so the CSS, like Leaflet itself, is only
downloaded by pages with a map (`LazyTrackingMap` loads the component with `React.lazy`).

Leaflet's default marker is a PNG that bundlers do not find. Our markers are HTML badges instead (`L.divIcon` with
Tailwind classes, `markers.tsx`), so no image paths are needed.

### Step 3: the tile layer and the attribution

```tsx
<MapContainer center={MAP_CONFIG.defaultCenter} zoom={MAP_CONFIG.defaultZoom} maxZoom={MAP_CONFIG.maxZoom} className="size-full">
  {/* The attribution is required by OpenStreetMap's licence: keep it */}
  <TileLayer url={MAP_CONFIG.tileUrl} attribution={MAP_CONFIG.attribution} maxZoom={MAP_CONFIG.maxZoom} />
  ...
</MapContainer>
```

The URL and the attribution come from `config/tracking.ts`, never from a component. Leaflet shows the attribution in the
map's corner ("© OpenStreetMap contributors"). Read [section 6](#6-openstreetmap-tiles-attribution-and-usage-policy)
before going to production.

### Step 4: the settings

**Frontend** (`fresh-grocery/.env.local`, see `.env.example`; empty = the defaults in `config/tracking.ts`):

| Variable | Default | What |
|---|---|---|
| `VITE_MAP_TILE_URL` | `https://tile.openstreetmap.org/{z}/{x}/{y}.png` | The tile server. Production: your tile provider's URL with its key |
| `VITE_MAP_ATTRIBUTION` | `© OpenStreetMap contributors` (linked) | The attribution of that provider (keep "OpenStreetMap contributors") |
| `VITE_MAP_DEFAULT_CENTER` | `12.9716,77.5946` (Bengaluru) | Where the map looks before any point is known |

```dotenv
# .env.local (production example with a commercial tile provider)
VITE_MAP_TILE_URL=https://api.maptiler.com/maps/streets-v2/256/{z}/{x}/{y}.png?key=YOUR_KEY
VITE_MAP_ATTRIBUTION=&copy; MapTiler &copy; OpenStreetMap contributors
VITE_MAP_DEFAULT_CENTER=12.9716,77.5946
```

The timings (reconnect, "delayed after 60 s", the phone's sending rules) are in `TRACKING_CONFIG` in the same file.

**Backend** (delivery-service `application.yml`, `app.tracking`):

| Property | Env variable | Default | What |
|---|---|---|---|
| `location-ttl` | – | `2h` | Redis forgets a position after this long without an update |
| `min-update-interval` | – | `2s` | Faster positions are skipped |
| `max-location-age` | – | `10m` | Older positions are refused |
| `max-accuracy-meters` | `TRACKING_MAX_ACCURACY_METERS` | `1000` | Less precise positions are refused |
| `heartbeat-interval` | – | `15s` | Keep-alive on every stream |
| `stream-timeout` | – | `30m` | A stream ends after this (the page reconnects) |
| `max-streams` | `TRACKING_MAX_STREAMS` | `2000` | Open streams per instance |
| `routing.url` | `TRACKING_ROUTING_URL` | empty = off | An OSRM-compatible server ([section 7](#7-routes-and-arrival-times-optional)) |
| `routing.timeout` | – | `3s` | Per routing call |
| `routing.recalculate-after-meters` / `-after` | – | `150` / `60s` | The route is reused until the partner moved this far / it is this old |

Gateway: `GATEWAY_TRACKING_MAX_STREAMS` (default `2000`) = open streams through one gateway instance. Kubernetes:
`deployment/kubernetes/base/configmap.yaml` has `TRACKING_ROUTING_URL: ""`.

**Sample data:** Fresh Mart is at `12.9784, 77.6408` (Indiranagar), Green Basket at `12.9336, 77.6265` (Koramangala).
customer1's home address (id 1) is at `12.9719, 77.6412`, about 720 m south of Fresh Mart. delivery1 (Arjun Singh)
delivers in Fresh Mart's zone. Orders placed before coordinates existed have none (their map shows what it knows).

### Step 5: the customer's tracking page

`features/tracking/OrderTrackingPage.tsx`, route `orders/:orderId/track` (customers only, `RequireCustomer`). The
order page shows a **Track on the map** button once the order is ready for pickup or on its way.

```tsx
const { snapshot, state } = useTrackingStream(orderId, trackable)   // the live connection
...
<LazyTrackingMap pickup={...} destination={...} partner={snapshot.live ? snapshot.location : null}
                 nextStop={snapshot.status === 'ACCEPTED' ? 'PICKUP' : 'DESTINATION'} route={snapshot.route} ... />
```

What the page shows in each situation:

| Situation | Shown |
|---|---|
| Loading the order | A skeleton |
| Order not found / someone else's | "We could not find this order" |
| Order not ready yet, or cancelled | "Live tracking starts soon" (with the order's progress), or "This order will not be delivered" |
| Connecting | Skeleton + "Connecting to live tracking…", badge "Connecting…" |
| No delivery yet (404) | "Waiting for the store to hand over your order…", badge "Waiting for the delivery" (asks again every 15 s) |
| Live | Map, headline ("Arjun is on the way to you"), arrival time or straight-line distance, partner card (name, vehicle, last update, call link), order progress; badge "Live" |
| Partner not sharing yet | "Waiting for Arjun's location…" |
| Position older than 60 s | Grey partner marker + "Last position 2 min ago. Arjun's phone may have lost its signal …" |
| Connection lost | "The live connection was lost. Trying again… The map shows the last known state.", badge "Reconnecting…" |
| Not allowed (401/403) | "Live tracking is not available" |
| Delivered | "Delivered", badge "Finished", no more updates |

The headline is an `aria-live` region, so a screen reader announces status changes. The map has an `aria-label`, and
every marker has a title.

### Step 6: the partner's location sharing

`features/delivery/tracking/useLocationSharing.ts` + `LiveLocationPanel.tsx`, shown in the delivery screen while the
delivery is accepted, picked up or on its way.

- **Starting:** the partner taps **Share my location**: the browser asks for permission. When the site was allowed
  before (Permissions API says `granted`), sharing starts by itself.
- **Measuring:** `navigator.geolocation.watchPosition(..., { enableHighAccuracy: true, maximumAge: 5000, timeout: 30000 })`.
- **Sending:** after 25 m or 15 s, at most every 4 s, not while the last one is still on its way, never above 1000 m
  accuracy (the panel says "turn on GPS" instead). A failed send is not repeated: the next position, seconds later, is
  newer anyway. "No connection" is shown until a send works again.
- **Stopping:** automatically when the delivery is no longer active (the GPS watch is cleared), when the page closes,
  and when the server answers that the delivery is over (`400 ... location sharing has stopped`): the delivery is then
  loaded again and the panel disappears.
- **States and messages:** `off` (button), `starting` ("Finding your position…"), `sharing` ("The customer sees where you
  are. Last sent just now."), `denied` (how to allow it again in the browser), `unavailable` (turn on location),
  `unsupported` (old browser), `insecure` (needs https).
- **Screen on:** while sharing, the Wake Lock API keeps the screen on where supported. The panel asks the partner to keep
  the screen open. After the page was in the background, it says the position may not have been sent. See
  [section 8](#8-location-in-the-background-what-a-web-page-cannot-do).

### Step 7: the backend APIs

- delivery-service: the two controllers and `DeliveryTrackingServiceImpl` ([section 3](#3-api-contracts)).
- The coordinates: store-service and customer-service already had `latitude` / `longitude`. order-service now keeps the
  store's coordinates on the order (`V6__add_store_coordinates_to_orders.sql`) and sends both points in
  `OrderReadyForPickup` (four optional Avro fields, default `null`: a backward-compatible schema change, older events
  still read). delivery-service saves them on the delivery (`V5__add_coordinates_to_deliveries.sql`).
- The gateway: `/api/v1/delivery-tracking/**` goes to delivery-service. The stream has a route of its own,
  `delivery-service-tracking-stream`, with **no response timeout** (`metadata.response-timeout: -1`; every other
  route is cut after 10 s). It also has its own circuit-breaker settings (a stream lasting minutes must not count as a
  "slow call") and its own bulkhead (`GATEWAY_TRACKING_MAX_STREAMS`).

### Step 8: real-time updates

- **Server:** `LiveTrackingStreams` keeps the open `SseEmitter`s per order (Spring MVC's SSE support; an idle stream holds
  a connection, not a thread). It sends the keep-alives and listens to the Redis channel.
- **Browser:** `useTrackingStream(orderId, enabled)` returns `{ snapshot, state }` and does the hard parts:
  - reconnects after any break: 2 s, 4 s, 8 s … at most 30 s apart, and at once when the browser comes back online;
  - treats 45 s without any data (not even a keep-alive) as a dead connection;
  - asks again every 15 s while the order has no delivery yet (404);
  - stops for good on 401/403, and after the final state;
  - cleans up everything (the connection, timers, listeners) when the page closes. One hook per order: the component
    that uses it gets `key={orderId}`.
- The order's progress list follows the live status at once (`orderStepOf`): order-service hears about a pick-up a
  moment later through Kafka.

### Step 9: simulated coordinates

You do not need to walk around with a phone:

1. **The simulation script** (real backend): a partner "driving" a real order through the real API.

   ```powershell
   .\scripts\simulate-delivery.ps1                    # customer1 orders, vendor1 hands it over, delivery1 drives and delivers
   .\scripts\simulate-delivery.ps1 -NoDeliver         # stop at the door (status OUT_FOR_DELIVERY)
   .\scripts\simulate-delivery.ps1 -OrderId 42        # drive an order already assigned to delivery1
   .\scripts\simulate-delivery.ps1 -Steps 30 -SecondsBetweenUpdates 2
   ```

   It prints the customer's tracking URL: log in as `customer1@grocery.com` (`Password@123`) and watch the scooter move
   from about 1 km north-west of Fresh Mart to the store, then to the customer. The positions are on a straight line (no
   road network).

2. **The browser's location sensor** (partner panel on your computer): DevTools → ⋮ → More tools → **Sensors** →
   Location → a preset, or enter a latitude and longitude (e.g. `12.9784`, `77.6408`). Change the values while
   sharing, and the customer's page (in another browser profile, logged in as customer1) moves along.

3. **Mock mode** (no backend): `npm run dev:mock`. The mock API has the same endpoints, rules and stream. Deliveries move
   with the mock vendor and delivery panels. There is no routing in the mock, so there is no arrival time.

4. **By hand** with curl (partner token from `POST /api/v1/auth/login` as `delivery1@grocery.com`):

   ```powershell
   $body = '{"latitude":12.9750,"longitude":77.6405,"accuracyMeters":8,"recordedAt":"' + (Get-Date).ToUniversalTime().ToString("o") + '"}'
   $body | curl.exe -s -X PUT -H "Authorization: Bearer <partner token>" -H "Content-Type: application/json" --data-binary "@-" http://localhost:8080/api/v1/delivery/deliveries/<deliveryId>/location
   ```

### Step 10: run, test and debug

```powershell
.\scripts\start-infra.ps1                     # MySQL, Kafka, Redis ... (see the root README)
# the services of the root README's quick start (step 2) plus delivery-service: cd delivery-service; .\mvnw spring-boot:run
# the Debezium connectors once: .\scripts\register-connectors.ps1
cd fresh-grocery; npm run dev                 # http://localhost:5173
.\scripts\simulate-delivery.ps1 -NoDeliver    # a moving partner to follow
```

| Problem | Look at |
|---|---|
| The map is grey, but markers show | The tiles cannot load: no internet, a blocked tile host, or a wrong `VITE_MAP_TILE_URL` (DevTools → Network → `png`) |
| Tiles are scattered across the page | Leaflet's CSS is missing (step 2) |
| "Waiting for the store to hand over your order…" for long | The order has no delivery: is delivery-service running, are the connectors registered (`OrderReadyForPickup` must reach it)? |
| "Waiting for Arjun's location…" | The partner has not shared yet, or their positions are refused: the partner panel shows why |
| A marker is missing, "… has no map position" | The store or the address has no coordinates (orders from before coordinates existed, or addresses saved without them) |
| The page says "Reconnecting…" | The stream broke: gateway / delivery-service logs; `curl.exe -N` the stream yourself (section 3) |
| Partner: "Location can only be shared on a secure (https) page" | Browsers give positions only to `https` pages and `localhost`. A phone opening `http://192.168.x.x:5173` is not secure: use DevTools sensors on the computer, or serve the dev site over https (a tunnel, or a dev certificate) |
| Partner: "Location access is blocked" | The site's location permission is "Block": the lock icon next to the address → Location → Allow |
| Updates are late or bunched | Something between buffers the stream: the response has `X-Accel-Buffering: no` for nginx; other proxies need buffering off for `text/event-stream` |

Where to see it working:

- **Redis Insight** (http://localhost:5540, [redis-insight.md](redis-insight.md)): key `delivery:location:<deliveryId>`
  with its TTL. The **Pub/Sub** tab → subscribe to `delivery-tracking` to watch the snapshots go by.
- **Prometheus / Grafana:** `delivery_tracking_streams` (open streams per instance),
  `delivery_tracking_location_updates_total{result="stored|skipped|refused"}` ([observability.md](observability.md)).
- **Logs:** a refused position is a `400` with its reason in delivery-service's log (Grafana → Grocery logs).

## 6. OpenStreetMap tiles: attribution and usage policy

- **The data is free; the attribution is required.** OpenStreetMap's data is under the
  [ODbL](https://www.openstreetmap.org/copyright): every map must say "© OpenStreetMap contributors", linked to
  https://www.openstreetmap.org/copyright. `MAP_CONFIG.attribution` does this, and Leaflet shows it in the corner.
  Do not hide it with CSS. A commercial tile provider has its own attribution text, which it adds to OpenStreetMap's.
- **The public tile server (`tile.openstreetmap.org`) is not for a production shop.** It is run by volunteers on donated
  servers, for light use. It has **no capacity promise and no service-level agreement**, and it blocks applications
  that use it heavily or break its [tile usage policy](https://operations.osmfoundation.org/policies/tiles/): a valid
  referer or user agent, no bulk downloading or pre-fetching, cache tiles as the server says, and attribution. It is
  fine for development and demos.
- **For production,** pick one:
  - a tile provider with a contract and a key, e.g. MapTiler, Stadia Maps, Thunderforest or Mapbox (set
    `VITE_MAP_TILE_URL` and `VITE_MAP_ATTRIBUTION`);
  - your own tile server, e.g. with OpenStreetMap data for your region and a tile-serving stack such as
    [switch2osm](https://switch2osm.org) describes. It needs its own servers and maintenance.

  Estimate the volume: a customer watching for 20 minutes loads a few dozen tiles (more when zooming and panning).
- **Automated tests do not load public tiles:** `e2e/tracking.spec.ts` blocks `tile.openstreetmap.org` and checks only
  the markers and the line.

## 7. Routes and arrival times (optional)

OpenStreetMap draws the map; it does not route. An arrival time needs a **routing service** that knows the streets.
It is **off by default**, and then nothing is guessed: the map draws a straight dashed line from the partner to the next
stop, and the page says "Arjun is about 720 m away from you (straight line)". A straight line is not an arrival time,
so none is shown.

To switch it on, point delivery-service at an OSRM-compatible server:

```powershell
$env:TRACKING_ROUTING_URL = "http://localhost:5000"     # then start delivery-service
```

delivery-service then asks
`GET {url}/route/v1/driving/{fromLon},{fromLat};{toLon},{toLat}?overview=simplified&geometries=geojson` (OSRM wants
longitude first) when a position is stored, and the answer becomes the snapshot's `route`. The page then shows "Arrives
around 10:42 (in about 7 min)" (or "At Fresh Mart around 10:35" before the pick-up) and the line along the streets. To
stay light, the route is reused until the partner has moved 150 m or it is 60 s old. A router that is down or slow (3 s
timeout) never breaks tracking: the position is stored without a route, and the problem is logged once.

**Run OSRM yourself** (Docker; the [OSRM README](https://github.com/Project-OSRM/osrm-backend#using-docker) has the
details). Take an extract of your region from [Geofabrik](https://download.geofabrik.de) (for Bengaluru: Asia → India →
the southern zone):

```powershell
mkdir osrm; cd osrm      # put the .osm.pbf file here
docker run -t -v "${PWD}:/data" ghcr.io/project-osrm/osrm-backend osrm-extract -p /opt/car.lua /data/southern-zone-latest.osm.pbf
docker run -t -v "${PWD}:/data" ghcr.io/project-osrm/osrm-backend osrm-partition /data/southern-zone-latest.osrm
docker run -t -v "${PWD}:/data" ghcr.io/project-osrm/osrm-backend osrm-customize /data/southern-zone-latest.osrm
docker run -t -p 5000:5000 -v "${PWD}:/data" ghcr.io/project-osrm/osrm-backend osrm-routed --algorithm mld /data/southern-zone-latest.osrm
```

The car profile is a fair stand-in for scooters and motorbikes. `bicycle.lua` exists for bicycle partners, but one
router serves one profile. Preparing a large region needs several GB of memory; a city or state extract is enough for a
local shop.

The **public OSRM demo server** (`router.project-osrm.org`) is for trying the API by hand, not for an application. Its
policy allows at most one request per second and makes no promises. The response format was checked against it once;
the tests use a fake OSRM (`RoutingClientTest`).

## 8. Location in the background: what a web page cannot do

A web page shares its location only **while it is open and in front**. This is a deliberate browser rule, not a bug we
can fix:

- When the phone's screen turns off, or the partner switches to another app (a call, WhatsApp, Google Maps
  navigation), mobile browsers **pause the page**: `watchPosition` stops reporting, timers stop, and no positions are
  sent. iOS Safari pauses almost at once. Android Chrome soon after, and it may also stop the tab entirely to save
  battery.
- There is no web API for background location (the old "background geolocation" ideas never shipped in browsers;
  service workers cannot read the location).
- What the web app does about it: it keeps the screen on while sharing (Screen Wake Lock API: Chrome, Edge, Safari 16.4+, Firefox 126+),
  asks the partner to keep the screen open, notices when the page was in the background and says so, and the customer's
  page shows a stale position as "delayed" instead of pretending it is current. When the page is back in front, sharing
  continues by itself.

**When a native app is needed:** as soon as partners must be tracked reliably while they use other apps or with the
screen off. That is usually true for a real delivery fleet: partners navigate with Google Maps and take calls on the way.
A native app (Android: a foreground service with a "sharing your location" notification; iOS: background location mode)
or a cross-platform one (React Native or Flutter with a background-location library) keeps measuring and sending, and
can buffer positions while offline. The backend does not change: the app sends the same `PUT .../location` with the
partner's token. Mind the stores' rules: both app stores require a clear reason and an explanation for background
location, and Android also needs the separate "Allow all the time" permission.

For a pilot or a small fleet, the web panel is enough when partners keep it open, for example on a phone holder.

## 9. Production considerations

**Security**

- Positions and streams need a valid JWT; the gateway checks the role and delivery-service checks ownership in the
  database query ([section 4](#4-who-may-see-what)). Ids in the URL are never trusted alone.
- `https` everywhere: browsers only give positions to secure pages, and tokens and positions must not travel in clear text.
- The gateway's rate limit (`default`: 20 requests per second per user) also covers `PUT .../location`. A phone sends
  one every 4–15 s. The server skips anything faster than 2 s anyway.
- The phone's position is not trusted blindly: positions from the future, older than 10 minutes, or very imprecise are
  refused. A partner who fakes their GPS (developer options, spoofing apps) cannot be detected by a web page; native
  apps can check for mock locations.
- The stream's token is in the `Authorization` header, not in the URL, so it does not end up in proxy logs.

**Privacy**

- Positions are shared only while a delivery is accepted, picked up or on its way, and only with the customer of that
  order. Sharing stops by itself at the end. The partner sees "Sharing" / "Not sharing" at all times.
- Only the last position is kept, in Redis, and it is deleted when tracking ends (at the latest 2 h after the last
  update). No trip history is stored, and positions are not written to MySQL, Kafka or the logs. The routing client logs
  only the cause of a failure, never the request URL that contains a position.
- The customer sees the partner's name, vehicle and phone during the delivery (to call them), not after it.
- If you add a trip history later (for disputes or analytics): decide the purpose, the retention period and who can see
  it, tell the partners (it is personal data under India's DPDP Act and the GDPR), and delete it on schedule.

**Scalability**

- Any number of delivery-service instances: Redis Pub/Sub delivers each snapshot to every instance, and each instance
  sends it to its own streams. No sticky sessions needed.
- Each open stream is one HTTP connection (Spring MVC async: no thread is held while it is idle). Limits per instance:
  `TRACKING_MAX_STREAMS` (delivery-service) and `GATEWAY_TRACKING_MAX_STREAMS` (gateway). Watch
  `delivery_tracking_streams`.
- Redis Pub/Sub is fire-and-forget: a snapshot sent during a Redis restart is lost, but the next position (seconds
  later) or a reconnect (which always starts with the full state) brings the page up to date. Positions written while
  Redis is down fail (`500`), and the phone sends the next one anyway.
- Proxies in front (Kubernetes Ingress, nginx, load balancers) must not buffer `text/event-stream`, and their idle/read
  timeout must be above 15 s (the keep-alive interval). nginx reads `X-Accel-Buffering: no`. With ingress-nginx, keep
  `proxy-read-timeout` at 60 s or more.
- Serve the site over **HTTP/2**: over HTTP/1.1 a browser allows only 6 connections per host, and each open tracking tab
  uses one for its stream.
- Many customers follow different orders, so the fan-out is small (one or two streams per order). At very large scale,
  a Redis channel per order, or a dedicated real-time service, keeps instances from receiving snapshots they do not need.

**Performance**

- The phone sends only useful positions (25 m / 15 s / at most every 4 s): about 4–15 requests per minute per partner,
  each a small JSON body and one Redis write.
- One snapshot (~1 KB of JSON) per change goes to the customer, not a polling request every few seconds.
- The route is computed only after 150 m or 60 s. Without a router there is no extra work at all.
- The map is loaded only on pages that show one (Leaflet + CSS: about 46 kB gzip). The map fits its view only when
  points appear, and otherwise follows the partner only when they leave the visible area. It does not redraw on every update.
- The stream is excluded from response-time panels and the SlowResponses alert (it is long on purpose).

## 10. Limitations

- **No routing by default:** a straight dashed line and a straight-line distance, no arrival time, until an OSRM server
  is configured ([section 7](#7-routes-and-arrival-times-optional)).
- **No trip history:** only the last position exists; the map does not draw the way already travelled.
- **The web panel shares only while it is open and in front** ([section 8](#8-location-in-the-background-what-a-web-page-cannot-do)).
- **Coordinates come from the seed data and the address book:** addresses saved without coordinates have no "home"
  marker; there is no geocoding (address → coordinates) yet.
- **The simulation drives straight lines,** not along roads.
- **One position per delivery:** a delivery is one partner; batching several orders on one trip is not modelled.

## 11. Tests

| Where | Test | What |
|---|---|---|
| delivery-service | `DeliveryTrackingIntegrationTest` (MySQL + Kafka + Redis containers) | The whole flow: positions stored and streamed (SSE through MockMvc, through Redis Pub/Sub), status changes on the stream, the final state after delivery, the position deleted and later positions refused; customers see only their own order, partners update only their own delivery; sharing starts at accept; refused and skipped positions |
| | `RoutingClientTest` (a fake OSRM) | Route parsing ([lon, lat] → [lat, lon]), arrival time, "NoRoute", a router that is down (logged once, without positions), routing off |
| | `GeoPointTest`, `DeliveryTest` | Distances; which statuses are tracked or over; status change detection |
| order-service | `OrderEventMapperTest` | `OrderReadyForPickup` carries the coordinates (and `null` without them) |
| api-gateway | `GatewayRoutingAndSecurityTest`, `ResilienceTest` | Roles on the tracking paths; the stream is not cut by the response timeout and has its own limits |
| fresh-grocery | `features/tracking/tracking.test.tsx` | The customer's page on the mock stream: step by step to "Delivered", the partner on the map, before pickup, lost connection, someone else's order, the button on the order page |
| | `features/delivery/tracking/liveLocation.test.tsx` | The partner's panel with a fake GPS: share and stop on delivery, too imprecise, permission blocked, auto-start, no Geolocation API |
| | `trackingTexts.test.ts`, `api/eventStream.test.ts` | Arrival texts (India time), the order step; the SSE parser |
| | `e2e/tracking.spec.ts` (Playwright, real backend) | Two browsers: the partner's phone (emulated GPS) accepts and shares, the customer sees him move, the steps, and "Delivered"; the markers are inside the visible map. Saves screenshots in `test-results/` |

```powershell
cd delivery-service; .\mvnw verify          # Docker running (Testcontainers)
cd fresh-grocery; npm run test               # Vitest, mock API
cd fresh-grocery; npx playwright test e2e/tracking.spec.ts   # the real backend must be running
```
