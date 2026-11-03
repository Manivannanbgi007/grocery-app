> **Archived (Phase 12, one kind cluster).** Replaced in Phase 23 by [deployment/kubernetes](../../../deployment/kubernetes/README.md). Kept for reference only; the links below are from the old location.

# Kubernetes manifests

Every service as a **Deployment** (the pods) and a **Service** (a stable name inside the cluster, e.g. `http://order-service`), plus the shared configuration and one **Ingress** in front of the API gateway. `kubectl apply -k k8s` applies everything into the namespace `grocery`.

```text
k8s/
├── kustomization.yaml        the list of everything below (+ namespace "grocery")
├── namespace.yaml
├── config/
│   ├── configmap.yaml        grocery-config: profile, MySQL host, Kafka, Schema Registry, Redis, Elasticsearch, mail, tracing, Loki
│   └── secret.yaml           grocery-secrets: database passwords, JWT secret, Razorpay secrets, MCP API key (local values!)
├── services/<service>.yaml   Deployment + Service per service (16); the two AI services also have a NetworkPolicy
└── ingress.yaml              grocery.localhost -> api-gateway
```

## What every Deployment has

| | Why |
|---|---|
| `image: grocery/<service>:0.0.1`, `imagePullPolicy: IfNotPresent` | Images built locally (`scripts\build-images.ps1`) and loaded into the cluster; in a real setup they come from a registry |
| `envFrom: grocery-config` + a few `env` entries | The same `${ENV_VARIABLE:default}` placeholders as locally: no code change, only other values. Passwords come from the Secret (`secretKeyRef`) |
| Port `8080` | The Dockerfile sets `SERVER_PORT=8080` in every image; the Service maps port 80 to it |
| `startupProbe` / `livenessProbe` / `readinessProbe` | Spring Boot's `/actuator/health/liveness` and `/readiness` (enabled in every `application.yml`). Startup allows up to 3 minutes (Flyway, Kafka) before liveness counts |
| `requests: 384Mi`, `limits: 640Mi` | The JVM takes 75 % of the limit as heap (`-XX:MaxRAMPercentage=75` in the Dockerfile) |
| Annotations `prometheus.io/scrape`, `/path`, `/port` on the pod | A Prometheus in the cluster that discovers pods by these (the common convention) scrapes `/actuator/prometheus` of every pod |
| `emptyDir` at `/app/uploads` (product-service, delivery-service) | Uploaded images; they disappear with the pod. Production: object storage (S3/MinIO) behind `ImageStorageService` |
| `PersistentVolumeClaim` `order-invoices` at `/app/invoices` (order-service only) | The stored invoice PDFs must survive restarts and new versions. `ReadWriteOnce` (kind's default storage class), so the Deployment uses `strategy: Recreate` (the old pod stops before the new one mounts the volume), and `securityContext` runs as user/group `10001` (fixed in the Dockerfile) so the volume is writable |

## Run it on a local cluster (kind)

The cluster only runs the **services**; the infrastructure stays in Docker Compose on your machine, and the pods reach it through `host.docker.internal` (Kafka has a separate listener for that on port **9094**). That is why the ConfigMap uses the `local` profile: the local databases already contain the seed data.

```powershell
.\scripts\start-infra.ps1                     # infrastructure (if it is not running yet)
# stop the services you run from the IDE / mvnw first: the cluster replaces them

# 1. A cluster of its own (its own kubeconfig file, so your current kubectl context is not touched)
kind create cluster --name grocery --kubeconfig $env:TEMP\grocery-kubeconfig
$env:KUBECONFIG = "$env:TEMP\grocery-kubeconfig"

# 2. Build the images and load them into the cluster (no registry needed)
.\scripts\build-images.ps1 -KindCluster grocery

# 3. Deploy and wait until every pod is ready
kubectl apply -k k8s
kubectl -n grocery wait --for=condition=available deployment --all --timeout=10m
kubectl -n grocery get pods

# 4. Reach the gateway like before, on http://localhost:8080 (the e2e\*.http files work unchanged)
kubectl -n grocery port-forward service/api-gateway 8080:80
```

Useful commands:

```powershell
kubectl -n grocery logs deployment/order-service -f          # logs of one service
kubectl -n grocery rollout restart deployment/order-service  # restart after loading a new image
kubectl -n grocery scale deployment/order-service --replicas=2
kind delete cluster --name grocery                            # remove everything again
```

**Ingress instead of port-forward:** install an ingress controller once
(`kubectl apply -f https://kind.sigs.k8s.io/examples/ingress/deploy-ingress-nginx.yaml`, with a kind cluster created with
`extraPortMappings` for port 80, see the kind documentation), then open `http://grocery.localhost`.

## The AI shopping assistant

- **Ollama stays in Docker Compose** (`.\scripts\start-infra.ps1 -Profiles ai`), like the other infrastructure; the
  `mcp-client-service` pod reaches it at `http://host.docker.internal:11434`. On a cluster with GPU nodes it can run as a
  Deployment with a GPU request instead.
- **NetworkPolicies** (in the two service files): only `api-gateway` pods may call `mcp-client-service`, and only
  `mcp-client-service` pods may call `mcp-server-service` (which also checks `MCP_API_KEY` from the Secret). A network
  plugin that supports NetworkPolicy enforces them (Calico, Cilium, recent kind); kubelet probes are not affected.
- The readiness of `mcp-client-service` does not depend on Ollama or the tools: when they are down it answers 503 quickly.

## Monitoring the pods

The monitoring tools stay in Docker Compose (`.\scripts\start-infra.ps1 -Profiles monitoring`, see
[docs/observability.md](../docs/observability.md)):

- **Traces and logs**: set `TRACING_EXPORT_ENABLED` and `LOKI_ENABLED` to `"true"` in `config/configmap.yaml`; the pods
  send to Jaeger (`host.docker.internal:4318`) and Loki like the services on your machine. Loki's `env` label is `kind`.
- **Metrics**: the Prometheus in Docker scrapes the services on your machine (`infrastructure/prometheus/targets/services.yml`),
  not the pods. To see the cluster's pods instead, `kubectl port-forward` a service to its usual port, or run a
  Prometheus in the cluster: it finds every pod through the `prometheus.io/*` annotations (with the Prometheus
  Operator: a `PodMonitor` selecting `app.kubernetes.io/part-of: grocery`). The alert rules
  (`infrastructure/prometheus/alerts`) and dashboards work unchanged as long as each pod gets a `service` label
  (relabel it from the pod label `app`).

## Scaling: why more replicas just work

- **Kafka consumers**: replicas of a service share its consumer group, so Kafka splits the partitions between them (3 per topic). Handlers are idempotent (`processed_event`), so a redelivery after a rebalance does no harm.
- **Outbox**: services only write rows; Debezium publishes them. No replica can publish twice.
- **Redis** (delivery presence, gateway rate limit) is shared by all replicas.
- **Scheduled job** (payment timeout): two replicas may pick the same payment; the entity's `@Version` lets only one win.
- **Not shared**: uploaded files in `emptyDir` (use object storage before running more than one product-service or delivery-service replica).
- **order-service** stays at **one replica** while its invoice volume is `ReadWriteOnce`. For more replicas, use a `ReadWriteMany` storage class (NFS, EFS, Azure Files ...) for `order-invoices` and switch back to rolling updates. Two replicas never generate the same invoice twice: they lock the order row first.

## Before production

These manifests are a working starting point; a real environment also needs:

- Managed or Helm-installed MySQL, Kafka + Schema Registry + Kafka Connect/Debezium, Elasticsearch and Redis, and the `prod` profile (empty databases, no seed data).
- Real secrets from a secret store (the values in `config/secret.yaml` are the public local ones), TLS on the Ingress.
- An image registry and versioned image tags (one CI pipeline per service folder).
- A `HorizontalPodAutoscaler` and a `PodDisruptionBudget` per service, and object storage for uploads.
- Logs: when a collector reads the pods' output (Grafana Alloy, Fluent Bit) instead of the services pushing to Loki,
  set `LOG_FORMAT: json` in `grocery-config`: one JSON object per line with the trace and correlation ids as fields
  ([observability.md](../docs/observability.md#log-format-text-or-json)).
