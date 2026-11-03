// Load test: product search on product-read-service, called directly (not through the gateway, whose rate limit
// would stop it). Elasticsearch only, no MySQL. docs/performance.md explains the runs and the results.
//
//   docker run --rm -i -e VUS=200 grafana/k6:2.3.0 run - < perf/k6/search.js
//
// VUS: how many users search at the same time (each sends its next search as soon as the answer arrives).
// BASE_URL: the service (from inside Docker, host.docker.internal is this machine). DURATION: how long (30s).
import http from 'k6/http'
import { check } from 'k6'

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8105'
const TERMS = ['milk', 'tomato', 'rice', 'oil', 'apple', 'bread', 'atta', 'juice', 'dal', 'tea']

export const options = {
  scenarios: {
    search: { executor: 'constant-vus', vus: Number(__ENV.VUS || 50), duration: __ENV.DURATION || '30s' },
  },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
}

export default function () {
  const term = TERMS[Math.floor(Math.random() * TERMS.length)]
  const response = http.get(`${BASE_URL}/api/v1/products?q=${term}&size=12`)
  check(response, { 'status 200': (r) => r.status === 200 })
}
