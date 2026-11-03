#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Daily snapshots of the search indexes (docs/resilience.md, "Search indexes").
#
# Runs once in the "elasticsearch-setup" container (docker-compose.yml) after Elasticsearch is healthy, then stops.
# Safe to run many times: both calls replace what is there.
#   1. the snapshot repository "grocery-search": a folder in the "es-snapshots" volume (path.repo of the elasticsearch
#      service). In production: a shared filesystem or an object store (S3, GCS, Azure) every node can reach.
#   2. the snapshot lifecycle policy "daily-search": every night at 01:30 (UTC) a snapshot of products_v*,
#      categories_v* and product_stock_v* (with their aliases); the last 7 are kept, none older than 30 days.
#
# The indexes can always be rebuilt from Kafka (scripts/rebuild-product-index.ps1); a snapshot is just faster.
# Take one now, list them, restore one: scripts/search-snapshots.ps1
# ---------------------------------------------------------------------
set -euo pipefail

ES="${ELASTICSEARCH_URL:-http://elasticsearch:9200}"

# Docker creates a new volume's folder owned by root; Elasticsearch runs as user 1000 and must write there
chown 1000:0 /usr/share/elasticsearch/snapshots

put() {
  local path="$1" body="$2"
  curl -fsS -o /dev/null -X PUT "${ES}${path}" -H "Content-Type: application/json" -d "${body}"
  echo "PUT ${path}: ok"
}

put "/_snapshot/grocery-search" '{
  "type": "fs",
  "settings": { "location": "grocery-search", "compress": true }
}'

put "/_slm/policy/daily-search" '{
  "schedule": "0 30 1 * * ?",
  "name": "<search-{now/d}>",
  "repository": "grocery-search",
  "config": {
    "indices": ["products_v*", "categories_v*", "product_stock_v*"],
    "include_global_state": false,
    "ignore_unavailable": true
  },
  "retention": { "expire_after": "30d", "min_count": 1, "max_count": 7 }
}'
