# Kibana: look into Elasticsearch

Elasticsearch holds the **read side of the catalog**: product-read-service copies products, stock and categories from
MySQL into it (Debezium → Kafka → product-read-service) and answers every product search from it. Kibana is the web UI to
**look at that data**: browse documents, try queries, check mappings, and find out why a product does not show up.

| | |
|---|---|
| URL | http://localhost:5601/app/discover (no login: Elasticsearch runs without security locally) |
| Start | `.\scripts\start-infra.ps1 -Profiles tools` (about a minute) |
| Version | always the Elasticsearch version: both use `ELASTIC_VERSION` in `infrastructure/.env` |
| Memory | about 1.4 GB, so it is optional: start it when you need it, stop it after |

> **Look, don't change.** The indexes are copies. A document you edit or delete in Kibana is out of step with MySQL until
> the next change of that product overwrites it. Fix data through the API (MySQL is the source of truth), or rebuild the
> indexes with `scripts\rebuild-product-index.ps1`.

## Start and stop

```powershell
.\scripts\start-infra.ps1 -Profiles tools        # + Kibana, and its data views (see below)
docker compose -f infrastructure/docker-compose.yml stop kibana    # stop it again (data views are kept)
```

`start-infra.ps1` waits until Kibana is ready and the data views exist. `check-health.ps1` shows
`[OK] Kibana http://localhost:5601 version 9.4.8, available`.

## What is prepared for you

Kibana only shows an index through a **data view** (it says which indexes to read). The one-shot container
`kibana-setup` ([infrastructure/kibana/setup-kibana.sh](../infrastructure/kibana/setup-kibana.sh)) creates one per index
each time the tools profile starts:

| Data view | Index | One document = | Filled from |
|---|---|---|---|
| **Products (search index)** (default) | `products` | a product as the shop shows it: name, brand, category name, prices, status, rating, stock flags | `cdc.product_db.products` + `categories` + inventory `StockChanged` |
| Product stock | `product_stock` | the stock of one product (`availableQuantity`, `lowStock`) | inventory `StockChanged` events |
| Categories | `categories` | a category | `cdc.product_db.categories` |

The data views have **no time field** on purpose: Discover shows every document, not only those of the last 15 minutes.
They also hide `_class` (the Java class name Spring Data stores in each document). Kibana keeps the data views in
Elasticsearch itself (`.kibana*` indexes), so they survive restarts and `rebuild-product-index.ps1`.

## Discover: browse and filter

1. Open http://localhost:5601/app/discover. **Products (search index)** is selected (top left: *Data view*).
2. Type a query in **KQL** (Kibana Query Language) into the search bar and press Enter.
3. Click a field on the left to see its most common values; click **+** next to it to make it a column (for example
   `name`, `sellingPrice`, `availableQuantity`).
4. Click the arrow at the start of a row to see the whole document (tab **JSON**).
5. **Save** keeps the query and columns as a Discover session for next time.

| KQL (data view) | Finds |
|---|---|
| `name : butter` | products with the word "butter" in the name (full text, like the shop's search box) |
| `brand : amul` | products of a brand |
| `id : 9` | one product by its id (the same id as in MySQL) |
| `sku : FM-DRY-*` | SKUs starting with `FM-DRY-` (`*` works on exact-value fields such as `sku`) |
| `inStock : false` | out of stock: customers who tick "in stock only" don't see them |
| `lowStock : true` | only a few left |
| `sellingPrice < 50` | cheaper than ₹50 (`<`, `<=`, `>`, `>=`) |
| `categoryId : 3 and discountPercentage > 0` | discounted products of category 3 (`and`, `or`, `not`, brackets) |
| `not status : ACTIVE` | INACTIVE (switched off by the vendor) or BLOCKED (by an admin): never shown to customers |
| `availableQuantity < 10` (*Product stock*) | stock rows below 10 |
| `active : false` (*Categories*) | hidden categories (usually none) |

## Dev Tools: run Elasticsearch requests

http://localhost:5601/app/dev_tools#/console: paste a request on the left, put the cursor in it and press
**Ctrl+Enter** (or ▶). The answer appears on the right. These are the same requests the services send (`curl
http://localhost:9200/...` works too, but the console helps with autocompletion and formatting).

```text
# 1. Which indexes exist, how many documents, how big
GET _cat/indices/products,product_stock,categories?v&h=index,docs.count,store.size

# 2. The fields of an index. "name" is "text" (split into words, for searching) with a sub-field
#    "name.keyword" (the exact value, for sorting, filters and counting)
GET products/_mapping

# 3. One product and its stock, by id
GET products/_doc/9
GET product_stock/_doc/9

# 4. The search the shop runs for "butter", in stock, at most ₹100 (ProductQueryServiceImpl builds this):
#    filters must match; at least one of the text matches must match; a match in the name counts 3x
GET products/_search
{
  "query": {
    "bool": {
      "filter": [
        { "terms": { "status": ["ACTIVE"] } },
        { "term":  { "inStock": true } },
        { "range": { "sellingPrice": { "lte": 100 } } }
      ],
      "should": [
        { "match": { "name":         { "query": "butter", "boost": 3 } } },
        { "match": { "brand":        { "query": "butter", "boost": 2 } } },
        { "match": { "categoryName": { "query": "butter", "boost": 2 } } },
        { "match": { "description":  "butter" } }
      ],
      "minimum_should_match": 1
    }
  },
  "_source": ["name", "brand", "sellingPrice", "availableQuantity"]
}

# 5. Products per category, with the average price (an aggregation: counting instead of listing)
GET products/_search
{
  "size": 0,
  "aggs": {
    "per_category": {
      "terms": { "field": "categoryId", "size": 20 },
      "aggs": { "average_price": { "avg": { "field": "sellingPrice" } } }
    }
  }
}

# 6. How a text is split into words (what a full-text search really compares): amul, butter, 500g
POST products/_analyze
{ "field": "name", "text": "Amul Butter 500g" }

# 7. Try an idea before changing code: today a typo finds nothing ...
GET products/_search
{ "query": { "match": { "name": "buter" } } }
#    ... with "fuzziness" it finds Amul Butter
GET products/_search
{ "query": { "match": { "name": { "query": "buter", "fuzziness": "AUTO" } } } }
```

Also in Dev Tools: **Search Profiler** shows which part of a slow query takes the time.

**Index details** (size, document count, mapping, settings): ☰ menu → Stack Management → Index Management, or
http://localhost:5601/app/management/data/index_management/indices.

## Recipe: "my product does not show up in the shop"

1. **Discover → Products → `id : 42`** (the product's id).
   - **Not found:** the change has not reached Elasticsearch. Is product-read-service running? Kafka UI
     (http://localhost:8090) → consumer group of product-read-service: lag on the `cdc.product_db.*` topics? Connector
     running (`GET http://localhost:8083/connectors?expand=status`)? Messages in a `*.DLT` topic?
   - **Found, but old data:** compare `version` with MySQL. Lower in Elasticsearch = the change is still on its way.
     ```sql
     SELECT id, version, status FROM product_db.products WHERE id = 42;   -- localhost:3307, root / root
     ```
2. **`status` is not ACTIVE:** hidden from customers on purpose (INACTIVE by the vendor, BLOCKED by an admin).
3. **`inStock : false`:** hidden when "in stock only" is ticked; `GET product_stock/_doc/42` shows the stock.
4. **Found and active, but a search text does not find it:** run request 4 above with that text; request 6 shows how the
   name was split into words. Typos are not corrected (request 7).

## Configuration

Everything is in [infrastructure/docker-compose.yml](../infrastructure/docker-compose.yml) (services `kibana` and
`kibana-setup`, profile `tools`):

| Setting | Why |
|---|---|
| image `kibana:${ELASTIC_VERSION}` | Kibana refuses to work with another Elasticsearch version |
| `ELASTICSEARCH_HOSTS` | `http://elasticsearch:9200`: the container name inside the Docker network |
| `SERVER_PUBLICBASEURL` | the address in your browser (Kibana warns without it) |
| `TELEMETRY_OPTIN`, `TELEMETRY_ALLOWCHANGINGOPTINSTATUS`, `NEWSFEED_ENABLED` | no usage data sent to Elastic, no news feed |
| `SECURITY_SHOWINSECURECLUSTERWARNING` | no "Your data is not secure" pop-up: Elasticsearch runs without security locally, on purpose |
| `XPACK_ENCRYPTEDSAVEDOBJECTS_ENCRYPTIONKEY` | from `KIBANA_ENCRYPTION_KEY` in `.env`; without it Kibana switches parts of itself off |
| `NODE_OPTIONS=--max-old-space-size=1024` | at most 1 GB heap (otherwise Kibana takes about 2 GB) |
| healthcheck `/api/status` | `start-infra.ps1` (`up --wait`) waits until Kibana is ready |

**A new index?** Add one line to `setup-kibana.sh` (`create_data_view "<id>" "<index>" "<name>"`), then run it again:

```powershell
docker compose -f infrastructure/docker-compose.yml --profile tools run --rm kibana-setup
```

## Troubleshooting

| Symptom | What to do |
|---|---|
| "Kibana server is not ready yet" | It is still starting (about a minute): `docker logs -f grocery-kibana` |
| Discover: "No results match your search criteria" | Right data view (top left)? Without a query, do you see documents? If the index is empty, product-read-service has not run yet: start it once, it fills the indexes from Kafka |
| No data views | `docker logs grocery-kibana-setup` shows why; run it again (command above) |
| Kibana does not start after changing `ELASTIC_VERSION` | Both containers must be recreated: `.\scripts\start-infra.ps1 -Profiles tools` |
| WARN/ERROR lines in Kibana's log at start (TLS, `xpack.security.encryptionKey`, `reporting`, AI assistant licence, workflows data stream) | Expected locally: features of a secured cluster or of paid licences. They do not affect Discover or Dev Tools |
| The laptop gets slow | Stop Kibana when you don't need it (above) |

## In production

Locally Kibana is a developer tool with no login. In a real environment:

- Elasticsearch runs with security. Kibana connects with a service account token (`ELASTICSEARCH_SERVICEACCOUNTTOKEN`),
  over HTTPS, and people log in with their own users and roles (for example read-only on `products*`).
- Three random encryption keys (`bin/kibana-encryption-keys generate`), kept as secrets, the same on every Kibana
  instance.
- Kibana is reachable only from the internal network or a VPN, never from the internet.
- On Kubernetes, Elastic's operator (ECK) usually runs Elasticsearch and Kibana together. Like MySQL and Kafka,
  Elasticsearch is not part of this repository's `deployment/kubernetes/` manifests.
