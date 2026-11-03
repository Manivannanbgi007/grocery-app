import { BadgePercent, LayoutGrid, Store, Truck, Wallet, type LucideIcon } from 'lucide-react'
import { useCategories } from '../../hooks/useCategories'
import { usePlatformSettings } from '../../hooks/usePlatformSettings'
import { formatPrice } from '../../utils/format'

interface Feature {
  icon: LucideIcon
  title: string
  text: string
}

/** The five cards above the footer. Every promise on them is something the platform really does. */
export function FeatureStrip() {
  const { data: settings } = usePlatformSettings()
  const { data: categories } = useCategories()

  const features: Feature[] = [
    { icon: BadgePercent, title: 'Best prices & offers', text: 'Coupons from stores and from us' },
    {
      icon: Truck,
      title: 'Free delivery',
      text: settings ? `On orders of ${formatPrice(settings.freeDeliveryFrom)} or more` : 'On bigger orders',
    },
    { icon: Store, title: 'Local stores', text: 'Fresh from stores near you' },
    {
      icon: LayoutGrid,
      title: 'Wide assortment',
      text: categories ? `${categories.length} categories to choose from` : 'Groceries and daily essentials',
    },
    { icon: Wallet, title: 'Easy payment', text: 'Cash on delivery or pay online' },
  ]

  return (
    <section aria-label="Why shop with us" className="page-container mt-12">
      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
        {features.map(({ icon: Icon, title, text }) => (
          <li key={title} className="flex items-center gap-4 rounded-xl border border-line bg-card p-5">
            <Icon className="size-10 shrink-0 text-brand" strokeWidth={1.5} aria-hidden="true" />
            <div>
              <h3 className="text-base">{title}</h3>
              <p className="text-sm">{text}</p>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
