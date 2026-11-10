# -----------------------------------------------------------------------------
# Kafka (Amazon MSK): the event bus between the services. One broker per zone.
#
# Encryption in transit: TLS_PLAINTEXT, i.e. the brokers offer both a TLS port
# (9094) and a plaintext port (9092), and the services use the plaintext one,
# which only the ECS tasks can reach (security group). Why not TLS only: the
# services' dead-letter producers (KafkaConsumerConfig.java in 11 services) build
# their Kafka settings by hand and would not pick up security.protocol=SSL, so
# failed events could not be parked. See README.md, "Going to production".
# Data on disk is always encrypted.
#
# The topics are created by the one-time setup task "kafka-topics"
# (ecs-bootstrap.tf), with the same script as on a laptop
# (infrastructure/kafka/create-topics.sh), like auto.create.topics.enable=false locally.
# -----------------------------------------------------------------------------

resource "aws_msk_configuration" "this" {
  name           = "${local.name}-kafka"
  kafka_versions = [var.msk_kafka_version]

  server_properties = <<-EOT
    auto.create.topics.enable=false
    default.replication.factor=${local.kafka_replication_factor}
    min.insync.replicas=1
    num.partitions=3
    log.retention.hours=168
  EOT

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_cloudwatch_log_group" "msk" {
  name              = "/aws/msk/${local.name}"
  retention_in_days = var.log_retention_days
}

resource "aws_msk_cluster" "this" {
  cluster_name           = "${local.name}-kafka"
  kafka_version          = var.msk_kafka_version
  number_of_broker_nodes = var.az_count # one broker per zone

  broker_node_group_info {
    instance_type   = var.msk_instance_type
    client_subnets  = aws_subnet.private[*].id
    security_groups = [aws_security_group.data["kafka"].id]

    storage_info {
      ebs_storage_info {
        volume_size = var.msk_volume_size
      }
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.this.arn
    revision = aws_msk_configuration.this.latest_revision
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS_PLAINTEXT" # see the note at the top of this file
      in_cluster    = true            # between the brokers: always TLS
    }
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.msk.name
      }
    }
  }
}
