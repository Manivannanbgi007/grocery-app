# -----------------------------------------------------------------------------
# The application's services on ECS Fargate: one ECS service per entry of the
# catalogue in services.tf. Only created when deploy_services = true (the images
# must be in ECR and the one-time setup done first; see README.md).
# -----------------------------------------------------------------------------

module "app_service" {
  source   = "./modules/ecs-service"
  for_each = var.deploy_services ? local.app_services : {}

  name             = each.key
  image            = "${aws_ecr_repository.this[each.key].repository_url}:${var.image_tag}"
  cpu              = each.value.cpu
  memory           = each.value.memory
  cpu_architecture = var.cpu_architecture
  container_port   = each.value.port

  environment          = each.value.environment
  secrets              = each.value.secrets
  health_check_command = each.value.health_check_command

  efs_volumes = [
    for path, access_point in each.value.efs_mounts : {
      name            = access_point
      file_system_id  = aws_efs_file_system.this.id
      access_point_id = aws_efs_access_point.this[access_point].id
      container_path  = path
    }
  ]

  region             = var.aws_region
  cluster_name       = aws_ecs_cluster.this.name
  cluster_arn        = aws_ecs_cluster.this.arn
  execution_role_arn = aws_iam_role.task_execution.arn
  task_role_arn      = aws_iam_role.task.arn
  subnet_ids         = aws_subnet.private[*].id
  security_group_ids = [aws_security_group.tasks.id]
  log_retention_days = var.log_retention_days

  desired_count          = each.value.desired_count
  autoscaling_max_count  = each.value.autoscaling ? max(var.autoscaling_max_count, each.value.desired_count) : null
  use_spot               = var.use_fargate_spot
  namespace_id           = aws_service_discovery_private_dns_namespace.this.id
  load_balanced          = each.value.target_group != null
  target_group_arn       = each.value.target_group == null ? null : aws_lb_target_group.this[each.value.target_group].arn
  enable_execute_command = var.enable_execute_command

  # A service behind the load balancer needs its listener rules first; the services
  # need the data stores' users and the platform services (Kafka Connect ...) to be there
  depends_on = [
    aws_lb_listener.http,
    aws_lb_listener.https,
    aws_lb_listener_rule.api,
    aws_efs_mount_target.this,
    aws_secretsmanager_secret_version.app,
    aws_secretsmanager_secret_version.external,
  ]
}
