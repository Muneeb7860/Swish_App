#!/usr/bin/env bash
# =============================================================================
# scripts/setup-gcp.sh
#
# One-shot GCP bootstrap for the Swish backend Cloud Run deployment.
#
# What this script does (idempotent — safe to re-run):
#   1.  Enable required GCP APIs
#   2.  Create an Artifact Registry repository for Docker images
#   3.  Create the Cloud Run service account (swish-backend-sa)
#   4.  Grant IAM bindings (Cloud Run invoker, Secret Manager accessor, logging)
#   5.  Create the JWT_SECRET in Secret Manager (from the local env var or prompts)
#   6.  Create the Workload Identity Pool + Provider for GitHub Actions OIDC
#   7.  Bind the GitHub Actions SA to impersonate the backend SA
#   8.  Print the required GitHub Actions secrets
#
# Prerequisites:
#   - gcloud CLI authenticated with a project owner / Editor + Security Admin
#   - GCP project already created
#   - Billing enabled on the project
#
# Usage:
#   export GCP_PROJECT_ID=my-project-id
#   export GITHUB_ORG=Muneeb7860
#   export GITHUB_REPO=Swish_App-1
#   export JWT_SECRET="<your-strong-secret-min-32-chars>"   # optional — will prompt if absent
#   bash scripts/setup-gcp.sh
# =============================================================================

set -euo pipefail

# ── helpers ──────────────────────────────────────────────────────────────────
info()  { echo "  ✅  $*"; }
warn()  { echo "  ⚠️   $*"; }
step()  { echo; echo "──────────────────────────────────────────────────────"; echo "▶  $*"; }
die()   { echo "  ❌  ERROR: $*" >&2; exit 1; }

# ── validate required vars ───────────────────────────────────────────────────
: "${GCP_PROJECT_ID:?Set GCP_PROJECT_ID}"
: "${GITHUB_ORG:?Set GITHUB_ORG (e.g. Muneeb7860)}"
: "${GITHUB_REPO:?Set GITHUB_REPO (e.g. Swish_App-1)}"

REGION="${GCP_REGION:-europe-west6}"          # Zurich — change if needed
AR_REPO="swish-backend"
SA_NAME="swish-backend-sa"
SA_EMAIL="${SA_NAME}@${GCP_PROJECT_ID}.iam.gserviceaccount.com"
WI_POOL="github-actions-pool"
WI_PROVIDER="github-actions-provider"
SECRET_NAME="JWT_SECRET"

# ── 1. Enable APIs ────────────────────────────────────────────────────────────
step "Enabling GCP APIs"
gcloud services enable \
    artifactregistry.googleapis.com \
    run.googleapis.com \
    secretmanager.googleapis.com \
    cloudbuild.googleapis.com \
    iam.googleapis.com \
    iamcredentials.googleapis.com \
    --project="${GCP_PROJECT_ID}"
info "APIs enabled"

# ── 2. Artifact Registry ──────────────────────────────────────────────────────
step "Creating Artifact Registry repository: ${AR_REPO}"
if gcloud artifacts repositories describe "${AR_REPO}" \
        --location="${REGION}" --project="${GCP_PROJECT_ID}" &>/dev/null; then
    warn "Repository ${AR_REPO} already exists — skipping create"
else
    gcloud artifacts repositories create "${AR_REPO}" \
        --repository-format=docker \
        --location="${REGION}" \
        --description="Swish backend Docker images" \
        --project="${GCP_PROJECT_ID}"
    info "Created ${REGION}-docker.pkg.dev/${GCP_PROJECT_ID}/${AR_REPO}"
fi

# ── 3. Service Account ────────────────────────────────────────────────────────
step "Creating service account: ${SA_NAME}"
if gcloud iam service-accounts describe "${SA_EMAIL}" \
        --project="${GCP_PROJECT_ID}" &>/dev/null; then
    warn "Service account ${SA_EMAIL} already exists — skipping create"
else
    gcloud iam service-accounts create "${SA_NAME}" \
        --display-name="Swish Backend Cloud Run SA" \
        --project="${GCP_PROJECT_ID}"
    info "Created ${SA_EMAIL}"
fi

# ── 4. IAM Bindings ───────────────────────────────────────────────────────────
step "Granting IAM roles to ${SA_EMAIL}"

grant_role() {
    local role="$1"
    gcloud projects add-iam-policy-binding "${GCP_PROJECT_ID}" \
        --member="serviceAccount:${SA_EMAIL}" \
        --role="${role}" \
        --condition=None \
        --quiet
    info "Granted ${role}"
}

grant_role "roles/run.invoker"
grant_role "roles/secretmanager.secretAccessor"
grant_role "roles/logging.logWriter"
grant_role "roles/monitoring.metricWriter"
grant_role "roles/artifactregistry.reader"

# ── 5. JWT_SECRET in Secret Manager ──────────────────────────────────────────
step "Creating Secret Manager secret: ${SECRET_NAME}"
if gcloud secrets describe "${SECRET_NAME}" --project="${GCP_PROJECT_ID}" &>/dev/null; then
    warn "Secret ${SECRET_NAME} already exists — skipping create"
else
    if [ -z "${JWT_SECRET:-}" ]; then
        read -rsp "Enter JWT_SECRET value (min 32 chars): " JWT_SECRET
        echo
    fi
    [ "${#JWT_SECRET}" -ge 32 ] || die "JWT_SECRET must be at least 32 characters"

    printf '%s' "${JWT_SECRET}" | gcloud secrets create "${SECRET_NAME}" \
        --data-file=- \
        --replication-policy=automatic \
        --project="${GCP_PROJECT_ID}"
    info "Created secret ${SECRET_NAME}"
fi

# ── 6. Workload Identity Pool ─────────────────────────────────────────────────
step "Creating Workload Identity Pool: ${WI_POOL}"
if gcloud iam workload-identity-pools describe "${WI_POOL}" \
        --location=global --project="${GCP_PROJECT_ID}" &>/dev/null; then
    warn "Pool ${WI_POOL} already exists — skipping create"
else
    gcloud iam workload-identity-pools create "${WI_POOL}" \
        --location=global \
        --display-name="GitHub Actions pool" \
        --project="${GCP_PROJECT_ID}"
    info "Created pool ${WI_POOL}"
fi

WI_POOL_ID=$(gcloud iam workload-identity-pools describe "${WI_POOL}" \
    --location=global \
    --project="${GCP_PROJECT_ID}" \
    --format="value(name)")

step "Creating Workload Identity Provider: ${WI_PROVIDER}"
if gcloud iam workload-identity-pools providers describe "${WI_PROVIDER}" \
        --location=global \
        --workload-identity-pool="${WI_POOL}" \
        --project="${GCP_PROJECT_ID}" &>/dev/null; then
    warn "Provider ${WI_PROVIDER} already exists — skipping create"
else
    gcloud iam workload-identity-pools providers create-oidc "${WI_PROVIDER}" \
        --location=global \
        --workload-identity-pool="${WI_POOL}" \
        --display-name="GitHub OIDC provider" \
        --issuer-uri="https://token.actions.githubusercontent.com" \
        --attribute-mapping="google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository" \
        --attribute-condition="assertion.repository=='${GITHUB_ORG}/${GITHUB_REPO}'" \
        --project="${GCP_PROJECT_ID}"
    info "Created OIDC provider ${WI_PROVIDER}"
fi

# ── 7. WI binding — allow GitHub Actions to impersonate the backend SA ────────
step "Binding GitHub Actions principal to ${SA_EMAIL}"
MEMBER="principalSet://iam.googleapis.com/${WI_POOL_ID}/attribute.repository/${GITHUB_ORG}/${GITHUB_REPO}"
gcloud iam service-accounts add-iam-policy-binding "${SA_EMAIL}" \
    --role="roles/iam.workloadIdentityUser" \
    --member="${MEMBER}" \
    --project="${GCP_PROJECT_ID}" \
    --quiet
info "Workload identity binding created"

# ── 8. Print GitHub Actions secrets ──────────────────────────────────────────
WI_PROVIDER_FULL=$(gcloud iam workload-identity-pools providers describe "${WI_PROVIDER}" \
    --location=global \
    --workload-identity-pool="${WI_POOL}" \
    --project="${GCP_PROJECT_ID}" \
    --format="value(name)")

step "Done! Add the following secrets to your GitHub repository:"
echo
echo "  GCP_PROJECT_ID                = ${GCP_PROJECT_ID}"
echo "  GCP_SERVICE_ACCOUNT           = ${SA_EMAIL}"
echo "  GCP_WORKLOAD_IDENTITY_PROVIDER= ${WI_PROVIDER_FULL}"
echo
echo "  Artifact Registry image base  = ${REGION}-docker.pkg.dev/${GCP_PROJECT_ID}/${AR_REPO}/swish-backend"
echo
echo "See .github/workflows/deploy-cloudrun.yml for how these are consumed."
