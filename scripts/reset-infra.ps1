<#
.SYNOPSIS
    Deletes ALL local infrastructure data (MySQL, Kafka, Elasticsearch, Redis, Grafana)
    and starts again from a clean state.

.DESCRIPTION
    Use this when the local data is broken, or to re-run the MySQL init script.
    Every service recreates its tables and seed data (Flyway) the next time it starts.

.EXAMPLE
    .\scripts\reset-infra.ps1          # asks for confirmation
    .\scripts\reset-infra.ps1 -Force   # no question
#>
param(
    [switch] $Force
)

if (-not $Force) {
    $answer = Read-Host "This deletes ALL local databases, topics and indexes. Type 'yes' to continue"
    if ($answer -ne "yes") {
        Write-Host "Cancelled."
        exit 0
    }
}

$composeFile = Join-Path $PSScriptRoot "..\infrastructure\docker-compose.yml"

docker compose -f $composeFile --profile "*" down --volumes
& (Join-Path $PSScriptRoot "start-infra.ps1")
