# -----------------------------------------------------------------------------
# One-time setup tasks: task definitions without a service. You start each one
# yourself, once, in this order (README.md, steps 7 and 9):
#
#   db-init           create every service's MySQL database and user   (before the services)
#   kafka-topics      create the Kafka topics                          (before the services)
#   kafka-connectors  register the Debezium connectors                 (after the services started)
#
#   ./scripts/run-setup-task.ps1 db-init      (Windows)
#   ./scripts/run-setup-task.sh  db-init      (macOS / Linux)
#
# All three are safe to run again.
#
# And one for you, any time: db-shell, a MySQL prompt to the RDS database
# (./scripts/db-shell.ps1). The database is private, so the prompt runs in a
# short-lived container inside the VPC.
# -----------------------------------------------------------------------------

locals {
  repo_root = "${path.module}/../../.."

  # ----- kafka-topics: the laptop's script, pointed at MSK -----
  create_topics_file   = file("${local.repo_root}/infrastructure/kafka/create-topics.sh")
  create_topics_marker = "BOOTSTRAP_SERVER=\"localhost:9092\""
  create_topics_script = replace(local.create_topics_file, local.create_topics_marker, "BOOTSTRAP_SERVER=\"$KAFKA_BOOTSTRAP_SERVERS\"")

  # ----- kafka-connectors: the connectors of <service>/debezium/*.json -----
  connector_definitions = {
    for path in fileset(local.repo_root, "*/debezium/*.json") : path => jsondecode(file("${local.repo_root}/${path}"))
  }

  # On a laptop the connectors talk to "mysql" with a fixed password. Here Kafka
  # Connect fills in the address and the password from its environment
  # ("${env:...}", see ecs-platform.tf), and the connection is encrypted.
  connector_overrides = {
    "database.hostname"                               = "$${env:MYSQL_HOST}"
    "database.port"                                   = tostring(local.mysql_port)
    "database.user"                                   = "debezium"
    "database.password"                               = "$${env:DEBEZIUM_DB_PASSWORD}"
    "database.ssl.mode"                               = "required"
    "schema.history.internal.kafka.bootstrap.servers" = local.kafka_bootstrap_servers
  }

  connectors = {
    for path, definition in local.connector_definitions : definition.name => merge(
      definition.config,
      local.connector_overrides,
      # Only the product CDC connector uses the Schema Registry
      { for key, value in definition.config : key => local.schema_registry_url if key == "value.converter.schema.registry.url" },
    )
  }

  register_connectors_script = join("\n", concat(
    [file("${path.module}/bootstrap/register-connectors.sh")],
    [for name in sort(keys(local.connectors)) : "put_connector ${name} <<'JSON'\n${jsonencode(local.connectors[name])}\nJSON"],
    ["finish"],
  ))

  # The MySQL admin user, from the secret RDS keeps (and rotates) for it
  mysql_admin_secrets = {
    MASTER_USERNAME = "${module.mysql.master_user_secret_arn}:username::"
    MASTER_PASSWORD = "${module.mysql.master_user_secret_arn}:password::"
  }

  # Settings every setup task shares
  bootstrap_defaults = {
    cluster_name       = aws_ecs_cluster.this.name
    cluster_arn        = aws_ecs_cluster.this.arn
    execution_role_arn = aws_iam_role.task_execution.arn
    task_role_arn      = aws_iam_role.task.arn
    subnet_ids         = aws_subnet.private[*].id
    security_group_ids = [aws_security_group.tasks.id]
  }
}

# Stop the plan when a file this configuration relies on has changed shape
resource "terraform_data" "bootstrap_checks" {
  lifecycle {
    precondition {
      condition     = strcontains(local.create_topics_file, local.create_topics_marker)
      error_message = "infrastructure/kafka/create-topics.sh no longer contains ${local.create_topics_marker}: update local.create_topics_marker in ecs-bootstrap.tf."
    }
    precondition {
      condition     = length(local.connectors) > 0
      error_message = "No Debezium connector files (<service>/debezium/*.json) were found next to deployment/."
    }
  }
}

module "db_init" {
  source         = "./modules/ecs-service"
  create_service = false

  name   = "db-init"
  image  = "public.ecr.aws/docker/library/mysql:8.4" # only for its mysql client (ECR Public: no Docker Hub limits)
  cpu    = 256
  memory = 512

  entry_point = ["bash", "-c"]
  command     = [file("${path.module}/bootstrap/db-init.sh")]

  environment = {
    DB_HOST   = local.mysql_host
    DATABASES = join(" ", local.databases)
  }
  secrets = merge(
    local.mysql_admin_secrets,
    { DEBEZIUM_DB_PASSWORD = local.app_secret["DEBEZIUM_DB_PASSWORD"] },
    { for db in local.databases : "${upper(db)}_DB_PASSWORD" => local.app_secret["${upper(db)}_DB_PASSWORD"] },
  )

  region             = var.aws_region
  cluster_name       = local.bootstrap_defaults.cluster_name
  cluster_arn        = local.bootstrap_defaults.cluster_arn
  execution_role_arn = local.bootstrap_defaults.execution_role_arn
  task_role_arn      = local.bootstrap_defaults.task_role_arn
  subnet_ids         = local.bootstrap_defaults.subnet_ids
  security_group_ids = local.bootstrap_defaults.security_group_ids
  log_retention_days = var.log_retention_days
}

module "kafka_topics" {
  source         = "./modules/ecs-service"
  create_service = false

  name   = "kafka-topics"
  image  = "confluentinc/cp-kafka:8.2.4" # only for its kafka-topics tool (same version as on a laptop)
  cpu    = 512
  memory = 1024

  entry_point = ["bash", "-c"]
  command     = [local.create_topics_script]

  environment = {
    KAFKA_BOOTSTRAP_SERVERS = local.kafka_bootstrap_servers
    REPLICATION_FACTOR      = tostring(local.kafka_replication_factor)
    MIN_ISR                 = "1"
  }

  region             = var.aws_region
  cluster_name       = local.bootstrap_defaults.cluster_name
  cluster_arn        = local.bootstrap_defaults.cluster_arn
  execution_role_arn = local.bootstrap_defaults.execution_role_arn
  task_role_arn      = local.bootstrap_defaults.task_role_arn
  subnet_ids         = local.bootstrap_defaults.subnet_ids
  security_group_ids = local.bootstrap_defaults.security_group_ids
  log_retention_days = var.log_retention_days

  depends_on = [terraform_data.bootstrap_checks]
}

module "kafka_connectors" {
  source         = "./modules/ecs-service"
  create_service = false

  name             = "kafka-connectors"
  image            = "${aws_ecr_repository.this["kafka-connect"].repository_url}:${var.image_tag}" # only for its curl
  cpu              = 256
  memory           = 512
  cpu_architecture = var.cpu_architecture

  entry_point = ["bash", "-c"]
  command     = [local.register_connectors_script]

  environment = {
    CONNECT_URL        = local.kafka_connect_url
    INVENTORY_URL      = "http://inventory-service.${local.internal_domain}:8080"
    PUBLISH_SEED_STOCK = tostring(var.load_demo_data)
  }

  region             = var.aws_region
  cluster_name       = local.bootstrap_defaults.cluster_name
  cluster_arn        = local.bootstrap_defaults.cluster_arn
  execution_role_arn = local.bootstrap_defaults.execution_role_arn
  task_role_arn      = local.bootstrap_defaults.task_role_arn
  subnet_ids         = local.bootstrap_defaults.subnet_ids
  security_group_ids = local.bootstrap_defaults.security_group_ids
  log_retention_days = var.log_retention_days

  depends_on = [terraform_data.bootstrap_checks]
}

# A MySQL prompt to the RDS database, for you (scripts/db-shell.ps1): the container
# waits for an hour; the script connects into it with `aws ecs execute-command`
# and runs `db-connect` there, then stops it.
module "db_shell" {
  source         = "./modules/ecs-service"
  create_service = false

  name   = "db-shell"
  image  = "public.ecr.aws/docker/library/mysql:8.4" # only for its mysql client
  cpu    = 256
  memory = 512

  entry_point = ["bash", "-c"]
  command     = [file("${path.module}/bootstrap/db-shell.sh")]

  environment = { DB_HOST = local.mysql_host }
  secrets     = local.mysql_admin_secrets

  region             = var.aws_region
  cluster_name       = local.bootstrap_defaults.cluster_name
  cluster_arn        = local.bootstrap_defaults.cluster_arn
  execution_role_arn = local.bootstrap_defaults.execution_role_arn
  task_role_arn      = local.bootstrap_defaults.task_role_arn
  subnet_ids         = local.bootstrap_defaults.subnet_ids
  security_group_ids = local.bootstrap_defaults.security_group_ids
  log_retention_days = var.log_retention_days
}
