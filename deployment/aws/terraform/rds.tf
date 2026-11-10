# -----------------------------------------------------------------------------
# The application's database: Amazon RDS for MySQL (modules/rds-mysql).
#
# One database server; every service has its own database and user on it
# (auth_db/auth_user, order_db/order_user ...), created by the db-init setup task
# (ecs-bootstrap.tf). Only the ECS tasks can reach it; every connection uses TLS.
#
# What the module sets up: see modules/rds-mysql/README.md. The settings below
# come from terraform.tfvars (the rds_* variables).
# -----------------------------------------------------------------------------

module "mysql" {
  source = "./modules/rds-mysql"

  name                       = local.name
  vpc_id                     = aws_vpc.this.id
  subnet_ids                 = aws_subnet.private[*].id
  allowed_security_group_ids = [aws_security_group.tasks.id]

  engine_version        = var.rds_engine_version
  instance_class        = var.rds_instance_class
  allocated_storage     = var.rds_allocated_storage
  max_allocated_storage = var.rds_max_allocated_storage
  multi_az              = var.rds_multi_az
  kms_key_arn           = var.rds_kms_key_arn == "" ? null : var.rds_kms_key_arn

  # Debezium (Kafka Connect) reads every change from MySQL's binary log
  change_data_capture = true

  backup_retention_days = var.rds_backup_retention_days
  snapshot_identifier   = var.rds_snapshot_identifier == "" ? null : var.rds_snapshot_identifier
  deletion_protection   = var.deletion_protection

  # Dev: no last snapshot on destroy, changes at once. Staging and prod: a final
  # snapshot, and changes wait for the maintenance window.
  skip_final_snapshot = var.environment == "dev"
  apply_immediately   = var.environment == "dev"

  monitoring_interval = var.rds_monitoring_interval
  log_retention_days  = var.log_retention_days

  # Emails about high CPU, low disk space, low memory, many connections (alarm_email)
  create_alarms       = local.alerts_enabled
  alarm_sns_topic_arn = local.alerts_enabled ? aws_sns_topic.alerts[0].arn : null
}

# The database used to be defined directly in this file. These blocks tell
# Terraform that it only moved into the module, so an existing database is kept
# (not deleted and created again). Harmless when nothing was created yet.
moved {
  from = aws_db_instance.this
  to   = module.mysql.aws_db_instance.this
}

moved {
  from = aws_db_subnet_group.this
  to   = module.mysql.aws_db_subnet_group.this
}

moved {
  from = aws_db_parameter_group.this
  to   = module.mysql.aws_db_parameter_group.this
}

moved {
  from = aws_security_group.data["mysql"]
  to   = module.mysql.aws_security_group.this
}

moved {
  from = aws_vpc_security_group_ingress_rule.data_from_tasks["mysql"]
  to   = module.mysql.aws_vpc_security_group_ingress_rule.from_allowed[0]
}
