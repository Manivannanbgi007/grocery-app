output "task_definition_arn" {
  description = "ARN of the task definition (with its revision)."
  value       = aws_ecs_task_definition.this.arn
}

output "task_family" {
  description = "Family of the task definition: what `aws ecs run-task --task-definition` takes (runs the newest revision)."
  value       = aws_ecs_task_definition.this.family
}

output "service_name" {
  description = "Name of the ECS service (null for a one-off task)."
  value       = var.create_service ? aws_ecs_service.this[0].name : null
}

output "log_group_name" {
  description = "CloudWatch log group of the container: aws logs tail <name> --follow"
  value       = aws_cloudwatch_log_group.this.name
}
