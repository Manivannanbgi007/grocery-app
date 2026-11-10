<#
.SYNOPSIS
    Opens a MySQL prompt to the RDS database, as its admin user.

.DESCRIPTION
    The database is private (only reachable inside the VPC), so this starts a short-lived container there (the
    "db-shell" task), connects into it with `aws ecs execute-command` and runs the mysql client. When you leave the
    prompt (exit), the container is stopped. The admin password is read from Secrets Manager by ECS; you never see it.

    Needs: the AWS CLI, the Session Manager plugin (https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html)
    and enable_execute_command = true (the default).

    Careful: this is the admin user of the live database. Read first, change only what you are sure about.

.EXAMPLE
    .\deployment\aws\scripts\db-shell.ps1
    mysql> SHOW DATABASES;
    mysql> USE order_db;
    mysql> SHOW TABLES;
#>

$ErrorActionPreference = "Stop"
$terraformDir = Join-Path $PSScriptRoot "..\terraform"

function Get-TerraformOutput([string] $name) {
    $json = terraform "-chdir=$terraformDir" output -json $name
    if ($LASTEXITCODE -ne 0) {
        throw "Could not read the Terraform output '$name'. Run 'terraform apply' in $terraformDir first."
    }
    return ($json | Out-String | ConvertFrom-Json)
}

if (-not (Get-Command session-manager-plugin -ErrorAction SilentlyContinue)) {
    throw "The Session Manager plugin is missing: install it from https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html and open a new terminal."
}

$region = Get-TerraformOutput "aws_region"
$cluster = Get-TerraformOutput "ecs_cluster_name"
$family = Get-TerraformOutput "db_shell_task"
$network = Get-TerraformOutput "task_network"
$networkConfiguration = "awsvpcConfiguration={subnets=[$($network.subnet_ids -join ',')],securityGroups=[$($network.security_group_id)],assignPublicIp=DISABLED}"

Write-Host "Starting a db-shell container in the VPC ..." -ForegroundColor Cyan
$taskArn = aws ecs run-task --region $region --cluster $cluster --launch-type FARGATE --enable-execute-command `
    --task-definition $family --network-configuration $networkConfiguration `
    --query "tasks[0].taskArn" --output text
if ($LASTEXITCODE -ne 0 -or -not $taskArn -or $taskArn -eq "None") {
    throw "ECS did not start the db-shell task (see the error above)."
}

try {
    aws ecs wait tasks-running --region $region --cluster $cluster --tasks $taskArn
    if ($LASTEXITCODE -ne 0) { throw "The db-shell task did not start. Check its logs: /ecs/$cluster/db-shell" }

    # The agent behind execute-command needs a few more seconds after the container started
    Write-Host "Waiting for the connection agent ..."
    for ($attempt = 1; $attempt -le 24; $attempt++) {
        $agent = aws ecs describe-tasks --region $region --cluster $cluster --tasks $taskArn `
            --query "tasks[0].containers[0].managedAgents[?name=='ExecuteCommandAgent'].lastStatus | [0]" --output text
        if ($agent -eq "RUNNING") { break }
        Start-Sleep -Seconds 5
    }
    if ($agent -ne "RUNNING") { throw "The connection agent did not start. Is enable_execute_command = true in terraform.tfvars?" }

    Write-Host "Connected. Type 'exit' to leave the MySQL prompt." -ForegroundColor Green
    aws ecs execute-command --region $region --cluster $cluster --task $taskArn --container db-shell `
        --interactive --command "db-connect"
}
finally {
    Write-Host "Stopping the db-shell container ..." -ForegroundColor Cyan
    aws ecs stop-task --region $region --cluster $cluster --task $taskArn --reason "db-shell closed" | Out-Null
}
