# -----------------------------------------------------------------------------
# Alerts by email: with alarm_email set, CloudWatch alarms (today: the
# database's, see modules/rds-mysql/alarms.tf) email that address through an
# SNS topic.
#
# After the first apply AWS sends a "Subscription Confirmation" email: click its
# link, or no alert will ever arrive.
#
# (The topic is not KMS-encrypted on purpose: CloudWatch cannot publish to a
# topic encrypted with the AWS managed SNS key.)
# -----------------------------------------------------------------------------

locals {
  alerts_enabled = var.alarm_email != ""
}

resource "aws_sns_topic" "alerts" {
  count = local.alerts_enabled ? 1 : 0

  name = "${local.name}-alerts"
}

resource "aws_sns_topic_subscription" "alerts_email" {
  count = local.alerts_enabled ? 1 : 0

  topic_arn = aws_sns_topic.alerts[0].arn
  protocol  = "email"
  endpoint  = var.alarm_email
}
