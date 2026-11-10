# -----------------------------------------------------------------------------
# Tests of the configuration that need NO AWS account and cost nothing:
#
#   terraform test
#
# Every "run" makes a plan with fake ("mock") AWS providers and checks the
# result. Run them after every change to the .tf files.
# -----------------------------------------------------------------------------

# Fake AWS: returns these values instead of calling AWS (already while planning)
mock_provider "aws" {
  override_during = plan

  mock_data "aws_availability_zones" {
    defaults = { names = ["ap-south-1a", "ap-south-1b", "ap-south-1c"] }
  }
  mock_data "aws_caller_identity" {
    defaults = { account_id = "123456789012" }
  }
  mock_data "aws_region" {
    defaults = { region = "ap-south-1" }
  }
  mock_data "aws_iam_policy_document" {
    defaults = { json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}" }
  }
  mock_resource "aws_db_instance" {
    defaults = {
      address            = "grocery-dev-mysql.abc.ap-south-1.rds.amazonaws.com"
      master_user_secret = [{ secret_arn = "arn:aws:secretsmanager:ap-south-1:123456789012:secret:rds-master", kms_key_id = "", secret_status = "active" }]
    }
  }
  mock_resource "aws_secretsmanager_secret" {
    defaults = { arn = "arn:aws:secretsmanager:ap-south-1:123456789012:secret:grocery-dev/app" }
  }
  mock_resource "aws_msk_cluster" {
    defaults = { bootstrap_brokers = "b-1.kafka.example:9092,b-2.kafka.example:9092" }
  }
  mock_resource "aws_elasticache_replication_group" {
    defaults = { primary_endpoint_address = "master.grocery-dev-redis.example.cache.amazonaws.com" }
  }
  mock_resource "aws_lb" {
    defaults = {
      arn      = "arn:aws:elasticloadbalancing:ap-south-1:123456789012:loadbalancer/app/grocery-dev-alb/1"
      dns_name = "grocery-dev-alb-1.ap-south-1.elb.amazonaws.com"
    }
  }
  mock_resource "aws_lb_listener" {
    defaults = { arn = "arn:aws:elasticloadbalancing:ap-south-1:123456789012:listener/app/grocery-dev-alb/1/1" }
  }
  mock_resource "aws_lb_target_group" {
    defaults = { arn = "arn:aws:elasticloadbalancing:ap-south-1:123456789012:targetgroup/grocery-dev/1" }
  }
  mock_resource "aws_ecr_repository" {
    defaults = { repository_url = "123456789012.dkr.ecr.ap-south-1.amazonaws.com/grocery-dev/image" }
  }
  mock_resource "aws_iam_role" {
    defaults = { arn = "arn:aws:iam::123456789012:role/grocery-dev" }
  }
  mock_resource "aws_ecs_cluster" {
    defaults = { arn = "arn:aws:ecs:ap-south-1:123456789012:cluster/grocery-dev" }
  }
  mock_resource "aws_efs_file_system" {
    defaults = { arn = "arn:aws:elasticfilesystem:ap-south-1:123456789012:file-system/fs-1" }
  }
  mock_resource "aws_ecs_task_definition" {
    defaults = { arn = "arn:aws:ecs:ap-south-1:123456789012:task-definition/grocery-dev-x:1" }
  }
  mock_resource "aws_service_discovery_service" {
    defaults = { arn = "arn:aws:servicediscovery:ap-south-1:123456789012:service/srv-1" }
  }
}

mock_provider "random" {
  override_during = plan
}

# The values of terraform.tfvars, unless a run says otherwise
variables {
  aws_region      = "ap-south-1"
  project         = "grocery"
  environment     = "dev"
  deploy_services = false
  image_tag       = "1.0.0"
  load_demo_data  = true
}

run "first_apply_creates_only_the_infrastructure" {
  command = plan

  assert {
    condition     = length(module.app_service) == 0 && length(module.kafka_connect) == 0
    error_message = "With deploy_services = false no ECS service may be created."
  }

  assert {
    condition     = length(aws_ecr_repository.this) == 16
    error_message = "Expected 16 image repositories (14 services, the frontend, kafka-connect)."
  }

  assert {
    condition     = length(local.connectors) == 8
    error_message = "Expected the 8 Debezium connectors of <service>/debezium/*.json."
  }
}

run "services_get_their_settings" {
  command = plan

  variables {
    deploy_services = true
  }

  assert {
    condition     = length(module.app_service) == 15 && length(module.elasticsearch) == 1
    error_message = "Expected 14 Spring Boot services and the frontend (no AI assistant), and Elasticsearch on Fargate."
  }

  assert {
    condition     = local.app_services["auth-service"].environment["DB_URL"] == "jdbc:mysql://grocery-dev-mysql.abc.ap-south-1.rds.amazonaws.com:3306/auth_db?sslMode=REQUIRED"
    error_message = "auth-service must use auth_db over TLS."
  }

  assert {
    condition     = local.app_services["auth-service"].environment["REFRESH_COOKIE_SECURE"] == "false" && startswith(local.public_url, "http://")
    error_message = "Without a certificate the shop is plain HTTP, so the refresh cookie must not be Secure."
  }

  assert {
    condition     = local.app_services["auth-service"].environment["SPRING_FLYWAY_LOCATIONS"] == "classpath:db/migration,classpath:db/seed"
    error_message = "load_demo_data = true must load db/seed."
  }

  assert {
    condition     = !contains(keys(local.app_services["order-service"].environment), "SPRING_FLYWAY_LOCATIONS")
    error_message = "order-service has no sample data."
  }

  assert {
    condition     = local.app_services["api-gateway"].environment["PRODUCT_READ_SERVICE_URL"] == "http://product-read-service.grocery.internal:8080"
    error_message = "The gateway must reach the services through their private DNS names."
  }

  assert {
    condition     = local.app_services["product-service"].efs_mounts["/app/uploads"] == "product-uploads"
    error_message = "Product photos must be on EFS."
  }

  assert {
    condition     = !contains(keys(local.app_services["notification-service"].environment), "MAIL_HOST")
    error_message = "Without mail_host no SMTP settings may be set."
  }

  assert {
    condition     = local.connectors["product-cdc-connector"]["database.password"] == "$${env:DEBEZIUM_DB_PASSWORD}"
    error_message = "The connectors must take the password from Kafka Connect's environment, never as text."
  }

  assert {
    condition     = local.connectors["product-cdc-connector"]["value.converter.schema.registry.url"] == "http://schema-registry.grocery.internal:8081"
    error_message = "The product CDC connector must use the Schema Registry on Fargate."
  }
}

run "database_wiring_and_alerts" {
  command = plan

  variables {
    deploy_services = true
    alarm_email     = "ops@example.com"
  }

  assert {
    condition     = module.mysql.port == 3306 && local.app_services["order-service"].environment["DB_URL"] == "jdbc:mysql://grocery-dev-mysql.abc.ap-south-1.rds.amazonaws.com:3306/order_db?sslMode=REQUIRED"
    error_message = "The services must connect to the RDS module's address and port, over TLS."
  }

  assert {
    condition     = local.connectors["order-outbox-connector"]["database.port"] == "3306" && local.connectors["order-outbox-connector"]["database.ssl.mode"] == "required"
    error_message = "Debezium must read the RDS database over TLS."
  }

  assert {
    condition     = length(aws_sns_topic.alerts) == 1 && aws_sns_topic_subscription.alerts_email[0].endpoint == "ops@example.com"
    error_message = "alarm_email must create the alerts topic and its email subscription."
  }
}

run "https_ai_assistant_and_email" {
  command = plan

  variables {
    deploy_services     = true
    certificate_arn     = "arn:aws:acm:ap-south-1:123456789012:certificate/abc"
    domain_name         = "shop.example.com"
    enable_ai_assistant = true
    ollama_base_url     = "http://10.20.16.50:11434"
    mail_host           = "email-smtp.ap-south-1.amazonaws.com"
    mail_username       = "AKIAEXAMPLE"
  }

  assert {
    condition     = local.public_url == "https://shop.example.com" && length(aws_lb_listener.https) == 1
    error_message = "With a certificate and a domain the shop must be https://<domain> on an HTTPS listener."
  }

  assert {
    condition     = local.app_services["auth-service"].environment["REFRESH_COOKIE_SECURE"] == "true"
    error_message = "Over HTTPS the refresh cookie must be Secure."
  }

  assert {
    condition     = length(module.app_service) == 17 && length(aws_ecr_repository.this) == 18
    error_message = "The AI assistant adds mcp-client-service and mcp-server-service (and their repositories)."
  }

  assert {
    condition     = local.app_services["notification-service"].environment["SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH"] == "true"
    error_message = "With an SMTP user name the SMTP login must be on."
  }
}

run "external_elasticsearch_replaces_the_fargate_one" {
  command = plan

  variables {
    deploy_services   = true
    elasticsearch_url = "https://my-deployment.es.ap-south-1.aws.elastic-cloud.com:443"
  }

  assert {
    condition     = length(module.elasticsearch) == 0 && local.app_services["product-read-service"].environment["ELASTICSEARCH_URIS"] == "https://my-deployment.es.ap-south-1.aws.elastic-cloud.com:443"
    error_message = "With elasticsearch_url no Elasticsearch may run on Fargate, and product-read-service must use the given one."
  }
}

run "prod_refuses_the_demo_accounts" {
  command = plan

  variables {
    environment    = "prod"
    load_demo_data = true
  }

  expect_failures = [var.load_demo_data]
}

run "razorpay_needs_its_keys" {
  command = plan

  variables {
    payment_gateway = "razorpay"
  }

  expect_failures = [var.payment_gateway]
}
