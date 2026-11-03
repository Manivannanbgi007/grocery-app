# Fresh Grocery (storefront, vendor panel, delivery panel, admin panel)

The website of the grocery platform (an AI assistant in the corner of the shop and of every panel): the shop for customers (with the stores, and
online payment with Razorpay or the test gateway); under `/vendor` the panel where a store runs its orders,
products, stock, coupons and settings; under `/delivery` the delivery partners' panel, made for a phone (KYC, online,
deliveries step by step with a proof photo, history, earnings); under `/admin` the platform team's panel (stores, orders,
deliveries, payments, accounts, catalog, coupons, banners, reports). The super admin uses the same panel with an extra
**Owner** menu: admins and roles, platform settings, feature flags, commission, the audit log and the system status.
React 19 + TypeScript + Tailwind CSS 4, built with Vite.
It talks to the backend only through the API gateway (`http://localhost:8080`).

- Plan, design decisions and phases: [../docs/frontend-development-plan.md](../docs/frontend-development-plan.md)
- Sample screens it follows: [../image-screens](../image-screens)

## Run it

**With the real backend** (start the infrastructure and the services first, see the root README; the shop and cart need
api-gateway, auth, customer, store, product, product-read, inventory, cart and platform; checkout and the order pages also
order and payment; the bell notification; the delivery rating, the live tracking map and the delivery panel delivery (the
map also needs internet for its OpenStreetMap tiles); the vendor panel's sales
report; the admin panel uses all of them; the super admin's System page also needs Prometheus, one of the monitoring tools;
the shopping assistant needs mcp-client-service, mcp-server-service and Ollama, see the root README step 6):

```powershell
cd fresh-grocery
npm install
npm run dev          # http://localhost:5173
```

The Vite dev server forwards every `/api/...` call to the gateway on port 8080 (see `vite.config.ts`), so the app itself only
ever calls its own address. Photos (`/media/...`) and proof-of-delivery photos (`/delivery-media/...`) go the same way;
`/prometheus/...` goes to Prometheus on port 9090 (the super admin's System page; in production that needs a protected route
or a health endpoint in the gateway).

**Without a backend** (demo data in the browser, Mock Service Worker):

```powershell
npm run dev:mock     # http://localhost:5173
```

Every API call is then answered in the browser with the demo catalog; you see them in the browser console
(`[MSW] GET /api/v1/...`). The mock carts are kept in memory, so a page reload resets them.

Log in with a seed customer: `customer1@grocery.com` or `customer2@grocery.com`, password `Password@123`
(both modes have the same accounts). After logging in, a customer lands on their own home page,
http://localhost:5173/customer/home (the shop's home page, only for logged-in customers), or back on the page that sent
them to the login (`?next=`). The vendor panel is at http://localhost:5173/vendor: `vendor1@grocery.com` (Fresh Mart)
or `vendor2@grocery.com` (Green Basket), same password. The delivery panel is at http://localhost:5173/delivery:
`delivery1@grocery.com` (Arjun, zone Indiranagar) or `delivery2@grocery.com` (Karthik). The admin panel is at
http://localhost:5173/admin: `admin@grocery.com`, or `superadmin@grocery.com` for the Owner pages. One login per browser; the role decides which
side it opens (`/login` is for customers, `/vendor/login` for vendors, `/delivery/login` for delivery partners,
`/admin/login` for admins and the super admin). The login and sign-up pages are full screen (no shop header), and while
developing they list the demo accounts: one click fills in the form. Every account and tool login is listed in
[../docs/local-accounts-and-tools.md](../docs/local-accounts-and-tools.md).

| Script | What it does |
|---|---|
| `npm run dev` | Development server with hot reload, real backend (`.env.development`) |
| `npm run dev:mock` | The same with the mock API (`.env.mock`) |
| `npm run test` | Unit and component tests (Vitest + React Testing Library + the mock API) |
| `npm run test:watch` | Tests again on every save |
| `npm run e2e` | Smoke tests in a real browser against the real backend (see below) |
| `npm run lint` | Oxlint |
| `npm run build` | Type check + production build into `dist/` |
| `npm run preview` | Serve the production build |
| `npm run seed:sql` | Writes the backend's demo seed files from `src/mocks/data/` (see "One catalog, two uses") |

## Where things are

```text
src/
├── main.tsx, App.tsx, routes.tsx   start-up, providers, the list of pages (all but Home load on first use: `lazy`)
├── styles/index.css                Tailwind + the theme (colours, fonts) and the dark theme's colour values
├── config/tracking.ts              the delivery tracking maps' settings: tile server, attribution, timings (VITE_MAP_*)
├── api/                            the ONLY place that calls the backend: client.ts (fetch wrapper), types.ts, one file per API
│                                   (monitoring.ts asks Prometheus for the System page)
├── mocks/                          demo catalog (data/), the mocked endpoints (handlers/), the seed SQL generator
├── hooks/                          shared hooks: data (useCategories, useProductSearch, useProductSuggestions, useDeals
│                                   ...) and helpers (useLocalStorageList, useDismiss, useDebouncedValue, useCountdown,
│                                   useScrollRow)
├── components/layout/              header, menus, footer, banners, breadcrumb: the frame around every page; the app's
│                                   "Loading…" sign (LoadingIndicator); the search box with its suggestions dropdown
│                                   (SearchBar, SearchSuggestions: products from Elasticsearch while typing)
├── components/ui/                  small reusable pieces: Button, TextField, Price, StarRating, Pagination, TablePagination
│                                   (the page controls above and below a table), Spinner and LoadingState (the one
│                                   spinner), StepBar (steps in a row), Select and SelectField (drop-downs), DateField and
│                                   Calendar, TimeField, DateTimeField, Countdown ...
├── components/profile/             the profile popup of the shop and the panels (ProfileMenu) and the initials Avatar
├── components/charts/              AreaChart (a trend over time), DonutChart (part of a whole) and their scale helpers
├── components/product/             product pieces used on many pages: ProductCard, ProductGrid, ProductImage, AddToCartButton,
│                                   WishlistButton and CompareButton (round on cards, a text button on the product page),
│                                   ProductRail (a titled row of small products that scrolls sideways), StockText ("70 in
│                                   stock") ...
├── components/auth/                the full-screen frame of every login and sign-up page (AuthScreen), each panel's look
│                                   (authLooks.ts), and the illustrations/ (inline SVG drawings: basket, store, rider ...)
├── components/panel/               what the panels share: menu (white, or dark for the admin panel; collapses to icons
│                                   from `lg`), top bar, status tabs,
│                                   search box, filter drop-down, PIN code list editor
├── components/sales/               the sales pieces of the dashboards: per-day chart (also the partner's earnings), totals and
│                                   number tiles, per-day card, orders by outcome (donut), top lists, period picker, from/to
│                                   period form
├── components/map/                 the delivery map (Leaflet + OpenStreetMap, loaded on demand): markers, route line, fit view
├── features/<page>/                one folder per page with the components only that page uses (auth, cart, checkout, orders,
│                                   account, home, shop ...)
├── features/assistant/             the AI assistant: chat button and panel (ChatPanel: title bar and question box), the
│                                   messages (ChatConversation: welcome, bubbles, typing, error, copy, listen), conversation
│                                   per login (useChat); voice: the microphone (useVoiceRecorder) and answers read aloud
│                                   (useReadAloud); the shop's and each panel's texts (assistantTexts.ts);
│                                   PanelAssistantButton in the three panels; the cards to confirm an order, a
│                                   cancellation or a change of the cart under an answer (ChatActionCard)
├── features/notifications/         the notification rows, their icons (notificationLooks.ts) and links
│                                   (notificationLinks.ts), used by the bell and the notifications page of each role
├── features/product/               the product page: photos and zoom (ProductGallery), the tabs (ProductTabs), related products
├── features/wishlist/              the wishlist (saved in the browser, useWishlist), its page, the pop-up a heart opens
│                                   (WishlistPopup; open/closed in wishlistPopupState.ts), the row both use (WishlistItem),
│                                   recently viewed products
├── features/compare/               the product comparison: the chosen products (useCompare, at most 4, in the browser), the
│                                   bar at the bottom while products are chosen (CompareBar), the table page (/compare)
├── features/stores/                the stores list and a store's page
├── features/tracking/              the customer's live tracking page (/orders/:id/track) and its live connection
├── features/coupons/               the coupon form and its rules, used by the vendor and the admin panel
├── features/theme/                 the theme switch on the right edge of every page: the choice (light, dark, same as
│                                   device), saved in the browser (useTheme), put on <html data-theme>
├── features/vendor/                the vendor panel: layout/ (menu, top bar), store/ (store gate, settings), orders/,
│                                   products/, coupons/, reviews/, reports/, dashboard/
├── features/delivery/              the delivery panel: layout/ (menu, bottom bar on phones, online switch), gate/ (partner
│                                   gate and joining steps), kyc/, tasks/
│                                   (today, a delivery, history), earnings/, profile/, tracking/ (sharing the location)
├── features/admin/                 the admin panel: layout/, dashboard/, stores/, orders/, deliveries/, payments/, partners/
│                                   (and zones), accounts/, catalog/ (products, categories, reviews), marketing/ (coupons,
│                                   banners), insights/ (reports, notifications), reports/ (report hooks), owner/ (the super
│                                   admin's pages: admins/, settings/, flags/, commission/, audit/, system/)
├── utils/                          plain functions: formatPrice, emoji placeholders, links, address lines, file download, cn
└── test/                           test set-up and render helpers
e2e/                                Playwright smoke tests (real backend)
scripts/                            generate-seed-sql.ts
```

Rules that keep the code easy to follow:

- API calls only in `src/api/`; components get server data through hooks (React Query), never with `fetch` in a component.
- One component per file; props are typed; logic goes into hooks or `utils/`, not into JSX.
- Shared pieces go in `components/`; something used by one page stays in `features/<page>/`.
- Every React Query key comes from `api/queryKeys.ts`.
- **Live data:** the app's React Query defaults (`api/queryClient.ts`: fresh for 60 s, nothing asked again when the
  browser tab is shown again) suit data that rarely changes. Data that must stay current is pushed by the server with
  Server-Sent Events, never asked for every few seconds:
  - The server says *what* changed; the page then marks those React Query keys as changed (`invalidateQueries`), and
    the normal REST calls load what is on screen. Examples: `features/vendor/orders/useStoreOrderStream.ts` (the vendor
    panel's orders, opened once in `VendorLayout`) and `features/tracking/useTrackingStream.ts` (the delivery map).
  - A stream is read with `apiStream()` and `readEventStream()` (`EventSource` cannot send the login token). It
    reconnects after a break and treats 45 s of silence as a dead connection (the server sends a keep-alive every 15 s).
  - Polling stays as the safety net only: slow while the stream is live, faster while it is down (`useLiveQueue` in
    `features/vendor/orders/useVendorOrders.ts`), with `staleTime: 0` and `refetchOnWindowFocus: true`.
  - A failed refresh keeps the last list on screen; only a failed first load shows the error page.
  - Test refresh timing with `renderApp(path, appQueryClient())`: the tests' usual client has React Query's own
    defaults, which refresh anyway and would hide the problem. The mock API streams too (`mocks/mockOrderStream.ts`,
    `mocks/mockTracking.ts`).
- The shop's filters live in the URL (`features/shop/shopFilters.ts`), never in component state, so links, refresh and
  the Back button all work.
- Pop-ups over the page (the wishlist, an enlarged photo) are portals with `role="dialog"` and `aria-modal`; they move the
  focus inside when they open, keep Tab inside (`utils/focusTrap.ts`), close with Escape and give the focus back.
- The login lives in `features/auth` (`useAuth()`); the cart in `features/cart/useCart.ts`. Every cart change returns the
  whole cart, which replaces the cached one, so the header counter and the cart page always agree.
- The access token (15 minutes) lives in memory only (`api/session.ts`), never in `localStorage`; the refresh token is an
  HttpOnly cookie the app cannot read. `api/client.ts` renews the token before it expires and once after a 401, with one
  `/auth/refresh` for all calls; after a reload the AuthProvider gets the login back with the cookie (pages that need a
  login show "Checking your login…" meanwhile). Logout tells the server and the other tabs. See
  [docs/authentication.md](../docs/authentication.md).
- Every login and sign-up page (and "this panel is for …") uses `components/auth/AuthScreen.tsx` with a look: `shop`,
  `vendor`, `delivery` or `admin`. A new panel's look (colours, illustration, three points) is one entry in `authLooks.ts`.
  The illustrations are React components drawing inline SVG: decoration only (`aria-hidden`), in the theme's colours.
- The customer's own data (orders, addresses, profile, notifications) is cached under `['me', userId, ...]` and dropped with
  the cart on every login and logout.
- Orders move on in the background (stock, payment, store, delivery). The order page asks again by itself; how often is decided
  in one place: `pollInterval()` in `features/orders/orderStatus.ts`.
- Online payment: the payment says which gateway payment-service runs (`gatewayOrderId` "mock_order_…" = the test gateway,
  "order_…" = Razorpay); `features/orders/razorpayCheckout.ts` loads Razorpay's `checkout.js` only when someone pays.
- The assistant's conversation id is kept in sessionStorage per login (`features/assistant/chatStorage.ts`): a
  reload shows the chat again, a login or logout starts a new one. Answers are shown as plain text, never as HTML.
- One assistant, five audiences: the shop (guests, customers) and the vendor, delivery and admin panels use the same chat
  API and components; the backend picks the tools and the rules from the login, the frontend only changes the texts
  (`assistantTexts.ts`). A new panel's assistant is one entry there plus `<PanelAssistantButton kind=… />` in its layout.
- The shop's assistant can change a customer's cart ("add 2 kg tomatoes"); the cart is loaded again after every answer.
  Orders and cancellations it only prepares: the answer carries a card (`ChatReply.action`) built by the backend from
  the shop's data, and only the customer's click on its Confirm places or cancels the order, with this browser's own call
  to order-service (`ChatActionCard`: an `Idempotency-Key` per card and the summary's checkout fingerprint; after 10
  minutes the card asks for a fresh summary). The confirmed card goes with the next question (`confirmed`), so the
  assistant knows about the order ("cancel my last order" right after ordering). After an online order the line has
  **Pay Now** (`PayNowButton`): it opens the page order-service names in `Order.paymentUrl` (the order page, where the
  payment is made: Razorpay Checkout, or the test gateway), never an address made up here; without a valid one the chat
  says how to pay instead. Around it `ChatPaymentStatus` shows the order number, the amount, the payment's status and
  the way to pay (Razorpay, or the test gateway), all from payment-service (`usePayment(orderId, true, true)`, asked
  again every 5 seconds until the payment is settled). When payment-service marks it paid (after checking Razorpay's
  signature or signed webhook) the box becomes "Payment of ₹231 received for order #1001."; a failed payment says why
  and that no money was taken. The browser never decides that an order is paid. Every card shows its **human
  approval** under its title (`ApprovalStatus`): "Pending Human Approval" (amber) until the click, then "✓ Approved"
  (green) or "Rejected" (red) with "by you" and the time, or "Expired" (grey) after 10 minutes without a decision.
  `useChat` sends the decision to `POST /api/v1/chat/approvals/{id}` right after the click, and the conversation's
  history brings each card back with its approval, so the status stays after a reload. Taking a product out of the cart
  ("Can you remove Harpic Toilet Cleaner from my cart?") works the same way: the card shows the cart's line with
  "Confirm removal" and "Keep it", and only the click removes it, with this browser's
  `DELETE /api/v1/cart/items/{productId}` (`removeCartItem`); the cart is then loaded again, and the chat line after
  it comes from cart-service's answer ("Removed Tomato (1 kg) from your cart. Your cart now has 2 products; ...").
  A product that is no longer in the cart (404) is said without another try; a busy service can be tried again.
  "Remove Tomato and Potato" gives one card with both lines ("Keep them"): Confirm removes them one after the other,
  a product that is gone already does not stop the others, and the line after it says which one was gone.
  A new amount ("Make it 3 kg of tomatoes": "Confirm change", `updateCartItem`, the new total) and emptying the cart
  ("Empty my cart": "Empty cart", `clearCart`) have their cards too, and so has adding ("Add 2 kg of tomatoes": each
  product with its packs and price, "Confirm and add" / "Don't add"). Its Confirm reads the cart once and makes each
  product's packs the card's `newQuantity` (`updateCartItem` when it is in the cart, `addCartItem` when not), so a
  second click or a retry adds nothing twice; a product cart-service refuses (another store, the stock) does not stop
  the others, and the line after the click says why. The chat itself changes nothing.
- Voice, on our own speech server (English; no audio leaves our machines): the microphone records with `MediaRecorder`
  (at most 30 s), `/api/v1/chat/transcriptions` writes the question down and it is sent like a typed one; the answer
  to a spoken question is read aloud by itself (`/api/v1/chat/speech`, first sentence first, the rest fetched while it
  plays), and every answer has "Listen". A browser without `MediaRecorder` gets no microphone button.
- Feature flags: `useFeatureFlag('PRODUCT_REVIEWS')` (`hooks/useFeatureFlag.ts`) reads the public flags; while a flag cannot be
  loaded, the feature stays as it is today.
- The vendor's data is cached under `['vendor', userId, ...]` and dropped the same way. Every vendor page sits behind the store
  gate (`features/vendor/store/gateState.ts`): no store yet, waiting for approval, rejected, disabled, or approved while this
  login's token has no store id yet: then the panel gets a new token by itself (`/auth/refresh`, made from the current
  account) and opens; "Try again" and "Log in again" remain for the few seconds before auth-service has linked the store.
- Product changes are saved by product-service but read from product-read-service a moment later (CQRS). After a change the
  vendor's product list is loaded again at once and after 2 s (`features/vendor/products/useVendorProducts.ts`); the admin's
  product and category lists get the answer at once and are loaded again after 2 s (`features/admin/catalog/useAdminCatalog.ts`).
- The admin's data is cached under `['admin', userId, ...]` and dropped the same way. Each panel's roles, start page and login
  page are listed once, in `PANELS` (`features/auth/authContext.ts`).
- The super admin's Owner pages sit inside one route element, `features/admin/owner/SuperAdminOnly.tsx`; the menu group and
  the role and commission cards check `admin.role === 'SUPER_ADMIN'`. Their data is cached under `['admin', userId, ...]` too.
- The delivery partner's data is cached under `['delivery', userId, ...]` and dropped the same way. Every partner page sits
  behind the partner gate (`features/delivery/gate/partnerGateState.ts`): profile being set up, KYC to send, waiting for review,
  rejected. "Online" lasts 30 minutes in the backend; `useStayOnline()` renews it while the panel is open.
- Every list of the panels keeps its filters, tab and page in the URL through `hooks/useUrlFilters.ts`, and so does the
  page size (`?size=50`, left out when it is the list's own size). A paged table shows `TablePagination` twice, with
  `position="top"` and `position="bottom"`; both read the same URL, so they always agree. The list's hook takes the size:
  `useStoreOrders(status, page, pageSize)`. A short list the API returns in full (the admin's categories) is searched
  and paged in the browser with `pageOfList()` (`utils/pagination.ts`), which gives `TablePagination` the same totals.
- Charts: `DailyBarChart` for amounts that are compared one by one (per day, per month), `AreaChart` for a trend over
  time, `DonutChart` for the parts of a whole (at most 4, with a legend that shows every number). Each chart can be read
  with the keyboard and has its numbers in a table too. Colours come from the theme (`brand`, and `chart-good`,
  `chart-wait`, `chart-bad`, `chart-info` for donut parts), which have their own dark-theme values; a new colour must
  first pass the dataviz palette validator in both themes. Add a chart only where it says something the page does not
  already say in numbers.
- Loading: one spinner everywhere (`components/ui/Spinner.tsx`). A page or section shows `<LoadingState>` while its data
  loads for the first time (give it the height of what is coming: `className="min-h-64"`); a button that sends a request
  gets `loading`. The "Loading…" sign at the top (`LoadingIndicator`, once in `App.tsx`) needs nothing: it appears by
  itself when a page, data that is not on screen yet, or a save takes longer than 0.4 s. A request that shows its own
  progress opts out with `meta: { quiet: true }` (the chat, the automatic "stay online"). Keep a `Skeleton` only where it
  draws the shape of the content (product cards, rows).
- Drop-downs, dates and times use our components, never a native `<select>` or `<input type="date">` / `"time"` /
  `"datetime-local"`: `SelectField` (forms), `FilterSelect` (above lists), `Select` (anything else), `DateField`,
  `TimeField`, `DateTimeField`. Their values stay strings (`YYYY-MM-DD`, `HH:MM`) and `onChange` gives the value, not an
  event. Tests choose like a user: `chooseOption` / `pickDate` (`src/test/pickers.ts`) and in Playwright `choose` /
  `pickDate` (`e2e/helpers.ts`).
- Notifications: a new kind of notification gets its icon and colour in `features/notifications/notificationLooks.ts`
  and, when it should open a page, its link in `notificationLinks.ts`; the bell and the notifications pages pick it up.
- Live delivery tracking ([../docs/delivery-tracking.md](../docs/delivery-tracking.md)): the customer's page follows a
  Server-Sent Events stream (`features/tracking/useTrackingStream.ts`, read with `fetch` so the login token can be sent);
  the partner's phone shares its position with `features/delivery/tracking/useLocationSharing.ts`. Map settings (tile
  server, attribution, timings) live only in `config/tracking.ts` / the `VITE_MAP_*` variables (`.env.example`): keep the
  OpenStreetMap attribution, and use a tile provider with a contract in production.
- A table that may be wider than a phone sits in a box with `relative overflow-x-auto`: the table scrolls sideways inside it,
  and `relative` keeps hidden helper texts (`sr-only`, absolutely positioned) from making the whole page wider.

## Tailwind CSS 4: what is different

- There is **no `tailwind.config.js`**. The theme lives in `src/styles/index.css` under `@theme`:
  `--color-brand: #3bb77e` creates `bg-brand`, `text-brand`, `border-brand` ...; `--font-heading` creates `font-heading`.
- **Three fonts:** `font-display` (Fraunces) for page and section titles: every `h1` and `h2` gets it without a class;
  `font-heading` (Quicksand) for the interface: buttons, labels, menus, prices, numbers, card titles (`h3`–`h6`);
  `font-body` (Lato) for running text. All three are self-hosted with `@fontsource` (imported in `main.tsx`), so no
  request goes to Google's servers.
- Use the theme names (`text-heading`, `bg-brand-light`, `border-line`), not hex values, so the colours stay consistent.
  Also in SVG drawings (`fill-brand`, `stroke-brand-light`). Code that needs a colour value (the map, Razorpay) reads
  it with `themeColour('brand')`; only `index.html` and `public/favicon.svg` have the hex written in.
- **The green (`#09792f`)**, which one:
  - `bg-brand` with `text-white`: buttons, badges, the active item;
  - `text-brand-text`: green text, and green icons on a light green or grey background (`bg-brand-light`,
    `bg-surface`), because it becomes light green in the dark theme;
  - `text-brand`: green icons on white or the card;
  - `text-ink-brand`: green text and icons on the always-dark surfaces (`bg-ink`: the admin menu, the wishlist
    pop-up's footer, the toasts).

  Each pair is at least 4.5:1 for text and 3:1 for icons, in both themes.
- **Cards** are `rounded-2xl border border-line bg-card`: with these classes a box gets the card shadow by itself
  (`--shadow-card`, the "Cards" rule at the end of `index.css`), so it stands out from the page. A card that needs
  another shadow (a menu, a dialog, a hover effect) just adds its own `shadow-…` class; fields and buttons stay flat.
- **Product pictures:** always `ProductImage`: every product (photo or emoji) sits on the same `bg-surface` background,
  so a list of products never shows coloured blocks. Pastel `tileBackground()` is for category tiles only.
- **Pages:** every page starts with `BreadcrumbBar` and an `h1`; a page with a summary (cart, checkout, order) puts it
  in a sticky right column from 1024 px (`lg:grid-cols-[minmax(0,1fr)_24rem]`). A table inside a card that shares the
  row with such a column switches its layout by the card's width (`@container`, e.g. the cart rows), not the screen's.
  A list with filters (the shop) puts them in a bar above the list, on every screen size (`ShopFilterBar`): a button
  per filter shows the chosen value and opens its choices inside the bar, below the buttons (never a floating menu
  that can go off a phone's screen). Filters never go at the bottom of a long column. The shop's left column (from
  1024 px) is for the search and the categories; below that a "Categories" button opens it above the list.
- **Product cards line up:** a product name always takes two lines (`min-h-[2lh]` in `ProductCard`), so the stars,
  the store and the price of the cards in a row are at the same height.
- **Two card sizes:** `ProductCard` is `regular` in the shop's grids and `small` where many products share a row (the
  home page's Popular Products: `ProductGrid cardSize="small"`, 2 to 6 per row). The small card leaves out the
  category line and keeps every action.
- **One design system for every panel** (admin, super admin, customer, vendor, delivery): build pages from the shared
  parts so they look the same everywhere.
  - Start pages: `PanelHero` (the panel's look and illustration, from `AUTH_LOOKS`).
  - Numbers: `KpiTile` (pick an `accent`; `to` makes it a link), or `StatCard` when the number should count up.
  - Empty and error states: `EmptyState` / `ErrorState` (an emoji becomes the floating illustration); loading:
    `LoadingState` and `Skeleton`.
  - Lists: `PageHeader` (tinted header with the page's illustration and actions), summary cards (`KpiTile` /
    `StatCard` with real counts, linking to their tab), `StatusTabs`, `FilterBar` around `SearchBox` and `FilterSelect`,
    the table classes of `components/panel/tableClasses.ts`, `StatusBadge` for statuses (a dot or an icon and the word)
    and `TablePagination`. The admin's Orders, Deliveries, Payments, Stores, Partners, Zones, Banners and Notifications
    pages show the pattern; their illustrations are in `features/admin/illustrations`.
  - `StatusTabs`: one bar in a card. A tab can have a `tone` (a dot in its status colour, the same as its `StatusBadge`,
    from `components/ui/statusTones.ts`) and a `group` (a thin line where the group changes). Tabs that do not fit
    scroll sideways, with a soft edge and a ‹ › button on the side with more, and the chosen tab is scrolled into view.
  - Buttons: `Button` / `ButtonLink`.
  - The menus mark the current page with the green pill. Each layout wraps its pages in `PageEnter`, so a page rises in
    when it opens. A table's rows light up under the mouse (`styles/index.css`).
- **Gradient and glass** (`styles/index.css`): every page sits on a soft gradient (four glows, `--color-glow-1` …
  `--color-glow-4`, fixed to the window). Big rounded boxes in the card colour (`bg-card` with `rounded-2xl` or
  `rounded-3xl`) are see-through glass by one rule at the end of the file; boxes that float (absolute, fixed, or with a
  dialog, listbox or menu role) stay solid. Sticky bars use `glass-bar` (a blur behind their content), other bars and
  menus `bg-glass`. A new floating panel needs `absolute`/`fixed` or its role to stay solid.
- **Animations respect "reduce motion":** write them as `motion-safe:animate-…` (keyframes in `styles/index.css`:
  `rise`, `float`, `wave`, `grow-up`, `grow-x`, `draw`, `reveal`, `ride`, `road`, `nudge`). `Appear` lets a page's blocks rise in
  one after the other, and `useCountUp` counts a number up; both show the final state at once when the system asks for
  less motion (and in the tests). The admin dashboard uses all of them. The delivery History page
  (`features/delivery/tasks`) is a timeline by day: each card's scooter rides from the store to the customer when the
  card appears (`ride`, `road`), and rides again on hover or focus, because the card gives its `Road` a new React key.
  The vendor's order queue (`features/vendor/orders`) shows the store's work as a flow of four steps (`OrderFlow`, the
  arrows `nudge`) and the orders as cards (`VendorOrderCard`): the order's number is a link stretched over the card
  (`after:absolute after:inset-0`), and the step button sits above it (`relative z-10`).
- **The shop's header stays at the top** (`Header.tsx`: `sticky top-0 z-50`). Its height per screen size is
  `--header-height` in `index.css`; change it when the header gets taller or smaller. A sticky column that should stop
  below it uses `lg:sticky lg:top-below-header` (the cart and checkout summaries); links to a part of a page land below
  it by themselves (`scroll-padding-top`). Pop-ups that cover the page use `z-50` and come later in the page than the
  header; round buttons in a corner use `z-40`.
- **Toasts** (`sonner`, set up once in `App.tsx`) are always dark, black with white text in both themes, so they stand
  out from the page. The kind shows in the icon's colour ("Toasts" at the end of `index.css`): green success, pink-red
  error, yellow warning, blue info.
- **Actions are buttons, not underlined text:** use `Button` / `ButtonLink` (or `buttonClasses()` on a `Link`). Clear,
  remove and empty actions ("Clear all", "Remove", "Empty cart") use `variant="remove"` with an icon (✕ to take one
  thing off, a bin to empty a list): a grey outline that turns pink on hover. Pink text uses `text-hot-text`, which is
  readable on white; `text-hot` is for icons and badges.
- **Links in the assistant's answers** never come from the model: the backend makes them from its tool results
  (`links`), and the chat shows them only when `safeInternalPath()` allows them (`features/assistant/chatLinks.ts`).
  A new page the assistant may link to goes into `CHAT_PAGES` (a test checks it is a real route). Any link text
  (Markdown or an address) goes through `ChatMessageText`: app pages open with the router, http(s) addresses in a new
  tab, anything else stays text.
- **A row that scrolls sideways** (`overflow-x-auto`) also gets `relative`: otherwise hidden screen reader texts
  (`sr-only` is `position: absolute`) inside it stick out of the row and the whole page scrolls sideways.
- **Light and dark theme:** the dark theme gives the same colour names other values (`:root[data-theme="dark"]` in
  `index.css`), so components need no `dark:` classes. That works only with theme names: a card, menu, dialog or field
  uses `bg-card`, not `bg-white` (white stays white in both themes: text on green, the knob of a switch); every screen's
  background is `bg-page`; grey boxes inside cards use `bg-surface`. For text on a colour that stays light in both themes
  (the yellow `accent` button), use `text-ink`, which stays dark. Check a new screen in both themes (the switch on the right
  edge).
- **Never build class names from variables** (`bg-${color}`): Tailwind only generates classes it finds written out in full.
  Use a map of complete names instead (see `TONE_CLASSES` in `components/ui/Badge.tsx`).
- Join classes with `cn()` (`utils/cn.ts`). It skips empty values and lets a later class win (`cn('rounded-lg', 'rounded-full')`),
  so a `className` passed to a component always overrides the component's default.
- `page-container` (defined in `index.css`) is the page width used by every section.

## The mock API

- `src/mocks/data/` holds the demo catalog: 11 categories, 59 products, 2 stores, coupons, banners, settings, reviews and users.
  Ids 1–17 are exact copies of the backend's local seed; categories 21+, products 101+ and reviews 101+ are demo rows.
- `src/mocks/mockCarts.ts` and `mockAuth.ts` keep the carts and logins in memory, with the backend's rules and messages.
- `mockCustomers.ts` (profiles, addresses), `mockOrders.ts` (orders and payments), `mockRatings.ts` (delivery ratings, reviews)
  and `mockNotifications.ts` do the same for the order pages. A new mock order moves on the next time it is read (cash on
  delivery: confirmed; online: the payment opens, "Pay" or "Decline" with the test gateway); delivery steps never happen in the
  mock, so customer1 has two older orders to show them: 901 (delivered) and 902 (cancelled).
- The vendor panel: `mockStores.ts` (Fresh Mart and Green Basket: registration, hours, PIN codes, pause), `mockCatalog.ts`
  (an editable copy of the demo products with stock and photos), `mockCoupons.ts` and `mockReports.ts` (sales per day, best
  sellers, reviews). An order placed by a mock customer reaches the store's queue and the store's steps move it on; Fresh Mart
  has order 903 waiting. `handlers/withRole.ts` checks the role (`asCustomer`, `asVendor`, `asPartner`, `asAdmin`, `asSuperAdmin`).
- The delivery panel: `handlers/delivery.ts` answers every `/api/v1/delivery/**` endpoint. `mockDelivery.ts` follows
  delivery-service: the partners (Arjun and Karthik, approved), online for 30 minutes, the zones, and the delivery jobs ("ready
  for pickup" makes one; it goes to an online, free partner of the store's zone, otherwise it waits). The partner's steps move
  the mock order on (picked up, delivered with the cash collected, failed: cancelled with the reason). Nobody is online when
  the mock starts: log in as delivery1 and go online to get Fresh Mart's waiting jobs.
- Live tracking: `handlers/tracking.ts` + `mockTracking.ts` follow delivery-service's tracking: the partner's last position
  (same rules and messages), the snapshot, and a real `text/event-stream` that sends a new snapshot after every position or
  delivery step. No routing service in the mock: the map draws a straight line and shows no arrival time.
- The admin panel: `handlers/admin.ts` answers every `/api/v1/admin/**` endpoint (partners, zones and deliveries come from
  `mockDelivery.ts`), `mockBanners.ts` keeps the banners. The categories in `mockCatalog.ts`, the reviews in `mockRatings.ts` and the
  accounts in `mockAuth.ts` can be changed by an admin; approving a store links it to its vendor's next login.
- The super admin: `handlers/superAdmin.ts` answers every `/api/v1/super-admin/**` endpoint (`asSuperAdmin`). `mockPlatform.ts`
  keeps the platform settings (the mock cart uses them), the feature flags and the audit log (a few seed events, then every
  settings, flag and banner change, like platform-service). `mockAuth.ts` creates admins and changes roles; it remembers each
  account's sign-up role, so only accounts that signed up as customers have a customer profile (as in customer-service).
  `handlers/monitoring.ts` answers the System page's Prometheus queries: every service up.
- The shopping assistant: `mockChat.ts` (`handlers/chat.ts`) is a small rule-based stand-in for the AI: it answers from the
  mock data (a product's price and stock, the running offers, the delivery rules, the customer's last order), adds to the
  cart ("add 2 tomatoes to my cart") and prepares an order, a cancellation or a removal from the cart ("remove the
  tomatoes from my cart"), a new amount ("make it 5 tomatoes") or emptying the cart with its card, with the same JSON, owner
  rule and errors as mcp-client-service; `mockPanelChat.ts` answers the panels' example questions (a vendor's
  orders, stock and sales, a partner's status, deliveries and earnings, the admins' waiting work and numbers). One store:
  `GET /api/v1/stores/:id` in `handlers/platform.ts`. Voice: `mockVoice.ts` "hears" "Which offers are running?" in
  every recording and reads answers aloud as silence (a real WAV, so the browser plays it).
- `src/mocks/handlers/` answers the same URLs as the backend with the same JSON (`ApiResponse` wrapper, 0-based pages,
  the same error messages). They only imitate what the screens need; the real rules stay in the backend.
- The tests use the same handlers (`src/mocks/server.ts`); a request without a handler fails the test.

## One catalog, two uses

The demo rows are also loaded into the backend (local profile only), so both modes show the same shop:

1. Change the demo data in `src/mocks/data/`.
2. `npm run seed:sql` writes `product-service/.../db/seed/R__insert_demo_catalog.sql` and
   `inventory-service/.../db/seed/R__insert_demo_stock.sql`.
3. Rebuild and restart product-service and inventory-service (Flyway runs the changed `R__` files), then publish the stock once:
   `Invoke-RestMethod -Method Post http://localhost:8106/internal/inventory/stock-snapshot`.

`src/mocks/seedSql.test.ts` fails when the SQL files are out of date, or when rows 1–17 differ from the backend's own seed.

## End-to-end tests (real backend)

`npm run e2e` runs 40 smoke flows in the installed Microsoft Edge (Playwright), through the real gateway:

> **Before you run them:** the flows use the demo accounts (customer1 Priya, vendor1 Fresh Mart, delivery1 Arjun) and
> first "free" delivery1 (`freePartner` in `e2e/helpers.ts`): his assigned deliveries are rejected, accepted ones
> cancelled, and picked-up ones reported and returned. That cannot be undone. Finish or note any order you are testing
> by hand with these accounts first.

| Flow | What it checks |
|---|---|
| `e2e/browse.spec.ts` | home → shop with category, stock and sort filters in the URL → product with reviews → "recently viewed" after a reload |
| `e2e/cart.spec.ts` | guest → login → back to the product → add to cart (also the other-store dialog) → coupon SAVE10 → totals → checkout page |
| `e2e/orders.spec.ts` | checkout (an address the store does not deliver to, then home) → pay online with the test gateway → cancel → refund → My orders |
| `e2e/orders.spec.ts` | a delivered order: progress, delivery partner, delivery rating, invoice PDF download |
| `e2e/account.spec.ts` | register a new customer → the heart opens the wishlist pop-up → wishlist survives a reload → log out |
| `e2e/account.spec.ts` | another new customer: profile, first address (the default), change password, forgot password → reset → log in |
| `e2e/session.spec.ts` | the refresh cookie (HttpOnly, SameSite=Strict, unreadable by the page; no token in storage) → a reload keeps the login, after a logout it does not → a 401 recovers through `/refresh` → a logout in one tab logs out the other |
| `e2e/social-login.spec.ts` | "Continue with Test provider" → the fake provider's login page → back in the shop, the sign-up form with the mobile number → logged in with the shop's own cookie, and next time at once → a social login over Priya's login, kept after a reload → an existing account connected once with its password, and its email in Mailpit → cancel at the provider → a replayed one-time code refused |
| `e2e/vendor.spec.ts` | customer1 orders (through the API); vendor1 finds it in the queue → accept → packed → ready for pickup → invoice PDF |
| `e2e/vendor.spec.ts` | vendor1 adds a product → stock → photo upload → the shop sells it → delete |
| `e2e/vendor.spec.ts` | vendor1 pauses new orders → a customer's order is refused → resume |
| `e2e/vendor.spec.ts` | dashboard (sales per day, best sellers) → reviews filtered by stars → coupon MANGO15 on and off |
| `e2e/vendor.spec.ts` | "Become Vendor": a new vendor registers and sends a store for approval |
| `e2e/admin.spec.ts` | a new store (API) → the admin approves it → its vendor logs in and reaches the vendor panel |
| `e2e/admin.spec.ts` | customer1 orders (API) → the admin cancels it with a reason → the customer sees "cancelled by an admin" |
| `e2e/admin.spec.ts` | an order ready for pickup (API) → the delivery waits → the admin assigns Arjun → the order names him |
| `e2e/admin.spec.ts` | a new customer (API) → the admin disables the account (login refused) → enables it again |
| `e2e/admin.spec.ts` | a new delivery partner sends a KYC (API) → the admin rejects it with a reason |
| `e2e/admin.spec.ts` | every admin page opens from the menu without an error |
| `e2e/delivery.spec.ts` | an order ready for pickup (API) → Arjun goes online and gets it → accept → picked up → on my way → proof photo → delivered with cash → the order is delivered |
| `e2e/delivery.spec.ts` | "Become a delivery partner": a new partner signs up → KYC (vehicle, licence, zone) → waiting for review |
| `e2e/delivery.spec.ts` | Arjun's history, earnings (one column per day) and profile |
| `e2e/tracking.spec.ts` | two browsers: customer1 opens the live tracking page → Arjun's phone (emulated GPS) accepts and shares its position → the customer sees him on the map, moving, picked up, on the way → delivered: sharing stops, the page shows the end (map tiles blocked: OpenStreetMap's servers are not for tests) |
| `e2e/superadmin.spec.ts` | the super admin adds an admin → the new admin logs in, without the Owner pages |
| `e2e/superadmin.spec.ts` | a new customer (API) → the super admin makes it an admin → a customer again |
| `e2e/superadmin.spec.ts` | a new delivery fee → customer1's cart charges it (cart-service) → "Platform settings updated" in the audit log → old fee back |
| `e2e/superadmin.spec.ts` | DELIVERY_SLOTS on and off → Fresh Mart's commission 12.5 % → commission report, audit log and System page |
| `e2e/assistant.spec.ts` | a guest asks the AI shopping assistant a real question → the answer arrives (20–60 s) → the chat survives a reload → new chat |
| `e2e/assistant.spec.ts` | vendor1 asks the store assistant in the vendor panel about the new orders → an answer from a vendor tool ("Checked against your store's live data") |
| `e2e/voice.spec.ts` | a guest asks by voice (Edge's fake microphone plays `e2e/fixtures/question-offers.wav`) → the speech server writes it down → the answer arrives → it is read aloud (mp3) |
| `e2e/assistant-orders.spec.ts` | customer2 in the chat: "Add 3 kg of tomatoes to my cart" → the cart badge → "Place my order, cash on delivery" → the order card (nothing ordered yet) → Confirm order → "Order #N placed …" → "Cancel my last order" → the cancellation card → Confirm cancellation → "Order #N is cancelled." → the order page says so. Second test: "Place my order, pay online" → Confirm order → **Pay Now** (order-service's `paymentUrl`) → the order page with "Pay ₹…" and the time left (not paid: the clean-up cancels it) |
| `e2e/stores.spec.ts` | Stores in the menu → "Shop at Fresh Mart" (`/shop?store=1`) → a product → its store's page with the opening hours |
| `e2e/stores.spec.ts` | PRODUCT_REVIEWS off → customer1's delivered order offers no product reviews → the flag on again |

Before running: the infrastructure, the services listed under "Run it", the Debezium connectors and the stock snapshot
(see the root README). Playwright starts its own dev server on port 5175. The cart and order flows empty customer1's cart
before and after; the order flow cancels the order it placed (also when it fails halfway); the account flows register new users
on every run. The vendor flows put everything back (the test order cancelled, the test product deleted, new orders taken
again, MANGO15 off), also when they fail halfway; the registration flow leaves a new vendor with a store waiting for approval
on every run. The admin flows cancel their orders, disable the store they approved (so the shop does not list it) and reject
the KYC (no new partner who could get deliveries); each run leaves a few new accounts. The admin's assign flow and the
partner's delivery flow are skipped when another partner is online and takes the delivery by itself. The partner's delivery
flow really delivers a cash order of customer1 (₹138): it stays as a delivered order and as ₹40 of Arjun's earnings on every
run; Arjun is offline and free again afterwards, also when the flow fails halfway. The partner sign-up flow rejects its KYC
again. The delivered-order flow uses a delivered order of customer1 from the backend's own end-to-end tests and is skipped when
there is none. The super admin flows put the delivery fee, Fresh Mart's commission (10 %) and DELIVERY_SLOTS (off) back,
also when they fail halfway, and switch their new admin off; each run leaves a new customer account and a few audit log
entries (two settings and two flag changes). The System page check passes whether or not Prometheus runs. The social
login flows need the fake provider (`oauth-test-provider`, profile `tools`) and auth-service started with
`SOCIAL_LOGIN_ENABLED=true`, `SOCIAL_TEST_CLIENT_ID=grocery-local` and `APP_PUBLIC_URL=http://localhost:5175` (this dev server); otherwise they are
skipped ([docs/social-login.md](../docs/social-login.md)). Each run leaves three new customer accounts. To use Google
Chrome instead of Edge: `$env:PLAYWRIGHT_CHANNEL = "chrome"`. The assistant flows need mcp-client-service,
mcp-server-service (both of the same version: the panel flow needs the panel tools) and Ollama
(`start-infra.ps1 -Profiles ai`) and may take up to 3 minutes each; the guest's flow leaves no conversation (it ends with
"New chat"), the vendor's leaves one conversation of vendor1. The voice flow also needs the speech server (speaches, same
profile) and leaves one guest conversation. The chat's order flow takes up to 6 minutes; it empties customer2's cart
before and after and cancels the order it placed (also when it fails halfway), and leaves one conversation of customer2.
To try new builds of the chat services before they replace the shared ones, start a second gateway that routes to them
(`SERVER_PORT=8180`, `MCP_CLIENT_SERVICE_URL=http://localhost:8124`) and point the dev server at it:
`$env:API_GATEWAY_URL = "http://localhost:8180"; npx playwright test e2e/assistant-orders.spec.ts`. The Razorpay path cannot run here without Razorpay test keys; its unit tests use a fake checkout.js.

The gateway allows only so many logins per minute from one address (rate limiting), so `apiLogin()` in `e2e/helpers.ts` logs
each account in through the API and reuses its token for 10 minutes (it lasts 15).
