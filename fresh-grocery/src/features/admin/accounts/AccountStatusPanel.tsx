import { useState } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { User } from '../../../api/types'
import { Button } from '../../../components/ui/Button'
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog'
import { cn } from '../../../utils/cn'
import { useAuth } from '../../auth/authContext'
import { statusChangeRule } from './accountRules'
import { useSetAccountStatus } from './useAdminAccounts'

/** Whether the account can log in, and "Disable account" / "Enable account" when the admin may change it. */
export function AccountStatusPanel({ account }: { account: User }) {
  const { admin } = useAuth()
  const setStatus = useSetAccountStatus(account.id)
  const [confirming, setConfirming] = useState(false)
  const rule = admin ? statusChangeRule(account, admin) : { allowed: false as const, reason: '' }
  const active = account.status === 'ACTIVE'

  function change(status: User['status']) {
    setStatus.mutate(status, {
      onSuccess: () => {
        setConfirming(false)
        toast.success(status === 'ACTIVE' ? `${account.fullName} can log in again` : `${account.fullName} can no longer log in`)
      },
      onError: (error) => {
        setConfirming(false)
        if (!isApiError(error, 401)) toast.error(error.message)
      },
    })
  }

  return (
    <section aria-labelledby="account-status-title" className={cn('rounded-2xl border p-6', active ? 'border-line bg-card' : 'border-hot/40 bg-hot/5')}>
      <h2 id="account-status-title" className="text-xl">
        {active ? 'Active' : 'Disabled'}
      </h2>
      <p className="mt-1 text-heading">{active ? 'This account can log in.' : 'This account cannot log in. Its orders and data are kept.'}</p>
      {rule.allowed ? (
        <div className="mt-4">
          {active ? (
            <Button variant="outline" onClick={() => setConfirming(true)}>
              Disable account
            </Button>
          ) : (
            <Button loading={setStatus.isPending} onClick={() => change('ACTIVE')}>
              Enable account
            </Button>
          )}
        </div>
      ) : (
        <p className="mt-3 text-sm">{rule.reason}</p>
      )}
      <ConfirmDialog
        open={confirming}
        title={`Disable ${account.fullName}'s account?`}
        message="They can no longer log in. A login that is open now keeps working until it runs out (at most an hour). Their orders and data stay."
        confirmLabel="Disable account"
        cancelLabel="Keep it"
        loading={setStatus.isPending}
        onConfirm={() => change('DISABLED')}
        onCancel={() => setConfirming(false)}
      />
    </section>
  )
}
