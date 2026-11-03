/*
 * The super admin's System page. No backend endpoint reports the services' health, so the page asks Prometheus, which
 * scrapes every service every 15 s (infrastructure/prometheus/prometheus.yml gives each one a "service" label). The
 * dev server forwards /prometheus to http://localhost:9090 (vite.config.ts). This is not a gateway call, so it does not
 * go through apiRequest; in production it needs a protected route to Prometheus or a health endpoint in the gateway.
 */

interface PrometheusSample {
  metric: Record<string, string>
  /** [unix time in seconds, value as text] */
  value: [number, string]
}

interface PrometheusAnswer {
  status: 'success' | 'error'
  data?: { result: PrometheusSample[] }
  error?: string
}

/** The current value of a PromQL query, one sample per matching series. */
async function query(promql: string, signal?: AbortSignal): Promise<PrometheusSample[]> {
  const url = new URL('/prometheus/api/v1/query', window.location.origin)
  url.searchParams.set('query', promql)
  let response: Response
  try {
    response = await fetch(url, { signal })
  } catch (error) {
    if (signal?.aborted) throw error
    throw new Error('Cannot reach Prometheus. Is it running?')
  }
  if (!response.ok) throw new Error(`Prometheus did not answer (HTTP ${response.status}). Is it running?`)
  const answer = (await response.json()) as PrometheusAnswer
  if (answer.status !== 'success' || !answer.data) throw new Error(answer.error ?? 'Prometheus could not answer the question.')
  return answer.data.result
}

export interface ServiceHealth {
  /** e.g. "order-service" */
  service: string
  /** Prometheus reached its /actuator/prometheus at the last scrape */
  up: boolean
}

export interface CircuitBreaker {
  /** the route's circuit breaker in the gateway, named after the service, e.g. "order-service" */
  name: string
  /** open: the gateway answers "temporarily unavailable" without calling the service; half_open: it tries again */
  state: 'open' | 'half_open' | 'forced_open'
}

export interface SystemHealth {
  services: ServiceHealth[]
  /** the gateway's circuit breakers that are not closed */
  circuitBreakers: CircuitBreaker[]
}

export async function getSystemHealth(signal?: AbortSignal): Promise<SystemHealth> {
  const [up, breakers] = await Promise.all([
    query('up{job="grocery-services"}', signal),
    query('resilience4j_circuitbreaker_state{service="api-gateway", state=~"open|half_open|forced_open"} == 1', signal),
  ])
  return {
    services: up.map((sample) => ({ service: sample.metric.service ?? sample.metric.instance, up: sample.value[1] === '1' })).sort((a, b) => a.service.localeCompare(b.service)),
    circuitBreakers: breakers.map((sample) => ({ name: sample.metric.name, state: sample.metric.state as CircuitBreaker['state'] })),
  }
}
