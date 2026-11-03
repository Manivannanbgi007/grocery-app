<#
.SYNOPSIS
    Prints the health of the local infrastructure and the useful URLs.
#>

$composeFile = Join-Path $PSScriptRoot "..\infrastructure\docker-compose.yml"

Write-Host "Containers:" -ForegroundColor Cyan
docker compose -f $composeFile ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"

$checks = @(
    @{ Name = "Elasticsearch";   Url = "http://localhost:9200/_cluster/health" },
    @{ Name = "Schema Registry"; Url = "http://localhost:8081/subjects" },
    @{ Name = "Kafka Connect";   Url = "http://localhost:8083/connectors" },
    @{ Name = "Kafka UI";        Url = "http://localhost:8090" },
    @{ Name = "Redis Insight";   Url = "http://localhost:5540/api/health" },
    @{ Name = "Mailpit";         Url = "http://localhost:8025" }
)

Write-Host ""
Write-Host "HTTP endpoints:" -ForegroundColor Cyan
foreach ($check in $checks) {
    try {
        Invoke-WebRequest -Uri $check.Url -UseBasicParsing -TimeoutSec 5 | Out-Null
        Write-Host ("  [OK]   {0,-16} {1}" -f $check.Name, $check.Url) -ForegroundColor Green
    }
    catch {
        Write-Host ("  [FAIL] {0,-16} {1}" -f $check.Name, $check.Url) -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "Debezium plugin in Kafka Connect:" -ForegroundColor Cyan
try {
    $plugins = Invoke-RestMethod -Uri "http://localhost:8083/connector-plugins" -TimeoutSec 5
    $mysqlPlugin = $plugins | Where-Object { $_.class -like "*MySqlConnector" }
    if ($mysqlPlugin) {
        Write-Host ("  [OK]   {0} {1}" -f $mysqlPlugin.class, $mysqlPlugin.version) -ForegroundColor Green
    }
    else {
        Write-Host "  [FAIL] Debezium MySQL connector not found" -ForegroundColor Red
    }
}
catch {
    Write-Host "  [FAIL] Kafka Connect is not reachable" -ForegroundColor Red
}

# Every connector and its task must be RUNNING: a FAILED task publishes nothing (the outbox rows wait in MySQL).
# None listed = run scripts\register-connectors.ps1
Write-Host ""
Write-Host "Debezium connectors:" -ForegroundColor Cyan
try {
    $connectors = Invoke-RestMethod -Uri "http://localhost:8083/connectors?expand=status" -TimeoutSec 5
    foreach ($name in ($connectors.PSObject.Properties.Name | Sort-Object)) {
        $status = $connectors.$name.status
        $taskStates = @($status.tasks | ForEach-Object { $_.state })
        $allRunning = $status.connector.state -eq "RUNNING" -and $taskStates.Count -gt 0 -and -not ($taskStates | Where-Object { $_ -ne "RUNNING" })
        $line = "  {0,-6} {1,-28} connector {2}, task(s) {3}" -f $(if ($allRunning) { "[OK]" } else { "[FAIL]" }), $name, $status.connector.state, ($taskStates -join ", ")
        Write-Host $line -ForegroundColor $(if ($allRunning) { "Green" } else { "Red" })
    }
    if (-not $connectors.PSObject.Properties.Name) { Write-Host "  [FAIL] no connectors registered: run scripts\register-connectors.ps1" -ForegroundColor Red }
}
catch {
    Write-Host "  [FAIL] Kafka Connect is not reachable" -ForegroundColor Red
}

# Ollama only runs with the "ai" profile, so it is reported but never counted as a failure
Write-Host ""
Write-Host "AI assistant (profile ai):" -ForegroundColor Cyan
try {
    $models = Invoke-RestMethod -Uri "http://localhost:11434/api/tags" -TimeoutSec 5
    $names = ($models.models | ForEach-Object { $_.name }) -join ", "
    if (-not $names) { $names = "no model downloaded yet" }
    Write-Host ("  [OK]   Ollama           http://localhost:11434  models: {0}" -f $names) -ForegroundColor Green
}
catch {
    Write-Host "  [--]   Ollama is not running (start with -Profiles ai)" -ForegroundColor DarkGray
}

# Kibana only runs with the "tools" profile: reported, never counted as a failure
Write-Host ""
Write-Host "Tools (profile tools):" -ForegroundColor Cyan
try {
    $kibana = Invoke-RestMethod -Uri "http://localhost:5601/api/status" -TimeoutSec 5
    Write-Host ("  [OK]   {0,-16} http://localhost:5601  version {1}, {2}" -f "Kibana", $kibana.version.number, $kibana.status.overall.level) -ForegroundColor Green
}
catch {
    Write-Host "  [--]   Kibana           not running or still starting (start with -Profiles tools)" -ForegroundColor DarkGray
}

# The monitoring tools only run with the "monitoring" profile: reported, never counted as a failure
Write-Host ""
Write-Host "Monitoring (profile monitoring):" -ForegroundColor Cyan
$monitoring = @(
    @{ Name = "Prometheus";   Url = "http://localhost:9090/-/ready" },
    @{ Name = "Alertmanager"; Url = "http://localhost:9093/-/ready" },
    @{ Name = "Blackbox";     Url = "http://localhost:9115/-/healthy" },
    @{ Name = "Grafana";      Url = "http://localhost:3000/api/health" },
    @{ Name = "Loki";         Url = "http://localhost:3100/ready" },
    @{ Name = "Jaeger";       Url = "http://localhost:16686/" }
)
foreach ($check in $monitoring) {
    try {
        Invoke-WebRequest -Uri $check.Url -UseBasicParsing -TimeoutSec 5 | Out-Null
        Write-Host ("  [OK]   {0,-16} {1}" -f $check.Name, $check.Url) -ForegroundColor Green
    }
    catch {
        Write-Host ("  [--]   {0,-16} not running (start with -Profiles monitoring)" -f $check.Name) -ForegroundColor DarkGray
    }
}

# What Prometheus can read, per job: the services, their health checks, the Kafka exporter and Kafka Connect
# (the exporters have no port on this machine, so Prometheus is where to see them)
try {
    $targets = (Invoke-RestMethod -Uri "http://localhost:9090/api/v1/targets?state=active" -TimeoutSec 5).data.activeTargets
    Write-Host ""
    Write-Host "Prometheus targets (up / total):" -ForegroundColor Cyan
    $targets | Group-Object { $_.labels.job } | Sort-Object Name | ForEach-Object {
        $up = @($_.Group | Where-Object { $_.health -eq "up" }).Count
        $allUp = $up -eq $_.Count
        Write-Host ("  {0,-6} {1,-16} {2} / {3}" -f $(if ($allUp) { "[OK]" } else { "[!!]" }), $_.Name, $up, $_.Count) -ForegroundColor $(if ($allUp) { "Green" } else { "Yellow" })
    }
}
catch { }

Write-Host ""
Write-Host "Useful URLs:" -ForegroundColor Cyan
Write-Host "  Grafana        http://localhost:3000  (dashboards Grocery services, Grocery service details, Grocery events, Grocery infrastructure, Grocery logs; Alerting)"
Write-Host "  Jaeger         http://localhost:16686 (traces; services need TRACING_EXPORT_ENABLED=true)"
Write-Host "  Alertmanager   http://localhost:9093  (firing alerts; the emails arrive in Mailpit)"
Write-Host "  Kafka UI       http://localhost:8090"
Write-Host "  Redis Insight  http://localhost:5540  (click grocery-redis; guide: docs/redis-insight.md)"
Write-Host "  Mailpit inbox  http://localhost:8025"
Write-Host "  Elasticsearch  http://localhost:9200"
Write-Host "  Kibana         http://localhost:5601/app/discover  (profile tools; guide: docs/kibana.md)"
Write-Host "  MySQL          localhost:3307  (root / root, or <service>_user / <service>_pass)"
