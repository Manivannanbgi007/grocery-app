<#
.SYNOPSIS
    Runs one of the one-time setup tasks on ECS, waits until it has finished, and shows its logs.

.DESCRIPTION
    The setup tasks (deployment/aws/terraform/ecs-bootstrap.tf), in this order:
      db-init           creates every service's MySQL database and user       (before deploy_services = true)
      kafka-topics      creates the Kafka topics                              (before deploy_services = true)
      kafka-connectors  registers the Debezium connectors                     (after the services have started)
    Every task is safe to run again. The script stops with an error when the task fails.

.EXAMPLE
    .\deployment\aws\scripts\run-setup-task.ps1 db-init
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("db-init", "kafka-topics", "kafka-connectors")]
    [string] $Task
)

$ErrorActionPreference = "Stop"
$terraformDir = Join-Path $PSScriptRoot "..\terraform"

function Get-TerraformOutput([string] $name) {
    $json = terraform "-chdir=$terraformDir" output -json $name
    if ($LASTEXITCODE -ne 0) {
        throw "Could not read the Terraform output '$name'. Run 'terraform apply' in $terraformDir first."
    }
    return ($json | Out-String | ConvertFrom-Json)
}

$region = Get-TerraformOutput "aws_region"
$cluster = Get-TerraformOutput "ecs_cluster_name"
$setup = (Get-TerraformOutput "setup_tasks").$Task
$network = Get-TerraformOutput "task_network"
$networkConfiguration = "awsvpcConfiguration={subnets=[$($network.subnet_ids -join ',')],securityGroups=[$($network.security_group_id)],assignPublicIp=DISABLED}"

Write-Host "Starting $Task ..." -ForegroundColor Cyan
$taskArn = aws ecs run-task --region $region --cluster $cluster --launch-type FARGATE `
    --task-definition $setup.family --network-configuration $networkConfiguration `
    --query "tasks[0].taskArn" --output text
if ($LASTEXITCODE -ne 0 -or -not $taskArn -or $taskArn -eq "None") {
    throw "ECS did not start the task (see the error above)."
}

Write-Host "Started $taskArn"
Write-Host "Waiting until it has finished (usually 1-5 minutes) ..."
aws ecs wait tasks-stopped --region $region --cluster $cluster --tasks $taskArn
if ($LASTEXITCODE -ne 0) { throw "Gave up waiting after 10 minutes; the task may still run. Check the ECS console." }

$exitCode = aws ecs describe-tasks --region $region --cluster $cluster --tasks $taskArn --query "tasks[0].containers[0].exitCode" --output text
$reason = aws ecs describe-tasks --region $region --cluster $cluster --tasks $taskArn --query "tasks[0].stoppedReason" --output text

# The log stream is "<prefix>/<container name>/<task id>" (modules/ecs-service: awslogs-stream-prefix "ecs")
$taskId = $taskArn.Split("/")[-1]
Write-Host ""
Write-Host "----- Logs of $Task -----" -ForegroundColor Cyan
aws logs tail $setup.log_group --region $region --log-stream-names "ecs/$Task/$taskId" --format short
Write-Host "-------------------------" -ForegroundColor Cyan

if ($exitCode -ne "0") {
    throw "$Task failed (exit code: $exitCode, reason: $reason). The logs above say why."
}
Write-Host "$Task finished successfully." -ForegroundColor Green
