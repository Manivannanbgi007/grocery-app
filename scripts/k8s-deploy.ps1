<#
.SYNOPSIS
    Deploys the grocery shop (16 services + the frontend) to the kind clusters and waits until every pod is ready.

.DESCRIPTION
    For each cluster:
      1. Loads the images (grocery/<name>:0.0.1, built with .\scripts\build-images.ps1) into the cluster's two
         worker nodes. kind has no registry: the nodes get a copy of the images from your Docker.
      2. kubectl apply -k deployment\kubernetes: the same manifests for both clusters.
      3. Waits until every Deployment is available (the first start takes a few minutes: 20+ JVMs start at once).
    The clusters must exist (.\scripts\k8s-create-clusters.ps1), and the infrastructure must run (.\scripts\start-infra.ps1).

.EXAMPLE
    .\scripts\k8s-deploy.ps1                          # both clusters
    .\scripts\k8s-deploy.ps1 -Clusters a              # only cluster A
    .\scripts\k8s-deploy.ps1 -SkipImageLoad           # only the manifests changed
    .\scripts\k8s-deploy.ps1 -Restart                 # new images with the same tag: restart every pod (rolling)
#>
param(
    [ValidateSet("a", "b")]
    [string[]] $Clusters = @("a", "b"),
    [switch] $SkipImageLoad,
    [switch] $Restart,
    [string] $Version = "0.0.1"
)

$kubernetesDir = Join-Path $PSScriptRoot "..\deployment\kubernetes"
$images = @("api-gateway", "auth-service", "customer-service", "store-service", "product-service",
            "product-read-service", "inventory-service", "cart-service", "order-service", "payment-service",
            "delivery-service", "notification-service", "report-service", "platform-service",
            "mcp-server-service", "mcp-client-service", "fresh-grocery") | ForEach-Object { "grocery/${_}:$Version" }

foreach ($letter in $Clusters) {
    $name = "grocery-cluster-$letter"
    $context = "kind-$name"

    if (-not $SkipImageLoad) {
        # Only the workers run pods (kind keeps them off the control plane), so only they need the images.
        Write-Host "Loading $($images.Count) images into $name ..." -ForegroundColor Cyan
        kind load docker-image @images --name $name --nodes "$name-worker,$name-worker2"
        if ($LASTEXITCODE -ne 0) {
            Write-Host "kind load failed. Are all images built? .\scripts\build-images.ps1" -ForegroundColor Red
            exit 1
        }
    }

    Write-Host "Applying deployment\kubernetes to $name ..." -ForegroundColor Cyan
    kubectl --context $context apply -k $kubernetesDir
    if ($LASTEXITCODE -ne 0) {
        Write-Host "kubectl apply failed for $name." -ForegroundColor Red
        exit 1
    }
    if ($Restart) {
        kubectl --context $context -n grocery rollout restart deployment
    }

    Write-Host "Waiting until every Deployment in $name is available (up to 15 minutes) ..." -ForegroundColor Cyan
    kubectl --context $context -n grocery wait --for=condition=available deployment --all --timeout=15m
    $allAvailable = $LASTEXITCODE -eq 0
    kubectl --context $context -n grocery get pods -o wide
    if (-not $allAvailable) {
        Write-Host "Not every Deployment in $name became available. Why: kubectl --context $context -n grocery describe pod <pod>" -ForegroundColor Red
        exit 1
    }

    $port = if ($letter -eq "a") { 8281 } else { 8282 }
    Write-Host "$name is up: http://localhost:$port (through the entry NGINX: http://localhost)" -ForegroundColor Green
}
