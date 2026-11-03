<#
.SYNOPSIS
    Registers (or updates) every Debezium connector with Kafka Connect.

.DESCRIPTION
    Each service that publishes events keeps its connector definition in
    <service>/debezium/*.json, in the form  { "name": "...", "config": { ... } }.
    This script sends each one to Kafka Connect with PUT /connectors/<name>/config,
    which creates the connector or updates it, so it is safe to run many times.

    Start the service at least once before registering its connector, so Flyway
    has already created the tables the connector reads.
#>
param(
    [string] $ConnectUrl = "http://localhost:8083"
)

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")

$connectorFiles = Get-ChildItem -Path $repoRoot -Directory |
    ForEach-Object { Join-Path $_.FullName "debezium" } |
    Where-Object { Test-Path $_ } |
    ForEach-Object { Get-ChildItem -Path $_ -Filter "*.json" }

if (-not $connectorFiles) {
    Write-Host "No connector files found (<service>/debezium/*.json). Nothing to register."
    exit 0
}

foreach ($file in $connectorFiles) {
    $connector = Get-Content -Path $file.FullName -Raw | ConvertFrom-Json
    $body = $connector.config | ConvertTo-Json -Depth 10

    Write-Host ("Registering {0}  ({1})" -f $connector.name, $file.FullName) -ForegroundColor Cyan
    try {
        Invoke-RestMethod -Method Put -Uri "$ConnectUrl/connectors/$($connector.name)/config" `
            -ContentType "application/json" -Body $body | Out-Null

        Start-Sleep -Seconds 2
        $status = Invoke-RestMethod -Uri "$ConnectUrl/connectors/$($connector.name)/status"
        Write-Host ("  connector: {0}   tasks: {1}" -f $status.connector.state, (($status.tasks | ForEach-Object { $_.state }) -join ", ")) -ForegroundColor Green
    }
    catch {
        Write-Host ("  FAILED: {0}" -f $_.Exception.Message) -ForegroundColor Red
    }
}
