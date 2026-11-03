#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Prepares Kibana for the grocery platform: one "data view" per
# Elasticsearch index of product-read-service, so Discover shows the
# documents right away (without a data view Kibana shows nothing).
#
# Runs once in the "kibana-setup" container (docker-compose.yml, profile
# "tools") after Kibana is ready, then stops. scripts/start-infra.ps1
# waits for it. Safe to run many times: an existing data view with the
# same id is replaced ("override"). It also works before the indexes
# exist (fresh machine): Discover shows them as soon as
# product-read-service has created them.
# ---------------------------------------------------------------------
set -euo pipefail

KIBANA_URL="${KIBANA_URL:-http://kibana:5601}"

# Calls the Kibana API. Every changing request needs the kbn-xsrf header.
kibana_post() {
  local path="$1" body="$2"
  curl -fsS -o /dev/null -X POST "${KIBANA_URL}${path}" \
    -H "kbn-xsrf: true" -H "Content-Type: application/json" -d "${body}"
}

# create_data_view <id> <index> <name shown in Kibana>
# No time field on purpose: Discover then shows every document, instead of only those of the last 15 minutes.
# sourceFilters hides "_class", the Java class name Spring Data stores in every document.
create_data_view() {
  local id="$1" index="$2" name="$3"
  kibana_post "/api/data_views/data_view" "{
    \"override\": true,
    \"data_view\": {
      \"id\": \"${id}\",
      \"title\": \"${index}\",
      \"name\": \"${name}\",
      \"sourceFilters\": [{\"value\": \"_class\"}]
    }
  }"
  echo "Data view '${name}' -> index '${index}'"
}

create_data_view "products"      "products"      "Products (search index)"
create_data_view "product_stock" "product_stock" "Product stock"
create_data_view "categories"    "categories"    "Categories"

# Discover opens with the products when you have not picked a data view yet
kibana_post "/api/data_views/default" '{"data_view_id": "products", "force": true}'
echo "Default data view: products"
echo "Kibana is ready: http://localhost:5601/app/discover"
