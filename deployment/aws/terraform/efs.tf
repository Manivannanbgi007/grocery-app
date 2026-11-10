# -----------------------------------------------------------------------------
# Shared files (Amazon EFS): files the services write to disk, which must
# survive a restart and be the same for every copy of a service:
#
#   product-uploads    product photos            product-service   /app/uploads
#   delivery-uploads   proof-of-delivery photos  delivery-service  /app/uploads
#   order-invoices     PDF invoices              order-service     /app/invoices
#   elasticsearch      the search index          elasticsearch     /usr/share/elasticsearch/data
#
# One encrypted file system; each folder is an "access point" that also fixes the
# Linux user the files belong to, so the containers' own users do not matter.
# Daily backups are on (AWS Backup): invoices must be kept.
# -----------------------------------------------------------------------------

resource "aws_efs_file_system" "this" {
  creation_token   = "${local.name}-files"
  encrypted        = true
  performance_mode = "generalPurpose"
  throughput_mode  = "elastic" # pay for what is used, no need to size it

  # Files nobody opened for 30 days move to the cheaper "infrequent access" class
  lifecycle_policy {
    transition_to_ia = "AFTER_30_DAYS"
  }

  tags = { Name = "${local.name}-files" }
}

resource "aws_efs_backup_policy" "this" {
  file_system_id = aws_efs_file_system.this.id

  backup_policy {
    status = "ENABLED"
  }
}

# One mount target per zone, so tasks in every zone can reach the files
resource "aws_efs_mount_target" "this" {
  count = var.az_count

  file_system_id  = aws_efs_file_system.this.id
  subnet_id       = aws_subnet.private[count.index].id
  security_groups = [aws_security_group.data["efs"].id]
}

locals {
  # access point => the Linux user and group that own its files
  efs_access_points = {
    product-uploads  = 10001 # the services' user (order-service's image uses 10001 too)
    delivery-uploads = 10001
    order-invoices   = 10001
    elasticsearch    = 1000 # the user inside the Elasticsearch image
  }
}

resource "aws_efs_access_point" "this" {
  for_each = local.efs_access_points

  file_system_id = aws_efs_file_system.this.id

  # Every file written through this access point belongs to this user and group
  posix_user {
    uid = each.value
    gid = each.value
  }

  # The folder is created on first use, owned by that user
  root_directory {
    path = "/${each.key}"
    creation_info {
      owner_uid   = each.value
      owner_gid   = each.value
      permissions = "0750"
    }
  }

  tags = { Name = "${local.name}-${each.key}" }
}
