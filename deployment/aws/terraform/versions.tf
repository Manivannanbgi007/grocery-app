# -----------------------------------------------------------------------------
# Terraform and provider versions.
#
# The exact provider versions in use are pinned in .terraform.lock.hcl (commit
# that file). `terraform init -upgrade` moves to newer versions within the
# ranges below.
# -----------------------------------------------------------------------------

terraform {
  # 1.9 or newer: input validations here compare one variable with another.
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.70"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.8"
    }
  }

  # ---------------------------------------------------------------------------
  # Where Terraform keeps its state (the list of everything it created).
  #
  # By default the state is a local file, terraform.tfstate, in this folder.
  # That is fine for trying things out alone. For a team, or for anything you
  # keep, store it in S3 instead: uncomment the block below, fill in your own
  # bucket (create it first, with versioning and encryption on) and run
  # `terraform init -migrate-state`. See README.md, "Remote state".
  #
  # The state contains the generated passwords, so it must stay private.
  # ---------------------------------------------------------------------------
  # backend "s3" {
  #   bucket       = "my-company-terraform-state"
  #   key          = "grocery/dev/terraform.tfstate"
  #   region       = "ap-south-1"
  #   encrypt      = true
  #   use_lockfile = true # S3 locking: two people cannot apply at the same time
  # }
}
