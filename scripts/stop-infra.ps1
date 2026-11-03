<#
.SYNOPSIS
    Stops and removes the infrastructure containers. Data in the Docker volumes is KEPT.
    Use reset-infra.ps1 to delete the data as well.
#>

$composeFile = Join-Path $PSScriptRoot "..\infrastructure\docker-compose.yml"

# --profile "*" also stops the optional containers (Kibana, the monitoring tools, Ollama)
docker compose -f $composeFile --profile "*" down
