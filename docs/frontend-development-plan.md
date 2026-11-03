# Fresh Grocery: frontend development plan

Phase 13 of the [architecture plan](architecture-and-development-plan.md): the customer storefront, starting with four sections.

## Context

The backend (14 Spring Boot services behind the API gateway on `http://localhost:8080`) is complete and tested. This plan
describes the customer-facing web app **"Fresh-grocery"** in React + TypeScript + Tailwind CSS. It follows the 5 sample screens in
[`image-screens/`](../image-screens) (the "Nest – Mart & Grocery" template) and `app-prompt.txt` §1 (customer panel) and §9
(the frontend talks to the backend only through the API gateway).

Agreed scope of the first frontend phase:

| Section | Sample image(s) |
|---|---|
| 1. **Home** incl. header, "Browse All Categories" menu, footer | `application-screen.png`, `menu-scree.png` |
| 2. **Shop**: product grid + sidebar (search, categories, trending, top rated, tags), paging | `shop-with-product-category.png` |
| 3. **Cart**: items, quantity, coupon, totals, proceed to checkout | `cart-page.png` |
| 4. **Wishlist + Recently viewed** | `Recently Viewd Products.png` |

Also agreed: the wishlist and recently viewed products live in **browser storage** (there is no wishlist API); the **local seed
catalog is extended** in the integration phase so the pages look full. Supporting screens needed to make these work:
**Login / Register** (the cart API needs a logged-in customer) and a small **Product detail** page (so "recently viewed" has views
to record; it is also one of the main screens in `app-prompt.txt`).

Working style: **screens and their behaviour first, against mock data that has exactly the backend's API shape; then switch to the
real backend.** Each phase ends with a demo and an approval before the next one starts.

---

## 1. Design analysis (from the sample images)

**Theme** (Nest colours, used as Tailwind tokens):

| Token | Value | Used for |
|---|---|---|
| `brand` | `#3BB77E` | buttons, badges, backgrounds, active states |
| `brand-dark` | `#29A56C` | hover |
| `brand-text` | `#1F7A50` | small green text (prices, links): the same green, darker, so it is readable (WCAG AA) |
| `brand-light` / `brand-soft` | `#DEF9EC` / `#BCE3C9` | page headers, hero background, "Add" buttons |
| `heading` | `#253D4E` | titles, product names |
| `body` | `#6B6B6B` | normal text (the template's `#7E7E7E`, slightly darker for readability); `muted` `#B6B6B6` for crossed-out prices |
| `line` | `#ECECEC` | card and table borders |
| `accent` | `#FDC040` | stars, yellow "Subscribe" button |
| `hot` / `sale` / `new` | `#F74B81` / `#67BCEE` / `#F59758` | product badges |
| category tiles | `#F2FCE4`, `#FFFCEB`, `#ECFFEC`, `#FEEFEA`, `#FFF3EB`, `#FFF3FF` | pastel tile backgrounds |

Fonts: **Quicksand** (headings, bold) and **Lato** (body), self-hosted via `@fontsource`; later **Fraunces** for the page
and section titles (section 20). Currency **₹ (INR)** everywhere
(`Intl.NumberFormat('en-IN')`), not `$` as in the template.

**Common layout on every screen:** top bar (links, promo text and "Need help? Call" + support phone from the platform settings),
header (logo, category select + search, "Become Vendor", Wishlist / Cart counters, Account), green nav bar ("Browse All Categories"
dropdown, Hot Deals, Home, Shop, Wishlist, support number), breadcrumb or green page banner, newsletter banner, feature strip (best
prices, free delivery …), footer, and the "X is on sale" popup (a real discounted product, once per visit, dismissible).

**Template items without matching backend data, and what we do (nothing fake):**

| Template | Decision |
|---|---|
| Compare, Vendors, Blog, Pages, Mega Menu, language/currency switch | Left out (not in scope); the nav shows only working links |
| "Top Selling / Trending" (needs sales data, admin-only) | Columns use data we have: **Recently added** (`NEWEST`), **Top rated** (`RATING`), **Budget picks** (lowest price), **Biggest discounts** (sorted in the browser) |
| "Sold 111/307" bars, per-product countdowns | Dropped (no such data). Deals of the Day = discounted products. Real countdowns only for **coupons** (`GET /api/v1/coupons` has `validUntil`): an "Offers" strip on Home and clickable coupon chips on the Cart page |
| "By Hambger Hel" on cards | **Store name** ("By Fresh Mart", from `GET /api/v1/stores`), which also explains the one-store cart rule |
| Category product counts (sidebar) | One tiny query per category (`?categoryId=X&size=1` → `totalElements`) in one hook, cached 10 minutes |
| Product / category photos (none in the seed) | Category `imageUrl` / product `imageUrls[0]` when present; otherwise a pastel tile with a matching emoji (🍅 tomato, 🥛 milk …) |
| Newsletter e-mail box (no newsletter API) | Banner kept; the button is a **"Start shopping"** link instead of a fake subscribe |
| Tag cloud | Becomes **brands** (the products API filters by `brand`) |
| Cart "Update cart", "Shipping to CA / Change address" | Quantities save immediately (no Update button); the totals show the delivery fee, "add ₹X more for free delivery", the minimum-order message and the coupon message |

---

## 2. Technology and structure

| Choice | Why |
|---|---|
| **Vite + React 19 + TypeScript** | Standard, fast dev server; the gateway already allows CORS from `http://localhost:5173` |
| **Tailwind CSS v4** (`@tailwindcss/vite`, theme tokens in `src/styles/index.css` `@theme`) | Colours and fonts defined once; classes like `bg-brand text-heading` |
| **React Router 7** | Pages and URL query params (shop filters live in the URL, so they can be shared and survive a refresh) |
| **TanStack Query 5**, used narrowly | Server data (loading / error / caching) without hand-written `useEffect` code. Only `useQuery` / `useMutation`, one `queryKeys.ts`; every cart change returns the whole cart, so it is simply stored (`setQueryData`): no invalidation chains, no optimistic updates. `retry: 1`, never on 4xx |
| **React Context** for auth; one `useLocalStorageList(key, max)` hook for wishlist and recently viewed | No Redux needed for this size |
| **MSW 2** (Mock Service Worker) for the mock phase, **kept thin** | Intercepts the *real* URLs, so components and API code never change when we switch to the backend; the same mocks run the unit tests. Handlers only mimic what the screens need (in-memory cart, 1–20 quantity, one-store 409, coupon accept/reject, delivery 30 below 299, minimum 99); the real rules stay in the backend |
| `sonner` toasts, `lucide-react` icons, `@fontsource/quicksand` + `/lato` | Feedback for add-to-cart / coupons; line icons like the template; no CDN |
| **Vitest + React Testing Library** (+ MSW); **Playwright** for 3 smoke flows (F5) | Behaviour tests; screenshots are only used to review the look against the samples, not as pass/fail |

Exact package versions are taken from npm at install time and pinned by `package-lock.json`. Tailwind v4 notes for juniors go in
the frontend README: the configuration lives in CSS `@theme` (no `tailwind.config.js`); never build class names at runtime like
`bg-${color}`, use maps of full class names. React Router is used in library mode (`createBrowserRouter`, imports from
`react-router`).

**One catalog, two uses:** the demo catalog (≈11 categories, ≈50 products, 2 stores, coupons, banners) is written once as typed data
in `src/mocks/data/`. The MSW mocks read it, and in F5 a small script generates the backend's local seed SQL from the same list, so
mock and real data never drift apart. Response shapes are copied from the real backend's JSON (0-based `page`, numbers, nulls).

**Folder `fresh-grocery/`** (repo root, its own npm project, like every service is its own project):

```text
fresh-grocery/
├── index.html, package.json, vite.config.ts (dev proxy /api -> :8080), tsconfig*.json, .env.development, .env.example, README.md
├── public/mockServiceWorker.js          (generated by MSW)
└── src/
    ├── main.tsx                         starts MSW when VITE_API_MOCKING=true, then renders <App/>
    ├── App.tsx                          providers (QueryClient, Auth) + routes
    ├── styles/index.css                 Tailwind import, @theme tokens, fonts, base styles
    ├── api/                             client.ts (fetch wrapper: unwraps ApiResponse, throws ApiError, adds Bearer token),
    │                                    types.ts (TypeScript copies of the backend records), products.ts, categories.ts,
    │                                    stores.ts, cart.ts, auth.ts, platform.ts, reviews.ts, queryKeys.ts
    ├── mocks/                           data/ (categories, products, stores, coupons, banners) + handlers/ (one file per API)
    ├── components/
    │   ├── layout/                      TopBar, Header, SearchBar, NavBar, CategoryMenu, MobileMenu, Footer,
    │   │                                NewsletterBanner, FeatureStrip, PageBanner, Breadcrumb, SaleToast, MainLayout
    │   ├── ui/                          Button, Badge, Price, StarRating, QuantityStepper, Pagination, Skeleton,
    │   │                                EmptyState, ErrorState (with retry), ConfirmDialog, SectionTitle
    │   └── product/                     ProductCard (+ skeleton), ProductListItem (small sidebar row), ProductImage
    │                                    (photo or emoji placeholder), AddToCartButton, WishlistButton, ProductGrid
    ├── features/
    │   ├── home/                        HomePage + HeroSlider, FeaturedCategories, PromoCards, PopularProducts,
    │   │                                DailyBestSells, DealsOfTheDay, OffersStrip, ProductColumns
    │   ├── shop/                        ShopPage, ShopToolbar (count + sort), ShopSidebar (+ widgets, reused by Cart),
    │   │                                useShopFilters (URL params; page 1-based in the URL, 0-based for the API)
    │   ├── product/                     ProductDetailPage, ReviewList
    │   ├── cart/                        CartPage, CartTable (cards on mobile), CartTotals, CouponForm, CouponChips, useCart
    │   ├── wishlist/                    WishlistPage, RecentlyViewed, useWishlist, useRecentlyViewed
    │   ├── auth/                        AuthContext (token + expiry), LoginPage, RegisterPage, RequireCustomer
    │   └── errors/                      NotFoundPage, RouteErrorPage (router errorElement)
    ├── hooks/                           useLocalStorageList, useCountdown, useCategoryCounts, useStores, usePlatformSettings
    └── utils/                           formatPrice (₹, en-IN), productEmoji, discount helpers
```

**Rules for readable code:** one component per file, typed props, no business logic in JSX (it goes into helpers and hooks), API
calls only in `api/`, data fetching only through hooks, shared pieces in `components/`, page-specific pieces in `features/<page>/`.

**Routes:** `/` · `/shop` (`?q=&category=&brand=&minPrice=&maxPrice=&inStock=&sort=&page=`) · `/products/:id` · `/cart` (login
required) · `/wishlist` · `/login?next=` · `/register` · `*` (not found).

---

## 3. Screen behaviour (business logic)

**Header (every page):** search box + category select → `/shop?q=…&category=…`; Wishlist count from browser storage; Cart count
from the cart API (logged-in customers); Account → login, or the user's name with "Log out"; "Become Vendor" → register with role
VENDOR; support phone from `GET /api/v1/platform/settings`; the "Browse All Categories" dropdown lists `GET /api/v1/categories`
with emoji icons.

**Home:** hero slider from `GET /api/v1/banners` (title, subtitle, "Shop now" → banner link; local artwork when the banner image
is missing) · featured category tiles (with counts) · 3 promo cards → categories · Popular Products with category tabs
(`sort=RATING`; a tab loads only when it is clicked, to stay well below the gateway's 20 requests/second) · Daily Best Sells (top
rated) and Deals of the Day (discounted products) with add-to-cart · Offers strip (public coupons with a real "ends in …"
countdown) · 4 product columns (see §1) · newsletter banner · feature strip (free-delivery amount from the platform settings).

**Shop:** product grid (5/4/3/2 columns by screen size); each card has a badge (discount % / Hot / Sale / New), category, name,
stars, store name ("By Fresh Mart" from `GET /api/v1/stores`), selling + crossed-out price and "Add" · toolbar "We found N items" +
sort select · pagination · sidebar: search, categories with counts (the active one highlighted), price range + "in stock only",
trending (`NEWEST`), top rated (`RATING`), brand tag cloud. All filters are URL params, so back/forward and refresh keep them.

**Product detail (supporting):** photo or placeholder, name, rating, price and discount, unit, stock status (in stock / only N left /
out of stock), store, quantity + Add to cart, wishlist toggle, description, reviews (`GET /api/v1/products/{id}/reviews`). Opening it
records the product in **recently viewed** (latest first, max 8).

**Add to cart (everywhere):** guest → `/login?next=<current page>`; customer → `POST /api/v1/cart/items`; **409 (other store)** →
dialog "Your cart has items from Fresh Mart. Start a new cart with this product?" → retry with `replaceCart=true`; success → toast
and the cart badge updates; out-of-stock products have a disabled button.

**Cart:** green page banner "Shop Cart" · table (remove, image, name, store, price, quantity stepper, subtotal) from the backend's
`CartResponse`; the stepper is limited to 1…min(20, stock) and disabled while its change is saving · coupon form + clickable coupon
chips of this store and the platform (`PUT/DELETE /cart/coupon`; the backend's message is shown, e.g. "You have already used coupon
WELCOME50") · "Empty cart" with confirmation · totals exactly as calculated by the backend (items total, savings, coupon, delivery
fee or "Free", "add ₹X more for free delivery", payable, GST included, minimum-order message) · **Proceed to checkout** →
`GET /cart/checkout`: shows "ready" or the problem list (the checkout page itself is a later phase) · "Order / Share on WhatsApp"
builds a `wa.me` link with the cart lines · the Shop sidebar, reused · empty-cart state with "Continue shopping".

**Wishlist + Recently viewed:** the heart button on cards and on the detail page toggles the product; both lists keep a small product
snapshot in `localStorage` (shown immediately; recently viewed without duplicates, latest first, max 8). The Wishlist page refreshes
each product from `GET /api/v1/products/{id}` (current price and stock, "no longer available" on 404), with Add to cart and remove;
empty state "There are no products on the Wishlist!"; "Recently viewed products" box below (as in the sample).

**Login / Register:** forms with the backend's validation rules shown inline (Indian mobile `^[6-9]\d{9}$`, password 8–64 with a
letter and a digit). This storefront is for customers: Register sends `role: CUSTOMER`; "Become Vendor" registers a VENDOR account
and explains that the vendor panel comes later; logging in with a non-customer account shows a clear message. Token, user and
expiry time (the token lasts 60 minutes, there is no refresh) are kept in `localStorage`; an expired token is dropped on start; a 401
logs out and sends the user to `/login?next=…` with "Your session has expired".

**Cross-cutting:** skeleton cards while loading, `ErrorState` with "Try again", empty states, router error page and 404 page, page
titles (React 19 `<title>`), focus styles, `aria-label`s on icon buttons, the category menu closes on Esc / outside click, mobile
layout (hamburger drawer, sidebar below the grid, cart rows as cards), and clear messages for 429 ("Too many requests, wait a
moment"; the gateway sends it with an empty body, which `apiClient` handles) and 503 ("Service unavailable, try again").

---

## 4. Phases (each ends with a demo and an approval)

| Phase | Builds | Demo / done when |
|---|---|---|
| **F1 Foundation** | Vite project, Tailwind theme tokens + fonts, folder structure, router (+ 404 and error page) + `MainLayout` (top bar, header, search, nav, category dropdown, mobile menu, footer, newsletter, feature strip), shared UI components, `api/` client + types + query keys, **demo catalog data + thin MSW handlers** (categories, products, stores, settings, banners, coupons), React Query setup; one quick real check through the Vite proxy (`GET /api/v1/categories`) | `npm run dev` shows the shared layout on every route with mock data; unit tests + lint + `npm run build` green |
| **F2 Home** | All home sections, sale popup | Home looks and behaves like `application-screen.png` / `menu-scree.png` (reviewed with screenshots) |
| **F3 Shop + Product detail** | Grid, filters in the URL, sort, paging, `ShopSidebar` widgets, product detail + reviews, recently-viewed recording | Matches `shop-with-product-category.png`; filters, paging and the back button work |
| **F4 Cart, Wishlist, Login** | Login/Register + auth context, cart page and mock cart, add-to-cart everywhere with the other-store dialog, coupon chips, wishlist page + recently viewed | Matches `cart-page.png` and `Recently Viewd Products.png`; guest → login → add → coupon → totals → checkout readiness works on mocks |
| **F5 Backend integration** | Mocks off by default (`VITE_API_MOCKING=false`), Vite proxy to the gateway, real login/JWT and expiry, error mapping (401/403/409/429/503), **extended local seed catalog** generated from the demo data (product-service categories/products + inventory stock seed, then stock snapshot), README (incl. Tailwind v4 notes), 3 Playwright smoke flows against the real gateway | Every screen works end to end with the running backend; Playwright smoke green |

Backend changes are limited to F5's **local seed data** (repeatable `R__` seed files in product-service and inventory-service;
nothing in the services' code).

**Progress:** F1 ✅ · F2 ✅ · F3 ✅ · F4 ✅ · F5 ✅ done (all 2026-10-26). The follow-up phases F6–F8 (checkout, my orders, my
account) are in section 6.

Decisions taken while building F1 (small additions to the plan above):

- `cn()` uses **tailwind-merge**, so a `className` passed to a component reliably overrides the component's default classes.
- The shop URL uses the **category id** (`/shop?category=3`), the same value the API takes. Banner links such as `/categories/1`
  and `/offers` are translated to app pages in `utils/links.ts`.
- Category menu links carry an explicit `aria-label` ("Vegetables, 10 products").
- The demo catalog includes **demo reviews** (from the two seed customers), so ratings and the `RATING` sort have data. F5 seeds
  them too, together with the products' `rating_average` / `rating_count`.
- `@playwright/test` is installed already (it drives the installed Edge, no browser download). In F1 it was used only for the
  review screenshots; the smoke flows stay in F5.
- The local database contains a category "Bakery" created by the phase 4 e2e test; the demo category names and slugs
  (`breakfast-bakery` …) do not clash with it.

Decisions taken while building F2 (Home):

- "Daily Best Sells" is called **Daily Fresh Picks** (best-rated products in stock from Vegetables / Fruits / Dairy & Eggs):
  the shop publishes no sales numbers, so the page does not claim "best selling".
- The fourth product column is **More deals** (deals 5–7, so it does not repeat the four "Deals of the Day" cards); the other
  three are Recently added (`NEWEST`), Top rated (`RATING`) and Budget picks (`PRICE_LOW_TO_HIGH`, in stock).
- Deal cards show "You save ₹X" instead of the sample's per-product countdown; the real countdowns are on the coupons in the
  **Offers For You** section (`/#offers`), which lists the platform coupons and each store's coupons.
- Product card badge, one per card from real data: Sold out → discount % → "Only N left" (low stock) → "Top rated"
  (≥ 4.5 from 2+ reviews). No "New" badge: the API has no creation date.
- Until F4 (login + cart) the **Add** buttons follow the guest rule: they open `/login?next=<this page>`.
  The wishlist heart already works (browser storage) and updates the header counter.
- Promo cards and Daily Fresh Picks find categories by **slug**, not id (ids differ between databases).
- The seed banners point at images that do not exist, so the hero shows emoji artwork (the image is used as soon as it loads).
- The "on sale" pop-up shows the biggest real discount, 4 seconds after arriving, once per browser tab (`sessionStorage`).

Decisions taken while building F3 (Shop + Product detail):

- Shop URL: `/shop?q=&category=&brand=&minPrice=&maxPrice=&inStock=true&sort=&page=` (page 1-based, defaults left out).
  `features/shop/shopFilters.ts` reads it (wrong values fall back to the defaults) and `utils/links.ts` `shopLink()` writes it.
  Every filter change goes back to page 1 and keeps the scroll position; a page change scrolls to the top.
- 12 products per page (3 rows of 4, as in the sample). Active filters are shown as removable chips with "Clear all".
- Sidebar: Search, Product Categories (with counts), Filter (price range with a check that min ≤ max, "In stock only"),
  **New arrivals** (`NEWEST`; the sample's "Trending Now" needs data the shop does not have), Top rated products, **Brands**
  (the sample's tag cloud, collected from up to 100 products of the chosen category, as there is no brands endpoint).
  On phones the sidebar is below the products, with a "Filters" link at the top to jump to it.
- Product page: badge, name, rating, price with "Save ₹X (Y% off)", pack size, brand, category, SKU, stock status, quantity
  (1 to min(20, stock)), Add to cart (guest → login until F4), wishlist, "Sold by" box with the one-store cart rule, reviews
  (5 per page, newest first), related products of the same category. Unknown or inactive products show "not available".
- **Code splitting:** every page except Home is loaded on first use (`lazy` in `routes.tsx`), which keeps the main
  JavaScript file below Vite's 500 kB warning; `PageLoading` shows while a page opened directly (bookmark) loads.

Decisions taken while building F4 (Login, Cart, Wishlist):

- **Login state:** `AuthProvider` (React context, `useAuth()`) keeps token, user and expiry in `localStorage`. It logs out when
  the 60-minute token runs out (a timer) or when the API client gets a 401 for a token it sent (`SESSION_EXPIRED_EVENT`),
  with the toast "Your session has expired". Cached carts are dropped on every login/logout.
- Only **customers** can log in to the storefront; other roles get "This shop is for customers …". "Become Vendor" creates a
  VENDOR account and explains that the vendor panel comes later (it does not log the vendor into the shop).
- Forms check the backend's rules in the browser first (`features/auth/validation.ts`); the backend's 400 field errors and
  409 "already registered" messages are shown next to the fields. `?next=` only accepts paths inside the app.
- In development the login page lists the seed demo accounts (`customer1/2@grocery.com`, `Password@123`).
- **Cart:** `useCart()` (query per user id) and mutations that store the returned cart (`setQueryData`); the header counter
  shows the number of different products (`itemCount`). Quantity steppers go up to min(20, stock) and only the row being saved
  is disabled. Coupon: typed code or a click on a suggested coupon (platform + this store); the backend's message is shown
  next to the box. Totals are the backend's; the page adds "Add ₹X more for free delivery" and the minimum-order hint.
  "Proceed to checkout" calls `GET /cart/checkout` and shows "ready" or the problem list (placing orders is a later phase).
- **WhatsApp:** only "Share on WhatsApp" (a `wa.me` link with the shopping list). The sample's "Order on WhatsApp" was left out:
  it would create orders outside the platform (no payment, no tracking).
- **Add to cart everywhere:** guest → login; 409 (other store) → "Start a new cart?" dialog naming both stores → retry with
  `replaceCart=true`; success toast with "View cart". The product page adds the chosen quantity.
- **Wishlist page:** saved products refreshed with today's price and stock (`useProductsByIds`, shared cache), "No longer
  available" for a 404, Add to cart, remove; "Recently viewed products" (fresh prices) with "Clear list".
- **Mock API:** demo users (all `Password@123`) and an in-memory cart per user with the backend's formulas and messages
  (customer1 starts with the seed cart: Tomato ×2, Milk ×3, Eggs ×1). Carts reset on a page reload.

Decisions taken while building F5 (backend integration):

- `npm run dev` now uses the **real backend** (`.env.development`: `VITE_API_MOCKING=false`); `npm run dev:mock` (Vite mode
  `mock`, `.env.mock`) keeps the mock API.
- **Extended local seed**, generated from the demo data by `npm run seed:sql` (`src/mocks/seedSql.ts`):
  `R__insert_demo_catalog.sql` (product-service: categories 21–26, products 101–143, reviews 101–143, then every product's
  rating recalculated from its visible reviews with `version + 1` so Debezium/Elasticsearch take it) and
  `R__insert_demo_stock.sql` (inventory-service). Only seed files changed in the backend, no service code.
  To avoid clashes with data the backend e2e tests create, demo SKUs end in the product id (`FM-VEG-101`) and review ids
  start at 101. `seedSql.test.ts` checks that the files are up to date and that rows 1–17 equal the backend's own seed.
- Checked against the running backend: all 59 products, categories, prices, discounts, taxes, stock flags and ratings equal
  the mock data (only stock numbers of products already ordered in backend tests differ, as expected).
- **Error mapping** checked on the real gateway: 401 (JSON, "Please log in…" / "…token is invalid or has expired…"),
  409 (other store), 429 (empty body → "Too many requests"), 503 (JSON "temporarily unavailable"); the API client covers all.
- **Dev proxy fix:** the proxy drops the browser's `Origin` header. Without it the gateway's CORS check (it only allows
  `http://localhost:5173`) answered 403 to every POST when the dev server ran on another port.
- **Focus fix:** the shop sidebar re-created the whole Filter box after every URL change, so the "In stock only" checkbox
  lost the keyboard focus; now only the price boxes are re-created when the price in the URL changes.
- **Playwright smoke tests** (`npm run e2e`, `e2e/`): browse, cart and account flows against the real gateway, in the
  installed Edge, with Playwright's own dev server on port 5175. All three pass (three runs in a row).

---

## 5. Verification

- Every phase: `npm run lint`, `npm run test` (Vitest + RTL + MSW: utils, hooks, components, page flows), `npm run build` (type
  check + production build).
- Visual review: screenshots of each page at desktop and mobile width, looked at side by side with the sample images (a review aid,
  not a test).
- F5: start the infrastructure and all services, register the connectors, publish the stock snapshot; `npm run dev` with mocks off;
  Playwright smoke flows through the gateway: browse home → shop filter → product detail → log in as customer1 → add to cart (and
  the other-store dialog) → coupon SAVE10 → totals → checkout readiness; wishlist and recently viewed survive a reload.

---

## 6. Checkout, my orders, my account (F6–F8)

The rest of the customer panel from `app-prompt.txt` §1: checkout, address management, payment, order confirmation, order
tracking, order history, profile. The backend for all of it exists (order, payment, customer, delivery, notification services).
Same working style: mock API first (same URLs and JSON as the backend), then checked against the real gateway; no backend code changes.

**Backend facts the screens follow:**

- `POST /api/v1/orders` `{ addressId, paymentMethod: COD | ONLINE, note? }` turns the **whole cart** into an order (prices come from
  the cart, not the request). It answers at once with a **PENDING** order; the rest happens in the background (saga):
  stock reserved → COD: CONFIRMED; ONLINE: the payment waits for the customer (`canPayNow`) for 15 minutes, then CONFIRMED or
  CANCELLED. The cart is emptied in the background too (cart-service reacts to OrderCreated).
- Errors: 409 with the cart's problems as `errors[]` (out of stock, minimum order …); 400 "Fresh Mart does not deliver to PIN code
  560034. Please choose another address." (each store delivers to a list of PIN codes, which the public store API does not show);
  400 "… is not taking orders right now".
- The local payment gateway is the **mock** (`app.payment.gateway=mock`): `POST /payments/orders/{id}/mock-pay { success }`.
  Razorpay Checkout (`razorpay-checkout` / `razorpay-verify`) needs real keys and is left for later.
- `GET /orders/{id}` has everything for tracking: status, timeline, amounts, the address copy, store and delivery partner contact,
  `cancellable`. Invoice PDF (`/orders/{id}/invoice`) only once the store accepted the order. Cancel: `POST /orders/{id}/cancel`.
- Not in the backend, so not in the screens: **delivery slots** and **saved payment methods**.

| Phase | Builds | Done when |
|---|---|---|
| **F6 Checkout + order page** | `/checkout`: address cards (default first, add a new address inline), payment method (cash on delivery / pay online), note, order summary from `GET /cart/checkout`, Place order. `/orders/:id`: "Thank you" banner after placing, status with progress steps, polling while the order is moving (every 3 s while PENDING, every 30 s while it is with the store or on the way), **Pay now** box for ONLINE (mock gateway: pay or decline, with the pay-before countdown), items, amounts, address, store, partner, timeline. Cart "Proceed to checkout" leads to `/checkout`. Mock API: addresses, orders with a small saga, payments | guest → login → cart → checkout → COD order confirmed, and ONLINE order paid, on mocks and on the real backend |
| **F7 My orders** | `/orders` history (status filter, paging), cancel with reason (until the order leaves the store), invoice download, rate the delivery and review the products of a delivered order, "My orders" in the account menu | history, cancel and invoice work against the real backend |
| **F8 My account** | `/account`: profile (name, phone, birthday, gender), address book (add / edit / delete / default, at most 10), change password; forgot / reset password (the code arrives by email: Mailpit locally); notification bell with unread count | all account forms work, with the backend's validation messages |

**Progress:** F6 ✅ · F7 ✅ · F8 ✅ done (all 2026-10-26). 216 unit/component tests, lint and build green; 6 Playwright flows pass
against the real backend (three runs in a row). Every response shape and error message was compared with the running services.

Decisions taken while building F6–F8:

- **Routes:** `/checkout` · `/orders` (`?status=DELIVERED|CANCELLED&page=`) · `/orders/:id` · `/account` · `/account/addresses` ·
  `/forgot-password` · `/reset-password?code=`. All but the last two need a customer login (`RequireCustomer`).
- **Cart → checkout:** "Proceed to checkout" is now a link; the checkout page loads `GET /cart/checkout` each time it opens and shows
  the problem list (with "Back to cart") when the cart cannot be ordered. The default address and cash on delivery are preselected;
  a first address can be added right there. "Does not deliver to PIN code …" is shown at the address list.
- **After placing:** the order page opens with a "Thank you" box (router state, so only right after placing; "Back" goes to the cart).
  It asks for news every 3 s while the order is PENDING or a refund is on its way, every 30 s while the store and the partner work
  on it, and stops when it is finished. The cart and the bell are loaded again whenever the order moves on, and the cart also after
  each answer while it still shows products (the backend empties it a moment after the order; an online order stays PENDING until
  paid). Checked on the real backend: cart empty after < 1 s, COD confirmed after ~1.5 s.
- **Online payment:** only the backend's **mock gateway** (local default): "Pay ₹X" or "Decline (test)", with the 15-minute
  countdown. The payment can answer 404 for a moment after the order; the page keeps waiting instead of showing an error. Razorpay
  Checkout stays for when real keys are configured.
- **Order page:** status headline + progress steps (Placed … Delivered; a red box with the reason for cancelled / rejected /
  failed), items at the prices paid, totals and the payment line, delivery address copy, store and partner with `tel:` links,
  history (a note that only repeats the status is left out), **Cancel order** with an optional reason (until it leaves the store),
  **Download invoice** (from ACCEPTED on; `apiDownload()` fetches the PDF with the token, `utils/download.ts` saves it).
- **Delivered orders:** "Rate the delivery" (once; shows the rating afterwards) and "Review this product" per item. The backend has
  no "did I review this?" endpoint, so the 409 answer is shown as "You have already reviewed …". The Playwright flow does not post
  reviews: they would change the real ratings of the seed catalog.
- **Account:** the profile (customer-service) holds the delivery name and number; the login email cannot be changed, and the
  header keeps the account name from auth-service. Profile and addresses retry a 404 for up to 8 seconds, because customer-service
  creates the profile from the registration in the background.
- **Password reset:** the local backend returns the reset code (`expose-token-in-response`); in development the page shows it and
  carries it to `/reset-password?code=`. Elsewhere the code only arrives by email.
- **Bell:** unread count every minute (only for a logged-in customer), the latest 6 in a menu, "Mark all as read"; an order
  notification opens its order.
- **Cache:** the customer's own data lives under `['me', userId, …]` (`queryKeys.ts`) and is dropped with the cart on every login
  and logout.
- **Mock API:** addresses, profiles, orders with a small saga (the next read moves a new order on: COD confirmed, or the online
  payment opened), payments, invoice (a small generated PDF), delivery ratings, reviews, notifications and password changes, all
  in memory. customer1 has two older orders (901 delivered, 902 cancelled) and four notifications.
- **Seen while testing on this machine:** with every service, Docker and the browsers running, memory was almost full and a JVM
  could stall for ~20 s (the gateway then answers 504 after its 10 s timeout). If a Playwright flow times out on a single request,
  check the free memory first.

---

## 7. Vendor panel (V1–V4)

The architecture plan (Phase 13) asks for **one app with role-based panels**, in the order customer → vendor → admin →
delivery → super admin. This section is the store/vendor panel from `app-prompt.txt` §2. The backend for it is complete
(`/api/v1/vendor/**`: store, products, stock, orders, coupons, reviews, reports); no backend code changes.

**How it fits into the app:**

- **Same app, own area:** `/vendor/...` with its own layout (sidebar + top bar, no shop header or footer). The vendor pages are
  loaded on first use (`lazy`), so customers never download them.
- **One login per browser; the role decides.** `/login` stays customers-only, `/vendor/login` is vendors-only; each says clearly
  where the other account type logs in. A vendor who opens the shop browses it like a guest (no cart; "Add" says that shopping
  needs a customer account) and gets a "Vendor panel" link in the account menu. "Become Vendor" registers and logs the vendor
  in, straight into the store registration.
- **The store gate** (the vendor panel's first check, `GET /vendor/store`):

  | Store | The panel shows |
  |---|---|
  | none (404) | "Register your store" form (name, phone, email, address) |
  | PENDING_APPROVAL | "Waiting for approval", details can still be changed |
  | REJECTED | the admin's reason and the form to send it again |
  | DISABLED | "Your store is disabled by the platform" and the reason |
  | ACTIVE, but the login is from before the approval | "Approved! Log in again": the store id is only in a token issued after the approval (the backend answers 403 "No store found for this vendor ... log in again" until then) |
  | ACTIVE | the panel |

- **Data:** cached under `['vendor', userId, ...]`, dropped on every login and logout like the customer's data. Product changes
  go to product-service but are read from product-read-service a second or two later (CQRS): after a change the list is
  loaded again at once and once more after 2 s, and a product page opened right after creating it retries a 404 for a few
  seconds. The order queue asks for new orders every 30 s; the bell shows "New order #12" (and opens that order).
- **Mock API:** vendor1 (Ravi Kumar, Fresh Mart, store 1) and vendor2 (Meena Iyer, Green Basket, store 2); the mock store,
  products, stock and coupons become editable copies of the demo data, and orders placed by mock customers reach the mock
  vendor's queue.
- **Not in the backend, so not in the panel:** vendors cannot create categories (admins manage them); the delivery partner
  is assigned by delivery-service and the hand-over is the partner's "picked up", so the store's last step is "Ready for pickup".

| Phase | Builds | Done when |
|---|---|---|
| **V1 Foundation + orders** | Role-aware login (`/vendor/login`, shop pages for customers only), vendor layout and menu, store gate, order queue with tabs (New, Accepted, Packed, Ready for pickup, On the way, Delivered, Cancelled, Rejected) and counts for the ones that need work, order page with the pick list, customer contact, payment (cash to collect or paid), timeline, Accept / Reject (with reason) / Packed / Ready for pickup, invoice | a customer's order is accepted, packed and handed over from the panel, on mocks and on the real backend |
| **V2 Products + stock** | Product list (search, status, category, low stock), add / edit product (category, unit, prices, GST, selling-price preview), show / hide, delete, photos (upload, remove; JPEG/PNG/WEBP ≤ 2 MB, ≤ 5), stock: set the counted number and low-stock threshold, or add received goods | a new product gets stock and a photo and is found in the shop |
| **V3 Store settings + registration** | Store details, opening hours (per day, closed days), delivery PIN codes, pause / resume new orders (also in the top bar); the same details form registers a new store | a new vendor registers a store and sees "waiting for approval"; settings changes show in the shop |
| **V4 Coupons, reviews, dashboard** | Coupons (create, edit, switch on/off, usage), customer reviews (filter by stars), dashboard (orders waiting, low stock, sales per day and top products from report-service) | every vendor page works against the real backend |

**Progress:** V1 ✅ · V2 ✅ · V3 ✅ · V4 ✅ done (all 2026-10-26). 254 unit/component tests, lint and build green; 11 Playwright
flows (6 shop + 5 vendor) against the real backend. Every vendor response shape and error message was compared with the running
services, and every page was checked on a desktop and a phone screen.

Decisions taken while building V1–V4:

- **Routes:** `/vendor/login` · `/vendor` (dashboard, `?days=7|14|30`) · `/vendor/orders` (`?tab=new|accepted|…|all&page=`) ·
  `/vendor/orders/:id` · `/vendor/products` (`?q=&status=&category=&lowStock=true&page=`) · `/vendor/products/new` ·
  `/vendor/products/:id` · `/vendor/coupons` · `/vendor/reviews` (`?rating=`) · `/vendor/store`. Filters and tabs live in the URL,
  so "Back" and a shared link show the same list.
- **Login and roles:** `logIn(request, panel)` refuses an account of the other panel with a clear message and a link to the right
  login (`WrongPanelError`). A logged-in account that opens a page of the other panel sees "This page is for customers" / "This
  panel is for vendors" with a link and "Log out" (`OtherAccountNotice`). The bell's links follow the role: a vendor's
  "New order" opens the store's order page, "Low stock" the product.
- **Orders from the store's side:** CONFIRMED is called **New**; the tabs that need work show a count. One button per step
  (Accept → Mark as packed → Ready for pickup) with a toast, and Reject asks for a reason (quick choices like "Out of stock").
  The queue asks for news every 30 s, an open order page follows the customer's polling rule. The history says "by you" for the
  store's steps and leaves out the backend's notes written for the customer ("The store accepted your order"); payment notes
  and reasons stay. Invoice from ACCEPTED on.
- **Products:** INACTIVE is shown as **Hidden** ("Hide from customers"). The form previews the price customers pay; a 409 for a
  used product code is shown at that field. Saving a new product opens its page with "Now set its stock and add a photo". A
  product without a stock record (inventory answers 404) shows "No stock yet" and is sold out in the shop until the stock is set.
- **Photos:** `apiRequest` sends a `FormData` body unchanged (the browser sets the multipart header). The unit tests replace the
  upload call (`vi.mock`), because a jsdom `FormData` sent through Node's `fetch` cannot be read by MSW; the real upload runs in
  the Playwright flow.
- **Store settings:** the same details form registers a store and changes it; opening hours per day (closed days, "must open
  before it closes"); delivery PIN codes as a list (Save only when changed); the "Taking new orders" switch is on the settings
  page and in the top bar.
- **Coupons:** the first and last day are whole days in India time (sent as the start of the first day and the end of the last
  day). Codes are unique on the whole platform, so a 409 says "already taken" at the code. The state is worked out on screen:
  Switched off, Not started, Ended, Used up, Running.
- **Reviews:** filter chips by stars; each review names its product, looked up among the vendor's own products (hidden ones too).
- **Dashboard:** four tiles (New, Accepted, Packed, Low stock) that open the matching list, then sales for 7, 14 or 30 days
  (the period scopes everything below it): totals, the sales per day and the five best sellers. "Sales per day" (shared with
  the admin dashboard and reports, `components/sales/SalesPerDayCard`) names the period and gives four quick facts (daily
  average, best day, days with sales, the last day with its change from the day before). The chart is a `brand-dark` line
  (#29A56C, 3:1 against white; `brand` #3BB77E is too light for marks) over a fading gradient, with a legend, a dashed daily
  average and the best day as a gold point carrying its value; every day shows its numbers and its change on hover and
  keyboard focus, and the chart draws itself again when the period changes. "Show the numbers" opens the same data as a
  table (best day marked, change per day, totals). A period without sales gets a message instead of a flat chart.
- **Mock API:** stores, catalog (products, stock, photos), coupons and reports are editable in memory; an order placed by a mock
  customer reaches the mock vendor's queue and the store's steps move the customer's order on. Fresh Mart has order 903 waiting
  (New) besides customer1's 901 and 902.
- **Found in the backend while testing (fixed there, 2026-10-26, by the AI assistant work):** store-service shifted opening hours
  by 5:30 on a PC in India time (the seed's 07:00–22:00 came back as 12:30–03:30, `openNow` always false). See "Opening hours and
  time zones" in the store-service README. The panel now shows 07:00–22:00.
- **Playwright and the real data:** the vendor flows clean up after themselves. The test order is cancelled, the test product
  deleted, "Taking new orders" switched back on and MANGO15 switched off again, even when a step fails. Each run of "a new
  vendor registers a store" leaves one new vendor account with a store waiting for approval.

---

## 8. Admin panel (A1–A4)

The operations panel from `app-prompt.txt` §4, next in the order customer → vendor → admin → delivery → super admin. Its
backend is complete: every service has `/api/v1/admin/**` endpoints (the gateway lets ADMIN and SUPER_ADMIN in). Same working
style: mock API first, then checked against the real gateway; no backend code changes.

**How it fits into the app:**

- **Same app, own area:** `/admin/...`, loaded on first use like the vendor panel. The vendor panel's sidebar, top bar and
  login frame become shared panel pieces (`components/panel/`), so both panels (and the delivery panel later) look and work
  the same.
- **Who logs in:** `/admin/login` takes ADMIN and SUPER_ADMIN accounts (the super admin is an admin with more rights). The
  super admin panel will add its own pages (admins, settings, commission, audit log) to this layout later, visible only to
  SUPER_ADMIN. Each login page names the right panel for the other roles.
- **No bell:** notification-service sends nothing to admins. What needs work shows as counts instead: on the dashboard and in
  the menu (stores waiting for approval, delivery partners waiting for KYC review, deliveries without a partner), refreshed
  every minute.
- **Data:** cached under `['admin', userId, ...]` and dropped on every login and logout, like the other panels. Lists keep
  their filters in the URL.
- **Accounts the panel will not change:** the backend refuses SUPER_ADMIN accounts; the panel also leaves admin accounts to
  the super admin and never offers to disable your own account.
- **Not in the backend, so not in the panel:** support tickets, manual refunds (a refund starts by itself when a paid order is
  cancelled), roles and permissions, delivery fee and minimum order (super admin settings), settlement reports (super admin).

**The admin endpoints the screens use:**

| Area | Endpoints (all under `/api/v1/admin`) | Service |
|---|---|---|
| Dashboard, reports | `reports/dashboard`, `reports/sales/daily`, `reports/top-stores`, `reports/top-products` | report |
| Stores | `stores` (status, city, keyword), `stores/{id}`, `…/approve`, `…/reject`, `…/disable`, `…/enable` (reason) | store |
| Orders | `orders` (customer, store, status), `orders/{id}`, `…/invoice`, `…/cancel` (reason) | order |
| Payments | `payments` (status, method, store), `payments/summary` (from, to) | payment |
| Delivery | `deliveries` (status, partner), `deliveries/{id}/assign`, `delivery-partners` (KYC), `…/approve`, `…/reject`, `delivery-zones` | delivery |
| People | `customers` (keyword), `customers/{id}`, `customers/{id}/addresses`; `users` (role, status, keyword), `users/{id}/status` | customer, auth |
| Catalog | `products` (all stores), `products/{id}/block`, `…/unblock`; `categories` (also hidden) + create, edit, show/hide; `reviews` + hide (reason), show | product-read, product |
| Marketing | `coupons` (all, one store's, platform only) + platform coupon create/edit, any coupon on/off; `banners` + create, edit, delete | cart, platform |
| Monitoring | `notifications` (user, type, email status, e.g. failed emails) | notification |

| Phase | Builds | Done when |
|---|---|---|
| **A1 Foundation + stores** | Shared panel frame (vendor panel moved onto it), `/admin/login`, admin layout and menu with work counts, dashboard (the prompt's metrics: orders, orders today, sales and revenue today, active stores and partners, customers, pending refunds, out-of-stock products, average delivery time; plus the work queues), stores list (tabs by status, search) and store page (details, hours, PIN codes, commission; approve, reject with reason, disable with reason, enable) | a new vendor's store is approved in the panel and the vendor reaches the vendor panel after logging in again, on mocks and on the real backend |
| **A2 Orders, payments, deliveries** | All orders (status, store and customer filters) and the order page (everything about it, cancel with reason while it is still with the store, invoice); payments list and the money summary for a period (paid online, cash collected, refunded); deliveries by status, with "Assign a partner" for one waiting for a partner | an order is cancelled from the panel (refund for a paid one) and a waiting delivery gets a partner |
| **A3 People + delivery setup** | Accounts of every role (search, role and status filters, enable/disable); the account page adds what belongs to it: a customer's profile, addresses and orders, a vendor's store, a partner's KYC. Delivery partners (KYC queue: approve, reject with reason), delivery zones (PIN codes, payout per delivery, on/off) | a disabled customer cannot log in until enabled again; a partner's KYC is reviewed |
| **A4 Catalog, marketing, reports** | Product moderation (search all stores, block/unblock), categories (add, edit, show/hide), review moderation (hide with reason, show), coupons (all stores; platform coupons added and changed; any coupon on/off), banners (add, edit, delete, with a preview), reports (sales per day for all stores or one, top stores, top products), notification log (e.g. failed emails) | every admin page works against the real backend |

**Progress:** A1 ✅ · A2 ✅ · A3 ✅ · A4 ✅ done (all 2026-10-26). 284 unit/component tests, lint and build green; 17 Playwright
flows (6 shop, 5 vendor, 6 admin) against the real backend. Every admin response shape and error message was compared with the
running services, and every admin page was checked on a desktop and a phone screen.

Decisions taken while building A1–A4:

- **Routes:** `/admin/login` · `/admin` (dashboard, `?days=7|14|30`, or `?from=&to=` for a day or a custom range picked in the
  Sales section's calendars, "Pick a day" and "Custom range"; up to 366 days, none after today) · `/admin/orders` (`?tab=&store=&customer=&page=`) ·
  `/admin/orders/:id` · `/admin/deliveries` (`?tab=`) · `/admin/payments` (`?days=&status=&method=&store=`) · `/admin/stores`
  (`?tab=&q=&city=`) · `/admin/stores/:id` · `/admin/partners` (`?tab=`) · `/admin/zones` · `/admin/accounts` (`?tab=&status=&q=`) ·
  `/admin/accounts/:userId` · `/admin/products` (`?q=&store=&category=&status=&lowStock=`) · `/admin/categories` ·
  `/admin/reviews` (`?status=&rating=&store=`) · `/admin/coupons` (`?tab=platform|all&store=`) · `/admin/banners` ·
  `/admin/reports` (`?from=&to=&store=`) · `/admin/notifications` (`?email=&type=&user=`).
- **Logins:** `authContext.ts` lists the panels with their roles, start page and login page (`PANELS`). One `LoginForm` serves
  the shop, the vendor and the admin login; an account of another panel gets "This is a vendor account. Vendors log in on the
  vendor panel." with a link to its login. Delivery partners are told their panel comes later. In the shop, a vendor's and an
  admin's account menu links back to their panel.
- **Shared panel pieces** (`components/panel/`): the menu with groups and red work counts, the top bar, the login frame, status
  tabs, a search box, filter drop-downs and a PIN code list editor; the vendor panel now uses them too. `ReasonDialog` (any
  "reject / disable / cancel / hide with a reason"), the sales pieces (`components/sales/`) and the coupon form
  (`features/coupons/`) are shared with the vendor panel as well. `useUrlFilters()` keeps every list's filters in the URL.
- **Work counts** in the menu and on the dashboard: stores waiting for approval, KYC waiting for a review, deliveries without a
  partner. Each is the total of a one-item page, asked for every minute. No bell: notification-service sends nothing to admins.
- **Accounts instead of a separate customer list:** one list of every login (auth-service). The account page adds the
  customer's delivery profile (customer-service has no "by user id" search: the panel searches the login email and keeps the
  profile with the same user id; customer ids differ from user ids), addresses and latest orders, a vendor's store, a partner's
  KYC. The panel never offers to disable the super admin (the backend refuses it), an admin (left to the super admin) or your
  own account.
- **Orders from the platform's side:** third-person headlines ("Waiting for the store", "Cancelled by an admin: …"), the payment
  line ("the refund is on its way" for a paid order that stopped) and the staff timeline notes shared with the vendor panel.
  Cancel needs a reason and is offered while `cancellable`.
- **Deliveries:** "Assign" lists the approved partners, online ones first. The backend refuses a busy partner; the message stays
  in the dialog so another one can be chosen. The customer then sees who brings the order (DeliveryAssigned).
- **Money:** the summary of a period shows received (paid online + cash collected; a refunded payment is in neither), paid
  online, cash collected and refunded, and every method/status total. Refunds start by themselves when a paid order stops.
- **Catalog (CQRS):** after blocking a product or saving a category, the answer is put into the cached list at once and the
  list is loaded again after 2 s (the read model catches up). A hidden category disappears from the shop's menus; a blocked
  product cannot be shown again by its vendor. Hiding a review needs a reason.
- **Marketing:** platform coupons use the vendor's coupon form; store coupons can only be switched on or off ("Store coupons
  are managed by their vendor"). Banners have a live preview and their times are India time (`datetime-local` boxes,
  converted in `utils/dates.ts`).
- **Reports:** any period up to 366 days (not in the future, first day before the last), all stores or one.
- **Mock API:** `handlers/admin.ts` answers every admin endpoint; new in-memory services `mockDelivery.ts` (partners, zones,
  jobs: "ready for pickup" makes a job, assigning names the partner on the order) and `mockBanners.ts`; editable categories in
  `mockCatalog.ts`; review hiding in `mockRatings.ts`; account search and status in `mockAuth.ts`. vendor3 (Suresh Patel, no
  store) joined the mock's seed users, as in the backend seed.
- **Found while checking phone screens:** a table's hidden "Actions" column title (`sr-only`, absolutely positioned) made the
  page wider than the phone, because the scrolling table box was not positioned. Every table box now has `relative` (also in
  the vendor panel).
- **Playwright and the real data:** the store approval flow disables its test store afterwards (the shop does not list it);
  the KYC flow rejects (no new partner who could get deliveries); the account flow switches the account on again; the order
  flows cancel their order. New accounts stay (a new email every run).

---

## 9. Delivery partner panel (D1–D3)

The panel from `app-prompt.txt` §3, next in the order (super admin last). Its backend is delivery-service's
`/api/v1/delivery/**` (DELIVERY_PARTNER only): profile and KYC, availability, the partner's deliveries and their steps,
proof photo, earnings. Same working style: mock API first, then the real gateway; no backend code changes.

**How it fits into the app:**

- **Same app, own area:** `/delivery/...`, built on the shared panel pieces (menu, top bar, login frame). Partners use it on a
  phone on the road, so every page is designed for a phone first: big buttons, one task at a time, call and map links.
- **Login and sign-up:** `/delivery/login` for DELIVERY_PARTNER accounts; "Become a delivery partner" registers one
  (`/register?role=DELIVERY_PARTNER`) and opens the panel. The shop's and the other panels' logins send partners here.
- **The partner gate** (like the vendor's store gate), from `GET /delivery/profile`:

  | Profile | The panel shows |
  |---|---|
  | none yet (404 right after the registration: delivery-service makes it from UserRegistered) | "Setting up your profile", asked again for a few seconds |
  | KYC NOT_SUBMITTED | the KYC form: vehicle type and number, driving licence, zone |
  | KYC PENDING | "Waiting for review" (the details can still be changed) |
  | KYC REJECTED | the admin's reason and the form to send it again |
  | KYC APPROVED | the panel |

- **Online:** "Go online" lasts 30 minutes in delivery-service (Redis); while the panel is open and the partner is online, it
  repeats the call before the time runs out, so the partner stays online as long as the app is open. Going online may give a
  delivery at once (one waiting in the partner's zone).
- **Steps of a delivery** (the backend checks each one): ASSIGNED → Accept or Reject (it goes to another partner) → ACCEPTED →
  Picked up (the customer sees "on the way") → PICKED_UP → On my way → OUT_FOR_DELIVERY → Delivered (cash collected for a COD
  order), with an optional proof photo (JPEG/PNG/WEBP, at most 5 MB, on the way or after delivering). From PICKED_UP or
  OUT_FOR_DELIVERY: Report a problem (customer not available, wrong address, item damaged, other + note) → FAILED → Returned to
  the store → RETURNED. A delivery not picked up yet is cancelled when its order is cancelled.
- **Data:** cached under `['delivery', userId, ...]`, dropped on login and logout. The current tasks are asked for every 15 s
  while online (a new job arrives); the bell says "New delivery: order #12".
- **Proof photos** are served to logged-in users only (`/delivery-media/**`), so the panel loads them with the token (the
  dev server forwards `/delivery-media` like `/api`).
- **Not in the backend:** a zone list partners may read (only admins can list zones). The KYC form offers the zones of the
  local seed (1 Indiranagar, 2 Koramangala) from a frontend list until delivery-service offers one; a zone an admin adds later
  cannot be chosen in the form. Maps: no map in the backend, so "Directions" opens Google Maps with the two addresses.

| Phase | Builds | Done when |
|---|---|---|
| **D1 Foundation + KYC** | Delivery panel entry in the logins, `/delivery/login`, partner sign-up, layout and menu, top bar with the online switch (kept online while open), partner gate (profile retry, KYC form, waiting, rejected), profile page (vehicle, zone, KYC, rating) | a new partner signs up and sends the KYC; an approved partner goes online and offline, on mocks and on the real backend |
| **D2 Deliveries** | "Today": the current task with its next step, accept / reject an assigned one, picked up, on my way, delivered (cash to collect), proof photo, report a problem, returned to the store; the delivery page (store and customer with call and directions links, payout); the bell's links | a waiting delivery is taken and delivered from the panel, and the customer's order shows it |
| **D3 History + earnings** | Completed deliveries (delivered and returned, with the customer's rating), earnings for 7, 14 or 30 days (totals, per day chart and table) | every partner page works against the real backend |

**Progress:** D1 ✅ · D2 ✅ · D3 ✅ done (all 2026-10-26). 294 unit/component tests, lint and build green; 20 Playwright
flows (6 shop, 5 vendor, 6 admin, 3 delivery) against the real backend. Every partner response shape and error message was
compared with the running delivery-service, and every partner page was checked on a phone screen.

Decisions taken while building D1–D3:

- **Routes:** `/delivery/login` · `/delivery` (today) · `/delivery/deliveries/:deliveryId` · `/delivery/history` (`?page=`) ·
  `/delivery/earnings` (`?days=7|14|30`) · `/delivery/profile`.
- **Logins:** `PANELS` has the delivery panel, so the shop's, the vendor's and the admin's login now send a partner to
  `/delivery/login` ("Go to the delivery login") instead of saying the panel comes later. Signing up as a partner logs in at once
  and opens the panel ("Welcome, Arjun! Now send your vehicle and licence details.").
- **Partner gate:** `partnerGateState()` turns the profile into one of the states of the table above. The 404 right after the
  sign-up is retried like the customer's profile (`api/retry.ts`, now shared). A waiting partner can change the details; a
  rejected one sees the admin's reason above the form, filled in with what was sent.
- **Online:** the switch is in the top bar on every page. Availability is asked every minute, and `useStayOnline` goes online
  again 5 minutes before `onlineUntil` while the panel is open. Going online or offline reloads the current tasks (going online
  can bring a waiting job at once).
- **Today:** one task at a time, with one big button named after what happened ("I picked up the order", "I am on my way",
  "Delivered", "Items returned to the store"). A cash order asks "Collected ₹138?" before it is delivered; "Reject" asks first
  and goes back to today (the job goes to another partner). The current tasks are asked every 15 s while online, an open task
  every 30 s until it is finished. "Delivered today" and "Earned today" come from the earnings of today.
- **The delivery page:** the store and the customer as stops, each with a call link and "Directions (Google Maps)"; what to
  collect in cash; "You earn" shows the zone's rate until delivery-service sets the payout at delivery ("You earned ₹40").
- **Proof photo:** chosen with the phone's camera (`capture="environment"`), checked for type and size before the upload, and
  shown again from `/delivery-media` with the login token (a blob URL, released when the page closes).
- **Earnings:** delivery-service lists only the days with deliveries; the chart fills in every day of the period (the shared
  `DailyBarChart`, now generic for sales and earnings).
- **Mock API:** `handlers/delivery.ts` answers every partner endpoint. `mockDelivery.ts` was rewritten to follow delivery-service:
  online for 30 minutes, a job goes to an online, free, approved partner of the store's zone, and going online, delivering or
  returning offers the next waiting job. The steps tell the mock order-service (picked up, delivered: cash collected and paid,
  failed: cancelled with the reason), as the events do in the backend.
- **Playwright and the real data:** the gateway now allows only so many logins per minute (another session added rate
  limiting), so `apiLogin()` logs each account in once per run and keeps its token. The delivery flow delivers a real cash
  order of customer1 (₹138), which stays as a delivered order and as ₹40 of Arjun's earnings; Arjun is offline and free again
  afterwards. The KYC flow rejects its new partner again.
- **Found while running the suite:** the shop flow sometimes opened the second product, because it read the first card while
  the sorted list was still loading. `ProductGrid` now has `isUpdating`: the old cards fade and the list is `aria-busy`, which
  the test waits for.

---

## 10. Super admin (S1–S3)

The last panel of `app-prompt.txt` §5: the platform owner. Its backend is `/api/v1/super-admin/**` (SUPER_ADMIN only, checked
by the gateway) in four services, plus everything an admin may do. Same working style: mock API first, then the real
gateway; no backend code changes.

**How it fits into the app:**

- **The admin panel, with more pages:** the super admin is an admin with more rights (SUPER_ADMIN └── ADMIN), and the gateway
  lets the super admin use every admin endpoint. So there is no second panel that copies the admin pages: the super admin
  logs in at `/admin/login` as today and sees one more menu group, **Owner**. An admin who opens one of these pages is told
  it is for the super admin (the backend would answer 403 anyway).
- **Owner pages:**

  | Page | Backend | Shows and does |
  |---|---|---|
  | `/admin/admins` | auth `GET /admin/users?role=ADMIN`, `POST /super-admin/admins` | the admin accounts; "Add an admin" (name, email, mobile, first password); switching an admin off stays on the account page |
  | account page (existing) | auth `PATCH /super-admin/users/{id}/role` | a "Role" card: change the role of any account but the super admin's and your own |
  | store page (existing) | store `PATCH /super-admin/stores/{id}/commission` | the platform commission (0–50 %) can be changed; it counts for new orders |
  | `/admin/settings` | platform `GET/PUT /super-admin/settings` | delivery fee (0–500), free delivery from, minimum order value (not above the free delivery amount), support email and phone; the currency is shown (INR: the website shows ₹) and sent back unchanged |
  | `/admin/feature-flags` | platform `GET/POST/PATCH /super-admin/feature-flags` | every switch with its description, on/off, who changed it last; "Add a feature flag" (KEY_LIKE_THIS, description, on or off) |
  | `/admin/commission` | report `GET /super-admin/reports/commissions` | per store: delivered orders, items total, platform commission, store payout, and the totals, for a period of up to 366 days |
  | `/admin/audit-log` | platform `GET /super-admin/audit-log` | the events of every service and the platform changes, newest first; filters: source, event type, account, store, reference (order or store id ...), dates; each entry opens to show its details |
  | `/admin/system` | Prometheus `up` (see below) | each service running or not, a gateway circuit breaker that is open, and links to Grafana, Prometheus, Jaeger, Kafka UI, Kibana, Mailpit |

- **Role changes** take effect at the account's next login: an open login keeps its old role until its token runs out (at
  most an hour; there is no refresh token). Other services do not hear of a role change: customer-service and
  delivery-service make their profile only at sign-up. So the role card offers "Customer" only when the account has a
  customer profile and "Delivery partner" only when it has a delivery profile; "Admin" and "Vendor" (who then registers a
  store) always work. A vendor who loses the role also loses the link to the store (the store stays).
- **Settings:** cart-service uses the new delivery fee and minimum order a moment later (PlatformSettingsChanged); the shop's
  own copy of the settings (header "Free delivery on orders of ₹299 or more") is loaded again after a save.
- **System health:** no backend endpoint reports the services' health, but Prometheus scrapes every service (`up`, with a
  `service` label) and the gateway's circuit breakers. The dev server forwards `/prometheus` to `http://localhost:9090`, like
  `/api`; the page asks every 30 s. In production this needs a protected route to Prometheus or a health endpoint in the
  gateway.
- **Data:** cached under `['admin', userId, ...]` like the other admin pages.
- **Not in the backend** (the page says so where it matters): tax settings, payment gateway and SMS/email provider settings
  (only the feature flag RAZORPAY_PAYMENTS exists); no service or screen reads the feature flags yet; creating an admin,
  changing a role, switching an account off and changing a commission publish no event, so they are not in the audit log.

| Phase | Builds | Done when |
|---|---|---|
| **S1 People + settings** | Owner menu group (super admin only) and the page guard, admin list with "Add an admin", role card on the account page, platform settings form, feature flags (switch, add) | a new admin is created and logs in to the admin panel; a settings change reaches the shop's cart, on mocks and on the real backend |
| **S2 Money + audit** | Commission report (period, per store, totals), commission editor on the store page, audit log (filters in the URL, details) | the commission report matches the delivered orders; a settings change shows up in the audit log |
| **S3 System** | System page (services, open circuit breakers, monitoring links), Playwright flows, docs | every owner page works against the real backend |

**Progress:** S1 ✅ · S2 ✅ · S3 ✅ done (all 2026-10-26). 308 unit/component tests, lint and build green; 24 Playwright
flows (6 shop, 5 vendor, 6 admin, 3 delivery, 4 super admin) against the real backend. Every super admin response shape and
error message was compared with the running services first, and every owner page was checked on a phone and a desktop screen.

Decisions taken while building S1–S3:

- **Routes:** `/admin/admins` (`?page=`) · `/admin/settings` · `/admin/feature-flags` · `/admin/commission` (`?from=&to=`) ·
  `/admin/audit-log` (`?source=&type=&store=&day=&ref=&user=&page=`) · `/admin/system`, all inside one route element
  (`SuperAdminOnly`); the role card is on `/admin/accounts/:userId`, the commission card on `/admin/stores/:storeId`.
- **One panel:** the Owner menu group and the two cards appear only for SUPER_ADMIN; an admin who types an Owner address gets
  "This page is for the super admin". The query keys stay under `['admin', userId, ...]`.
- **Adding an admin** uses the registration rules (`validateRegistration`); the first password is given to the new admin,
  who can choose another one with "Forgot your password?" (the admin panel has no password page).
- **Role card:** offers every role but the current one and SUPER_ADMIN; "Customer" and "Delivery partner" are disabled
  with the reason when the account has no profile there (checked with the customer search by email and the partner list),
  because those profiles are only made at sign-up. A vendor's store link is removed with the role (auth-service sets
  storeId to null); the store stays.
- **Settings:** the amounts are typed as text and checked like SettingsRequest (fee 0–500, two decimals, minimum not above
  the free delivery amount); the currency is shown and sent back unchanged. After a save the shop's settings are loaded
  again (top bar, cart hints). cart-service applied a new fee within seconds in the Playwright run.
- **Feature flags:** the key is turned into UPPER_SNAKE_CASE while typing ("city pune" → CITY_PUNE); a switch and a new flag
  go into the cached list at once (sorted by key). The page says that nothing reads the flags yet.
- **Commission:** the report uses the shared period form (`components/sales/ReportPeriodForm`, also used by the reports
  page now) and shows each store's share of its items; the store page has a 0–50 % editor. The mock uses the store's current
  percentage; report-service keeps the one each order was placed with.
- **Audit log:** filters in the URL; the event types offered follow the chosen source; a day filter is one India day
  (`from` = its start, `to` = the next day's start, not included). Each entry links the order (from `orderId` in the
  details), the account and the store, and opens to the whole event (indented when it is JSON; platform-service writes some
  changes as Java record text). The page says which changes are not recorded.
- **System:** `api/monitoring.ts` asks Prometheus for `up{job="grocery-services"}` and the gateway's circuit breakers that
  are not closed (`resilience4j_circuitbreaker_state`); every 30 s and with "Check again". When Prometheus cannot be
  reached, the page says how to start it. The tools are links to the local addresses of `infrastructure/docker-compose.yml`
  (tracing moved from Zipkin to Jaeger in the infrastructure while this was built; the page links Jaeger).
- **Shared pieces:** `components/ui/Switch` (coupons and flags), `components/sales/KpiTile`, `reportPeriod.ts` and
  `ReportPeriodForm`.
- **Mock API:** `handlers/superAdmin.ts` answers every `/api/v1/super-admin/**` endpoint (`asSuperAdmin`);
  `mockPlatform.ts` keeps the settings (the mock cart now reads them), the flags and an audit log that records the settings,
  flag and banner changes like platform-service; `mockAuth.ts` creates admins and changes roles (and remembers the sign-up
  role, so the mock customer profiles behave like customer-service); `mockStores.setCommission`, `mockReports.commissionReport`;
  `handlers/monitoring.ts` answers the Prometheus queries (all up).
- **Playwright and the real data:** the flows put the settings, Fresh Mart's commission and DELIVERY_SLOTS back (only when
  they are still changed, since every save is an audit entry), switch the new admin off at the end, and leave a new customer
  account per run (made an admin and a customer again). Each run adds two "Platform settings updated" and two "Feature flag
  changed" entries to the audit log.

---

## 11. Completing the shop (C1–C4)

With the five panels done, a comparison of every backend endpoint with `src/api` left these customer features unused:
the AI shopping assistant (`/api/v1/chat/**`, backend phase 14: "chat widget in Fresh Grocery: later"), Razorpay Checkout
(`razorpay-checkout` / `razorpay-verify`, used when payment-service runs with `PAYMENT_GATEWAY=razorpay`), one store
(`GET /api/v1/stores/{id}`) and the public feature flags. The inventory lists are covered by the product search's low-stock
filter (vendor and admin product pages). Same working style: mock API first, then the real gateway; no backend code changes.

**C1 Shopping assistant (chat widget)**

- A round "Ask our assistant" button in the bottom right corner of every shop page (the back-to-top button moves up),
  for guests and customers (other roles have their own panels). It opens a chat panel: full screen on a phone, a card
  above the button on bigger screens; Escape and the close button close it, and the focus goes back to the button.
- First view: what the assistant can do and three example questions (a logged-in customer also gets "Where is my last
  order?"). A question is shown at once; "Looking it up…" while waiting (an answer takes 2–60 s on a CPU; the gateway waits up
  to 180 s); the answer is plain text, never HTML. A grounded answer says "Checked against the shop's live data".
- `POST /api/v1/chat/messages` without `conversationId` starts a conversation; the answer's id goes with the next message.
  The id is kept in sessionStorage per login (guest or user id): a reload shows the conversation again
  (`GET /conversations/{id}`), while a login or logout starts a new one (conversations belong to their user; another user
  gets 404). "New chat" deletes the conversation (`DELETE`).
- Errors: 400 (empty, more than 500 characters: a counter shows what is left), 429 (10 questions a minute: the gateway's
  message), 503 (the language model is not running) are shown in the chat with "Try again"; a lost conversation (404)
  starts a new one.
- Mock: a small rule-based assistant over the mock catalog (product prices and stock, offers, delivery rules, the
  customer's last order), with the same JSON, owner rule and errors.

**C2 Razorpay Checkout**

- payment-service runs either the mock gateway (local default, `gatewayOrderId` = `mock_order_<id>`) or Razorpay
  (`order_...`). The order page's payment panel reads the mode from the payment: the test buttons stay for the mock; with
  Razorpay "Pay ₹X" loads `checkout.js` once, opens Razorpay's window with the details of `razorpay-checkout` (key id, order
  id, amount, the customer's name, email and phone), and sends what it returns to `razorpay-verify`.
- Closing the window changes nothing (the customer can pay again until the deadline); a failed attempt keeps the payment
  waiting with `failureReason`, which the panel shows above the button.
- Not testable against Razorpay here: there are no test keys (`rzp_test_replace_me`). Tested with the mock API and a fake
  `window.Razorpay`.

**C3 Stores and flags in the shop**

- `/stores`: every approved store with open or closed now, today's hours, address and "Shop here". `/stores/:storeId`
  (`GET /api/v1/stores/{id}`): address, phone, taking orders or not, the week's hours, and its products.
- The shop gets a store filter (`/shop?store=1`, in the sidebar and as a removable chip); product pages name the store with
  a link to it; the main menu and the footer link to `/stores`.
- Feature flags: the shop reads `GET /api/v1/platform/feature-flags`; PRODUCT_REVIEWS off hides the "review this product"
  form of delivered orders (the backend does not check the flag). The Feature flags page says which flag the website uses.

**C4 Checks and docs:** Playwright flows (a guest asks the assistant a real question; stores and the store filter), the
READMEs, and a list of the demo accounts and local tools.

| Phase | Builds | Done when |
|---|---|---|
| **C1 Shopping assistant** | chat API module, chat hook (conversation per login, reload, new chat), chat button and panel, mock assistant | a guest and a customer chat with the real assistant through the gateway |
| **C2 Razorpay Checkout** | Razorpay API calls, checkout.js loader, payment panel for both gateways | the mock gateway works as before; the Razorpay path passes its tests |
| **C3 Stores and flags** | stores page, store page, shop store filter, store links, public flags, review form behind PRODUCT_REVIEWS | the store pages and the filter work against the real backend |
| **C4 Checks and docs** | Playwright flows, docs, accounts list | every flow passes against the real backend |

**Progress:** C1 ✅ · C2 ✅ (mock API and fake checkout only, see below) · C3 ✅ · C4 ✅ done (2026-10-27). 320
unit/component tests, lint and build green; 27 Playwright flows against the real backend (+1 assistant, +2 stores and flag).
The demo accounts and the local tools are listed in [local-accounts-and-tools.md](local-accounts-and-tools.md).

Decisions taken while building C1–C4:

- **Routes:** `/stores` · `/stores/:storeId` · `/shop?store=` (the store filter); the chat is not a page but a panel on
  every shop page.
- **Chat state:** the messages live in the chat button's component, which stays mounted while the customer moves between
  shop pages, so an answer that arrives after a page change is not lost. A login or logout gives the component a new key
  (`guest` / the user id) and so a fresh chat. The messages of a saved conversation are loaded once when the chat is
  first opened (React Query, kept for good); later messages are added locally.
- **Chat errors:** the client's own texts are shown (429: "Too many requests…", 502–504: "This service is not available
  right now…"), with "Try again", which asks the same question again without showing it twice. Escape closes the chat
  wherever the focus is; the focus goes back to the chat button.
- **Real assistant:** answers took 20–40 s (a CPU runs the model); "grounded" answers say "Checked against the shop's live
  data". The model sometimes says it cannot answer reliably (e.g. "And is delivery free?" in a follow-up); that is the
  backend's answer check working, not a frontend error.
- **Shopping by chat** (2026-10-29, [assistant plan, section 20](ai-assistant-plan.md#20-shopping-by-chat-the-cart-orders-and-cancellations-2026-10-2829)):
  - an answer can carry a card (`ChatActionCard`), an order to place or an order to cancel, built by the backend;
  - only the card's Confirm places or cancels the order, with this browser's own call to order-service: an
    `Idempotency-Key` per card, the summary's checkout fingerprint, and 10 minutes to confirm;
  - the confirmed card goes with the next question (`confirmed`), so the assistant knows the order exists;
  - the shop's chat loads the cart after every answer, since the assistant can change it;
  - the Playwright flow is `e2e/assistant-orders.spec.ts`.
- **Razorpay:** the payment panel keeps both mutations (test gateway and Razorpay) itself, not in the buttons, because the
  buttons disappear as soon as the payment is made and a component that is gone gets no `onSuccess` (the "Payment
  received" message was lost before this). `checkout.js` is loaded from `checkout.razorpay.com` the first time someone pays.
- **Stores:** a store's products come from the product search (`storeId`), its details from `GET /api/v1/stores/{id}`
  (asked again after a minute: "open now" changes during the day). Today's opening hours use the India weekday.
- **Feature flags:** only PRODUCT_REVIEWS is used by the website; when the flags cannot be loaded the review form stays
  (it is on today). The super admin's switch refreshes the shop's copy in the same browser; other browsers within
  5 minutes.
- **System page:** the infrastructure moved tracing from Zipkin to Jaeger during this work; the monitoring links now point
  to Jaeger (16686) and include Kibana.
- **Playwright and the real data:** the assistant flow ends with "New chat" (the conversation is deleted); the flag flow
  switches PRODUCT_REVIEWS on again only when it is still off.

## 12. Live delivery tracking (T1)

The customer follows the delivery partner on an OpenStreetMap map, live; the partner's phone shares its position from the
browser while delivering. This phase needed backend work too (delivery-service, order-service, the gateway). Everything
is described in [delivery-tracking.md](delivery-tracking.md): the architecture, the API contracts with real samples, the
settings, the OpenStreetMap tile policy, the optional routing (OSRM), the limits of a web page in the background, and
production notes.

**T1 Live delivery tracking**

- Customer: `/orders/:orderId/track` (a "Track on the map" button on the order page once it is ready for pickup or on its
  way): the map with the store, the address and the partner, a headline per status, the arrival time (only with a routing
  service) or the straight-line distance, the partner card (name, vehicle, call, last update), the order progress, and
  every state of the connection (connecting, live, reconnecting, waiting for the delivery, finished, not available) and
  of the data (no position yet, a delayed position, a place without coordinates).
- Partner: a "Live location" box in the delivery screen while the delivery is accepted, picked up or on its way: start
  (permission), what is shared and when, the reasons it cannot (blocked, no GPS, old browser, not https), the map.
- Reusable parts: `config/tracking.ts` (every map setting, `VITE_MAP_*`), `components/map/` (map, markers, route line, fit
  view, loaded on demand), `api/tracking.ts` + `api/eventStream.ts`, `useTrackingStream`, `useLocationSharing`.
- Mock API: the same endpoints, rules and a real `text/event-stream`; the mock delivery panel moves it.

| Phase | Builds | Done when |
|---|---|---|
| **T1 Live delivery tracking** | backend: coordinates on the delivery, location API, Redis store + Pub/Sub, SSE stream, routing client, gateway route; frontend: tracking page, location sharing, map components, mock API; simulation script; tests and docs | a partner's phone (emulated GPS) and a customer's page work together against the real backend |

**Progress:** T1 ✅ done (2026-10-27). 337 unit/component tests, lint and build green; 28 Playwright flows against the real
backend (+1: two browsers, partner and customer).

Decisions taken while building T1:

- **SSE, not WebSockets:** the updates go one way; SSE is plain HTTP through the gateway (JWT, rate limits). `EventSource`
  cannot send the `Authorization` header, so the stream is read with `fetch` (`api/eventStream.ts`).
- **Leaflet on demand:** the map component is loaded with `React.lazy`, so only map pages download Leaflet and its CSS
  (46 kB gzip).
- **The map fits its view without animation** when points appear: an animated fit and a pan to the partner started in
  the same moment fought each other and left markers out of view (found in the Playwright screenshots; the flow now
  checks that every marker is inside the visible map).
- **Order progress from the live status:** order-service hears about a pick-up a moment later (Kafka), so the tracking
  page moves the progress on from the delivery's status (`orderStepOf`) instead of waiting for the next order refresh.
  The progress list is vertical in the page's narrow side column (`OrderProgress vertical`).
- **No arrival time without routing:** a straight line is not a road; the page shows the straight-line distance and says
  so, instead of guessing a time.
- **Tests do not load OpenStreetMap tiles** (the public servers are not for automated traffic); Vitest renders Leaflet in
  jsdom with markers only.

## 13. Modern login screens and admin dashboard (M1–M3)

The request: the customer login on a page of its own (not inside the shop's header and footer), every login and sign-up
page in a modern design with illustrations, and a modern admin dashboard with the parts around it. Only the look and the
page frames change: no API and no backend changes. The labels, button texts and headings that the tests and the
Playwright flows use stay the same.

**M1 Login and sign-up screens**

- `/login`, `/register`, `/forgot-password` and `/reset-password` leave the shop layout: full-screen pages without the
  shop's header, menus, newsletter and footer. The logo and "Back to the shop" lead back.
- One split screen for every login and sign-up page (`AuthScreen`, it replaces `PanelAuthFrame`): the form on the right;
  on big screens a coloured panel on the left with an illustration, a headline and three points about that panel; on
  phones a small illustration above the form. Each panel has its own look: shop (green, grocery basket), vendor (orange,
  store front), delivery (blue, rider on a scooter), admin (dark, dashboard). The "This panel is for …" notices use the
  same screen.
- The illustrations are React components that draw inline SVG (`components/auth/illustrations/`): no image files, sharp
  at any size, in the theme's colours, hidden from screen readers.
- Forms: icons in the fields; the password rules tick themselves off while typing (sign-up, new password); the sign-up
  page switches between Shop, Sell and Deliver (`/register?role=`) and its "Log in" link opens the right panel's login;
  while developing, a demo account fills in the login form with one click.
- Forgot and new password: an envelope once the code is sent, a padlock for the new password, an open padlock with a
  check once it is changed.

**M2 Admin dashboard**

- Panel frame: a dark menu for the admin panel (the vendor and delivery panels keep the light one, with the same new
  current-page style); a top bar with the user's initials, name and role.
- Dashboard: a welcome banner (today's date, greeting, quick links, illustration); "Needs your attention" tiles that turn
  amber with "Review" when work waits and say "All clear" otherwise; today's and the platform's numbers as tiles with
  icons; active customers with a meter (their share of all customers); the latest 5 orders (the order list's first page,
  shared with the Orders page); the sales section with a segmented period picker and top lists with a bar per row (its
  share of the first row).
- The shared parts (KPI tile, period picker, ranked list, menu, top bar) change for every panel that uses them.

**M3 Checks and docs:** component tests for the new behaviour (the login outside the shop layout, demo fill, password
rules, the sign-up switch, the latest orders), the existing tests and Playwright flows still passing, screenshots of every
screen on a phone and a laptop, the README and this section.

| Phase | Builds | Done when |
|---|---|---|
| **M1 Login and sign-up** | auth routes outside the shop layout, `AuthScreen`, illustrations, field icons, password rules, sign-up switch, demo fill | every login, sign-up and password page works on a phone and a laptop, against the mock API and the real backend |
| **M2 Admin dashboard** | dark admin menu, top bar, welcome banner, attention tiles, KPI tiles, latest orders, ranked lists with bars | the dashboard shows the real backend's numbers on a phone and a laptop |
| **M3 Checks and docs** | tests, screenshots, docs | unit tests, lint, build and the Playwright flows are green |

**Progress:** M1 ✅ · M2 ✅ · M3 ✅ done (2026-10-27). 345 unit/component tests (+8: the separate login, demo fill,
password rules, the sign-up switch, the dashboard's attention tiles and latest orders, the retry rules), lint and build
green; all 28 Playwright flows pass against the real backend. Screens checked on a phone (390 px) and laptops (1280 and
1440 px).

Decisions taken while building M1–M3:

- **Routes:** `/login`, `/register`, `/forgot-password` and `/reset-password` are a route group of their own, outside
  MainLayout (no header, sale pop-up, assistant button or footer). `AuthScreen` brings its own `ScrollRestoration`.
- **One frame:** `AuthScreen` replaced `PanelAuthFrame` for the shop and the three panels; a look is one entry in
  `components/auth/authLooks.ts`. The form comes first in the page; the side panel is moved to the left only visually
  (`order-first`), so the keyboard and screen readers reach the form first.
- **Illustrations:** inline SVG components, no image files. On phones a small one sits above the form, except on the
  password pages, which show their own (padlock, envelope).
- **Texts kept:** the labels, buttons and headings the tests and Playwright use did not change ("Log in", "Create account",
  "Admin login" …). The sign-up page's "Log in" link now opens the vendor or delivery login for those accounts.
- **Password rules:** `PASSWORD_RULES` sits next to `passwordError` (the same rules); the list is the box's hint, so screen
  readers read it as the box's description, and a met rule adds ": done".
- **Demo accounts:** only while developing (`import.meta.env.DEV`). Their buttons avoid the words "Log in" and "Email",
  which the Playwright flows look for by part of a name.
- **Edge's own "reveal password" eye** is hidden (`::-ms-reveal`): it doubled the Show / Hide button.
- **Admin dashboard:** the dark menu is a `dark` option of the shared `PanelSidebar`; an attention tile is amber only
  together with its number and "Review now" (never colour alone); the meter and the bars of the top lists are hidden from
  screen readers because the numbers are written next to them; the latest orders use the Orders page's first-page request
  (the same cache entry).
- **Shared parts:** the KPI tile (icon), period picker (segmented), top lists (bars), top bar (initials and role) and menu
  (current-page bar) changed for every panel, so the vendor and delivery panels have the same look.
- **"Too many requests" is retried once:** with the login outside the shop layout, the header's data is no longer loaded
  as a guest before signing up, so a new customer's first home page (about 30 requests, doubled in part by React's
  StrictMode while developing) sometimes used up the gateway's burst of 40 per user, and "My account" got a 429 (found by
  the Playwright flow, 1 run in 3). The gateway answers 429 with `Retry-After: 1`; a query now tries once more after
  1.5 s instead of showing the error (`api/retry.ts`). Other 4xx answers are still not retried, mutations never.

## 14. Shop top bar, one page background and a theme switch (B1–B3)

The request: remove the thin bar above the shop's header ("Need help? Call us: 1800-123-4567"), because the same
information is shown below it; give the whole app one fitting background colour; and let users switch the theme with a
button in the middle of the right edge of the page. Only the look changes: no API and no backend changes.

**B1 Top bar and page background**

- The shop's `TopBar` goes away on every shop page (it was the same bar everywhere). Nothing is lost: the support phone
  is in the menu bar (laptops), the phone menu and the footer; the free-delivery amount is in the feature strip and the
  cart; the Log in / Wishlist / Cart links are the header's icons. The settings page's hint about the phone is updated.
- One page colour for every screen: a new `page` colour token (a soft green-grey) behind the shop, the login screens and
  the three panels (they used the grey `surface`). Cards stay white and stand out on it; the header and the menu bar are
  white too. Grey boxes that sat straight on the page (the feature strip, the order status box …) become white cards.
- A new `card` colour token replaces the hard-coded `bg-white` of cards, menus, dialogs and fields, so the dark theme can
  change it. White that must stay white (the knob of a switch, a map marker's ring) keeps `bg-white`.

**B2 Theme switch and dark theme**

- A small tab fixed in the middle of the right edge of every page (shop, login screens, panels) opens a panel with three
  choices: Light, Dark and Same as device (the default). The choice is saved in the browser (`localStorage`); "Same as
  device" follows the computer's or phone's setting, also when it changes.
- The dark theme gives the same colour names dark values (`<html data-theme="dark">` in `styles/index.css`), so the
  components do not change: green stays the brand colour, text becomes light, cards dark. A small script in `index.html`
  sets the theme before the page is drawn, so a dark page does not flash white. The toasts follow the theme.
- The panel is a group of radio buttons (arrow keys move between them); Escape or a click outside closes it.

**B3 Checks and docs:** component tests (the top bar is gone and the phone is still there; the theme switch changes and
remembers the theme), the existing tests and Playwright flows still passing, screenshots of the shop, a login screen and
the panels in both themes on a phone and a laptop, the README and this section.

| Phase | Builds | Done when |
|---|---|---|
| **B1 Top bar and background** | no `TopBar`, `page` and `card` colours, white header and menu bar | the shop, the login screens and the panels share one background in the light theme |
| **B2 Theme switch** | dark colour values, `index.html` script, `features/theme/` (choice, hook, switch) | every screen reads well in the dark theme, and the choice survives a reload |
| **B3 Checks and docs** | tests, screenshots, docs | unit tests, lint, build and the Playwright flows are green |

**Progress:** B1 ✅ · B2 ✅ · B3 ✅ done (2026-10-27). 349 unit/component tests (+4: no top bar, the theme switch picks,
remembers and follows the device, arrow keys and Escape), lint and build green; all 28 Playwright flows pass against the
real backend (a first run lost the typed new password in the password-reset step once, like the page reset seen in M3;
the flow then passed 4 times in a row and in a second full run). Screens checked in both themes on a phone
(390 px) and laptops (1280 and 1440 px): home, shop, product, cart, order, account, the login, sign-up and password pages,
the admin dashboard and orders, the vendor dashboard, the delivery panel, the phone menu and a confirm dialog.

Decisions taken while building B1–B3:

- **Top bar:** removed from every shop page, not only Home (it was one bar for all pages, and everything in it is shown
  below it). `TopBar.tsx` is deleted.
- **Colours:** `page` light `#f4f7f5` (soft green-grey), dark `#0f171a`; `card` white / `#172126`; dark text and green
  values checked for contrast (body text 8:1, green text 8.7:1 on a dark card; the light theme keeps its values).
  `ink-soft` replaces `heading` as the light end of the dark admin gradients, and the backdrops of drawers and dialogs use
  `ink`, because `heading` turns light in the dark theme. The yellow button's text is `ink` for the same reason.
- **`bg-white` → `bg-card`** everywhere (107 files) except the knobs of the switches; bordered boxes that had no fill (they
  showed the white page through) got `bg-card`; grey boxes that sat straight on the page (feature strip, order status,
  cart column titles, round remove buttons, scroll arrows, the sign-up switch) became white cards.
- **No `dark:` classes:** the dark theme only gives the colour names other values, so a new component gets both themes for
  free as long as it uses the theme names. The illustrations' backdrop circles use `fill-card` so they darken too.
- **Theme switch:** outside the router in `App.tsx`, so it is on every page and the error pages; `z-40`, below dialogs and
  drawers (`z-50`); hidden when printing. Three choices instead of a light/dark toggle, so "Same as device" can be picked
  again; the toasts get the same choice (`<Toaster theme>`).

## 15. An AI assistant in every panel (P1)

The request: an AI chatbot for every panel, so everyone can ask about their own work. The shop's assistant only knew the
shop; the user chose real AI answers with backend changes (phase A6 of [ai-assistant-plan.md](ai-assistant-plan.md),
section 14: read-only tools per role in mcp-server-service, rules per role in mcp-client-service). The chat API stays
the same (`/api/v1/chat/**`): the backend picks the tools and the rules from the login.

**P1 Panel assistants (frontend)**

- The vendor, delivery and admin panels get the chat button in the bottom right corner ("Ask the assistant"), shown to
  their own logins on every page of the panel, also before the store or the KYC is approved (the assistant can say where
  it stands).
- One chat for all: `ChatPanel` and `useChat` are shared; a table of texts per place (`assistantTexts.ts`) gives the title
  (Store assistant, Delivery assistant, Admin assistant), the greeting, three example questions, the placeholder and the
  "checked" note. The shop keeps its texts word for word.
- Each login has its own conversation (as in the shop: the id in sessionStorage per user, a new chat after another login).
- The mock API answers the panels' example questions from the mock data (`mocks/mockPanelChat.ts`).
- Checks: component tests (each panel's chat, its title and an answer from that user's data), screenshots, a Playwright
  flow with the real model (a vendor asks about the new orders).

| Phase | Builds | Done when |
|---|---|---|
| **P1 Panel assistants** | the chat in the three panels, texts per panel, mock answers, tests | a vendor, a partner and an admin get answers from their own live data in their panel |

**Progress:** P1 ✅ built (2026-10-27). 352 unit/component tests (+3: the vendor's, the partner's and the admin's chat),
lint and build green; screenshots of the vendor and admin chat on a laptop (light and dark) and the delivery chat on a
phone. The new backend answered real questions from vendor1, delivery1 and the admin (9 of 9 panel golden questions,
ai-assistant-plan.md section 14). The new Playwright flow (`e2e/assistant.spec.ts`, flow 29) needs mcp-client-service and
mcp-server-service of the new version on ports 8114 and 8115 behind the gateway; it was not run yet, because the
services running there were started by another session and were not restarted.

## 16. Pagination, profile popup, notifications and charts (U1–U5)

The request: page controls above and below every paged table (page size too), a profile popup, a modern notification
popup, and pie, bar and area charts wherever they fit the data, in every panel; responsive and accessible, with loading,
empty and error states; no duplicate features and no charts for their own sake. Frontend only: every feature uses
endpoints that exist. ("Restaurant owner" in the request is this app's vendor.)

**What the review found**

- 20 paged lists use `Pagination` under the list only, with a fixed page size per list (20; 10 for My orders). The panels
  keep the page in the URL (`useUrlFilters`); the APIs accept a `size` parameter (order-service allows up to 100).
- Profile: the shop's `AccountMenu` (a small link menu for customers) and the panels' `PanelTopBar` (initials, name and
  a separate "Log out"). There are no profile photos in the backend (`User` has no picture): the avatar shows initials.
- Notifications: the bell (customers, vendors, partners; admins get none from notification-service) shows the latest 6 as
  plain text with a dot; there is no page with all of them.
- Charts: one hand-made column chart (`DailyBarChart`) for sales per day (admin dashboard, vendor dashboard, admin
  reports) and earnings per day (delivery); "top stores" and "best sellers" are lists with share bars (`RankedListCard`),
  already a bar chart.

**U1 Page controls above and below the tables**

- `TablePagination` (new, uses `Pagination`): "Showing 21–40 of 132", rows per page (10, 20, 50) and the page buttons.
  Each paged list shows it above and below; both read the same URL (`?page=&size=`), so they always agree. A new page
  size goes back to page 1.
- `useUrlFilters` gets `pageSize` / `setPageSize`; each list hook takes the size instead of its constant.
- Where: every paged table of the admin panel (12), the vendor panel (4), the delivery history, My orders and the new
  notifications pages. Not: the shop's product grid and a product's reviews (not tables; the shop grid keeps its own bar).

**U2 Profile popup**

- One `ProfileMenu` for the shop and the panels: the avatar button ("Account of <name>") opens a card with a large avatar
  (initials), name, role and status, email, phone, member since and last login, and the actions of the role:
  - customer: My account, My orders, Addresses, Wishlist, Notifications, Log out;
  - vendor: Store settings, Notifications, See the shop, Log out (the card also shows the store);
  - delivery partner: My profile, Earnings, Notifications, Log out (the card also shows the zone);
  - admin: My account details, See the shop, Log out; a vendor or admin in the shop: their panel, Log out.
- "Log out" moves from the panels' top bar into the card (one place, like the shop).

**U3 Notification popup and page**

- The bell's card: a filter All / Unread, an icon per kind of notification (order steps, payments, refunds, low stock,
  stores, KYC, welcome), the time as "5 min ago" (the exact time on hover and for screen readers), unread ones with a dot
  and a light background, "Mark as read" per notification, "Mark all as read", and "View all notifications".
- A notifications page for each role with a bell: `/account/notifications` (customers, in the account area), `/vendor/notifications`,
  `/delivery/notifications`: All / Unread, the same rows, page controls above and below.
- One place for the icons (`notificationLooks.ts`) and the links (`notificationLinks.ts`), used by the bell and the page.

**U4 Charts where the data asks for them**

- New `AreaChart` (a trend over time, one series) and `DonutChart` (part of a whole, at most 4 parts, always with a
  legend that shows the numbers); `DailyBarChart` stays for amounts per day or month that are compared one by one.
  Every chart: hover and keyboard focus show the values, the numbers are also in a table ("Show the numbers"), colours
  from tokens that change with the theme, checked with the dataviz palette validator in both themes.
- Admin dashboard, vendor dashboard and admin reports: sales per day becomes an **area chart** (a trend); new **donut**
  "Orders in this period": counted as sales / not confirmed yet / cancelled, rejected or not delivered (from the same
  daily report, no extra request). Top stores and best sellers stay bar lists.
- Admin payments: **donut** "Payments by status" in 4 groups (paid or cash collected, not paid yet, failed or cancelled,
  refunded) from the summary that is already loaded; the full table of every method and status stays next to it.
- Customer, My orders: **bar chart** "Your spending", the last 6 months, from the newest 100 orders (orders that count:
  not cancelled, rejected or failed).
- Delivery earnings keep their **bar chart** (amount per day).
- Not added on purpose: a donut of the vendor's order steps (the dashboard tiles show the same four numbers), a stock
  donut (no endpoint gives the sold-out count), charts on lists and forms.

**U5 Checks and docs:** component tests for each new component and page, the existing tests and Playwright flows
updated where "Log out" moved, screenshots on a phone and a laptop in both themes, the README and this section.

| Phase | Builds | Done when |
|---|---|---|
| **U1 Page controls** | `TablePagination`, page size in the URL, 18 lists | every paged list has working controls above and below |
| **U2 Profile popup** | `Avatar`, `ProfileMenu`, shop and panel top bars | every role sees its details and actions in the popup |
| **U3 Notifications** | bell card, notifications pages, icons and links | a user can filter, open, mark and page through notifications |
| **U4 Charts** | `AreaChart`, `DonutChart`, sales sections, payments, customer spending | the charts read the same numbers as the tables, in both themes |
| **U5 Checks and docs** | tests, screenshots, docs | unit tests, lint, build and the Playwright flows are green |

**Progress:** U1 ✅ · U2 ✅ · U3 ✅ · U4 ✅ · U5 ✅ done (2026-10-27). 369 unit/component tests (+17: page controls,
the profile card, the bell and the notifications pages, the area and donut charts, the spending card, the date and
money helpers), type check, lint and build green; screenshots of every new part on a laptop (1440 px) and a phone
(390 px) in both themes. The admin Playwright flow now opens the profile card before "Log out"; 28 Playwright flows
pass against the real backend (all but flow 29, the vendor's assistant, which still waits for the new mcp services, see
section 15).

**Decisions made while building**

- The page size is in the URL only when it is not the list's own size (`?size=50`); a size that is not one of 10, 20, 50
  (the admin notification log has 30) is still offered in the select, so the select always shows the size in use.
- The vendor's products page keeps its own filters and paging; it only takes the page size from the URL.
- On a phone the profile and notification cards are fixed under the top bar, 16 px from both edges; from `sm` they hang
  from their button.
- Chart colours are four tokens (`chart-good`, `chart-wait`, `chart-bad`, `chart-info`) with their own dark-theme
  steps; the validator passes them on the card colour of each theme, and its contrast warnings are met by the legend,
  which always shows the name, number and share of each part next to its colour.
- The donut's legend sits beside the ring only when the card is wide enough (a container query), not on the screen
  width, because the same card is in a narrow grid column on the admin dashboard.
- "Your spending" counts the newest 100 orders and says so when the customer has more.

## 17. Delivery panel redesign, collapsible menu and one loading spinner (R1–R4)

The request: a modern, clean, responsive redesign of every delivery partner page; a button on the edge of the panel
menu that collapses and expands it; one modern loading spinner in the whole app, shown whenever data or a request takes
time; nothing that the pages do may change. Frontend only.

**What the review found**

- Loading: 74 files use `Skeleton`; most of them show a single grey block for a whole page or section
  (`<Skeleton className="h-64" />`), which does not say that anything is happening. A list that loads its next page only
  fades (`opacity-60`). Opening a page whose code is not loaded yet shows nothing: the old page stays until the new one is
  ready. Buttons show a spinner (`LoaderCircle`); the chat, the payment panel and the partner gate each draw their own.
- Menu: `PanelSidebar` (vendor, delivery and admin panels) is a fixed 256 px column from `lg` and a drawer on phones; it
  cannot be made smaller.
- Delivery panel (made for a phone): plain cards; the current delivery shows its step only as a badge; on a phone the 4
  pages are behind ☰; the history and the profile are plain lists; the KYC pages do not show how far the partner is.

**R1 One loading spinner**

- `Spinner` (`components/ui`): the app's only spinner, a faint ring with a turning arc in the current text colour (it
  turns slower when the device asks for less motion). `Button`, `PageLoading`, the chat, the payment panel and the
  partner gate use it instead of their own icons.
- `LoadingState`: the spinner and a short text ("Loading…") at the height of what is coming. It replaces the single
  grey blocks: `<Skeleton className="h-64" />` becomes `<LoadingState className="min-h-64" />`. Skeletons that draw the shape
  of what is coming (product cards, notification rows, number tiles) stay, because they show where things will appear.
- `LoadingIndicator` (once, in `App`): a small "Loading…" pill with the spinner at the top of the screen while the app
  waits longer than 0.4 s for something the user is looking at or asked for: a page opening (its code), data that is not
  on screen yet (a first load, another page or filter of a list), or a change being saved. Background refreshes of data
  already on screen (polling) do not show it, and a request with its own progress text opts out with
  `meta: { quiet: true }` (the chat, the partner's automatic "stay online").

**R2 Collapsible panel menu**

- A round button on the menu's edge, level with the logo ("Collapse menu" / "Expand menu", `aria-expanded`): from `lg`
  the menu shrinks to an 80 px column of icons, and grows back. Collapsed: the leaf mark only, the icons with their
  numbers, the page's name as a tooltip on hover and keyboard focus (it stays the link's name for screen readers), the
  store or zone box hidden.
- The choice is kept in the browser (localStorage) and is the same in all three panels. Phones keep the drawer.

**R3 Delivery panel redesign** (the same data, buttons and steps; only the look and the layout change)

- Phones: a bar at the bottom with the 4 pages (Today, History, Earnings, Profile), in reach of the thumb, instead of ☰;
  the assistant's button moves up above it.
- Today: a greeting with the date and the online state, three tiles (delivered today, earned today, rating), then the
  current delivery.
- A delivery: a step bar (Accepted, Picked up, On the way, Delivered), the trip drawn as a route from the store to the
  customer with call buttons and directions, money tiles with icons, the proof photo and the rating in cards.
- History: rows with a status icon, the trip, the date, the rating and the payout.
- Earnings: tiles with icons; the chart card stays.
- Profile: a header with the avatar, name, KYC state and rating; contact and vehicle cards; support with call and mail
  links.
- KYC pages: the steps of joining (Sign up, Send KYC, Review, Deliver) with the current one marked, and the cards with a
  coloured icon.
- Not changed: the login page (redesigned in M1), the notifications page (U3), the KYC form's fields and checks.

**R4 Checks and docs:** tests for the spinner states, the indicator (shown for slow loads and saves, not for polling or
quiet requests), the menu button (names kept, choice saved) and the delivery pages (step bar, bottom bar, profile); the
existing tests and Playwright flows kept working; screenshots on a phone and a laptop in both themes; the README and
this section.

| Phase | Builds | Done when |
|---|---|---|
| **R1 Loading spinner** | `Spinner`, `LoadingState`, `LoadingIndicator`, the single grey blocks replaced | every wait in the app shows the same spinner |
| **R2 Collapsible menu** | the edge button, the icon column, tooltips, saved choice | every panel's menu collapses and expands from `lg` |
| **R3 Delivery redesign** | bottom bar, Today, a delivery, History, Earnings, Profile, KYC pages | every delivery page has the new look on a phone and a laptop |
| **R4 Checks and docs** | tests, screenshots, docs | unit tests, lint, build and the Playwright flows are green |

**Progress:** R1 ✅ · R2 ✅ · R3 ✅ · R4 ✅ done (2026-10-27). 375 unit/component tests (+6: the loading block and the
"Loading…" sign (slow loads and saves; not for polling or quiet requests), the menu button, the delivery step bar and
bottom bar, the joining steps, the profile), type check, lint and build green; screenshots of every delivery page, the
collapsed vendor and admin menus and the loading states on a laptop (1440 px) and a phone (390 px) in both themes.
28 Playwright flows pass against the real backend (all but flow 29, see section 15); the shop's assistant flow
failed once in the full run because the dev server reloaded the page when a README changed meanwhile, and passed when
run again.

**Decisions made while building**

- The sign counts a query only while it has no data yet (`query.state.data === undefined`): a first load or another
  page of a list, never a refresh of what is on screen. It waits 0.4 s, so fast answers do not flash it. On a phone it
  sits under the top bar (the bar's middle holds the online switch or the shop's icons); from `sm` in the middle of the
  top bar.
- 46 places now show `LoadingState` (the former grey blocks and the map's box); 29 files keep skeletons that draw the
  shape of what is coming (product cards, rows, number tiles, a photo frame).
- The menu's tooltip is placed with `position: fixed` next to the icon: inside the scrolling column it would be cut off.
  It is decoration (`aria-hidden`); screen readers get the name from the link, where it stays as `sr-only` text.
- `PanelSidebar`'s `open` and `onClose` are optional now: the delivery panel has no drawer, its pages are in the bottom
  bar below `lg`, and the leaf mark in its top bar leads to today. The assistant's button takes `aboveBottomBar`.
- No new tile: the delivery tiles are `KpiTile` (which got a `className`). `StepBar` (`components/ui`) is new and
  shared by the delivery's steps and the joining steps; the customer's `OrderProgress` keeps its own seven-step look.
- The rating tile on Today reads the partner's profile, which the layout has loaded already (no extra request).
- Fix after review: the assistant's button (fixed in the bottom right corner) covered the page controls at the end of
  the panels' lists. Each panel's <main> now leaves room under its last row (pb-24; the delivery panel pb-40 below lg,
  where the button sits above the bottom bar), and the shop footer's bottom row keeps that corner free (lg:pr-56).

## 18. Modern drop-downs, date pickers and time pickers (E1–E4)

The request: every drop-down, date picker and time picker gets a modern look of its own instead of the browser's, the
same everywhere, easy to use with a mouse, a finger and the keyboard, and responsive; what the fields do must not
change. Frontend only, and no new library: like the dialogs, menus and charts, the pickers are small components of our
own.

**What the review found**

- Drop-downs, 28 native `<select>`s: `FilterSelect` (18 filters above the admin lists), `SelectField` (4 form fields:
  KYC zone, product category, unit, GST) and 6 written by hand (the shop search's category, the shop's sort, rows per
  page, gender, the vendor's two product filters). Closed they look different from each other (36, 40 or 48 px, other
  borders); open, the list is the browser's own.
- Date pickers: 6 `type="date"` (report period from and to, date of birth, a coupon's first and last day, the audit log's
  day) and 2 `datetime-local` (a banner's shown from and until): the browser's calendar, different in every browser.
- Time pickers: `type="time"` in the store's opening hours (7 days, opens and closes).
- No `<dialog>` element is used and every field sits in a page or a card, so a list or calendar can open in place,
  under its field (or above it).
- The menus that open on a click (account, notifications, categories) are our own already (U2, U3): not changed.

**E1 Drop-downs**

- `Select` (`components/ui`): a button with the choice and a chevron; the list is a card with rounded rows, the chosen
  row has a tick, the highlighted one is tinted green. It opens under its field, above it when there is no room below,
  and right-aligned near the right edge of the screen; the same in both themes.
- Keyboard as with a native drop-down (the WAI-ARIA "select-only combobox"): ↑ ↓ Home End PageUp PageDown, Enter or
  Space chooses, Escape closes, Tab chooses and moves on, typing letters jumps to the first match. Screen readers get
  `role="combobox"` with a `listbox` and `aria-activedescendant`.
- Two sizes: `md` for forms (48 px, like `TextField`) and `sm` for filters and toolbars (40 px); `variant="plain"`
  inside another box (the shop's search bar).
- `SelectField` (label, hint, error) and `FilterSelect` use it, and the 6 hand-written drop-downs become one of the two.
  `SelectField` takes `options` instead of `<option>` children, and `onChange` gives the chosen value.

**E2 Date picker**

- `DateField`: a field like `TextField` with a calendar icon and the date written out ("17 May 1994"). It opens a
  calendar card: month and year drop-downs (quick jumps, e.g. to a year of birth), previous and next month, the month's
  days (Sunday first, as in Indian calendars), today ringed, the chosen day filled, days outside `min`/`max` greyed out;
  "Today", and "Clear" for optional fields.
- Keyboard: the arrows move by a day or a week, PageUp/PageDown by a month, Home/End to the start or end of the week,
  Enter chooses, Escape closes; the focus goes back to the field.
- The value stays `YYYY-MM-DD`, as the forms and the API use it.
- Used for the report period, the date of birth, a coupon's days and the audit log's day (`sm`, "Any day").

**E3 Time pickers**

- `TimeField`: a `Select` with a clock icon and the times of the day every 15 minutes, written "7:00 am"; typing "7"
  jumps to 7:00 am. A saved time between the steps stays in the list.
- `DateTimeField`: a date and a time side by side (a banner's shown from and until). The time can be chosen once there
  is a date (it starts at 12:00 am); "Clear" empties both.
- The values stay `HH:MM` and `YYYY-MM-DDTHH:MM`.

**E4 Checks and docs:** component tests (mouse, keyboard, opening upwards, calendar moves, `min`/`max`, clearing); test
helpers `chooseOption`, `pickDate` and `pickTime` for the existing tests (they chose with `selectOptions` or typed);
the Playwright flows that chose options; screenshots on a phone and a laptop in both themes; the README and this
section.

| Phase | Builds | Done when |
|---|---|---|
| **E1 Drop-downs** | `Select`, `SelectField`, `FilterSelect`, the 6 hand-written drop-downs | no native `<select>` is left |
| **E2 Date picker** | `Calendar`, `DateField`, its 6 uses | no `type="date"` is left |
| **E3 Time pickers** | `TimeField`, `DateTimeField`, opening hours, banners | no `type="time"` or `datetime-local` is left |
| **E4 Checks and docs** | tests, helpers, screenshots, docs | unit tests, lint, build and the Playwright flows are green |

**Progress:** E1 ✅ · E2 ✅ · E3 ✅ · E4 ✅ done (2026-10-27). 385 unit/component tests (+10: the drop-down with the
mouse and the keyboard, opening upwards, the calendar's keys, `min`/`max`, Escape and Clear, the times, the day and time
pair, the calendar helpers); 18 existing tests and 6 Playwright flows choose with the new helpers. Type check, lint and
build green; screenshots on a laptop and a phone in both themes. 28 Playwright flows pass against the real backend (all
but flow 29, section 15); the account flow had typed a date of birth and now picks it from the calendar.

**Decisions made while building**

- The labels are `<span id>` + `aria-labelledby`, not `<label for>`: a click on a label clicks its button, which would
  open the list. A date field's name is its label and its date ("Date of birth (optional) 17 May 1994"); a drop-down's
  choice is the combobox's value.
- Filter and toolbar drop-downs are as wide as their longest option (`fitOptions`, like a native drop-down), so the row
  does not jump when the choice changes. The hidden labels are drawn by CSS (`content: attr(data-label)`): as text they
  would be in the page twice.
- Lists and calendars open in place (no portal): no `<dialog>` is used and no container cuts them off. Only the list
  scrolls to the highlighted option (centred when it opens), never the page.
- Phones get the same list and calendar instead of the system's wheels: the same look everywhere, with 40 px rows and
  36 px days to tap.
- A report period cannot reach into the future any more: the calendar stops at today (the test that typed 31 December
  checks that now).
- A banner's two day-and-time fields have a row of their own: half a column was too narrow for a day and a time.

## 19. A wider, full-height assistant chat (W1)

The request: the AI chat 20 % wider and as high as the screen. Frontend only; one component (`ChatPanel`) is shared by
the shop and the three panels, so all four change together.

**W1 Wider and full height**

- From `sm` up the chat is a panel docked at the right edge, from the top of the screen to the bottom, instead of a
  36rem card above the chat button. Width 28.8rem instead of 24rem (+20 %, 461 px). Phones keep the full-screen chat.
- While it is open it covers the chat button (and, in the delivery panel between `sm` and `lg`, the bottom bar, as on a
  phone); the close button and Escape close it and the focus goes back to the button, as before. `ChatPanel` no longer
  needs `aboveBottomBar` (the button still does).
- It stays non-modal: the page beside it can still be used.
- Checks: the assistant tests, type check, lint, build; screenshots of the shop, a panel and the delivery panel at laptop
  and tablet width, in both themes.

| Phase | Builds | Done when |
|---|---|---|
| **W1 Wider and full height** | `ChatPanel` classes, docs | the chat is 28.8rem wide and as high as the screen in the shop and every panel |

**Progress:** W1 ✅ done (2026-10-27). 385 unit/component tests (the 8 assistant tests unchanged), type check, lint and
build green; screenshots of the shop, the vendor and admin panels on a laptop, the shop at 640 px (dark), the delivery
panel at tablet width (the chat covers the bottom bar) and on a phone (unchanged).

**W2 Another 30 % wider:** the next request made the chat 30 % wider again: 28.8rem × 1.3 = 37.44rem (599 px). From
`sm` (640 px) it still fits beside the edge of the screen; phones keep the full-screen chat.

**Progress:** W2 ✅ done (2026-10-27). 385 unit/component tests, lint and build green; screenshots of the shop and the
admin panel on a laptop, the shop at 640 px (dark) and the delivery panel at tablet width.

## 20. Fraunces for the titles (F1)

The request: the Google font Fraunces, used where it suits the application. Frontend only.

**What the review found**

- Two fonts today: Quicksand (`font-heading`: all headings, and about 160 places of interface text: buttons, form labels,
  the menus, table headers, prices, numbers) and Lato (`font-body`: running text).
- Fraunces is a soft "old style" serif made for display sizes: it gives titles a warm, fresh-market feel, but on 14 px
  labels, buttons and prices it would be harder to read than the round sans.

**F1 Fraunces for the titles**

- A third font: `--font-display` ("Fraunces Variable"), class `font-display`. Page titles (`h1`) and section titles
  (`h2`) use it, in the shop and in every panel, at weight 600; `h3`–`h6` (card titles, product names) keep Quicksand.
- Quicksand stays the interface font (buttons, labels, menus, prices, numbers, the logo) and Lato the text font.
- The display banner that is an `h3` ("Bring nature into your home") gets `font-display`; small `h2`s that ask for
  `font-heading` (the chat's title, the audit log's entries) keep Quicksand.
- Self-hosted like the other two fonts: `@fontsource-variable/fraunces`, the `opsz.css` file (weight and optical size,
  67 KB for latin): no request to Google's servers, it works offline, and the optical size keeps the letters right from
  the 16 px titles to the 60 px banner.
- Checks: lint, build, unit tests; screenshots of the shop, the panels and the login screens on a laptop and a phone in
  both themes.

| Phase | Builds | Done when |
|---|---|---|
| **F1 Fraunces for the titles** | the font package, `--font-display`, the `h1`/`h2` rule, docs | every page and section title is in Fraunces, the interface text is unchanged |

**Progress:** F1 ✅ done (2026-10-27). Lint and build green (the font file is 67 KB); 385 unit/component tests pass (a
first run had 3 failures that did not come back in two reruns; the tests do not load fonts or CSS). Screenshots of the
home page, the shop, a product, the login screen, the admin dashboard (dark) and orders, the vendor dashboard on a
laptop, and the home page (dark) and the delivery panel on a phone.

## 21. Product page, product comparison and the wishlist (G1–G5)

The request: four screens after reference images (Downloads: `product-view-details.png`,
`product-compare-page-ui-screen.png`, `wishlist-popup.png`, `wishlist-page.png`): a product page, a comparison page, a
wishlist popup when a heart is clicked, and the full wishlist page. Same look in all four, responsive, accessible, the
existing components reused, nothing else changed. Frontend only (no backend changes).

**What the review found**

- The product page has a photo, the facts, quantity and Add to cart, the heart, the store, reviews and related products.
  The references add: photo thumbnails and a zoom, the badge above the name, size chips, Compare and Add to wishlist
  as buttons, a facts block, tabs (Description, Additional information, Reviews), a sidebar (an advert, categories with
  counts, top rated products) and "recently viewed" under the page.
- A product has one pack size and no variants, no manufacturing date, shelf life or tags (`Product` in `api/types.ts`).
  The page shows what exists: the pack size as one chip, brand, SKU, category, GST, the store and the stock. No made-up
  facts, and no advert with a made-up discount.
- There is no comparison yet. The wishlist is kept in this browser (`useWishlist`, localStorage; no wishlist API) and
  does not know when a product was added; the references show that date and the number in stock.
- No dialog in the app keeps the keyboard focus inside it; the new popups will.

**G1 Product page**

- Left: the photo card with the zoom button (only when there is a photo: a dialog with the big photo, arrows when there
  are more); thumbnails under it when there are two or more photos.
- Middle: the badge, the name, stars (they open the Reviews tab), the price and the saving, the description, "Pack size"
  with its chip, quantity and Add to cart, the Compare and Add to wishlist buttons, the facts (SKU, category, brand,
  stock) and the store card.
- Under them a tabs card: Description (the description and how the order reaches you), Additional information (a table
  of the facts), Reviews (the existing list). The tabs work with the arrow keys (WAI-ARIA tabs).
- Right, from `lg`: the shop's sidebar boxes (categories with counts, top rated products), reused from `ShopSidebar`.
- Below: related products and recently viewed products.

**G2 Product comparison**

- `useCompare`: like the wishlist, a list in this browser (`fresh-grocery.compare`), at most 4 products (a fifth is
  refused with a message).
- `CompareButton`: a round button under the heart on product cards, and a "Compare" button on the product page.
- `CompareBar`: while products are chosen, a bar at the bottom of every shop page with their pictures, "Compare (n)"
  and a button that empties the list (not on the comparison page itself).
- `/compare`: a table, one column per product (picture, name, Remove), rows for price, saving, rating, pack size, brand,
  category, store, stock, description and Add to cart. Today's data from the API; a product the shop no longer sells
  says so. On a phone the table scrolls sideways with the row names kept in view.
- The header gets a Compare link with a counter (from `sm`), like Wishlist and Cart.

**G3 Wishlist popup**

- A heart (or "Add to wishlist") that adds a product opens the popup: "Wishlist (n)" in a green bar with a close button;
  one row per product (remove, picture, name, prices, the day it was added, the number in stock, Add to cart); a dark
  bar with "Open wishlist page" and "Continue shopping". Removing with the heart still shows a short message.
- Escape, a click outside and both buttons close it; the focus stays inside while it is open and goes back to the heart
  afterwards. The "Start a new cart?" question of Add to cart works inside it.

**G4 Wishlist page**

- The products in a card with one row each, the same row as in the popup (shared `WishlistItem`): remove, picture,
  name, prices, the day added, the number in stock and Add to cart.
- "Wishlist link" with Copy: a link with the product numbers (`/wishlist?shared=1,5,9`). Whoever opens it sees "A shared
  wishlist" with those products and can save them to their own wishlist.
- Recently viewed products stay under it.

**G5 Checks and docs:** component tests (the gallery and zoom, the tabs, compare: add, the limit, remove, the table, the
bar; the popup: open, remove, close, focus; the page: the date, the stock, the shared link), the existing tests and the
Playwright flows that the new tabs and popup touch; screenshots on a laptop, a tablet and a phone in both themes; the
README and this section.

| Phase | Builds | Done when |
|---|---|---|
| **G1 Product page** | gallery and zoom, info column, tabs, sidebar, recently viewed | the page follows the reference with real data only |
| **G2 Product comparison** | `useCompare`, `CompareButton`, `CompareBar`, `/compare`, header link | up to 4 products side by side, from any product card or page |
| **G3 Wishlist popup** | popup store, `WishlistPopup`, `WishlistItem`, focus trap | a heart opens the popup; every button in it works |
| **G4 Wishlist page** | the page with `WishlistItem`, added date, share link | the page follows the reference; a shared link shows the products |
| **G5 Checks and docs** | tests, screenshots, docs | unit tests, lint, build and the Playwright flows are green |

**Decisions made while building**

- The badge (discount, "only a few left", "top rated") moved from the photo to above the name, as in the reference.
- No advert in the sidebar: the reference's "Save 17% on organic juice" would be a made-up offer. The sidebar shows the
  categories and the top rated products; related products sit in the main column next to it (4 instead of 5), so the
  tall category list leaves no empty space.
- Compare and the heart are toggles (`aria-pressed`): their names stay the same in both states, the colour and the filled
  icon show the state. Adding opens the pop-up (the heart) or shows a message with "Compare now"; a fifth product is
  refused with a message.
- The wishlist's rows lay themselves out by the width of their list (a CSS container query, `@container` / `@xl:`), so
  the same row fits the pop-up and the page.
- The pop-up closes on a click on its dark background and on Escape only for keys pressed inside it: the "Start a new
  cart?" dialog of Add to cart (also a portal) handles its own Escape and clicks.
- The Compare link in the header shows from `lg` (its label from `2xl`); below that the bar at the bottom leads to the
  comparison. On a phone the bar shows the count without the pictures and leaves the corner free for the assistant.
- Fix found on the way: the header's search box could not get narrower than its contents, so at 768–800 px the account
  icon was pushed off the screen (the page scrolled sideways). It now shrinks (`min-w-0`); checked from 640 to 1280 px.
- The popup's open state is a small store outside React (`wishlistPopupState.ts`), because any heart opens it and the
  layout shows it. It is not called `wishlistPopup.ts`: on Windows that name clashes with `WishlistPopup.tsx`.
- Fix after the Playwright run: the categories box counts the products of every category with one request each (about
  27). On the product page that made a fresh page send more than the gateway's burst of 40 requests, and the cart
  answered 429 (Too Many Requests). The product page's box now asks for no counts (`categoryCounts={false}`): it shows
  them only when the shop page has loaded them already (cached for 10 minutes). A test checks that no count is requested.

**Progress:** G1 ✅ · G2 ✅ · G3 ✅ · G4 ✅ · G5 ✅ done (2026-10-27). 403 unit/component tests (+18: the tabs, the stars, the
gallery and zoom, Compare and Add to wishlist on the page, no count requests; the comparison from the cards, the limit,
the table, removing, the bar; the pop-up: opening, focus, Tab, removing, its links, not on removal; the date added, the
share link, a shared wishlist); 3 existing tests and 2 Playwright flows follow the new tabs and pop-up. Type check, lint
and build green; screenshots on a laptop, a tablet and a phone in both themes.

## 22. A wider delivery panel (H1)

The request: the body of the delivery panel (`/delivery`) about 75 % wider. Frontend only.

- The delivery pages were made for a phone first: their content stops at 48rem (Today, a delivery, History, Earnings,
  Profile) and the joining steps' card at 42rem. Both become 75 % wider: 84rem and 73.5rem.
- The two widths are written once (`features/delivery/layout/pageWidth.ts`) instead of in every page.
- Phones and tablets do not change (the content is narrower than 48rem there anyway). On a 1440 px laptop the content
  fills the space next to the menu (about 1120 px instead of 768 px); the full 84rem shows from about 1700 px.
- Checks: the delivery tests, type check, lint, build; screenshots of every delivery page on a laptop and a wide screen,
  in both themes.

| Phase | Builds | Done when |
|---|---|---|
| **H1 Wider delivery panel** | `pageWidth.ts`, the six places that used the old widths | every delivery page is up to 75 % wider and its contents still fit |

**Progress:** H1 ✅ done (2026-10-27). The KYC form's three text fields stand side by side from `lg` (one field across
the wider card would be hard to read). Unit tests, type check, lint and build green; screenshots of Today (offline and
online), History, Earnings, Profile and the KYC step at 1440 px and 1920 px, light and dark; the delivery Playwright
flows pass.

## 23. A modern conversation in the AI chat (I1)

The request: a modern design for the chat's conversation (the part between the title bar and the question box), in the
shop and every panel. Frontend only; the title bar and the question box stay as they are.

**What the review found**

- The conversation is plain: a line of greeting with three small chips; grey and green bubbles without a sender; a
  spinner with "Looking it up…" as text; the error as a red box.
- The API gives each message only its sender and text (no time), so there are no times to show: times only on the
  new messages would disappear after a reload.

**I1 The conversation**

- Its own component, `ChatConversation`, so `ChatPanel` stays the frame (title bar, conversation, question box).
- A soft page-coloured background, so the messages stand out as cards.
- Welcome (no messages yet): the assistant's round avatar in brand green, "Hi, Priya!" as a heading, the greeting, and
  the example questions as full-width suggestion cards ("Try asking") with an arrow.
- The assistant's answers: its avatar on the left, a white card bubble; under it the "Checked against…" chip (when the
  backend checked the answer) and a Copy button. The user's questions: a green gradient bubble on the right.
- "Looking it up…": a typing bubble with three bouncing dots (still with the text, and still as `role="status"`); the
  dots stand still with reduced motion. The error: a red-tinted bubble from the assistant with "Try again".
- New messages fade in; long words and links wrap. Everything works in both themes.
- Unchanged: the log (`role="log"`, one list item per message, which the tests and Playwright count), plain text only
  (never HTML), the names of the buttons.
- Checks: the assistant tests (+ copy, the typing bubble), type check, lint, build; screenshots of the welcome, a
  conversation, waiting and an error, on a laptop and a phone, in both themes.

| Phase | Builds | Done when |
|---|---|---|
| **I1 The conversation** | `ChatConversation`, the bubbles, welcome, typing and error bubbles, copy | the conversation looks like the plan in the shop and the panels |

**Progress:** I1 ✅ done (2026-10-27). 9 assistant tests (+1: the typing bubble and Copy; the vendor test reads the
welcome heading and the greeting apart), type check, lint and build green; screenshots of the welcome, a conversation,
the typing bubble and an error on a laptop, a phone and in the vendor panel, light and dark; the guest Playwright flow
with the real model passes.

## 24. Finding the order tracking (J1)

The request: a customer (Priya) could not find where to follow an order on the map. Find the right place for it, and
make sure the map shows the delivery partner's current position and the delivery's status. Frontend only.

**What the review found**

- The live tracking page exists since T1 (section 12): `/orders/:orderId/track`, with the map (store, address, the
  partner's live position, the route), the headline ("Arjun is on the way to you"), the arrival time, the partner's
  phone and the order's progress. It updates itself through the live stream.
- The only way there: My orders → open an order → "Track on the map" inside the status box, and only while the order is
  ready for pickup or out for delivery. Nothing in the menus, the order list or the footer leads there.
- In the backend Priya has 50 orders (most from the Playwright runs); 170 is out for delivery and 169 ready for pickup.

**J1 Ways to the tracking**

- "Track my order" in the customer's account menu, in the phone menu and in the footer. It opens `/orders/track`: with
  one open order it goes straight to that order's tracking page, with several it lists them ("Which order do you want to
  follow?"), with none it says so and leads to My orders. Open = not delivered, failed, cancelled or rejected; read from
  the newest 100 orders, the list the spending chart already loads (no new request).
- My orders gets "On its way" at the top: the open orders with their status, each with "Track on the map" (ready for
  pickup, out for delivery) or "Follow the order" (still at the store: the tracking page shows the progress and starts
  the map when the store hands the order over).
- An order card that is on its way gets "Track on the map" next to "View details".
- The tracking page itself does not change. Check with the real backend: the Playwright tracking flow (a partner on a
  real route, his phone's positions) and screenshots of the map.
- Checks: tests for the menu item, `/orders/track` (none, one, several), "On its way" and the card's button; type check,
  lint, build; screenshots.

| Phase | Builds | Done when |
|---|---|---|
| **J1 Ways to the tracking** | menu item, `/orders/track`, "On its way" on My orders, the card button | a customer gets to the map from the menu in one click (two with several orders) |

**Progress:** J1 ✅ done (2026-10-27). Tests +4 (the menu item; `/orders/track` with none, one and several open orders,
"On its way" and the card's button); type check, lint, build green. Screenshots with the real backend as Priya: the menu,
My orders with order 169, and `/orders/track` opening 169's live map.

**What went wrong on the way:** the Playwright tracking flow was run to check the map and first "freed" delivery1, as
every partner flow does. Priya's orders 169 and 170 were the user's own manual test, not Playwright's: 170 (out for
delivery with Arjun) became "delivery failed", and 169's delivery went back to waiting for a partner. The flow itself
then failed (Arjun was offered 169 instead of its new order). The README's Playwright section now warns about it. That
the map shows the partner's live position is covered by the tracking component test and by the same flow's earlier
pass today (full run, section 21).

## 25. Voice in the AI chat (K1, removed)

Built on 2026-10-27 with the browser's own speech recognition and speech synthesis (a microphone button, answers read
aloud, "Listen"), then removed the same day at the user's request: the user had already decided with the other session
on a local speech server, so that no audio leaves our machines (docs/ai-assistant-plan.md, section 16, V1 done). The
browser's speech recognition sends the recording to Google or Microsoft. The voice in the chat panel is V2 there
(built as N1 and N2, section 27).

## 26. Search suggestions in the header (L1, L2)

The request: when a customer types into the search box at the top, show suggestions as they type, like Google:
products that match the text, also partial words ("toma" → Tomato) and close keywords, found and ranked by
Elasticsearch, in a clean dropdown under the box, with a debounce so not every key press makes a request. Simple code;
the existing search, filters and everything else keep working as they are.

**What the review found**

- The search box (`components/layout/SearchBar.tsx`) only submits: Enter or "Search" opens `/shop?q=…`. Nothing appears
  while typing.
- product-read-service answers every catalog read from Elasticsearch (index `products`). Its search
  (`ProductQueryServiceImpl.buildCriteria`) is a `match` on name (×3), brand (×2), category (×2) and description: whole
  words only. "toma" finds nothing, "tomatto" (a typo) finds nothing. The index has no autocomplete field (no
  `search_as_you_type`, no edge n-grams).
- So partial words need the backend: a frontend calling today's search cannot do it. The gateway already sends every
  GET `/api/v1/products/**` to product-read-service, so a new path under it needs no gateway change.
- The running product-read-service is the other session's jar: deploying means restarting that one service (the user
  agreed).

**L1 Backend: `GET /api/v1/products/suggestions?q=toma&categoryId=1&size=6`** (product-read-service, public, read only)

- One Elasticsearch query, without a new mapping or reindexing (works on the existing text fields):
  - only ACTIVE products (like the public search), optionally in one category;
  - should (at least one): `match_phrase_prefix` on the name ("toma", "cherry tom": the name contains the typed words,
    the last one as a prefix; boost 5); `multi_match` of type `bool_prefix` on name ×3, brand ×2, category ×2 (the
    words in any order and field, the last as a prefix: "butter amu" → Amul Butter); a fuzzy `multi_match` on name ×3
    and brand ×2 (`fuzziness AUTO`, first letter fixed: "tomatto" → Tomato, and "amu but" through "amu" ≈ "amul";
    boost 0.3, so a typo match never comes before a real one);
  - ranked by score, ties by name; `size` 1–10 (default 6); `q` 1–100 characters (a blank `q` → 400).
- Answers `ApiResponse<List<ProductResponse>>` (the same product as everywhere, so the frontend reuses its type).
- The existing search does not change (same query, same results).
- Tests: the controller (parameters, validation) and the search against a real Elasticsearch (Testcontainers):
  prefix, two words in any order, brand prefix, a typo, the category filter, no inactive products.

**L2 Frontend: the dropdown under the search box**

- From 2 characters on, 250 ms after the last key press (`useDebouncedValue`), one request; answers are cached per
  text and category for a minute, an older request that is still running is cancelled (TanStack Query's signal), and
  the last list stays visible while the next one loads (no flicker).
- The dropdown under the box: up to 6 products (photo, name with the typed part in bold, category and unit, price, "Out
  of stock" when so), and last "Search for “toma”" (the full search, as Enter does). The chosen category in the search
  bar narrows the suggestions too. No match: "No products match “…”" and still the full search.
- Keyboard and screen readers: the ARIA combobox pattern (the box is a `combobox`, the list a `listbox`): ↓/↑ move
  through the list, Enter opens the chosen product (or searches when nothing is chosen), Escape closes the list, Tab
  or a click outside closes it. A hidden status says how many suggestions there are.
- A failed request shows no error in the dropdown: the full search still works.
- Mock: `GET /api/v1/products/suggestions` in `mocks/handlers/catalog.ts` (prefix words, simple ranking; the real
  ranking is Elasticsearch's).
- Tests: suggestions after typing, one request for a fast typed word (debounce), keyboard, product page, the full
  search row, the category, Escape. The existing search tests stay as they are.
- Checks: type check, lint, build, unit tests; the backend tests; with the real backend after the restart: the endpoint
  and screenshots of the dropdown (laptop, phone, dark).

| Phase | Builds | Done when |
|---|---|---|
| **L1 Suggestions endpoint** | product-read-service `/api/v1/products/suggestions`, Elasticsearch prefix + fuzzy query, tests | "toma", "amu but" and "tomatto" find the right products from Elasticsearch |
| **L2 Search dropdown** | debounce, suggestion hook, combobox dropdown, mock, tests | the dropdown suggests products while typing, by mouse and keyboard, in the shop and on a phone |

**Progress (2026-10-27):**

- L1 ✅ `PublicProductController` `/suggestions`, `ProductQueryServiceImpl.suggestProducts` (a `NativeQuery` with the
  query above), README. The controller test passes (8, +3: parameters, default size, a blank text and a size above 10
  refused); `CatalogIndexAndSearchIntegrationTest` passes on a real Elasticsearch (23, +6). Found with the real catalog:
  for "cherry tom" a typo match ("cherry" ≈ "Curry") came first, before Tomato; the typo clause now weighs 0.3, with a
  test that failed before ("potatoe tom": Potato last). product-read-service runs the new jar; through the gateway
  "toma", "amu but", "butter amu", "tomatto", "potatoe", "masal", the category filter and a blank or too large request
  answer as expected; the existing search answers as before ("tomato": 3 found, "toma": none). Its Kafka listener has
  every partition, with no lag.
- L2 ✅ in the mock: `useDebouncedValue`, `useProductSuggestions`, `SearchSuggestions`, the combobox in `SearchBar`, the
  mock endpoint; 5 new tests (suggestions and highlight, one request per pause and none for one letter, the keyboard,
  the category, a failed request). Found on the way: Escape kept the chosen option (fixed: closing clears it), and a
  `role="status"` for the count was taken for the Razorpay page's status message (now a plain `aria-live` region).
  413 tests, type check, lint, build green (on the overloaded machine each full run had 3 different timing failures
  that pass alone). With the real backend on 5173: one request per typed text, the products of Elasticsearch in the
  dropdown, laptop, phone and dark screenshots, no sideways scroll.
- Open: pressing Enter on "toma" still runs the existing whole-word search, which finds nothing, although the dropdown
  suggested Tomato. Matching partial words in the full search too would change its ranking; to be decided.

## 27. Voice in the AI chat, with the local speech server (N1, N2)

**Why there was no voice (2026-10-27).** The voice backend is done (docs/ai-assistant-plan.md, section 16, phase V1:
`POST /api/v1/chat/transcriptions` and `/speech`, speaches with Whisper and Kokoro). The chat panel's part (V2, V3
there) was never built, and the browser-voice version K1 (section 25) was removed on purpose. So the chat had no
microphone. N1 and N2 are V2 and V3 of the assistant plan, built as decided there: local speech server, English only,
the question is sent as soon as the recording stops, and the answer to a spoken question is read aloud by itself.

**N1 Frontend: the microphone and the answers read aloud** (mock first)

- `api/chat.ts`: `transcribeQuestion(audio)` (multipart part `audio` → `{ text }`) and `speakText(text)` (→ mp3), with
  `apiAudio()` in `api/client.ts` (POST JSON, `Accept: audio/mpeg, application/json`; errors stay JSON → `ApiError`).
- `useVoiceRecorder`: the browser's `MediaRecorder` (webm/opus, ogg/opus or mp4, whatever the browser records; the
  backend takes them all), at most 30 s (it stops by itself), then the recording goes to `/transcriptions`.
  Messages: microphone not allowed, no microphone, nothing heard (the backend's 400), voice not available (503).
  A browser without `MediaRecorder` gets no microphone button; typing works as before.
- `useReadAloud`: reads an answer in parts, as measured in V1: the first sentence alone (about 2 s), played at once,
  while the rest is fetched. Stop at any time; reading stops when the chat closes, a new chat starts or a new recording
  starts. Only one answer is read at a time.
- In the chat panel (shop and the three panels share `ChatPanel`): a microphone button next to Send ("Ask by voice"),
  which turns into "Stop recording" with the time (0:07 / 0:30); then "Writing down your question…". What was
  understood is sent as the question and shown as the user's message (so a misheard word can be seen and retyped).
  Every answer gets "Listen" / "Stop" next to Copy.
- Mock: `/transcriptions` "hears" "Which offers are running?" (an example question, so the mock assistant answers it);
  `/speech` returns a short silent WAV.
- Tests (fake `MediaRecorder`, microphone and audio): a spoken question is sent and its answer read aloud (first
  sentence, then the rest); "Listen" and "Stop" on a typed answer; microphone refused; nothing heard (no question
  sent); voice not available; the 30 s limit; the microphone in a panel.

**N2 Real backend**: the round trip against speaches through the gateway, and a Playwright flow with Edge's fake
microphone (`--use-file-for-fake-audio-capture`, a WAV of a spoken question made once by the local speech server).

| Phase | Builds | Done when |
|---|---|---|
| **N1 Voice in the chat** | recorder, read aloud, microphone and Listen buttons, mock, tests | a spoken question is answered and the answer is heard, in the mock, in the shop and the panels |
| **N2 Voice, real backend** | real round trip, Playwright with a fake microphone | the same with the real speech server and model |

**Progress (2026-10-27):**

- N1 ✅ `apiAudio`, `transcribeQuestion`, `speakText`, `useVoiceRecorder`, `useReadAloud`, the microphone in `ChatPanel`,
  "Listen" / "Stop" in `ChatConversation`, `mockVoice.ts`; 9 new tests (`voice.test.tsx`). Found on the way: in the
  tests the fake browser (jsdom) cannot send a recording's bytes, and parsing its multipart body throws, so the mock
  only checks that the `audio` part is there (an empty recording is already caught in the browser). 422 tests, type
  check, lint and build green. In the browser (mock, Edge's fake microphone): the clock counts, the question is sent
  when the recording stops, the answer is played and "Listen" comes back.
- N2 ✅ With the real backend on 5173, logged in as customer1: Priya's question "How many products are in my cart?" as
  a WAV through the fake microphone → written down word for word → "Your cart has 1 product." (her cart: Harpic x2;
  "Checked against the shop's live data") → read aloud (mp3, 1.7 s). Transcription took 17 s while other Maven tests
  shared the CPU (V1 measured about 2 s). New smoke flow 30 (`e2e/voice.spec.ts`, fixture `e2e/fixtures/question-offers.wav`)
  passes in 2.1 minutes.
- The same day's "please log in" answer to a logged-in customer was not in the code: mcp-client-service ran a new
  build (token header `X-User-Token`), mcp-server-service still a build from before A6 (`X-Customer-Token`, no panel
  tools). The other session restarted mcp-server-service from the current build; the troubleshooting table in
  mcp-server-service's README now shows how to recognise it.

## 28. Cards that stand out from the page (Q1)

**The request (2026-10-28):** every container (card, panel, section box) should stand out from the page background,
with a visible border colour and a shadow.

**Why they do not today.** The page is `#f4f7f5`, a card `#ffffff` and its border (`--color-line`) `#ececec`: almost
the same light colours, so a white card on the page shows only a faint outline. About 115 cards are written as
`rounded-2xl border border-line bg-card`; only about 25 of them have a shadow.

**Q1: one change in `styles/index.css`, both themes, no component edits**

- A clearer line colour: `--color-line` light `#ececec` → a soft green-grey that is visible on white and on the page;
  dark `#2a3940` → a lighter line on the dark card. Every card, table and divider line uses this token, so all of them
  get clearer at once.
- A card shadow token `--shadow-card` (Tailwind makes `shadow-card` of it): a small, soft shadow; a deeper one in the
  dark theme, where a light shadow is invisible.
- One base rule: every card (an element with `bg-card` and `border-line`) gets `--shadow-card`, unless it sets its own
  shadow (menus, dialogs and hover effects keep theirs: the rule has the lowest priority, `:where()` in `@layer base`).
  Form fields and buttons are left out (they also use `bg-card` and `border-line`).
- Checked: before/after screenshots of the shop, a product, the cart, the account, an order and the vendor, delivery
  and admin panels, light and dark, laptop and phone; unit tests, lint and build.

| Phase | Builds | Done when |
|---|---|---|
| **Q1 Cards stand out** | line colour, card shadow, the base rule | cards are clearly separate from the page on every screen, in both themes |

**Progress (2026-10-28):** Q1 ✅ `--color-line` light `#ececec` → `#cdd8d2`, dark `#2a3940` → `#34474f`; `--shadow-card`
light `0 1px 3px` + `0 6px 20px -4px` in the heading colour at 8 % / 16 %, dark in black at 45 % / 55 %; the "Cards" rule
`:where(.bg-card.border.border-line):not(input, textarea, select, button)`. A first, lighter try (`#d6dfda`, 6 % / 10 %)
was still hard to see on a laptop screen. Before/after screenshots on 5173 (real backend): home, shop, product, cart,
account, orders, vendor, delivery and admin dashboards, admin and account in dark, cart and vendor on a phone. The
coloured tiles (categories, promo banners) keep their look: they stand out by their colour. 422 tests, lint and build green.

## 29. A highlighted main menu (Q2)

**The request (2026-10-28):** the main menu under the header (Home, Shop, Stores, Hot Deals, Offers, Wishlist, Cart)
looks like plain text; it should stand out.

**Today:** `NavBar` shows the links as bold text 28 px apart; the current page only turns green; only Hot Deals has an
icon. The phone menu (`MobileMenu`) lists the same links as text.

**Q2: pills with icons, the current page filled green**

- `navLinks.ts`: every link gets its icon (Home, Shop, Stores, Hot Deals, Offers, Wishlist, Cart), used by both menus.
- `NavBar`: the links sit in one rounded track (light grey); each link is a pill with its green icon; hover: a white
  pill with a soft shadow; the current page: a filled green pill with white text (like "Browse All Categories" next to
  it), `aria-current="page"` as before. Hot Deals keeps its flame, in the "hot" pink. The links to home page sections
  (Hot Deals, Offers) are never "current", as before.
- It must fit from 1024 px (where the bar appears): at smaller laptop widths the pills get less padding and the
  support phone shows only from 1280 px if needed (checked with screenshots at 1024, 1280 and 1440 px).
- The phone menu shows the same icons.
- The link names do not change (the icons are hidden from screen readers), so the tests that find links by name keep
  working. Checked: screenshots light and dark, hover and current page; unit tests, lint, build.

| Phase | Builds | Done when |
|---|---|---|
| **Q2 Main menu** | icons per link, pill track, current and hover states, phone menu icons | the menu stands out and shows the current page, from 1024 px on, in both themes |

**Progress (2026-10-28):** Q2 ✅ `navLinks.ts` (an icon per link), `NavBar` (the track, `pillClass`, `PillContent`),
`MobileMenu` (an icon on every row, also My account, My orders, Track my order, the panel link, Log in / Log out and
Become Vendor, so the names line up). Found with the screenshots: at 1024 px the menu squeezed "Browse All Categories"
onto two lines (`CategoryMenu` now does not shrink), and at 1280 px the support phone broke onto two lines. So the pills
are a little smaller below 1536 px, and the support phone shows from 1280 px (below that it stays in the footer and the
phone menu; it does not shrink or wrap). Measured at 1024, 1280, 1440 and 1600 px: nothing overflows or wraps; hover,
current page and dark checked. 422 tests, lint and build green.

## 30. A compact cart, and one look for product pictures (R1–R3)

**The request (2026-10-28):** a modern, compact, responsive cart page (`/cart`): less empty space, a clear cart table,
product pictures on the theme's background, a tidy summary with the checkout button; the same fixes wherever other
pages have them; nothing in the cart's behaviour changes.

**What causes it today** (screenshots in mock mode, 1440 px: the page is 3,018 px tall for 3 products):

- The cart is the only page with the big green `PageBanner` (230 px high, with six category chips); every other page
  uses the slim `BreadcrumbBar` and a plain title.
- The shop's sidebar (search and 21 categories, 320 px wide) sits next to the cart, although the header has the same
  search and category menu. It makes the page long and the cart narrow.
- The rows lie on the page background, not in a card; the remove button is at the start of the row.
- The coupon box is between the rows and the totals, so the totals and "Proceed to checkout" come after a large empty
  area, at the bottom right.
- **Pictures:** `ProductImage` draws a product without a photo on a pastel tile whose colour depends on the category
  (pink, peach, green, yellow ...). Next to each other (cart rows, product cards) they look like random colour blocks.
  The order and checkout lists pass `categoryId: 0`, so their colour is arbitrary. The wishlist's zebra rows
  (`bg-surface/60`) add another background behind them.

**R1 The cart page** (same data, same buttons, same API calls; only the layout and look change)

- Header like every page: `BreadcrumbBar` (Home › Shop › Cart) and the title. `PageBanner` (only the cart used it) and
  the cart's category chips are removed; the sidebar is no longer on the cart.
- Two columns like the checkout (`lg:grid-cols-[1fr_24rem]`, the summary sticky): on the left one card with the cart
  table; on the right the coupon and the totals. Below 1024 px the summary follows the table.
- The table card: a top line with "There are 3 products in your cart from Fresh Mart." and "Empty cart"; column titles
  on a grey header row (`bg-surface`) from 768 px; each row: picture (64 px), name with unit and the stock warning,
  price, quantity, subtotal, and a remove button at the end. On a phone a row is picture, name and price with the remove
  button, and below them the quantity and the subtotal. "Continue shopping" under the card.
- The coupon in its own small card: the code field and "Apply" on one line, the coupon in use, and the suggestions as
  one compact row each (code, what it gives, the condition) to click.
- `CartTotals` (also on the checkout): tighter rows, and the checkout and WhatsApp buttons inside the same card
  (`children`), so the total and the button are always together.
- Loading: skeletons in the same two columns; empty cart: the empty message in a card.

**R2 Product pictures, everywhere:** `ProductImage` puts every product (photo or emoji) on the same theme background,
`bg-surface` (the theme's grey for boxes inside cards; dark in the dark theme), with rounded corners. That changes the
product cards, the product page, the cart, the checkout, the orders, the wishlist, the compare page, the search
suggestions and the vendor and admin product lists at once. The category tiles and promo banners keep their colours
(they are categories and adverts, not product pictures).

**R3 The same fixes on other pages:** the wishlist list loses its zebra rows (plain lines, like the cart table); the
checkout's "Your order" and the order page's items get the same row spacing as the cart (they already have the card
and summary layout the cart now copies). Other pages are checked with screenshots for the same problems.

- Tests: the cart tests keep their checks (rows, totals, coupons, empty cart, WhatsApp, checkout link); labels that
  tests and screen readers use stay the same. Checked with screenshots at 390, 820, 1024 and 1440 px, light and dark,
  in mock mode, then against the real backend.

| Phase | Builds | Done when |
|---|---|---|
| **R1 Cart page** | header, two columns, table card, coupon card, totals with buttons, loading and empty states | the cart is compact on a phone, a tablet and a laptop; every cart test passes |
| **R2 Product pictures** | one theme background in `ProductImage` | no coloured blocks behind product pictures, in both themes |
| **R3 Other pages** | wishlist rows, checkout and order item rows, a screenshot check of the others | the same look wherever products are listed |

**Progress (2026-10-28):**

- R1 ✅ `CartPage` (BreadcrumbBar and title, two columns, the table card with `@container`, Continue shopping, the coupon
  under the table, the summary sticky on the right), `CartItemRow` (one grid, `CART_COLUMNS` shared with the column
  titles, the remove button at the end), `CouponSection` (its own card, suggestions as compact rows, 1–3 per line),
  `CartTotals` (tighter, `children` for the buttons). `PageBanner` deleted (the cart was its only user). The page is
  2,058 px tall instead of 3,018 (mock, 3 products, 1440 px), and the total and "Proceed to checkout" are visible
  without scrolling from 1280 px. Found on the way: (1) with the coupon in the right column, that column was taller than
  the table and the floating assistant button could cover the checkout button, so the coupon moved under the table;
  (2) only the real backend showed it (Priya's cart, 1024 px): next to the summary the card is 568 px wide, the
  table's fixed columns left 28 px for the name ("Am But…"). The rows now switch to the table by the card's width
  (`@3xl`, 768 px), not the screen's: two lines at 390 and 1024 px, a table at 820, 1280 and 1440 px, names 208–442 px
  wide, no sideways scroll. A click-through in the browser (mock, 1440 and 390 px): quantity, coupon on and off,
  remove, checkout; the totals follow (₹231 → ₹279 → ₹254.10 → ₹279 → ₹183), no page errors.
- R2 ✅ `ProductImage`: one `bg-surface` background with rounded corners for every product; the compare page's own
  copy of that background removed; the product page's picture frame thinner (`p-3 sm:p-4`). Checked on the home page,
  the product page, the cart, the checkout, the wishlist and the order page, light and dark.
- R3 ✅ The wishlist without zebra rows; the order page's items with the cart's row spacing and dark names (green on
  hover); the checkout's "Your order" with the same padding as its totals card. The checkout and order pages already
  had the card and summary layout; their only problem was the coloured picture blocks (R2).
- The green "Stay home & get your daily needs" banner and the five "Why shop with us" cards above the footer
  (`MainLayout`) are left out on the cart and the checkout (`CHECKOUT_PAGES`), decided with the user: nothing leads
  away from paying there. With Priya's real cart (2 products, 1440 px) the cart page is 1,339 px tall (3,018 before
  R1) and the checkout 1,475 px (2,184). A new cart test checks both pages without them and the home page with them.
- 423 tests (the cart's earlier tests unchanged), type check, lint and build green.

## 31. Search and pages in the admin's category table (X1)

**The request (2026-10-28):** `/admin/categories` needs search and pagination in its table.

**Today:** the page shows every category (21) in one table, without search or pages. The API
(`GET /api/v1/admin/categories`, product-read-service) returns all categories at once, in display order, without
paging; that suits the data: a shop has a few dozen categories, not thousands.

**X1: search and pages in the browser, with the parts every other admin list uses**

- No backend change: the full list is already loaded (and cached); the page searches and pages it in the browser.
- `useUrlFilters(10)`: the search (`?q=`), the page (`?page=`) and the rows per page (`?size=`, 10 / 20 / 50) are in the
  URL, so refresh, links and "Back" show the same list; a new search goes back to page 1.
- `SearchBox` above the table (Enter or the button, as on the products, stores and accounts lists): the name, the slug
  and the description, without regard to capitals.
- `TablePagination` above and below the table ("Showing 1–10 of 21 categories", rows per page, the page buttons).
- No match: "No categories match “…”" with "Clear the search".
- `pageOfList()` in `utils/pagination.ts`: one page of a list the browser has in full, shaped like the API's pages
  (`totalElements`, `totalPages`), so `TablePagination` works unchanged; a page after the last one shows the last page.
  Unit tests for it.
- Adding, changing, hiding and showing a category work as before. The category test is extended: 10 rows and the
  totals, page 2, a search, no match, and a new category found by the search.

| Phase | Builds | Done when |
|---|---|---|
| **X1 Category table** | search, page controls, empty search result, `pageOfList` | the admin finds a category by a part of its name and pages through the list; the URL keeps the state |

**Progress (2026-10-28):** X1 ✅ `AdminCategoriesPage` (`useUrlFilters(10)`, `SearchBox`, `TablePagination` above and
below, "No categories match" with "Clear the search"), `pageOfList()` with 3 unit tests. The category tests: a new one
(10 rows, "Showing 1–10 of 21 categories", page 3, a search by the slug back on page 1, no match, cleared), and the
add/hide test now opens with `?size=50` and finds the new category and Frozen Foods by searching. On the real backend
(22 categories), laptop and phone: 10 rows, page 2 (`?page=2`), "dal" → Rice, Atta & Dals (`?q=dal`), no match and
cleared; no page errors. 427 tests, type check, lint and build green.

## 32. Login sessions: access token in memory, refresh cookie (Z1)

**The request (2026-10-28):** JWT login with refresh tokens, so users stay logged in without typing the password again
when the access token expires (backend: Phase 21 of the root README, [docs/authentication.md](authentication.md)).

**Today:** the token (60 minutes) is in `localStorage`; any 401 logs the user out, and a timer logs out after 60 minutes.
The backend now issues 15-minute tokens and a rotating refresh token in an HttpOnly cookie, so without this phase the
app logs out after 15 minutes.

**Z1:**

- `api/session.ts`: the session lives in memory (same `loadSession` / `saveSession` / `clearSession`), plus `renewAt`,
  `subscribeSession` (for `useSyncExternalStore`) and a generation number.
- `api/client.ts`:
  - renews a token that is due before sending a call;
  - after a 401, one refresh shared by all calls, then the call is sent once more;
  - only a 401 from `/refresh` logs out;
  - no token for the public auth calls;
  - a late refresh after a logout is thrown away;
  - another user in the refresh logs out.
- `AuthProvider`:
  - `restoring` with `/refresh` after a reload (the four guards show `RestoringLogin`);
  - a renewal timer;
  - `logOut()` tells the server (`keepalive`, finished after a reload if needed) and the other tabs (`BroadcastChannel`);
  - a login on the wrong panel ends its server session.
- Mock API: `/refresh` and `/logout`; tokens expire after 15 minutes; the "cookie" is in `localStorage` (mock only).
  `logInAs()` in the tests sets both. `src/test/setup.ts` clears the session and the open toasts after each test.

| Phase | Builds | Done when |
|---|---|---|
| **Z1 Login sessions** | in-memory session, renew/retry in the client, restore after reload, server logout, tabs | a reload keeps the login, an expired token is renewed without the user noticing, logout ends the session on the server and in every tab |

**Progress (2026-10-28):** Z1 ✅
- 446 tests (+19: `client.test.ts` +13 and `auth.test.tsx` +6, after replacing the old expired-token tests). Type
  check, lint and build green.
- Later the same day, the mock follows auth-service's new reset rule: only the newest email's code works, and a
  password change or reset ends the open codes. 447 tests (+1 in `passwordReset.test.tsx`).
- Found by `session.spec.ts` (failed once in a full run, then 6 of 6 times in a small browser script): logging out on a
  page that needs a login (`/orders`) could end on `/login?next=/orders` instead of the home page. The guard saw the
  guest before the router's move to `/` was on screen, because React Router renders it in a transition and the logout
  is a synchronous update. This was already possible before Z1.
  - Fix: the shop's three logout buttons (`AccountMenu`, `MobileMenu`, `AccountLayout`) now
    `await navigate('/', { flushSync: true })` before `logOut()`.
  - `App.tsx` takes `RouterProvider` from `react-router/dom`, the version that can flush.
  - In Vitest it stays the plain one: there `react-router/dom` loads a second copy of the router.
  - After the fix: 6 of 6 script runs, and 10 of 10 test repeats, end on `/`.
  - 448 tests (+1: logging out on My account lands on `/`).
- The vendor's "Log in again" after the store's approval became automatic. The panel asks for a new token
  (`refreshSession()`); it is made from the current account, which has the store id. Then the panel opens.
  - "Try again" and "Log in again" stay, for the few seconds before auth-service has linked the store, or when the
    server cannot be reached.
  - The texts changed: the pending card, the admin's approval panel, and the store-approved notification in the
    mock and in notification-service.
  - Tests: 3 instead of 1 in `vendor.test.tsx`. 456 tests green.
  - Checked in a real browser: the vendor waited on the panel, an admin approved the store, and the open tab showed
    the panel after one refresh, without "Log in again". With all workers on this busy machine, a different slow test
    failed in each of two runs (each passes alone); with `--maxWorkers=4`, all 448 pass.
- `e2e/session.spec.ts`: 4 flows pass against the real backend. The whole suite: 34 flows pass.
  - In the first full run two failed for other reasons, and both pass when run again.
  - The assistant flow: a README edit during the run made Vite reload the page.
  - The admin's "assign": Arjun was still busy with an earlier delivery.

## 33. A shop page that shows its highlights first (Y1–Y3)

**The request (2026-10-28):** redesign `/shop`. "New arrivals" and "Top rated products" are at the bottom of the page,
so customers have to scroll all the way down to find them. Also:
- arrange the categories, search, filters and products better;
- less empty space;
- show the products the same clear way;
- keep the theme, work on every screen size, reuse components;
- break nothing.

**Today:**
- Products are on the left. A 20rem column on the right holds:
  - the search;
  - 22 tall category rows (about 1,200 px);
  - the stores and the price filter;
  - New arrivals and Top rated (about 2,400 px down on a laptop);
  - the brands.
- On phones the whole column comes after the products. "Filters" jumps down to it, and New arrivals is about 5,000 px down.
- The page is 5,200 px tall on a laptop and 8,900 px on a phone.

**Y1: filters on the left, a shorter filter column**
- The filter column moves to the left (16rem, 17rem from `xl`), where shops usually put it; the products get the rest.
  The title is above both; the count, the sort and the filter chips stay right above the products.
- Phones and tablets: a "Filters" button (`aria-expanded`) opens the filter column above the products, and
  "Show N products" under it closes it again. Nothing jumps to the bottom of the page any more.
- Categories: compact rows; the first 8 and "Show all 22 categories" (the chosen category is always in the list).
  The product page's sidebar gets the same list.
- Smaller boxes: less padding, smaller titles, smaller brand chips and price boxes.

**Y2: the highlights at the top**
- `components/product/ProductRail.tsx` is new and reusable. It has a title, a "See all" link, ← → buttons and a row of
  small products that scrolls sideways. Each product is a `ProductListItem` that now has an optional Add button.
- "New arrivals" (`NEWEST`) and "Top rated products" (`RATING`) sit at the top of the product column. They show 10
  products each, only ones in stock, because a highlight should be something you can buy. They are side by side from
  `xl` and one above the other below that.
- They show on the shop's first page with no filter and no search. That is the page for looking around.
  - Once the list is narrowed, the list comes first. The sort menu has "Newest first" and "Top rated" for any list.
  - The categories have 3–10 products each, so a category's highlights would only repeat its list.
- "See all" sorts the whole shop the same way (`/shop?sort=NEWEST`, `/shop?sort=RATING`).
- They leave the shop's filter column. The product page keeps "Top rated" in its sidebar. There are still two requests,
  as before.

**Y3: product cards that line up (everywhere they are used)**
- A product name always takes two lines, so the pack size, the stars, the store and the price line up across a row.
- Less padding around the picture, and in the card on phones; a little less space between cards.

| Phase | Builds | Done when |
|---|---|---|
| **Y1 Layout** | filters on the left, Filters button on phones, short category list, smaller boxes | the filters are next to the products on a laptop and one tap away on a phone |
| **Y2 Highlights** | `ProductRail`, New arrivals and Top rated at the top, "See all" | both are visible without scrolling on a laptop and right after the title on a phone |
| **Y3 Cards** | names on two lines, less padding | the cards in a row line up; search, filters, sort, pages, cart, wishlist and compare work as before |

**Progress (2026-10-28):** Y1–Y3 ✅
- **Code:**
  - `ShopPage`: a two-column grid (16rem / 17rem filters, then the products), the "Filters" button (`aria-expanded`) and
    "Show N products".
  - `ShopHighlights` and `components/product/ProductRail` are new; `ProductListItem` gets `addButton` and `className`.
  - `CategoryWidget`: the first 8, "Show all" / "Show fewer", and the chosen category is always listed.
  - Smaller sidebar boxes (`SidebarWidget`, `SearchWidget`, `FilterWidget`, `BrandWidget`); "Open now" no longer wraps.
  - `ProductCard`: a two-line name and less padding; `ProductGrid`: `sm:gap-4`.
- **Found while checking:** hidden screen reader texts inside the sideways row widened the page (3,700 px on a 1,440 px
  screen). Fixed with `relative` on the row, and added to the README conventions.
- **Tests:**
  - 4 new shop tests: the highlights and their "See all" links, gone for a category and on page 2; 8 categories and
    "Show all 21"; a chosen 21st category stays in the short list; the Filters button.
  - The shop tests wait up to 10 s for a new list, as many other tests do, because the busy full run sometimes took
    longer than 3 s.
  - 452 tests pass (with `--maxWorkers=4`; with the default workers one random slow test fails, another one each run,
    and each passes alone). Type check, lint and build are green.
- **Real backend:**
  - Page height: 5,200 → 3,200 px on a laptop, 8,900 → 5,500 px on a phone.
  - On a laptop both highlights are on the first screen. Each shows 10 products, none sold out.
  - The ← → arrows scroll; "See all" → `?sort=RATING` ("Top rated" in the sort box); a guest's Add goes to the login.
  - On a phone the filters are closed at first and open above the products; Fruits, then "Show 8 products", closes
    them, and the highlights are gone for the category.
  - No sideways scroll and no page errors at 390, 820, 1024 and 1440 px, light and dark; the product page's
    sidebar uses the short category list.
  - `e2e/browse.spec.ts` and `e2e/stores.spec.ts` (3 flows) pass.

## 34. A header that stays at the top (O1)

**The request (2026-10-28):** the shop's header (logo, search, Become Vendor, Wishlist and Cart with their counts, the
account) stays visible while scrolling, on every screen size, without covering the page and without changing the
design.

**Today:** `Header` (one shared component in `MainLayout`, on every shop page) scrolls away with the page. Below it,
`NavBar` (categories, main menu, phone) is shown from 1024 px.

**O1: a sticky header**
- `position: sticky` (`sticky top-0`), not `fixed`. A sticky header keeps its place in the page, so nothing has to be
  pushed down to make room and nothing slides under it at the top.
- Only `Header` is sticky, not `NavBar`. Two sticky rows would take 170 px of a laptop screen. The menu is the first
  thing you see at the top, and the phone menu is in the header.
- Layer `z-50`: the phone menu lives inside the header. With a lower layer, the round buttons (assistant, back to top,
  theme; `z-40`) would sit on top of the open phone menu. Dialogs, the chat and the wishlist pop-up come later in the
  page with the same layer, so they still cover the header. "Skip to content" moves to `z-60`.
- No `backdrop-blur` on the header: it would make the header the frame of the fixed phone menu and squeeze the menu
  into it.
- Once the page is scrolled, a soft shadow under the header shows that the page moves beneath it.
  `hooks/useScrolledPast.ts` is new; the back-to-top button uses it too.
- Phones: a little less padding (`py-3`, `gap-y-2`), so the two rows (icons, search) take less room. Tablets and
  laptops keep their spacing.
- Things that stop or land at the top of the screen stay below the header. `--header-height` in `index.css` holds its
  height per screen size:
  - `scroll-padding-top` on shop pages: links to a part of a page (Hot Deals → `/#deals`, the product tabs) and the
    keyboard focus end below the header.
  - The new `top-below-header` utility for the sticky summaries of the cart and the checkout (they used `top-4`).

| Phase | Builds | Done when |
|---|---|---|
| **O1 Sticky header** | sticky header with a shadow while scrolled, layers, header height for anchors and sticky columns, compact on phones | logo, search, vendor link, wishlist, cart and account stay visible while scrolling on phones, tablets and laptops; nothing is hidden under them |

**Progress (2026-10-28):** O1 ✅
- **Code:**
  - `Header`: `sticky top-0 z-50`, a shadow from the first scrolled pixel, `id="shop-header"`.
  - `useScrolledPast` is new; `BackToTopButton` uses it now.
  - "Skip to content" moved to `z-60`.
  - `index.css`: `--header-height` (8rem / 5.25rem / 6.25rem), `scroll-padding-top` on shop pages (`html:has(#shop-header)`),
    and the `top-below-header` utility. The cart and checkout summaries use it.
- **Found while measuring:** at 320 px, and at 360 px when logged in (the bell is added), the icons wrapped to a third
  row, making the header 159 px tall.
  - Smaller gaps on phones (`gap-x-3`, icons `gap-4`) fix 360 px.
  - Below 360 px the logo shows only the leaf mark (`Logo` gets `wordmarkClassName`); its name for screen readers
    stays.
  - Header heights now: 127 px from 320 to 767 px, 81 px from 768 px, 99 px from 1024 px; the same for guests and
    logged-in customers.
- **Tests:** 2 new layout tests (the header is sticky and gets its shadow when scrolled; back to top after 600 px).
  454 tests pass (`--maxWorkers=4`); type check, lint and build are green. `e2e/browse.spec.ts` and
  `e2e/stores.spec.ts` (3 flows) pass with the sticky header.
- **Browser, real backend (read only) and mock API:**
  - The header stays at `top: 0` after scrolling at 320, 390, 820 and 1440 px, also in the dark theme.
  - Search suggestions open while scrolled; Priya's account menu opens while scrolled.
  - "Hot Deals" (`/#deals`) puts the title at 124 px, below the 99 px header; "read the reviews" puts the tabs at
    157 px.
  - The cart and checkout summaries stick at 116 px.
  - The open phone menu covers the round buttons; the focused skip link is above the header.
  - Mock: an Add and a wishlist heart on a product far down the page change "Cart" and "Wishlist" in the visible
    header (0 → 3 items, 0 → 1). No page errors.

## 35. Dark toasts (O2)

**The request (2026-10-28):** the toast messages look like the page behind them; make them dark black.

**Why they blended in:** `richColors` gave each kind a pale tint. A success toast was pale green, almost the colour of
the page (`#f4f7f5`).

**O2:**
- `App.tsx`: the `Toaster` always uses sonner's dark look (`theme="dark"`, black `#000` with white text and a grey
  border), in the light and the dark theme, without `richColors`.
- `index.css` ("Toasts"): the icon keeps the kind's colour from the theme, since sonner draws it in the text colour.
  Success uses `brand` green, error `hot`, warning `accent` yellow, info `sale` blue.
- A toast's action button ("View cart") is white with black text, from sonner's dark look.

**Progress (2026-10-28):** O2 ✅ In the browser, all four kinds have background `rgb(0, 0, 0)`, white text and their
own icon colour, in both themes. Type check, lint, build and the toast tests (cart, product, wishlist, routes: 37)
are green.

## 36. Social login: "Continue with Google" (SL1)

**The request (2026-10-28):** log in with Google, Microsoft, Facebook or Apple next to the email and password login,
one provider at a time, Google first (backend: Phase 22 of the root README, [docs/social-login.md](social-login.md)).
Buttons on the shop, vendor and delivery pages, not on the admin login.

**How the app takes part:** the button is a plain link to the backend, which sends the browser to the provider. The
provider sends it back to the backend, which redirects to `/login/social#code=...`. The page exchanges that one-time
code for the shop's own login (the same access token and refresh cookie as the password login). The provider's tokens
never reach the app, and the app has no client secret.

**SL1:**
- `components/auth/SocialLoginButtons.tsx`:
  - "Continue with Google" (`action="Sign up"` on the register page) for every provider in
    `GET /auth/social/providers`; nothing at all when the server has none;
  - before leaving, it remembers the panel, the next page and the account type (`features/auth/socialLoginIntent.ts`,
    `sessionStorage`);
  - with the mock API it goes straight to `/login/social#code=mock-google`.
- `components/auth/SocialIcons.tsx`: the four brand logos as inline SVG, and a flask for the local test provider.
- `features/auth/SocialLoginPage.tsx` (`/login/social`):
  - removes the code from the address bar and exchanges it once (also under StrictMode's double effect);
  - then logs in, or shows the sign-up form (name and email from the provider, the email locked when verified, the
    mobile number with the register page's rules), or the password form to connect an existing account;
  - shows errors with a way back: cancelled, failed, expired, refused, and the wrong panel with the right login's link.
- `AuthProvider.finishSocialLogin()`: the same panel check and session start as `logIn`. `LOGIN_LINKS` moved to
  `authContext.ts`, shared with `LoginForm`.
- `api/socialAuth.ts` and `apiUrl()` in `api/client.ts`.
- `safeNextPath` also refuses `\` and control characters (browsers read `/\evil.com` as another site).
- Mock API:
  - `mocks/mockSocial.ts` holds the mock codes, the connected accounts and the waiting attempt;
  - `mocks/handlers/socialAuth.ts` has the four endpoints with the backend's rules and messages;
  - the register handler's account creation is now `createAccount()`, used by both.

| Phase | Builds | Done when |
|---|---|---|
| **SL1 Social login (Google)** | the buttons, `/login/social` with the sign-up and connect forms, the errors, the mock API | a new person signs up with Google and a mobile number, an existing account is connected once with its password, and every error is explained on the page |

**Progress (2026-10-28):** SL1 ✅
- 474 tests (+18):
  - `socialLogin.test.tsx` (17): the buttons (not on the admin login), sign-up with the page's account type, a
    missing email, backend field errors, a connected account, the password to connect, a vendor on the shop's page, an
    admin refused, the five error cases, and the exchange waiting for the check of an earlier login;
  - `validation.test.ts` (+1): `safeNextPath`.
- Type check, lint and build green.
- Found while checking the page: `/login/social` is always a fresh page load, so AuthProvider's `/refresh` and the code
  exchange started together. With a valid cookie of an earlier login in the browser, the exchange could win. That
  login's refused refresh then removed the new refresh cookie: the person looked logged in, but a reload logged them
  out.
  - Fix: the page exchanges the code only when `restoring` is over.
  - The four social calls are public auth calls in `api/client.ts`: no access token, and a 401 ("Invalid email or
    password") is not refreshed and sent again.
  - The new test fails without the fix.
- `e2e/social-login.spec.ts` runs in Edge against the real backend and the local fake provider: sign-up and the next
  login at once, a social login over Priya's login kept after a reload, the password to connect with its email in
  Mailpit, cancel, and a replayed code. All 5 flows pass. The whole suite, 38 flows before the reload flow was
  added, passed: the existing 34 are unchanged.
- Later the same day, the backend's steps 2 to 4 (Microsoft, Facebook, Apple). The React app already had their logos
  and names. Now the mock API offers all four providers in the backend's order: `mock-microsoft`, `mock-facebook`
  (an email the person may change) and `mock-apple` (a relay address). 478 tests (+4 in `socialLogin.test.tsx`: the
  four buttons in order, and a sign-up with each new provider).
  - Found on a phone (390 px) with several buttons: the button list was as wide as its longest label ("Continue with
    Test provider (local only)"), so the page scrolled sideways. The list is now one `minmax(0, 1fr)` column
    (`grid-cols-1`), a long label wraps, and the logos keep their size (`shrink-0`). Measured afterwards: page 390 px,
    the buttons end where the form ends.
  - Checked in Edge against the real backend, with the fake provider playing Microsoft and Apple: both sign-ups end
    logged in. Apple's answer reaches the callback as a form POST through the dev server and the gateway.
    `social-login.spec.ts` and `session.spec.ts`: 9 of 9 pass.
  - The buttons now look like each provider's own, following their branding rules, so people recognise them:
    - Google and Microsoft: light buttons with their logos;
    - Facebook: blue with a white logo (Meta's current blue `#0866FF`; white text 4.8:1);
    - Apple: black.

    In the dark theme Google and Microsoft are dark and Apple is white. The colours are tokens in
    `styles/index.css`, light and dark. The local test provider keeps the shop's outline button. Checked in
    screenshots (light, dark, phone, login and register pages).

## 37. Deal cards that look like cards (O3)

**The request (2026-10-28):** on the home page, the box with a background colour in "Deals Of The Day" (`/#deals`)
does not look good.

**Why:** each deal was a grey picture box with a white box hanging over its bottom edge.
- In the light theme the grey (`surface`, `#f4f6fa`) is almost the page colour (`#f4f7f5`), so the picture box was a
  faint rectangle with no edge.
- In the dark theme it was a visible slab with the white box sticking out below it.
- The white boxes had different heights (a two-line name), so the bottoms did not line up.
- On a phone the four deals were one per row, about 1,550 px together.

**O3:** `features/home/DealsOfTheDay.tsx`
- A deal is one card, the same shape as `ProductCard`: white, border, the card shadow, the picture inside it (on the
  grey of boxes inside cards), then the name, the store, "You save", the price and Add. No overlap.
- The discount badge sits in the card's corner like the product cards' badge, in `hot` pink (deals stand out from the
  green discount badge of the other cards).
- The name is always two lines high and the price row is at the bottom, so the cards of a row line up.
- Two cards per row on phones (like the product grids), all four in one row from 768 px.
- The skeleton has the same card shape.

| Phase | Builds | Done when |
|---|---|---|
| **O3 Deal cards** | the deal card and its skeleton | every deal is a whole card with an edge, in both themes, and a row's cards line up |

**Progress (2026-10-28):** O3 ✅
- Measured in Edge at 320, 390, 768, 820, 1024 and 1440 px: the cards of a row have the same height, and the page
  never scrolls sideways.
- The section is 749 px tall on a phone (was about 1,550), 411 px on a tablet and 489 px on a laptop.
- Screenshots in the light and the dark theme.
- The home tests (8), type check, lint and build are green.

## 38. Clear and remove buttons that look like buttons (O4)

**The request (2026-10-28):** on `/shop?store=1&brand=Himalaya` the "Clear all" next to the filter chips is not
visible: it looks like underlined text. Give it, and the buttons like it elsewhere, a modern button look.

**Found:** the clear and remove actions had four different looks.
- Underlined text (51 × 17 px, no background or edge): "Clear all" (shop filters), "Clear list" (recently viewed),
  "Remove" under each product on the compare page.
- Bold text with an ✕: "Remove" of the coupon in the cart.
- A ghost button (text until hovered): "Empty cart".
- A green outline button: "Remove all" (compare).

**O4:**
- `buttonClasses.ts`: a new variant `remove` for clear, remove and empty actions: a white button with a grey edge and
  dark text; on hover the edge turns pink, with a light pink background and pink text. It stays flat (`shadow-none`),
  also as a link.
- `index.css`: `--color-hot-text`, a pink readable as text (5.2:1 on the hover's background; `#ff85a9` in the dark
  theme, 6.5:1), like `brand-text` and `sale-text`.
- The six buttons use it, each with an icon: ✕ to take something off (filters, a compared product, the coupon), a
  bin to empty a list ("Clear list", "Remove all", "Empty cart").
- "Clear all" is a round pill like the filter chips next to it; the chips are 32 px high, the same as the button.
- Icon-only buttons (the bin on a cart row, the ✕ on a wishlist row and on the compare bar) already look like
  buttons and stay as they are. The admin's "Clear the filters" buttons are normal buttons already.

| Phase | Builds | Done when |
|---|---|---|
| **O4 Remove buttons** | the `remove` variant, `hot-text`, the six buttons | every clear and remove action is a visible button with an edge and an icon, in both themes |

**Progress (2026-10-28):** O4 ✅
- Measured in Edge (light, dark, 390 px):
  - "Clear all" was 51 × 17 px of underlined text; now it is a 102 × 32 px pill with an edge, no underline.
  - On hover: pink edge, pink background, text `#c2185b` (dark theme `#ff85a9`).
  - "Clear list", "Remove" and "Remove all" (compare) checked as a guest; "Empty cart" and the coupon's "Remove" on
    the mock API. The coupon was applied and removed again, so the mock cart is as it was.
- README: "Actions are buttons, not underlined text".
- Type check, lint and build are green. 477 of 478 tests passed in the full run (`--maxWorkers=4`). The failure was
  the live tracking map test (timing under load, not these files), which passes on its own (13 of 13).

## 39. The shop's filters at the top of the products (O5)

**The request (2026-10-28):** on `/shop?store=1&brand=Himalaya&sort=PRICE_HIGH_TO_LOW` the filter, stores and brands
are at the bottom of the page; make it user friendly.

**Measured before (Edge):**
- Laptop (1440 × 900): the left column is Search, the categories, then Filter, Stores, Brands. Filter starts at 943 px,
  Stores at 1,215 px, Brands at 1,500 px: all below the first screen, while the two products end at about 880 px.
- Phone (390 px): the filters are behind the "Filters" button, and after it Brands starts at 1,431 px. The products
  move down to 1,924 px.

**O5: a filter bar above the products** (`features/shop/ShopFilterBar.tsx`)
- A card at the top of the product list, on every screen size, with one row of buttons: **Store**, **Brand**, **Price**
  and an **In stock only** tick box.
  - A button shows the chosen value ("Store: Fresh Mart") and is green while that filter is on.
  - A button opens its choices in the card, below the row (not a floating menu, so it never goes off screen on a
    phone); one at a time, the same button closes it.
  - Choosing a store or a brand, or applying a price, closes the choices; the focus goes back to the button.
  - The chips of the chosen filters and "Clear all" are the card's last row.
- The choices (`features/shop/filters/`): `StoreOptions` (the stores with open or closed), `BrandOptions` (the brand
  chips; loaded when opened), `PriceForm` (min, max, Apply price). They replace the sidebar's `StoreWidget`,
  `BrandWidget` and `FilterWidget`, with the same behaviour and the same URL.
- The left column keeps Search and the categories (`ShopSidebar`: `search`, `categories`; the product page's
  `categories`, `topRated` is unchanged). Its name for screen readers is "Browse the shop".
- Below 1024 px the button above the list is "Categories" (it was "Filters"): the filters are in the bar already.

| Phase | Builds | Done when |
|---|---|---|
| **O5 Filter bar** | `ShopFilterBar`, the three choice parts, the smaller sidebar, the tests | store, brand, price and stock are on the first screen at every width, and every filter works as before |

**Progress (2026-10-28):** O5 ✅
- Measured afterwards on the same page (Edge, real backend, read only):
  - from 1024 px up, the filter bar starts at 313 px and the first product at 520 px. The page is 2,068 px tall
    (was 2,933).
  - On a phone the bar is the first thing under the title (264 px), and the products start at 631 px. With the
    categories open they start at 1,304 px (was 1,924).
  - The open choices stay in the bar (laptop about +100 px, phone brands +220 px). The page never scrolls sideways.
- Checked in screenshots: light and dark theme, 390, 820, 1024, 1280 and 1440 px, each kind of choice open.
- Tests (480, +3):
  - `shop.test.tsx`: brand, store and price from the bar (the choices close, the button shows the value and keeps
    the focus; the same button closes them), "Categories" on small screens.
  - `shopFilters.test.ts`: `priceRangeLabel`.
  - `stores.test.tsx` and `productPage.test.tsx`: the new names.
- `e2e/browse.spec.ts` uses the bar's "In stock only"; browse and stores (3 flows) pass in Edge.
- Type check, lint and build are green.

## 40. The assistant knows the wishlist (O6)

**The request (2026-10-28):** "How many items exist in my wishlist?" in the AI chat got "I cannot check your wishlist",
while the wishlist had one product. Find the root cause and fix it.

**Root cause:** the wishlist is kept only in this browser (`useWishlist.ts`, "there is no wishlist API in the
backend"). The chat sent only the question, so the assistant had nothing to look it up in. The backend side is in
[ai-assistant-plan.md](ai-assistant-plan.md) section 17.

**O6:**
- `api/chat.ts`: `sendChatMessage(message, conversationId, wishlist?)` sends the product names as `wishlist`.
- `useChat(open, shop)`: the shop's chat (`AssistantButton`, `kind="shop"`) sends the current wishlist's names with
  every question (also "Try again"); the panels' chats do not.
- Mock API (`mockChat.ts`): a question about the wishlist answers from the names sent ("You have 1 product on your
  wishlist: Tomato.", tool getWishlist); more than 50 names → 400, like the real service.

| Phase | Builds | Done when |
|---|---|---|
| **O6 Wishlist in the chat** | the request field, the mock answer, the test; the backend tool (ai-assistant-plan.md 17) | "how many items are in my wishlist?" is answered from the wishlist, checked against the shop's live data |

**Progress (2026-10-28):** O6 ✅
- `assistant.test.tsx` (+1): the page sends `wishlist: ['Tomato']` and the answer names it, marked as checked.
- Type check, lint and build green; 481 tests pass (`--maxWorkers=4`).
- With the user's approval, mcp-client-service on 8114 now runs the new build (from this session's scratchpad jar
  `mcp-client-service-wishlist.jar`; the other session's copy was stopped and it was told).
- Checked in Edge through the shop (5173 → gateway → 8114) as a guest: the heart of Himalaya Baby Lotion, then "how many
  items exist in my Wishlist ?" → "There is 1 product in your wishlist: Himalaya Baby Lotion.", "Checked against the
  shop's live data", in 14 s. The guest conversation was deleted with "New chat".

## 41. Links in the assistant's answers (O7)

**The request (2026-10-28):** clickable links in the AI chat's answers to the right pages of the app (cart, wishlist,
orders, account, products ...). Internal links use React Router (no page reload) and only go to real routes. External
links open safely in a new tab. Markdown links if the format allows. The chat stays when moving between pages. Text
chat, voice and the API stay as they are. One reusable piece, simple code.

**What there is:** the answer is plain text (`whitespace-pre-line`, never HTML). The reply has `answer`, `grounded`,
`toolsUsed`. No streaming (one reply per question). The model is told "plain text only"; it never sees page addresses.
The chat panel lives in the layout (shop or panel), so its messages stay while the pages under it change.

**Where links come from: the code, never the model** (backend: [ai-assistant-plan.md](ai-assistant-plan.md) 18)
- mcp-client-service builds `links: [{label, path}]` from this question's tool results, after the answer passed the
  check: a product the answer names (`/products/{id}` from the tool's `productId`), an order (`/orders/{n}`), the cart,
  the wishlist, the orders, the stores, the offers; in the panels their pages (`/vendor/orders`, `/admin/reports` ...).
  At most 4. A model that writes a wrong address cannot create a link.
- The rules say: no links or web addresses in the text; the website adds links to the right pages.

**O7 (frontend):**
- `features/assistant/chatLinks.ts`: which paths a chat may open: the shop's routes in the shop's chat, the panel's
  own routes in a panel's chat (a list of route patterns, checked with React Router's `matchPath`; a test keeps the
  list in step with `routes.tsx`). `safeInternalPath()` refuses `//other.site`, `\`, `javascript:` and unknown pages;
  `safeExternalUrl()` allows only `https:`/`http:`.
- `ChatMessageText` (reusable): shows an answer's text with Markdown links `[label](target)` and bare `https://`
  addresses as links: an allowed path → `<Link>` (no reload); `https` → `<a target="_blank" rel="noopener noreferrer">`
  with an "opens in a new tab" icon and text for screen readers; anything else → only the label, as plain text.
- `ChatLinks`: the reply's `links` as buttons under the answer ("Your wishlist →"), checked the same way.
- After a link to a page, the chat stays open from 1024 px (the page is next to it); on smaller screens it closes, so
  the page is visible. The conversation stays either way (reopen the chat).
- Listen reads the labels, not the addresses; Copy copies the text as it was.
- Mock API: the same `links` (wishlist, cart, orders, products, stores, offers).

| Phase | Builds | Done when |
|---|---|---|
| **O7 Links in answers** | backend `links`, `chatLinks.ts`, `ChatMessageText`, `ChatLinks`, mock, tests | an answer about the wishlist, an order or a product has a link that opens the page without a reload, and unsafe or unknown links are plain text |

**Progress (2026-10-28):** O7 ✅
- Tests (494, +13):
  - `chatLinks.test.ts` (7): every allowed pattern is a route of `routes.tsx`; shop and panel pages; refused targets
    (`//evil.example`, `/\evil.example`, `javascript:`, unknown pages, spaces, control characters); http(s) only for other
    sites; the parser; Listen reads labels.
  - `ChatMessageText.test.tsx` (3): an internal link navigates with the router, an external one has `target="_blank"`
    and `rel="noopener noreferrer"`, unsafe ones are text; the link buttons show only allowed pages.
  - `assistant.test.tsx` (+2): "Order #902" opens `/orders/902`, the chat closes on a small screen and reopens with the
    conversation; on a wide screen "Offers for you" opens `/#offers` and the chat stays open.
- mcp-client-service runs the new build on 8114 (this session's scratchpad jar `mcp-client-service-links.jar`).
- Checked in Edge through the shop (5173 → gateway → 8114), guest: "Do you have Amul butter?" → the answer with the
  button "Amul Butter →" (`/products/7`). At 1440 px a click opens the product page next to the open chat. At 390 px
  the chat closes and the product page shows. One page load in both cases (no reload). Reopened: the conversation is
  still there.
- Type check, lint and build green.

## 42. The theme colour #09792f (O8)

**The request (2026-10-28):** make `#09792f` the app's primary colour wherever the green is used, with readable
foreground colours, light green backgrounds where they fit, the same look in every page (shop, panels, chat), and no
other design changes. Through the shared theme, not colours copied into components.

**What there is:** the green is five tokens in `styles/index.css` (`brand`, `brand-dark`, `brand-text`, `brand-light`,
`brand-soft`), used about 600 times as Tailwind classes. Copies of the old hex values are only in:
- the six login illustrations (`components/auth/illustrations/`, about 50 SVG fills);
- the logo;
- the map colours (`config/tracking.ts`);
- Razorpay's checkout colour;
- `index.html` (`theme-color`) and `public/favicon.svg`.

**Contrast (WCAG 2, computed):**
- White on the old `#3bb77e` was 2.5:1 (too low for the text on buttons); on `#09792f` it is 5.5:1.
- `#09792f` as text is 5.5:1 on white, 5.1:1 on the page and 5.1:1 on the new light green.
- On the dark theme's card it is only 2.9:1, and 2.8:1 on the admin's dark menu ("ink"), so:
  - the dark theme uses one step lighter, `#0a8031` (white text 5.1:1, an icon on a card 3.2:1);
  - green text in the dark theme is `#54d47e` (8.7:1);
  - a new token `ink-brand` (`#54d47e`, the same in both themes) is the green on the always-dark surfaces.

**O8:**

| Token | Light | Dark | Used for |
|---|---|---|---|
| `brand` | `#09792f` | `#0a8031` | buttons, badges, active states, icons |
| `brand-dark` | `#075f24` | `#09792f` | hover and pressed |
| `brand-text` | `#09792f` | `#54d47e` | green text |
| `brand-light` | `#e7f9ed` | `#163b23` | light green backgrounds |
| `brand-soft` | `#bce6ca` | `#2b643e` | borders on light green |
| `ink-brand` (new) | `#54d47e` | `#54d47e` | green on the ink surfaces |

- The ink surfaces use `ink-brand`: the wishlist pop-up's "Open wishlist page", the admin menu's shield and the active
  page's bar and icon.
- The illustrations and the logo use the tokens (`fill-brand`, `fill-brand-light` ...), so they follow the theme.
- The map and Razorpay read the colour from the theme at run time (`themeColour()`); `index.html` and the favicon,
  which cannot read CSS, get `#09792f` written in.
- Unchanged: layout, sizes, fonts, the other colours (yellow, pink, blue, orange), and the chart colours (a validated
  set of their own, section 16).

| Phase | Builds | Done when |
|---|---|---|
| **O8 Theme #09792f** | the tokens, `ink-brand`, the illustrations, logo, map, Razorpay, favicon | every page shows the new green with readable text in both themes, and no hex copy of the old green is left |

**Progress (2026-10-28):** O8 ✅
- Tokens as in the table. About 50 hex copies in the six illustrations became classes; the logo, the map
  (`themeColour()`), Razorpay, `index.html` and the favicon follow. No hex copy of the old greens is left (searched:
  `#3bb77e`, `#29a56c`, `#1f7a50`, `#def9ec`, `#bce3c9` and the old dark values).
- Found with an automatic contrast scan, then fixed:
  - On the always-dark surfaces, `ink-brand` is used for:
    - the wishlist pop-up's "Open wishlist page";
    - the admin menu's shield, its current page's bar and icon, and the logo's "Fresh" there;
    - the admin login's three icons;
    - the success toast's icon.
  - In the dark theme, `brand-text` is used for green icons on a grey or light-green background: the main menu's icons
    (2.8:1 before), the vendor and delivery menus' current page, the chat's title icon, the delivery "online" circle and
    the partner's set-up card.
  - Green text uses `brand-text`: the logo's "Fresh" and the big "404".
- The scan: every green text and icon on 17 pages (shop, product, cart, checkout, order, account, wishlist, stores,
  login, register, admin login, the chat with an answer, and the vendor, admin and delivery panels), light and dark
  theme. 0 below 4.5:1 for text or 3:1 for icons.
- Screenshots before and after, 26 views: light, dark, 1440 and 390 px, every role (mock API).
- Type check, lint and build green. 493 of 494 tests in the full run. The one failure is the live tracking map test,
  the same timing failure as before (a marker still there under load), and it passes on its own 3 times out of 3.

## 43. A modern order page (O9)

**The request (2026-10-28):** redesign the customer's order page (`/orders/194`): a summary with the order number,
date, status, payment and total; the progress as a step-by-step timeline from the real statuses; clearer products,
prices, delivery and payment; the actions in the right place; the theme, every screen size, the same parts wherever
they are used; nothing in the order, payment, invoice, cancel or tracking rules changes.

**Measured before (order 194, Priya, delivered, real backend, read only):**
- The page is 2,905 px tall on a laptop and 5,047 px on a phone.
- There is no summary: the status is a badge, the payment a small line inside the totals, the total in the side column.
- The progress is seven bars without times, and the history below repeats the same seven steps in full (about 420 px).
- The side column has four separate cards and no payment section.

**O9:**
- **Summary card** at the top:
  - "Order #194" with the status badge, "Placed on … at Fresh Mart";
  - the actions: Track on the map (primary, only while there is a delivery to follow), Download invoice (once the
    store accepted it), Cancel order (`remove` button, only while `cancellable`);
  - four facts: order date, items, payment (method and a payment status badge) and the total.
- **Progress** (`OrderProgress`, also on the admin's order page and the tracking page):
  - a step for each status of the normal way, with an icon, a line to the next step, and the time it happened (from
    the order's own history);
  - reached steps are green with a tick, the current one is ringed, and later steps stay grey without a time;
  - across the card from 768 px, a vertical list on phones;
  - a cancelled, rejected or failed order shows no progress (as now): the red status card says why.
- **History**: folded under the progress ("Full history, 7 updates"). It opens by itself for a stopped order, where the
  history says what happened. The vendor and admin pages keep it as a section (`OrderTimeline`, `folded`).
- **Products** (`OrderItemsList`, also on the admin page):
  - the picture on the theme's background (`ProductImage`), name, pack size, unit price (with the old price crossed
    out), quantity and subtotal;
  - in columns with titles when the card is wide (container width, like the cart), stacked on a phone.
- **Side column:**
  - "Order total" (items total with the number of items, coupon, delivery, GST included, total);
  - "Payment" (method, status badge, the payment sentence);
  - "Delivery" (the recipient and phone, the address, the note, and the delivery partner with "Call");
  - "Sold by" (the store, its address, "Call the store").
- Unchanged: the thank-you note after placing an order, the online payment panel (still right under the progress), the
  delivery rating and the product reviews (under the products), every rule (`cancellable`, `hasInvoice`, `hasLiveMap`,
  `pollInterval`, `paymentSummary`).
- Not added: "Order again". The app has no reorder rule; adding the items one by one would need the one-store cart
  rule and stock checks for each item, so it is a change of its own.

| Phase | Builds | Done when |
|---|---|---|
| **O9 Order page** | summary card, `OrderProgress` with icons and times, folded history, products in columns, payment and delivery cards | order 194 shows its summary, steps with times, products, totals, payment and delivery at a glance on a laptop, tablet and phone, and every existing order test passes |

**Progress (2026-10-28):** O9 ✅
- Order 194 (real backend, read only):
  - On a laptop the summary, the seven steps with their times and the totals are on the first screen. The page is
    2,615 px tall (was 2,905): the history is folded.
  - On a phone the summary is two columns and the steps a vertical list. 5,007 px: the products, rating and side cards
    come one under the other, and the footer and the green banner take about 2,000 px.
  - No sideways scrolling at 390, 820 or 1440 px; the dark theme checked.
- Mock API (each browser has its own mock data, so nothing is shared):
  - a new online order shows "Thank you", "Placed" as the current step with only its own time, "Not paid yet", the
    payment panel under the progress and "Cancel order";
  - a cash order shows "Pay on delivery";
  - a cancelled order has no progress, and its history is open.
- The same parts on the admin's order page: step times and product columns (`minmax(0,1fr)` column so the products
  fit). Its "Cancel order" is the `remove` button too.
- Contrast scan (section 42) of the order pages, light and dark: 0 problems.
- `order.test.tsx`: the summary (status, "Pay on delivery", total), a time on the current step and none on later steps,
  the history folded (and open for a stopped order). 494 tests pass (`--maxWorkers=4`). Type check, lint and build
  green.
- Not run: `e2e/orders.spec.ts`, which places and cancels real orders as Priya. Its texts ("Delivered", "Order progress"
  with the last step current, "Rate the delivery", "Cancel order", "Download invoice", "Paid ₹… online") are unchanged.

## 44. Maximize and minimize the AI chat (O10)

**The request (2026-10-28):** a maximize and a minimize button in the chat's header. Maximize uses the screen's height,
minimize goes back to a compact chat. The width stays exactly as it is, in the same place; every size of screen; the
conversation, the question box, voice and links keep working; in the shared chat component.

**What there is:** `ChatPanel` (shared by the shop and the three panels) is a full-height panel at the right edge,
37.44rem wide (W1, W2 in section 19); on a phone it is the whole screen. Before W1 it was a 36rem-high card above the
chat button.

**O10 (`ChatPanel`):**
- Two sizes:
  - **Maximized** (the start, as W1 asked): from the top of the screen to the bottom, as now;
  - **Minimized**: 36rem high (at most 85 % of the screen), at the bottom of the same right edge with a rounded top
    corner. On a phone it is a sheet at the bottom with rounded top corners, and the page shows above it.
- The width does not change: 37.44rem from 640 px, the whole width below.
- One button in the header between "New chat" and close: "Minimize the chat" (shrink icon) while maximized, "Maximize
  the chat" (expand icon) while minimized; `aria-pressed` is not used, the name says what a click does.
- The choice is remembered in this browser (`localStorage`, `chatStorage.ts`; it works without storage too).
- The messages take the height there is (they already scroll in the middle); after a change of size the newest message
  is in view again. The question box, voice, links, "Try again" and Escape are unchanged.

| Phase | Builds | Done when |
|---|---|---|
| **O10 Chat sizes** | the size button, the two heights, the remembered choice, tests | the chat switches between full height and compact at the same width on a laptop, tablet and phone, and keeps its conversation |

**Progress (2026-10-28):** O10 ✅
- Measured in Edge with three answers in the chat (mock API):

  | Screen | Width, both sizes | Maximized | Minimized |
  |---|---|---|---|
  | 1440 × 900 | 599 px, at x 841 | 900 px | 576 px |
  | 1280 × 600 | 599 px | 600 px | 510 px (85 % of the screen) |
  | 820 × 1180 | 599 px | 1,180 px | 576 px |
  | 390 × 844 | 390 px | 844 px | 576 px |

- Both sizes, every screen: the newest message and the question box in view, no sideways scrolling.
- Checked in screenshots, light and dark.
- `assistant.test.tsx` (+1): starts maximized; minimizing keeps the answer and the question box; closed and opened
  again it is still minimized with the same conversation; maximizing saves the choice. 495 tests pass
  (`--maxWorkers=4`). Type check, lint and build green.
- The same `ChatPanel` serves the shop and the vendor, delivery and admin panels, so all four have the button.

## 45. A modern look for the AI chat (O11)

**The request (2026-10-28):** redesign the AI chat's look and feel like a modern UI screen.

**What there is (screenshots, mock API):**
- a white title bar with a small icon and three text or icon buttons;
- the welcome screen: a centred greeting and three plain example cards, all with the same question icon;
- answers in bordered white boxes, with a row of text buttons under each ("Copy", "Listen");
- the question box: a bordered text field with two square buttons beside it;
- the chat button: a flat green pill.

**Found while looking:** the link buttons under an answer are a list inside the message's list item. The browser flow
`e2e/assistant.spec.ts` counts the messages by list item (`toHaveCount(2)`), so an answer with a link (any product)
would count as 3. The link buttons become a labelled group of links instead of a nested list.

**O11** (the same parts for the shop and the three panels; every name for screen readers and every behaviour stays):
- **Title bar:**
  - a band in the brand gradient (`brand` to `brand-dark`, white text: 5.5:1 and more);
  - the assistant's picture in a white circle, the title, and the topics under it;
  - round icon buttons with tooltips: new chat, minimize or maximize, close, with a white focus ring on the green.
- **Welcome:**
  - the picture large with a soft green halo, "Hi, Priya! 👋", the greeting;
  - the example questions as cards, each with an icon for its topic (a product, offers, delivery, an order, stock,
    sales, earnings …) and an arrow that moves on hover.
- **Messages:**
  - the customer's in the brand green with white text, the assistant's in a white bubble with a soft shadow, each with
    a "tail" corner;
  - under an answer: the link buttons, the "checked against live data" pill, and Copy and Listen as small round icon
    buttons with tooltips.
- **Typing:** the bubble with three bouncing dots and "Looking it up…" as now, in the new bubble style.
- **Question box:**
  - one rounded box holding the text field (it grows with the text, up to 9rem, where the browser can), the
    microphone and a round send button;
  - the whole box shows the focus with a green border and halo, and the red border while recording;
  - under it: "Enter to send, Shift + Enter for a new line" (from 640 px) and the voice status or the characters left.
- **Chat button:** the brand gradient with a soft green shadow, lifting a little on hover.
- Unchanged: sizes and places (sections 19 and 44), the texts, the links, voice, "Try again", Escape, the remembered
  size.

| Phase | Builds | Done when |
|---|---|---|
| **O11 Modern chat** | title bar, welcome, messages, typing, question box, chat button, the link group | the chat looks like a current chat app in both themes and every size, the e2e message count is right with links, and every chat test passes |

**Progress (2026-10-28):** O11 ✅
- Screenshots before and after (mock API), 11 states:
  - the welcome screen for a guest, a customer and a vendor;
  - a conversation with links, on a laptop and a phone;
  - the typing bubble, minimized, a typed question;
  - the dark theme;
  - the chat button on a laptop, a phone and in the dark theme.
- Found on a phone and fixed: Copy and Listen wrapped one by one under the "checked" pill; they now stay together.
- Contrast scan (section 42) with the chat open and answered, light and dark: 0 problems.
- Tests (495): the link buttons are a group (`ChatMessageText.test.tsx`), and an answer with links is still one list
  item of the chat (`assistant.test.tsx`).
- `e2e/assistant.spec.ts` against the real backend and the language model (a guest's tomato question with its
  product link, and vendor1's store question): 2 of 2 pass. Type check, lint and build green.

## 46. The vendor's order queue keeps itself up to date (O12)

**The request (2026-10-28):** on `/vendor/orders` the automatic updates do not work as expected; find the root cause,
fix it, keep the counts and statuses current, handle errors, no leaks or duplicate requests.

**What there is:** React Query. The list (`useStoreOrders`) and the counters of the tabs, the menu and the dashboard
(`useStoreOrderCount`) ask again every 30 s (`refetchInterval`); an order's page follows `pollInterval`. Every step
reloads the queue (`allVendorOrderLists`). The app's defaults for every query: `staleTime` 60 s,
`refetchOnWindowFocus: false`.

**Measured (Edge, real backend, read only, vendor1):**
- While the page is in front, the list and the three counters are asked every 30 s (200, `Cache-Control: no-store`; no
  cache in order-service). The polling itself works.
- **Gap 1, the browser tab:** hidden for 40 s, no request (React Query pauses polling in a hidden tab, as designed).
  Shown again: no request within 3 s, because `refetchOnWindowFocus` is off. The vendor looks at the queue as it was
  when they left until the next tick, up to 30 s later.
- **Gap 2, showing a list again:** "New" → "All" → back to "New" 23 s later: no request for 25 s. Data younger than the
  60 s `staleTime` is shown from the cache without asking, and the timer starts again. The worst case is about 90 s old.
- **Gap 3, an error:** one refresh that fails (a service restarting) makes `isError` true. The page then replaces the
  whole list with "Could not load the orders" although it still has the last good list.
- Not a cause: duplicate requests. Coming from the dashboard, the "new" counter shared by the menu and the tab was asked
  twice in 62 s, once per 30 s.

**Root cause:** the queue uses the app's defaults for data that rarely changes (60 s fresh, no refresh on return to
the tab), on top of a 30 s timer that only runs while the tab is shown. A failed refresh is treated like a failed first
load.

**O12:**
- `useVendorOrders.ts`, one `LIVE_QUEUE` setting for the list and the counters:
  - asked every **15 s** while the page is shown;
  - `staleTime: 0`, so a list or counter is asked again whenever it is shown (another tab, back from an order);
  - `refetchOnWindowFocus: true`, asked at once when the vendor comes back to the browser tab.
  - React Query still stops the timer when the page is left (no leak), drops a request the page no longer needs (the
    `signal`), and asks once for a list several parts show.
- An order's page (`useStoreOrder`) is asked again on return to the tab too.
- `useRefreshStoreOrders()`: reloads every list and counter of the queue, for the new "Check now" button.
- `VendorOrdersPage`:
  - next to the title, "Updated 3:45 pm · checks every 15 s", or "Checking for new orders…", and a "Check now" button;
  - a failed refresh keeps the list on screen with a notice: "Could not refresh the orders. Showing the orders from
    3:45 pm; trying again by itself." and "Try now";
  - only a failed first load shows the error page.

| Phase | Builds | Done when |
|---|---|---|
| **O12 Live queue** | `LIVE_QUEUE`, focus and mount refresh, "Check now", the refresh notice, tests | a new order shows up on its own (≤ 15 s while shown, at once on return to the tab or the list), a failed refresh keeps the list, and nothing is asked twice |

**Progress (2026-10-28):** O12 ✅
- Why the tests never caught it: the tests' QueryClient uses React Query's own defaults (stale at once, asked again on
  focus), not the app's. A new `appQueryClient()` in `test/render.tsx` gives a test the app's settings
  (`renderApp(path, appQueryClient())`); the other tests are unchanged.
- `vendor.test.tsx` (+4, with the app's settings):
  - a new order shows up by itself after 15 s (fake timers);
  - at once when the browser tab is shown again;
  - when "New" is shown again after "All";
  - a failed refresh (503) keeps the list with the notice, and "Try now" clears it.
- With the old settings the tab and list tests fail (checked by putting the old settings back for one run); with the
  fix all 36 vendor tests pass.
- Measured again in Edge, real backend, read only:
  - the New list is asked every 15 s, each counter once per tick;
  - nothing is asked while the tab is hidden;
  - when the tab is shown again, the list and the three counters are asked at once (+0.0 s);
  - "New" shown again after "All" is asked at once (0.1 s);
  - "Check now" asks the list and the three counters.
- Correction to the measurement before the fix: its "shown again" step sent a `visibilitychange` that does not bubble,
  but React Query listens on `window`. The finding stands from the settings (`refetchOnWindowFocus: false`); the
  measurement after the fix sends it the way a browser does.
- A new order in a real browser (mock API, placed in the same page while the queue was open, no click): shown after
  15.1 s, with "New, 2 waiting" and the menu's "Orders, 2 new".
- 503 tests (`--maxWorkers=4`), type check, lint and build green.
- Not run: `e2e/vendor.spec.ts`, which places and moves real orders.

## 47. Live order updates for the vendor: Server-Sent Events (O13)

**The request (2026-10-28):** after O12 the queue asks the server every 15 s (four requests per tick, also when nothing
changed). Use Server-Sent Events instead, "the best and simple way", so that even a junior developer can follow the code
and the configuration.

**What there is:**
- The customer's tracking page already uses SSE: `LiveTrackingStreams` in delivery-service, `api/eventStream.ts` and
  `useTrackingStream` in the frontend. O13 copies that pattern, so there is one way to do live updates in this project.
- Every order change is already an event on Kafka `grocery.order.events` (outbox + Debezium; the key is the order id).

**Why SSE and not WebSockets:** the updates go one way (server → vendor). SSE is a normal HTTP response that stays
open, so it passes the gateway, the JWT check, proxies and HTTP/2 like any request. (The reasons for 10 million users
were given in the chat before this section.)

**How it works:**
```
any order change → outbox → Debezium → Kafka grocery.order.events (key = order id)
  → OrderChangeListener on EVERY order-service instance (a consumer group of its own, newest events only)
  → reads the order (its store and status) → VendorOrderStreams of that store on this instance
  → event: order   data: {"orderId":194,"status":"CONFIRMED"}
  → the page asks the normal REST endpoints again for what it shows (React Query)
```
- The stream says *which* order changed, not the whole list. The REST endpoints stay the only source of the lists,
  pages and counters, so there is no second copy of the queue's logic.
- Why one consumer group per instance: a vendor's stream is open on one instance, and a shared group gives each event to
  only one instance. With its own group, every instance sees every event and keeps the ones for its own streams. It
  starts at the newest event; `earliest`, the service's default, would replay the whole order history at every start.

**O13, order-service:**
- `VendorOrderStreams`:
  - the open streams by store; `ready` when one opens; an `order` event per change;
  - a keep-alive comment every 15 s; a stream closes after 30 min and the browser opens a new one;
  - at most 2,000 streams per instance (503 beyond), as for the tracking streams;
  - every stream closed first when the service stops;
  - gauge `order.vendor.streams`.
- `OrderChangeListener` (Kafka): does nothing while no stream is open; never throws (a live update is not worth a retry
  or a dead letter).
- `GET /api/v1/vendor/orders/stream` (VENDOR, the store from X-Store-Id): `text/event-stream`, `no-cache`,
  `X-Accel-Buffering: no`.
- Settings `app.vendor-order-stream`.

**O13, gateway:** the route `order-service-vendor-order-stream`, before `order-service`, without the 10 s response
timeout. A circuit breaker whose slow call is 2 h, and a bulkhead of 2,000 streams per gateway instance, the same as the
tracking stream.

**O13, frontend:**
- `followStoreOrders()` in `api/vendorOrders.ts` (read with fetch: `EventSource` cannot send the token).
- `useStoreOrderStream()`:
  - opened once, by the vendor panel's frame (`VendorLayout`), so the queue, its tab counters, the menu's badge, the
    dashboard and an open order page all update;
  - reconnects after a break (2 s, 4 s … at most 30 s; at once when the browser is back online);
  - treats 45 s of silence as a dead connection; stops on 401/403.
- React Query does the rest: `ready` and `order` mark the lists (and that order) as changed, and only what is on screen
  is asked again.
- Polling becomes the safety net: once a minute while live (in case an event got lost), every 15 s while the stream is
  down (as in O12). An order's page keeps its own rule (every 30 s while the order moves); the stream makes it instant.
- The queue's title line:
  - while live: "● Live · new orders appear by themselves", with no "Check now";
  - otherwise "Updated 3:45 pm · checks every 15 s" and "Check now", as before (an amber dot while reconnecting).
- Mock: `mockOrderStream.ts` sends `ready`, then an `order` event when one of the store's orders is new or changes
  status (it compares the store's orders every 0.5 s). The handler sits before `/:orderId`.

**Not in O13:**
- the notification bell still polls;
- a separate real-time service for very large numbers of users;
- replay of missed events with `Last-Event-ID` (the reload after `ready` covers them).

| Phase | Builds | Done when |
|---|---|---|
| **O13 Live orders (SSE)** | order-service stream, listener, endpoint and tests; gateway route; frontend hook, page line, safety-net polling, mock and tests; docs | a new order shows in the queue within about a second without polling; while live the queue asks at most once a minute; with the stream down it falls back to 15 s; all tests pass |

**Progress (2026-10-29):** O13 ✅
- Changes beyond the design above:
  - **The listener never saves offsets** (`ackMode = "MANUAL"`, nothing acknowledged). The `KafkaConsumerNoProgress`
    alert covers every group it does not name. A group per instance with saved offsets would stay behind after each
    restart, with a growing lag, and alert for up to 7 days. Without offsets, kafka-exporter reports -1, and Kafka drops
    the empty group within minutes.
  - **Streams close on `ContextClosedEvent`, not `@PreDestroy`:** the graceful shutdown waits up to 30 s for open async
    requests before `@PreDestroy` runs.
  - **Monitoring:** the gateway's latency SLO (Prometheus rules and the Grafana SLO dashboard) left out only the
    tracking stream's route. It now leaves out every route named `*-stream`, so a stream open for minutes never burns
    the latency budget. The per-service rules already left out `.../stream` URIs.
- **Found and fixed on the way** (measured on the running delivery-service, read only): `apiStream()` asked for
  `text/event-stream` alone, so Spring could not write the JSON error and every stream error became a **500**. The
  tracking stream answered 500 instead of 404, so the tracking page's "waiting" state never started. `apiStream()` now
  accepts `application/json` too (agreed with the session working in `api/client.ts`); 404 measured with it.
- **Found on the real backend and fixed:** every closed vendor tab logged an ERROR with a stack trace. Tomcat reports
  the gone browser as `AsyncRequestNotUsableException`, and the catch-all `@ExceptionHandler(Exception.class)` logged
  it as "Unexpected error", then failed to write JSON into the event stream. The closed stream also stayed counted for
  about 75 s. Now `GlobalExceptionHandler` handles `AsyncRequestNotUsableException` quietly (DEBUG), and
  `VendorOrderStreams` forgets a stream on `emitter.onError`. delivery-service's tracking stream has the same catch-all
  (not changed).
- Tests:
  - order-service: 78 tests including the Docker ones, run on the final code (one suite at a time, after Docker
    Desktop hung).
    - `VendorOrderControllerTest` (4): `ready`, then only the own store's changes, and the headers; a browser that
      left is forgotten at once and not logged as an error; 503 when full; 403 without a store. Checked to fail without
      each of the two fixes.
    - `OrderChangeListenerTest` (3).
    - `VendorOrderStreamIntegrationTest` (MySQL + Kafka): its first run failed only because a keep-alive came in
      during the test; the assertion now allows one.
  - gateway: 57 tests.
    - `ResilienceTest`: the stream is not cut by the timeout and has its own breaker and bulkhead.
    - `GatewayRoutingAndSecurityTest`: routed to order-service; a customer gets 403.
  - frontend: `vendor.test.tsx` +3:
    - live: a new order appears at once, with no "Check now";
    - no request for 45 s while live, then the safety net at 60 s;
    - after a 503 it reconnects and is live again.
    The O12 tests now run with the stream down. 514 tests (`--maxWorkers=4`), type check, lint and build pass. A first
    full run had 11 timeouts in auth, cart and voice while Docker hung; all 45 pass alone, then all 514.
- In Edge on the mock API (the page's own mock data):
  - "Live" at once;
  - a new order shown after 0.8 s, without a click;
  - no queue request in the 50 s after, then the safety net's 4 requests at 59 s;
  - stream down (503): "Check now" and the amber dot after 2.4 s; back: "Live" after 4.4 s;
  - no page errors.
- On the real backend (the user approved restarting order-service and the gateway; both now run from this session's
  jar copies, with the other session's Phase 3 code):
  - the live listener joined its own group, got the 3 partitions of `grocery.order.events`, and saved no offsets;
  - Edge as vendor1, read only: the stream opens on the dashboard (`200 text/event-stream`, `no-cache`, through the
    gateway); the queue shows "Live" at once; one stream for 39 s (past the gateway's 10 s timeout); no queue request;
    `order_vendor_streams` = 1;
  - curl directly: a keep-alive every 15 s;
  - page closed: the stream is forgotten at the next keep-alive (16.7 s), with no ERROR or WARN in the log.
  - Real `order` events, recorded read only with curl, from the other session's assistant test orders (customer2 at
    Fresh Mart): #200 PENDING 01:15:13, CONFIRMED 01:15:14, CANCELLED 01:15:21; #201 PENDING 01:16:03, CONFIRMED
    01:16:04, CANCELLED 01:16:25. Each arrived within about 1 s of the database change, for example confirmed at
    01:15:13.686 and the event at 01:15:14 (outbox, Debezium, Kafka, listener, stream).

## 48. The customer's home page after logging in (O14)

**The request (2026-10-29):** after a successful customer login, go to http://localhost:5173/customer/home instead of
http://localhost:5173/. Only the customer login: the admin, vendor and delivery partner logins keep their pages, and the
login itself, the role checks and everything else stay as they are.

**What there is:**
- The shop's login (`LoginPage`) goes to the page in `?next=` (e.g. `/cart`), else to `/` (`safeNextPath`). The
  vendor, delivery and admin login pages pass their own start page instead (`'/vendor'`, `'/delivery'`, `'/admin'`).
- "Continue with Google" on the login page remembers the same page for the way back (`socialLoginIntent.ts`); when
  nothing was remembered (storage blocked, or `/login/social` opened directly) it ends on `/`.
- `/customer/home` does not exist yet: it would show "Page not found".

**O14:**
- `CUSTOMER_HOME = '/customer/home'` in `authContext.ts`.
- The route `customer/home` inside `MainLayout`: the shop's home page (`HomePage`), only for a logged-in customer
  (`RequireCustomer`, like the cart and My account). A guest goes to the login and comes back; another kind of account
  is told the page is for customers.
- `LoginPage`: after logging in, the page in `?next=` as before. When that is the shop's home page (`/`, also when no
  page or another website was given), `CUSTOMER_HOME` instead. "Continue with Google" gets the same page. The hint
  "This page needs a customer account" and the "Create an account" link use the page that was asked for, as before.
- `readSocialLogin()`: nothing remembered → `CUSTOMER_HOME` (it is the shop's login, so only a customer gets there).
- `MainLayout` and `NavBar`: `/customer/home` counts as the home page (the light banner, the green "Home" pill).
- Unchanged: the vendor, delivery and admin logins; the sign-up page (also with Google) still ends on `/`; logging out
  still goes to `/`; `PANELS`; the logo, "Home" and "Back to the shop" still link to `/`.

| Phase | Builds | Done when |
|---|---|---|
| **O14 Customer home** | constant, route, login default, social fallback, layout, tests, docs | a customer who logs in on /login (password or Google) lands on /customer/home and sees the home page; with `?next=` they go back to that page; the other logins are unchanged; all tests pass |

**Progress (2026-10-29):** O14 ✅
- The page is `features/home/CustomerHomePage.tsx` (`RequireCustomer` around `HomePage`), loaded with `lazy` like the
  other customer pages.
- Tests (+5):
  - `auth.test.tsx`: logging in on `/login` and on `/login?next=/` lands on `/customer/home`, with the home page and the
    green "Home" pill; a guest who opens `/customer/home` logs in first and comes back; vendor1 is told the page is for
    customers.
  - `socialLogin.test.tsx`: "Continue with Google" from `/login` ends on `/customer/home`. The three tests that open
    `/login/social` with nothing remembered now expect `/customer/home`.
  - Unchanged and passing: `?next=/wishlist`; the sign-up (ends on `/`; the "Create an account" link still carries
    `next=/`); the vendor, delivery and admin logins.
  - 539 tests (`--maxWorkers=4`); type check, lint and build pass.
  - `e2e/social-login.spec.ts` now expects `/customer/home` after a sign-up started on the login page. Not run: it
    needs the fake provider.
- In Edge on the real backend (5173 → gateway), read only:
  - customer1 from the header's account icon: http://localhost:5173/customer/home, the home page, "Welcome back,
    Priya!", the green "Home" pill and the light banner; still there after a reload;
  - on a phone (390 px): the same, no sideways scrolling;
  - `/login?next=/cart` → `/cart`;
  - a guest on `/customer/home` → `/login?next=%2Fcustomer%2Fhome` with the hint, then back to `/customer/home`;
  - vendor1 → `/vendor` (and "This page is for customers" on `/customer/home`), delivery1 → `/delivery`, admin →
    `/admin`;
  - no page errors.

## 49. A compact Popular Products section (O15)

**The request (2026-10-29):** make the home page's Popular Products section more compact, modern and user-friendly:
neat category tabs with a clear active state, less space per product, more products in the same area, aligned names,
prices, badges and Add buttons, and a fitting number of cards per row on every screen. Filtering, Add to cart, the
product page, prices and discounts keep working; no business logic or API change. Simple code for a junior developer.

**What there is (measured in Edge, 10 products, guest):**

| Screen | Cards per row | Card | Section height |
|---|---|---|---|
| 1440 px | 5 | 266 × 456 px | 987 px |
| 820 px | 3 | 252 × 442 px | 1,908 px |
| 390 px | 2 | 173 × 363 px | 2,087 px |

- A square picture box with a large inset (`px-2 pt-5`) around a small picture; the category line; a name box two lines
  high; the pack size, stars and store on three lines; 16 px padding.
- The tabs are plain words; only the colour shows the chosen one. On a phone they wrap onto two lines.
- On narrow cards "Add" drops under the price, so the buttons do not line up.
- `CategoryTabs` is shared with Daily Fresh Picks; `ProductCard` and `ProductGrid` with the shop, a store's page and
  related products.

**O15:**
- `CategoryTabs`: round pills with a border; the chosen one filled green (`aria-pressed`, as before). On a narrow
  screen they wrap onto a second line, so every category stays in view. (A first try put them in one track that
  scrolls sideways on a phone; there the row seemed to end at "Dairy & Eggs", hiding two categories.) Daily Fresh Picks
  gets the same look.
- `ProductCard size="small"` (only Popular Products; the other pages keep the regular card):
  - 10 px padding; the picture fills the card's width in a 4:3 box; smaller badge and round buttons;
  - no category line (the tabs say it);
  - the name two lines high (so a row lines up); pack size and stars on one line; the store on one short line;
  - price and Add on one line at the bottom: the crossed-out price goes under the price when there is no room, so Add
    never moves under it.
- `ProductGrid cardSize="small"`: 2 cards per row on a phone, 3 from 640 px, 4 from 768 px, 6 from 1280 px; smaller
  gaps. `ProductCardSkeleton` gets the same small shape.
- Popular Products shows 12 products instead of 10, so every row is full at 2, 3, 4 and 6 per row.
- Unchanged: the filtering, the data (`useProductSearch`), Add to cart, the wishlist and compare buttons, the product
  links, prices, badges.

| Phase | Builds | Done when |
|---|---|---|
| **O15 Compact Popular Products** | tabs, small card, grid, skeleton, tests, docs | more products in less height on desktop, tablet and phone without sideways scrolling; every tab in view with a clear chosen one; filtering, Add, wishlist, compare and links work; all tests pass |

**Progress (2026-10-29):** O15 ✅
- Measured in Edge on the real backend (guest), before → after:

  | Screen | Cards per row | Card | Section height |
  |---|---|---|---|
  | 1440 px | 5 → 6 | 266 × 456 → 222 × 302 px | 987 → 668 px (10 → 12 products) |
  | 1280 px | 5 → 6 | 234 × 424 → 195 × 282 px | 923 → 640 px |
  | 820 px | 3 → 4 | 252 × 442 → 188 × 277 px | 1,908 → 976 px |
  | 390 px | 2 → 2 | 173 × 363 → 175 × 267 px | 2,087 → 1,814 px |

  No sideways scrolling at any width; the tabs take one line from 640 px, two on a phone (a first version took three
  there, so the pills are a little smaller below 640 px).
- Checked in the browser: "Fruits" shows only fruits and is marked as chosen; the heart and the compare button
  toggle; the name opens the product page; Add sends a guest to the login and back; keyboard focus shows a ring on
  the pills; the dark theme; Daily Fresh Picks with the same pills next to its arrows. No page errors.
- Tests (+2): `product.test.tsx` (the small card keeps the link, pack size, badge, stars, prices, Add, heart,
  compare and store, without the category line), `home.test.tsx` (12 products). 545 tests (`--maxWorkers=4`), type
  check, lint and build pass.

## 50. A modern, lively admin dashboard (O16)

**The request (2026-10-29):** the admin dashboard (`/admin`) looks too simple and traditional. Make it modern,
user-friendly and interactive: attractive illustrations, subtle animations and smooth transitions; better cards, charts
and menu; easy to navigate; responsive. Same stack, all functionality and business logic kept, simple code.

**What there is (screenshots in Edge, admin on the real backend):** M2's dashboard (section 13). A dark welcome banner
with a still illustration; three attention tiles; "Today" as four white tiles and "The platform" as six, all alike
with small green icons; "Latest orders" beside them, with empty space under it; the sales section: four more alike
tiles, the area chart over the full width, then the donut and two top lists. Nothing moves. 2,301 px high at 1440 px,
3,372 px on a phone.

**O16** (only the look and the arrangement; the data, the requests, the links and the texts the tests use stay):
- **Moving in:** each row of the dashboard rises and fades in, one after the other (`Appear`, `animate-rise`). The
  numbers count up from 0 (`useCountUp`). The charts draw themselves: the area reveals from left to right, the rings
  sweep round, the bars of the top lists grow. All of it only when the system allows motion (`motion-safe`; "reduce
  motion" and the tests show the final state at once).
- **Welcome banner:** a deeper gradient with two slowly drifting lights and a fine dot grid; the date in a chip with a
  live dot; "Hello, Platform" with a waving hand; quick links as glass buttons; the illustration floats and its
  columns grow (`DashboardIllustration animated`; the admin login keeps the still one).
- **Attention tiles:** an icon in a coloured chip (amber while work waits, with a pulsing dot; green when clear); they
  lift on hover. "Review now" / "All clear" and the number, as before.
- **Today:** four `StatCard`s, each with its own colour (an icon chip with a gradient and a soft glow), a large number
  that counts up, and a link to the page with the details (orders, reports, payments); they lift on hover. Pending
  refunds turn amber when there are any.
- **The platform:** one card instead of six tiles: a ring with the share of active customers (the number written in
  the middle and under it), and six small figures with icons (total orders, customers, active stores, active delivery
  partners, out-of-stock products, average delivery time). Beside it, at the same height, the latest orders: a feed
  where the whole row opens the order and lights up on hover.
- **Sales:** the four totals as `StatCard`s too; the area chart (two thirds) beside the donut (one third); the top
  stores and best sellers side by side.
- **Menu (dark, so only the admin panel):** the current page is a green pill with a glow instead of a thin bar; the
  links slide a little on hover; the team box gets a green shield chip.
- **Responsive:** the rows use container queries (the space beside the menu, not the screen width): two cards per row
  on a phone and a laptop with the menu open, four from 768 px of content; the platform card and the feed side by
  side from 896 px of content.
- New code: `hooks/useCountUp.ts`, `components/ui/Appear.tsx`, `features/admin/dashboard/StatCard.tsx` and
  `PlatformCard.tsx`, `components/sales/periodTotals.ts` (the totals' sums, shared by `SalesTotals` and the dashboard);
  keyframes `rise`, `float`, `wave`, `grow-up`, `grow-x`, `draw`, `reveal` in `styles/index.css`.
- Shared parts: the chart animations (area, donut, top-list bars) also run on the vendor dashboard and the reports;
  nothing else changes for the other panels.

| Phase | Builds | Done when |
|---|---|---|
| **O16 Modern admin dashboard** | animations, banner, tiles, stat cards, platform card, feed, sales layout, dark menu, tests, docs | the dashboard looks modern and moves smoothly on a laptop, tablet and phone, light and dark, still without motion when the system asks for none; every link, number, chart and the period picker work as before; all tests pass |

**Progress (2026-10-29):** O16 ✅
- Changes beyond the design above:
  - the totals' sums are in `components/sales/periodTotals.ts` (`periodTotalsOf`): a file named `salesTotals.ts` was the
    same name as `SalesTotals.tsx` on Windows, and the imports picked the wrong one;
  - the attention tiles sit side by side only from 896 px of content (at 672 px their words wrapped onto three lines
    on a tablet); below that, one under the other;
  - "Pending refunds" is green when none wait: a dark grey chip did not show on the dark card;
  - the banner also links to Stores.
- Checked in Edge as admin on the real backend (read only):
  - screenshots at 1440, 1280, 820 and 390 px, light and dark; nothing scrolls sideways; page height 2,301 → 2,240 px
    at 1440 px, 3,038 → 2,972 px at 820 px, 3,372 → 3,853 px on a phone (the ring and the tiles one under the other);
  - the rows rise in and the numbers end on the real values; a card lifts 4 px on hover and opens its page; a click
    anywhere on a latest order's row opens the order; "30 days" changes the URL and the chart; the current page in the
    menu is the green pill (`aria-current="page"` as before); the focus ring shows on the cards;
  - with "reduce motion": no animation, the numbers at once;
  - no page errors.
- Tests (+4): `useCountUp.test.ts` (counts up, counts from the old to a new number, at once without motion);
  `admin.test.tsx` (today's cards link to orders, reports and payments; the platform card's active customers). The
  dashboard's other tests and the vendor and chart tests pass unchanged. 549 tests (`--maxWorkers=4`), type check, lint
  and build pass.

## 51. One design system for all five panels (O17)

**The request (2026-10-29):** take the redesigned admin dashboard's style (modern, colourful, animated, with
illustrations) to every page of the admin, super admin, customer, vendor and delivery partner panels, so they feel like
one application: menus, headers, buttons, forms, tables, cards, empty states, loading and hover feedback; responsive and
accessible. Same stack, no new dependencies, nothing removed, simple code.

**What there is:** about 60 pages. They share the menus (`PanelSidebar`, the account menu), the top bar, buttons
(`buttonClasses`), fields, tabs (`StatusTabs`), pagination, the number tiles (`KpiTile`) and the empty, error and loading
states, but no page header or table component: each page writes its own `<h1>` and `<table>` in the same pattern. The
admin dashboard (O16) has the new look; the vendor and delivery start pages and the account pages still have a plain
heading and plain tiles; the white menus mark the current page with a thin bar; nothing on the other pages moves.

**Approach:** change the shared parts, so every page that uses them changes the same way, plus each panel's start page.
No page loses a feature; the texts, labels and links the tests use stay.

**O17:**
- **Hero banner for every panel** (`components/panel/PanelHero.tsx`, from O16's `WelcomeBanner`): date chip with a live
  dot, a greeting with a waving hand, quick links, and the panel's floating illustration, in the panel's own look (the
  same as its login screen, `AUTH_LOOKS`): admin dark, vendor orange, delivery blue, customer green. Used by the admin
  dashboard, the vendor dashboard, the delivery panel's "Today" and (compact) every account page.
- **Number tiles:** `KpiTile` gets O16's card look (a gradient icon chip in the tile's colour, a soft glow, optional link
  with a lift on hover); the admin's `StatCard` becomes `KpiTile` + `useCountUp`. The totals rows, the vendor's work
  tiles, the delivery numbers and the commission page take colours.
- **Menus:** the current page is the green pill with a glow in every panel (the white menus too) and in the account
  menu; links slide a little on hover; the store and zone boxes get the gradient icon chip.
- **Every page comes in:** a page rises and fades in when it opens (`PageEnter` in the four layouts).
- **Empty and error states:** the emoji sits in a soft gradient circle that floats, with two sparkles; errors in a warm
  circle.
- **Loading:** skeletons shimmer instead of pulsing; the spinner has a soft glow.
- **Buttons:** a coloured shadow on the green ones, slightly rounder corners, a small press-down when clicked.
- **Tabs, pagination, search:** the chosen tab and page glow like the menu; the search box gets the fields' focus ring.
- **Tables:** a row lights up under the mouse (one rule in `styles/index.css`).
- All movement is `motion-safe` ("reduce motion" turns it off); colours never carry meaning alone.

| Phase | Builds | Done when |
|---|---|---|
| **O17 One design system** | hero, tiles, menus, page entrance, empty/error/loading states, buttons, tabs, tables, start pages, tests, docs | the five panels look like one app on a phone, a tablet and a laptop, light and dark; every page still works (links, forms, filters, tables, charts); all tests pass |

**Progress (2026-10-29):** O17 ✅
- Changes beyond the design above:
  - `StatCard` moved to `components/sales/StatCard.tsx` and is now `KpiTile` + `useCountUp` (one card design);
  - the vendor's work tiles and low stock count up and link to their queue tab or filter; the delivery numbers link
    to history, earnings and profile; the commission tiles got icons;
  - the delivery panel's bottom bar (phones) marks its current page with the same green pill;
  - `EmptyState` gives an emoji the floating illustration (`EmojiIllustration`, also used by `ErrorState`); an icon
    element of its own (the delivery panel's pulsing bike) stays as it is;
  - the table-row rule leaves out the date picker's grid (`role="grid"`).
- Checked in Edge on the real backend (read only), before and after, desktop (1440 px) and phone (390 px): the super
  admin's dashboard, stores and settings; vendor1's dashboard, products and orders; delivery1's Today and history;
  customer1's account, orders and wishlist; dark theme for all but delivery (each visit to the delivery panel keeps
  the partner online for longer). Nothing scrolls sideways; no page errors.
- Interactions, after the change: the vendor's "New orders" tile → `/vendor/orders`; the hero's "Products" link →
  `/vendor/products`; the product search → `?q=milk`; a table row lights up under the mouse; the menu's current page is
  the pill with `aria-current="page"`; the admin's "Active" tab → `?tab=active` with 2 stores, and the store search
  keeps the tab (`?tab=active&q=Fresh`); the owner page `/admin/settings`; the account menu → `/account/addresses` with
  "Addresses" as the hero's title; every page rises in, and with "reduce motion" nothing moves.
- Tests (+5, `components/panel/panelHero.test.tsx`): the hero's title (without the hand), quick links and date chip;
  its own chip and no links; a tile with a link and the label before the value; a plain tile; the empty state's emoji
  illustration and an icon element of its own. All earlier tests pass unchanged: 554 tests (`--maxWorkers=4`), type
  check, lint and build pass.

## 52. Seven admin pages with illustrations (O18)

**The request (2026-10-29):** redesign the admin's Deliveries, Payments, Stores, Partners, Zones, Banners and
Notifications pages: modern, colourful, interactive and easy to use, with real SVG illustrations (not only emojis),
gentle animated backgrounds, summary cards, clearer status indicators, better tables, filters and actions; the same style
on all seven; responsive. Every API call, query parameter (`?tab=all` …), action, form and pagination stays; no made-up
numbers.

**What there is:** each page is a plain `<h1>` (and a line of text), the shared tabs or filters, then a white table or
cards with solid-colour badges; the empty states show an emoji. Payments already has its money tiles and a ring.

**O18:**
- **Shared parts** (so the seven pages look alike, and other pages can use them later):
  - `PageHeader` (`components/panel`): a tinted card with two slowly drifting lights and a dot grid, an icon chip, the
    `<h1>`, the line of text, the page's actions (e.g. "New zone"), and the page's illustration on the right (floating).
  - `StatusBadge` (`components/ui`): a soft pill in the status colour with a dot (pulsing for work that waits) or an
    icon, and the word; the same tones as the badges, so the existing tone maps are kept. Never colour alone.
  - `FilterBar` (`components/panel`): the search boxes and drop-downs in one card row with a "Filter" label.
  - Summary cards are `KpiTile` / `StatCard` (O16/O17), filled only with real numbers: the existing counts
    (`useDeliveryCount`, `useStoreCount`, `usePartnerCount`, one-line pages of the lists) or the data already on the
    page (zones, banners). A card that stands for a tab or a filter is a link to it.
- **Illustrations** (`features/admin/illustrations`, inline SVG in the theme's colours, decoration only, motion-safe):
  payments (card, coins, receipt), partners (ID card with a helmet), zones (folded map with pins and a route), banners (a
  browser window with a slide), notifications (a bell that swings, an envelope). Deliveries and stores reuse the rider and
  the storefront of the login screens. The empty states show the page's illustration instead of an emoji.
- **Per page:**
  - Deliveries: cards for waiting, on the way (assigned to on the way), delivered and failed; the order with a bike icon;
    `StatusBadge`; the store → customer route with pin icons; the cash column as a pill.
  - Payments: the money tiles as coloured cards; `StatusBadge` with an icon per kind (paid ✓, waiting ◷, failed ✕,
    refunded ↺); the method with a card or cash icon; filters in the `FilterBar`.
  - Stores: cards per status (links to the tabs, `?tab=all` kept); the store with a storefront icon; `StatusBadge`.
  - Partners: cards per KYC status; the partner with initials and an online dot (green, pulsing) or grey offline;
    `StatusBadge` for the KYC; the vehicle with an icon.
  - Zones: cards for zones, zones in use, PIN codes and the average payout; each zone card with a map-pin chip, its
    PIN codes as chips and the payout highlighted. The form and "Change" work as before.
  - Banners: cards for live, scheduled and switched-off banners; each banner card with the preview on top, the state
    over it, the period and the link with icons.
  - Notifications: cards for all, emails sent, emails failed and no email (links to that filter); each message with
    an icon for its kind, a read / unread dot, and `StatusBadge` for the email.
- Tests and texts: the table captions, the tab and button names, the tiles' "label then value" and the lists the tests
  use stay; tests for the new parts.

| Phase | Builds | Done when |
|---|---|---|
| **O18 Seven admin pages** | shared header, status badge, filter bar, five illustrations, the seven pages, tests, docs | the seven pages look modern and alike on a phone, tablet and laptop, light and dark; every tab, filter, search, pagination, form and action works as before; all tests pass |

**Progress (2026-10-29):** O18 ✅
- Also new: `components/panel/tableClasses.ts` (the card, header row and cells of the list tables, written once) and
  `PAYMENT_STATUS_ICONS` next to the payment labels and tones. The banner preview (also in the banner form) got the
  slider's gradient, and a leaf icon instead of the 🥬 emoji when there is no picture. Deliveries show the store → customer
  route in one column; partners have a "Now" column (online / offline); notifications a "Seen" column (read / not read
  yet), from the data's own `online` and `read`.
- Checked in Edge as the super admin on the real backend (read only): the seven pages at 1440, 820 and 390 px light,
  and 1440 px dark; nothing scrolls sideways (the tables scroll inside their card); no page errors.
- Interactions: "Delivered" card → `?tab=delivered` with that tab chosen; payments "Failed" → `?status=FAILED`, 1 row
  (#1), and the period keeps the filter (`?status=FAILED&days=30`); `/admin/stores?tab=all` → the "All" tab, 2 rows;
  the "Active" card, then a search → `?tab=active&q=Green`, 1 row; partners "Approved" card → `?tab=approved`; "New zone",
  "Change zone", "New banner" and "Change the banner" open their forms and "Cancel" closes them (nothing saved);
  "Emails sent" → `?email=SENT`, and the type filter changes the cards' numbers; with "reduce motion" the header's
  lights stand still. (A first run counted rows before the dimmed list had updated; checked again with waits.)
- Tests (+9): `adminPages.test.tsx` (the store, delivery, partner and notification cards open their tab or filter with
  real counts, `?tab=all` stays, online/offline and read/not read in words, the zone and banner numbers from the page's
  data), `panelHero.test.tsx` (`PageHeader`, `StatusBadge`, `FilterBar`). All earlier tests pass unchanged: 563 tests
  (`--maxWorkers=4`), type check, lint and build pass.
- **Follow-up (2026-10-29): "Every method and status" on Payments** (`payments/PaymentLinesCard.tsx`). The card beside
  the ring is still its table view (caption "Payments by method and status"), now grouped by method: each group starts
  with the method's card or cash icon and its subtotal; each line has the status as a `StatusBadge` with its icon, the
  number and the amount with a thin bar (next to the biggest line's; the amount is written out); a total row "All
  methods" closes it. Both cards of the row have the same height. Found on a phone: the table's minimum width pushed the
  page sideways; the card is now `min-w-0` (the table scrolls inside it) and the minimum is 18rem. Checked in Edge on
  the real backend at 1440 and 390 px, light and dark; +1 test (`adminPages.test.tsx`: the method groups, a status in
  words, the total row last). 564 tests, type check, lint and build pass.

## 53. A gradient background with glass panels (O19)

**The request (2026-10-30):** change the background colour of the entire application to a gradient with a glass effect.

**What there is:** one flat page colour (`--color-page`, set on `body` and again on the panel layouts' frames and the
login screens) with white cards (`bg-card`) on it; the shop's header, menu bar and footer are white; the panels' top bar
is white at 90 % with a blur.

**O19** (only `styles/index.css` and the frames; no page changes):
- **The gradient:** `body` gets four large, soft colour glows over the page colour (mint top left, sky top right, peach
  bottom right, lilac bottom left; deep green, blue, amber and plum in the dark theme), fixed to the window so it does
  not scroll. Tokens `--color-glow-1` … `--color-glow-4`. The layouts and the login screens no longer paint their own
  flat colour over it.
- **Glass cards:** every big rounded box in the card colour (`bg-card` with `rounded-2xl` or `rounded-3xl`: the cards,
  tiles, tables, forms) is a see-through pane (`--color-glass`) with a light top edge, so the gradient shows through.
  Boxes that float over the page (menus, drop-downs, pop-ups and dialogs: absolute, fixed or with a dialog, listbox or
  menu role) stay solid, so text behind them never shows through. One rule at the end of `index.css`.
- **No blur on the cards on purpose:** a `backdrop-filter` makes each box its own layer, and a drop-down opening out of
  one card (a filter over the table below) would slide under the next card. Over the soft gradient the see-through cards
  look like glass without it.
- **Frosted bars:** the shop's sticky header and the panels' top bar blur what scrolls under them (`glass-bar`: the blur
  sits on a layer behind the bar's content, not on the bar itself, so the phone menu inside the header, which covers the
  whole screen, keeps working). The shop's menu bar, the footer and the vendor's and delivery partner's menus are glass
  (`bg-glass`). The admin's dark menu stays dark.
- Inputs, buttons and pills keep their solid colours (readability).

| Phase | Builds | Done when |
|---|---|---|
| **O19 Gradient and glass** | tokens, body gradient, glass rule, `glass-bar`, frames, docs | every page of the shop and the panels shows the gradient behind glass cards, light and dark; menus, drop-downs, dialogs and the phone menu still work and stay readable; all tests pass |

**Progress (2026-10-30):** O19 ✅
- A first version was too faint in the light theme (the glows hardly showed and the cards looked white); the glows are now
  a little richer and larger (they meet in the middle of the window) and the glass a little clearer (55 % white).
- The vendor's and delivery partner's phone drawers stay solid (`bg-card`): they float over the page; only the column
  beside the page is glass.
- Checked in Edge on the real backend (read only), light and dark: the shop's home (and scrolled: the header blurs
  the cards under it), the login screen, the admin dashboard, payments, banners, the vendor dashboard and the customer's
  account. Floating things: the payments status drop-down opens over the table and stays on top; a banner's delete
  dialog is on top and centred (cancelled); on a phone the shop's menu inside the frosted header still covers the whole
  height (320 × 844) and nothing scrolls sideways. No page errors.
- 564 tests (unchanged: only colours), type check, lint and build pass.

## 54. A modern, gently animated AI chat (O20)

**The request (2026-10-30):** the AI chat should look like a modern UI, with a little animation.

**What there is (O10/O11, screenshots on the mock API):** a flat green title bar with a robot icon; a grey conversation
area; white answer bubbles and solid green question bubbles that fade in; three plain example questions; a plain
question box; a green round launcher. The order cards and "Pay Now" (`ChatActionCard`, `PayNowButton`, the other
session's) sit under an answer.

**O20** (only `ChatPanel`, `ChatConversation`, `AssistantButton` and keyframes; the texts, names, roles and props stay):
- **Opening:** the panel slides in from the right (from the bottom on a phone) and is frosted glass over the page.
- **Title bar:** the brand gradient slowly drifts; the assistant's picture is a sparkle icon with a live "online" dot.
- **Conversation:** a soft green-to-blue gradient behind the messages. The customer's bubbles have the brand gradient
  and slide in from the right; the assistant's answers are white and slide in from the left.
- **Welcome:** the picture floats with a soft ring, the hand waves, and the example questions rise in one after another.
- **Typing:** three dots bounce in the brand gradient, beside "Thinking…" and the old explanation.
- **Question box:** a rounded glass box that glows when it has the focus; the send button is a gradient circle that
  grows a little on hover and presses in on click.
- **Launcher:** a soft ring pulses three times after the page opens, then stops; the icon tilts on hover.
- All movement is `motion-safe`. Kept for the tests and the other session's flows: one `<li>` per message in the
  "Chat" log (typing and errors outside it), `PayNowButton` right under the answer (its alert inside the panel) with
  `onNavigate`, the names "Shopping assistant", "Your question", "Send", "Try again", "Ask our assistant", `data-size`.

| Phase | Builds | Done when |
|---|---|---|
| **O20 Modern chat** | panel, title bar, conversation, welcome, typing, question box, launcher, keyframes, docs | the chat looks modern and moves gently in the shop and the panels, light and dark, desktop and phone; nothing moves with "reduce motion"; all tests pass |

**Progress (2026-10-30):** O20 ✅
- After a first look: the conversation's gradient is nearly solid (a see-through one let the blurred page show as muddy
  blobs in the empty part), and the title bar drifts from green to blue (green to green did not read as a gradient).
- Checked in Edge on a mock dev server of this session (port 5179, stopped afterwards), light and dark, desktop and
  phone: welcome, typing and an answer. Animations measured: launcher `ping` (3 times), panel `chat-in` (`chat-up` on
  a phone), title bar `gradient-drift`, question `bubble-mine`, answer `bubble-theirs`; all `none` with "reduce motion".
  One question gives exactly 2 items in the "Chat" log (the other session's e2e helper counts them); minimize still works.
- **Found and fixed on the way:** `useCountUp` (O16) timed the count with `performance.now()` against the animation
  frames' time; a frame's time can be a little earlier, so the first frame could show a number below zero for a moment
  (in the tests the clocks differ more, and 2 tests failed once under load). It now counts from the first frame's own
  time and never below zero; the tests passed 3 runs in a row.
- 564 tests (`--maxWorkers=4`; the chat's 53 including the other session's order and cart cards), type check, lint and
  build pass.

