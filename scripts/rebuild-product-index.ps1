<#
.SYNOPSIS
    Rebuilds the product search index (Elasticsearch) from scratch.

.DESCRIPTION
    Elasticsearch is only a READ copy of product_db and inventory_db (MySQL is the source of truth),
    so it can always be thrown away and rebuilt:
      1. delete the indexes behind the aliases "products", "categories" and "product_stock" (products_v1 ...)
      2. move product-read-service's Kafka position back to the start of its topics
         (cdc.product_db.* and grocery.inventory.events)
      3. ask inventory-service to publish the current stock again (if it is running)
      4. start product-read-service: it recreates the indexes (products_v1 ...) and replays every change
    The cdc.* topics are compacted (create-topics.sh), so they always contain the latest version of every row.
    grocery.inventory.events only keeps recent events, which is why step 3 republishes the current stock.

    Stop product-read-service before running this script.

.EXAMPLE
    .\scripts\rebuild-product-index.ps1
#>
param(
    [string] $ElasticsearchUrl = "http://localhost:9200",
    [string] $InventoryServiceUrl = "http://localhost:8106"
)

$consumerGroup = "product-read-service"
$topics = @("cdc.product_db.products", "cdc.product_db.categories", "grocery.inventory.events")

if (Get-NetTCPConnection -LocalPort 8105 -State Listen -ErrorAction SilentlyContinue) {
    Write-Host "product-read-service is still running on port 8105. Stop it first, then run this script again." -ForegroundColor Red
    exit 1
}

Write-Host "1. Deleting the Elasticsearch indexes..." -ForegroundColor Cyan
# "products" ... are aliases of versioned indexes ("products_v1"); Elasticsearch only deletes real indexes, so find
# them first. A plain index from before aliases is deleted by its own name.
foreach ($name in @("products", "categories", "product_stock")) {
    try {
        $indexes = @((Invoke-RestMethod -Uri "$ElasticsearchUrl/_alias/$name").PSObject.Properties.Name)
    }
    catch {
        $indexes = @($name)
    }
    foreach ($index in $indexes) {
        try {
            Invoke-RestMethod -Method Delete -Uri "$ElasticsearchUrl/$index" | Out-Null
            Write-Host "   deleted '$index'"
        }
        catch {
            Write-Host "   '$index' did not exist"
        }
    }
}

Write-Host "2. Moving the consumer group '$consumerGroup' back to the start of the change topics..." -ForegroundColor Cyan

# Kafka only allows this when the group has no running consumers. A service that was killed (not stopped
# cleanly) stays a member until its session times out (about 45 seconds), so wait for that.
$groupIsInactive = $false
for ($attempt = 1; $attempt -le 30; $attempt++) {
    $state = docker exec grocery-kafka kafka-consumer-groups --bootstrap-server localhost:9092 `
        --group $consumerGroup --describe --state 2>&1 | Out-String
    if ($state -match "Empty|Dead|does not exist") {
        $groupIsInactive = $true
        break
    }
    if ($attempt -eq 1) { Write-Host "   waiting until the consumer group is inactive (up to 60 seconds)..." }
    Start-Sleep -Seconds 2
}
if (-not $groupIsInactive) {
    Write-Host "The consumer group is still active. Is product-read-service still running somewhere?" -ForegroundColor Red
    exit 1
}

$topicArgs = @()
foreach ($topic in $topics) { $topicArgs += @("--topic", $topic) }
$result = docker exec grocery-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group $consumerGroup `
    --reset-offsets --to-earliest @topicArgs --execute 2>&1 | Out-String
Write-Host $result
if ($result -notmatch "cdc\.product_db\.products") {   # the tool prints a table of new positions on success
    Write-Host "Resetting the consumer group failed (see the message above)." -ForegroundColor Red
    exit 1
}

Write-Host "3. Asking inventory-service to publish the current stock again..." -ForegroundColor Cyan
try {
    $snapshot = Invoke-RestMethod -Method Post -Uri "$InventoryServiceUrl/internal/inventory/stock-snapshot"
    Write-Host "   published StockChanged for $($snapshot.data.publishedEvents) product(s)"
}
catch {
    Write-Host "   inventory-service is not running. Once it runs, call:" -ForegroundColor Yellow
    Write-Host "   POST $InventoryServiceUrl/internal/inventory/stock-snapshot" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "4. Now start product-read-service. It recreates the indexes and replays every change." -ForegroundColor Green
Write-Host "   Check the result with:  curl $ElasticsearchUrl/products/_count"
Write-Host "                           curl $ElasticsearchUrl/product_stock/_count"
