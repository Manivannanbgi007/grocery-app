# -----------------------------------------------------------------------------
# The catalogue of the application's services: what each one needs.
# ecs-services.tf turns every entry into an ECS service (modules/ecs-service).
#
# An entry only says what is special about a service; the rest comes from flags:
#   database  = "auth"  -> DB_URL / DB_USERNAME / DB_PASSWORD of auth_db
#   kafka     = true    -> KAFKA_BOOTSTRAP_SERVERS and SCHEMA_REGISTRY_URL
#   demo_data = true    -> loads db/seed when load_demo_data = true
#   spring    = false   -> not a Spring Boot service (the frontend)
# Optional: cpu, memory, desired_count, autoscaling, target_group, port,
# environment, secrets, efs_mounts (container path => EFS access point).
#
# To add a service: add an entry here, push its image (scripts/push-images),
# apply. Sizes can be changed per environment with service_overrides.
#
# The settings mirror deployment/kubernetes/services/*/deployment.yaml.
# -----------------------------------------------------------------------------

locals {
  # Every service's own MySQL database: <db>_db, user <db>_user
  databases = ["auth", "customer", "store", "product", "inventory", "cart", "order", "payment", "delivery", "notification", "report", "platform", "chat"]

  # The internal address of a service: http://<name>.grocery.internal:8080
  service_url = { for name in keys(local.app_service_catalogue) : name => "http://${name}.${local.internal_domain}:8080" }

  # The services the API gateway sends requests to: AUTH_SERVICE_URL, PRODUCT_READ_SERVICE_URL ...
  gateway_targets = [
    "auth-service", "customer-service", "store-service", "product-service", "product-read-service", "inventory-service",
    "cart-service", "order-service", "payment-service", "delivery-service", "notification-service", "report-service",
    "platform-service", "mcp-client-service",
  ]

  # The Redis connection (TLS: ElastiCache encrypts in transit)
  redis_env = {
    REDIS_HOST                    = local.redis_host
    REDIS_PORT                    = "6379"
    SPRING_DATA_REDIS_SSL_ENABLED = "true"
  }

  # Email (notification-service). Amazon SES: port 587 with STARTTLS and an SMTP login.
  # (The "for ... if" keeps the settings only when mail_host is set.)
  mail_env = {
    for key, value in {
      MAIL_HOST                                        = var.mail_host
      MAIL_PORT                                        = tostring(var.mail_port)
      MAIL_FROM                                        = var.mail_from
      SPRING_MAIL_USERNAME                             = var.mail_username
      SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH            = tostring(var.mail_username != "")
      SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE = "true"
    } : key => value if var.mail_host != ""
  }

  # Every service. The AI assistant's two (ai = true) are left out unless enable_ai_assistant = true.
  app_service_catalogue = {
    for name, service in local.all_services : name => service if !try(service.ai, false) || var.enable_ai_assistant
  }

  all_services = {
    "api-gateway" = {
      desired_count = 2
      autoscaling   = true
      target_group  = "api" # the load balancer sends /api, /media, /delivery-media here
      environment = merge(
        { for target in local.gateway_targets : "${upper(replace(target, "-", "_"))}_URL" => "http://${target}.${local.internal_domain}:8080" },
        local.redis_env,
        {
          REDIS_USERNAME             = "gateway"
          CORS_ALLOWED_ORIGINS       = local.public_url
          RATE_LIMIT_TRUSTED_PROXIES = "1" # the load balancer: the client's address is the last one it adds
        },
      )
      secrets = {
        JWT_SECRET     = local.app_secret["JWT_SECRET"]
        REDIS_PASSWORD = local.app_secret["REDIS_GATEWAY_PASSWORD"]
      }
    }

    "auth-service" = {
      database  = "auth"
      kafka     = true
      demo_data = true
      environment = {
        APP_PUBLIC_URL        = local.public_url
        SOCIAL_LOGIN_ENABLED  = "false"
        REFRESH_COOKIE_SECURE = tostring(local.https_enabled) # a Secure cookie only travels over HTTPS
      }
      secrets = {
        JWT_SECRET               = local.app_secret["JWT_SECRET"]
        PASSWORD_RESET_EVENT_KEY = local.app_secret["PASSWORD_RESET_EVENT_KEY"]
      }
    }

    "customer-service" = {
      database  = "customer"
      kafka     = true
      demo_data = true
    }

    "store-service" = {
      database  = "store"
      demo_data = true
      # Publishes through its outbox table and Debezium; only needs the Schema Registry
      environment = { SCHEMA_REGISTRY_URL = local.schema_registry_url }
    }

    "product-service" = {
      database  = "product"
      demo_data = true
      environment = {
        ORDER_SERVICE_URL     = "http://order-service.${local.internal_domain}:8080"
        MEDIA_UPLOAD_DIR      = "/app/uploads"
        MEDIA_PUBLIC_BASE_URL = "${local.public_url}/media"
      }
      efs_mounts = { "/app/uploads" = "product-uploads" }
    }

    "product-read-service" = {
      kafka         = true
      desired_count = 2
      autoscaling   = true # the busiest service: every search and product page
      environment   = { ELASTICSEARCH_URIS = local.elasticsearch_url }
    }

    "inventory-service" = {
      database    = "inventory"
      kafka       = true
      demo_data   = true
      environment = { PRODUCT_READ_SERVICE_URL = "http://product-read-service.${local.internal_domain}:8080" }
    }

    "cart-service" = {
      database    = "cart"
      kafka       = true
      demo_data   = true
      environment = { PRODUCT_READ_SERVICE_URL = "http://product-read-service.${local.internal_domain}:8080" }
    }

    "order-service" = {
      database = "order"
      kafka    = true
      environment = {
        CART_SERVICE_URL     = "http://cart-service.${local.internal_domain}:8080"
        CUSTOMER_SERVICE_URL = "http://customer-service.${local.internal_domain}:8080"
        STORE_SERVICE_URL    = "http://store-service.${local.internal_domain}:8080"
        INVOICE_STORAGE_DIR  = "/app/invoices"
      }
      efs_mounts = { "/app/invoices" = "order-invoices" }
    }

    "payment-service" = {
      database = "payment"
      kafka    = true
      environment = {
        PAYMENT_GATEWAY = var.payment_gateway
        RAZORPAY_KEY_ID = var.razorpay_key_id
        RAZORPAY_MODE   = var.razorpay_mode
      }
      secrets = {
        RAZORPAY_KEY_SECRET     = local.external_secret["RAZORPAY_KEY_SECRET"]
        RAZORPAY_WEBHOOK_SECRET = local.external_secret["RAZORPAY_WEBHOOK_SECRET"]
      }
    }

    "delivery-service" = {
      database  = "delivery"
      kafka     = true
      demo_data = true
      environment = merge(local.redis_env, {
        REDIS_USERNAME        = "delivery"
        MEDIA_UPLOAD_DIR      = "/app/uploads"
        MEDIA_PUBLIC_BASE_URL = "${local.public_url}/delivery-media"
      })
      secrets    = { REDIS_PASSWORD = local.app_secret["REDIS_DELIVERY_PASSWORD"] }
      efs_mounts = { "/app/uploads" = "delivery-uploads" }
    }

    "notification-service" = {
      database    = "notification"
      kafka       = true
      demo_data   = true
      environment = local.mail_env
      secrets = {
        PASSWORD_RESET_EVENT_KEY = local.app_secret["PASSWORD_RESET_EVENT_KEY"]
        SPRING_MAIL_PASSWORD     = local.external_secret["MAIL_PASSWORD"] # empty without SMTP login
      }
    }

    "report-service" = {
      database  = "report"
      kafka     = true
      demo_data = true
    }

    "platform-service" = {
      database  = "platform"
      kafka     = true
      demo_data = true
    }

    # The React shop: nginx serving the built files
    "frontend" = {
      spring        = false
      port          = 80
      cpu           = 256
      memory        = 512
      desired_count = 2
      autoscaling   = true
      target_group  = "web"
    }

    # The AI shopping assistant (only with enable_ai_assistant = true: needs your own Ollama server)
    "mcp-server-service" = {
      ai          = true
      environment = { API_GATEWAY_URL = "http://api-gateway.${local.internal_domain}:8080" }
      secrets     = { MCP_API_KEY = local.app_secret["MCP_API_KEY"] }
    }

    "mcp-client-service" = {
      ai       = true
      database = "chat"
      memory   = 2048
      environment = {
        MCP_SERVER_URL      = "http://mcp-server-service.${local.internal_domain}:8080"
        OLLAMA_BASE_URL     = var.ollama_base_url
        OLLAMA_CHAT_MODEL   = "qwen3:4b-instruct-2507-q4_K_M"
        SPEECH_BASE_URL     = var.speech_base_url
        CHAT_MAX_CONCURRENT = "4"
      }
      secrets = { MCP_API_KEY = local.app_secret["MCP_API_KEY"] }
    }
  }

  # ----- Settings shared by the Spring Boot services -----

  spring_env = {
    SPRING_PROFILES_ACTIVE = "prod"
    JAVA_TOOL_OPTIONS      = "-XX:+ExitOnOutOfMemoryError" # a task out of memory stops (and is replaced) instead of limping on
    LOG_FORMAT             = "json"                        # one JSON object per line: easy to search in CloudWatch
    PUBLIC_BASE_URL        = local.public_url
    TRACING_EXPORT_ENABLED = "false"
    LOKI_ENABLED           = "false"
  }

  kafka_env = {
    KAFKA_BOOTSTRAP_SERVERS = local.kafka_bootstrap_servers
    SCHEMA_REGISTRY_URL     = local.schema_registry_url
  }

  # The sample data lives in db/seed next to the real migrations (db/migration)
  demo_data_env = {
    SPRING_FLYWAY_LOCATIONS = "classpath:db/migration,classpath:db/seed"
  }

  # ----- Every service with all its settings filled in -----
  # (try(x, default) reads an optional setting; "for ... if" keeps a group of
  # settings only when the service's flag is on.)

  app_services = {
    for name, service in local.app_service_catalogue : name => {
      port          = try(service.port, 8080)
      cpu           = coalesce(try(var.service_overrides[name].cpu, null), try(service.cpu, 512))
      memory        = coalesce(try(var.service_overrides[name].memory, null), try(service.memory, 1024))
      desired_count = coalesce(try(var.service_overrides[name].desired_count, null), try(service.desired_count, 1))
      autoscaling   = try(service.autoscaling, false)
      target_group  = try(service.target_group, null)
      efs_mounts    = tomap(try(service.efs_mounts, {}))

      environment = tomap(merge(
        { for key, value in local.spring_env : key => value if try(service.spring, true) },
        {
          for key, value in {
            DB_URL      = "jdbc:mysql://${local.mysql_host}:${local.mysql_port}/${try(service.database, "")}_db?sslMode=REQUIRED"
            DB_USERNAME = "${try(service.database, "")}_user"
          } : key => value if try(service.database, null) != null
        },
        { for key, value in local.kafka_env : key => value if try(service.kafka, false) },
        { for key, value in local.demo_data_env : key => value if try(service.demo_data, false) && var.load_demo_data },
        try(service.environment, {}),
      ))

      secrets = tomap(merge(
        {
          for key, value in { DB_PASSWORD = try(local.app_secret["${upper(service.database)}_DB_PASSWORD"], "") } :
          key => value if try(service.database, null) != null
        },
        try(service.secrets, {}),
      ))

      # Spring Boot services: their liveness endpoint; the frontend (nginx on Alpine, no curl): its start page
      health_check_command = (try(service.spring, true)
        ? ["CMD-SHELL", "curl -fsS http://localhost:8080/actuator/health/liveness > /dev/null || exit 1"]
        : ["CMD-SHELL", "wget -q -O /dev/null http://localhost/ || exit 1"]
      )
    }
  }
}
