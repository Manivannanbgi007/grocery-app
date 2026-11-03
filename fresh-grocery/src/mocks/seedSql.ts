import { demoCategories } from './data/categories'
import { demoProductRows, LOW_STOCK_THRESHOLD, toProduct } from './data/products'
import { demoReviews } from './data/reviews'

/*
 * Builds the backend's extra LOCAL seed files from the demo catalog, so the mock API and the real backend show the
 * same shop. Run `npm run seed:sql` after changing src/mocks/data/ (a test fails when the files are out of date).
 *
 * Only the demo rows are written: categories from id 21, products from id 101 (101-143 and 201-250), reviews from id 101.
 * Ids 1-17 already come from the backend's own seed files (R__insert_default_*.sql).
 */

const FIRST_DEMO_CATEGORY = 21
const FIRST_DEMO_PRODUCT = 101

/** Customer ids of the reviewers in auth-service's seed. */
const REVIEWER_IDS: Record<string, number> = { 'Priya Sharma': 4, 'Rahul Verma': 7 }

/** 'Lay''s' : SQL writes a quote inside a text as two quotes. */
function text(value: string | null): string {
  return value === null ? 'NULL' : `'${value.replace(/'/g, "''")}'`
}

const money = (amount: number) => amount.toFixed(2)
const daysAgo = (days: number) => (days === 0 ? 'UTC_TIMESTAMP(6)' : `UTC_TIMESTAMP(6) - INTERVAL ${days} DAY`)

const DAY = 24 * 60 * 60 * 1000

/** The R__ file for product-service: demo categories, products and reviews, then every product's rating. */
export function demoCatalogSql(): string {
  const categories = demoCategories
    .filter((category) => category.id >= FIRST_DEMO_CATEGORY)
    .map(
      (category) =>
        ` (${category.id}, ${text(category.name)}, ${text(category.slug)}, ${text(category.description)}, NULL, ${category.displayOrder}, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)`,
    )

  const products = demoProductRows
    .filter((row) => row.id >= FIRST_DEMO_PRODUCT)
    .map((row) => {
      const product = toProduct(row)
      const created = daysAgo(row.addedDaysAgo)
      return (
        ` (${row.id}, ${row.storeId}, ${row.categoryId}, ${text(row.name)}, ${text(row.description)}, ${text(row.brand)}, ${text(row.sku)}, '${row.unit}', ` +
        `${row.unitQuantity.toFixed(3)}, ${money(row.price)}, ${money(row.discountPercentage)}, ${money(product.sellingPrice)}, ${money(row.taxPercentage)}, ` +
        `'[]', '${row.status ?? 'ACTIVE'}', ${created}, ${created}, 0)`
      )
    })

  const reviews = demoReviews.map((review) => {
    const days = Math.round((Date.now() - Date.parse(review.createdAt)) / DAY)
    const created = daysAgo(days)
    return (
      ` (${review.id}, ${review.productId}, ${review.storeId}, ${review.orderId}, ${REVIEWER_IDS[review.reviewerName]}, ${text(review.reviewerName)}, ` +
      `${review.rating}, ${text(review.comment)}, '${review.status}', NULL, ${created}, ${created}, 0)`
    )
  })

  return `-- =====================================================================
-- LOCAL ONLY demo catalog for the Fresh Grocery storefront (loaded by the "local" profile).
--
-- GENERATED from fresh-grocery/src/mocks/data/ by "npm run seed:sql" - do not edit by hand:
-- change the demo data there and generate again, so the mock API and this seed stay the same.
--
-- Adds categories 21-36, products 101-143 and 201-250 (stock: inventory-service R__insert_demo_stock.sql) and
-- demo reviews 101+ by customer1 (id 4) and customer2 (id 7). The review order ids (9001, 9002, 9101,
-- 9102) are demo values: the seed has no orders behind them.
-- Repeatable migration (R__): runs again whenever this file changes. INSERT IGNORE skips existing rows.
-- =====================================================================

INSERT IGNORE INTO categories (id, name, slug, description, image_url, display_order, active, created_at, updated_at, version) VALUES
${categories.join(',\n')};

INSERT IGNORE INTO products (id, store_id, category_id, name, description, brand, sku, unit, unit_quantity,
                             price, discount_percentage, selling_price, tax_percentage, image_urls, status,
                             created_at, updated_at, version) VALUES
${products.join(',\n')};

INSERT IGNORE INTO product_reviews (id, product_id, store_id, order_id, customer_user_id, reviewer_name, rating, comment,
                                    status, hidden_reason, created_at, updated_at, version) VALUES
${reviews.join(',\n')};

-- Every product's rating = average (1 decimal) and number of its VISIBLE reviews, as product-service keeps them.
-- version + 1 makes the search index (Elasticsearch, fed by Debezium) take the new numbers.
UPDATE products p
LEFT JOIN (SELECT product_id, ROUND(AVG(rating), 1) AS average, COUNT(*) AS reviews
           FROM product_reviews
           WHERE status = 'VISIBLE'
           GROUP BY product_id) r ON r.product_id = p.id
SET p.rating_average = r.average,
    p.rating_count   = COALESCE(r.reviews, 0),
    p.version        = p.version + 1,
    p.updated_at     = UTC_TIMESTAMP(6)
WHERE NOT (p.rating_average <=> r.average) OR p.rating_count <> COALESCE(r.reviews, 0);
`
}

/** The R__ file for inventory-service: the stock of the demo products. */
export function demoStockSql(): string {
  const rows = demoProductRows
    .filter((row) => row.id >= FIRST_DEMO_PRODUCT)
    .map((row) => {
      const note = row.stock === 0 ? '   OUT OF STOCK' : row.stock <= LOW_STOCK_THRESHOLD ? '   LOW' : ''
      return { sql: ` (${row.id}, ${row.storeId}, ${row.stock}, 0, ${LOW_STOCK_THRESHOLD}, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)`, comment: `-- ${row.name}${note}` }
    })

  return `-- =====================================================================
-- LOCAL ONLY stock of the Fresh Grocery demo products 101-143 and 201-250 (loaded by the "local" profile).
--
-- GENERATED from fresh-grocery/src/mocks/data/products.ts by "npm run seed:sql" - do not edit by hand.
-- Repeatable migration (R__): runs again whenever this file changes. INSERT IGNORE skips existing rows.
--
-- SQL inserts publish no events. After the first start, call
-- POST /internal/inventory/stock-snapshot so product-read-service learns the stock.
-- =====================================================================

INSERT IGNORE INTO stock_items (product_id, store_id, quantity_on_hand, reserved_quantity, low_stock_threshold,
                                created_at, updated_at, version) VALUES
${rows.map((row, index) => `${row.sql}${index === rows.length - 1 ? ';' : ','}   ${row.comment}`).join('\n')}
`
}

/** Where the generated files go, relative to the fresh-grocery folder. */
export const SEED_FILES = {
  catalog: '../product-service/src/main/resources/db/seed/R__insert_demo_catalog.sql',
  stock: '../inventory-service/src/main/resources/db/seed/R__insert_demo_stock.sql',
}
