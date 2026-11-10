# -----------------------------------------------------------------------------
# Outputs: shown after `terraform apply`, and any time with `terraform output`.
# The scripts in ../scripts read some of them (terraform output -json).
# -----------------------------------------------------------------------------

output "application_url" {
  description = "The shop. The vendor, delivery and admin panels are at /vendor, /delivery and /admin."
  value       = local.public_url
}

output "load_balancer_dns_name" {
  description = "The load balancer's AWS name. With domain_name, point your domain at it (a CNAME record, or an alias record in Route 53)."
  value       = aws_lb.this.dns_name
}

output "aws_region" {
  description = "The region everything is in."
  value       = var.aws_region
}

# ----- Images -----

output "ecr_registry" {
  description = "Your private image registry: docker login to it before pushing (scripts/push-images does it)."
  value       = local.ecr_registry
}

output "ecr_repository_urls" {
  description = "One repository per image: image name => repository URL (push <url>:<image_tag>)."
  value       = { for name, repository in aws_ecr_repository.this : name => repository.repository_url }
}

output "image_tag" {
  description = "The image tag the services run (variable image_tag)."
  value       = var.image_tag
}

output "image_platform" {
  description = "The Docker platform to build the images for (follows cpu_architecture)."
  value       = var.cpu_architecture == "ARM64" ? "linux/arm64" : "linux/amd64"
}

output "image_build_contexts" {
  description = "Image name => folder of its Dockerfile, relative to the repository root (used by scripts/push-images)."
  value = merge(
    { for name in keys(local.app_service_catalogue) : name => name },
    { frontend = "fresh-grocery", kafka-connect = "infrastructure/kafka-connect" },
  )
}

# ----- ECS -----

output "ecs_cluster_name" {
  description = "The ECS cluster (AWS console: ECS > Clusters)."
  value       = aws_ecs_cluster.this.name
}

output "ecs_service_names" {
  description = "Every running ECS service (empty until deploy_services = true)."
  value = concat(
    [for service in module.app_service : service.service_name],
    [for service in module.schema_registry : service.service_name],
    [for service in module.kafka_connect : service.service_name],
    [for service in module.elasticsearch : service.service_name],
  )
}

output "setup_tasks" {
  description = "The one-time setup tasks: name => task definition family and log group (scripts/run-setup-task)."
  value = {
    db-init          = { family = module.db_init.task_family, log_group = module.db_init.log_group_name }
    kafka-topics     = { family = module.kafka_topics.task_family, log_group = module.kafka_topics.log_group_name }
    kafka-connectors = { family = module.kafka_connectors.task_family, log_group = module.kafka_connectors.log_group_name }
  }
}

output "task_network" {
  description = "Where the setup tasks run: the private subnets and the tasks' security group."
  value = {
    subnet_ids        = aws_subnet.private[*].id
    security_group_id = aws_security_group.tasks.id
  }
}

output "run_setup_task_commands" {
  description = "The AWS CLI command that starts each setup task by hand (the scripts do the same and also wait and show the logs)."
  value = {
    for name, module_output in {
      db-init          = module.db_init
      kafka-topics     = module.kafka_topics
      kafka-connectors = module.kafka_connectors
    } : name => "aws ecs run-task --region ${var.aws_region} --cluster ${aws_ecs_cluster.this.name} --launch-type FARGATE --task-definition ${module_output.task_family} --network-configuration \"awsvpcConfiguration={subnets=[${join(",", aws_subnet.private[*].id)}],securityGroups=[${aws_security_group.tasks.id}],assignPublicIp=DISABLED}\""
  }
}

# ----- Data stores (private: reachable from the ECS tasks only) -----

output "mysql" {
  description = "The RDS MySQL database: endpoint, port, name in RDS, admin user, its secret, and the CloudWatch log groups of its logs."
  value = {
    endpoint          = module.mysql.address
    port              = module.mysql.port
    identifier        = module.mysql.identifier
    admin_username    = module.mysql.master_username
    admin_secret_arn  = module.mysql.master_user_secret_arn
    parameter_group   = module.mysql.parameter_group_name
    log_groups        = module.mysql.log_group_names
    security_group_id = module.mysql.security_group_id
  }
}

output "db_shell_task" {
  description = "The db-shell task (a MySQL prompt to the database, scripts/db-shell): its task definition family."
  value       = module.db_shell.task_family
}

output "alerts_topic_arn" {
  description = "SNS topic that emails the alerts (null without alarm_email)."
  value       = local.alerts_enabled ? aws_sns_topic.alerts[0].arn : null
}

output "kafka_bootstrap_servers" {
  description = "The Kafka brokers (plaintext listener inside the VPC)."
  value       = local.kafka_bootstrap_servers
}

output "redis_endpoint" {
  description = "The Redis primary node (TLS, port 6379)."
  value       = local.redis_host
}

output "elasticsearch_url" {
  description = "The Elasticsearch the search uses."
  value       = local.elasticsearch_url
}

output "app_secret_name" {
  description = "Secrets Manager secret with the generated application secrets (database passwords, JWT secret ...)."
  value       = aws_secretsmanager_secret.app.name
}

# ----- What to do now -----

locals {
  next_steps_infrastructure = <<-EOT
    The infrastructure is ready. Next (README.md, steps 6 and 7):
      1. Build and push the images with tag ${var.image_tag}:  ./scripts/push-images.ps1
      2. Prepare MySQL and Kafka (once):  ./scripts/run-setup-task.ps1 db-init
                                          ./scripts/run-setup-task.ps1 kafka-topics
      3. Set deploy_services = true in terraform.tfvars and run terraform apply again.
  EOT

  next_steps_services = <<-EOT
    The services are starting (Spring Boot needs 2-4 minutes). Then (README.md, step 9):
      1. Register the Debezium connectors (once):  ./scripts/run-setup-task.ps1 kafka-connectors
      2. Open the shop:                            ${local.public_url}
    Follow a service's logs:  aws logs tail /ecs/${aws_ecs_cluster.this.name}/api-gateway --follow --region ${var.aws_region}
  EOT
}

output "next_steps" {
  description = "What to do after this apply."
  value       = var.deploy_services ? local.next_steps_services : local.next_steps_infrastructure
}
