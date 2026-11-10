# -----------------------------------------------------------------------------
# ecs-service: one container on ECS Fargate. Creates
#   - a CloudWatch log group             /ecs/<cluster>/<name>
#   - a task definition                  what to run: image, CPU/memory, env, secrets, health check, volumes
#   - (create_service = true)
#       a DNS name in Cloud Map          <name>.<namespace>, so other services can call it
#       an ECS service                   keeps desired_count tasks running, replaces failed ones,
#                                        rolls out new versions without downtime (and rolls back
#                                        by itself when the new version does not start)
#       autoscaling                      (optional) more tasks under high CPU
# -----------------------------------------------------------------------------

# No data sources in this module on purpose: callers use depends_on, which would make
# Terraform read them only during apply, so every plan would show changed task
# definitions (and every apply would restart the services).

resource "aws_cloudwatch_log_group" "this" {
  name              = "/ecs/${var.cluster_name}/${var.name}"
  retention_in_days = var.log_retention_days
}

locals {
  container = {
    name      = var.name
    image     = var.image
    essential = true

    portMappings = var.container_port == null ? [] : [{
      containerPort = var.container_port
      protocol      = "tcp"
    }]

    # Sorted, so the task definition does not change when only the order changes
    environment = [for key in sort(keys(var.environment)) : { name = key, value = var.environment[key] }]
    secrets     = [for key in sort(keys(var.secrets)) : { name = key, valueFrom = var.secrets[key] }]

    entryPoint = var.entry_point
    command    = var.command

    healthCheck = var.health_check_command == null ? null : {
      command     = var.health_check_command
      interval    = 15
      timeout     = 5
      retries     = 3
      startPeriod = var.health_check_start_period
    }

    mountPoints = [for volume in var.efs_volumes : {
      sourceVolume  = volume.name
      containerPath = volume.container_path
      readOnly      = false
    }]

    ulimits = var.nofile_limit == null ? null : [{
      name      = "nofile"
      softLimit = var.nofile_limit
      hardLimit = var.nofile_limit
    }]

    # A small init process: passes signals on (clean shutdown) and is needed by ECS Exec
    linuxParameters = { initProcessEnabled = true }
    stopTimeout     = var.stop_timeout

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.this.name
        awslogs-region        = var.region
        awslogs-stream-prefix = "ecs"
      }
    }
  }
}

resource "aws_ecs_task_definition" "this" {
  family                   = "${var.cluster_name}-${var.name}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc" # every task gets its own IP address in the VPC
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = var.execution_role_arn
  task_role_arn            = var.task_role_arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }

  # Leave out the settings that are not used (null), so AWS does not see a change on every plan
  container_definitions = jsonencode([{ for key, value in local.container : key => value if value != null }])

  dynamic "volume" {
    for_each = var.efs_volumes
    content {
      name = volume.value.name
      efs_volume_configuration {
        file_system_id     = volume.value.file_system_id
        transit_encryption = "ENABLED" # encrypted on the network
        authorization_config {
          access_point_id = volume.value.access_point_id
          iam             = "ENABLED" # the task role must allow the mount
        }
      }
    }
  }
}

# ----- The service -----

resource "aws_service_discovery_service" "this" {
  count = var.create_service && var.register_dns_name ? 1 : 0

  name = var.name

  dns_config {
    namespace_id   = var.namespace_id
    routing_policy = "MULTIVALUE" # the DNS name returns every healthy task

    dns_records {
      type = "A"
      ttl  = 10
    }
  }

  # ECS tells Cloud Map which tasks are healthy
  health_check_custom_config {}
}

resource "aws_ecs_service" "this" {
  count = var.create_service ? 1 : 0

  name            = var.name
  cluster         = var.cluster_arn
  task_definition = aws_ecs_task_definition.this.arn
  desired_count   = var.desired_count

  capacity_provider_strategy {
    capacity_provider = var.use_spot ? "FARGATE_SPOT" : "FARGATE"
    weight            = 1
  }

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = var.security_group_ids
    assign_public_ip = false # private subnets: no public address
  }

  dynamic "service_registries" {
    for_each = aws_service_discovery_service.this
    content {
      registry_arn = service_registries.value.arn
    }
  }

  dynamic "load_balancer" {
    for_each = var.load_balanced ? [1] : []
    content {
      target_group_arn = var.target_group_arn
      container_name   = var.name
      container_port   = var.container_port
    }
  }

  health_check_grace_period_seconds = var.load_balanced ? var.health_check_grace_period : null

  # Rolling update without downtime: new tasks start first (up to 200%), old ones stop
  # only when the new ones are healthy (never below 100%)
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  # A new version that keeps failing to start is stopped and the previous one comes back
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  enable_execute_command  = var.enable_execute_command
  enable_ecs_managed_tags = true
  propagate_tags          = "SERVICE"

  # Do not let `terraform apply` wait until every task is healthy (that can take minutes)
  wait_for_steady_state = false
}

# ----- Autoscaling (optional) -----

locals {
  autoscaling = var.create_service && var.autoscaling_max_count != null && coalesce(var.autoscaling_max_count, 0) > var.desired_count
}

resource "aws_appautoscaling_target" "this" {
  count = local.autoscaling ? 1 : 0

  service_namespace  = "ecs"
  resource_id        = "service/${var.cluster_name}/${aws_ecs_service.this[0].name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = var.desired_count
  max_capacity       = var.autoscaling_max_count
}

resource "aws_appautoscaling_policy" "cpu" {
  count = local.autoscaling ? 1 : 0

  name               = "${var.name}-cpu"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.this[0].service_namespace
  resource_id        = aws_appautoscaling_target.this[0].resource_id
  scalable_dimension = aws_appautoscaling_target.this[0].scalable_dimension

  target_tracking_scaling_policy_configuration {
    target_value       = 70 # keep the average CPU around 70%
    scale_out_cooldown = 60
    scale_in_cooldown  = 300

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}
