import { CircleCheck, CircleX, ExternalLink, RefreshCw } from 'lucide-react'
import { PageTitle } from '../../../../components/layout/PageTitle'
import { Badge } from '../../../../components/ui/Badge'
import { Button } from '../../../../components/ui/Button'
import { ErrorState } from '../../../../components/ui/ErrorState'
import { LoadingState } from '../../../../components/ui/LoadingState'
import { cn } from '../../../../utils/cn'
import { formatTime } from '../../../../utils/format'
import { MONITORING_TOOLS, SERVICE_ROLES } from './systemInfo'
import { useSystemHealth } from './useSystemHealth'

const BREAKER_TEXTS = {
  open: 'The gateway does not call it now and answers “temporarily unavailable”',
  half_open: 'The gateway is trying it again with a few requests',
  forced_open: 'Switched off in the gateway',
}

/** Which services are running, whether the gateway has stopped calling one, and the monitoring tools. /admin/system */
export function AdminSystemPage() {
  const { data: health, isPending, isError, error, refetch, isFetching, dataUpdatedAt } = useSystemHealth()

  let content
  if (isPending) {
    content = <LoadingState className="min-h-96" />
  } else if (isError) {
    content = (
      <ErrorState
        title="Could not ask Prometheus"
        error={new Error(`${error.message} It is one of the monitoring tools: .\\scripts\\start-infra.ps1 -Profiles tools,monitoring`)}
        onRetry={() => void refetch()}
      />
    )
  } else {
    const down = health.services.filter((service) => !service.up)
    content = (
      <div className="space-y-6">
        <section aria-labelledby="services-title" className="rounded-2xl border border-line bg-card p-6">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <h2 id="services-title" className="text-xl">
              Services
            </h2>
            <p role="status" className={cn('flex items-center gap-2 font-bold', down.length === 0 ? 'text-brand-text' : 'text-hot')}>
              {down.length === 0 ? <CircleCheck className="size-5" aria-hidden="true" /> : <CircleX className="size-5" aria-hidden="true" />}
              {down.length === 0 ? `All ${health.services.length} services are running` : `${down.length} of ${health.services.length} services are not answering`}
            </p>
          </div>
          <ul aria-label="Services" className="grid gap-x-6 sm:grid-cols-2">
            {health.services.map((service) => (
              <li key={service.service} className="flex items-start justify-between gap-3 border-b border-line py-3">
                <span>
                  <span className="block font-bold text-heading">{service.service}</span>
                  <span className="block text-sm">{SERVICE_ROLES[service.service] ?? ''}</span>
                </span>
                <Badge tone={service.up ? 'brand' : 'hot'}>{service.up ? 'Running' : 'Not answering'}</Badge>
              </li>
            ))}
          </ul>
        </section>

        <section aria-labelledby="breakers-title" className="rounded-2xl border border-line bg-card p-6">
          <h2 id="breakers-title" className="mb-2 text-xl">
            Gateway circuit breakers
          </h2>
          {health.circuitBreakers.length === 0 ? (
            <p className="text-sm">All closed: the gateway calls every service. A breaker opens when a service fails or answers too slowly too often.</p>
          ) : (
            <ul aria-label="Open circuit breakers" className="space-y-2">
              {health.circuitBreakers.map((breaker) => (
                <li key={breaker.name} className="rounded-xl border border-hot/40 bg-hot/5 px-4 py-3 text-sm">
                  <span className="font-bold text-heading">{breaker.name}</span>: {BREAKER_TEXTS[breaker.state]}
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    )
  }

  return (
    <>
      <PageTitle title="System" />
      <div className="mb-2 flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-3xl">System</h1>
        <Button variant="light" onClick={() => void refetch()} loading={isFetching}>
          <RefreshCw className="size-4" aria-hidden="true" />
          Check again
        </Button>
      </div>
      <p className="mb-6 max-w-3xl text-sm">
        From Prometheus, which checks every service every 15 seconds{dataUpdatedAt > 0 && ` (asked at ${formatTime(new Date(dataUpdatedAt).toISOString())})`}. This page asks
        again every 30 seconds.
      </p>
      {content}
      <section aria-labelledby="tools-title" className="mt-6 rounded-2xl border border-line bg-card p-6">
        <h2 id="tools-title" className="mb-1 text-xl">
          Monitoring tools
        </h2>
        <p className="mb-4 text-sm">Local addresses; they run with .\scripts\start-infra.ps1 -Profiles tools,monitoring.</p>
        <ul className="grid gap-3 sm:grid-cols-2">
          {MONITORING_TOOLS.map((tool) => (
            <li key={tool.name}>
              <a href={tool.url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 font-bold text-brand-text hover:underline">
                {tool.name}
                <ExternalLink className="size-4" aria-hidden="true" />
                <span className="sr-only">(opens in a new tab)</span>
              </a>
              <span className="block text-sm">{tool.use}</span>
            </li>
          ))}
        </ul>
      </section>
    </>
  )
}
