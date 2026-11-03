import { Leaf } from 'lucide-react'
import { ImageWithFallback } from '../../../components/ui/ImageWithFallback'

interface BannerPreviewProps {
  title: string
  subtitle: string | null
  imageUrl: string | null
}

/**
 * A small picture of the banner as the home page shows it: the text on the left, the picture on the right, on the
 * slider's soft green with two decorative circles. Without a picture, a leaf stands in.
 */
export function BannerPreview({ title, subtitle, imageUrl }: BannerPreviewProps) {
  return (
    <div className="relative flex items-center gap-4 overflow-hidden rounded-xl bg-linear-to-br from-brand-light to-tile-1 p-4 sm:p-5">
      <span aria-hidden="true" className="absolute -right-6 -bottom-10 size-32 rounded-full bg-brand/10" />
      <span aria-hidden="true" className="absolute top-3 left-1/2 size-3 rounded-full bg-accent/60" />
      <div className="relative min-w-0 flex-1">
        <p className="font-display text-xl leading-tight font-semibold break-words text-heading">{title || 'Banner title'}</p>
        {subtitle && <p className="mt-1 text-sm">{subtitle}</p>}
      </div>
      <div className="relative flex h-20 w-28 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-card shadow-md sm:h-24 sm:w-32">
        <ImageWithFallback src={imageUrl} alt="" fallback={<Leaf className="size-9 text-brand-text" aria-hidden="true" />} className="size-full object-cover" />
      </div>
    </div>
  )
}
