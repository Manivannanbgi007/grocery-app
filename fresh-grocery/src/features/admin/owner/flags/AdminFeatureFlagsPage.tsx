import { Plus } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { isApiError } from '../../../../api/client'
import type { FeatureFlag } from '../../../../api/types'
import { PageTitle } from '../../../../components/layout/PageTitle'
import { Badge } from '../../../../components/ui/Badge'
import { Button } from '../../../../components/ui/Button'
import { EmptyState } from '../../../../components/ui/EmptyState'
import { ErrorState } from '../../../../components/ui/ErrorState'
import { LoadingState } from '../../../../components/ui/LoadingState'
import { Switch } from '../../../../components/ui/Switch'
import { formatDateTime } from '../../../../utils/format'
import { useAuth } from '../../../auth/authContext'
import { FeatureFlagForm } from './FeatureFlagForm'
import { useFeatureFlagChanges, useFeatureFlags } from './useFeatureFlags'

/** Feature flags: switches for things that are not for everyone yet (a new city, delivery slots ...). /admin/feature-flags */
export function AdminFeatureFlagsPage() {
  const { admin } = useAuth()
  const { data: flags, isPending, isError, error, refetch } = useFeatureFlags()
  const { toggle } = useFeatureFlagChanges()
  const [adding, setAdding] = useState(false)

  function switchFlag(flag: FeatureFlag) {
    toggle.mutate(flag, {
      onSuccess: (changed) => toast.success(`${changed.key} is ${changed.enabled ? 'on' : 'off'}`),
      onError: (changeError) => {
        if (!isApiError(changeError, 401)) toast.error(changeError.message)
      },
    })
  }

  /** "Changed by you on 26 Oct 2026, 6:15 pm" / "by account #2" / not changed since the seed. */
  function changedLine(flag: FeatureFlag) {
    if (flag.updatedByUserId === null) return 'Not changed since it was set up'
    const who =
      flag.updatedByUserId === admin?.id ? (
        'you'
      ) : (
        <Link to={`/admin/accounts/${flag.updatedByUserId}`} className="font-bold text-brand-text hover:underline">
          account #{flag.updatedByUserId}
        </Link>
      )
    return (
      <>
        Changed by {who} on {formatDateTime(flag.updatedAt)}
      </>
    )
  }

  let content
  if (isPending) content = <LoadingState className="min-h-64" />
  else if (isError) content = <ErrorState title="Could not load the feature flags" error={error} onRetry={() => void refetch()} />
  else if (flags.length === 0) content = <EmptyState icon="🚩" title="No feature flags yet" className="rounded-2xl bg-card" />
  else {
    content = (
      <ul aria-label="Feature flags" className="divide-y divide-line rounded-2xl border border-line bg-card">
        {flags.map((flag) => (
          <li key={flag.key} className="flex items-start gap-4 p-4 sm:p-5">
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="font-mono text-base break-all">{flag.key}</h2>
                <Badge tone={flag.enabled ? 'brand' : 'neutral'}>{flag.enabled ? 'On' : 'Off'}</Badge>
              </div>
              <p className="mt-1 text-heading">{flag.description}</p>
              <p className="mt-1 text-xs">{changedLine(flag)}</p>
            </div>
            <Switch checked={flag.enabled} label={`${flag.key} on`} onToggle={() => switchFlag(flag)} disabled={toggle.isPending && toggle.variables?.key === flag.key} />
          </li>
        ))}
      </ul>
    )
  }

  return (
    <>
      <PageTitle title="Feature flags" />
      <div className="mb-2 flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-3xl">Feature flags</h1>
        {!adding && (
          <Button onClick={() => setAdding(true)}>
            <Plus className="size-4" aria-hidden="true" />
            Add a feature flag
          </Button>
        )}
      </div>
      <p className="mb-6 max-w-3xl text-sm">
        Switches for features that are not for everyone yet, e.g. a new city. Every change is in the audit log. The website reads PRODUCT_REVIEWS: when it is off,
        customers do not see the &quot;review this product&quot; form of delivered orders (within 5 minutes). No other flag is read by the website or a service yet.
      </p>
      {adding && (
        <section aria-labelledby="new-flag-title" className="mb-6 rounded-2xl border border-brand/40 bg-card p-6">
          <h2 id="new-flag-title" className="mb-4 text-xl">
            New feature flag
          </h2>
          <FeatureFlagForm onDone={() => setAdding(false)} />
        </section>
      )}
      {content}
    </>
  )
}
