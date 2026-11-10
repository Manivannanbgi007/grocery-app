# -----------------------------------------------------------------------------
# Security groups: the firewall rules. The idea, from the outside in:
#
#   internet --80/443--> alb --80/8080--> tasks (every ECS container)
#   tasks --> each other (service to service, inside the VPC)
#   tasks --3306--> mysql   --9092--> kafka   --6379--> redis   --2049--> efs
#
# The data stores accept connections ONLY from the tasks' security group.
# (MySQL's security group is part of the RDS module: modules/rds-mysql.)
# Rules are separate resources (aws_vpc_security_group_*_rule), as AWS recommends.
# -----------------------------------------------------------------------------

# ----- Load balancer -----

resource "aws_security_group" "alb" {
  name        = "${local.name}-alb"
  description = "Public load balancer of ${local.name}"
  vpc_id      = aws_vpc.this.id
  tags        = { Name = "${local.name}-alb" }
}

resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  for_each = toset(var.allowed_ingress_cidrs)

  security_group_id = aws_security_group.alb.id
  description       = "HTTP from ${each.value}"
  cidr_ipv4         = each.value
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
}

resource "aws_vpc_security_group_ingress_rule" "alb_https" {
  for_each = local.https_enabled ? toset(var.allowed_ingress_cidrs) : toset([])

  security_group_id = aws_security_group.alb.id
  description       = "HTTPS from ${each.value}"
  cidr_ipv4         = each.value
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_egress_rule" "alb_to_tasks" {
  for_each = toset(["80", "8080"])

  security_group_id            = aws_security_group.alb.id
  description                  = "To the frontend (80) and the API gateway (8080)"
  referenced_security_group_id = aws_security_group.tasks.id
  ip_protocol                  = "tcp"
  from_port                    = tonumber(each.value)
  to_port                      = tonumber(each.value)
}

# ----- ECS tasks (all containers share this group) -----

resource "aws_security_group" "tasks" {
  name        = "${local.name}-tasks"
  description = "ECS tasks of ${local.name}"
  vpc_id      = aws_vpc.this.id
  tags        = { Name = "${local.name}-tasks" }
}

resource "aws_vpc_security_group_ingress_rule" "tasks_from_alb" {
  for_each = toset(["80", "8080"])

  security_group_id            = aws_security_group.tasks.id
  description                  = "From the load balancer"
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = tonumber(each.value)
  to_port                      = tonumber(each.value)
}

resource "aws_vpc_security_group_ingress_rule" "tasks_from_tasks" {
  security_group_id            = aws_security_group.tasks.id
  description                  = "Service to service (8080 services, 8081 Schema Registry, 8083 Kafka Connect, 9200 Elasticsearch)"
  referenced_security_group_id = aws_security_group.tasks.id
  ip_protocol                  = "tcp"
  from_port                    = 1024
  to_port                      = 65535
}

resource "aws_vpc_security_group_egress_rule" "tasks_all" {
  security_group_id = aws_security_group.tasks.id
  description       = "Outbound: data stores, other tasks, AWS APIs, and the internet through NAT (Razorpay, SMTP, public images)"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# ----- Data stores: only the tasks may connect -----

locals {
  # name => port of every data store security group
  data_store_ports = {
    kafka = 9092 # plaintext listener inside the VPC (see msk.tf for why)
    redis = 6379
    efs   = 2049
  }
}

resource "aws_security_group" "data" {
  for_each = local.data_store_ports

  name        = "${local.name}-${each.key}"
  description = "${each.key} of ${local.name}: reachable from the ECS tasks only"
  vpc_id      = aws_vpc.this.id
  tags        = { Name = "${local.name}-${each.key}" }
}

resource "aws_vpc_security_group_ingress_rule" "data_from_tasks" {
  for_each = local.data_store_ports

  security_group_id            = aws_security_group.data[each.key].id
  description                  = "${each.key} from the ECS tasks"
  referenced_security_group_id = aws_security_group.tasks.id
  ip_protocol                  = "tcp"
  from_port                    = each.value
  to_port                      = each.value
}

# The Kafka brokers share the group: let them talk to each other
resource "aws_vpc_security_group_ingress_rule" "kafka_between_brokers" {
  security_group_id            = aws_security_group.data["kafka"].id
  description                  = "Between the Kafka brokers"
  referenced_security_group_id = aws_security_group.data["kafka"].id
  ip_protocol                  = "-1"
}
