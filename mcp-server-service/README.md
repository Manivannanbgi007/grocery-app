# mcp-server-service

The **tools** of the AI assistant ([plan](../docs/ai-assistant-plan.md)): the shopping assistant of the shop and the work
assistant of the vendor, delivery and admin panels. It speaks the **Model Context Protocol**
(MCP): [mcp-client-service](../mcp-client-service/README.md) connects to it, shows the tool list to the language model, and
runs the tool the model picks. Every tool reads **live data through the API gateway**, exactly like the website, and
**nothing here changes any data**.

| | |
|---|---|
| Port | `8115` (8080 in containers) |
| MCP endpoint | `POST /mcp` (Streamable HTTP, stateless) |
| Who may call it | only mcp-client-service, with the `X-MCP-API-Key` header; the gateway never routes to it |
| Calls | the API gateway (`http://localhost:8080`): public catalog, store, coupon and settings endpoints; with the user's token also a customer's `/api/v1/cart` and `/api/v1/orders`, a vendor's `/api/v1/vendor/**`, a partner's `/api/v1/delivery/**` and the admins' `/api/v1/admin/**` (read only), and the customer's cart changes (`POST /api/v1/cart/items`, `PUT` / `DELETE /api/v1/cart/items/{productId}`) |
| Database | none |
| Built with | Spring Boot 4.1, Spring AI 2.0 (`spring-ai-starter-mcp-server-webmvc`), MCP Java SDK 2.1 |

## The tools

| Tool | Gateway call | Answers |
|---|---|---|
| `searchProducts(query?, category?, maxPrice?, inStockOnly?)` | `GET /api/v1/products` | at most 5 best matches: name, pack size, price, discount, stock, store, rating |
| `getProductDetails(product)` | `GET /api/v1/products/{id}`, or the best `GET /api/v1/products?q=` match for a name | the same plus the description and "Price for 100 g, including GST" |
| `getProductReviews(product)` | the same + `/reviews` | the rating and the 3 newest reviews (no reviewer names) |
| `listCategories()` | `GET /api/v1/categories` | name and description of every category |
| `listCurrentOffers(storeName?)` | `GET /api/v1/coupons` (+ `?storeId=` per store) | every usable coupon once: code, benefit, minimum order, last day, stores |
| `getDeliveryRules()` | `GET /api/v1/platform/settings` | delivery fee, free delivery from, minimum order, support contact, a summary sentence |
| `listStores()` | `GET /api/v1/stores` | address, phone, "Open now", opening hours grouped by day |
| **Customer tools** (the logged-in customer's own data) | | |
| `getMyCart()` | `GET /api/v1/cart` with the customer's token | lines (unit price and line total), totals, coupon, delivery fee, amount to pay, the minimum order and whether the cart reaches it (also when it does: the model repeated an earlier "does not reach it yet") |
| `getMyOrders()` | `GET /api/v1/orders?size=5` with the token | the 5 newest orders: number, store, status ("On the way"), total, date |
| `getMyOrderDetails(orderNumber)` | `GET /api/v1/orders/{id}` with the token | status and latest step, lines, amounts, payment, "can still be cancelled", delivery partner |
| **Summaries to confirm** (read-only: they add, change, remove, empty, place and cancel nothing; no tool of this server changes data) | | |
| `prepareMyCartAdd(product, quantity?, unit?)` | `GET /api/v1/cart`, the product search with the token | the product to add ("Adding prepared; NOTHING is added yet. … 2 x Tomato (1 kg) (₹72) …"): its store, the line (packs and their price), and under `confirm` the productId, the packs and `newQuantity` (the cart's packs of it after the click) for the website's Confirm button. What cart-service would refuse is said before: another store's product, the stock, more than 20 packs → `REFUSED` with the reason; an unclear product or amount → `CHOOSE`; no such product → `NOT_FOUND` |
| `prepareMyCartQuantity(product, quantity, unit?)` | `GET /api/v1/cart` with the token | the cart's line as it is now and its new number of packs ("Change prepared; NOTHING is changed yet. … from 2 to 3."): store, the line, and under `confirm` the productId and the new quantity for the website's Confirm button. The same quantity → "already has 2 x …; nothing to change." (no card); an unclear amount or two matching lines → `CHOOSE`; quantity 0 → "use prepareMyCartRemoval" |
| `prepareMyCartRemoval(product)` | `GET /api/v1/cart` with the token | the cart's line to take out ("Removal prepared; NOTHING is removed yet. …"): store, the line, and under `confirm` the productId for the website's Confirm button. Two matching lines (e.g. two pack sizes) → `CHOOSE`; not in the cart or an empty cart → `NOT_FOUND` |
| `prepareMyCartClear()` | `GET /api/v1/cart` with the token | the whole cart as `getMyCart` shows it ("Emptying prepared; NOTHING is removed yet. …"), for the website's card that empties it. An empty cart → `NOT_FOUND` "The cart is empty already" |
| `prepareMyOrder(paymentMethod?, address?)` | `GET /api/v1/cart/checkout`, `/api/v1/customers/me/addresses`, `/api/v1/orders?size=1` with the token | the order the cart would be now: lines, totals, the delivery address (the default one, or "home", "work" …), the payment (asks when the customer did not say), "Order #196 was placed 3 minutes ago" when it was; under `confirm` the addressId, paymentMethod and checkoutFingerprint for the website's Confirm button. Not ready → `REFUSED` with the cart's problems in ₹; a cart that is empty or still holds just the newest order's products while that order is an online order waiting for its payment ("proceed to pay" after Confirm: order-service empties the cart a moment later, in the background) → `REFUSED` with that order as data (`UnpaidOrder`: number, amount, order-service's own `paymentUrl` for Pay Now), so nothing is ordered twice; no address → `NOT_FOUND` |
| `prepareMyOrderCancellation(orderNumber?, reason?)` | `GET /api/v1/orders/{id}` with the token; without a number `/api/v1/orders?size=5` and each one's details | the order to cancel: lines, total, status and what happens to the money ("The online payment of ₹138 will be refunded." or "Nothing was paid yet (cash on delivery)."); under `confirm` the orderId and the customer's reason for the website's Confirm button. Another customer's or an unknown order → `NOT_FOUND`; an order that left the store or was cancelled before → `REFUSED` ("Order #35 was cancelled earlier, so there is nothing to cancel."); no number and several open orders → `CHOOSE` with their numbers |
| **Vendor tools** (the logged-in vendor's own store) | | |
| `getVendorStoreToday()` | `GET /api/v1/vendor/store`, `/vendor/orders?status=…&size=1` per step, `/vendor/products?lowStockOnly=true&size=1` | approval, open now, taking orders, orders waiting at each step (new, accepted, packed, ready), products low on stock |
| `getVendorOrders(status?)` | `GET /api/v1/vendor/orders` | the 5 newest orders, of one step if asked ("new", "packed", "ready" …) |
| `getVendorLowStock()` | `GET /api/v1/vendor/products?lowStockOnly=true` | up to 10 products: "Low: 3 left" or "Sold out", hidden ones marked |
| `getVendorSales(days?)` | `GET /api/v1/vendor/reports/sales/daily`, `/top-products` | sales, orders, cancelled orders and the best day of the last 1-90 days (default 7), 5 best sellers |
| **Delivery partner tools** (the logged-in partner's own data) | | |
| `getPartnerStatus()` | `GET /api/v1/delivery/profile`, `/availability` | online (until when), KYC (and why rejected), zone, vehicle, rating |
| `getPartnerDeliveries()` | `GET /api/v1/delivery/deliveries?active=true` | up to 5 current deliveries: order number, step, store, customer name and address, cash to collect, payout (no phone numbers) |
| `getPartnerEarnings(days?)` | `GET /api/v1/delivery/earnings` | deliveries and earnings of the last 1-90 days (default 7), per day |
| **Admin tools** (admins and the super admin) | | |
| `getAdminOverview()` | `GET /api/v1/admin/reports/dashboard` + `/admin/stores`, `/delivery-partners`, `/deliveries` with `size=1` | today's numbers (share of active customers written out) and the counts of waiting work |
| `getAdminWaitingWork()` | `GET /api/v1/admin/stores?status=PENDING_APPROVAL`, `/delivery-partners?kycStatus=PENDING`, `/deliveries?status=WAITING_FOR_PARTNER` | up to 5 of each by name, with the totals |
| `getAdminOrders(status?)` | `GET /api/v1/admin/orders` | the 5 newest orders of all stores, of one step if asked |
| `getAdminSales(days?)` | `GET /api/v1/admin/reports/sales/daily`, `/top-stores`, `/top-products` | the platform's sales of the last 1-90 days (default 7), 5 top stores, 5 best sellers |
| **Notification tools** (every logged-in user's own notifications: customers, vendors, delivery partners, admins) | | |
| `getMyUnreadNotificationCount()` | `GET /api/v1/notifications/unread-count` with the user's token | how many are unread (the bell icon's number) |
| `getMyNotifications(unreadOnly?, topic?)` | `GET /api/v1/notifications?unreadOnly=…` with the token (the newest 50 for a topic) | the 5 newest: title, text (amounts as ₹), unread or read, when; how many there are; `topic` keeps orders, deliveries, payments, store or account |

Every tool answers the same shape (`tool/ToolResult`):

```json
{ "status": "OK", "message": "Found 1 product.", "data": [ { "name": "Amul Butter", "price": "₹56", "stock": "Only 3 left", "store": "Fresh Mart" } ] }
{ "status": "NOT_FOUND", "message": "No product of the shop matches \"iphone 15\"." }
```

| Status | Meaning (explained to the model in mcp-client-service's system prompt) |
|---|---|
| `OK` | the data is there |
| `NOT_FOUND` | nothing matches: the assistant must say so, never guess |
| `INVALID_INPUT` | wrong arguments; the message says how to call the tool correctly |
| `SERVICE_UNAVAILABLE` | a service did not answer: "please try again later" |
| `LOGIN_REQUIRED` | a tool for a user's own data without a valid login (no token, expired, or the account may not see it): "please log in again" |
| `REFUSED` | a cart tool: the shop said no to the change ("Tomato is out of stock", a cart of another store …); the message says why |
| `CHOOSE` | a cart tool: the customer must decide first, which product (`data.options`) or how much ("1 kg (1 pack) or 2 kg (2 packs)?"); the model asks and changes nothing |

### Why the tools look like this (hallucination prevention)

- **Few tools, clear descriptions** (`@McpTool(description = ...)`): what the tool does and *when* to use it. Small models pick
  the right tool much more reliably when the list is short and the descriptions say when to use each one.
- **Prices as texts** (`"₹56"`, `"₹28.50"`), stock and rating as sentences (`"Only 3 left"`, `"4.5 out of 5 (2 reviews)"`):
  the model copies them instead of calculating, and mcp-client-service can check that every ₹ amount in an answer came
  from a tool. Lines of a cart or order have `unitPrice` **and** `lineTotal`: with one field called "price" the model
  took the line total for the price of one pack.
- **Short results** (at most 5 products, 3 reviews, 5 orders) with only the fields an answer needs; no addresses or
  phone numbers of customers.
- **Tools never throw.** Wrong arguments, "nothing found", "service down" and "please log in" are statuses with a clear
  message (`tool/ToolRunner` logs every call: tool, status, milliseconds).
- **Names instead of ids:** `getProductDetails` and `getProductReviews` take a product name *or* a productId. In a
  follow-up ("is it in stock?") a small model repeats the name from the conversation reliably, but it does not
  remember ids (only the texts of earlier answers are kept) and would guess one.
- **Loose names, exact data:** "fruit" finds the category Fruits and "green basket store" the store Green Basket, but the
  data itself always comes from the services.
- Every tool is marked read-only in MCP (`readOnlyHint = true`, `destructiveHint = false`): no tool changes data.
  mcp-client-service offers a tool that is not marked read-only to nobody, so a new tool that changes data can never
  reach a user by mistake.

### How the cart tools change only what the customer asked for

- **Whose cart:** like the customer tools, with the customer's own token (below). cart-service finds the cart by the user
  id in that token; no tool takes a user id, a price or a total.
- **Which product, decided in code** (`tool/ProductChoice`): the model passes the customer's words. A product counts only
  when its name is just those words, singular or plural alike, brand words may be left out ("tomatoes" is Tomato,
  "butter" is Amul Butter, but "milk" is not Amul Taaza Toned Milk). A pack size or a store in the words narrows it down
  ("amul butter 500 g", "tomato from Green Basket"); with a cart, only the cart's store counts. Anything else is
  `CHOOSE` with the products that have all the words, or `NOT_FOUND`. Preparing a new quantity or a removal looks in
  the cart's own lines.
- **Spelling mistakes** (`tool/Spelling`, 2026-10-31): when nothing fits the words as they are written, the same rules
  run again among the search box's suggestions (`GET /api/v1/products/suggestions`, which allows small typos), with each
  word allowed a small mistake: the first letter right, 1 letter wrong in a word of 3-5 letters, 2 in a longer one
  ("tomoto" and "tomatto" are Tomato, "poteto" is Potato, "orgnic carrot" is Organic Carrot). One such product: that
  one; several: `CHOOSE`; a product with more words ("bananna": Banana Robusta) is only offered. Only catalog products
  are ever chosen. `searchProducts` offers the similar names too when its search finds nothing.
- **No amount, no guess** (2026-10-31): `prepareMyCartAdd` without a quantity finds the product and answers `CHOOSE`
  "how much?", with the product as `data.amountFor` (id, name, pack size, price). mcp-client-service keeps it with the
  conversation, so the customer's answer ("3 kg") adds exactly that product. One pack must be said ("1", "one", "a").
- **How many packs, decided in code** (`tool/PackCount`): "2 kg" of a 500 g pack is 4 packs, "12 eggs" of a 6-pack is 2.
  Part of a pack, a weight for a product sold in pieces, or more than 20 packs is a question (`CHOOSE`), never a guess.
- **The rules stay in cart-service** (stock, at most 20 per product, one store per cart, prices, totals) and are
  checked again on the click. `prepareMyCartAdd` checks them before too, so a card shows only an addition that can
  work; what cannot is `REFUSED` with the reason.
- mcp-client-service prepares an addition only when the customer's own message asks for a change, only once per
  question, and not while the customer has a `CHOOSE` question to answer (see its README).

### Why no tool changes the cart, places or cancels an order

`prepareMyOrder` only reads and returns the summary. mcp-client-service turns that result (never the model's text) into
a card with a Confirm button under the answer, and only the customer's click places the order: the browser calls
`POST /api/v1/orders` itself, with the customer's login, the summary's `addressId`, `paymentMethod` and
`expectedCheckoutFingerprint`, and an `Idempotency-Key`. order-service refuses the order when the cart changed since the
summary, and never places two orders for one key (see its README). `prepareMyOrderCancellation` works the same way:
the card's Confirm calls `POST /api/v1/orders/{id}/cancel`, where order-service checks again whose order it is and
whether it can still be cancelled. The cart's prepare tools too: the cards' Confirm calls cart-service's
`POST /api/v1/cart/items` or `PUT /api/v1/cart/items/{productId}` up to the card's new total (`prepareMyCartAdd`, so
a second click adds nothing twice), `DELETE /api/v1/cart/items/{productId}` (`prepareMyCartRemoval`),
`PUT /api/v1/cart/items/{productId}` with the new total (`prepareMyCartQuantity`) or `DELETE /api/v1/cart`
(`prepareMyCartClear`), which only ever change the cart of the token's owner. The model has no tool that changes the
cart, places, pays or cancels. ("Can you remove Harpic Toilet Cleaner from my cart?" used to run a removing tool at
once, and "add 2 kg tomatoes" an adding one; now nothing changes before the customer's click.)

### How the tools for a user's own data see only that user's data

```text
browser --JWT--> gateway --Authorization + X-User-Id/Role--> mcp-client-service --X-User-Token (+ X-MCP-API-Key)--> /mcp
   McpTransportConfig: copies the header into the MCP transport context of the request
   CustomerTools / VendorTools / PartnerTools / AdminTools: get it as an McpTransportContext parameter (LoginToken; Spring
   AI fills it in, it is NOT a parameter the model sees)
   CustomerClient / VendorClient / PartnerClient / AdminClient --Authorization: Bearer <token>--> gateway (checks it and
   the role again) --> the services
```

- The token never goes into the prompt, so the model can neither see it nor ask for anybody else's data: every call is
  made with the chatting user's own token, and the services only return what that user sees on the website (a vendor's
  own store, a partner's own deliveries; the admin endpoints only answer admins).
- mcp-client-service sends the token for every logged-in user and offers each role only its own tools, by the start of
  the name: `getMy…` customers, `getVendor…` vendors, `getPartner…` delivery partners, `getAdmin…` admins. Without a
  token the tools answer `LOGIN_REQUIRED`; an expired token (401) or a role the endpoint refuses (403, also a vendor who
  logged in before the store was approved) does too.
- Another customer's order number gives `NOT_FOUND` (order-service answers 404). The token is never logged.

## How it is built

```text
com.grocery.mcpserver
├── config/      GatewayProperties, McpSecurityProperties, RestClientConfig (timeouts), SecurityConfig, CacheConfig,
│                McpTransportConfig (the /mcp endpoint + the X-User-Token context extractor), ClockConfig (India time)
├── security/    ApiKeyAuthenticationFilter  (X-MCP-API-Key, compared in constant time)
├── client/      CatalogClient, ShopInfoClient, CustomerClient, VendorClient, PartnerClient, AdminClient (all only read)
│                (+ dto/ records with only the fields we need), GatewayCall
├── tool/        CatalogTools, ShopInfoTools, CustomerTools, CartActionTools, OrderPrepareTools, VendorTools, PartnerTools,
│                AdminTools (@McpTool methods), ProductChoice and PackCount (the cart tools' product and packs), ToolResult,
│                ToolRunner, ToolInputs, Formats, LoginToken, StoreNames, DateRange ("the last N days"),
│                OrderStatusWords ("new" -> CONFIRMED …)
│   └── result/  what the tools return: ProductSummary, ProductDetails, ReviewOverview, OfferInfo, DeliveryRules, StoreInfo,
│                CartSummary, CartChange, OrderPreview, OrderOverview, OrderDetails, StoreToday, StockLine, SalesSummary, PartnerStatus,
│                PartnerDelivery, PartnerEarnings, AdminOverview, WaitingWork …
├── filter/      CorrelationIdFilter (the id is passed on to the gateway)
└── exception/   ExternalServiceException, LoginRequiredException, ActionRefusedException
```

**Flow of one tool call:** `POST /mcp` → `ApiKeyAuthenticationFilter` → Spring AI MCP server (finds the `@McpTool` method) →
`ToolRunner` → the tool checks its arguments → `CatalogClient` / `ShopInfoClient` / a client with the user's token → API gateway →
`ToolResult` as JSON text.

**Stateless Streamable HTTP** (`spring.ai.mcp.server.protocol: STATELESS`): every `POST /mcp` stands on its own (no MCP session),
so several instances can run behind a load balancer without sticky sessions. It cannot send requests back to the client
(sampling, elicitation), which these tools do not need.

**Cache:** categories, stores, coupons and the platform settings are kept for 1 minute (Caffeine): fewer gateway calls.
Product searches, carts, orders and everything read with a user's token are never cached.

## Run it

```powershell
# The API gateway and the catalog services must run (see the root README): api-gateway, product-read-service,
# product-service (reviews), store-service, cart-service (coupons, carts), platform-service; order-service for orders.
cd mcp-server-service
.\mvnw spring-boot:run
```

Then try `http/mcp-server-service.http` (the API key, `initialize`, `tools/list`, every tool, and the customer tools with
customer1's token). The container image (non-root, profile `prod`, needs `MCP_API_KEY`) is built and run as shown at the
top of the `Dockerfile`. A graphical alternative is the MCP Inspector: `npx @modelcontextprotocol/inspector`, transport
"Streamable HTTP", URL `http://localhost:8115/mcp`, header `X-MCP-API-Key` with the local key.

## Test it

```powershell
.\mvnw verify     # 139 tests, no Docker and no other service needed
```

| Test | What it checks |
|---|---|
| `OrderPrepareToolsTest` | the order summary: the cart's amounts, the default or a named address ("office"), a question when the payment is missing, the cart's problems in ₹, no address, an order placed a moment ago, nothing read without a login; the cancellation summary: a cash or a paid online order, another customer's order, a delivered or cancelled one, no number with one or several open orders, a long reason cut to 250 characters |
| `McpServerIntegrationTest` | the whole server on a random port with a **real MCP client** (Streamable HTTP): 29 tools listed, a tool call, the customer's and a vendor's token reaching their tools (and not being a model parameter; the vendor's status word is the only parameter), `LOGIN_REQUIRED` without it, the cart tools with the customer's token and only `product`, `quantity`, `unit` as parameters (`prepareMyCartRemoval`: only `product`; `prepareMyCartClear`: none), read-only hints on all tools, 401 without / with a wrong key, health open, everything else closed |
| `CartActionToolsTest`, `ProductChoiceTest`, `PackCountTest` | the cart tools with mocked clients: amounts into packs (kg, g, pieces, dozen), questions instead of guesses (part of a pack, "milk", two pack sizes), the cart's store first, another store's product explained, cart-service's "no" passed on, the 20-pack limit, nothing changed without a login; a removal, a new quantity and emptying only prepared (the cart's line, its productId and the new quantity; "which one?" for two matching lines; the same quantity, an unclear amount, zero, not in the cart, an empty cart) |
| `CatalogToolsTest`, `ShopInfoToolsTest`, `CustomerToolsTest`, `VendorToolsTest`, `PartnerToolsTest`, `AdminToolsTest` | each tool with mocked clients: results, `NOT_FOUND` messages, argument checks (status words, 1-90 days), India dates for "today", "service down", "please log in", a store not approved yet, nothing waiting |
| `CatalogClientTest`, `CustomerClientTest`, `PanelClientsTest` | the gateway calls with `MockRestServiceServer`: filters in the URL, "&" in the search text encoded, the user's token sent, real gateway answers read, 401/403 → login required, 404 → empty, 5xx → `ExternalServiceException` |
| `FormatsTest` | ₹ amounts, pack sizes, India dates, opening hours grouping |

## Configuration

| Setting | Environment variable | Local default | Notes |
|---|---|---|---|
| `server.port` | `SERVER_PORT` | `8115` | 8080 in containers |
| `app.gateway.url` | `API_GATEWAY_URL` | `http://localhost:8080` | Kubernetes: `http://api-gateway` |
| `app.security.mcp-api-key` | `MCP_API_KEY` | a local key in `application-local.yml` | **required** outside the local profile, at least 32 characters, the same value as in mcp-client-service |
| `spring.http.clients.connect-timeout` / `read-timeout` | | `2s` / `5s` | gateway calls |
| `spring.ai.mcp.server.request-timeout` | | `20s` | one MCP request |
| `spring.cache.caffeine.spec` | | `maximumSize=200,expireAfterWrite=1m` | |

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `401 A valid X-MCP-API-Key header is required` | Header missing or different from `MCP_API_KEY`. Both services must use the same key. A wrong key is logged as a warning (without the key itself). |
| The service does not start: `app.security.mcpApiKey ... set the environment variable MCP_API_KEY` | Outside the local profile (for example in Docker, where the profile is `prod`) `MCP_API_KEY` is required and must have at least 32 characters. |
| Three `WARN ... is not eligible for getting processed by all BeanPostProcessors` lines at start-up | They come from Spring AI's `@McpTool` scanner and are harmless. |
| `mvnw verify` fails with `Unable to rename '...jar' to '...jar.original'` | Windows locks the jar while it runs: stop the running service (or run a copy of the jar) and build again. |
| Every tool answers `SERVICE_UNAVAILABLE` | The gateway (or the service behind it) is down, or answers 429 (rate limit). Check `http://localhost:8080/actuator/health` and the WARN line `Tool <name>: ...` in the log. |
| The tools for a user's own data always answer `LOGIN_REQUIRED` | No `X-User-Token` header (mcp-client-service sends it for logged-in users only; an older mcp-client-service sent, and an older mcp-server-service reads, `X-Customer-Token`: run both services from the same version), the token expired (15 minutes; the website renews it, other clients call `POST /api/v1/auth/refresh`), or the role may not read that endpoint. A vendor whose store was approved after the login gets the store id with the next token renewal (or logs in again). A version mismatch looks like this: `audience=CUSTOMER` in mcp-client-service's log, `Tool getMyCart -> LOGIN_REQUIRED in 0 ms` here (seen on 2026-10-27: a customer was asked to log in although logged in). |
| `searchProducts` finds nothing for a product that exists | product-read-service (Elasticsearch) learns about new products a second later through Debezium; check the connectors (`scripts\register-connectors.ps1`). |
| `tools/list` shows fewer than 21 tools | A tool class failed to register: look for `Registered tools: 21` at start-up and for errors from `org.springframework.ai.mcp`. Tool parameters need the `-parameters` compiler flag (set in `pom.xml`). |
| A request to `/mcp` from a browser fails | MCP needs `Content-Type: application/json` and `Accept: application/json, text/event-stream`. |
