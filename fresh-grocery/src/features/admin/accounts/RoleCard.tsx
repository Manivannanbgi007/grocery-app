import { useId, useState } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { User } from '../../../api/types'
import { Badge } from '../../../components/ui/Badge'
import { Button } from '../../../components/ui/Button'
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog'
import { LoadingState } from '../../../components/ui/LoadingState'
import { cn } from '../../../utils/cn'
import { aRole, ROLE_NAMES } from '../../auth/authContext'
import { usePartnerList } from '../deliveries/useAdminDeliveries'
import { roleChoices, ROLE_TONES, type AssignableRole } from './accountRules'
import { useChangeRole, useCustomerProfile } from './useAdminAccounts'

/** Super admin only: the account's role, and another one to give it. */
export function RoleCard({ account }: { account: User }) {
  const changeRole = useChangeRole(account.id)
  const customerProfile = useCustomerProfile(account)
  const partners = usePartnerList()
  const groupName = useId()
  const [role, setRole] = useState<AssignableRole | null>(null)
  const [confirming, setConfirming] = useState(false)

  function save() {
    if (!role) return
    changeRole.mutate(role, {
      onSuccess: () => {
        setConfirming(false)
        setRole(null)
        toast.success(`${account.fullName} is now ${aRole(role)}`)
      },
      onError: (error) => {
        setConfirming(false)
        if (!isApiError(error, 401)) toast.error(error.message)
      },
    })
  }

  let content
  if (account.role === 'SUPER_ADMIN') {
    content = <p className="text-sm">The super admin&apos;s role cannot be changed.</p>
  } else if (customerProfile.isPending || partners.isPending) {
    content = <LoadingState className="min-h-32" />
  } else {
    const choices = roleChoices(account, {
      customer: Boolean(customerProfile.data),
      partner: Boolean(partners.data?.some((partner) => partner.userId === account.id)),
    })
    content = (
      <form
        onSubmit={(event) => {
          event.preventDefault()
          if (role) setConfirming(true)
        }}
      >
        <fieldset>
          <legend className="mb-2 font-heading text-sm font-bold text-heading">Give this account another role</legend>
          <div className="space-y-2">
            {choices.map((choice) => (
              <label
                key={choice.role}
                className={cn('flex gap-3 rounded-xl border border-line p-3 text-sm', choice.unavailable ? 'cursor-not-allowed bg-surface' : 'cursor-pointer', role === choice.role && 'border-brand bg-brand-light')}
              >
                <input
                  type="radio"
                  name={groupName}
                  value={choice.role}
                  checked={role === choice.role}
                  disabled={Boolean(choice.unavailable)}
                  onChange={() => setRole(choice.role)}
                  className="mt-0.5 size-4 accent-brand"
                />
                <span>
                  <span className="block font-bold text-heading">{ROLE_NAMES[choice.role]}</span>
                  {choice.unavailable && <span className="block">{choice.unavailable}</span>}
                </span>
              </label>
            ))}
          </div>
        </fieldset>
        {account.role === 'VENDOR' && account.storeId && <p className="mt-3 text-sm">The link to store #{account.storeId} is removed when the role changes; the store itself stays.</p>}
        <Button type="submit" variant="outline" disabled={!role} className="mt-4">
          Change role
        </Button>
      </form>
    )
  }

  return (
    <section aria-labelledby="account-role-title" className="rounded-2xl border border-line bg-card p-6">
      <div className="mb-1 flex flex-wrap items-center gap-3">
        <h2 id="account-role-title" className="text-xl">
          Role
        </h2>
        <Badge tone={ROLE_TONES[account.role]}>{ROLE_NAMES[account.role]}</Badge>
      </div>
      <p className="mb-4 text-sm">A new role counts from the account&apos;s next login. A login that is open now keeps the old role until it runs out (at most an hour).</p>
      {content}
      <ConfirmDialog
        open={confirming}
        title={role ? `Make ${account.fullName} ${aRole(role)}?` : ''}
        message={role === 'ADMIN' ? 'Admins can see and change everything in the admin panel, except the Owner pages.' : 'The account loses what its current role allows at its next login.'}
        confirmLabel="Change role"
        cancelLabel="Keep the role"
        loading={changeRole.isPending}
        onConfirm={save}
        onCancel={() => setConfirming(false)}
      />
    </section>
  )
}
