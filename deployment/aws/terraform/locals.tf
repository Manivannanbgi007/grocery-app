# -----------------------------------------------------------------------------
# Names and addresses used all over the configuration, computed once here.
# -----------------------------------------------------------------------------

# The zones of the region that every account can use (not the opt-in ones)
data "aws_availability_zones" "available" {
  state = "available"

  filter {
    name   = "opt-in-status"
    values = ["opt-in-not-required"]
  }
}

data "aws_caller_identity" "current" {}

locals {
  # Every resource name starts with this, e.g. "grocery-dev-alb"
  name = "${var.project}-${var.environment}"

  azs = slice(data.aws_availability_zones.available.names, 0, var.az_count)

  common_tags = {
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }

  # ----- The public address of the shop -----
  https_enabled = var.certificate_arn != ""
  public_host   = var.domain_name != "" ? var.domain_name : aws_lb.this.dns_name
  public_url    = "${local.https_enabled ? "https" : "http"}://${local.public_host}"

  # ----- Addresses inside the VPC -----
  # Every ECS service gets a DNS name in this private zone (AWS Cloud Map), e.g. auth-service.grocery.internal
  internal_domain = "${var.project}.internal"

  mysql_host               = module.mysql.address # the RDS MySQL server (rds.tf)
  mysql_port               = module.mysql.port
  kafka_bootstrap_servers  = aws_msk_cluster.this.bootstrap_brokers # host:9092,host:9092 (inside the VPC only)
  kafka_replication_factor = min(var.az_count, 3)                   # every Kafka message is kept on this many brokers
  schema_registry_url      = "http://schema-registry.${local.internal_domain}:8081"
  kafka_connect_url        = "http://kafka-connect.${local.internal_domain}:8083"
  redis_host               = aws_elasticache_replication_group.this.primary_endpoint_address
  run_elasticsearch        = var.elasticsearch_url == ""
  elasticsearch_url        = local.run_elasticsearch ? "http://elasticsearch.${local.internal_domain}:9200" : var.elasticsearch_url

  # ----- Container images -----
  ecr_registry = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
}
