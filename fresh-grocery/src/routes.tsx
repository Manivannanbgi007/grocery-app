import type { RouteObject } from 'react-router'
import { MainLayout } from './components/layout/MainLayout'
import { PageLoading } from './components/layout/PageLoading'
import { NotFoundPage } from './features/errors/NotFoundPage'
import { RouteErrorPage } from './features/errors/RouteErrorPage'
import { HomePage } from './features/home/HomePage'

/**
 * Every page of the app. Used by App.tsx (browser router) and by the tests (memory router).
 *
 * The shop's pages sit inside MainLayout (header, menus, footer); its login and sign-up pages are full screen; the vendor
 * panel (/vendor/...), the delivery panel (/delivery/...) and the admin panel (/admin/...) have their own layouts.
 * Two error pages:
 * - the inner one: a page crashed, the header and footer stay;
 * - the outer one: the layout itself crashed.
 *
 * Code splitting: the home page is part of the main JavaScript file (most visits start there). The other pages use
 * `lazy`: each one is a separate small file that the browser only downloads the first time that page is opened.
 */
export const routes: RouteObject[] = [
  {
    path: '/',
    element: <MainLayout />,
    errorElement: <RouteErrorPage />,
    hydrateFallbackElement: <PageLoading />,
    children: [
      {
        errorElement: <RouteErrorPage />,
        children: [
          { index: true, element: <HomePage /> },
          { path: 'customer/home', lazy: () => import('./features/home/CustomerHomePage').then((page) => ({ Component: page.CustomerHomePage })) },
          { path: 'shop', lazy: () => import('./features/shop/ShopPage').then((page) => ({ Component: page.ShopPage })) },
          { path: 'products/:productId', lazy: () => import('./features/product/ProductDetailPage').then((page) => ({ Component: page.ProductDetailPage })) },
          { path: 'cart', lazy: () => import('./features/cart/CartPage').then((page) => ({ Component: page.CartPage })) },
          { path: 'checkout', lazy: () => import('./features/checkout/CheckoutPage').then((page) => ({ Component: page.CheckoutPage })) },
          { path: 'orders', lazy: () => import('./features/orders/OrdersPage').then((page) => ({ Component: page.OrdersPage })) },
          { path: 'orders/track', lazy: () => import('./features/tracking/TrackOrderPage').then((page) => ({ Component: page.TrackOrderPage })) },
          { path: 'orders/:orderId', lazy: () => import('./features/orders/OrderPage').then((page) => ({ Component: page.OrderPage })) },
          { path: 'orders/:orderId/track', lazy: () => import('./features/tracking/OrderTrackingPage').then((page) => ({ Component: page.OrderTrackingPage })) },
          { path: 'stores', lazy: () => import('./features/stores/StoresPage').then((page) => ({ Component: page.StoresPage })) },
          { path: 'stores/:storeId', lazy: () => import('./features/stores/StorePage').then((page) => ({ Component: page.StorePage })) },
          { path: 'wishlist', lazy: () => import('./features/wishlist/WishlistPage').then((page) => ({ Component: page.WishlistPage })) },
          { path: 'compare', lazy: () => import('./features/compare/ComparePage').then((page) => ({ Component: page.ComparePage })) },
          { path: 'account', lazy: () => import('./features/account/AccountPage').then((page) => ({ Component: page.AccountPage })) },
          { path: 'account/addresses', lazy: () => import('./features/account/AddressBookPage').then((page) => ({ Component: page.AddressBookPage })) },
          { path: 'account/notifications', lazy: () => import('./features/account/AccountNotificationsPage').then((page) => ({ Component: page.AccountNotificationsPage })) },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
  // The shop's login and sign-up pages: full screen, without the shop's header and footer (AuthScreen is their frame)
  {
    errorElement: <RouteErrorPage />,
    hydrateFallbackElement: <PageLoading />,
    children: [
      { path: '/login', lazy: () => import('./features/auth/LoginPage').then((page) => ({ Component: page.LoginPage })) },
      { path: '/register', lazy: () => import('./features/auth/RegisterPage').then((page) => ({ Component: page.RegisterPage })) },
      { path: '/forgot-password', lazy: () => import('./features/auth/ForgotPasswordPage').then((page) => ({ Component: page.ForgotPasswordPage })) },
      { path: '/reset-password', lazy: () => import('./features/auth/ResetPasswordPage').then((page) => ({ Component: page.ResetPasswordPage })) },
      // Where a login with Google (Microsoft, Facebook, Apple) comes back to (docs/social-login.md)
      { path: '/login/social', lazy: () => import('./features/auth/SocialLoginPage').then((page) => ({ Component: page.SocialLoginPage })) },
    ],
  },
  // The vendor panel: its own login and its own layout (menu, top bar, store gate); none of it is in the main file
  {
    path: '/vendor',
    errorElement: <RouteErrorPage />,
    hydrateFallbackElement: <PageLoading />,
    children: [
      { path: 'login', lazy: () => import('./features/vendor/VendorLoginPage').then((page) => ({ Component: page.VendorLoginPage })) },
      {
        lazy: () => import('./features/vendor/VendorLayout').then((layout) => ({ Component: layout.VendorLayout })),
        children: [
          {
            errorElement: <RouteErrorPage />,
            children: [
              { index: true, lazy: () => import('./features/vendor/dashboard/VendorDashboardPage').then((page) => ({ Component: page.VendorDashboardPage })) },
              { path: 'orders', lazy: () => import('./features/vendor/orders/VendorOrdersPage').then((page) => ({ Component: page.VendorOrdersPage })) },
              { path: 'orders/:orderId', lazy: () => import('./features/vendor/orders/VendorOrderPage').then((page) => ({ Component: page.VendorOrderPage })) },
              { path: 'products', lazy: () => import('./features/vendor/products/VendorProductsPage').then((page) => ({ Component: page.VendorProductsPage })) },
              { path: 'products/new', lazy: () => import('./features/vendor/products/VendorNewProductPage').then((page) => ({ Component: page.VendorNewProductPage })) },
              { path: 'products/:productId', lazy: () => import('./features/vendor/products/VendorProductPage').then((page) => ({ Component: page.VendorProductPage })) },
              { path: 'coupons', lazy: () => import('./features/vendor/coupons/VendorCouponsPage').then((page) => ({ Component: page.VendorCouponsPage })) },
              { path: 'reviews', lazy: () => import('./features/vendor/reviews/VendorReviewsPage').then((page) => ({ Component: page.VendorReviewsPage })) },
              { path: 'store', lazy: () => import('./features/vendor/store/VendorStorePage').then((page) => ({ Component: page.VendorStorePage })) },
              { path: 'notifications', lazy: () => import('./features/notifications/PanelNotificationsPage').then((page) => ({ Component: page.PanelNotificationsPage })) },
              { path: '*', lazy: () => import('./features/vendor/VendorNotFoundPage').then((page) => ({ Component: page.VendorNotFoundPage })) },
            ],
          },
        ],
      },
    ],
  },
  // The delivery partner panel: the same idea, designed for a phone first
  {
    path: '/delivery',
    errorElement: <RouteErrorPage />,
    hydrateFallbackElement: <PageLoading />,
    children: [
      { path: 'login', lazy: () => import('./features/delivery/DeliveryLoginPage').then((page) => ({ Component: page.DeliveryLoginPage })) },
      {
        lazy: () => import('./features/delivery/DeliveryLayout').then((layout) => ({ Component: layout.DeliveryLayout })),
        children: [
          {
            errorElement: <RouteErrorPage />,
            children: [
              { index: true, lazy: () => import('./features/delivery/tasks/DeliveryTodayPage').then((page) => ({ Component: page.DeliveryTodayPage })) },
              { path: 'deliveries/:deliveryId', lazy: () => import('./features/delivery/tasks/DeliveryTaskPage').then((page) => ({ Component: page.DeliveryTaskPage })) },
              { path: 'history', lazy: () => import('./features/delivery/tasks/DeliveryHistoryPage').then((page) => ({ Component: page.DeliveryHistoryPage })) },
              { path: 'earnings', lazy: () => import('./features/delivery/earnings/DeliveryEarningsPage').then((page) => ({ Component: page.DeliveryEarningsPage })) },
              { path: 'profile', lazy: () => import('./features/delivery/profile/DeliveryProfilePage').then((page) => ({ Component: page.DeliveryProfilePage })) },
              { path: 'notifications', lazy: () => import('./features/notifications/PanelNotificationsPage').then((page) => ({ Component: page.PanelNotificationsPage })) },
              { path: '*', lazy: () => import('./features/delivery/DeliveryNotFoundPage').then((page) => ({ Component: page.DeliveryNotFoundPage })) },
            ],
          },
        ],
      },
    ],
  },
  // The admin panel (admins and the super admin): the same idea as the vendor panel
  {
    path: '/admin',
    errorElement: <RouteErrorPage />,
    hydrateFallbackElement: <PageLoading />,
    children: [
      { path: 'login', lazy: () => import('./features/admin/AdminLoginPage').then((page) => ({ Component: page.AdminLoginPage })) },
      {
        lazy: () => import('./features/admin/AdminLayout').then((layout) => ({ Component: layout.AdminLayout })),
        children: [
          {
            errorElement: <RouteErrorPage />,
            children: [
              { index: true, lazy: () => import('./features/admin/dashboard/AdminDashboardPage').then((page) => ({ Component: page.AdminDashboardPage })) },
              { path: 'stores', lazy: () => import('./features/admin/stores/AdminStoresPage').then((page) => ({ Component: page.AdminStoresPage })) },
              { path: 'stores/:storeId', lazy: () => import('./features/admin/stores/AdminStorePage').then((page) => ({ Component: page.AdminStorePage })) },
              { path: 'orders', lazy: () => import('./features/admin/orders/AdminOrdersPage').then((page) => ({ Component: page.AdminOrdersPage })) },
              { path: 'orders/:orderId', lazy: () => import('./features/admin/orders/AdminOrderPage').then((page) => ({ Component: page.AdminOrderPage })) },
              { path: 'payments', lazy: () => import('./features/admin/payments/AdminPaymentsPage').then((page) => ({ Component: page.AdminPaymentsPage })) },
              { path: 'deliveries', lazy: () => import('./features/admin/deliveries/AdminDeliveriesPage').then((page) => ({ Component: page.AdminDeliveriesPage })) },
              { path: 'partners', lazy: () => import('./features/admin/partners/AdminPartnersPage').then((page) => ({ Component: page.AdminPartnersPage })) },
              { path: 'zones', lazy: () => import('./features/admin/partners/AdminZonesPage').then((page) => ({ Component: page.AdminZonesPage })) },
              { path: 'accounts', lazy: () => import('./features/admin/accounts/AdminAccountsPage').then((page) => ({ Component: page.AdminAccountsPage })) },
              { path: 'accounts/:userId', lazy: () => import('./features/admin/accounts/AdminAccountPage').then((page) => ({ Component: page.AdminAccountPage })) },
              { path: 'products', lazy: () => import('./features/admin/catalog/AdminProductsPage').then((page) => ({ Component: page.AdminProductsPage })) },
              { path: 'categories', lazy: () => import('./features/admin/catalog/AdminCategoriesPage').then((page) => ({ Component: page.AdminCategoriesPage })) },
              { path: 'reviews', lazy: () => import('./features/admin/catalog/AdminReviewsPage').then((page) => ({ Component: page.AdminReviewsPage })) },
              { path: 'coupons', lazy: () => import('./features/admin/marketing/AdminCouponsPage').then((page) => ({ Component: page.AdminCouponsPage })) },
              { path: 'banners', lazy: () => import('./features/admin/marketing/AdminBannersPage').then((page) => ({ Component: page.AdminBannersPage })) },
              { path: 'reports', lazy: () => import('./features/admin/insights/AdminReportsPage').then((page) => ({ Component: page.AdminReportsPage })) },
              { path: 'notifications', lazy: () => import('./features/admin/insights/AdminNotificationsPage').then((page) => ({ Component: page.AdminNotificationsPage })) },
              // The Owner pages: only the super admin sees them (an admin is told they are for the super admin)
              {
                lazy: () => import('./features/admin/owner/SuperAdminOnly').then((guard) => ({ Component: guard.SuperAdminOnly })),
                children: [
                  { path: 'admins', lazy: () => import('./features/admin/owner/admins/AdminAdminsPage').then((page) => ({ Component: page.AdminAdminsPage })) },
                  { path: 'settings', lazy: () => import('./features/admin/owner/settings/AdminSettingsPage').then((page) => ({ Component: page.AdminSettingsPage })) },
                  { path: 'feature-flags', lazy: () => import('./features/admin/owner/flags/AdminFeatureFlagsPage').then((page) => ({ Component: page.AdminFeatureFlagsPage })) },
                  { path: 'commission', lazy: () => import('./features/admin/owner/commission/AdminCommissionPage').then((page) => ({ Component: page.AdminCommissionPage })) },
                  { path: 'audit-log', lazy: () => import('./features/admin/owner/audit/AdminAuditLogPage').then((page) => ({ Component: page.AdminAuditLogPage })) },
                  { path: 'system', lazy: () => import('./features/admin/owner/system/AdminSystemPage').then((page) => ({ Component: page.AdminSystemPage })) },
                ],
              },
              { path: '*', lazy: () => import('./features/admin/AdminNotFoundPage').then((page) => ({ Component: page.AdminNotFoundPage })) },
            ],
          },
        ],
      },
    ],
  },
]
