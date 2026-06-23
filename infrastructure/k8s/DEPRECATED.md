# ⚠️ DEPRECATED — Kubernetes manifests are not the deployment path

These manifests are **legacy and drifted**. They are **not maintained** and
**must not be used** to deploy Swish OS.

## Why
- No manifests exist for the modern services (`platform-gateway`,
  `core-business-engine`, `notification-engine`, `shared-async-services`).
- The remaining manifests target the retired `bff` and an outdated topology
  (port collisions, stale images). They cannot boot the current stack.

## What to use instead (the supported deploy paths)
| Environment | Path |
| --- | --- |
| **Production / staging** | **Cloud Run** — `.github/workflows/deploy-cloudrun.yml` (Artifact Registry + Workload Identity, `europe-west6`). See `scripts/setup-gcp.sh`. |
| **Demo / closed beta** | **docker-compose** — `docker-compose.demo.yml` + `demo/` (Mac-as-server). |
| **Local dev infra** | `docker-compose-local.yml`. |

## Architecture decision
Cloud Run was chosen over Kubernetes for this stage: scale-to-zero economics,
near-zero ops for a small team, and no service-mesh requirement. Kubernetes
(GKE) is a future option only when always-on workload volume, team size, or
networking needs justify a cluster — at which point these manifests should be
**regenerated from scratch**, not revived.

_Deprecated 2026-06-23._
