# Inputs of the rds-mysql module. See main.tf for what it creates, README.md for an example.

# ===== Naming and network ======================================================

variable "name" {
  description = "Prefix of every name, e.g. grocery-dev: the database becomes grocery-dev-mysql."
  type        = string
}

variable "vpc_id" {
  description = "VPC the database lives in."
  type        = string
}

variable "subnet_ids" {
  description = "Private subnets for the database, in at least two Availability Zones (RDS requires two even without Multi-AZ)."
  type        = list(string)

  validation {
    condition     = length(var.subnet_ids) >= 2
    error_message = "subnet_ids needs at least two subnets in different Availability Zones."
  }
}

variable "allowed_security_group_ids" {
  description = "Security groups whose members may connect to MySQL (port 3306). Nobody else can."
  type        = list(string)
}

variable "allowed_from" {
  description = "Who the allowed security groups are, for the descriptions in the AWS console."
  type        = string
  default     = "the ECS tasks"
}

# ===== Engine and size =========================================================

variable "engine_version" {
  description = "MySQL version: a major version such as \"8.4\" (RDS picks the newest minor one and keeps it updated) or an exact one such as \"8.4.6\"."
  type        = string
  default     = "8.4"

  validation {
    condition     = can(regex("^8\\.(0|4)(\\.[0-9]+)?$", var.engine_version))
    error_message = "engine_version must be 8.0 or 8.4 (optionally with a minor version, e.g. 8.4.6)."
  }
}

variable "instance_class" {
  description = "Size of the database server, e.g. db.t4g.medium (2 vCPU, 4 GiB) or db.m7g.large (2 vCPU, 8 GiB)."
  type        = string
  default     = "db.t4g.medium"

  validation {
    condition     = startswith(var.instance_class, "db.")
    error_message = "instance_class must be an RDS instance class such as db.t4g.medium."
  }
}

variable "allocated_storage" {
  description = "Initial disk size in GiB (gp3)."
  type        = number
  default     = 20

  validation {
    condition     = var.allocated_storage >= 20
    error_message = "allocated_storage must be at least 20 GiB."
  }
}

variable "max_allocated_storage" {
  description = "The disk grows by itself up to this size (GiB) when it runs low. Must be larger than allocated_storage."
  type        = number
  default     = 100
}

variable "multi_az" {
  description = "true: a standby copy in another Availability Zone takes over automatically when the primary fails (about 1-2 minutes)."
  type        = bool
  default     = false
}

variable "master_username" {
  description = "Name of the admin user. Its password is created and kept by RDS in Secrets Manager."
  type        = string
  default     = "grocery_admin"

  validation {
    condition     = can(regex("^[A-Za-z][A-Za-z0-9_]{0,15}$", var.master_username)) && !contains(["admin", "root", "mysql", "rdsadmin"], lower(var.master_username))
    error_message = "master_username: 1-16 letters, digits or _, starting with a letter, and not a reserved name (admin, root, mysql, rdsadmin)."
  }
}

variable "parameters" {
  description = "Extra MySQL settings (parameter group), e.g. { long_query_time = \"1\" }. They are added to (and win over) the module's own."
  type        = map(string)
  default     = {}
}

variable "change_data_capture" {
  description = "true: keep a full row-based binary log, which Debezium (Kafka Connect) reads every change from. Needs backups (backup_retention_days >= 1)."
  type        = bool
  default     = true
}

variable "ca_cert_identifier" {
  description = "The certificate authority of the server's TLS certificate."
  type        = string
  default     = "rds-ca-rsa2048-g1"
}

# ===== Encryption ==============================================================

variable "kms_key_arn" {
  description = "Your own KMS key for the disk, the backups, the admin secret and Performance Insights (null: the AWS managed key aws/rds)."
  type        = string
  default     = null
}

# ===== Backups and maintenance =================================================

variable "backup_retention_days" {
  description = "Days of automatic backups (also the window for point-in-time restore). 0 turns backups off."
  type        = number
  default     = 7

  validation {
    condition     = var.backup_retention_days >= 0 && var.backup_retention_days <= 35
    error_message = "backup_retention_days must be between 0 and 35."
  }

  validation {
    condition     = !var.change_data_capture || var.backup_retention_days >= 1
    error_message = "With change_data_capture = true, backup_retention_days must be at least 1: RDS keeps the binary log only while backups are on."
  }
}

variable "backup_window" {
  description = "Daily backup time, UTC (hh24:mi-hh24:mi, at least 30 minutes, not overlapping the maintenance window)."
  type        = string
  default     = "19:00-20:00" # 00:30-01:30 India time
}

variable "maintenance_window" {
  description = "Weekly time for patches, UTC (ddd:hh24:mi-ddd:hh24:mi)."
  type        = string
  default     = "sun:20:30-sun:21:30" # Monday 02:00-03:00 India time
}

variable "snapshot_identifier" {
  description = "Create the database from this snapshot (its name or ARN) instead of empty, e.g. to restore a backup. null: a new, empty database."
  type        = string
  default     = null
}

variable "deletion_protection" {
  description = "true: the database cannot be deleted (not even by terraform destroy) until this is set back to false."
  type        = bool
  default     = false
}

variable "skip_final_snapshot" {
  description = "true: no last snapshot when the database is deleted (dev). false: a snapshot <name>-mysql-final is taken first."
  type        = bool
  default     = false
}

variable "delete_automated_backups" {
  description = "true: the automatic backups are deleted together with the database."
  type        = bool
  default     = true
}

variable "apply_immediately" {
  description = "true: changes are made at once (may restart the database). false: in the next maintenance window."
  type        = bool
  default     = false
}

# ===== Monitoring ==============================================================

variable "monitoring_interval" {
  description = "Seconds between Enhanced Monitoring samples (operating-system metrics: CPU per process, memory, disk). 0 turns it off."
  type        = number
  default     = 60

  validation {
    condition     = contains([0, 1, 5, 10, 15, 30, 60], var.monitoring_interval)
    error_message = "monitoring_interval must be 0, 1, 5, 10, 15, 30 or 60."
  }
}

variable "performance_insights_enabled" {
  description = "true: Performance Insights (which queries load the database; 7 days are free)."
  type        = bool
  default     = true
}

variable "log_exports" {
  description = "MySQL logs sent to CloudWatch Logs: error, slowquery, general (very noisy), audit (needs an option group)."
  type        = list(string)
  default     = ["error", "slowquery"]

  validation {
    condition     = alltrue([for log in var.log_exports : contains(["error", "slowquery", "general", "audit", "iam-db-auth-error"], log)])
    error_message = "log_exports may contain: error, slowquery, general, audit, iam-db-auth-error."
  }
}

variable "log_retention_days" {
  description = "Days CloudWatch keeps the exported MySQL logs."
  type        = number
  default     = 14
}

variable "create_alarms" {
  description = "true: CloudWatch alarms for high CPU, low disk space, low memory and too many connections, sent to alarm_sns_topic_arn."
  type        = bool
  default     = false
}

variable "alarm_sns_topic_arn" {
  description = "SNS topic the alarms notify (with create_alarms = true), e.g. one that emails you."
  type        = string
  default     = null
}

variable "alarm_max_connections" {
  description = "Alarm when more connections than this are open (the server's limit depends on its memory: about 340 for db.t4g.medium)."
  type        = number
  default     = 250
}
