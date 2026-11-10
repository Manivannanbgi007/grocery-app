# -----------------------------------------------------------------------------
# Redis (Amazon ElastiCache): the API gateway's rate limits and the live
# delivery positions. Encrypted in transit (TLS) and at rest.
#
# The users are the same as on a laptop (infrastructure/docker-compose.yml):
#   default   switched off
#   gateway   only the rate limiter's keys (api-gateway)
#   delivery  only delivery:* keys and the delivery-tracking channel (delivery-service)
# -----------------------------------------------------------------------------

resource "aws_elasticache_subnet_group" "this" {
  name       = "${local.name}-redis"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_elasticache_parameter_group" "this" {
  name   = "${local.name}-redis7"
  family = "redis7"

  # Like on a laptop: when memory is full, forget the least used keys
  parameter {
    name  = "maxmemory-policy"
    value = "allkeys-lru"
  }
}

locals {
  # user => Redis ACL rules (the same rules as infrastructure/docker-compose.yml)
  redis_users = {
    default  = "off -@all"
    gateway  = "on ~request_rate_limiter.* -@all +@connection +info +evalsha +eval +get +setex +time"
    delivery = "on ~delivery:* &delivery-tracking -@all +@connection +info +get +set +del +exists +expire +publish +subscribe +unsubscribe"
  }
}

resource "aws_elasticache_user" "this" {
  for_each = local.redis_users

  user_id       = "${local.name}-${each.key}"
  user_name     = each.key
  access_string = each.value
  engine        = "redis"
  passwords     = [random_password.redis[each.key].result]
}

resource "aws_elasticache_user_group" "this" {
  user_group_id = "${local.name}-users"
  engine        = "redis"
  user_ids      = [for user in aws_elasticache_user.this : user.user_id]
}

resource "aws_elasticache_replication_group" "this" {
  replication_group_id = "${local.name}-redis"
  description          = "Redis of ${local.name}: rate limits and delivery positions"

  engine               = "redis"
  engine_version       = "7.1"
  node_type            = var.redis_node_type
  num_cache_clusters   = var.redis_node_count
  parameter_group_name = aws_elasticache_parameter_group.this.name
  port                 = 6379

  # With 2+ nodes a replica in another zone takes over when the primary fails
  automatic_failover_enabled = var.redis_node_count > 1
  multi_az_enabled           = var.redis_node_count > 1

  subnet_group_name  = aws_elasticache_subnet_group.this.name
  security_group_ids = [aws_security_group.data["redis"].id]

  at_rest_encryption_enabled = true
  transit_encryption_enabled = true # required for users and passwords
  user_group_ids             = [aws_elasticache_user_group.this.user_group_id]

  # The data is short-lived (rate-limit counters, last positions): no snapshots needed
  snapshot_retention_limit = 0
  apply_immediately        = var.environment == "dev"
}
