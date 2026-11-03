import { useState, type FormEvent, type ReactNode } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../../api/client'
import type { PlatformSettings } from '../../../../api/types'
import { PageTitle } from '../../../../components/layout/PageTitle'
import { Button } from '../../../../components/ui/Button'
import { ErrorState } from '../../../../components/ui/ErrorState'
import { LoadingState } from '../../../../components/ui/LoadingState'
import { TextField } from '../../../../components/ui/TextField'
import { formatDateTime } from '../../../../utils/format'
import { hasErrors, serverFieldErrors, type FieldErrors } from '../../../auth/validation'
import { SETTINGS_FIELDS, settingsValuesOf, toSettingsRequest, validateSettings, type SettingsValues } from './settingsValidation'
import { useSettings, useUpdateSettings } from './useSettings'

function Card({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-2xl border border-line bg-card p-6">
      <h2 className="mb-4 text-xl">{title}</h2>
      <div className="grid gap-4 sm:grid-cols-2">{children}</div>
    </section>
  )
}

/** The platform settings: what delivery costs, the minimum order and the support contact. /admin/settings */
export function AdminSettingsPage() {
  const { data: settings, isPending, isError, error, refetch } = useSettings()

  let content
  if (isPending) content = <LoadingState className="min-h-96 max-w-3xl" />
  else if (isError) content = <ErrorState title="Could not load the settings" error={error} onRetry={() => void refetch()} />
  else content = <SettingsForm settings={settings} />

  return (
    <>
      <PageTitle title="Settings" />
      <h1 className="mb-2 text-3xl">Platform settings</h1>
      <p className="mb-6 max-w-3xl text-sm">
        These count for every store. The cart uses a new delivery fee and minimum order a moment after you save (cart-service hears of the change).
      </p>
      {content}
    </>
  )
}

function SettingsForm({ settings }: { settings: PlatformSettings }) {
  const update = useUpdateSettings()
  const [values, setValues] = useState<SettingsValues>(() => settingsValuesOf(settings))
  const [errors, setErrors] = useState<FieldErrors<keyof SettingsValues>>({})
  const [formError, setFormError] = useState<string | null>(null)

  function change(field: keyof SettingsValues, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateSettings(values)
    setErrors(found)
    setFormError(null)
    if (hasErrors(found)) return

    update.mutate(toSettingsRequest(values, settings.currency), {
      onSuccess: () => toast.success('Settings saved. New carts use them in a moment.'),
      onError: (error) => {
        const fieldErrors = isApiError(error, 400) ? serverFieldErrors(error, SETTINGS_FIELDS) : {}
        if (hasErrors(fieldErrors)) setErrors(fieldErrors)
        else if (!isApiError(error, 401)) setFormError(error.message)
      },
    })
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="max-w-3xl space-y-6">
      {formError && (
        <p role="alert" className="rounded-lg border border-hot/40 bg-hot/10 px-4 py-3 text-sm text-heading">
          {formError}
        </p>
      )}
      <Card title="Delivery and orders">
        <TextField
          label="Delivery fee"
          leading="₹"
          inputMode="decimal"
          value={values.deliveryFee}
          onChange={(event) => change('deliveryFee', event.target.value)}
          error={errors.deliveryFee}
          hint="Charged when the items cost less than the free delivery amount (at most ₹500)."
        />
        <TextField
          label="Free delivery from"
          leading="₹"
          inputMode="decimal"
          value={values.freeDeliveryFrom}
          onChange={(event) => change('freeDeliveryFrom', event.target.value)}
          error={errors.freeDeliveryFrom}
          hint="Items total from which delivery is free. The shop's top bar shows it."
        />
        <TextField
          label="Minimum order value"
          leading="₹"
          inputMode="decimal"
          value={values.minimumOrderValue}
          onChange={(event) => change('minimumOrderValue', event.target.value)}
          error={errors.minimumOrderValue}
          hint="A cart below this cannot check out. Not higher than the free delivery amount."
        />
        <div>
          <p className="mb-1.5 font-heading text-sm font-bold text-heading">Currency</p>
          <p className="text-heading">{settings.currency}</p>
          <p className="mt-1 text-xs">The website shows every price in rupees (₹), so the currency stays as it is.</p>
        </div>
      </Card>
      <Card title="Support contact">
        <TextField label="Support email" type="email" value={values.supportEmail} onChange={(event) => change('supportEmail', event.target.value)} error={errors.supportEmail} />
        <TextField
          label="Support phone"
          type="tel"
          value={values.supportPhone}
          onChange={(event) => change('supportPhone', event.target.value)}
          error={errors.supportPhone}
          hint="The shop shows it in the menu bar (“Support Center”), the phone menu and the footer."
        />
      </Card>
      <div className="flex flex-wrap items-center gap-4">
        <Button type="submit" loading={update.isPending}>
          Save settings
        </Button>
        <p className="text-sm">Last changed {formatDateTime(settings.updatedAt)}</p>
      </div>
      <p className="text-sm">Tax, payment gateway and SMS or email provider settings are not offered by the backend yet.</p>
    </form>
  )
}
