# -----------------------------------------------------------------------------
# Input variables. Set them in terraform.tfvars (or with TF_VAR_<name>
# environment variables for secrets). Every variable has a description and a
# safe default where one makes sense.
# -----------------------------------------------------------------------------

# ===== General =================================================================

variable "aws_region" {
  description = "AWS region to deploy into, e.g. ap-south-1 (Mumbai) or us-east-1 (N. Virginia)."
  type        = string
  default     = "ap-south-1"

  validation {
    condition     = can(regex("^[a-z]{2}(-gov)?-[a-z]+-[0-9]$", var.aws_region))
    error_message = "aws_region must be an AWS region code such as ap-south-1 or us-east-1."
  }
}

variable "project" {
  description = "Short project name, used at the start of every resource name (e.g. grocery-dev-alb)."
  type        = string
  default     = "grocery"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,13}$", var.project))
    error_message = "project must be 2-14 characters: lowercase letters, digits and hyphens, starting with a letter."
  }
}

variable "environment" {
  description = "Which environment this is: dev, staging or prod. Part of every resource name."
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be one of: dev, staging, prod."
  }
}

# ===== Network =================================================================

variable "vpc_cidr" {
  description = "IP range of the new VPC. Must be a /16, e.g. 10.20.0.0/16 (the subnets are cut from it)."
  type        = string
  default     = "10.20.0.0/16"

  validation {
    condition     = can(cidrnetmask(var.vpc_cidr)) && endswith(var.vpc_cidr, "/16")
    error_message = "vpc_cidr must be a valid /16 range, e.g. 10.20.0.0/16."
  }
}

variable "az_count" {
  description = "How many Availability Zones to spread over (2 or 3). Kafka gets one broker per zone."
  type        = number
  default     = 2

  validation {
    condition     = contains([2, 3], var.az_count)
    error_message = "az_count must be 2 or 3."
  }
}

variable "single_nat_gateway" {
  description = "true: one NAT gateway for all zones (cheaper, fine for dev). false: one per zone (keeps outbound internet when a zone fails; use for prod)."
  type        = bool
  default     = true
}

# ===== Public access (load balancer) ===========================================

variable "certificate_arn" {
  description = "ARN of an ACM certificate for HTTPS (in the same region). Empty = plain HTTP only. Without HTTPS the login does not survive a page reload (the refresh cookie needs HTTPS)."
  type        = string
  default     = ""

  validation {
    condition     = var.certificate_arn == "" || can(regex("^arn:aws:acm:", var.certificate_arn))
    error_message = "certificate_arn must be empty or an ACM certificate ARN (arn:aws:acm:...)."
  }
}

variable "domain_name" {
  description = "Your own domain for the shop, e.g. shop.example.com (point a DNS record at the load balancer yourself). Empty = use the load balancer's AWS name."
  type        = string
  default     = ""

  validation {
    condition     = var.domain_name == "" || can(regex("^([a-z0-9-]+\\.)+[a-z]{2,}$", var.domain_name))
    error_message = "domain_name must be empty or a host name such as shop.example.com."
  }
}

variable "allowed_ingress_cidrs" {
  description = "Who may open the shop: IP ranges allowed to reach the load balancer. [\"0.0.0.0/0\"] = everyone; use your own IP (e.g. [\"203.0.113.7/32\"]) while testing."
  type        = list(string)
  default     = ["0.0.0.0/0"]

  validation {
    condition     = length(var.allowed_ingress_cidrs) > 0 && alltrue([for cidr in var.allowed_ingress_cidrs : can(cidrnetmask(cidr))])
    error_message = "allowed_ingress_cidrs must be a non-empty list of CIDR ranges, e.g. [\"0.0.0.0/0\"]."
  }
}

variable "alb_idle_timeout" {
  description = "Seconds the load balancer keeps a quiet connection open. At least 180: the live order tracking and the AI chat keep connections open that long."
  type        = number
  default     = 300

  validation {
    condition     = var.alb_idle_timeout >= 180 && var.alb_idle_timeout <= 4000
    error_message = "alb_idle_timeout must be between 180 and 4000 seconds."
  }
}

variable "expose_api_docs" {
  description = "true: the Swagger UI (/swagger-ui.html) is reachable from the internet. Keep false outside dev."
  type        = bool
  default     = false
}

# ===== Deployment ==============================================================

variable "deploy_services" {
  description = "false (first apply): create the infrastructure only. true: also start the ECS services. Set it to true after pushing the images and running the one-time setup tasks (README.md)."
  type        = bool
  default     = false
}

variable "image_tag" {
  description = "Tag of the images in ECR to run (the tag you pushed with scripts/push-images). Tags cannot be overwritten in ECR, so use a new one per release, e.g. 1.0.1."
  type        = string
  default     = "1.0.0"

  validation {
    condition     = can(regex("^[A-Za-z0-9._-]{1,128}$", var.image_tag)) && var.image_tag != "latest"
    error_message = "image_tag must be a version such as 1.0.0 (letters, digits, . _ -), and not \"latest\" (ECR tags here are immutable)."
  }
}

variable "load_demo_data" {
  description = "true: load the sample stores, products and demo accounts (password Password@123, public in the repository!). Only for dev; never true in prod."
  type        = bool
  default     = false

  validation {
    condition     = !(var.load_demo_data && var.environment == "prod")
    error_message = "load_demo_data must be false in prod: the demo accounts have publicly known passwords."
  }
}

variable "use_fargate_spot" {
  description = "true: run the application services on Fargate Spot (about 70% cheaper; AWS may stop a task with 2 minutes' notice and ECS starts a new one). Data and Kafka services always use normal Fargate."
  type        = bool
  default     = false
}

variable "enable_execute_command" {
  description = "true: allows `aws ecs execute-command` (a shell inside a running container) for debugging."
  type        = bool
  default     = true
}

variable "service_overrides" {
  description = "Per-service sizing, e.g. { \"order-service\" = { cpu = 1024, memory = 2048, desired_count = 2 } }. cpu: 256, 512, 1024 ...; memory in MiB, a valid Fargate combination."
  type = map(object({
    cpu           = optional(number)
    memory        = optional(number)
    desired_count = optional(number)
  }))
  default = {}
}

variable "autoscaling_max_count" {
  description = "Most tasks the busiest services (api-gateway, product-read-service, frontend) scale out to when their CPU stays above 70%."
  type        = number
  default     = 4

  validation {
    condition     = var.autoscaling_max_count >= 2 && var.autoscaling_max_count <= 20
    error_message = "autoscaling_max_count must be between 2 and 20."
  }
}

variable "cpu_architecture" {
  description = "CPU type of the images: X86_64 (images built on a normal PC) or ARM64 (images built for Graviton)."
  type        = string
  default     = "X86_64"

  validation {
    condition     = contains(["X86_64", "ARM64"], var.cpu_architecture)
    error_message = "cpu_architecture must be X86_64 or ARM64."
  }
}

# ===== Logs ====================================================================

variable "log_retention_days" {
  description = "How many days CloudWatch keeps the containers' logs."
  type        = number
  default     = 14

  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1096, 1827, 2192, 2557, 2922, 3288, 3653], var.log_retention_days)
    error_message = "log_retention_days must be a value CloudWatch accepts, e.g. 7, 14, 30, 90 or 365."
  }
}

variable "enable_container_insights" {
  description = "true: CloudWatch Container Insights (CPU, memory and network graphs per service; small extra cost)."
  type        = bool
  default     = true
}

# ===== Application settings ====================================================

variable "payment_gateway" {
  description = "Payment provider: \"mock\" (test payments, no money moves) or \"razorpay\" (needs the razorpay_* settings)."
  type        = string
  default     = "mock"

  validation {
    condition     = contains(["mock", "razorpay"], var.payment_gateway)
    error_message = "payment_gateway must be \"mock\" or \"razorpay\"."
  }

  validation {
    condition     = var.payment_gateway == "mock" || (var.razorpay_key_id != "" && var.razorpay_key_secret != "" && var.razorpay_webhook_secret != "")
    error_message = "With payment_gateway = \"razorpay\", set razorpay_key_id, razorpay_key_secret and razorpay_webhook_secret."
  }
}

variable "razorpay_key_id" {
  description = "Razorpay key id (rzp_test_... or rzp_live_...). Only used when payment_gateway = \"razorpay\"."
  type        = string
  default     = ""
}

variable "razorpay_mode" {
  description = "Razorpay mode: \"test\" or \"live\"."
  type        = string
  default     = "test"

  validation {
    condition     = contains(["test", "live"], var.razorpay_mode)
    error_message = "razorpay_mode must be \"test\" or \"live\"."
  }
}

variable "razorpay_key_secret" {
  description = "Razorpay key secret. Secret: set it with the TF_VAR_razorpay_key_secret environment variable, never in a file in git."
  type        = string
  default     = ""
  sensitive   = true
}

variable "razorpay_webhook_secret" {
  description = "Razorpay webhook secret. Secret: set it with TF_VAR_razorpay_webhook_secret."
  type        = string
  default     = ""
  sensitive   = true
}

variable "mail_host" {
  description = "SMTP server for the platform's emails, e.g. email-smtp.ap-south-1.amazonaws.com (Amazon SES). Empty = no emails are sent (in-app notifications still work)."
  type        = string
  default     = ""
}

variable "mail_port" {
  description = "SMTP port (587 for Amazon SES with STARTTLS)."
  type        = number
  default     = 587
}

variable "mail_from" {
  description = "Sender address of the emails. With Amazon SES it must be a verified address or domain."
  type        = string
  default     = "no-reply@example.com"
}

variable "mail_username" {
  description = "SMTP user name (for Amazon SES: the SMTP credentials' user name). Empty = no SMTP login."
  type        = string
  default     = ""
}

variable "mail_password" {
  description = "SMTP password. Secret: set it with TF_VAR_mail_password."
  type        = string
  default     = ""
  sensitive   = true
}

variable "enable_ai_assistant" {
  description = "true: also deploy the AI shopping assistant (mcp-client-service, mcp-server-service). It needs an Ollama server you run yourself (e.g. on a GPU EC2 instance): set ollama_base_url."
  type        = bool
  default     = false

  validation {
    condition     = !var.enable_ai_assistant || var.ollama_base_url != ""
    error_message = "With enable_ai_assistant = true, set ollama_base_url (e.g. http://10.20.16.50:11434)."
  }
}

variable "ollama_base_url" {
  description = "Address of the Ollama server for the AI assistant, reachable from the VPC."
  type        = string
  default     = ""
}

variable "speech_base_url" {
  description = "Address of the speaches server (voice for the AI assistant). Empty = the assistant works without voice."
  type        = string
  default     = ""
}

# ===== MySQL (Amazon RDS) ======================================================

variable "rds_engine_version" {
  description = "MySQL version of the RDS database: \"8.4\" (the newest 8.4.x, kept patched) or an exact one such as \"8.4.6\". The app is tested with 8.4."
  type        = string
  default     = "8.4"

  validation {
    condition     = can(regex("^8\\.4(\\.[0-9]+)?$", var.rds_engine_version))
    error_message = "rds_engine_version must be 8.4 or an 8.4.x version (the version the app runs on locally)."
  }
}

variable "rds_instance_class" {
  description = "Size of the MySQL database server, e.g. db.t4g.medium (2 vCPU, 4 GiB) for dev, db.m7g.large (2 vCPU, 8 GiB) for prod. 13 service databases share it."
  type        = string
  default     = "db.t4g.medium"

  validation {
    condition     = startswith(var.rds_instance_class, "db.")
    error_message = "rds_instance_class must be an RDS instance class such as db.t4g.medium."
  }
}

variable "rds_allocated_storage" {
  description = "Initial disk size of the database in GiB."
  type        = number
  default     = 20

  validation {
    condition     = var.rds_allocated_storage >= 20
    error_message = "rds_allocated_storage must be at least 20 GiB."
  }
}

variable "rds_max_allocated_storage" {
  description = "The database disk grows by itself up to this size in GiB when it fills up."
  type        = number
  default     = 100
}

variable "rds_multi_az" {
  description = "true: a standby copy of the database in a second zone takes over on failure (doubles the database cost). Recommended for prod."
  type        = bool
  default     = false
}

variable "rds_backup_retention_days" {
  description = "Days of automatic database backups. At least 1: MySQL keeps its binary log (which Debezium reads) only while backups are on."
  type        = number
  default     = 7

  validation {
    condition     = var.rds_backup_retention_days >= 1 && var.rds_backup_retention_days <= 35
    error_message = "rds_backup_retention_days must be between 1 and 35 (Debezium needs the binary log, which needs backups)."
  }
}

variable "rds_monitoring_interval" {
  description = "Seconds between RDS Enhanced Monitoring samples (CPU per process, memory, disk of the database server). 0 turns it off (it costs a little)."
  type        = number
  default     = 60

  validation {
    condition     = contains([0, 1, 5, 10, 15, 30, 60], var.rds_monitoring_interval)
    error_message = "rds_monitoring_interval must be 0, 1, 5, 10, 15, 30 or 60."
  }
}

variable "rds_kms_key_arn" {
  description = "ARN of your own KMS key to encrypt the database, its backups and its admin secret. Empty = the AWS managed key (aws/rds), fine for most cases."
  type        = string
  default     = ""

  validation {
    condition     = var.rds_kms_key_arn == "" || can(regex("^arn:aws:kms:", var.rds_kms_key_arn))
    error_message = "rds_kms_key_arn must be empty or a KMS key ARN (arn:aws:kms:...)."
  }
}

variable "rds_snapshot_identifier" {
  description = "Create the database from this RDS snapshot (name or ARN), e.g. to restore a backup into a new environment. Empty = a new, empty database. Only read when the database is created."
  type        = string
  default     = ""
}

variable "alarm_email" {
  description = "Email address for alerts (database CPU, disk space, memory, connections). Empty = no alarms. Confirm the subscription email AWS sends after the first apply."
  type        = string
  default     = ""

  validation {
    condition     = var.alarm_email == "" || can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.alarm_email))
    error_message = "alarm_email must be empty or an email address."
  }
}

variable "deletion_protection" {
  description = "true: the database and the load balancer cannot be deleted (also not by terraform destroy) until you set this back to false. Recommended for prod."
  type        = bool
  default     = false
}

variable "msk_kafka_version" {
  description = "Kafka version of Amazon MSK. List the versions your region offers with: aws kafka list-kafka-versions"
  type        = string
  default     = "3.8.x"
}

variable "msk_instance_type" {
  description = "Size of each Kafka broker, e.g. kafka.t3.small (dev) or kafka.m7g.large (prod)."
  type        = string
  default     = "kafka.t3.small"
}

variable "msk_volume_size" {
  description = "Disk size of each Kafka broker in GiB."
  type        = number
  default     = 20

  validation {
    condition     = var.msk_volume_size >= 1 && var.msk_volume_size <= 16384
    error_message = "msk_volume_size must be between 1 and 16384 GiB."
  }
}

variable "redis_node_type" {
  description = "Size of the Redis node, e.g. cache.t4g.micro (dev) or cache.t4g.small."
  type        = string
  default     = "cache.t4g.micro"
}

variable "redis_node_count" {
  description = "Redis nodes: 1 (dev) or 2+ (a replica in another zone takes over on failure)."
  type        = number
  default     = 1

  validation {
    condition     = var.redis_node_count >= 1 && var.redis_node_count <= 6
    error_message = "redis_node_count must be between 1 and 6."
  }
}

variable "elasticsearch_url" {
  description = "Address of an Elasticsearch 9 you run elsewhere (e.g. Elastic Cloud, https://...:443). Empty = run a single Elasticsearch node on Fargate (fine for dev; see README.md)."
  type        = string
  default     = ""
}

variable "elasticsearch_memory" {
  description = "Memory (MiB) of the Elasticsearch task when it runs on Fargate; half of it is the Java heap."
  type        = number
  default     = 4096

  validation {
    condition     = contains([2048, 3072, 4096, 5120, 6144, 7168, 8192], var.elasticsearch_memory)
    error_message = "elasticsearch_memory must be 2048-8192 MiB in steps of 1024 (Fargate with 1 vCPU)."
  }
}

# ===== Deleting things =========================================================

variable "ecr_force_delete" {
  description = "true: terraform destroy also deletes the image repositories when they still contain images. Handy for dev; false keeps prod images safe."
  type        = bool
  default     = true
}

variable "secret_recovery_window_days" {
  description = "Days a deleted secret can still be restored. 0 = deleted at once (lets you destroy and re-create dev quickly); 7-30 for prod."
  type        = number
  default     = 0

  validation {
    condition     = var.secret_recovery_window_days == 0 || (var.secret_recovery_window_days >= 7 && var.secret_recovery_window_days <= 30)
    error_message = "secret_recovery_window_days must be 0 or between 7 and 30."
  }
}
