# product-service

The **write side of the catalog** (CQRS): categories and products are created and changed here, and stored in MySQL, the source of truth.
**Every GET request for products and categories is served by [product-read-service](../product-read-service/README.md)** from Elasticsearch.

| | |
|---|---|
| Port | `8104` |
| Database | `product_db` (MySQL, user `product_user`) |
| Swagger UI | http://localhost:8104/swagger-ui.html (or the central one at http://localhost:8080/swagger-ui.html) |
| Images | saved to `./uploads`, served at `/media/**` (public, through the gateway) |
| Events | none published by code. **Debezium captures the `products` and `categories` tables** (`debezium/product-cdc-connector.json`) |
| Calls (REST) | order-service `/internal/orders/{id}` (was the reviewed product really delivered to this customer?) |

## How a change reaches search

```text
PUT /api/v1/vendor/products/5 ─► product-service ─► MySQL product_db.products (version 3 -> 4)
                                                          │ binlog
                                                          ▼
                     Debezium ─► Kafka topic cdc.product_db.products (key "5", Avro row)
                                                          ▼
                     product-read-service ─► Elasticsearch "products" (document 5, version 4)
```

This usually takes about a second. Search results are **eventually consistent** with MySQL.

## Rules (in the `Product` entity)

- **Multi-tenant:** every product has a `storeId`. A vendor only ever finds their own products (`findByIdAndStoreIdAndStatusNot`); another store's product id gives a 404.
- **Pricing:** `sellingPrice = price - price × discount%`, always calculated by `updatePricing()` and never set directly.
- **Status:** a vendor switches between ACTIVE and INACTIVE. An admin can **block** a product; the vendor cannot undo that. Delete is a soft delete (status DELETED).
- **SKU:** unique within a store (two stores may use the same SKU).
- At most 5 images per product (JPEG, PNG or WEBP, max 2 MB each).
- New products only in active categories. Categories are global and managed by admins.

## Reviews

A customer reviews a product of one of their **delivered** orders: 1–5 stars and a comment, once per product and order.
product-service asks order-service for the order (theirs? DELIVERED? contains the product?) **before** opening a transaction.
The product keeps `rating_average` and `rating_count` of its visible reviews; because they are columns of `products`,
**Debezium copies them to search** like every other product change (no extra event). An admin can hide a review; the rating is
recalculated without it. Publicly only the first name and an initial are shown ("Priya S.").

## Endpoints

| Method | Path | Who | What |
|---|---|---|---|
| POST | `/api/v1/vendor/products` | VENDOR (with approved store) | Add a product |
| PUT | `/api/v1/vendor/products/{id}` | VENDOR | Update details and price |
| PATCH | `/api/v1/vendor/products/{id}/status` | VENDOR | ACTIVE / INACTIVE |
| DELETE | `/api/v1/vendor/products/{id}` | VENDOR | Soft delete |
| POST / DELETE | `/api/v1/vendor/products/{id}/images` | VENDOR | Upload (multipart field `image`) / remove (`?imageUrl=`) |
| POST | `/api/v1/admin/products/{id}/block` · `/unblock` | ADMIN, SUPER_ADMIN | Moderation |
| POST / PUT | `/api/v1/admin/categories` · `/{id}` | ADMIN, SUPER_ADMIN | Create / update a category |
| PATCH | `/api/v1/admin/categories/{id}/status` | ADMIN, SUPER_ADMIN | Show / hide a category |
| GET | `/media/**` | public | Product images |
| POST | `/api/v1/products/{id}/reviews` | CUSTOMER | Review a product of a delivered order `{orderId, rating, comment}` |
| GET | `/api/v1/products/{id}/reviews` | public | Visible reviews, newest first |
| GET | `/api/v1/vendor/reviews?productId=&rating=` | VENDOR | Reviews of my store's products |
| GET / PATCH | `/api/v1/admin/reviews?storeId=&productId=&rating=&status=` · `/{id}/hide` · `/{id}/show` | ADMIN, SUPER_ADMIN | Moderation |

Vendors are identified by **`X-Store-Id`**, which the gateway takes from the JWT. A vendor without an approved store gets a 403.

## Run it

```powershell
cd product-service; .\mvnw spring-boot:run      # creates the tables + 5 categories and 17 sample products
.\scripts\register-connectors.ps1                # from the repo root, once: starts the CDC connector
```

Then open [http/product-service.http](http/product-service.http). To see the whole read/write loop, use [e2e/phase4-catalog-cqrs.http](../e2e/phase4-catalog-cqrs.http).

## Test it

```powershell
.\mvnw verify      # Docker must be running (MySQL via Testcontainers)
```

| Test | Kind | What it proves |
|---|---|---|
| `ProductTest` | plain JUnit | Selling price, status rules (admin block wins), image limit |
| `SlugUtilsTest` | plain JUnit | "Dairy & Eggs" → `dairy-eggs` |
| `VendorProductServiceImplTest` | Mockito | Store scope, active category, unique SKU per store |
| `VendorProductControllerTest` | `@WebMvcTest` | 403 without a store, validation errors, missing upload file |
| `ProductFlowIntegrationTest` | `@SpringBootTest` + MySQL container | Category → product → price → image (JSON column + `/media`) → other store 404 → block → delete |
| `ProductReviewTest` | plain JUnit | Public name, 1–5 stars, hide/show, rating rounded to one decimal |
| `ReviewFlowIntegrationTest` | `@SpringBootTest` + MySQL container (order-service mocked) | Delivered → reviewed once, not delivered → 400, rating follows the visible reviews, vendor sees own store only |

## Configuration

| Property | Env variable | Local default |
|---|---|---|
| `server.port` | `SERVER_PORT` | `8104` |
| `spring.datasource.url` / `username` / `password` | `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:mysql://localhost:3307/product_db?...` / `product_user` / `product_pass` |
| `app.media.upload-dir` | `MEDIA_UPLOAD_DIR` | `./uploads` |
| `app.media.public-base-url` | `MEDIA_PUBLIC_BASE_URL` | `http://localhost:8080/media` (through the gateway) |
| `app.services.order-service` | `ORDER_SERVICE_URL` | `http://localhost:8108` |
