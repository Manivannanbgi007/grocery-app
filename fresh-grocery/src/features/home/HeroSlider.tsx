import { ArrowRight } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { buttonClasses } from '../../components/ui/buttonClasses'
import { ImageWithFallback } from '../../components/ui/ImageWithFallback'
import { Skeleton } from '../../components/ui/Skeleton'
import { useBanners } from '../../hooks/useBanners'
import { cn } from '../../utils/cn'
import { appLink } from '../../utils/links'

interface Slide {
  id: number
  title: string
  subtitle: string | null
  imageUrl: string | null
  linkUrl: string | null
}

/** Shown when there are no live banners (or they could not be loaded). */
const WELCOME_SLIDE: Slide = {
  id: 0,
  title: 'Fresh groceries from local stores',
  subtitle: 'Fruits, vegetables and daily essentials, delivered to your door',
  imageUrl: null,
  linkUrl: '/shop',
}

const SLIDE_SECONDS = 7

/** Big emoji compositions used instead of a photo (the banners in the seed have no real image yet). */
const ARTWORK = [
  ['🥬', '🍅', '🥕', '🌽', '🍋', '🥦'],
  ['🧺', '🍎', '🥛', '🍞', '🧀', '🍇'],
]

function HeroArtwork({ index }: { index: number }) {
  const emojis = ARTWORK[index % ARTWORK.length]
  return (
    <div className="grid grid-cols-3 gap-2 text-6xl select-none lg:gap-4 lg:text-8xl" aria-hidden="true">
      {emojis.map((emoji, position) => (
        <span key={emoji} className={cn('inline-block', position % 2 === 0 ? '-rotate-6' : 'translate-y-4 rotate-6')}>
          {emoji}
        </span>
      ))}
    </div>
  )
}

function prefersReducedMotion(): boolean {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
}

/**
 * The banners from the platform settings (admins manage them), one at a time with dots to switch.
 * It moves on by itself every few seconds, but not while the mouse or keyboard focus is on it,
 * and not at all for people who asked their system for reduced motion.
 */
export function HeroSlider() {
  const { data: banners, isPending } = useBanners()
  const [current, setCurrent] = useState(0)
  const [paused, setPaused] = useState(false)

  // A Banner from the API has every field a Slide needs, so banners can be used as slides directly
  const slides: Slide[] = banners && banners.length > 0 ? banners : [WELCOME_SLIDE]
  const slide = slides[current % slides.length]

  useEffect(() => {
    if (paused || slides.length < 2 || prefersReducedMotion()) return
    const timer = setInterval(() => setCurrent((index) => (index + 1) % slides.length), SLIDE_SECONDS * 1000)
    return () => clearInterval(timer)
  }, [paused, slides.length])

  if (isPending) {
    return (
      <div className="page-container mt-6">
        <Skeleton className="h-80 rounded-3xl lg:h-[28rem]" />
      </div>
    )
  }

  return (
    <section
      aria-roledescription="carousel"
      aria-label="Offers and news"
      className="page-container mt-6"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
    >
      <div className="relative flex min-h-80 flex-col justify-center gap-8 overflow-hidden rounded-3xl bg-brand-soft/60 px-6 py-12 sm:px-12 lg:min-h-[28rem] lg:flex-row lg:items-center lg:justify-between lg:px-20">
        <div key={slide.id} aria-roledescription="slide" aria-label={`${(current % slides.length) + 1} of ${slides.length}`} className="relative z-10 max-w-2xl motion-safe:animate-slide-up">
          <h1 className="text-4xl leading-tight lg:text-6xl">{slide.title}</h1>
          {slide.subtitle && <p className="mt-4 text-lg lg:text-2xl">{slide.subtitle}</p>}
          <Link to={appLink(slide.linkUrl)} className={buttonClasses('primary', 'lg', 'mt-8 rounded-full')}>
            Shop now
            <ArrowRight className="size-5" aria-hidden="true" />
          </Link>
        </div>

        {/* Only beside the text on big screens; on smaller ones the text alone is clearer */}
        <div className="hidden shrink-0 lg:block">
          <ImageWithFallback src={slide.imageUrl} alt="" fallback={<HeroArtwork index={current} />} className="max-h-80 rounded-2xl object-cover" />
        </div>

        {slides.length > 1 && (
          <div className="absolute bottom-5 left-1/2 flex -translate-x-1/2 gap-2">
            {slides.map((item, index) => (
              <button
                key={item.id}
                type="button"
                onClick={() => setCurrent(index)}
                aria-label={`Show slide ${index + 1}: ${item.title}`}
                aria-current={index === current % slides.length ? 'true' : undefined}
                className={cn('size-3 rounded-full border-2 border-brand transition-colors', index === current % slides.length ? 'bg-brand' : 'bg-transparent hover:bg-brand-soft')}
              />
            ))}
          </div>
        )}
      </div>
    </section>
  )
}
