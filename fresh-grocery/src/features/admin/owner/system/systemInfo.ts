/* What the System page says about each service, and where the monitoring tools are (local addresses, infrastructure/docker-compose.yml). */

export const SERVICE_ROLES: Record<string, string> = {
  'api-gateway': 'The single entry point: logins, routes, limits',
  'auth-service': 'Logins, accounts and roles',
  'customer-service': 'Customer profiles and addresses',
  'store-service': 'Stores, opening hours, delivery areas, commission',
  'product-service': 'Products and categories (changes)',
  'product-read-service': 'Product search (Elasticsearch)',
  'inventory-service': 'Stock and reservations',
  'cart-service': 'Carts and coupons',
  'order-service': 'Orders and invoices',
  'payment-service': 'Payments and refunds',
  'delivery-service': 'Delivery partners and deliveries',
  'notification-service': 'Bell notifications and emails',
  'report-service': 'Dashboards and reports',
  'platform-service': 'Settings, feature flags, banners, audit log',
  'mcp-client-service': 'AI shopping assistant (chat)',
  'mcp-server-service': 'Tools of the AI shopping assistant',
}

export const MONITORING_TOOLS: { name: string; url: string; use: string }[] = [
  { name: 'Grafana', url: 'http://localhost:3000', use: 'Dashboards: requests, errors and response times of every service, and their logs' },
  { name: 'Prometheus', url: 'http://localhost:9090', use: 'The raw metrics; Status → Targets shows every service' },
  { name: 'Jaeger', url: 'http://localhost:16686', use: 'Follow one request through the gateway and the services (traces)' },
  { name: 'Kafka UI', url: 'http://localhost:8090', use: 'Events, consumer lag, dead-letter topics, Debezium connectors' },
  { name: 'Kibana', url: 'http://localhost:5601', use: "The product search index (Elasticsearch) behind the shop's search" },
  { name: 'Mailpit', url: 'http://localhost:8025', use: 'The emails the platform sent' },
]
