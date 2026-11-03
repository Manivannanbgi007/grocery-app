import { useState } from 'react'
import { Outlet, ScrollRestoration } from 'react-router'
import { PageEnter } from '../../components/layout/PageEnter'
import { PanelAssistantButton } from '../assistant/AssistantButton'
import { useAuth } from '../auth/authContext'
import { VendorSidebar } from './layout/VendorSidebar'
import { VendorTopBar } from './layout/VendorTopBar'
import { OrderStreamContext, useStoreOrderStream } from './orders/useStoreOrderStream'
import { RequireVendor } from './RequireVendor'
import { gateState } from './store/gateState'
import { StoreGate } from './store/StoreGate'
import { useMyStore } from './useVendorStore'

/**
 * The frame of the vendor panel (route element of /vendor/...): only for vendors, and the pages only open once the
 * store is approved and known to the login (the store gate shows the registration or the approval state otherwise).
 * It follows the store's orders live (one stream for every page: the queue, its counters, the menu, an order's page).
 */
export function VendorLayout() {
  return (
    <RequireVendor>
      <VendorFrame />
    </RequireVendor>
  )
}

function VendorFrame() {
  const { vendor } = useAuth()
  const storeQuery = useMyStore()
  const [menuOpen, setMenuOpen] = useState(false)
  const state = gateState(storeQuery, vendor)
  const store = state === 'ready' ? storeQuery.data : undefined
  const orderStream = useStoreOrderStream(state === 'ready')

  return (
    <OrderStreamContext value={orderStream}>
      <div className="flex min-h-screen">
        <a href="#main" className="sr-only rounded-md bg-brand px-4 py-2 font-bold text-white focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50">
          Skip to content
        </a>
        {store && <VendorSidebar store={store} open={menuOpen} onClose={() => setMenuOpen(false)} />}
        <div className="flex min-w-0 flex-1 flex-col">
          <VendorTopBar store={store} onOpenMenu={store ? () => setMenuOpen(true) : undefined} />
          {/* pb-24: room under the last row (e.g. the page controls), so the assistant's button in the corner never covers it */}
          <main id="main" className="flex-1 px-4 pt-4 pb-24 md:px-6 md:pt-6 lg:px-8 lg:pt-8">
            {state === 'ready' ? (
              <PageEnter>
                <Outlet />
              </PageEnter>
            ) : (
              <StoreGate state={state} />
            )}
          </main>
        </div>
        {/* Also before the approval: the assistant can tell the vendor where the store stands */}
        <PanelAssistantButton kind="vendor" />
        <ScrollRestoration />
      </div>
    </OrderStreamContext>
  )
}
