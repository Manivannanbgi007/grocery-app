<#
.SYNOPSIS
    Lists, takes and restores snapshots of the search indexes (Elasticsearch).

.DESCRIPTION
    Every night at 01:30 (UTC) Elasticsearch takes a snapshot of products_v*, categories_v* and product_stock_v*
    (policy "daily-search", repository "grocery-search": infrastructure/elasticsearch/setup-snapshots.sh), keeping 7.

    -List      (default) the snapshots, newest first
    -Create    a snapshot now (e.g. before a risky change)
    -Restore   puts a snapshot back (the newest, or -Snapshot <name>). Stop product-read-service first. Steps:
                 1. delete the current indexes behind the aliases
                 2. restore the snapshot's indexes and aliases
                 3. move product-read-service's Kafka position back to the snapshot's time: on its next start it
                    applies every change since then again. That is safe: each write carries the row's version, so a
                    change that is already in the index is simply skipped (EXTERNAL_GTE)
                 4. ask inventory-service to publish the current stock again (stock events are only kept for days)
               Then start product-read-service.

    A snapshot is only faster: the indexes can always be rebuilt from Kafka (scripts/rebuild-product-index.ps1).

.EXAMPLE
    .\scripts\search-snapshots.ps1
    .\scripts\search-snapshots.ps1 -Create
    .\scripts\search-snapshots.ps1 -Restore
    .\scripts\search-snapshots.ps1 -Restore -Snapshot search-2026.10.28-abc123
#>
param(
    [switch] $Create,
    [switch] $Restore,
    [string] $Snapshot,
    [string] $ElasticsearchUrl = "http://localhost:9200",
    [string] $InventoryServiceUrl = "http://localhost:8106"
)

$ErrorActionPreference = "Stop"
$repository = "grocery-search"
$consumerGroup = "product-read-service"
$topics = @("cdc.product_db.products", "cdc.product_db.categories", "grocery.inventory.events")

# Runs Kafka's consumer-group tool. It writes notes to stderr; with "Stop" PowerShell 5.1 would treat them as errors
function Invoke-ConsumerGroups {
    $ErrorActionPreference = "Continue"
    docker exec grocery-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group $consumerGroup @args 2>&1 | Out-String
}

function Get-Snapshots {
    try {
        $result = Invoke-RestMethod -Uri "$ElasticsearchUrl/_snapshot/$repository/_all?sort=start_time&order=desc"
    }
    catch {
        Write-Host "No snapshot repository '$repository'. Start the infrastructure (scripts\start-infra.ps1): its" -ForegroundColor Red
        Write-Host "elasticsearch-setup container creates it." -ForegroundColor Red
        exit 1
    }
    return @($result.snapshots)
}

if ($Create) {
    Write-Host "Taking a snapshot now (policy 'daily-search')..." -ForegroundColor Cyan
    $name = (Invoke-RestMethod -Method Post -Uri "$ElasticsearchUrl/_slm/policy/daily-search/_execute").snapshot_name
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        try { $state = (Invoke-RestMethod -Uri "$ElasticsearchUrl/_snapshot/$repository/$name").snapshots[0].state }
        catch { $state = "STARTING" }   # 404 for a moment: the snapshot is taken in the background
        if ($state -ne "IN_PROGRESS" -and $state -ne "STARTING") { break }
    }
    Write-Host "'$name': $state"
    if ($state -ne "SUCCESS") { exit 1 }
    exit 0
}

if (-not $Restore) {
    $snapshots = Get-Snapshots
    if ($snapshots.Count -eq 0) { Write-Host "No snapshots yet. Take one with: .\scripts\search-snapshots.ps1 -Create" }
    foreach ($s in $snapshots) {
        $indexes = ($s.indices | Sort-Object) -join ", "
        Write-Host ("{0,-40} {1,-8} {2}  {3}" -f $s.snapshot, $s.state, $s.start_time, $indexes)
    }
    exit 0
}

# ----- restore -----
if (Get-NetTCPConnection -LocalPort 8105 -State Listen -ErrorAction SilentlyContinue) {
    Write-Host "product-read-service is still running on port 8105. Stop it first, then run this script again." -ForegroundColor Red
    exit 1
}

$snapshots = @(Get-Snapshots | Where-Object { $_.state -eq "SUCCESS" })
$chosen = if ($Snapshot) { $snapshots | Where-Object { $_.snapshot -eq $Snapshot } } else { $snapshots | Select-Object -First 1 }
if (-not $chosen) {
    Write-Host "No successful snapshot '$Snapshot' found. See the list: .\scripts\search-snapshots.ps1" -ForegroundColor Red
    exit 1
}
Write-Host "Restoring '$($chosen.snapshot)' taken at $($chosen.start_time) (UTC)" -ForegroundColor Cyan

Write-Host "1. Deleting the current indexes..." -ForegroundColor Cyan
foreach ($name in @("products", "categories", "product_stock")) {
    try { $indexes = @((Invoke-RestMethod -Uri "$ElasticsearchUrl/_alias/$name").PSObject.Properties.Name) }
    catch { $indexes = @($name) }   # a plain index from before aliases, or none
    foreach ($index in $indexes) {
        try { Invoke-RestMethod -Method Delete -Uri "$ElasticsearchUrl/$index" | Out-Null; Write-Host "   deleted '$index'" }
        catch { }
    }
}

Write-Host "2. Restoring the indexes and their aliases..." -ForegroundColor Cyan
$body = '{ "indices": "products_v*,categories_v*,product_stock_v*", "include_aliases": true, "include_global_state": false }'
$restored = Invoke-RestMethod -Method Post -ContentType "application/json" -Body $body `
    -Uri "$ElasticsearchUrl/_snapshot/$repository/$($chosen.snapshot)/_restore?wait_for_completion=true"
Write-Host "   restored: $(($restored.snapshot.indices | Sort-Object) -join ', ')"

Write-Host "3. Moving the consumer group '$consumerGroup' back to $($chosen.start_time)..." -ForegroundColor Cyan
# Kafka only allows this when the group has no running consumers (a killed service stays a member ~45 s)
$groupIsInactive = $false
for ($attempt = 1; $attempt -le 30; $attempt++) {
    $state = Invoke-ConsumerGroups --describe --state
    if ($state -match "Empty|Dead|does not exist") { $groupIsInactive = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $groupIsInactive) {
    Write-Host "The consumer group is still active. Is product-read-service still running somewhere?" -ForegroundColor Red
    exit 1
}
# One minute earlier than the snapshot started: a change applied while it was being taken is applied again (harmless)
$from = ([datetime]::Parse($chosen.start_time).ToUniversalTime().AddMinutes(-1)).ToString("yyyy-MM-ddTHH:mm:ss.fff") + "Z"
$topicArgs = @()
foreach ($topic in $topics) { $topicArgs += @("--topic", $topic) }
$result = Invoke-ConsumerGroups --reset-offsets --to-datetime $from @topicArgs --execute
Write-Host $result
if ($result -notmatch "cdc\.product_db\.products") {
    Write-Host "Moving the consumer group failed (see above). Start product-read-service anyway: the indexes are back," -ForegroundColor Red
    Write-Host "but changes since the snapshot are missing until scripts\rebuild-product-index.ps1 is run." -ForegroundColor Red
    exit 1
}

Write-Host "4. Asking inventory-service to publish the current stock again..." -ForegroundColor Cyan
try {
    $published = Invoke-RestMethod -Method Post -Uri "$InventoryServiceUrl/internal/inventory/stock-snapshot"
    Write-Host "   published StockChanged for $($published.data.publishedEvents) product(s)"
}
catch {
    Write-Host "   inventory-service is not running. Once it runs: POST $InventoryServiceUrl/internal/inventory/stock-snapshot" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Now start product-read-service: it applies the changes since $from again." -ForegroundColor Green
