# Swish App — Infrastructure & Resource Procurement Plan

> **Version**: 1.0  
> **Date**: 2026-06-11  
> **Author**: Engineering Team  
> **Status**: Draft — pending load test baseline metrics

---

## 1. Executive Summary

This document defines the compute, API, and operational resources required to
deploy the Swish App's hybrid agentic architecture (Spring Boot + Python
Governance + Gemini LLM). The **current deployment target is on-premises**
(homelab / bare-metal servers), with a **future migration path to Google
Kubernetes Engine (GKE)**. It covers three resource tiers: **Dev/CI**,
**Staging (on-prem)**, and **Production (on-prem → GKE)**.

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client Layer                             │
│   React Micro-Frontends (customer / wholesaler / rider / admin) │
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTPS
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                   Spring Boot Backend                            │
│                   (ResilientLlmGateway)                          │
│                                                                  │
│  ┌──────────────┐  ┌───────────────────┐  ┌──────────────────┐  │
│  │ CustomerSupp. │  │ B2BProcurement   │  │ DynamicPricing   │  │
│  │ Agent         │  │ Agent            │  │ Agent            │  │
│  └──────┬───────┘  └────────┬─────────┘  └────────┬─────────┘  │
│         └──────────────┬────┘────────────────┬─────┘            │
│                        ▼                     ▼                  │
│  ┌───────────────────────────┐  ┌────────────────────────────┐  │
│  │ PythonGovernanceAdapter   │  │ GeminiFreeAdapter          │  │
│  │ (primary governed path)   │  │ (PII-gated cloud fallback) │  │
│  └────────────┬──────────────┘  └────────────┬───────────────┘  │
│               │                              │                  │
└───────────────┼──────────────────────────────┼──────────────────┘
                │ REST / HTTP                  │ HTTPS
                ▼                              ▼
┌───────────────────────────┐   ┌──────────────────────────────┐
│ Python Governance Service │   │ Google Gemini 2.0 Flash API  │
│ (FastAPI + Uvicorn)       │   │ (via OpenAI-compat endpoint) │
│ PII gate · guardrails ·   │   └──────────────────────────────┘
│ routing · self-correction │
│         ▼                 │
│ ┌─────────────────────┐   │
│ │ Ollama (Qwen 2.5 /  │   │
│ │ Gemma 4B local)     │   │
│ └─────────────────────┘   │
└───────────────────────────┘
```

---

## 3. Resource Tiers

### 3.1 Dev / CI Tier

| Component                  | Resource                      | Quantity | Notes                                     |
|----------------------------|-------------------------------|----------|--------------------------------------------|
| Spring Boot Backend        | 1 vCPU, 2 GB RAM              | 1        | Local Docker or macOS process              |
| Python Governance Service  | 0.5 vCPU, 512 MB RAM          | 1        | Uvicorn single-worker                      |
| Ollama (Qwen 2.5 / Gemma) | 4 vCPU, 8 GB RAM (+ GPU opt.) | 1        | Local model server                         |
| PostgreSQL                 | 0.5 vCPU, 512 MB RAM          | 1        | Docker container                           |
| Redis                      | 256 MB RAM                    | 1        | Docker container                           |
| Gemini API                 | Free tier                     | —        | 15 RPM / 1M tokens/day (free)              |
| **Total**                  |                               |          | ~6 vCPU, 11.5 GB RAM                      |

### 3.2 Staging Tier (On-Prem)

| Component                  | Resource                           | Quantity | Notes                                            |
|----------------------------|------------------------------------|----------|--------------------------------------------------|
| Spring Boot Backend        | 2 vCPU, 4 GB RAM (Docker/systemd)  | 2        | Nginx reverse-proxy, health-checked              |
| Python Governance Service  | 1 vCPU, 1 GB RAM (Docker/systemd)  | 2        | Uvicorn 2-worker, behind Nginx internal upstream  |
| Ollama (Gemma 4B)          | 4 vCPU, 16 GB RAM + consumer GPU   | 1        | RTX 3060/4060 or equivalent for inference        |
| PostgreSQL 15              | 2 vCPU, 8 GB RAM (Docker or native)| 1        | Single-node, 50 GB SSD, pg_dump backups          |
| Redis 7                    | 512 MB RAM (Docker)                | 1        | Single-node, AOF persistence                     |
| Gemini API                 | Pay-as-you-go                      | —        | Estimated ~500K tokens/day (see §4)              |
| **Hardware Total**         |                                    |          | **~10 vCPU, 30 GB RAM, 1 GPU** (1 server)       |

### 3.3 Production Tier (On-Prem → GKE Migration)

#### Current: On-Prem Production

| Component                  | Resource                             | Quantity | Notes                                           |
|----------------------------|--------------------------------------|----------|--------------------------------------------------|
| Spring Boot Backend        | 4 vCPU, 8 GB RAM (Docker/systemd)    | 2–3      | Nginx LB with sticky sessions                   |
| Python Governance Service  | 2 vCPU, 2 GB RAM (Docker/systemd)    | 2        | Uvicorn 4-worker, process-managed by systemd     |
| Ollama (Gemma 4B / Qwen)   | 8 vCPU, 32 GB RAM + RTX 4070/A4000  | 1–2      | Dedicated GPU server(s)                          |
| PostgreSQL 15              | 4 vCPU, 16 GB RAM                   | 1        | Streaming replication standby, 200 GB SSD        |
| Redis 7                    | 2 GB RAM                            | 1        | Sentinel for HA (or single-node + AOF)           |
| Nginx + Certbot            | Shared with backend host             | 1        | TLS termination, rate limiting                   |
| Gemini API                 | Pay-as-you-go + budget alerts        | —        | Estimated ~2M tokens/day (see §4)                |
| **Hardware Total**         |                                      |          | **~24 vCPU, 68 GB RAM, 1–2 GPUs** (2–3 servers) |

#### Future: GKE Migration Target

| Component                  | GKE Resource                         | Quantity | Notes                                           |
|----------------------------|--------------------------------------|----------|--------------------------------------------------|
| Spring Boot Backend        | e2-standard-2 (2 vCPU, 8 GB)        | 3–6      | HPA: target 60% CPU, min 3                      |
| Python Governance Service  | e2-medium (1 vCPU, 4 GB)             | 2–4      | HPA: target 70% CPU, min 2                      |
| Ollama                     | n1-standard-8 + T4 GPU               | 2        | GPU node pool, zone-redundant                    |
| Cloud SQL (PostgreSQL 15)  | db-custom-4-16384                    | 1        | HA enabled, 200 GB SSD, automated backups        |
| Memorystore (Redis 7)      | standard-M2 (5 GB)                   | 1        | HA with automatic failover                       |
| Cloud Armor WAF            | Standard tier                        | 1        | DDoS + OWASP Top 10 rules                       |
| Cloud CDN                  | Standard                             | 1        | Frontend static assets                           |
| **Est. Monthly Cost**      |                                      |          | **~$1,200 – $2,100/mo** (excl. GPU premium)     |

---

## 4. LLM API Cost Projections (Gemini 2.0 Flash)

### Pricing (as of June 2026)

| Metric           | Rate                              |
|------------------|-----------------------------------|
| Input tokens     | $0.075 per 1M tokens              |
| Output tokens    | $0.30 per 1M tokens               |
| Context caching  | $0.01875 per 1M tokens (storage)  |

### Usage Estimates

| Scenario         | DAU   | Queries/User/Day | Avg Input Tokens | Avg Output Tokens | Daily Input | Daily Output | Daily Cost |
|------------------|-------|-------------------|------------------|-------------------|-------------|--------------|------------|
| **Staging**      | 50    | 5                 | 500              | 300               | 125K        | 75K          | ~$0.03     |
| **Prod (Low)**   | 1,000 | 3                 | 500              | 300               | 1.5M        | 900K         | ~$0.38     |
| **Prod (High)**  | 5,000 | 5                 | 600              | 400               | 15M         | 10M          | ~$4.13     |

### Monthly Projections

| Scenario         | Monthly Input Cost | Monthly Output Cost | **Total/Month** |
|------------------|--------------------|---------------------|-----------------|
| **Staging**      | $0.28              | $0.68               | **~$1**         |
| **Prod (Low)**   | $3.38              | $8.10               | **~$12**        |
| **Prod (High)**  | $33.75             | $90.00              | **~$124**       |

> **Note**: These projections assume all queries reach the Gemini API. In practice,
> the governance pipeline routes many queries to local Ollama models, reducing
> cloud API usage by an estimated 40–60%.

### Budget Safeguards

1. **Billing alerts**: Set at 50%, 80%, and 100% of monthly budget cap.
2. **API key rotation**: Quarterly rotation via GCP Secret Manager.
3. **Rate limiting**: Governance pipeline enforces 60 req/hour per client; Spring Boot
   circuit breaker prevents runaway retry storms.

---

## 5. Scaling Strategy

### Current: On-Prem Process Management

On-prem scaling is managed via Docker Compose replica counts or systemd service
instancing behind an Nginx load balancer.

```yaml
# docker-compose.prod.yml (excerpt)
services:
  backend:
    image: swish-backend:latest
    deploy:
      replicas: 2
    ports:
      - "8080-8081:8080"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  governance:
    image: swish-governance:latest
    deploy:
      replicas: 2
    ports:
      - "8000-8001:8000"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/health"]
      interval: 15s
      timeout: 5s
      retries: 3
```

### Future: GKE HPA Definitions (for migration)

#### Spring Boot Backend

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: swish-backend-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: swish-backend
  minReplicas: 3
  maxReplicas: 6
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 60
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 75
```

#### Python Governance Service

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: swish-governance-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: swish-governance
  minReplicas: 2
  maxReplicas: 4
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

---

## 6. Networking & Security

### On-Prem (Current)

| Concern              | Solution                                                                 |
|----------------------|--------------------------------------------------------------------------|
| **PII boundary**     | Python governance runs on localhost / private LAN — no external egress except to Ollama |
| **TLS termination**  | Nginx + Certbot (Let's Encrypt) on the edge server                       |
| **Secrets**          | HashiCorp Vault (or `.env` files with strict `chmod 600` for dev/staging)|
| **Inter-service**    | Backend → Governance via `localhost` or private DNS (no public exposure)  |
| **Firewall**         | `iptables` / `ufw` — only ports 80, 443, 22 exposed externally          |

### GKE (Future)

| Concern              | Solution                                                                 |
|----------------------|--------------------------------------------------------------------------|
| **PII boundary**     | Python governance in internal-only VPC subnet — no external egress       |
| **TLS termination**  | Google-managed certificate on Cloud Load Balancer                        |
| **Secrets**          | GCP Secret Manager for API keys, DB creds, JWT signing key               |
| **Inter-service**    | ClusterIP services with internal DNS                                     |
| **WAF**              | Cloud Armor with OWASP Top 10 + rate-limiting policies                   |

---

## 7. Monitoring & Observability

| Signal       | Tool                  | Config                                               |
|--------------|-----------------------|------------------------------------------------------|
| Metrics      | Prometheus + Grafana  | Spring Actuator `/actuator/prometheus`                |
| Traces       | Zipkin / Cloud Trace  | 10% sampling rate (staging: 100%)                    |
| Logs         | Cloud Logging         | Structured JSON via Logback + Python `logging`       |
| Alerts       | Cloud Monitoring      | P99 latency > 5s, error rate > 2%, fallback rate > 30% |
| Uptime       | Cloud Monitoring      | Uptime check on `/actuator/health` + `/health`       |

---

## 8. Procurement Checklist

### Phase 1: On-Prem (Immediate)

- [ ] Provision 2–3 bare-metal / VM servers (see §3.3 for specs)
- [ ] Install Docker + Docker Compose on all hosts
- [ ] Install and configure Nginx as reverse proxy + TLS terminator
- [ ] Deploy PostgreSQL 15 with streaming replication
- [ ] Deploy Redis 7 with AOF persistence
- [ ] Install Ollama + download Gemma 4B / Qwen 2.5 models
- [ ] Generate Gemini API key and store in Vault / `.env`
- [ ] Configure Gemini billing alerts at $50, $100, $200 thresholds
- [ ] Set up Prometheus + Grafana stack on monitoring host
- [ ] Configure `iptables` / `ufw` firewall rules
- [ ] Establish CI/CD pipeline (GitHub Actions → SSH deploy)
- [ ] Run JMeter load tests to establish performance baseline

### Phase 2: GKE Migration (Future)

- [ ] Provision GKE Autopilot cluster
- [ ] Migrate PostgreSQL to Cloud SQL
- [ ] Migrate Redis to Memorystore
- [ ] Containerize all services with multi-stage Dockerfiles
- [ ] Deploy HPA manifests (§5)
- [ ] Set up Cloud Armor WAF rules
- [ ] Migrate secrets to GCP Secret Manager
- [ ] Configure Cloud Monitoring alerts + uptime checks

---

## 9. Risk Register

| Risk                                    | Likelihood | Impact | Mitigation                                         |
|-----------------------------------------|------------|--------|------------------------------------------------------|
| Gemini API outage                       | Low        | High   | ResilientLlmGateway falls back to local Ollama/mock  |
| PII data leak to cloud                  | Low        | Critical | Dual PII gate (Python + Java PiiPreScanner)        |
| Ollama GPU node failure                 | Medium     | Medium | Zone-redundant deployment, mock fallback             |
| Rate limit exhaustion under DDoS        | Medium     | Medium | Cloud Armor WAF + governance rate limiter            |
| Cost overrun from LLM API               | Low        | Medium | Billing alerts, API key quotas, local-first routing  |
