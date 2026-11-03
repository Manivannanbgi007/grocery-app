<#
.SYNOPSIS
    Moves the search indexes to a new version (a new mapping or settings) while search keeps working.

.DESCRIPTION
    product-read-service reads and writes the ALIASES "products", "categories" and "product_stock"; each points to a
    versioned index ("products_v1" ...). Elasticsearch cannot change a field's type in place, so a new version is built
    next to the old one. For each index this script:
      1. creates the next version ("products_v2") with the mapping and settings files of
         product-read-service/src/main/resources/elasticsearch (edit those files first);
      2. pauses product-read-service's Kafka listener: no change is written while the documents are copied
         (the changes wait in Kafka);
      3. copies every document (_reindex), keeping each document's version (version_type external), so an older change
         that arrives later can still not overwrite a newer one;
      4. compares the document counts; on a difference it deletes the new index and changes nothing;
      5. switches the alias to the new index in ONE request: searches see the old or the new index, never none;
      6. resumes the listener: the changes that waited are written to the new index.
    The old index is kept for a rollback (the command is printed); delete it when the new one is fine.

    A plain index from before aliases ("products" itself, made by an older product-read-service) is moved behind an
    alias the same way; in the same request that adds the alias, the plain index is deleted (an alias cannot have the
    name of an index), so there is no rollback copy in that case.

    product-read-service may run (search keeps working) or be stopped (then nothing writes anyway).

.EXAMPLE
    .\scripts\reindex-products.ps1                     # all three indexes
    .\scripts\reindex-products.ps1 -Index products     # only one
#>
param(
    [ValidateSet("products", "categories", "product_stock")]
    [string[]] $Index = @("categories", "products", "product_stock"),
    [string] $ElasticsearchUrl = "http://localhost:9200",
    [string] $ServiceUrl = "http://localhost:8105"
)

$ErrorActionPreference = "Stop"
$resources = Join-Path $PSScriptRoot "..\product-read-service\src\main\resources\elasticsearch"

function Invoke-Es([string] $Method, [string] $Path, [string] $Body = $null) {
    $request = @{ Method = $Method; Uri = "$ElasticsearchUrl$Path"; ContentType = "application/json; charset=utf-8" }
    if ($Body) { $request.Body = [System.Text.Encoding]::UTF8.GetBytes($Body) }
    return Invoke-RestMethod @request
}

function Test-EsPath([string] $Path) {
    try { Invoke-Es HEAD $Path | Out-Null; return $true } catch { return $false }
}

# The real index behind the alias ("products_v1"), "" for a plain index named like the alias, $null when there is none
function Get-CurrentIndex([string] $Name) {
    try {
        $aliases = Invoke-Es GET "/_alias/$Name"
        return @($aliases.PSObject.Properties.Name)[0]
    }
    catch {
        if (Test-EsPath "/$Name") { return "" }
        return $null
    }
}

function Set-ListenerPaused([bool] $Paused) {
    $action = if ($Paused) { "pause" } else { "resume" }
    try {
        $status = (Invoke-RestMethod -Method Post -Uri "$ServiceUrl/internal/catalog/indexing/$action").data
    }
    catch {
        Write-Host "   product-read-service does not answer on $ServiceUrl - not running, so nothing writes" -ForegroundColor Yellow
        return $true
    }
    if ($status.paused -ne $Paused) {
        Write-Host "   the listener did not $action (running: $($status.running), paused: $($status.paused))" -ForegroundColor Red
        return $false
    }
    Write-Host "   listener: ${action}d"
    return $true
}

$plans = @()
foreach ($name in $Index) {
    $current = Get-CurrentIndex $name
    if ($null -eq $current) {
        Write-Host "'$name' does not exist: start product-read-service, it creates it." -ForegroundColor Red
        exit 1
    }
    $version = if ($current -match "_v(\d+)$") { [int] $Matches[1] + 1 } else { 1 }
    $next = "${name}_v$version"
    if (Test-EsPath "/$next") {
        Write-Host "'$next' already exists (left over from an earlier run?). Delete it first:" -ForegroundColor Red
        Write-Host "   Invoke-RestMethod -Method Delete -Uri $ElasticsearchUrl/$next"
        exit 1
    }
    $plans += [pscustomobject] @{ Name = $name; Current = $current; Next = $next }
}

Write-Host "1. Creating the new indexes from $resources" -ForegroundColor Cyan
$settings = Get-Content -Raw (Join-Path $resources "index-settings.json")
foreach ($plan in $plans) {
    $mapping = Get-Content -Raw (Join-Path $resources "$($plan.Name)-mapping.json")
    Invoke-Es PUT "/$($plan.Next)" "{ ""settings"": $settings, ""mappings"": $mapping }" | Out-Null
    # Keep the number of replicas the old index has (0 locally, 1 or more in production)
    $source = if ($plan.Current) { $plan.Current } else { $plan.Name }
    $replicas = (Invoke-Es GET "/$source/_settings").$source.settings.index.number_of_replicas
    Invoke-Es PUT "/$($plan.Next)/_settings" "{ ""index"": { ""number_of_replicas"": $replicas } }" | Out-Null
    Write-Host "   created '$($plan.Next)' (replicas: $replicas)"
}

Write-Host "2. Pausing product-read-service's Kafka listener..." -ForegroundColor Cyan
if (-not (Set-ListenerPaused $true)) {
    foreach ($plan in $plans) { Invoke-Es DELETE "/$($plan.Next)" | Out-Null }
    exit 1
}

$failed = $false
try {
    foreach ($plan in $plans) {
        $source = if ($plan.Current) { $plan.Current } else { $plan.Name }
        Write-Host "3. Copying '$source' -> '$($plan.Next)'..." -ForegroundColor Cyan
        $copy = Invoke-Es POST "/_reindex?wait_for_completion=true&refresh=true" `
            "{ ""source"": { ""index"": ""$source"" }, ""dest"": { ""index"": ""$($plan.Next)"", ""version_type"": ""external"" } }"
        $before = (Invoke-Es GET "/$source/_count").count
        $after = (Invoke-Es GET "/$($plan.Next)/_count").count
        Write-Host "4. Documents: $before in '$source', $after in '$($plan.Next)' (copied $($copy.created), failures $(@($copy.failures).Count))"
        if ($before -ne $after -or @($copy.failures).Count -gt 0) {
            Write-Host "   The counts differ: '$($plan.Name)' is NOT switched, '$($plan.Next)' is deleted." -ForegroundColor Red
            Invoke-Es DELETE "/$($plan.Next)" | Out-Null
            $failed = $true
            continue
        }

        Write-Host "5. Switching the alias '$($plan.Name)' to '$($plan.Next)' (one request)..." -ForegroundColor Cyan
        $add = "{ ""add"": { ""index"": ""$($plan.Next)"", ""alias"": ""$($plan.Name)"", ""is_write_index"": true } }"
        $actions = if ($plan.Current) {
            "{ ""remove"": { ""index"": ""$($plan.Current)"", ""alias"": ""$($plan.Name)"" } }, $add"
        }
        else {
            "$add, { ""remove_index"": { ""index"": ""$($plan.Name)"" } }"   # the plain index from before aliases
        }
        Invoke-Es POST "/_aliases" "{ ""actions"": [ $actions ] }" | Out-Null
    }
}
finally {
    Write-Host "6. Resuming the listener: the changes that waited are applied now..." -ForegroundColor Cyan
    Set-ListenerPaused $false | Out-Null
}

Write-Host ""
foreach ($plan in $plans) {
    $now = Get-CurrentIndex $plan.Name
    Write-Host "'$($plan.Name)' -> '$now'"
    if ($plan.Current -and $now -eq $plan.Next) {
        $rollback = "{""actions"":[{""remove"":{""index"":""$($plan.Next)"",""alias"":""$($plan.Name)""}},{""add"":{""index"":""$($plan.Current)"",""alias"":""$($plan.Name)"",""is_write_index"":true}}]}"
        Write-Host "   Rollback (right away: changes made since the switch are only in '$($plan.Next)'):" -ForegroundColor DarkGray
        Write-Host "   Invoke-RestMethod -Method Post -Uri $ElasticsearchUrl/_aliases -ContentType application/json -Body '$rollback'" -ForegroundColor DarkGray
        Write-Host "   When the new index is fine: Invoke-RestMethod -Method Delete -Uri $ElasticsearchUrl/$($plan.Current)" -ForegroundColor DarkGray
    }
}
if ($failed) { exit 1 }
