<#
.SYNOPSIS
    Creates the kind clusters grocery-cluster-a and grocery-cluster-b and installs what every cluster needs.

.DESCRIPTION
    For each cluster:
      1. kind create cluster: 1 control plane + 2 workers (deployment\kubernetes\clusters\cluster-<a|b>.yaml)
      2. Metrics Server: CPU and memory per pod, for "kubectl top" and the autoscalers
      3. Gateway API resource types + NGINX Gateway Fabric: the cluster's entry point on node port 30080
         (localhost:8281 for cluster A, localhost:8282 for cluster B)
    Then deploy the shop with .\scripts\k8s-deploy.ps1.
    kubectl contexts: kind-grocery-cluster-a and kind-grocery-cluster-b (kind also switches to the new one).
    Needs: Docker Desktop, kind, kubectl, helm, and internet access (the add-ons are downloaded).

.EXAMPLE
    .\scripts\k8s-create-clusters.ps1                 # both clusters
    .\scripts\k8s-create-clusters.ps1 -Clusters a     # only cluster A
#>
param(
    [ValidateSet("a", "b")]
    [string[]] $Clusters = @("a", "b")
)

$kubernetesDir = Join-Path $PSScriptRoot "..\deployment\kubernetes"
$gatewayFabricVersion = "2.8.0"

foreach ($letter in $Clusters) {
    $name = "grocery-cluster-$letter"
    $context = "kind-$name"

    if ((kind get clusters) -contains $name) {
        Write-Host "$name exists already." -ForegroundColor Yellow
    } else {
        Write-Host "Creating $name (1 control plane + 2 workers) ..." -ForegroundColor Cyan
        kind create cluster --config (Join-Path $kubernetesDir "clusters\cluster-$letter.yaml")
        if ($LASTEXITCODE -ne 0) {
            Write-Host "kind create cluster failed for $name." -ForegroundColor Red
            exit 1
        }
    }

    Write-Host "Metrics Server ..." -ForegroundColor Cyan
    kubectl --context $context apply -k (Join-Path $kubernetesDir "addons\metrics-server")

    # The Gateway API's resource types (Gateway, HTTPRoute ...) in the version NGINX Gateway Fabric was tested with.
    # --server-side: some of these definitions are too big for a normal "apply".
    Write-Host "Gateway API resource types + NGINX Gateway Fabric $gatewayFabricVersion ..." -ForegroundColor Cyan
    kubectl --context $context apply --server-side -k "https://github.com/nginx/nginx-gateway-fabric/config/crd/gateway-api/standard?ref=v$gatewayFabricVersion"
    helm upgrade --install ngf oci://ghcr.io/nginx/charts/nginx-gateway-fabric --version $gatewayFabricVersion `
        --kube-context $context --namespace nginx-gateway --create-namespace `
        --values (Join-Path $kubernetesDir "addons\nginx-gateway-fabric\values.yaml") --wait
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Installing NGINX Gateway Fabric failed for $name." -ForegroundColor Red
        exit 1
    }

    Write-Host "$name is ready. Deploy the shop: .\scripts\k8s-deploy.ps1 -Clusters $letter" -ForegroundColor Green
}
