# -----------------------------------------------------------------------------
# rds-mysql: an Amazon RDS for MySQL database server, set up the way AWS
# recommends. Creates
#
#   aws_security_group            only allowed_security_group_ids may connect (3306)
#   aws_db_subnet_group           private subnets in several zones
#   aws_db_parameter_group        MySQL settings: TLS required, utf8mb4, slow query
#                                 log, and (change_data_capture) the binary log for Debezium
#   aws_db_instance               encrypted gp3 disk that grows by itself, admin password
#                                 kept by RDS in Secrets Manager (rotated by RDS), backups,
#                                 not reachable from the internet
#   CloudWatch log groups         the error and slow query logs, with a retention
#   IAM role                      for Enhanced Monitoring (when monitoring_interval > 0)
#   CloudWatch alarms             (create_alarms) CPU, disk space, memory, connections
#
# It creates the server only. The databases and users inside it (one per
# service) are created by the db-init setup task (ecs-bootstrap.tf).
# -----------------------------------------------------------------------------

locals {
  identifier = "${var.name}-mysql"

  # "8.4" or "8.4.6" -> parameter group family "mysql8.4"
  major_version = join(".", slice(split(".", var.engine_version), 0, 2))

  parameters = merge(
    {
      require_secure_transport   = "1"                  # every connection must use TLS
      character_set_server       = "utf8mb4"            # all of Unicode, emojis included
      collation_server           = "utf8mb4_0900_ai_ci" # MySQL 8's default collation
      slow_query_log             = "1"                  # log queries slower than long_query_time ...
      long_query_time            = "2"                  # ... seconds
      log_output                 = "FILE"               # needed to send the logs to CloudWatch
      innodb_print_all_deadlocks = "1"                  # every deadlock goes to the error log
    },
    # Debezium reads every change of every row from the binary log
    { for name, value in { binlog_format = "ROW", binlog_row_image = "FULL" } : name => value if var.change_data_capture },
    var.parameters,
  )
}

# ----- Network -----

resource "aws_security_group" "this" {
  name        = local.identifier
  description = "mysql of ${var.name}: reachable from ${var.allowed_from} only"
  vpc_id      = var.vpc_id
  tags        = { Name = local.identifier }
}

resource "aws_vpc_security_group_ingress_rule" "from_allowed" {
  count = length(var.allowed_security_group_ids)

  security_group_id            = aws_security_group.this.id
  description                  = "mysql from ${var.allowed_from}"
  referenced_security_group_id = var.allowed_security_group_ids[count.index]
  ip_protocol                  = "tcp"
  from_port                    = 3306
  to_port                      = 3306
}

resource "aws_db_subnet_group" "this" {
  name       = local.identifier
  subnet_ids = var.subnet_ids
  tags       = { Name = local.identifier }
}

# ----- MySQL settings -----

resource "aws_db_parameter_group" "this" {
  name   = "${local.identifier}${replace(local.major_version, ".", "")}" # e.g. grocery-dev-mysql84
  family = "mysql${local.major_version}"

  dynamic "parameter" {
    for_each = local.parameters
    content {
      name  = parameter.key
      value = parameter.value
    }
  }

  # A new group (e.g. after a major version change) is created before the old one is removed
  lifecycle {
    create_before_destroy = true
  }
}

# ----- Logs and monitoring -----

# RDS would create these by itself, but without an end date: created here, with a retention
resource "aws_cloudwatch_log_group" "this" {
  for_each = toset(var.log_exports)

  name              = "/aws/rds/instance/${local.identifier}/${each.key}"
  retention_in_days = var.log_retention_days
}

# Enhanced Monitoring: RDS's monitoring service writes the operating-system metrics with this role
resource "aws_iam_role" "monitoring" {
  count = var.monitoring_interval > 0 ? 1 : 0

  name = "${local.identifier}-monitoring"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "monitoring.rds.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "monitoring" {
  count = var.monitoring_interval > 0 ? 1 : 0

  role       = aws_iam_role.monitoring[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

# ----- The database server -----

resource "aws_db_instance" "this" {
  identifier = local.identifier

  engine                      = "mysql"
  engine_version              = var.engine_version
  auto_minor_version_upgrade  = true  # security patches in the maintenance window
  allow_major_version_upgrade = false # a major upgrade (8.4 -> 9) is a planned step, never a side effect
  instance_class              = var.instance_class
  parameter_group_name        = aws_db_parameter_group.this.name
  ca_cert_identifier          = var.ca_cert_identifier

  # Disk: gp3, encrypted, grows by itself up to max_allocated_storage
  storage_type          = "gp3"
  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage
  storage_encrypted     = true
  kms_key_id            = var.kms_key_arn

  # The admin user: RDS creates its password, keeps it in Secrets Manager and rotates it
  username                      = var.master_username
  manage_master_user_password   = true
  master_user_secret_kms_key_id = var.kms_key_arn

  # Restore from a snapshot (null: a new, empty database)
  snapshot_identifier = var.snapshot_identifier

  # Private: only the allowed security groups can connect
  port                   = 3306
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.this.id]
  publicly_accessible    = false
  multi_az               = var.multi_az

  backup_retention_period  = var.backup_retention_days
  backup_window            = var.backup_window
  maintenance_window       = var.maintenance_window
  copy_tags_to_snapshot    = true
  delete_automated_backups = var.delete_automated_backups

  enabled_cloudwatch_logs_exports       = var.log_exports
  monitoring_interval                   = var.monitoring_interval
  monitoring_role_arn                   = var.monitoring_interval > 0 ? aws_iam_role.monitoring[0].arn : null
  performance_insights_enabled          = var.performance_insights_enabled
  performance_insights_retention_period = var.performance_insights_enabled ? 7 : null # 7 days: free
  performance_insights_kms_key_id       = var.performance_insights_enabled ? var.kms_key_arn : null

  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = "${local.identifier}-final"
  apply_immediately         = var.apply_immediately

  lifecycle {
    # The snapshot is only read when the database is created. Without this, changing
    # snapshot_identifier later would make Terraform DELETE the database and create a
    # new one from the snapshot.
    ignore_changes = [snapshot_identifier]
  }

  depends_on = [aws_cloudwatch_log_group.this, aws_iam_role_policy_attachment.monitoring]
}
