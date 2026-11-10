# -----------------------------------------------------------------------------
# The ECS cluster (a name to group the services; Fargate runs the containers,
# there are no servers to manage) and the private DNS zone the services find
# each other through (AWS Cloud Map): auth-service.grocery.internal ...
# -----------------------------------------------------------------------------

resource "aws_ecs_cluster" "this" {
  name = local.name

  setting {
    name  = "containerInsights"
    value = var.enable_container_insights ? "enabled" : "disabled"
  }

  # Commands run with `aws ecs execute-command` are written to the default log
  configuration {
    execute_command_configuration {
      logging = "DEFAULT"
    }
  }
}

# Normal Fargate, and Fargate Spot (spare capacity, about 70% cheaper; see use_fargate_spot)
resource "aws_ecs_cluster_capacity_providers" "this" {
  cluster_name       = aws_ecs_cluster.this.name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
  }
}

resource "aws_service_discovery_private_dns_namespace" "this" {
  name        = local.internal_domain
  description = "Private DNS names of the ${local.name} services"
  vpc         = aws_vpc.this.id
}
