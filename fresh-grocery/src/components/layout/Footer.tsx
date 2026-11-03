import { Headset, Mail, Phone } from 'lucide-react'
import { Link } from 'react-router'
import { useCategories } from '../../hooks/useCategories'
import { usePlatformSettings } from '../../hooks/usePlatformSettings'
import { phoneHref } from '../../utils/format'
import { shopLink } from '../../utils/links'
import { Logo } from './Logo'

interface FooterLink {
  label: string
  to: string
}

function FooterColumn({ title, links }: { title: string; links: FooterLink[] }) {
  return (
    <div>
      <h2 className="mb-5 text-xl">{title}</h2>
      <ul className="space-y-3 text-sm">
        {links.map((link) => (
          <li key={link.to}>
            <Link to={link.to} className="text-heading transition-colors hover:text-brand-text">
              {link.label}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}

const SHOP_LINKS: FooterLink[] = [
  { label: 'Home', to: '/' },
  { label: 'All products', to: '/shop' },
  { label: 'Our stores', to: '/stores' },
  { label: 'Hot deals', to: '/#deals' },
  { label: 'Offers and coupons', to: '/#offers' },
]

const ACCOUNT_LINKS: FooterLink[] = [
  { label: 'Sign in', to: '/login' },
  { label: 'Create an account', to: '/register' },
  { label: 'View cart', to: '/cart' },
  { label: 'Track my order', to: '/orders/track' },
  { label: 'My wishlist', to: '/wishlist' },
  { label: 'Become a vendor', to: '/register?role=VENDOR' },
]

/** Worked out once when the app loads (reading the clock while rendering would give unstable results). */
const CURRENT_YEAR = new Date().getFullYear()

/** Only real links: pages of this app, the categories from the API, the support phone and e-mail from the platform settings. */
export function Footer() {
  const { data: settings } = usePlatformSettings()
  const { data: categories = [] } = useCategories()
  const categoryLinks = categories.slice(0, 6).map((category) => ({ label: category.name, to: shopLink({ category: category.id }) }))

  return (
    <footer className="mt-12 border-t border-line bg-glass">
      <div className="page-container grid gap-10 py-12 sm:grid-cols-2 lg:grid-cols-[1.4fr_1fr_1fr_1fr]">
        <div className="space-y-5">
          <Logo />
          <p className="max-w-xs text-heading">Fresh fruits, vegetables and daily essentials from local stores, delivered to your door.</p>
          {settings && (
            <ul className="space-y-3 text-sm">
              <li className="flex items-center gap-2">
                <Phone className="size-4 text-brand" aria-hidden="true" />
                <span className="font-bold text-heading">Call us:</span>
                <a href={phoneHref(settings.supportPhone)} className="text-brand-text hover:underline">
                  {settings.supportPhone}
                </a>
              </li>
              <li className="flex items-center gap-2">
                <Mail className="size-4 text-brand" aria-hidden="true" />
                <span className="font-bold text-heading">Email:</span>
                <a href={`mailto:${settings.supportEmail}`} className="text-brand-text hover:underline">
                  {settings.supportEmail}
                </a>
              </li>
            </ul>
          )}
        </div>

        <FooterColumn title="Shop" links={SHOP_LINKS} />
        <FooterColumn title="Account" links={ACCOUNT_LINKS} />
        {categoryLinks.length > 0 && <FooterColumn title="Popular" links={categoryLinks} />}
      </div>

      <div className="border-t border-line">
        {/* The bottom right corner stays free for the assistant's and the back-to-top buttons (fixed there): centred in
            a column on smaller screens, and lg:pr-56 once the row reaches the right edge */}
        <div className="page-container flex flex-col items-center justify-between gap-4 py-6 text-center text-sm lg:flex-row lg:pr-56 lg:text-left">
          <p>© {CURRENT_YEAR} Fresh Grocery. All rights reserved.</p>
          {settings && (
            <a href={phoneHref(settings.supportPhone)} className="flex items-center gap-2.5">
              <Headset className="size-8 text-heading" strokeWidth={1.5} aria-hidden="true" />
              <span className="flex flex-col leading-tight">
                <span className="font-heading text-xl font-bold text-brand-text">{settings.supportPhone}</span>
                <span className="text-xs">Support Center</span>
              </span>
            </a>
          )}
          <p>Pay cash on delivery or online</p>
        </div>
      </div>
    </footer>
  )
}
