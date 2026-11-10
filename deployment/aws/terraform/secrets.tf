# -----------------------------------------------------------------------------
# Secrets.
#
# Terraform generates every password and key the application needs (nothing is
# typed in or written in a file) and keeps them in AWS Secrets Manager:
#   <name>/app       generated: database passwords, JWT secret, Redis passwords ...
#   <name>/external  given by you (sensitive variables): Razorpay keys, SMTP password
# The database's admin password is generated and kept by RDS itself (rds.tf).
#
# ECS reads the secrets when a container starts and hands them over as
# environment variables; they never appear in a task definition.
#
# Note: the generated values are also in the Terraform state, so keep the state
# private (README.md, "Remote state").
# -----------------------------------------------------------------------------

# One database per service; each has its own MySQL user <db>_user in <db>_db
resource "random_password" "database" {
  for_each = toset(local.databases)

  length  = 32
  special = false # only letters and digits: safe in JDBC URLs, SQL and shell scripts
}

# The MySQL user Debezium reads the binary log with (kafka-connect)
resource "random_password" "debezium" {
  length  = 32
  special = false
}

# Signs the login tokens (auth-service creates them, api-gateway checks them)
resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

# AES-256 key for the password-reset tokens inside Kafka events: 32 random bytes, base64 encoded
resource "random_id" "password_reset_event_key" {
  byte_length = 32
}

# The Redis users (elasticache.tf): "default" is switched off, the others have limited rights
resource "random_password" "redis" {
  for_each = toset(["default", "gateway", "delivery"])

  length  = 32
  special = false
}

# Shared key between the AI assistant's two services
resource "random_password" "mcp_api_key" {
  length  = 48
  special = false
}

locals {
  # The names of the keys in the <name>/app secret (a plain list, so it can be used in for_each)
  app_secret_keys = concat(
    [for db in local.databases : "${upper(db)}_DB_PASSWORD"],
    ["DEBEZIUM_DB_PASSWORD", "JWT_SECRET", "PASSWORD_RESET_EVENT_KEY", "REDIS_GATEWAY_PASSWORD", "REDIS_DELIVERY_PASSWORD", "MCP_API_KEY"],
  )

  app_secret_values = merge(
    { for db in local.databases : "${upper(db)}_DB_PASSWORD" => random_password.database[db].result },
    {
      DEBEZIUM_DB_PASSWORD     = random_password.debezium.result
      JWT_SECRET               = random_password.jwt_secret.result
      PASSWORD_RESET_EVENT_KEY = random_id.password_reset_event_key.b64_std
      REDIS_GATEWAY_PASSWORD   = random_password.redis["gateway"].result
      REDIS_DELIVERY_PASSWORD  = random_password.redis["delivery"].result
      MCP_API_KEY              = random_password.mcp_api_key.result
    },
  )

  external_secret_keys = ["RAZORPAY_KEY_SECRET", "RAZORPAY_WEBHOOK_SECRET", "MAIL_PASSWORD"]

  # How a container asks ECS for one key of a JSON secret: "<secret ARN>:<key>::"
  app_secret      = { for key in local.app_secret_keys : key => "${aws_secretsmanager_secret.app.arn}:${key}::" }
  external_secret = { for key in local.external_secret_keys : key => "${aws_secretsmanager_secret.external.arn}:${key}::" }
}

resource "aws_secretsmanager_secret" "app" {
  name                    = "${local.name}/app"
  description             = "Generated secrets of the ${local.name} application (database passwords, JWT secret, Redis passwords ...)."
  recovery_window_in_days = var.secret_recovery_window_days
}

resource "aws_secretsmanager_secret_version" "app" {
  secret_id     = aws_secretsmanager_secret.app.id
  secret_string = jsonencode(local.app_secret_values)
}

resource "aws_secretsmanager_secret" "external" {
  name                    = "${local.name}/external"
  description             = "Secrets from outside providers for ${local.name} (Razorpay, SMTP). Empty values are allowed."
  recovery_window_in_days = var.secret_recovery_window_days
}

resource "aws_secretsmanager_secret_version" "external" {
  secret_id = aws_secretsmanager_secret.external.id
  secret_string = jsonencode({
    RAZORPAY_KEY_SECRET     = var.razorpay_key_secret
    RAZORPAY_WEBHOOK_SECRET = var.razorpay_webhook_secret
    MAIL_PASSWORD           = var.mail_password
  })
}
