<#
.SYNOPSIS
    Builds the Docker image of every service and of the frontend (grocery/<folder>:0.0.1).

.DESCRIPTION
    Each service folder has its own multi-stage Dockerfile (Maven build inside Docker, then a small JRE image);
    fresh-grocery's Dockerfile builds the shop with Node and serves it with NGINX.
    Run ".\mvnw verify" in a service before building it: the Dockerfile skips the tests.
    With -KindCluster the images are also loaded into one or more local kind clusters (no registry needed).

.EXAMPLE
    .\scripts\build-images.ps1                                    # every service + the frontend
    .\scripts\build-images.ps1 -Services order-service,api-gateway
    .\scripts\build-images.ps1 -KindCluster grocery-cluster-a,grocery-cluster-b   # + kind load docker-image
#>
param(
    [string[]] $Services = @("api-gateway", "auth-service", "customer-service", "store-service", "product-service",
                             "product-read-service", "inventory-service", "cart-service", "order-service", "payment-service",
                             "delivery-service", "notification-service", "report-service", "platform-service",
                             "mcp-server-service", "mcp-client-service", "fresh-grocery"),
    [string] $Version = "0.0.1",
    [string[]] $KindCluster = @()
)

$repo = Join-Path $PSScriptRoot ".."
$failed = @()

foreach ($service in $Services) {
    $image = "grocery/${service}:$Version"
    Write-Host "Building $image ..." -ForegroundColor Cyan
    docker build -t $image (Join-Path $repo $service)
    if ($LASTEXITCODE -ne 0) {
        $failed += $service
        continue
    }
    foreach ($cluster in $KindCluster) {
        kind load docker-image $image --name $cluster
        if ($LASTEXITCODE -ne 0) { $failed += "$service (kind load into $cluster)" }
    }
}

if ($failed.Count -gt 0) {
    Write-Host "Failed: $($failed -join ', ')" -ForegroundColor Red
    exit 1
}
Write-Host "Built $($Services.Count) images." -ForegroundColor Green
