# product-read-service

The **read side of the catalog** (CQRS). It answers **every GET** for products and categories from **Elasticsearch**: full-text search, filters, sorting and paging.
It has **no MySQL database**. Its data is a copy of `product_db`, kept up to date from Debezium change events,
plus the stock of every product from inventory-service's `StockChanged` events.

| | |
|---|---|
| Port | `8105` |
| Store | Elasticsearch: the aliases `products`, `categories` and `product_stock`, each pointing to a versioned index (`products_v1` ...) with a strict mapping from `src/main/resources/elasticsearch` |
| Swagger UI | http://localhost:8105/swagger-ui.html (or the central one at http://localhost:8080/swagger-ui.html) |
| Consumes | `cdc.product_db.products`, `cdc.product_db.categories` (Debezium rows), `grocery.inventory.events` (only `StockChanged`); all read as Avro `GenericRecord` |

## How the copy stays correct

| Concern | How it is handled |
|---|---|
| **Duplicates and replays** | Each document is written with the MySQL row's `version` (Elasticsearch `EXTERNAL_GTE` versioning). An older or repeated change can never overwrite newer data; `saveIfNotStale` skips it. |
| **Order** | The Kafka key is the row id, so all changes of one product arrive in order. |
| **Temporary failures** | If Elasticsearch is down, the change is retried 10 times over about 3 minutes, then copied to `<topic>.DLT`. |
| **Deletes** | A product with status DELETED, or a row deleted in MySQL (`__deleted=true`), is removed from the index. |
| **Category name** | Products store their category's name so they can be found by it. When a category changes, its products are updated too. |
| **Stock** | `StockChanged` is stored in `product_stock` with its own version (`stockVersion`), then copied onto the product (`availableQuantity`, `inStock`, `lowStock`) keeping the product's version. If the stock arrives before the product, the product picks it up when it is indexed. A product without a stock record is **not in stock**. |
| **One at a time** | One listener with one thread reads all three topics, so a product change and its stock change are never applied at the same moment (one could otherwise overwrite the other). |
| **Rebuild** | The cdc topics are compacted (they keep the latest version of every row), so `scripts\rebuild-product-index.ps1` can recreate the indexes at any time. The inventory topic is not compacted, so the script also asks inventory-service to publish the current stock again. |

The column types Debezium sends (decimals as text, timestamps as epoch millis, BOOLEAN as `1`/`0`) are handled in [`AvroFields`](src/main/java/com/grocery/productread/util/AvroFields.java).

## Endpoints

| Method | Path | Who | What |
|---|---|---|---|
| GET | `/api/v1/products?q=&categoryId=&storeId=&brand=&minPrice=&maxPrice=&inStockOnly=&sort=&page=&size=` | public | Search ACTIVE products (`inStockOnly=true` hides sold-out ones) |
| GET | `/api/v1/products/suggestions?q=&categoryId=&size=` | public | The search box's suggestions while typing (ACTIVE, best first, `size` 1–10, default 6): partly typed last word (`toma` → Tomato), words in any order, small typos; one Elasticsearch query on the existing fields |
| GET | `/api/v1/products/{id}` | public | One ACTIVE product |
| GET | `/api/v1/categories` · `/{id}` | public | Active categories in home-page order |
| GET | `/api/v1/vendor/products?q=&status=&categoryId=&lowStockOnly=` · `/{id}` | VENDOR | My store's products, every status, with their stock |
| GET | `/api/v1/admin/products?q=&storeId=&categoryId=&status=&lowStockOnly=` · `/{id}` | ADMIN, SUPER_ADMIN | All products |
| GET | `/api/v1/admin/categories` | ADMIN, SUPER_ADMIN | All categories, also inactive ones |
| GET | `/internal/products/{id}` | other services | Any product |
| GET | `/internal/products?ids=1,2,3` | other services | Up to 100 products in one call (cart checkout); unknown ids are left out |
| GET / POST | `/internal/catalog/indexing`, `/pause`, `/resume` | `scripts\reindex-products.ps1` | The Kafka listener's state; pause it while an index is copied, resume it after |

`sort`: `RELEVANCE` (default; with no search text it sorts by name), `PRICE_LOW_TO_HIGH`, `PRICE_HIGH_TO_LOW`, `NAME`, `NEWEST`, `RATING` (best rated first). `size` is at most 100.
The search text matches the name (strongest), brand, category name and description.
Every product in a response has `availableQuantity`, `inStock` and `lowStock` ("only a few left"), and `ratingAverage` / `ratingCount` (from product-service's reviews, copied by Debezium with the product row).

**Indexes** ([docs/resilience.md](../docs/resilience.md#search-indexes-elasticsearch)): `SearchIndexes` creates
`products_v1` ... and their aliases at start-up, from the mapping files in `src/main/resources/elasticsearch`. The
mappings are strict: **a new field in a document class needs a line in its mapping file too** (it is added to the live
index at the next start); a field with another type needs a new index version (`scripts\reindex-products.ps1`).

## Run it

```powershell
cd product-read-service; .\mvnw spring-boot:run
```

On its first start it reads all its topics from the beginning and fills Elasticsearch, so product-service and its connector must have run at least once.
For stock in search, inventory-service must have published its stock once (`POST http://localhost:8106/internal/inventory/stock-snapshot`).

**Coming from Phase 4?** Run the rebuild below once, so the index is created again with the new stock fields.
Then open [http/product-read-service.http](http/product-read-service.http).

**Rebuild the index from scratch:** stop the service, run `.\scripts\rebuild-product-index.ps1`, then start the service again.

**A new index version (new mapping), while search keeps working:** `.\scripts\reindex-products.ps1` (the service may run).

**Snapshots:** taken every night (01:30 UTC, the last 7 kept). `.\scripts\search-snapshots.ps1` lists them, `-Create`
takes one now, `-Restore` puts the newest back (stop the service first; on its next start it replays the changes made
since the snapshot).

**Look at the indexes:** Kibana (`.\scripts\start-infra.ps1 -Profiles tools`, then http://localhost:5601/app/discover)
shows every document and runs queries. See [docs/kibana.md](../docs/kibana.md), including the request this service sends
for a search and a recipe for "my product does not show up in the shop".

## Test it

```powershell
.\mvnw verify      # Docker must be running (Elasticsearch + Kafka via Testcontainers)
```

| Test | Kind | What it proves |
|---|---|---|
| `AvroFieldsTest` | plain JUnit | Debezium types, booleans as 1/0, missing fields, deleted rows |
| `CatalogIndexAndSearchIntegrationTest` | `@SpringBootTest` + Elasticsearch container | Old changes never win, category rename, late category, deletes, stock copied onto products (also when it arrives first, never older stock), in-stock and low-stock filters, text/brand/category search, filters, sorting, paging, vendor scope, search suggestions (partial words, any order, typos ranked after real matches, category, never inactive) |
| `CatalogChangesConsumerIntegrationTest` | `@SpringBootTest` + Elasticsearch + Kafka containers | Rows on the cdc topics and StockChanged on the inventory topic end up in Elasticsearch; other inventory events are skipped; a paused listener applies nothing and catches up after resume |
| `SearchIndexesIntegrationTest` | `@SpringBootTest` + Elasticsearch container | `products_v1` ... behind their aliases (write index, 1 shard, no replica); a second start changes nothing; a field missing from the mapping file is refused; a plain index from before aliases keeps working |
| `PublicProductControllerTest` | `@WebMvcTest` | Customers only get ACTIVE products, the in-stock filter, size limit, unknown sort, vendor needs a store, the suggestions' parameters and limits |

Tests that do not need Kafka start with `spring.kafka.listener.auto-startup=false`. Without it, a test could join the real consumer group of the local Kafka.

## Configuration

| Property | Env variable | Local default |
|---|---|---|
| `server.port` | `SERVER_PORT` | `8105` |
| `spring.elasticsearch.uris` | `ELASTICSEARCH_URIS` | `http://localhost:9200` |
| `spring.elasticsearch.connection-timeout` / `socket-timeout` | `ELASTICSEARCH_CONNECTION_TIMEOUT` / `ELASTICSEARCH_SOCKET_TIMEOUT` | `2s` / `5s` |
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `spring.kafka.properties.schema.registry.url` | `SCHEMA_REGISTRY_URL` | `http://localhost:8081` |
