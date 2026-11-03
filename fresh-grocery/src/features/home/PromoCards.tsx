import { ArrowRight } from 'lucide-react'
import { Link } from 'react-router'
import { buttonClasses } from '../../components/ui/buttonClasses'
import { useCategories } from '../../hooks/useCategories'
import { cn } from '../../utils/cn'
import { shopLink } from '../../utils/links'

interface Promo {
  title: string
  /** the category to open (looked up by slug, because ids differ between databases) ... */
  categorySlug?: string
  /** ... or a search text */
  search?: string
  emojis: string[]
  background: string
}

const PROMOS: Promo[] = [
  { title: 'Everyday fresh vegetables, straight from the farm', categorySlug: 'vegetables', emojis: ['🧅', '🥕'], background: 'bg-promo-1' },
  { title: 'Make your breakfast healthy and easy', categorySlug: 'breakfast-bakery', emojis: ['🥣', '🍓'], background: 'bg-promo-2' },
  { title: 'The best organic products online', search: 'organic', emojis: ['🥬', '🧺'], background: 'bg-promo-3' },
]

/** Three big cards that lead to a category or a search. */
export function PromoCards() {
  const { data: categories = [] } = useCategories()

  function linkFor(promo: Promo): string {
    if (promo.search) return shopLink({ q: promo.search })
    const category = categories.find((item) => item.slug === promo.categorySlug)
    return category ? shopLink({ category: category.id }) : shopLink()
  }

  return (
    <section aria-label="Promotions" className="page-container mt-8">
      <ul className="grid gap-5 lg:grid-cols-3">
        {PROMOS.map((promo) => (
          <li key={promo.title} className={cn('relative flex min-h-56 overflow-hidden rounded-2xl p-8', promo.background)}>
            <div className="relative z-10 flex max-w-[65%] flex-col items-start gap-5">
              <h2 className="text-xl leading-snug lg:text-2xl">{promo.title}</h2>
              <Link to={linkFor(promo)} className={buttonClasses('primary', 'sm', 'mt-auto')}>
                Shop now
                <ArrowRight className="size-3.5" aria-hidden="true" />
              </Link>
            </div>
            <div className="absolute right-4 bottom-2 flex text-6xl select-none sm:text-7xl xl:text-8xl" aria-hidden="true">
              <span className="-rotate-12">{promo.emojis[0]}</span>
              <span className="translate-y-4 rotate-12">{promo.emojis[1]}</span>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
