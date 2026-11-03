<#
.SYNOPSIS
    Starts the local infrastructure and creates the Kafka topics.

.DESCRIPTION
    Starts MySQL, Kafka, Schema Registry, Kafka Connect + Debezium, Elasticsearch,
    Redis, Mailpit, Kafka UI and Redis Insight with Docker Compose, waits until every container
    is healthy, creates the Kafka topics and prints a health summary.
    The first run downloads images and builds the Kafka Connect image (a few minutes).

.EXAMPLE
    .\scripts\start-infra.ps1
    .\scripts\start-infra.ps1 -Profiles tools               # + Kibana with data views for the product indexes
    .\scripts\start-infra.ps1 -Profiles monitoring          # + Prometheus, Alertmanager, Grafana, Loki, Jaeger
    .\scripts\start-infra.ps1 -Profiles tools,monitoring    # both
    .\scripts\start-infra.ps1 -Profiles ai                  # + Ollama and the Qwen model (AI assistant), speaches (voice)
#>
param(
    [string[]] $Profiles = @()
)

$composeFile = Join-Path $PSScriptRoot "..\infrastructure\docker-compose.yml"

$profileArgs = @()
foreach ($profileName in $Profiles) {   # ($profile is a built-in PowerShell variable, so not used here)
    $profileArgs += @("--profile", $profileName)
}

# Containers that run once and then stop; they are checked further down with "docker wait"
$oneShotContainers = @("grocery-elasticsearch-setup", "grocery-ollama-pull", "grocery-speaches-pull", "grocery-kibana-setup")

# The other containers that are not running, or not healthy yet. Each line from Compose looks like
# "grocery-kafka running healthy" (the health is empty for containers without a healthcheck).
function Get-ContainersNotReady {
    foreach ($line in $(docker compose -f $composeFile @profileArgs ps -a --format "{{.Name}} {{.State}} {{.Health}}")) {
        $name, $state, $health = $line -split ' '
        if ($oneShotContainers -contains $name) { continue }
        if ($state -ne "running" -or $health -eq "starting" -or $health -eq "unhealthy") {
            "$name ($state $health)".Replace(" )", ")")
        }
    }
}

Write-Host "Starting infrastructure containers..." -ForegroundColor Cyan
docker compose -f $composeFile @profileArgs up -d --build --wait
if ($LASTEXITCODE -ne 0) {
    # "up --wait" also fails when a one-shot container FINISHES while it still waits for the others, even with exit
    # code 0 (common on a restart, when the models are already downloaded). So look ourselves before giving up:
    # every other container must be running and healthy within 3 minutes.
    $deadline = (Get-Date).AddMinutes(3)
    do {
        $notReady = @(Get-ContainersNotReady)
        if ($notReady.Count -eq 0) { break }
        Start-Sleep -Seconds 5
    } while ((Get-Date) -lt $deadline)

    if ($notReady.Count -gt 0) {
        Write-Host "Docker Compose failed. Not running or not healthy: $($notReady -join ', ')" -ForegroundColor Red
        Write-Host "See the logs with:"
        Write-Host "  docker compose -f infrastructure/docker-compose.yml logs <service-name>"
        exit 1
    }
    Write-Host "Every container is up (Compose only stopped waiting because a one-shot container had finished)." -ForegroundColor Yellow
}

# MySQL runs the init script only when its data volume is brand new. Running it again is safe
# (everything is CREATE ... IF NOT EXISTS) and adds databases that later phases introduced (e.g. chat_db).
Write-Host ""
Write-Host "Making sure every database and user exists..." -ForegroundColor Cyan
$envFile = Join-Path $PSScriptRoot "..\infrastructure\.env"
$rootPassword = ((Get-Content $envFile) -match '^MYSQL_ROOT_PASSWORD=') -replace '^MYSQL_ROOT_PASSWORD=', ''
$initSql = Join-Path $PSScriptRoot "..\infrastructure\mysql\init\01-create-databases-and-users.sql"
Get-Content $initSql -Raw | docker exec -i -e MYSQL_PWD=$rootPassword grocery-mysql mysql -uroot
if ($LASTEXITCODE -ne 0) {
    Write-Host "Could not run the MySQL init script." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Creating Kafka topics..." -ForegroundColor Cyan
docker exec grocery-kafka bash /scripts/create-topics.sh
if ($LASTEXITCODE -ne 0) {
    Write-Host "Topic creation failed." -ForegroundColor Red
    exit 1
}

# "up --wait" does not wait for one-shot containers to FINISH: wait for the model download here, so the services
# are never started before the model exists. The first download takes a few minutes; later starts take seconds.
if ($Profiles -contains "ai") {
    Write-Host ""
    Write-Host "Downloading the AI model if it is not there yet..." -ForegroundColor Cyan
    docker logs -f grocery-ollama-pull
    $pullExitCode = docker wait grocery-ollama-pull
    if ($pullExitCode -ne "0") {
        Write-Host "The model download failed. See: docker logs grocery-ollama-pull" -ForegroundColor Red
        exit 1
    }
    Write-Host "Downloading the voice models if they are not there yet (about 660 MB the first time)..." -ForegroundColor Cyan
    docker logs -f grocery-speaches-pull
    $voiceExitCode = docker wait grocery-speaches-pull
    if ($voiceExitCode -ne "0") {
        Write-Host "The voice model download failed. See: docker logs grocery-speaches-pull" -ForegroundColor Red
        exit 1
    }
}

# The snapshot setup of the search indexes (repository + daily policy) runs once Elasticsearch is healthy
$snapshotSetupExitCode = docker wait grocery-elasticsearch-setup
if ($snapshotSetupExitCode -ne "0") {
    Write-Host "The Elasticsearch snapshot setup failed. See: docker logs grocery-elasticsearch-setup" -ForegroundColor Red
    exit 1
}

# Same for Kibana's one-shot setup: it creates the data views a few seconds after Kibana is ready
if ($Profiles -contains "tools") {
    Write-Host ""
    Write-Host "Preparing Kibana (data views)..." -ForegroundColor Cyan
    $setupExitCode = docker wait grocery-kibana-setup
    docker logs grocery-kibana-setup
    if ($setupExitCode -ne "0") {
        Write-Host "The Kibana setup failed. See: docker logs grocery-kibana-setup" -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
& (Join-Path $PSScriptRoot "check-health.ps1")
