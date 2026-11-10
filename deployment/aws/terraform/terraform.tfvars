# -----------------------------------------------------------------------------
# Settings of THIS environment (dev). Every variable is explained in variables.tf.
#
# No secrets in this file: it is meant to be committed. Secret values (Razorpay,
# SMTP password) go into environment variables, e.g. in PowerShell:
#   $env:TF_VAR_razorpay_key_secret = "..."
# -----------------------------------------------------------------------------

# ----- Where -----
aws_region  = "ap-south-1" # Mumbai
project     = "grocery"
environment = "dev"

# ----- What runs -----
# Step 5 of README.md: first create the infrastructure only (false).
# Step 7: after pushing the images and the setup tasks, change this to true.
deploy_services = false

# The image tag you pushed (scripts/push-images). A new release = a new tag.
image_tag = "1.0.0"

# Sample stores, products and the demo accounts (password Password@123). Dev only!
load_demo_data = true

# ----- Who can open the shop -----
# Everyone. While testing you can limit it to your own IP, e.g. ["203.0.113.7/32"]
# (find yours at https://checkip.amazonaws.com).
allowed_ingress_cidrs = ["0.0.0.0/0"]

# HTTPS: the ARN of an ACM certificate for your domain, and the domain itself.
# Leave both empty for plain HTTP on the load balancer's AWS name.
certificate_arn = ""
domain_name     = ""

# ----- MySQL (Amazon RDS): the application's database -----
rds_engine_version        = "8.4"           # the newest 8.4.x, patched automatically
rds_instance_class        = "db.t4g.medium" # 2 vCPU, 4 GiB; prod: e.g. db.m7g.large
rds_allocated_storage     = 20              # GiB to start with ...
rds_max_allocated_storage = 100             # ... grows by itself up to this
rds_multi_az              = false           # prod: true (a standby in another zone takes over on failure)
rds_backup_retention_days = 7               # daily backups and point-in-time restore for 7 days
rds_monitoring_interval   = 60              # Enhanced Monitoring every 60 s (0 = off)
rds_kms_key_arn           = ""              # "" = AWS managed encryption key
rds_snapshot_identifier   = ""              # restore a snapshot instead of an empty database (only at creation)

# Alert emails (database CPU, disk space, memory, connections). "" = no alarms.
# After the first apply, confirm the subscription email AWS sends.
alarm_email = ""

# ----- Cost savers for dev -----
single_nat_gateway = true  # one NAT gateway instead of one per zone
use_fargate_spot   = false # true = about 70% cheaper application tasks (may be interrupted)
redis_node_count   = 1

# ----- Payments: test mode without a provider -----
payment_gateway = "mock"

# ----- Emails: off (in-app notifications still work). For Amazon SES see README.md -----
mail_host = ""

# ----- Easy clean-up in dev -----
deletion_protection         = false
ecr_force_delete            = true
secret_recovery_window_days = 0
