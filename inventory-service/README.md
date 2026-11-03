# inventory-service

Owns **stock**: how many units of each product a store has, how many are held for open orders, and when a product is running low.
Search shows the stock: product-read-service receives every change as a `StockChanged` event.

| | |
|---|---|
| Port | `8106` |
| Database | `inventory_db` (MySQL, user `inventory_user`) |
| Swagger UI | http://localhost:8106/swagger-ui.html (or the central one at http://localhost:8080/swagger-ui.html) |
| Publishes events | `StockChanged`, `LowStockDetected`, `StockReserved`, `StockReservationFailed`, `StockReleased` on `grocery.inventory.events` (outbox + Debezium) |
| Consumes events | `OrderCreated` (reserve), `OrderCancelled` / `OrderRejected` / `OrderDeliveryFailed` (release), `OrderDelivered` (commit) on `grocery.order.events` |
| Calls | product-read-service `GET /internal/products/{id}` (REST): "is this product really in the vendor's store?" |

## The stock rules (`Stock` entity)

```text
quantityOnHand     units in the store                      set by the vendor / goods received
reservedQuantity   units held for orders not delivered     reserve() / release() / commitReservation()
available          quantityOnHand - reservedQuantity       what customers can still buy
low stock          available <= lowStockThreshold          an empty shelf is low stock too
```

- Quantities are never negative and never above 1,000,000.
- The vendor cannot set `quantityOnHand` below the reserved units: those are already promised to customers.
- `reserve()` fails when not enough is available. `commitReservation()` (order delivered) removes the units from the shelf for good.
- The entity has no setters for these numbers: every change goes through a method that checks the rules.

## Events

Every change publishes **`StockChanged`** with the full current stock and `stockVersion` (the row's version), so consumers can ignore old events.
**`LowStockDetected`** is published only when the stock **becomes** low (not again while it stays low). notification-service will use it in Phase 8.

The service runs `stockRepository.flush()` before building the event: the UPDATE runs first, so `stockVersion` is already the new version.

The seed rows are inserted by SQL, so no events exist for them. `POST /internal/inventory/stock-snapshot` publishes `StockChanged` for every product again (also used by `scripts\rebuild-product-index.ps1`).

## Stock for orders (the order saga)

| Event in | What happens | Events out |
|---|---|---|
| `OrderCreated` | Check EVERY item first. All available → reserve them all and save a `StockReservation` (one per order). One short → reserve nothing. | `StockReserved` or `StockReservationFailed` (with requested and available units), plus `StockChanged` per product |
| `OrderCancelled`, `OrderRejected`, `OrderDeliveryFailed` | Give back exactly the reserved units (reservation → RELEASED) | `StockReleased`, `StockChanged` |
| `OrderDelivered` | The units leave the shelf for good: on hand and reserved both go down (reservation → COMMITTED) | `StockChanged` |

- All or nothing: an order never gets half its items.
- Two orders reserving the last units at the same moment cannot both win: the second update fails on the `Stock` row's `@Version`, the transaction rolls back, and Kafka retries it with the new numbers.
- Duplicate events are skipped (`processed_event` table, plus one reservation per order).
- Reservation events use the **orderId** as Kafka key, stock events the productId.

## The REST call to product-read-service

The first time a vendor sets the stock of a product, the service asks product-read-service for the product and checks that its `storeId` is the vendor's store:

| product-read-service answers | Result for the vendor |
|---|---|
| product of the vendor's store | stock record created |
| product of another store, or 404 | **404** "Product … not found in your store" (other stores' products look like they do not exist) |
| product with status DELETED | **400** |
| no answer within 2 s / 5 s, or an error | **503** "The product catalog is not available right now" |

A product created a moment ago may not be in product-read-service yet (search is eventually consistent), hence "try again in a few seconds" in the 404 message.
Later changes find the existing stock record by `productId` + `storeId` and do not call product-read-service again.

## Endpoints

| Method | Path | Who (enforced by the gateway) | What |
|---|---|---|---|
| GET | `/api/v1/vendor/inventory?lowStockOnly=&page=&size=&sort=` | VENDOR | My store's stock |
| GET | `/api/v1/vendor/inventory/{productId}` | VENDOR | Stock of one of my products |
| PUT | `/api/v1/vendor/inventory/{productId}` | VENDOR | Set the exact stock `{quantityOnHand, lowStockThreshold?}`; the first call creates the record |
| POST | `/api/v1/vendor/inventory/{productId}/additions` | VENDOR | Goods received `{quantity}` |
| GET | `/api/v1/admin/inventory?storeId=&lowStockOnly=` | ADMIN, SUPER_ADMIN | Stock of all stores (read only) |
| GET | `/api/v1/admin/inventory/{productId}` | ADMIN, SUPER_ADMIN | Stock of one product |
| GET | `/internal/inventory/products/{productId}` | other services only | Stock of a product (404 = no stock record) |
| POST | `/internal/inventory/stock-snapshot` | other services / developers | Publish `StockChanged` for every product again |

Vendor endpoints use the vendor's **store id** (`X-Store-Id`, from the JWT). A vendor without an approved store gets 403.
The default low-stock threshold for a new stock record is `app.inventory.default-low-stock-threshold` (5).

## Run it

```powershell
.\scripts\start-infra.ps1                        # from the repo root, once
cd inventory-service; .\mvnw spring-boot:run     # Flyway creates the tables + stock for the 17 seed products
.\scripts\register-connectors.ps1                # from the repo root, once: publishes the outbox to Kafka
Invoke-RestMethod -Method Post http://localhost:8106/internal/inventory/stock-snapshot   # once: seed stock -> search
```

Then open [http/inventory-service.http](http/inventory-service.http). Creating stock for a new product needs product-read-service (port 8105) running.
The whole flow through the gateway is in [e2e/phase5-inventory.http](../e2e/phase5-inventory.http).

## Test it

```powershell
.\mvnw verify      # Docker must be running (MySQL + Kafka via Testcontainers)
```

| Test | Kind | What it proves |
|---|---|---|
| `StockTest` | plain JUnit | Available / low / out of stock, limits, reserve, release, commit |
| `VendorInventoryServiceImplTest` | Mockito | Product ownership check, default threshold, LowStockDetected only when the stock becomes low |
| `ProductCatalogClientTest` | `MockRestServiceServer` | 404 → empty, errors → 503, extra fields ignored, correlation id forwarded |
| `VendorInventoryControllerTest` | `@WebMvcTest` | Validation, 403 without a store, 503 when the catalog is down |
| `StockRepositoryTest` | `@DataJpaTest` + MySQL container | Store and low-stock queries, one stock record per product, schema matches the entity |
| `InventoryFlowIntegrationTest` | `@SpringBootTest` + MySQL container | Set → restock → snapshot; outbox events decoded with the right `stockVersion` |
| `StockReservationServiceImplTest` | Mockito | All or nothing, missing stock record, release, nothing to release |
| `OrderEventsConsumerIntegrationTest` | `@SpringBootTest` + MySQL + Kafka containers | OrderCreated reserves, OrderCancelled releases, not enough stock reserves nothing, OrderDelivered commits |

## Configuration

| Property | Env variable | Local default |
|---|---|---|
| `server.port` | `SERVER_PORT` | `8106` |
| `spring.datasource.url` / `username` / `password` | `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:mysql://localhost:3307/inventory_db?...` / `inventory_user` / `inventory_pass` |
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `spring.kafka.properties.schema.registry.url` | `SCHEMA_REGISTRY_URL` | `http://localhost:8081` |
| `app.services.product-read-service` | `PRODUCT_READ_SERVICE_URL` | `http://localhost:8105` |
| `spring.http.clients.connect-timeout` / `read-timeout` | – | `2s` / `5s` |
| `app.inventory.default-low-stock-threshold` | – | `5` |
