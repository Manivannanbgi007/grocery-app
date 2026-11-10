# -----------------------------------------------------------------------------
# The AWS provider.
#
# Credentials are NEVER written here. The provider finds them by itself, in this
# order: environment variables (AWS_ACCESS_KEY_ID ...), then AWS_PROFILE / the
# shared files written by `aws configure` or `aws configure sso`. See README.md,
# "Step 2: Configure AWS credentials".
# -----------------------------------------------------------------------------

provider "aws" {
  region = var.aws_region

  # Every resource that supports tags gets these, so the AWS console and the
  # bill show what belongs to this project and environment.
  default_tags {
    tags = local.common_tags
  }
}
