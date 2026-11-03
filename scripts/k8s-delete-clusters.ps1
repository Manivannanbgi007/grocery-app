<#
.SYNOPSIS
    Deletes the kind clusters grocery-cluster-a and grocery-cluster-b (and with both, the entry NGINX).

.DESCRIPTION
    Removes the cluster's node containers and everything in them. Nothing of value is lost: the data is in the
    Docker Compose infrastructure, and photos and invoices are in folders on your machine (see clusters\cluster-a.yaml).

.EXAMPLE
    .\scripts\k8s-delete-clusters.ps1                 # both clusters + the entry NGINX
    .\scripts\k8s-delete-clusters.ps1 -Clusters b     # only cluster B
#>
param(
    [ValidateSet("a", "b")]
    [string[]] $Clusters = @("a", "b")
)

foreach ($letter in $Clusters) {
    Write-Host "Deleting grocery-cluster-$letter ..." -ForegroundColor Cyan
    kind delete cluster --name "grocery-cluster-$letter"
}

if ($Clusters.Count -eq 2) {
    & (Join-Path $PSScriptRoot "k8s-entry-nginx.ps1") -Stop
}
