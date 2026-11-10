#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Runs one of the one-time setup tasks on ECS, waits until it has finished and
# shows its logs (macOS / Linux; on Windows use run-setup-task.ps1).
# Needs: terraform, aws, jq.
#
#   ./deployment/aws/scripts/run-setup-task.sh db-init            (before deploy_services = true)
#   ./deployment/aws/scripts/run-setup-task.sh kafka-topics       (before deploy_services = true)
#   ./deployment/aws/scripts/run-setup-task.sh kafka-connectors   (after the services have started)
# -----------------------------------------------------------------------------
set -euo pipefail

task="${1:-}"
case "$task" in
  db-init | kafka-topics | kafka-connectors) ;;
  *)
    echo "Usage: $0 db-init|kafka-topics|kafka-connectors" >&2
    exit 1
    ;;
esac

terraform_dir="$(cd "$(dirname "$0")" && pwd)/../terraform"
output() { terraform -chdir="$terraform_dir" output -json "$1"; }

region="$(output aws_region | jq -r .)"
cluster="$(output ecs_cluster_name | jq -r .)"
family="$(output setup_tasks | jq -r --arg task "$task" '.[$task].family')"
log_group="$(output setup_tasks | jq -r --arg task "$task" '.[$task].log_group')"
subnets="$(output task_network | jq -r '.subnet_ids | join(",")')"
security_group="$(output task_network | jq -r '.security_group_id')"

echo "Starting $task ..."
task_arn="$(aws ecs run-task --region "$region" --cluster "$cluster" --launch-type FARGATE \
  --task-definition "$family" \
  --network-configuration "awsvpcConfiguration={subnets=[$subnets],securityGroups=[$security_group],assignPublicIp=DISABLED}" \
  --query 'tasks[0].taskArn' --output text)"
if [ -z "$task_arn" ] || [ "$task_arn" = "None" ]; then
  echo "ECS did not start the task (see the error above)." >&2
  exit 1
fi

echo "Started $task_arn"
echo "Waiting until it has finished (usually 1-5 minutes) ..."
aws ecs wait tasks-stopped --region "$region" --cluster "$cluster" --tasks "$task_arn"

exit_code="$(aws ecs describe-tasks --region "$region" --cluster "$cluster" --tasks "$task_arn" --query 'tasks[0].containers[0].exitCode' --output text)"
reason="$(aws ecs describe-tasks --region "$region" --cluster "$cluster" --tasks "$task_arn" --query 'tasks[0].stoppedReason' --output text)"

echo
echo "----- Logs of $task -----"
aws logs tail "$log_group" --region "$region" --log-stream-names "ecs/$task/${task_arn##*/}" --format short
echo "-------------------------"

if [ "$exit_code" != "0" ]; then
  echo "$task failed (exit code: $exit_code, reason: $reason). The logs above say why." >&2
  exit 1
fi
echo "$task finished successfully."
