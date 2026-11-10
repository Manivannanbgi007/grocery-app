# Inputs of the ecs-service module. See main.tf for what it creates.

# ----- What to run -----

variable "name" {
  description = "Name of the service and its container, e.g. auth-service. Also its DNS name in Cloud Map."
  type        = string
}

variable "image" {
  description = "Full image name with tag, e.g. 123456789012.dkr.ecr.ap-south-1.amazonaws.com/grocery-dev/auth-service:1.0.0."
  type        = string
}

variable "cpu" {
  description = "CPU units of the task (1024 = 1 vCPU): 256, 512, 1024, 2048 or 4096."
  type        = number

  validation {
    condition     = contains([256, 512, 1024, 2048, 4096], var.cpu)
    error_message = "cpu must be 256, 512, 1024, 2048 or 4096."
  }
}

variable "memory" {
  description = "Memory of the task in MiB; must fit the CPU (e.g. 512 CPU: 1024-4096 MiB)."
  type        = number
}

variable "container_port" {
  description = "Port the container listens on (null: none, e.g. a one-off task)."
  type        = number
  default     = null
}

variable "environment" {
  description = "Plain environment variables (name => value). Never put secrets here: use secrets."
  type        = map(string)
  default     = {}
}

variable "secrets" {
  description = "Environment variables read from Secrets Manager when the container starts (name => \"<secret ARN>:<json key>::\")."
  type        = map(string)
  default     = {}
}

variable "entry_point" {
  description = "Replaces the image's entry point (null: keep it)."
  type        = list(string)
  default     = null
}

variable "command" {
  description = "Replaces the image's command (null: keep it)."
  type        = list(string)
  default     = null
}

variable "health_check_command" {
  description = "Container health check, e.g. [\"CMD-SHELL\", \"curl -fsS http://localhost:8080/actuator/health/liveness || exit 1\"] (null: none)."
  type        = list(string)
  default     = null
}

variable "health_check_start_period" {
  description = "Seconds after start during which failed health checks do not count (Spring Boot needs time to start). At most 300."
  type        = number
  default     = 240
}

variable "efs_volumes" {
  description = "EFS folders to mount: name, file system, access point, and the path inside the container."
  type = list(object({
    name            = string
    file_system_id  = string
    access_point_id = string
    container_path  = string
  }))
  default = []
}

variable "nofile_limit" {
  description = "Most open files of the container (null: the Fargate default). Elasticsearch needs 65535."
  type        = number
  default     = null
}

variable "stop_timeout" {
  description = "Seconds the container gets to shut down cleanly before it is killed (Fargate: at most 120)."
  type        = number
  default     = 45
}

variable "cpu_architecture" {
  description = "X86_64 or ARM64: must match the image."
  type        = string
  default     = "X86_64"
}

# ----- Where and how -----

variable "region" {
  description = "AWS region (for the CloudWatch log settings)."
  type        = string
}

variable "cluster_name" {
  description = "Name of the ECS cluster."
  type        = string
}

variable "cluster_arn" {
  description = "ARN of the ECS cluster."
  type        = string
}

variable "execution_role_arn" {
  description = "Role ECS uses to pull the image, read the secrets and write logs."
  type        = string
}

variable "task_role_arn" {
  description = "Role the application runs with."
  type        = string
}

variable "subnet_ids" {
  description = "Subnets the tasks run in (private)."
  type        = list(string)
}

variable "security_group_ids" {
  description = "Security groups of the tasks."
  type        = list(string)
}

variable "log_retention_days" {
  description = "Days CloudWatch keeps the logs."
  type        = number
  default     = 14
}

# ----- The service (skip all of this for a one-off task) -----

variable "create_service" {
  description = "true: run the task as a service (always running, restarted on failure). false: only the task definition, for one-off tasks started with `aws ecs run-task`."
  type        = bool
  default     = true
}

variable "desired_count" {
  description = "How many copies of the task run."
  type        = number
  default     = 1
}

variable "use_spot" {
  description = "true: run on Fargate Spot (cheaper; AWS may stop a task with 2 minutes' notice, ECS replaces it)."
  type        = bool
  default     = false
}

variable "register_dns_name" {
  description = "true: the service gets the DNS name <name>.<namespace> in namespace_id (Cloud Map), so other services can call it."
  type        = bool
  default     = true
}

variable "namespace_id" {
  description = "Cloud Map namespace for the DNS name (with register_dns_name = true)."
  type        = string
  default     = null
}

# A separate true/false next to the ARN: Terraform must know how many load balancer
# blocks to plan before AWS has created the target group (and its ARN).
variable "load_balanced" {
  description = "true: register the tasks in the load balancer target group target_group_arn."
  type        = bool
  default     = false
}

variable "target_group_arn" {
  description = "Load balancer target group (with load_balanced = true)."
  type        = string
  default     = null
}

variable "health_check_grace_period" {
  description = "Seconds the load balancer's health checks are ignored after a task starts (only with target_group_arn)."
  type        = number
  default     = 300
}

variable "enable_execute_command" {
  description = "true: allow `aws ecs execute-command` into the containers."
  type        = bool
  default     = false
}

variable "autoscaling_max_count" {
  description = "Scale out up to this many tasks when the average CPU stays above 70% (null or <= desired_count: no autoscaling)."
  type        = number
  default     = null
}
