# -----------------------------------------------------------------------------
# CloudWatch alarms of the database (create_alarms = true). Each one notifies
# alarm_sns_topic_arn when it goes off, and again when it is back to normal.
# They look at 3 periods of 5 minutes, so a short peak does not wake anybody.
# -----------------------------------------------------------------------------

locals {
  alarms = {
    cpu-high = {
      description = "The database's CPU has been above 80% for 15 minutes: slow queries (Performance Insights shows which) or a too small instance_class."
      metric      = "CPUUtilization"
      comparison  = "GreaterThanThreshold"
      threshold   = 80
    }
    free-storage-low = {
      description = "Less than 2 GiB of disk left. The disk grows by itself up to max_allocated_storage; raise that if it is reached."
      metric      = "FreeStorageSpace"
      comparison  = "LessThanThreshold"
      threshold   = 2 * pow(1024, 3)
    }
    free-memory-low = {
      description = "Less than 256 MiB of memory free for 15 minutes: consider a larger instance_class."
      metric      = "FreeableMemory"
      comparison  = "LessThanThreshold"
      threshold   = 256 * pow(1024, 2)
    }
    connections-high = {
      description = "More than ${var.alarm_max_connections} open connections: the services' connection pools may soon wait for a free connection."
      metric      = "DatabaseConnections"
      comparison  = "GreaterThanThreshold"
      threshold   = var.alarm_max_connections
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "this" {
  for_each = { for name, alarm in local.alarms : name => alarm if var.create_alarms }

  alarm_name          = "${local.identifier}-${each.key}"
  alarm_description   = each.value.description
  namespace           = "AWS/RDS"
  metric_name         = each.value.metric
  dimensions          = { DBInstanceIdentifier = aws_db_instance.this.identifier }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  datapoints_to_alarm = 3
  comparison_operator = each.value.comparison
  threshold           = each.value.threshold
  treat_missing_data  = "missing"

  alarm_actions = [var.alarm_sns_topic_arn]
  ok_actions    = [var.alarm_sns_topic_arn]
}
