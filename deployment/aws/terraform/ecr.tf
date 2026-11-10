# -----------------------------------------------------------------------------
# Amazon ECR: one private image repository per image, e.g.
#   <account>.dkr.ecr.<region>.amazonaws.com/grocery-dev/auth-service
#
# scripts/push-images.ps1 (or .sh) builds the images and pushes them here.
# -----------------------------------------------------------------------------

locals {
  # Every image the deployment runs that we build ourselves.
  # (Schema Registry, Elasticsearch and the setup tasks use public images.)
  ecr_images = concat(
    keys(local.app_service_catalogue), # the Spring Boot services and the frontend
    ["kafka-connect"],                 # Kafka Connect with the Debezium plugin (infrastructure/kafka-connect)
  )
}

resource "aws_ecr_repository" "this" {
  for_each = toset(local.ecr_images)

  name = "${local.name}/${each.key}"

  # A pushed tag can never be overwritten: "1.0.0" always means the same image, and a
  # rollback is just going back to the previous tag
  image_tag_mutability = "IMMUTABLE"

  # Every pushed image is checked for known vulnerabilities (see the console, or
  # `aws ecr describe-image-scan-findings`)
  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  force_delete = var.ecr_force_delete
}

# Keep the repositories small (storage costs money): the last 15 tagged images stay,
# images without a tag are removed after 7 days.
resource "aws_ecr_lifecycle_policy" "this" {
  for_each = aws_ecr_repository.this

  repository = each.value.name
  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Remove untagged images after 7 days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 7
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "Keep the last 15 images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 15
        }
        action = { type = "expire" }
      },
    ]
  })
}
