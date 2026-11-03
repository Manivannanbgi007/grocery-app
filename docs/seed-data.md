# Seed Data (local profile only)

Every service loads sample data from `src/main/resources/db/seed/R__*.sql` when it runs with the `local` profile (the default).
Ids are **fixed** so services can refer to each other's data. For example, store 1 in store-service is owned by user 3 from auth-service.
Use these ids in the `.http` files.

**Password for every user: `Password@123`**

## Users (auth-service)

| Id | Name | Email | Role | Status | Store id |
|---|---|---|---|---|---|
| 1 | Super Admin | superadmin@grocery.com | SUPER_ADMIN | ACTIVE | – |
| 2 | Platform Admin | admin@grocery.com | ADMIN | ACTIVE | – |
| 3 | Ravi Kumar | vendor1@grocery.com | VENDOR | ACTIVE | 1 |
| 4 | Priya Sharma | customer1@grocery.com | CUSTOMER | ACTIVE | – |
| 5 | Arjun Singh | delivery1@grocery.com | DELIVERY_PARTNER | ACTIVE | – |
| 6 | Meena Iyer | vendor2@grocery.com | VENDOR | ACTIVE | 2 |
| 7 | Rahul Verma | customer2@grocery.com | CUSTOMER | ACTIVE | – |
| 8 | Karthik Reddy | delivery2@grocery.com | DELIVERY_PARTNER | ACTIVE | – |
| 9 | Anita Desai | customer3@grocery.com | CUSTOMER | **DISABLED** (login returns 403) | – |
| 10 | Suresh Patel | vendor3@grocery.com | VENDOR | ACTIVE | – (no store yet: use them to try store registration) |

New users get ids from 11 upwards.

## Customers (customer-service)

| Customer id | User id | Name | Addresses |
|---|---|---|---|
| 1 | 4 | Priya Sharma | 1 HOME (default, 560038), 2 WORK (560034) |
| 2 | 7 | Rahul Verma | 3 HOME (default, 560008) |
| 3 | 9 | Anita Desai | none |

Customers who register through the API get a profile from the `UserRegistered` event.

## Stores (store-service)

| Store id | Name | Owner (user id) | Status | Hours | Delivers to |
|---|---|---|---|---|---|
| 1 | Fresh Mart (Indiranagar, Bengaluru) | 3 (vendor1) | ACTIVE, 10 % commission | 07:00–22:00 daily | 560038, 560008, 560071 |
| 2 | Green Basket (Koramangala, Bengaluru) | 6 (vendor2) | ACTIVE, 8.5 % commission | 08:00–21:00, Sunday 09:00–14:00 | 560034, 560095, 560047 |

The next registered store gets id 3.

## Catalog (product-service; copied to Elasticsearch by product-read-service)

| Category id | Name | Slug |
|---|---|---|
| 1 | Vegetables | vegetables |
| 2 | Fruits | fruits |
| 3 | Dairy & Eggs | dairy-eggs |
| 4 | Snacks & Beverages | snacks-beverages |
| 5 | Household | household |

| Product ids | Store | Products |
|---|---|---|
| 1–10 | 1 Fresh Mart | Tomato (36.00 after 10 % off), Onion, Potato, Banana Robusta, Apple Shimla, Amul Taaza Toned Milk, Amul Butter, Farm Eggs, Lay's Classic Salted, Surf Excel Matic Liquid |
| 17 | 1 Fresh Mart | Mango Alphonso: **INACTIVE** (not visible to customers) |
| 11–16 | 2 Green Basket | Organic Spinach, Organic Carrot, Pomegranate, Nandini Curd, Haldiram's Aloo Bhujia, Vim Dishwash Liquid |

### Demo catalog for the storefront (Fresh Grocery)

A second, **generated** seed file adds a fuller shop for the storefront, so the website looks the same with the real backend
as with its mock API: `product-service/.../db/seed/R__insert_demo_catalog.sql` and
`inventory-service/.../db/seed/R__insert_demo_stock.sql`. Do not edit them by hand: change the demo data in
`fresh-grocery/src/mocks/data/` and run `npm run seed:sql` in `fresh-grocery` (a frontend test fails when they are out of date).

| Ids | What |
|---|---|
| Categories 21–26 | Breakfast & Bakery, Tea & Coffee, Meat & Seafood, Rice, Atta & Dals, Frozen Foods, Personal Care |
| Categories 27–36 | Masala & Spices, Oils & Ghee, Dry Fruits & Nuts, Sauces & Pickles, Noodles & Pasta, Chocolates & Sweets, Baby Care, Pet Care, Health & Wellness, Pooja Needs |
| Products 101–143 | 43 products of both stores (SKUs end in the product id, e.g. `FM-VEG-101`), with stock (some low, 129 Bru Instant Coffee out of stock) |
| Products 201–250 | 5 products in each of the categories 27–36, from both stores (e.g. `FM-SPC-201` MDH Garam Masala, `GB-PET-238` Drools Puppy), with stock (205, 212, 230, 245 low; 219 Ching's Schezwan Chutney out of stock) |
| Reviews 101–143 | Reviews by customer1 and customer2 (demo order ids 9001/9002/9101/9102, which have no real order); the file then sets every product's `rating_average` / `rating_count` from the visible reviews |

The demo products start again at **201** (not 144) because a database that is already in use has products created
through the API from 144 upwards, and `INSERT IGNORE` would silently skip a seed row whose id exists. New products
created through the API get ids from **251** upwards (MySQL continues after the highest id; SKU prefix `FM-` for
Fresh Mart, `GB-` for Green Basket); new categories from **37**.

## Stock (inventory-service)

Every seed product has a stock record with a low-stock threshold of **5** and nothing reserved.

| Product | Units | | Product | Units | |
|---|---|---|---|---|---|
| 1 Tomato | 120 | | 11 Organic Spinach | 20 | |
| 2 Onion | 200 | | 12 Organic Carrot | 35 | |
| 3 Potato | 150 | | 13 Pomegranate | 0 | **out of stock** |
| 4 Banana Robusta | 40 | | 14 Nandini Curd | 4 | **low** |
| 5 Apple Shimla | 25 | | 15 Haldiram's Aloo Bhujia | 50 | |
| 6 Amul Taaza Toned Milk | 60 | | 16 Vim Dishwash Liquid | 18 | |
| 7 Amul Butter | 3 | **low** | | | |
| 8 Farm Eggs | 30 | | | | |
| 9 Lay's Classic Salted | 80 | | | | |
| 10 Surf Excel Matic Liquid | 12 | | | | |
| 17 Mango Alphonso | 0 | **out of stock** (and INACTIVE) | | | |

The demo products 101–143 and 201–250 have their own stock rows (see the demo catalog above).

The seed rows are inserted by SQL, so no event exists for them. Once after the first start, publish them so search shows the stock:
`Invoke-RestMethod -Method Post http://localhost:8106/internal/inventory/stock-snapshot`

## Coupons and carts (cart-service)

| Id | Code | Store | Discount | Minimum items total | Notes |
|---|---|---|---|---|---|
| 1 | WELCOME50 | platform | 50.00 off | 299.00 | once per customer, 1000 uses in total |
| 2 | SAVE10 | platform | 10 % (max 100.00) | 199.00 | 5 times per customer |
| 3 | FRESH20 | 1 Fresh Mart | 20 % (max 60.00) | 249.00 | twice per customer |
| 4 | GREEN30 | 2 Green Basket | 30.00 off | 199.00 | |
| 5 | SUMMER25 | platform | 25 % | – | **expired** (ended 2026-06-30) |
| 6 | MANGO15 | 1 Fresh Mart | 15 % | – | **switched off** by the vendor |

Delivery costs 30.00 below an items total of 299.00; the minimum order is 99.00.

customer1 (user 4) has a Fresh Mart cart: Tomato × 2, Amul Taaza Toned Milk × 3, Farm Eggs × 1 = **201.00** (+ 30.00 delivery = 231.00). The other customers start with an empty cart.

## Delivery zones and partners (delivery_db)

| Zone | PIN codes | Payout per delivery | Partners |
|---|---|---|---|
| 1 Indiranagar | 560038, 560008, 560071 | 40.00 | delivery1 (user 5, Arjun Singh, SCOOTER), KYC APPROVED |
| 2 Koramangala | 560034, 560095, 560047 | 40.00 | delivery2 (user 8, Karthik Reddy, MOTORBIKE), KYC APPROVED |

A delivery belongs to the zone that covers the **store's** PIN code: Fresh Mart (560038) → zone 1, Green Basket (560034) → zone 2.
Nobody is online after a start: a partner calls `POST /api/v1/delivery/availability/online` first (it lasts 30 minutes).
New delivery partners register through auth-service (`"role": "DELIVERY_PARTNER"`) and start with KYC `NOT_SUBMITTED`.

## Platform settings, feature flags and banners (platform_db)

Settings (in every environment, migration V1): currency INR, delivery fee 30.00, free delivery from 299.00, minimum order 99.00,
support support@grocery.com / 1800-123-4567. cart-service keeps a copy in `cart_db.pricing_settings` (same values; updated by
`PlatformSettingsChanged`).

| Feature flag | Enabled |
|---|---|
| DELIVERY_SLOTS | no |
| CITY_HYDERABAD | no |
| PRODUCT_REVIEWS | yes |
| RAZORPAY_PAYMENTS | no |

Banners (local only): 1 "Fresh vegetables, delivered today", 2 "WELCOME50 ...", 3 "Diwali sale" (ended 2025, so it is not shown).

## Report facts (report_db, local only)

The seed users, stores and partners have no events, so `report-service/src/main/resources/db/seed/R__insert_seed_facts.sql`
adds them (10 users, stores 1 and 2 ACTIVE, partners 5 and 8 APPROVED). Orders, payments and stock only arrive as events.

## Sample delivered orders (scripts\create-sample-orders.ps1, local only)

There are no seed orders (see below), so this script makes **24 delivered orders** for testing every panel by hand:
the admin's orders, deliveries and dashboard, the customers' order history, the vendors' orders and the delivery
partners' completed deliveries and earnings.

```powershell
.\scripts\create-sample-orders.ps1                # the 24 sample orders, then the checks (about 15 minutes)
.\scripts\create-sample-orders.ps1 -Count 3       # only sample orders 1-3 (a quick try)
.\scripts\create-sample-orders.ps1 -VerifyOnly    # only the checks, nothing is created
```

**How:** through the API gateway, the way the website does it, so every service stores the order its own way (order,
payment, stock, delivery, notifications, reports) and nothing is written to a database by hand:
1. The customer fills the cart with products of one store, from 2-5 of the categories Vegetables, Fruits, Dairy &
   Eggs, Snacks & Beverages and Household, 1-3 packs each. The customer then orders: cash on delivery, or online,
   paid with the built-in **mock** gateway (`PAYMENT_GATEWAY=mock`, the default; no real payment provider is called).
   About a third of the orders use the store's coupon (FRESH20 or GREEN30) when the items reach its minimum.
2. The store's vendor accepts, packs and hands it over.
3. Only the chosen partner of the store's zone is online; they get the delivery, pick it up and deliver it.

The services calculate the prices, the tax (included in the prices), the coupon discount, the delivery fee and the
totals, as for any order. It refuses a gateway that is not `localhost`.

| Accounts | Who |
|---|---|
| Customers | customer1 and customer2, and 4 sample customers: Kavya Nair (`sample.kavya@example.com`, Domlur and Koramangala), Vikram Joshi (`sample.vikram@example.com`, Koramangala and Indiranagar), Sneha Rao (`sample.sneha@example.com`, Viveknagar), Mohammed Irfan (`sample.irfan@example.com`, Halasuru) |
| Delivery partners | delivery1 and Deepak Gowda (`sample.deepak@example.com`, MOTORBIKE) in zone 1; delivery2 and Lakshmi Prasad (`sample.lakshmi@example.com`, SCOOTER) in zone 2 |

The sample accounts are registered the first time, with the password `Password@123` and phone numbers 98765010xx and
98765020xx; the admin approves the partners' KYC. Odd sample orders are Fresh Mart orders, even ones Green Basket
orders, and the store's customers take turns.

**Running it again is safe:** every sample order has the note "Sample order NN (test data)" (visible in every panel)
and the Idempotency-Key `sample-order-NN`. An order that exists is skipped, and one that was interrupted is finished.

**The checks at the end** (also `-VerifyOnly`), for every sample order:
- it is DELIVERED and paid (COD: payment COLLECTED, online: PAID, and the partner collected the total or nothing);
- each line is quantity x price, the items total is the sum of the lines, the delivery fee is 30.00 below 299.00,
  and total = items - coupon + delivery = the payment's amount;
- it is in the admin's delivered orders and deliveries, the customer's history, the vendor's orders and the partner's
  completed deliveries;
- the admin's orders give the right number of pages at 10 per page, and the dashboard counts at least the sample orders.

**Side effects, the same as for real orders:**
- The customers, vendors and partners get in-app notifications, about 8 per order.
- Emails go to the local Mailpit only (the sample accounts use `@example.com`, which never receives mail).
- The stock goes down: a product is never taken below 10 units, so the shop still shows it in stock.
- customer1's and customer2's carts and every partner's online status are put back at the end.
- The order times are the real times of the run (today), so the dashboards show the sample orders under today, and
  the average delivery time is short (each order is delivered within a minute).

To remove the sample orders again, use the clean-up below: it removes all orders.

## Getting back to a clean state

Running the `.http` files changes data (passwords, statuses, new users). To reset one service's database:

```powershell
docker exec grocery-mysql mysql -uroot -proot -e "DROP DATABASE store_db; CREATE DATABASE store_db;"
# then restart the service: Flyway recreates the tables and the seed data
```

The Debezium connectors keep running through such a reset.

**Careful: ids start again at 1, but Kafka still has the old events.** Services that build data from events
(notification-service's contact book and notifications, delivery-service's deliveries, report data later) then mix old
and new records that share an id. For example, a new user 11 gets the welcome message of the old user 11, or a new order 2 is never delivered
because delivery-service already has a delivery for the old order 2. The same happens after
`ALTER TABLE ... AUTO_INCREMENT = 1`. When you need a really clean state, reset everything (see the end of this section).

The contact book of notification-service has the 10 seed users and the 2 seed stores (`R__insert_default_contacts.sql`),
because their SQL rows never produced UserRegistered / StoreApproved events.

**product_db is different:** Elasticsearch copies it, and dropping a database does not produce delete events. So remove only the rows you added; the deletes then flow to Elasticsearch too:

```powershell
docker exec grocery-mysql mysql -uroot -proot -e "DELETE FROM product_db.products WHERE id > 17; DELETE FROM product_db.categories WHERE id > 5;"
```

**inventory_db is the same:** its stock versions are copied to Elasticsearch, so do not drop it. Put the seed numbers back through the API (requests 15 and 16 of `inventory-service/http/inventory-service.http` do that for the products they change) and delete the stock of test products:

```powershell
docker exec grocery-mysql mysql -uroot -proot -e "DELETE FROM inventory_db.stock_items WHERE product_id > 17;"
```

There are **no seed orders or payments**: they come from placing orders (e2e\phase7-order-saga.http, or the sample
orders above).
To remove test orders and put the stock back, delete the rows (do not drop inventory_db, see above) and set the stock again through the vendor API:

```powershell
docker exec grocery-mysql mysql -uroot -proot -e "DELETE FROM order_db.order_status_history; DELETE FROM order_db.order_items; DELETE FROM order_db.orders; DELETE FROM payment_db.payments; DELETE FROM inventory_db.stock_reservation_items; DELETE FROM inventory_db.stock_reservations; DELETE FROM cart_db.coupon_redemptions; UPDATE cart_db.coupons SET used_count = 0;"
```

cart_db can be dropped like store_db. To keep it and only remove test data:
`docker exec grocery-mysql mysql -uroot -proot -e "DELETE FROM cart_db.coupons WHERE id > 6; DELETE FROM cart_db.carts WHERE customer_user_id <> 4;"`

To reset **everything** (including Kafka, Elasticsearch and the connectors), run `.\scripts\reset-infra.ps1`, then start the services and run `.\scripts\register-connectors.ps1` again.
