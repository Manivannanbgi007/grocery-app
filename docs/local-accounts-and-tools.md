# Local accounts and tools

Everything needed to log in on a local machine: the demo accounts of every panel, the addresses of the website and the
services, and the developer tools. **Local development only**: the seed passwords are public on purpose and must never be
used anywhere else.

## Demo accounts (website and API)

Every seed account (auth-service `db/seed/R__insert_default_users.sql`) has the password **`Password@123`**. The mock API
(`npm run dev:mock`) has the same accounts.

| Panel | Log in at | Email | Who |
|---|---|---|---|
| Shop (customer) | http://localhost:5173/login | `customer1@grocery.com` | Priya Sharma: home and work address, past orders |
| Shop (customer) | http://localhost:5173/login | `customer2@grocery.com` | Rahul Verma |
| Shop (customer) | – | `customer3@grocery.com` | Anita Desai: **disabled** (shows "Your account is disabled") |
| Vendor panel | http://localhost:5173/vendor/login | `vendor1@grocery.com` | Ravi Kumar, Fresh Mart (store 1) |
| Vendor panel | http://localhost:5173/vendor/login | `vendor2@grocery.com` | Meena Iyer, Green Basket (store 2) |
| Vendor panel | http://localhost:5173/vendor/login | `vendor3@grocery.com` | Suresh Patel: no store yet (to try the store registration and approval) |
| Delivery panel | http://localhost:5173/delivery/login | `delivery1@grocery.com` | Arjun Singh, zone Indiranagar (Fresh Mart's zone), approved |
| Delivery panel | http://localhost:5173/delivery/login | `delivery2@grocery.com` | Karthik Reddy, approved |
| Admin panel | http://localhost:5173/admin/login | `admin@grocery.com` | Platform Admin |
| Admin panel + Owner pages | http://localhost:5173/admin/login | `superadmin@grocery.com` | Super Admin (admins and roles, settings, feature flags, commission, audit log, system) |

While developing (`npm run dev` and `npm run dev:mock`), every login page lists its demo accounts under the form: a click
fills in the email and the password. The production build leaves that box out.

New accounts: "Create an account" on the shop's login (`/register`, with Shop · Sell · Deliver at the top for a customer,
vendor or delivery partner account), "Become a vendor" and "Become a delivery partner" on those panels' logins; admins are
added by the super admin (Owner → Admins). The Playwright tests also leave accounts behind
(`e2e.*@example.com`, password in the test file); they are test data, not needed for anything.

Every API call goes through the gateway: log in with `POST http://localhost:8080/api/v1/auth/login`
(`{"email": "...", "password": "Password@123"}`) and send `Authorization: Bearer <accessToken>` (valid 15 minutes; `POST /api/v1/auth/refresh` with the
`refresh_token` cookie of the login gives a new one, see [authentication](authentication.md)). The
gateway allows 10 logins per minute per address (`RATE_LIMIT_LOGIN_PER_MINUTE`, 120 in the local profile) and 10 questions
per minute to the shopping assistant.

## Website and services

| What | Address |
|---|---|
| Website (dev server, real backend) | http://localhost:5173 (`npm run dev` in `fresh-grocery`) |
| Website with demo data, no backend | http://localhost:5173 (`npm run dev:mock`) |
| API gateway (the only public door) | http://localhost:8080 |
| Swagger UI of every service | http://localhost:8080/swagger-ui.html |
| Services (internal, behind the gateway) | auth 8101, customer 8102, store 8103, product 8104, product-read 8105, inventory 8106, cart 8107, order 8108, payment 8109, delivery 8110, notification 8111, report 8112, platform 8113, mcp-client (assistant) 8114, mcp-server (assistant tools) 8115 |

## Developer tools (Docker, `scripts/start-infra.ps1`)

User names and passwords of the tools are in `infrastructure/.env` (local-only values).

| Tool | Address | Login | Started by |
|---|---|---|---|
| MySQL | `localhost:3307`, or a prompt inside the container: `docker exec -it grocery-mysql mysql -uroot -p` | user `root`, password `MYSQL_ROOT_PASSWORD`; each service also has `<service>_user` / `<service>_pass` for its own database | default |
| Kafka | `localhost:9092` | none | default |
| Schema Registry | http://localhost:8081 | none | default |
| Kafka Connect (Debezium) | http://localhost:8083 | none | default |
| Elasticsearch | http://localhost:9200 | none (security off locally) | default |
| Redis | `localhost:6379` | none | default |
| Mailpit (the emails the platform sent) | http://localhost:8025 | none | default |
| Kafka UI | http://localhost:8090 | none | default |
| RedisInsight | http://localhost:5540 | none | default |
| Kibana | http://localhost:5601 | none | `-Profiles tools` |
| Prometheus | http://localhost:9090 | none | `-Profiles monitoring` |
| Grafana | http://localhost:3000 | user `admin`, password `GRAFANA_ADMIN_PASSWORD` | `-Profiles monitoring` |
| Jaeger (traces) | http://localhost:16686 | none | `-Profiles monitoring` |
| Alertmanager | http://localhost:9093 | none | `-Profiles monitoring` |
| Loki (logs, use Grafana) | http://localhost:3100 | none | `-Profiles monitoring` |
| Ollama (the assistant's language model) | http://localhost:11434 | none | `-Profiles ai` |

## Payments

payment-service runs the **mock gateway** by default (`PAYMENT_GATEWAY=mock`): the order page shows "Pay" and "Decline
(test)", no money moves. Razorpay needs test keys from a Razorpay account: put `PAYMENT_GATEWAY=razorpay`,
`RAZORPAY_MODE=test`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` and `RAZORPAY_WEBHOOK_SECRET` in `payment-service/.env`
(a copy of `.env.example`, kept out of git) and restart payment-service; the website then opens Razorpay Checkout by itself (also after the chat's Pay Now). The repository has only placeholders
(`rzp_test_replace_me`): in razorpay mode payment-service refuses to start with them, or with keys Razorpay rejects, and
says which variable to fix (`RazorpaySettingsCheck`, see the payment-service README).
