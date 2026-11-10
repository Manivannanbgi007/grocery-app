# -----------------------------------------------------------------------------
# Supporting services that AWS has no managed version of (that this app can use),
# run as containers on Fargate next to the application:
#
#   schema-registry  Confluent Schema Registry: the Avro schemas of the Kafka events
#   kafka-connect    Kafka Connect with Debezium: reads MySQL's binary log and
#                    publishes the outbox events and the product changes to Kafka
#   elasticsearch    the search index (product-read-service). Amazon OpenSearch does
#                    not work here: the app uses the Elasticsearch 9 client, which
#                    refuses OpenSearch. Single node, data on EFS: fine for dev and
#                    testing; for prod see elasticsearch_url (e.g. Elastic Cloud).
#
# Created together with the application services (deploy_services = true).
# -----------------------------------------------------------------------------

locals {
  # Settings every supporting service shares
  platform_defaults = {
    cluster_name           = aws_ecs_cluster.this.name
    cluster_arn            = aws_ecs_cluster.this.arn
    execution_role_arn     = aws_iam_role.task_execution.arn
    task_role_arn          = aws_iam_role.task.arn
    subnet_ids             = aws_subnet.private[*].id
    security_group_ids     = [aws_security_group.tasks.id]
    namespace_id           = aws_service_discovery_private_dns_namespace.this.id
    enable_execute_command = var.enable_execute_command
  }
}

module "schema_registry" {
  source = "./modules/ecs-service"
  count  = var.deploy_services ? 1 : 0

  name           = "schema-registry"
  image          = "confluentinc/cp-schema-registry:8.2.4" # the same version as on a laptop (infrastructure/.env)
  cpu            = 512
  memory         = 1024
  container_port = 8081

  environment = {
    SCHEMA_REGISTRY_HOST_NAME                           = "schema-registry.${local.internal_domain}"
    SCHEMA_REGISTRY_LISTENERS                           = "http://0.0.0.0:8081"
    SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS        = local.kafka_bootstrap_servers
    SCHEMA_REGISTRY_KAFKASTORE_TOPIC_REPLICATION_FACTOR = tostring(local.kafka_replication_factor)
    SCHEMA_REGISTRY_SCHEMA_COMPATIBILITY_LEVEL          = "backward"
    SCHEMA_REGISTRY_HEAP_OPTS                           = "-Xms512m -Xmx512m"
  }
  health_check_command      = ["CMD-SHELL", "curl -fsS http://localhost:8081/subjects > /dev/null || exit 1"]
  health_check_start_period = 120

  region                 = var.aws_region
  cluster_name           = local.platform_defaults.cluster_name
  cluster_arn            = local.platform_defaults.cluster_arn
  execution_role_arn     = local.platform_defaults.execution_role_arn
  task_role_arn          = local.platform_defaults.task_role_arn
  subnet_ids             = local.platform_defaults.subnet_ids
  security_group_ids     = local.platform_defaults.security_group_ids
  namespace_id           = local.platform_defaults.namespace_id
  enable_execute_command = local.platform_defaults.enable_execute_command
  log_retention_days     = var.log_retention_days
}

module "kafka_connect" {
  source = "./modules/ecs-service"
  count  = var.deploy_services ? 1 : 0

  name             = "kafka-connect"
  image            = "${aws_ecr_repository.this["kafka-connect"].repository_url}:${var.image_tag}"
  cpu              = 1024
  memory           = 2048
  cpu_architecture = var.cpu_architecture
  container_port   = 8083

  environment = {
    CONNECT_BOOTSTRAP_SERVERS                   = local.kafka_bootstrap_servers
    CONNECT_REST_ADVERTISED_HOST_NAME           = "kafka-connect.${local.internal_domain}"
    CONNECT_REST_PORT                           = "8083"
    CONNECT_GROUP_ID                            = "grocery-connect"
    CONNECT_CONFIG_STORAGE_TOPIC                = "_connect-configs"
    CONNECT_OFFSET_STORAGE_TOPIC                = "_connect-offsets"
    CONNECT_STATUS_STORAGE_TOPIC                = "_connect-status"
    CONNECT_CONFIG_STORAGE_REPLICATION_FACTOR   = tostring(local.kafka_replication_factor)
    CONNECT_OFFSET_STORAGE_REPLICATION_FACTOR   = tostring(local.kafka_replication_factor)
    CONNECT_STATUS_STORAGE_REPLICATION_FACTOR   = tostring(local.kafka_replication_factor)
    CONNECT_KEY_CONVERTER                       = "org.apache.kafka.connect.storage.StringConverter"
    CONNECT_VALUE_CONVERTER                     = "io.confluent.connect.avro.AvroConverter"
    CONNECT_VALUE_CONVERTER_SCHEMA_REGISTRY_URL = local.schema_registry_url
    CONNECT_PLUGIN_PATH                         = "/usr/share/java,/usr/share/confluent-hub-components"
    CONNECT_PRODUCER_ENABLE_IDEMPOTENCE         = "true"
    KAFKA_HEAP_OPTS                             = "-Xms512m -Xmx1g"
    # No Prometheus JMX agent here (it needs a config file the image does not contain)
    KAFKA_OPTS = ""

    # The connectors ask for "${env:MYSQL_HOST}" and "${env:DEBEZIUM_DB_PASSWORD}"
    # (ecs-bootstrap.tf): Kafka Connect fills them in from these environment
    # variables, so the password is never stored in Kafka.
    CONNECT_CONFIG_PROVIDERS           = "env"
    CONNECT_CONFIG_PROVIDERS_ENV_CLASS = "org.apache.kafka.common.config.provider.EnvVarConfigProvider"
    MYSQL_HOST                         = local.mysql_host
  }
  secrets = {
    DEBEZIUM_DB_PASSWORD = local.app_secret["DEBEZIUM_DB_PASSWORD"]
  }
  health_check_command      = ["CMD-SHELL", "curl -fsS http://localhost:8083/connectors > /dev/null || exit 1"]
  health_check_start_period = 180

  region                 = var.aws_region
  cluster_name           = local.platform_defaults.cluster_name
  cluster_arn            = local.platform_defaults.cluster_arn
  execution_role_arn     = local.platform_defaults.execution_role_arn
  task_role_arn          = local.platform_defaults.task_role_arn
  subnet_ids             = local.platform_defaults.subnet_ids
  security_group_ids     = local.platform_defaults.security_group_ids
  namespace_id           = local.platform_defaults.namespace_id
  enable_execute_command = local.platform_defaults.enable_execute_command
  log_retention_days     = var.log_retention_days

  depends_on = [aws_secretsmanager_secret_version.app]
}

module "elasticsearch" {
  source = "./modules/ecs-service"
  count  = var.deploy_services && local.run_elasticsearch ? 1 : 0

  name           = "elasticsearch"
  image          = "docker.elastic.co/elasticsearch/elasticsearch:9.4.8" # the version the app's client expects (infrastructure/.env)
  cpu            = 1024
  memory         = var.elasticsearch_memory
  container_port = 9200

  environment = {
    "discovery.type"                                    = "single-node"
    "xpack.security.enabled"                            = "false" # reachable only from the ECS tasks (security group)
    "node.store.allow_mmap"                             = "false" # Fargate cannot raise vm.max_map_count
    "cluster.routing.allocation.disk.threshold_enabled" = "false"
    ES_JAVA_OPTS                                        = "-Xms${floor(var.elasticsearch_memory / 2)}m -Xmx${floor(var.elasticsearch_memory / 2)}m"
  }
  efs_volumes = [{
    name            = "elasticsearch"
    file_system_id  = aws_efs_file_system.this.id
    access_point_id = aws_efs_access_point.this["elasticsearch"].id
    container_path  = "/usr/share/elasticsearch/data"
  }]
  nofile_limit              = 65535
  health_check_command      = ["CMD-SHELL", "curl -fsS http://localhost:9200/_cluster/health > /dev/null || exit 1"]
  health_check_start_period = 180
  stop_timeout              = 120 # let Elasticsearch close its files cleanly

  region                 = var.aws_region
  cluster_name           = local.platform_defaults.cluster_name
  cluster_arn            = local.platform_defaults.cluster_arn
  execution_role_arn     = local.platform_defaults.execution_role_arn
  task_role_arn          = local.platform_defaults.task_role_arn
  subnet_ids             = local.platform_defaults.subnet_ids
  security_group_ids     = local.platform_defaults.security_group_ids
  namespace_id           = local.platform_defaults.namespace_id
  enable_execute_command = local.platform_defaults.enable_execute_command
  log_retention_days     = var.log_retention_days

  depends_on = [aws_efs_mount_target.this]
}
