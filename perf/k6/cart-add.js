// Load test: add a product to the cart, then remove it, on cart-service called directly (not through the gateway).
// Each add calls product-read-service over HTTP (price, stock) between two short MySQL transactions: a mix of
// database and network waiting. docs/performance.md explains the runs and the results.
//
//   docker run --rm -i -e VUS=200 grafana/k6:2.3.0 run - < perf/k6/cart-add.js
//
// Every user is a made-up customer (id 900001, 900002 ...), sent in the headers the gateway would add; their carts are
// emptied at the end. PRODUCT_ID must be an active product with stock (9 = Lay's Classic Salted in the seed data).
import http from 'k6/http'
import { check } from 'k6'

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8107'
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || 9)
const VUS = Number(__ENV.VUS || 50)
const FIRST_CUSTOMER_ID = 900000

export const options = {
  scenarios: {
    cart: { executor: 'constant-vus', vus: VUS, duration: __ENV.DURATION || '30s' },
  },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
  // Thresholds that always pass: they only make the summary show the add and the remove separately
  thresholds: {
    'http_req_duration{name:add}': ['max>=0'],
    'http_req_duration{name:remove}': ['max>=0'],
  },
}

function headersOf(customerId) {
  return { 'Content-Type': 'application/json', 'X-User-Id': String(customerId), 'X-User-Role': 'CUSTOMER' }
}

export default function () {
  const headers = headersOf(FIRST_CUSTOMER_ID + __VU)
  const added = http.post(`${BASE_URL}/api/v1/cart/items?replaceCart=true`, JSON.stringify({ productId: PRODUCT_ID, quantity: 1 }),
    { headers, tags: { name: 'add' } })
  check(added, { 'added (200)': (r) => r.status === 200 })
  const removed = http.del(`${BASE_URL}/api/v1/cart/items/${PRODUCT_ID}`, null, { headers, tags: { name: 'remove' } })
  check(removed, { 'removed (200)': (r) => r.status === 200 })
}

// Leave the made-up customers' carts empty
export function teardown() {
  for (let vu = 1; vu <= VUS; vu++) {
    http.del(`${BASE_URL}/api/v1/cart`, null, { headers: headersOf(FIRST_CUSTOMER_ID + vu) })
  }
}
