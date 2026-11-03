import { delay, http, HttpResponse } from 'msw'

/*
 * Mock of the Prometheus queries of the System page: every service of infrastructure/prometheus/prometheus.yml is up,
 * and no circuit breaker of the gateway is open. A test can override it with server.use(...).
 */

export const MONITORED_SERVICES = [
  'api-gateway',
  'auth-service',
  'customer-service',
  'store-service',
  'product-service',
  'product-read-service',
  'inventory-service',
  'cart-service',
  'order-service',
  'payment-service',
  'delivery-service',
  'notification-service',
  'report-service',
  'platform-service',
  'mcp-client-service',
  'mcp-server-service',
]

/** A Prometheus answer with one sample per series. */
export function prometheusAnswer(samples: { metric: Record<string, string>; value: string }[]) {
  const now = Date.now() / 1000
  return HttpResponse.json({ status: 'success', data: { resultType: 'vector', result: samples.map(({ metric, value }) => ({ metric, value: [now, value] })) } })
}

export const monitoringHandlers = [
  http.get('*/prometheus/api/v1/query', async ({ request }) => {
    await delay()
    const promql = new URL(request.url).searchParams.get('query') ?? ''
    if (promql.startsWith('up')) {
      return prometheusAnswer(MONITORED_SERVICES.map((service) => ({ metric: { __name__: 'up', job: 'grocery-services', service }, value: '1' })))
    }
    return prometheusAnswer([])
  }),
]
