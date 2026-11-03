import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { MONITORED_SERVICES, prometheusAnswer } from '../../../mocks/handlers/monitoring'
import { server } from '../../../mocks/server'
import { logInAs, renderApp } from '../../../test/render'

/* The super admin's System page: the services' up/down and the gateway's circuit breakers, from Prometheus (mocked). */

describe('System page', () => {
  it('says that every service is running', async () => {
    logInAs('superadmin@grocery.com')
    renderApp('/admin/system')
    expect(await screen.findByText(`All ${MONITORED_SERVICES.length} services are running`, {}, { timeout: 10_000 })).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Services' })).getAllByRole('listitem')).toHaveLength(MONITORED_SERVICES.length)
    expect(screen.getByText(/All closed: the gateway calls every service/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Grafana/ })).toHaveAttribute('href', 'http://localhost:3000')
  })

  it('names a service that does not answer and an open circuit breaker', async () => {
    server.use(
      http.get('*/prometheus/api/v1/query', ({ request }) => {
        const promql = new URL(request.url).searchParams.get('query') ?? ''
        if (promql.startsWith('up')) {
          return prometheusAnswer(MONITORED_SERVICES.map((service) => ({ metric: { job: 'grocery-services', service }, value: service === 'order-service' ? '0' : '1' })))
        }
        return prometheusAnswer([{ metric: { service: 'api-gateway', name: 'order-service', state: 'open' }, value: '1' }])
      }),
    )
    logInAs('superadmin@grocery.com')
    renderApp('/admin/system')
    expect(await screen.findByText(`1 of ${MONITORED_SERVICES.length} services are not answering`, {}, { timeout: 10_000 })).toBeInTheDocument()
    const order = within(screen.getByRole('list', { name: 'Services' })).getByText('order-service').closest('li')!
    expect(order).toHaveTextContent('Not answering')
    expect(screen.getByRole('list', { name: 'Open circuit breakers' })).toHaveTextContent('order-service: The gateway does not call it now')
  })

  it('explains how to start Prometheus when it cannot be reached', async () => {
    server.use(http.get('*/prometheus/api/v1/query', () => new HttpResponse(null, { status: 502 })))
    logInAs('superadmin@grocery.com')
    renderApp('/admin/system')
    expect(await screen.findByText(/Prometheus did not answer \(HTTP 502\)/, {}, { timeout: 10_000 })).toHaveTextContent('start-infra.ps1 -Profiles tools,monitoring')
  })
})
