import { ArrowRight } from 'lucide-react'
import { cn } from '../../utils/cn'
import { ButtonLink } from '../ui/Button'

interface NewsletterBannerProps {
  /** light green on the home page, solid green on the other pages (as in the sample screens) */
  variant: 'light' | 'green'
}

/**
 * "Stay home & get your daily needs from our shop".
 * The sample has an e-mail box here, but the backend has no newsletter, so the button simply opens the shop.
 */
export function NewsletterBanner({ variant }: NewsletterBannerProps) {
  const green = variant === 'green'

  return (
    <section className="page-container mt-16">
      <div className={cn('relative flex items-center overflow-hidden rounded-3xl px-6 py-12 sm:px-12 lg:px-20 lg:py-16', green ? 'bg-brand' : 'bg-brand-soft/60')}>
        <div className="relative z-10 max-w-xl space-y-4">
          <p className={cn('font-heading font-bold', green ? 'text-white' : 'text-brand-text')}>Start your daily shopping with Fresh Grocery</p>
          <h2 className={cn('text-3xl leading-tight lg:text-5xl', green && 'text-white')}>Stay home &amp; get your daily needs from our shop</h2>
          <ButtonLink to="/shop" variant={green ? 'accent' : 'primary'} size="lg" className="rounded-full">
            Start shopping
            <ArrowRight className="size-5" aria-hidden="true" />
          </ButtonLink>
        </div>

        {/* Decoration only: a basket of emoji instead of a photo */}
        <div className="pointer-events-none absolute right-6 bottom-0 hidden text-[5.5rem] leading-none select-none md:block lg:right-20 lg:text-[7.5rem]" aria-hidden="true">
          <span className="inline-block -rotate-12">🥦</span>
          <span className="inline-block translate-y-6">🧺</span>
          <span className="inline-block rotate-12">🍎</span>
        </div>
      </div>
    </section>
  )
}
