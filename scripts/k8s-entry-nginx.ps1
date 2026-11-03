<#
.SYNOPSIS
    Starts (or stops) the entry NGINX in front of both kind clusters: http://localhost.

.DESCRIPTION
    Runs nginx:1.28-alpine as the Docker container "grocery-entry-nginx" in kind's Docker network, with the
    configuration deployment\kubernetes\nginx\nginx.conf. It sends every request to cluster A or cluster B and skips
    a cluster that does not answer. Port 80 is published on this machine only (127.0.0.1).
    Run it again after changing nginx.conf.

.EXAMPLE
    .\scripts\k8s-entry-nginx.ps1          # (re)start it
    .\scripts\k8s-entry-nginx.ps1 -Stop    # stop and remove it
#>
param(
    [switch] $Stop
)

$containerName = "grocery-entry-nginx"
$config = (Resolve-Path (Join-Path $PSScriptRoot "..\deployment\kubernetes\nginx\nginx.conf")).Path

if (docker ps -a --filter "name=$containerName" --format "{{.Names}}") {
    docker rm -f $containerName | Out-Null
}
if ($Stop) {
    Write-Host "Entry NGINX stopped." -ForegroundColor Green
    exit 0
}

docker run -d --name $containerName --restart unless-stopped --network kind -p 127.0.0.1:80:80 `
    -v "${config}:/etc/nginx/nginx.conf:ro" nginx:1.28-alpine | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Starting the entry NGINX failed (is port 80 free? does the Docker network 'kind' exist?)." -ForegroundColor Red
    exit 1
}

Start-Sleep -Seconds 2
try {
    $status = (Invoke-WebRequest -Uri "http://localhost/" -UseBasicParsing -TimeoutSec 10).StatusCode
    Write-Host "Entry NGINX is up: http://localhost (HTTP $status)" -ForegroundColor Green
} catch {
    Write-Host "Entry NGINX runs, but http://localhost did not answer yet: $($_.Exception.Message)" -ForegroundColor Yellow
    Write-Host "Are the clusters deployed? docker logs $containerName"
}
