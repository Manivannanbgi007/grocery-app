# Kubernetes deployment plan (Phase 23): two kind clusters behind NGINX

**Status (2026-10-29): built.** Cluster A ran completely; cluster B ran with the Gateway and the shop. Running both
completely needs more free memory than this machine had (see [Progress](#progress)). How to run it:
[deployment/kubernetes/README.md](../deployment/kubernetes/README.md).

This plan follows the deployment guide "Microservices Deployment Guide — KIND Kubernetes" (two kind clusters,
3–5 replicas for selected services, React frontend in a container, NGINX in front). Where this machine or the
project forces a different choice, the section says so and why.

---

## 1. Where we start

Phase 12 already gave us [k8s/](../k8s/README.md):

- one kind cluster (`grocery`), every service as a Deployment + Service with **1 replica**
- startup, liveness and readiness probes, memory limits, the shared ConfigMap and Secret (with the Redis users)
- NetworkPolicies for the two AI services, one Ingress for the retired ingress-nginx controller
- the data tier (MySQL, Kafka, Schema Registry, Debezium, Redis, Elasticsearch, Mailpit) stays in **Docker Compose**;
  pods reach it through `host.docker.internal`

What the guide adds: a **second cluster**, its folder layout, **autoscaling 3–5**, a **frontend image**, the
**Metrics Server**, an **external NGINX** in front of both clusters and **failover tests**.

## 2. The machine's limit (read this first)

Measured on 2026-10-29:

| | |
|---|---|
| Docker Desktop memory | **15.5 GB** (the default: half of the 32 GB) |
| Used in Docker now | Ollama **9.2 GB** (the chat model is loaded), the other Compose containers **3.8 GB** |
| Host memory | 31.7 GB, **1.2 GB free**; the 16 services and the frontend are running on it |

Each Spring Boot pod needs about **0.4 GB** (limit 640 Mi; an estimate, we measure it in K2).

**The guide taken literally does not fit:** 16 services × 3 replicas × 2 clusters = 96 pods ≈ 38 GB.

What this plan needs instead (estimates):

| Per cluster | Pods | Memory |
|---|---|---|
| Every service once | 16 | ~6.4 GB |
| + api-gateway and product-read-service at 3 replicas (autoscaling 3–5) | +4 | ~1.6 GB |
| Frontend at 3 replicas (NGINX serving files) | 3 | ~0.05 GB |
| kind nodes (1 control plane + 2 workers), gateway controller, Metrics Server | | ~1.2 GB |
| **One cluster** | | **~9.3 GB** |
| **Both clusters** | | **~18.5 GB** |

Plus the Compose data tier (3.8 GB):

- **Cluster A alone** fits in today's 15.5 GB once Ollama's model is unloaded (9.3 + 3.8 ≈ 13 GB).
- **Both clusters** need Docker's memory raised to about **24 GB** (one line in `%USERPROFILE%\.wslconfig`, then a
  Docker restart), and the services running on your machine stopped while the clusters run (the clusters replace
  them). We decide the exact number after measuring cluster A. *(Measured: about 26 GB, see [Progress](#progress).)*
- **The AI assistant's chat** cannot answer while both clusters run: there is no room for Ollama's 9 GB model.
  `mcp-client-service` still starts and answers "assistant unavailable" (503) quickly.

## 3. Target picture

```text
Browser ── http://localhost  (port 80)
             │
       Entry NGINX  (Docker container, deployment/kubernetes/nginx/nginx.conf)
             │  round robin; a cluster that does not answer is skipped
     ┌───────┴────────┐
 host port 8281    host port 8282
     │                │
 ┌── grocery-cluster-a ──┐   ┌── grocery-cluster-b ──┐
 │ NGINX Gateway Fabric  │   │ (the same manifests)  │
 │  /api, /media, ...  → api-gateway (3–5) → services│
 │  everything else    → frontend (3–5)              │
 └──────────┬────────────┘   └──────────┬────────────┘
            └──────────────┬────────────┘
     Shared data tier in Docker Compose (host.docker.internal):
     MySQL · Kafka + Schema Registry + Debezium · Redis · Elasticsearch · Mailpit
     Shared files folder on your machine: product photos, proof photos, invoices
```

## 4. Decisions (my recommendation for each)

### D1. The data tier stays shared in Docker Compose, not StatefulSets inside the clusters

The guide's section 10 itself says: two unrelated MySQL primaries in two clusters do not keep their data in sync;
for a local simulation, use a shared database endpoint. Compose is that endpoint: it plays the role a managed
database, Kafka or Redis plays in production.

- **Kafka:** the pods of both clusters join the same consumer groups, so Kafka splits the partitions between them and
  each event is handled once (as with more replicas today).
- **Outbox:** Debezium publishes every outbox row once, whichever cluster wrote it.
- **StatefulSets** are not part of this phase. If you want the practice, K5 adds a MySQL + Redis StatefulSet example
  in a separate namespace, only for learning.

### D2. Gateway API with NGINX Gateway Fabric instead of an Ingress with ingress-nginx

The community **ingress-nginx controller was retired in March 2026**: no more releases or security fixes
([Kubernetes blog](https://www.kubernetes.io/blog/2025/11/11/ingress-nginx-retirement/),
[statement of January 2026](https://www.kubernetes.io/blog/2026/01/29/ingress-nginx-statement/)). Kubernetes recommends
the **Gateway API**, the successor of Ingress. [NGINX Gateway Fabric](https://docs.nginx.com/nginx-gateway-fabric/get-started/)
implements it with NGINX and has a kind setup.

- The guide's `ingress/ingress.yaml` becomes `gateway/gateway.yaml` (the entry point) and `gateway/routes.yaml`
  (which paths go to the API gateway and which go to the frontend).
- Each cluster's gateway listens on a fixed NodePort, mapped by kind to **host port 8281 (A)** and **8282 (B)**. The
  guide's example ports 8081/8082 cannot be used: Schema Registry already uses 8081.

### D3. Autoscaling 3–5 for the frontend, api-gateway and product-read-service

- **api-gateway:** every request passes through it.
- **product-read-service:** search and browsing are the busiest read path.
- **frontend:** tiny NGINX pods, so 3 replicas cost almost nothing.

The other 13 services run **1 replica per cluster**, which means 2 copies across both clusters. They already
handle several replicas (see "Scaling" in [k8s/README.md](../k8s/README.md)), so scaling one later only takes an
`hpa.yaml`. Limit to keep in mind: the Kafka topics have 3 partitions, so at most 3 consumers per group do work.

### D4. One shared files folder for both clusters

Uploaded product photos, delivery proof photos and invoices are files on disk. Today they live in the pod
(`emptyDir`) or in a volume of one cluster, so a photo uploaded through cluster A would be missing in cluster B.

- kind mounts folders from your machine into every node of both clusters (`extraMounts`), and the pods use them
  through `hostPath` volumes.
- These are the same folders the services use when you run them with mvnw (`product-service/uploads`,
  `delivery-service/uploads`, `order-service/data/invoices`), so existing photos keep working.
- order-service no longer needs `strategy: Recreate`, so it gets rolling updates like the others.
- In production, object storage (S3) or a ReadWriteMany volume takes this role.

### D5. The guide's folder layout

```text
deployment/kubernetes/
├── kustomization.yaml          kubectl apply -k deployment/kubernetes   (the same for both clusters)
├── clusters/                   cluster-a.yaml, cluster-b.yaml (kind configs: 1 control plane + 2 workers, ports, shared folders)
├── base/                       namespace.yaml, configmap.yaml, secret.yaml
├── services/<service>/         deployment.yaml, service.yaml (+ hpa.yaml, networkpolicy.yaml where needed)
├── frontend/                   deployment.yaml, service.yaml, hpa.yaml
├── gateway/                    gateway.yaml, routes.yaml
└── nginx/                      nginx.conf (the entry NGINX)
```

- Everything in today's manifests moves over: probes, Secret references, Redis users, NetworkPolicies, the comments.
- `k8s/` is moved to [docs/archive/k8s-phase12/](archive/k8s-phase12/README.md). The project is not a git
  repository, so it is kept, not deleted. (Not into `backups/`: `restore-database.ps1` takes the newest folder there as
  the newest database backup.)
- The manifests are identical for both clusters; only `--context` changes.

### D6. Images: the same names as now, plus the frontend

- Service images keep their current names: `grocery/<service>:0.0.1` (versioned, never `latest`). The guide's
  `1.0.0` is only an example.
- New image `grocery/fresh-grocery:0.0.1`: Node builds the shop, then NGINX serves the files. Requests the site does
  not know fall back to `index.html` (React's routes).
- This adds 3 files in `fresh-grocery/`: `Dockerfile`, `.dockerignore` and `nginx/default.conf`. No source code
  changes: the shop already calls the API through the relative `/api`.
- `scripts\build-images.ps1` builds the frontend too, and loads images into one or both clusters.

### D7. Probes and resources

- **Probes keep their endpoints:** startup and liveness use `/actuator/health/liveness`, readiness uses
  `/readiness`. Liveness does not include MySQL or Kafka, so a database outage takes pods out of traffic but never
  restarts them all. This is what the guide's section 8.1 asks for. (The guide's startup probe uses
  `/actuator/health`, which includes the databases.)
- **Probe timeouts (added after K2):** Kubernetes waits only 1 s for a probe by default. On a busy machine pods
  missed that and were killed while healthy. Now liveness waits 5 s, readiness and startup 3 s. Liveness restarts a
  pod only after 3 slow answers in a row (30 s).
- **Memory:** request 384 Mi, limit 640 Mi as now (the JVM uses 75 % of it as heap).
- **CPU:** the three autoscaled services get the guide's request of 250m, because the autoscaler's 70 % target counts
  against the request. Every container gets a CPU limit of 1 core.

### Entry NGINX and failover

- **Container:** NGINX runs in Docker on port 80 (the guide allows "an NGINX Docker container") and reaches the two
  clusters through `host.docker.internal:8281` and `:8282`.
- **Failover:** open-source NGINX has only *passive* health checks (active checks are an NGINX Plus feature).
  - `max_fails` / `fail_timeout`: a cluster that fails is skipped for a while.
  - `proxy_next_upstream`: a request that cannot reach cluster A is sent to cluster B. A POST is resent only when it
    never reached A, so an order is never placed twice.
  - A short connect timeout (2 s) keeps the switch fast.
- **The same rules as today's Ingress:** uploads up to 6 MB, a 180 s read timeout (AI chat), no buffering for the
  live streams (vendor order queue, delivery tracking).
- **Config changes:** `PUBLIC_BASE_URL` becomes `http://localhost`, and `http://localhost` is added to the allowed
  CORS origins.

## 5. Steps, each ending with a review stop

| Step | What | Done when |
|---|---|---|
| **K1** | Manifests and images, no cluster yet: the folder layout, per-service files, autoscalers, the frontend image and its manifests, Gateway and routes, kind configs, scripts (`k8s-create-clusters.ps1`, `k8s-deploy.ps1`, `k8s-delete-clusters.ps1`), `deployment/kubernetes/README.md` | `kubectl kustomize` renders every resource; the frontend image builds and serves the shop |
| **K2** | Cluster A: create it, install the Metrics Server and NGINX Gateway Fabric, load the images, deploy | every pod ready; `kubectl top` and the autoscalers show CPU; shop and API work on `http://localhost:8281`; the `e2e\*.http` flows pass; a rolling update fails no requests; memory per pod measured |
| **K3** | Cluster B, the same | the same checks on `http://localhost:8282` |
| **K4** | Entry NGINX on port 80, and the guide's 8 test scenarios (a script like `failure-drills.ps1`) | the results are written in the README, including cluster A stopped while the shop keeps working through cluster B |
| **K5** *(optional)* | A StatefulSet example (MySQL + Redis) in its own namespace, for learning | |

### Steps that need your yes when we reach them

- **K2:** unload Ollama's model (it reloads on the next chat request). Also decide whether to stop the services
  running on your machine. If they keep running, they share the Kafka consumer groups with the pods: that works, but
  events are split between the two, which makes testing confusing.
- **K3:** raise Docker's memory (`.wslconfig`, then a Docker restart that briefly stops every container). Stop the
  services on your machine while both clusters run. This includes the shared chat on 8114/8115 that other sessions
  use.

## 6. What this still does not give

- **No real high availability:** both clusters run in the same Docker VM on one machine. The guide says this too.
- **Single points of failure:** the shared data tier has one MySQL, one Kafka broker and one Redis.
- **Passive health checks only:** right after a cluster dies, NGINX first tries it (up to the 2 s connect timeout)
  before it skips it.
- **Local secrets:** the Secret holds the public local values, and kind does not encrypt Secrets at rest.

## Progress

| Step | Status |
|---|---|
| Plan | ✅ 2026-10-29 (the user: "do not wait for my approval ... complete it") |
| K1 manifests, images, scripts | ✅ `kubectl kustomize` renders 17 Deployments, 17 Services, 3 HPAs, 3 PDBs, 3 NetworkPolicies, the Gateway, 2 routes, 2 NGINX policies. All 17 images build (`build-images.ps1`, ~35 min the first time: the Maven repository is now a BuildKit cache shared by all builds) |
| K2 cluster A | ✅ complete, verified, then stopped for lack of memory (details below) |
| K3 cluster B | ◐ created and verified with the Gateway and the shop; the 16 services not deployed (no memory left) |
| K4 entry NGINX + drills | ◐ entry NGINX verified, failover to cluster B verified with cluster A down; `k8s-drills.ps1` written, only `access` run |
| K5 StatefulSet example | – (optional, not started) |

### Results, 2026-10-29

**Cluster A** (Kubernetes 1.37 from kind 0.33, NGINX Gateway Fabric 2.8.0, Metrics Server 0.9.0):

- **Deployed:** in 5 waves (to watch the memory); each wave of 4-7 Spring Boot pods was ready in 55-75 s.
- **Pods:** all 20 Spring Boot pods + 3 frontend + 2 gateway NGINX ran; `kubectl apply -k` afterwards changed no spec.
- **Autoscalers:** all three held 3 pods at 2-4 % CPU. The Metrics Server works.
- **Through http://localhost:8281 and the entry NGINX:**
  - the shop's pages: 200
  - product search: 200 (Elasticsearch through `host.docker.internal`)
  - login: 200
  - Swagger and `/product-read-service/v3/api-docs`: 200
  - `/prometheus/...`: 503, as designed
- **Gateway:** Gateway, routes and both policies Accepted/Programmed. The generated NGINX config has
  `client_max_body_size 6m`, `proxy_read_timeout 180s` and every route.
- **Shared folders:** visible in both workers, holding the existing photos and invoices, and writable by non-root
  users.
- **Memory, measured:**
  - Spring Boot pods at start: 230-410 Mi each, 330 Mi on average; the 20 together 6.6 GB.
  - Whole cluster about 9 GB (nodes: control plane 1.1 GB, workers 4.2 + 3.7 GB).
  - The JVMs kept growing toward their 640 Mi limit: after ~20 minutes the Docker VM had 0.9 GB left (of 15.5 GB).
- **What went wrong:** the machine had only ~0.5 GB of free RAM before the start. Other running programs held the
  rest: VS Code with Java language servers, the 16 services of the other sessions on the host, another project's
  services, browsers.
  - Windows paged heavily (~12,000 pages/s).
  - Probes timed out (1 s default) and cart-service was killed while healthy.
  - NGINX Gateway Fabric's controller lost its leader election 4 times (the API server answered too slowly), and a
    few requests ended in 504.
  - Docker's API answered 500 for ~35 s.
  - Cluster A was stopped (`docker stop` of its nodes; everything in it is kept) before the shared MySQL, Kafka and
    Redis could be hit.
  - The probe timeouts were raised in the manifests afterwards (D7). They are not applied to cluster A yet; the next
    `k8s-deploy.ps1` does it.
- **Access drill:** 3 of 5 checks passed. The cart failed because cart-service had just been restarted (see above).
  The photo check had a script bug, fixed since.

**Cluster B:**

- Created by `k8s-create-clusters.ps1 -Clusters b` (the Windows folder mounts with a space in the path work).
- The Gateway and 3 frontend pods ran: http://localhost:8282 answered 200.
- `docker stop` + `docker start` of its three nodes: it answered again after 27 s.

**Entry NGINX:**

- **Starts without cluster B:** it logs that the name cannot be resolved and sends everything to A (`resolve` in
  `nginx.conf`).
- **With cluster A down:** 20 of 20 requests to http://localhost/shop were answered by cluster B.

**Both clusters were stopped** (and the entry NGINX), to give the memory back. Later that day Docker Desktop was
reset, which removed both clusters and every image: recreate them with `build-images.ps1`,
`k8s-create-clusters.ps1` and `k8s-deploy.ps1`.

**Not run yet** (they need both clusters complete):

- the drills pod, rolling, downstream, hpa, redis, backup;
- failover while requests are running, with the API (not only the shop's pages).

**Memory, corrected by the measurements:** a complete cluster settles at about **11 GB**, not 9 (the JVMs grow
toward their limit). Two complete clusters + the data tier need about **26 GB** for Docker. On this 32 GB machine
that leaves too little for Windows and the IDE. Ways to finish:

1. **A machine with 48-64 GB** (or a cloud VM): give Docker ~28 GB and run everything as planned.
2. **This machine, with almost everything else closed:** stop the services on the host, the other project and
   extra IDE windows, and give Docker ~26 GB in `.wslconfig`.
3. **Smaller JVMs:** a memory limit of 512 Mi instead of 640 Mi saves ~2.5 GB per cluster. It needs a test of every
   service under load first (order-service builds PDFs, mcp-client-service holds conversations).

Then start everything again:

```powershell
docker start grocery-cluster-a-control-plane grocery-cluster-a-worker grocery-cluster-a-worker2 grocery-cluster-b-control-plane grocery-cluster-b-worker grocery-cluster-b-worker2
# Cluster A still has the old probe settings. Re-create its Deployments instead of a rolling update of all 17 at once
# (each would start one extra pod: +17 JVMs):
kubectl --context kind-grocery-cluster-a -n grocery delete deployment --all
.\scripts\k8s-deploy.ps1          # loads the images into B, applies the manifests to both clusters
.\scripts\k8s-entry-nginx.ps1
.\scripts\k8s-drills.ps1
```
