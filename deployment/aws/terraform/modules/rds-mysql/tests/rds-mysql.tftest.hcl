# -----------------------------------------------------------------------------
# Tests of the rds-mysql module, without an AWS account (fake providers):
#
#   cd deployment/aws/terraform/modules/rds-mysql
#   terraform init
#   terraform test
# -----------------------------------------------------------------------------

mock_provider "aws" {
  override_during = plan

  mock_resource "aws_db_instance" {
    defaults = {
      master_user_secret = [{ secret_arn = "arn:aws:secretsmanager:ap-south-1:123456789012:secret:rds-master", kms_key_id = "", secret_status = "active" }]
    }
  }
  mock_resource "aws_iam_role" {
    defaults = { arn = "arn:aws:iam::123456789012:role/monitoring" }
  }
}

variables {
  name                       = "grocery-test"
  vpc_id                     = "vpc-0123456789abcdef0"
  subnet_ids                 = ["subnet-0aaaaaaaaaaaaaaaa", "subnet-0bbbbbbbbbbbbbbbb"]
  allowed_security_group_ids = ["sg-0123456789abcdef0"]
}

run "secure_defaults" {
  command = plan

  assert {
    condition     = aws_db_instance.this.engine == "mysql" && aws_db_instance.this.engine_version == "8.4" && aws_db_instance.this.identifier == "grocery-test-mysql"
    error_message = "Expected MySQL 8.4 named grocery-test-mysql."
  }

  assert {
    condition     = aws_db_instance.this.storage_encrypted && !aws_db_instance.this.publicly_accessible && aws_db_instance.this.manage_master_user_password
    error_message = "The database must be encrypted, private, and its admin password kept by RDS."
  }

  assert {
    condition     = aws_db_instance.this.storage_type == "gp3" && aws_db_instance.this.max_allocated_storage == 100
    error_message = "The disk must be gp3 and grow by itself."
  }

  assert {
    condition     = aws_db_parameter_group.this.family == "mysql8.4" && aws_db_parameter_group.this.name == "grocery-test-mysql84"
    error_message = "The parameter group must be for MySQL 8.4."
  }

  assert {
    condition = alltrue([
      for setting in ["require_secure_transport=1", "binlog_format=ROW", "binlog_row_image=FULL", "character_set_server=utf8mb4", "slow_query_log=1"] :
      contains([for parameter in aws_db_parameter_group.this.parameter : "${parameter.name}=${parameter.value}"], setting)
    ])
    error_message = "TLS must be required, the binary log row-based and full (Debezium), the character set utf8mb4, the slow query log on."
  }

  assert {
    condition     = length(aws_vpc_security_group_ingress_rule.from_allowed) == 1 && aws_vpc_security_group_ingress_rule.from_allowed[0].from_port == 3306
    error_message = "Only the allowed security group may connect, on 3306."
  }

  assert {
    condition     = length(aws_cloudwatch_log_group.this) == 2 && length(aws_iam_role.monitoring) == 1 && aws_db_instance.this.monitoring_interval == 60
    error_message = "The error and slow query logs must go to CloudWatch, and Enhanced Monitoring must be on."
  }

  assert {
    condition     = length(aws_cloudwatch_metric_alarm.this) == 0
    error_message = "No alarms without create_alarms."
  }
}

run "alarms_without_change_data_capture_or_monitoring" {
  command = plan

  variables {
    create_alarms       = true
    alarm_sns_topic_arn = "arn:aws:sns:ap-south-1:123456789012:alerts"
    change_data_capture = false
    monitoring_interval = 0
  }

  assert {
    condition     = length(aws_cloudwatch_metric_alarm.this) == 4 && aws_cloudwatch_metric_alarm.this["free-storage-low"].threshold == 2147483648
    error_message = "Expected 4 alarms (CPU, disk space below 2 GiB, memory, connections)."
  }

  assert {
    condition     = !contains([for parameter in aws_db_parameter_group.this.parameter : parameter.name], "binlog_format")
    error_message = "Without change_data_capture the binary log settings must not be set."
  }

  assert {
    condition     = length(aws_iam_role.monitoring) == 0 && aws_db_instance.this.monitoring_interval == 0
    error_message = "With monitoring_interval = 0 there is no monitoring role."
  }
}

run "change_data_capture_needs_backups" {
  command = plan

  variables {
    backup_retention_days = 0
  }

  expect_failures = [var.backup_retention_days]
}

run "reserved_admin_name_is_refused" {
  command = plan

  variables {
    master_username = "admin"
  }

  expect_failures = [var.master_username]
}
