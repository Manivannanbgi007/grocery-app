output "address" {
  description = "Host name of the database server (inside the VPC), e.g. grocery-dev-mysql.abc123.ap-south-1.rds.amazonaws.com."
  value       = aws_db_instance.this.address
}

output "port" {
  description = "MySQL port."
  value       = aws_db_instance.this.port
}

output "identifier" {
  description = "Name of the database server in RDS (AWS console: RDS > Databases)."
  value       = aws_db_instance.this.identifier
}

output "arn" {
  description = "ARN of the database server."
  value       = aws_db_instance.this.arn
}

output "master_username" {
  description = "Name of the admin user."
  value       = aws_db_instance.this.username
}

output "master_user_secret_arn" {
  description = "Secrets Manager secret with the admin user's name and password (JSON keys username, password), kept and rotated by RDS."
  value       = aws_db_instance.this.master_user_secret[0].secret_arn
}

output "security_group_id" {
  description = "Security group of the database."
  value       = aws_security_group.this.id
}

output "parameter_group_name" {
  description = "Parameter group with the MySQL settings."
  value       = aws_db_parameter_group.this.name
}

output "log_group_names" {
  description = "CloudWatch log groups of the exported MySQL logs: log type => group name."
  value       = { for log, group in aws_cloudwatch_log_group.this : log => group.name }
}
