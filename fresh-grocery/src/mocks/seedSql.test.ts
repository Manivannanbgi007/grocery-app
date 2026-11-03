import { describe, expect, it } from 'vitest'
// "?raw" imports a file's text (a Vite feature): here the backend's seed files
import defaultCatalogSql from '../../../product-service/src/main/resources/db/seed/R__insert_default_catalog.sql?raw'
import demoCatalogFile from '../../../product-service/src/main/resources/db/seed/R__insert_demo_catalog.sql?raw'
import defaultStockSql from '../../../inventory-service/src/main/resources/db/seed/R__insert_default_stock.sql?raw'
import demoStockFile from '../../../inventory-service/src/main/resources/db/seed/R__insert_demo_stock.sql?raw'
import { demoCategories } from './data/categories'
import { demoProductRows, toProduct } from './data/products'
import { demoCatalogSql, demoStockSql } from './seedSql'

/*
 * One catalog, two uses: the mock API and the backend's local seed must show the same shop.
 * These tests fail when one side changes without the other.
 */

/** Windows checkouts may have \r\n line ends; compare the text only. */
const normalise = (sql: string) => sql.replace(/\r\n/g, '\n')

describe('backend seed files', () => {
  it('are generated from the current demo data (run "npm run seed:sql" when this fails)', () => {
    expect(normalise(demoCatalogFile)).toBe(demoCatalogSql())
    expect(normalise(demoStockFile)).toBe(demoStockSql())
  })
})

describe('demo rows 1-17 = the backend’s own seed', () => {
  // ( 1,  1, 1, 'Tomato', 'Farm fresh red tomatoes', NULL, 'FM-VEG-001', 'KG', 1.000, 40.00, 10.00, 36.00, 0.00, '[]', 'ACTIVE', ...
  const productRow =
    /^\s*\((\d+),\s*(\d+),\s*(\d+),\s*'((?:[^']|'')*)',\s*'((?:[^']|'')*)',\s*(NULL|'(?:[^']|'')*'),\s*'([^']*)',\s*'([A-Z]+)',\s*([\d.]+),\s*([\d.]+),\s*([\d.]+),\s*([\d.]+),\s*([\d.]+),\s*'\[\]',\s*'([A-Z]+)'/gm
  const seedProducts = [...defaultCatalogSql.matchAll(productRow)].map((match) => ({
    id: Number(match[1]),
    storeId: Number(match[2]),
    categoryId: Number(match[3]),
    name: match[4].replace(/''/g, "'"),
    description: match[5].replace(/''/g, "'"),
    brand: match[6] === 'NULL' ? null : match[6].slice(1, -1).replace(/''/g, "'"),
    sku: match[7],
    unit: match[8],
    unitQuantity: Number(match[9]),
    price: Number(match[10]),
    discountPercentage: Number(match[11]),
    sellingPrice: Number(match[12]),
    taxPercentage: Number(match[13]),
    status: match[14],
  }))

  it('has the same products, prices and taxes', () => {
    expect(seedProducts).toHaveLength(17)
    for (const seed of seedProducts) {
      const row = demoProductRows.find((product) => product.id === seed.id)
      expect(row, `product ${seed.id}`).toBeDefined()
      const mock = toProduct(row!)
      expect({ ...seed }, `product ${seed.id}`).toEqual({
        id: mock.id,
        storeId: mock.storeId,
        categoryId: mock.categoryId,
        name: mock.name,
        description: mock.description,
        brand: mock.brand,
        sku: mock.sku,
        unit: mock.unit,
        unitQuantity: mock.unitQuantity,
        price: mock.price,
        discountPercentage: mock.discountPercentage,
        sellingPrice: mock.sellingPrice,
        taxPercentage: mock.taxPercentage,
        status: mock.status,
      })
    }
  })

  it('has the same stock', () => {
    // (1,  1, 120, 0, 5, ...)   -- Tomato
    const stockRow = /^\s*\((\d+),\s*(\d+),\s*(\d+),\s*0,\s*5,/gm
    const seedStock = [...defaultStockSql.matchAll(stockRow)].map((match) => ({ id: Number(match[1]), storeId: Number(match[2]), stock: Number(match[3]) }))
    expect(seedStock).toHaveLength(17)
    for (const seed of seedStock) {
      const row = demoProductRows.find((product) => product.id === seed.id)
      expect({ storeId: row?.storeId, stock: row?.stock }, `stock of product ${seed.id}`).toEqual({ storeId: seed.storeId, stock: seed.stock })
    }
  })

  it('has the same categories 1-5', () => {
    for (const category of demoCategories.filter((item) => item.id <= 5)) {
      expect(defaultCatalogSql).toContain(`(${category.id}, '${category.name}',`)
      expect(defaultCatalogSql).toContain(`'${category.slug}'`)
    }
  })
})
