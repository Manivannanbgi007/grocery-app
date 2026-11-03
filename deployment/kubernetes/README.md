# Kubernetes: two kind clusters behind NGINX (Phase 23)

The whole shop (16 Spring Boot services + the React frontend) runs in **two local Kubernetes clusters**,
`grocery-cluster-a` and `grocery-cluster-b`, made with [kind](https://kind.sigs.k8s.io/) (Kubernetes in Docker). An
**entry NGINX** in front of them is the one address for the browser: **http://localhost**. Plan and decisions:
[docs/kubernetes-deployment-plan.md](../../docs/kubernetes-deployment-plan.md).

```text
Browser ── http://localhost
             │
       Entry NGINX  (Docker container "grocery-entry-nginx", nginx/nginx.conf)
             │  round robin; a cluster that does not answer is skipped
     ┌───────┴────────────────┐
 grocery-cluster-a        grocery-cluster-b          (also directly: http://localhost:8281 and :8282)
 ┌──────────────────────┐ ┌──────────────────────┐
 │ NGINX Gateway Fabric │ │ (the same manifests) │   gateway/: the cluster's entry point (Gateway API)
 │  /api, /media ... ─> api-gateway (3-5) ─> 15 services (1 each)
 │  everything else ─> frontend (3-5)            │
 └──────────┬───────────┘ └──────────┬───────────┘
            └──────────────┬─────────┘
   Shared data tier in Docker Compose (host.docker.internal):
   MySQL · Kafka + Schema Registry + Debezium · Redis · Elasticsearch · Mailpit
   Shared folders on your machine: product photos, proof photos, invoices
```

These clusters simulate two separate Kubernetes environments. They do **not** give real high availability: both run
in the same Docker on one machine, and the data tier exists once.

## What is where

```text
deployment/kubernetes/
├── kustomization.yaml          the list of everything below that goes into a cluster (kubectl apply -k)
├── clusters/                   cluster-a.yaml, cluster-b.yaml: kind configs (1 control plane + 2 workers, port, shared folders)
├── base/                       namespace "grocery", configmap.yaml (settings), secret.yaml (passwords), network-policy.yaml
├── services/<service>/         deployment.yaml (the pods), service.yaml (their name inside the cluster),
│                               hpa.yaml + pdb.yaml (api-gateway, product-read-service), networkpolicy.yaml (the AI services)
├── frontend/                   the React shop: deployment, service, hpa, pdb (image built from fresh-grocery/Dockerfile)
├── gateway/                    gateway.yaml (entry point, upload size, timeouts), routes.yaml (which path goes where)
├── addons/                     installed once per cluster: metrics-server (CPU per pod), nginx-gateway-fabric (Helm values)
└── nginx/nginx.conf            the entry NGINX in front of both clusters
```

The manifests are **the same for both clusters**; only the kubectl context changes
(`kind-grocery-cluster-a` / `kind-grocery-cluster-b`).

## Run it

Before you start:

- **Infrastructure:** it must be running: `.\scripts\start-infra.ps1`.
- **Ollama's model:** unload it if it is loaded (it takes 5 GB of Docker's memory): `docker exec grocery-ollama ollama stop qwen3:4b-instruct-2507-q4_K_M`.
- **Memory:** see [Memory](#memory) first. Two clusters need more memory than Docker Desktop gets by default.

```powershell
# 1. Images: the 16 services + the frontend (grocery/<name>:0.0.1). The first build takes ~30 minutes.
.\scripts\build-images.ps1

# 2. The clusters: kind + Metrics Server + NGINX Gateway Fabric (a few minutes each)
.\scripts\k8s-create-clusters.ps1                 # both; or -Clusters a

# 3. The shop: load the images into the clusters, apply deployment/kubernetes, wait until every pod is ready
.\scripts\k8s-deploy.ps1                          # both; or -Clusters a

# 4. The entry NGINX on http://localhost
.\scripts\k8s-entry-nginx.ps1
```

Open **http://localhost** (both clusters), **http://localhost:8281** (only A) or **http://localhost:8282** (only B).
The `e2e\*.http` files work against any of them (set `@gateway` to the address).

The same steps by hand, as in the deployment guide:

```powershell
kind create cluster --config deployment/kubernetes/clusters/cluster-a.yaml
kind load docker-image grocery/product-service:0.0.1 --name grocery-cluster-a
kubectl --context kind-grocery-cluster-a apply -k deployment/kubernetes                        # everything
kubectl --context kind-grocery-cluster-a apply -f deployment/kubernetes/services/product-service/ # one service
```

Remove everything again: `.\scripts\k8s-delete-clusters.ps1`. Nothing of value is lost: the data is in Docker
Compose, and the photos and invoices are in folders on your machine.

## What every service's Deployment has

| | Why |
|---|---|
| `image: grocery/<service>:0.0.1`, `imagePullPolicy: IfNotPresent` | Built locally and copied into the cluster with `kind load` (no registry). Versioned tags, never `latest`: a rollback is "apply the old tag" |
| `strategy: RollingUpdate`, `maxSurge: 1`, `maxUnavailable: 0` | A new version starts one new pod, waits until it is ready, then stops one old pod: no downtime |
| `startupProbe` / `livenessProbe` on `/actuator/health/liveness` | Liveness does **not** include MySQL, Kafka or Redis: when the database is down the pods are not restarted (that would not help), they only stop getting traffic. Startup allows 5 minutes. Liveness waits up to 5 s per check (`timeoutSeconds`; the default 1 s killed healthy pods on a busy machine) and restarts only after 3 failed checks in a row |
| `readinessProbe` on `/actuator/health/readiness` | A pod gets traffic only when it is ready (started, not shutting down) |
| `lifecycle.preStop: sleep 10` + `terminationGracePeriodSeconds: 45` | On "stop" the pod keeps serving 10 s while the Service and the Gateway take it out of their lists, then Spring Boot finishes the running requests (graceful shutdown, up to 30 s) |
| `requests: cpu 100m, memory 384Mi`, `limits: cpu 1, memory 640Mi` | What the scheduler reserves, and the most a pod may use. The JVM takes 75 % of the memory limit as heap (`-XX:MaxRAMPercentage=75` in the Dockerfile). The autoscaled services request `cpu: 250m` |
| `envFrom: grocery-config` + `env` with `secretKeyRef` | The same `${ENV_VARIABLE:default}` placeholders as locally, other values: settings from `base/configmap.yaml`, passwords from `base/secret.yaml`. No code change |
| `automountServiceAccountToken: false` | The services never call the Kubernetes API, so they get no token (least privilege) |
| `prometheus.io/*` annotations | A Prometheus in the cluster that discovers pods by these scrapes `/actuator/prometheus` |

## Autoscaling (3 to 5 pods)

`api-gateway`, `product-read-service` and `frontend` each have a **HorizontalPodAutoscaler** (`hpa.yaml`):

- **Range:** at least 3 pods, at most 5.
- **Trigger:** a pod is added when the pods use more than 70 % of their CPU request on average. One is removed
  again after 5 minutes below that.
- **Spread:** `topologySpreadConstraints` put the 3 pods on both workers, so one node down never takes all of
  them.
- **Maintenance:** a **PodDisruptionBudget** (`pdb.yaml`) keeps at least 2 running while a node is emptied
  (`kubectl drain`).
- **Metrics:** the autoscaler reads the CPU from the **Metrics Server** (`addons/metrics-server`). Check it with
  `kubectl --context kind-grocery-cluster-a top pods -n grocery`.

The other 13 services run **one pod per cluster**, which means two copies across both clusters. Each can scale
the same way: copy `hpa.yaml` and `pdb.yaml`, and remove `replicas: 1` from its Deployment.

## Why two clusters can share one set of data

- **MySQL, Kafka, Redis, Elasticsearch** run once, in Docker Compose. Both clusters use them through
  `host.docker.internal`, the way a production cluster uses managed databases.
  - Two separate MySQL databases, one per cluster, would never stay in sync (the guide warns about exactly that).
- **Kafka:** the pods of both clusters join the same consumer groups (e.g. `order-service`), so Kafka splits the
  partitions between them. Each event is handled once, by whichever pod owns its partition.
  - Each topic has 3 partitions, so at most 3 pods of a group do work at the same time.
  - The handlers are idempotent (`processed_event`), so an event redelivered after a rebalance does no harm.
- **Outbox:** services only write rows; Debezium publishes them once, whichever cluster wrote them.
- **Redis** (delivery presence, gateway rate limits) is shared too. A user's rate limit counts the same in both
  clusters.
- **Scheduled jobs** (the payment timeout): two pods may pick the same payment; the entity's `@Version` lets only
  one win.
- **Login:** tokens are JWTs and refresh tokens live in MySQL, so a user can switch clusters between two requests.
  Social login keeps its state in the database as well.
- **Files:** product photos, proof-of-delivery photos and invoice PDFs are written to three folders on your
  machine, mounted into every worker node (`clusters/cluster-*.yaml`, `extraMounts`) and into the pods (`hostPath`).
  - These are the same folders the services use when run with mvnw.
  - A photo uploaded through cluster A therefore shows through cluster B.
  - Production uses object storage (S3) or a ReadWriteMany volume instead.

## The entry NGINX and failover

- **Where it runs:** `nginx/nginx.conf` runs in Docker on port 80, in kind's Docker network "kind". It finds the
  clusters by their node names (`grocery-cluster-a-control-plane:30080`).
- **How it shares the traffic:** it takes turns between the two clusters (round robin).
- **When a cluster is down:** a request that cannot reach a cluster (refused, no answer within 2 s, or a
  502/503/504) is sent to the other cluster, and the failed cluster is skipped for 10 s.
  - A POST is resent only when it never reached the first cluster, so an order is never placed twice.
- **Limit:** open-source NGINX has only these *passive* checks; *active* health checks (probing a cluster before a
  request fails) are an NGINX Plus feature. In production a cloud load balancer with health checks does this job.

Inside each cluster, **NGINX Gateway Fabric** implements the **Gateway API**, the successor of Ingress. The
community ingress-nginx controller was retired in March 2026.

- `gateway/gateway.yaml` is the entry point (port 80 in the cluster, node port 30080 outside). It also sets the
  upload size (6 MB) and the timeouts (180 s).
- `gateway/routes.yaml` sends `/api`, `/media`, `/delivery-media` and Swagger to `api-gateway`, and everything else
  to `frontend`.

## Network rules

- **`base/network-policy.yaml`:** grocery pods accept connections only from pods of the namespace `grocery`.
- **The AI services** have stricter rules (`services/mcp-*/networkpolicy.yaml`): only `api-gateway` may call
  `mcp-client-service`, and only `mcp-client-service` may call `mcp-server-service`, which also checks
  `MCP_API_KEY`.
- **Enforcement:** kind's network plugin enforces NetworkPolicies. Kubelet health probes are not affected.

## Memory

Measured on 2026-10-29 (details in [Results](#results)):

| | Memory |
|---|---|
| One Spring Boot pod | 330 Mi on average right after the start, ~450 Mi after 20 minutes (limit 640 Mi) |
| One complete cluster (20 Spring Boot pods, 3 frontends, gateway, nodes, add-ons) | ~9 GB at the start, ~11 GB later |
| The Docker Compose data tier (without Ollama's model) | ~3.8 GB |
| Ollama's chat model, while loaded | ~5 GB (more with its cache) |

Docker Desktop gets half of the machine's memory by default (15.5 GB on a 32 GB machine). One complete cluster plus
the data tier fit **only when the rest of the machine leaves enough free RAM**. With little free RAM Windows pages
the Docker VM out, probes time out and Docker itself can stop answering. Two complete clusters need about **26 GB**
for Docker, so a machine with 48-64 GB.

To give Docker more, set it in `%USERPROFILE%\.wslconfig`, then run `wsl --shutdown` and start Docker Desktop
again:

```ini
[wsl2]
memory=26GB
```

- **Stop the services on your machine** while the clusters run: the clusters replace them.
- **No AI chat:** with both clusters running there is no room for Ollama's model, so the AI assistant answers
  "not available right now".
- **When you do not need the clusters**, stop them; `docker start` brings them back in under a minute, with
  everything they had:

```powershell
docker stop grocery-entry-nginx grocery-cluster-a-control-plane grocery-cluster-a-worker grocery-cluster-a-worker2
docker start grocery-cluster-a-control-plane grocery-cluster-a-worker grocery-cluster-a-worker2
```

## Useful commands

```powershell
$ctx = "kind-grocery-cluster-a"
kubectl --context $ctx -n grocery get pods -o wide                  # which pod runs on which node
kubectl --context $ctx -n grocery get deployments,hpa,services
kubectl --context $ctx -n grocery top pods                          # CPU and memory per pod (Metrics Server)
kubectl --context $ctx -n grocery logs deployment/order-service -f  # logs of one service
kubectl --context $ctx -n grocery describe pod <pod-name>           # why a pod does not start (events at the end)
kubectl --context $ctx -n grocery rollout restart deployment/order-service   # after loading a new image with the same tag
kubectl --context $ctx -n grocery rollout undo deployment/order-service      # back to the previous version
kubectl --context $ctx -n grocery scale deployment/cart-service --replicas=2
```

## The AI shopping assistant and monitoring

- **Ollama stays in Docker Compose** (`.\scripts\start-infra.ps1 -Profiles ai`). The `mcp-client-service` pods reach
  it at `http://host.docker.internal:11434`. The readiness of `mcp-client-service` does not depend on Ollama: without
  it, the chat answers 503 "not available right now" quickly.
- **Traces and logs:** set `TRACING_EXPORT_ENABLED` and `LOKI_ENABLED` to `"true"` in `base/configmap.yaml` while the
  monitoring tools run (`.\scripts\start-infra.ps1 -Profiles monitoring`). Loki's `env` label is `kind`.
- **Metrics:** the Prometheus in Docker scrapes the services on your machine, not the pods. A Prometheus inside the
  cluster finds every pod through the `prometheus.io/*` annotations (with the Prometheus Operator: a `PodMonitor`
  selecting `app.kubernetes.io/part-of: grocery`); relabel the pod label `app` to `service` and the alert rules and
  dashboards work unchanged.
- **The super admin's System page** reads Prometheus through `/prometheus`, which only the Vite dev server forwards.
  In the clusters the frontend's NGINX answers it with 503, so the page says "Prometheus did not answer".

## Before production

These manifests are a working base. A real environment also needs:

- **Data tier:** managed or operator-run MySQL (with replicas and tested restores), Kafka + Schema Registry + Kafka
  Connect, Elasticsearch and Redis, and the `prod` profile (empty databases, no seed data).
- **Secrets:** real values from a secret store (the values in `base/secret.yaml` are the public local ones),
  encryption of Secrets at rest, and RBAC.
- **TLS** on the entry point, and a load balancer with active health checks across clusters in different failure
  domains.
- **Images:** a registry, and one CI pipeline per service folder.
- **Files:** object storage for photos and invoices.
- **Monitoring:** a Prometheus and a log collector in the cluster (then `LOG_FORMAT: json`, see
  [observability.md](../../docs/observability.md#log-format-text-or-json)).

## Results

See [docs/kubernetes-deployment-plan.md](../../docs/kubernetes-deployment-plan.md#progress).
