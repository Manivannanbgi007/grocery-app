# cart-service

Owns the **shopping cart** and **coupons**. It also prepares the **checkout summary** that order-service turns into an order.

| | |
|---|---|
| Port | `8107` |
| Database | `cart_db` (MySQL, user `cart_user`) |
| Swagger UI | http://localhost:8107/swagger-ui.html (or the central one at http://localhost:8080/swagger-ui.html) |
| Publishes events | none |
| Consumes events | `OrderCreated` (remove the ordered products, count the coupon use), `OrderCancelled`, `OrderRejected`, `OrderDeliveryFailed` (the coupon use no longer counts) on `grocery.order.events`; `PlatformSettingsChanged` (delivery fee, free delivery amount, minimum order) on `grocery.platform.events` |
| Calls | product-read-service `GET /internal/products/{id}` and `GET /internal/products?ids=...` (REST): current price, status and stock |

## The cart rules (`Cart` entity)

- **One store per cart.** The first product decides the store. A product of another store gives **409**, unless the customer adds it with `replaceCart=true` (the old cart is emptied first).
- 1 to 20 units per product, at most 50 different products.
- Each item keeps a copy of the product's name and prices from when it was added. **Checkout refreshes them** with today's catalog values, so an order always uses current prices.
- Emptying the cart (or removing its last item) also removes the store and the coupon.

Adding a product calls product-read-service: the product must be ACTIVE and have enough available units. That is a first check only (the stock number in search can be a second old); the real guarantee comes when the order reserves the stock in inventory-service.

**After an order is placed** (`OrderCreated`), the ordered products leave the cart; products the customer added in the meantime stay. The coupon use is recorded in `coupon_redemptions` (one row per order) and counts for the usage limits. If the order ends without a delivery (`OrderCancelled`, `OrderRejected`, `OrderDeliveryFailed`), the use is removed again, so for example WELCOME50 can be used on the next order.

## Money

All prices include tax (like the price printed on the pack).

```text
itemsTotal     = sum of sellingPrice x quantity            (product discounts already applied)
couponDiscount = from the coupon, never more than itemsTotal
deliveryFee    = 30.00, free from an items total of 299.00  (pricing_settings)
payableAmount  = itemsTotal - couponDiscount + deliveryFee
taxIncluded    = the GST inside the prices, for information
```

An order needs an items total of at least **99.00**. These three numbers are **platform settings**: the super admin changes them in platform-service, cart-service keeps a copy in its `pricing_settings` table (updated by `PlatformSettingsChanged`) and `DatabasePricingRulesProvider` reads it for every calculation.

## Coupons (`Coupon` entity)

| | Platform coupon | Store coupon |
|---|---|---|
| Created by | an admin (`/api/v1/admin/coupons`) | the store's vendor (`/api/v1/vendor/coupons`) |
| `storeId` | null | the vendor's store |
| Valid in | every store | only that store |

A coupon is `PERCENTAGE` (optionally capped by `maxDiscountAmount`) or `FLAT`. It can be used when it is **active**, inside **validFrom–validUntil**, the cart is from the right store, the items total reaches **minOrderAmount**, the **usageLimit** is not used up, and the customer has not used it **perCustomerLimit** times yet. Codes are stored in upper case; customers can type them in any case.

The coupon is checked again every time the cart is shown: if the cart drops below the minimum, the discount becomes 0 and `couponMessage` says why. Checkout lists it as a problem.

## Endpoints

| Method | Path | Who (enforced by the gateway) | What |
|---|---|---|---|
| GET | `/api/v1/cart` | CUSTOMER | My cart with totals |
| POST | `/api/v1/cart/items?replaceCart=` | CUSTOMER | Add a product `{productId, quantity}` |
| PUT · DELETE | `/api/v1/cart/items/{productId}` | CUSTOMER | Change the quantity · remove the product |
| DELETE | `/api/v1/cart` | CUSTOMER | Empty my cart |
| PUT · DELETE | `/api/v1/cart/coupon` | CUSTOMER | Apply a coupon `{code}` · remove it |
| GET | `/api/v1/cart/checkout` | CUSTOMER | Checkout page: today's prices, `readyForCheckout`, the list of `problems`, and the `checkoutFingerprint` (16 hex characters of a SHA-256 over the store, every product with its quantity and price, the coupon and the totals: any change gives another one; order-service compares it with the summary the customer confirmed in the chat, and uses it to notice the same cart ordered twice) |
| GET | `/api/v1/coupons?storeId=` | public | Offers usable right now (platform + that store's coupons) |
| GET · POST | `/api/v1/vendor/coupons` | VENDOR | My store's coupons · create one |
| GET · PUT | `/api/v1/vendor/coupons/{id}` | VENDOR | One of my coupons · update it |
| PATCH | `/api/v1/vendor/coupons/{id}/active` | VENDOR | Switch it on or off `{active}` |
| GET · POST | `/api/v1/admin/coupons?storeId=&platformOnly=` | ADMIN, SUPER_ADMIN | All coupons · create a platform coupon |
| GET · PUT | `/api/v1/admin/coupons/{id}` | ADMIN, SUPER_ADMIN | Any coupon · update a platform coupon |
| PATCH | `/api/v1/admin/coupons/{id}/active` | ADMIN, SUPER_ADMIN | Switch any coupon on or off |
| GET | `/internal/carts/{customerUserId}/checkout` | other services only | Checkout summary (order-service) |

The checkout summary collects every problem instead of failing on the first one (unavailable or out-of-stock products, an unusable coupon, the minimum order value), so the customer can fix everything at once. order-service only places an order when `readyForCheckout` is true.

## Run it

```powershell
.\scripts\start-infra.ps1                   # from the repo root, once
cd cart-service; .\mvnw spring-boot:run     # Flyway creates the tables + 6 coupons + customer1's cart
```

product-read-service (port 8105) must run for adding products and checkout. Then open [http/cart-service.http](http/cart-service.http).
The flow through the gateway is in [e2e/phase6-cart.http](../e2e/phase6-cart.http).

## Test it

```powershell
.\mvnw verify      # Docker must be running (MySQL via Testcontainers)
```

| Test | Kind | What it proves |
|---|---|---|
| `CartTest` | plain JUnit | One store per cart, quantity limits, an empty cart forgets store and coupon |
| `CouponTest` | plain JUnit | Percentage cap, flat amount, validity dates, store, minimum, usage limits, settings checks |
| `CartPricingCalculatorTest` | plain JUnit | Delivery fee, free delivery, product savings, included tax, minimum order |
| `CartServiceImplTest`, `CheckoutServiceImplTest` | Mockito | Stock and status checks, `replaceCart`, coupon re-check, today's prices at checkout, every problem listed |
| `ProductCatalogClientTest` | `MockRestServiceServer` | One product, several products in one call, 404 and 503 |
| `CartControllerTest` | `@WebMvcTest` | Validation, 409 for another store, 401 without the gateway header |
| `CouponRepositoryTest` | `@DataJpaTest` + MySQL container | Offers query (store + platform, no expired or switched-off coupons) |
| `CartFlowIntegrationTest` | `@SpringBootTest` + MySQL container | Vendor coupon → cart → coupon → checkout → admin switches the coupon off |
| `OrderEventsConsumerIntegrationTest` | `@SpringBootTest` + MySQL + Kafka containers | OrderCreated removes only the ordered products and counts the coupon; OrderCancelled frees it; PlatformSettingsChanged changes the pricing rules |

## Configuration

| Property | Env variable | Local default |
|---|---|---|
| `server.port` | `SERVER_PORT` | `8107` |
| `spring.datasource.url` / `username` / `password` | `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:mysql://localhost:3307/cart_db?...` / `cart_user` / `cart_pass` |
| `app.services.product-read-service` | `PRODUCT_READ_SERVICE_URL` | `http://localhost:8105` |
| `spring.http.clients.connect-timeout` / `read-timeout` | – | `2s` / `5s` |
