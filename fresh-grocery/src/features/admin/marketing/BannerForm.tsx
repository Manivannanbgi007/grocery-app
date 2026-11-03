import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import type { BannerRequest } from '../../../api/adminMarketing'
import { isApiError } from '../../../api/client'
import type { Banner } from '../../../api/types'
import { Button } from '../../../components/ui/Button'
import { DateTimeField } from '../../../components/ui/DateTimeField'
import { TextField } from '../../../components/ui/TextField'
import { indiaDateTimeToIso, isoToIndiaDateTime } from '../../../utils/dates'
import { BannerPreview } from './BannerPreview'
import { useBannerChanges } from './useAdminMarketing'

interface BannerValues {
  title: string
  subtitle: string
  imageUrl: string
  linkUrl: string
  position: string
  /** "2026-11-01T09:00" in India time, or '' for "from now on" / "no end" */
  startsAt: string
  endsAt: string
  active: boolean
}

type BannerErrors = Partial<Record<keyof BannerValues, string>>

/** The BannerRequest rules (BannerRequest.java and Banner.update), checked before asking the server. */
function validateBanner(values: BannerValues): BannerErrors {
  const errors: BannerErrors = {}
  if (!values.title.trim()) errors.title = 'Enter the title.'
  else if (values.title.trim().length > 100) errors.title = 'Use at most 100 characters.'
  if (values.subtitle.length > 200) errors.subtitle = 'Use at most 200 characters.'
  if (!values.imageUrl.trim()) errors.imageUrl = 'Enter the address of the picture.'
  else if (values.imageUrl.length > 500) errors.imageUrl = 'Use at most 500 characters.'
  if (values.linkUrl.length > 500) errors.linkUrl = 'Use at most 500 characters.'
  const position = Number(values.position)
  if (!Number.isInteger(position) || position < 1 || position > 100) errors.position = 'Enter a number from 1 to 100.'
  if (values.startsAt && values.endsAt && values.endsAt <= values.startsAt) errors.endsAt = 'The banner must end after it starts.'
  return errors
}

interface BannerFormProps {
  /** the banner to change; left out to add a new one */
  banner?: Banner
  /** the position a new banner gets (after the last one) */
  nextPosition: number
  onDone: () => void
}

/** Add or change a home page banner: text, picture, link, position and when it is shown. */
export function BannerForm({ banner, nextPosition, onDone }: BannerFormProps) {
  const { create, update } = useBannerChanges()
  const [values, setValues] = useState<BannerValues>({
    title: banner?.title ?? '',
    subtitle: banner?.subtitle ?? '',
    imageUrl: banner?.imageUrl ?? '',
    linkUrl: banner?.linkUrl ?? '',
    position: String(banner?.position ?? nextPosition),
    startsAt: banner?.startsAt ? isoToIndiaDateTime(banner.startsAt) : '',
    endsAt: banner?.endsAt ? isoToIndiaDateTime(banner.endsAt) : '',
    active: banner?.active ?? true,
  })
  const [errors, setErrors] = useState<BannerErrors>({})
  const [formError, setFormError] = useState<string | null>(null)

  function change<K extends keyof BannerValues>(field: K, value: BannerValues[K]) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateBanner(values)
    setErrors(found)
    setFormError(null)
    if (Object.keys(found).length > 0) return

    const request: BannerRequest = {
      title: values.title.trim(),
      subtitle: values.subtitle.trim() || null,
      imageUrl: values.imageUrl.trim(),
      linkUrl: values.linkUrl.trim() || null,
      position: Number(values.position),
      startsAt: values.startsAt ? indiaDateTimeToIso(values.startsAt) : null,
      endsAt: values.endsAt ? indiaDateTimeToIso(values.endsAt) : null,
      active: values.active,
    }
    const callbacks = {
      onSuccess: () => {
        toast.success(banner ? 'Banner saved' : 'Banner added')
        onDone()
      },
      onError: (error: Error) => {
        if (!isApiError(error, 401)) setFormError(error.message)
      },
    }
    if (banner) update.mutate({ bannerId: banner.id, request }, callbacks)
    else create.mutate(request, callbacks)
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-4">
      {formError && (
        <p role="alert" className="rounded-lg border border-hot/40 bg-hot/10 px-4 py-3 text-sm text-heading">
          {formError}
        </p>
      )}
      <BannerPreview title={values.title} subtitle={values.subtitle || null} imageUrl={values.imageUrl || null} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Title" value={values.title} onChange={(event) => change('title', event.target.value)} error={errors.title} />
        <TextField label="Subtitle (optional)" value={values.subtitle} onChange={(event) => change('subtitle', event.target.value)} error={errors.subtitle} />
        <TextField label="Picture address" placeholder="https://..." value={values.imageUrl} onChange={(event) => change('imageUrl', event.target.value)} error={errors.imageUrl} />
        <TextField
          label="Link (optional)"
          placeholder="/categories/1, /offers or /products/5"
          value={values.linkUrl}
          onChange={(event) => change('linkUrl', event.target.value)}
          error={errors.linkUrl}
          hint="Where “Shop now” leads."
        />
        <TextField label="Position" inputMode="numeric" hint="1 = shown first" value={values.position} onChange={(event) => change('position', event.target.value)} error={errors.position} />
        <div className="grid gap-4 sm:col-span-2 sm:grid-cols-2">
          <DateTimeField label="Shown from (optional)" value={values.startsAt} onChange={(startsAt) => change('startsAt', startsAt)} error={errors.startsAt} />
          <DateTimeField label="Shown until (optional)" value={values.endsAt} onChange={(endsAt) => change('endsAt', endsAt)} error={errors.endsAt} />
        </div>
      </div>
      <p className="text-xs">Times are India time. Without them the banner is shown as long as it is on.</p>
      <label className="flex items-center gap-2 text-sm text-heading">
        <input type="checkbox" checked={values.active} onChange={(event) => change('active', event.target.checked)} className="size-4 accent-brand" />
        On (customers can see it)
      </label>
      <div className="flex flex-wrap gap-3">
        <Button type="submit" loading={create.isPending || update.isPending}>
          {banner ? 'Save banner' : 'Add banner'}
        </Button>
        <Button type="button" variant="ghost" onClick={onDone}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
