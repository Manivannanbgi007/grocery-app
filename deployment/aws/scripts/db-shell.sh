#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Opens a MySQL prompt to the RDS database, as its admin user (macOS / Linux; on
# Windows use db-shell.ps1). Needs: terraform, aws, jq, and the Session Manager
# plugin (https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html).
#
# The database is private, so this starts a short-lived container inside the VPC
# (the "db-shell" task), connects into it with `aws ecs execute-command` and runs
# the mysql client. Leaving the prompt (exit) stops the container.
#
#   ./deployment/aws/scripts/db-shell.sh
# -----------------------------------------------------------------------------
set -euo pipefail

if ! command -v session-manager-plugin > /dev/null; then
  echo "The Session Manager plugin is missing: https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html" >&2
  exit 1
fi

terraform_dir="$(cd "$(dirname "$0")" && pwd)/../terraform"
output() { terraform -chdir="$terraform_dir" output -json "$1"; }

region="$(output aws_region | jq -r .)"
cluster="$(output ecs_cluster_name | jq -r .)"
family="$(output db_shell_task | jq -r .)"
subnets="$(output task_network | jq -r '.subnet_ids | join(",")')"
security_group="$(output task_network | jq -r '.security_group_id')"

echo "Starting a db-shell container in the VPC ..."
task_arn="$(aws ecs run-task --region "$region" --cluster "$cluster" --launch-type FARGATE --enable-execute-command \
  --task-definition "$family" \
  --network-configuration "awsvpcConfiguration={subnets=[$subnets],securityGroups=[$security_group],assignPublicIp=DISABLED}" \
  --query 'tasks[0].taskArn' --output text)"

# Whatever happens next, stop the container at the end
trap 'echo "Stopping the db-shell container ..."; aws ecs stop-task --region "$region" --cluster "$cluster" --task "$task_arn" --reason "db-shell closed" > /dev/null' EXIT

aws ecs wait tasks-running --region "$region" --cluster "$cluster" --tasks "$task_arn"

echo "Waiting for the connection agent ..."
agent=""
for attempt in $(seq 1 24); do
  agent="$(aws ecs describe-tasks --region "$region" --cluster "$cluster" --tasks "$task_arn" \
    --query "tasks[0].containers[0].managedAgents[?name=='ExecuteCommandAgent'].lastStatus | [0]" --output text)"
  [ "$agent" = "RUNNING" ] && break
  sleep 5
done
if [ "$agent" != "RUNNING" ]; then
  echo "The connection agent did not start. Is enable_execute_command = true in terraform.tfvars?" >&2
  exit 1
fi

echo "Connected. Type 'exit' to leave the MySQL prompt."
aws ecs execute-command --region "$region" --cluster "$cluster" --task "$task_arn" --container db-shell \
  --interactive --command "db-connect"
