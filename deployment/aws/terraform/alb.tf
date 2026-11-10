# -----------------------------------------------------------------------------
# The public entrance: an Application Load Balancer in the public subnets.
#
#   /api/*, /media/*, /delivery-media/*  -> api-gateway  (port 8080)
#   everything else                       -> frontend     (the React shop, nginx on port 80)
#
# With certificate_arn: HTTPS on 443, and HTTP on 80 only redirects to HTTPS.
# Without it: plain HTTP on 80 (fine for a first try; see README.md for HTTPS).
# -----------------------------------------------------------------------------

resource "aws_lb" "this" {
  name               = "${local.name}-alb"
  load_balancer_type = "application"
  internal           = false
  subnets            = aws_subnet.public[*].id
  security_groups    = [aws_security_group.alb.id]

  # Live order tracking and the AI chat keep connections open; see alb_idle_timeout
  idle_timeout = var.alb_idle_timeout

  # Drop requests with malformed headers (protection against request smuggling)
  drop_invalid_header_fields = true

  enable_deletion_protection = var.deletion_protection
}

# ----- Target groups: where the load balancer sends requests -----

locals {
  # target group => port and health check path of the containers behind it
  target_groups = {
    web = { port = 80, health_path = "/" }                            # frontend
    api = { port = 8080, health_path = "/actuator/health/readiness" } # api-gateway
  }
}

resource "aws_lb_target_group" "this" {
  for_each = local.target_groups

  name        = "${local.name}-${each.key}"
  port        = each.value.port
  protocol    = "HTTP"
  target_type = "ip" # Fargate tasks are registered by their IP address
  vpc_id      = aws_vpc.this.id

  # Wait at most 30 s for open requests when a task stops (default 300 s)
  deregistration_delay = 30

  health_check {
    path                = each.value.health_path
    matcher             = "200"
    interval            = 15
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

# ----- Listeners -----

# Port 80: redirects to HTTPS when there is a certificate, otherwise serves the shop
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = local.https_enabled ? "redirect" : "forward"
    target_group_arn = local.https_enabled ? null : aws_lb_target_group.this["web"].arn

    dynamic "redirect" {
      for_each = local.https_enabled ? [1] : []
      content {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }
  }
}

resource "aws_lb_listener" "https" {
  count = local.https_enabled ? 1 : 0

  load_balancer_arn = aws_lb.this.arn
  port              = 443
  protocol          = "HTTPS"
  certificate_arn   = var.certificate_arn
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06" # TLS 1.2 and 1.3 only

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.this["web"].arn
  }
}

locals {
  # The listener that serves the shop: HTTPS when there is a certificate
  app_listener_arn = local.https_enabled ? aws_lb_listener.https[0].arn : aws_lb_listener.http.arn

  # Path patterns sent to api-gateway; the Swagger UI's only with expose_api_docs = true
  api_path_groups = {
    for group, paths in {
      api  = ["/api/*", "/media/*", "/delivery-media/*"]
      docs = ["/swagger-ui*", "/v3/api-docs*", "/*-service/v3/api-docs*"]
    } : group => paths if group == "api" || var.expose_api_docs
  }
}

resource "aws_lb_listener_rule" "api" {
  for_each = local.api_path_groups

  listener_arn = local.app_listener_arn
  priority     = each.key == "api" ? 10 : 20

  condition {
    path_pattern {
      values = each.value
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.this["api"].arn
  }
}
