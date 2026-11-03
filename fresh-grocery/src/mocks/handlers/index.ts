import { adminHandlers } from './admin'
import { authHandlers } from './auth'
import { cartHandlers } from './cart'
import { catalogHandlers } from './catalog'
import { chatHandlers } from './chat'
import { customerHandlers } from './customers'
import { monitoringHandlers } from './monitoring'
import { deliveryHandlers } from './delivery'
import { notificationHandlers } from './notifications'
import { orderHandlers } from './orders'
import { platformHandlers } from './platform'
import { socialAuthHandlers } from './socialAuth'
import { superAdminHandlers } from './superAdmin'
import { trackingHandlers } from './tracking'
import { vendorHandlers } from './vendor'

/** Every mocked API endpoint. Used by the browser (npm run dev:mock) and by the tests. */
export const handlers = [
  ...adminHandlers,
  ...authHandlers,
  ...cartHandlers,
  ...catalogHandlers,
  ...chatHandlers,
  ...customerHandlers,
  ...deliveryHandlers,
  ...monitoringHandlers,
  ...notificationHandlers,
  ...orderHandlers,
  ...platformHandlers,
  ...socialAuthHandlers,
  ...superAdminHandlers,
  ...trackingHandlers,
  ...vendorHandlers,
]
