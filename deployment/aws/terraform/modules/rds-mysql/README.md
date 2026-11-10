# Module rds-mysql

An **Amazon RDS for MySQL** database server, set up the way AWS recommends. The Fresh Grocery deployment uses
it once (`../../rds.tf`); it can be reused for any other MySQL database.

## What it creates

| Resource | Settings |
|---|---|
| Security group | Only `allowed_security_group_ids` may connect, on 3306 |
| DB subnet group | The private subnets you give it (at least two zones) |
| Parameter group | TLS required (`require_secure_transport`), `utf8mb4`, slow query log (> 2 s), every deadlock logged; with `change_data_capture`: row-based full binary log for Debezium. Add or override settings with `parameters` |
| DB instance | MySQL 8.4 (minor updates automatic, major ones never by surprise), gp3 disk that grows by itself, encrypted (AWS managed key or `kms_key_arn`), not public, optional Multi-AZ, daily backups with point-in-time restore, final snapshot on delete (unless `skip_final_snapshot`) |
| Admin password | Created, kept in Secrets Manager and rotated by RDS (`manage_master_user_password`); nobody types it |
| CloudWatch log groups | The error and slow query logs, kept `log_retention_days` |
| Enhanced Monitoring | Operating-system metrics every `monitoring_interval` seconds (and its IAM role) |
| Performance Insights | Which queries load the database (7 days, free) |
| Alarms (optional) | CPU > 80%, disk < 2 GiB, memory < 256 MiB, connections > `alarm_max_connections`, to an SNS topic |

It creates the server, not the databases inside it: create those with your own migrations or a setup step
(in this project: the db-init task in `../../ecs-bootstrap.tf`).

## Example

```hcl
module "mysql" {
  source = "./modules/rds-mysql"

  name                       = "grocery-dev"                  # -> grocery-dev-mysql
  vpc_id                     = aws_vpc.this.id
  subnet_ids                 = aws_subnet.private[*].id
  allowed_security_group_ids = [aws_security_group.tasks.id]

  instance_class        = "db.t4g.medium"
  multi_az              = false
  backup_retention_days = 7

  create_alarms       = true
  alarm_sns_topic_arn = aws_sns_topic.alerts.arn
}

# Connect with: jdbc:mysql://${module.mysql.address}:${module.mysql.port}/<database>?sslMode=REQUIRED
```

## Inputs and outputs

Every input is described, with its default and checks, in [variables.tf](variables.tf); every output in
[outputs.tf](outputs.tf). The most used:

| Input | Default | |
|---|---|---|
| `name` | | Prefix of every name |
| `vpc_id`, `subnet_ids`, `allowed_security_group_ids` | | Where it lives, who may connect |
| `engine_version` | `8.4` | `8.0`, `8.4` or an exact version |
| `instance_class` | `db.t4g.medium` | |
| `allocated_storage` / `max_allocated_storage` | 20 / 100 GiB | |
| `multi_az` | `false` | Standby in another zone (doubles the cost) |
| `backup_retention_days` | 7 | At least 1 with `change_data_capture` |
| `snapshot_identifier` | `null` | Create the database from a snapshot |
| `deletion_protection`, `skip_final_snapshot` | `false`, `false` | |
| `kms_key_arn` | `null` | Your own KMS key |
| `create_alarms`, `alarm_sns_topic_arn` | `false`, `null` | |

| Output | |
|---|---|
| `address`, `port` | Where to connect |
| `master_username`, `master_user_secret_arn` | The admin user, and its secret (JSON keys `username`, `password`) |
| `identifier`, `arn`, `security_group_id`, `parameter_group_name`, `log_group_names` | |

## Tests

Without an AWS account (fake providers):

```powershell
cd deployment/aws/terraform/modules/rds-mysql
terraform init
terraform test
```
