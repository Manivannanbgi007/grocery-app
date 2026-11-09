import { Bell, IdCard, MapPin, Wallet } from 'lucide-react'
import { Outlet, ScrollRestoration } from 'react-router'
import { Logo } from '../../components/layout/Logo'
import { PageEnter } from '../../components/layout/PageEnter'
import { NotificationBell } from '../../components/layout/NotificationBell'
import { PanelSidebar } from '../../components/panel/PanelSidebar'
import { PanelTopBar } from '../../components/panel/PanelTopBar'
import type { ProfileAction } from '../../components/profile/ProfileMenu'
import { cn } from '../../utils/cn'
import { PanelAssistantButton } from '../assistant/AssistantButton'
import { partnerGateState } from './gate/partnerGateState'
import { PartnerGate } from './gate/PartnerGate'
import { DeliveryBottomNav } from './layout/DeliveryBottomNav'
import { DELIVERY_LINKS } from './layout/deliveryNavLinks'
import { OnlineSwitch } from './layout/OnlineSwitch'
import { RequirePartner } from './RequirePartner'
import { useAvailability, usePartnerProfile, useStayOnline } from './usePartner'

const PROFILE_ACTIONS: ProfileAction[] = [
  { to: '/delivery/profile', label: 'My profile', icon: IdCard, tone: 'violet' },
  { to: '/delivery/earnings', label: 'Earnings', icon: Wallet, tone: 'green' },
  { to: '/delivery/notifications', label: 'Notifications', icon: Bell, tone: 'yellow' },
]

/**
 * The frame of the delivery panel (route element of /delivery/...): only for delivery partners, and the pages only open
 * once the KYC is approved (the partner gate shows the KYC form or its state otherwise).
 */
export function DeliveryLayout() {
  return (
    <RequirePartner>
      <DeliveryFrame />
    </RequirePartner>
  )
}

function DeliveryFrame() {
  const profile = usePartnerProfile()
  const state = partnerGateState(profile)
  const ready = state === 'ready'
  const { data: availability } = useAvailability(ready)
  useStayOnline(availability)

  return (
    <div className="flex min-h-screen">
      <a href="#main" className="sr-only rounded-md bg-brand px-4 py-2 font-bold text-white focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50">
        Skip to content
      </a>
      {/* Big screens: the menu on the left; phones and tablets: the bar at the bottom (no drawer) */}
      {ready && (
        <PanelSidebar
          name="Delivery panel"
          home="/delivery"
          groups={[{ links: DELIVERY_LINKS }]}
          header={
            <div className="flex items-center gap-3 rounded-2xl border border-line bg-linear-to-br from-sale-light to-card p-3">
              <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-linear-to-br from-sale to-sale-text text-white shadow-lg shadow-sale/30">
                <MapPin className="size-5" aria-hidden="true" />
              </span>
              <div className="min-w-0">
                <p className="text-xs">Your zone</p>
                <p className="truncate font-heading font-bold text-heading">{profile.data?.zoneName ?? '–'}</p>
              </div>
            </div>
          }
        />
      )}
      <div className="flex min-w-0 flex-1 flex-col">
        <PanelTopBar
          loginPath="/delivery/login"
          actions={<NotificationBell />}
          profileActions={PROFILE_ACTIONS}
          profileDetails={profile.data?.zoneName ? [{ label: 'Zone', value: profile.data.zoneName, icon: MapPin }] : []}
        >
          {/* Phones have no menu on the left: the leaf mark leads to today */}
          <Logo to="/delivery" label="Delivery panel, today" compact className="lg:hidden" />
          {ready ? <OnlineSwitch availability={availability} /> : <span className="font-heading font-bold text-heading">Delivery panel</span>}
        </PanelTopBar>
        {/* Room under the last row (e.g. the page controls), so the assistant's button never covers it; below lg also for
            the bottom bar, with the button raised above it (pb-40) */}
        <main id="main" className={cn('flex-1 px-4 pt-4 md:px-6 md:pt-6 lg:px-8 lg:pt-8', ready ? 'pb-40 lg:pb-24' : 'pb-24')}>
          {ready ? (
            <PageEnter>
              <Outlet />
            </PageEnter>
          ) : (
            <PartnerGate state={state} />
          )}
        </main>
      </div>
      {ready && <DeliveryBottomNav />}
      {/* Also before the KYC is approved: the assistant can tell the partner where it stands */}
      <PanelAssistantButton kind="delivery" aboveBottomBar={ready} />
      <ScrollRestoration />
    </div>
  )
}
