import { ShieldCheck } from 'lucide-react'
import { PanelSidebar, type PanelLink } from '../../../components/panel/PanelSidebar'
import { useAuth } from '../../auth/authContext'
import { useDeliveryCount, usePartnerCount, useStoreCount } from '../useWorkCounts'
import { ADMIN_LINK_GROUPS, OWNER_LINK_GROUP } from './adminNavLinks'

interface AdminSidebarProps {
  /** phones: the menu slides in when open */
  open: boolean
  onClose: () => void
}

/**
 * The admin panel's menu, with the work that waits next to the pages (e.g. stores waiting for approval). The super admin
 * also gets the Owner pages.
 */
export function AdminSidebar({ open, onClose }: AdminSidebarProps) {
  const { admin } = useAuth()
  const { data: storesWaiting = 0 } = useStoreCount('PENDING_APPROVAL')
  const { data: deliveriesWaiting = 0 } = useDeliveryCount('WAITING_FOR_PARTNER')
  const { data: kycWaiting = 0 } = usePartnerCount('PENDING')

  /** The number shown next to a page, and what it counts. */
  function withCount(link: PanelLink): PanelLink {
    if (link.to === '/admin/stores') return { ...link, count: storesWaiting, countLabel: 'waiting for approval' }
    if (link.to === '/admin/deliveries') return { ...link, count: deliveriesWaiting, countLabel: 'waiting for a partner' }
    if (link.to === '/admin/partners') return { ...link, count: kycWaiting, countLabel: 'waiting for a KYC review' }
    return link
  }

  const superAdmin = admin?.role === 'SUPER_ADMIN'
  const groups = [...ADMIN_LINK_GROUPS, ...(superAdmin ? [OWNER_LINK_GROUP] : [])].map((group) => ({ ...group, links: group.links.map(withCount) }))

  return (
    <PanelSidebar
      name="Admin panel"
      home="/admin"
      groups={groups}
      dark
      header={
        <div className="flex items-center gap-3 rounded-2xl border border-ink-line bg-linear-to-br from-white/10 to-white/0 px-3 py-3">
          <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-linear-to-br from-brand to-brand-dark shadow-lg shadow-brand/30">
            <ShieldCheck className="size-5 text-white" aria-hidden="true" />
          </span>
          <div className="min-w-0">
            <p className="text-xs text-ink-text">{superAdmin ? 'Super admin' : 'Platform team'}</p>
            <p className="truncate font-heading font-bold text-white">Fresh Grocery</p>
          </div>
        </div>
      }
      open={open}
      onClose={onClose}
    />
  )
}
