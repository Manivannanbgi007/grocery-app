/*
 * Writes the backend's demo seed files from the demo catalog (src/mocks/data/):
 *   npm run seed:sql
 * Then restart product-service and inventory-service (Flyway runs the changed R__ files),
 * and publish the stock once: POST http://localhost:8106/internal/inventory/stock-snapshot
 */
import { writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { demoCatalogSql, demoStockSql, SEED_FILES } from '../src/mocks/seedSql'

const projectFolder = resolve(import.meta.dirname, '..')

for (const [file, content] of [
  [SEED_FILES.catalog, demoCatalogSql()],
  [SEED_FILES.stock, demoStockSql()],
]) {
  const path = resolve(projectFolder, file)
  writeFileSync(path, content)
  console.log(`written: ${path}`)
}
