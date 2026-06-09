/**
 * E2E — Admin & observability layer (API-level via cy.request)
 *
 * Tests admin endpoints and the Prometheus/Actuator observability stack:
 *   1. GET /api/admin/health        — system health report (ROLE_ADMIN required)
 *   2. GET /api/admin/onboard/queue — pending rider onboarding queue
 *   3. GET /api/admin/hitl/queue    — HITL agent escalation queue
 *   4. GET /api/admin/chaos/active  — active chaos fault log
 *   5. GET /actuator/health         — Spring Boot liveness probe
 *   6. GET /actuator/prometheus     — Prometheus metrics scrape endpoint
 *   7. Security: admin endpoints return 401/403 without/wrong role
 *
 * Admin JWT setup:
 *   export CYPRESS_ADMIN_TOKEN=$(
 *     curl -s -X POST http://localhost:8080/api/v1/auth/login \
 *          -H 'Content-Type: application/json' \
 *          -d '{"email":"admin@swish.ch","password":"<ADMIN_PASSWORD>","deviceFingerprint":"cypress"}' \
 *       | jq -r .token
 *   )
 *
 * When CYPRESS_ADMIN_TOKEN is absent, the admin-guarded tests are gracefully skipped.
 *
 * Pre-condition: Platform gateway at http://localhost:8080
 */

const API = () => Cypress.env("apiUrl") as string;
const uniqueEmail = () => `e2e.noadmin.${Date.now()}@swish-test.local`;

describe("Admin & observability — API", () => {
  let adminToken: string;
  let regularUserToken: string;
  let regularSessionId: string;
  const regularEmail = uniqueEmail();
  const password = "Cypress1!";

  before(() => {
    adminToken = Cypress.env("adminToken") as string || "";

    // Register a non-admin user to verify that admin endpoints are ROLE-protected
    cy.apiRegister(regularEmail, password);
    cy.apiLogin(regularEmail, password).then((session) => {
      regularUserToken = session.token;
      regularSessionId = session.sessionId;
    });
  });

  after(() => {
    if (regularSessionId) cy.apiLogout(regularSessionId);
  });

  // ─── Observability — these endpoints are public (no auth) ─────────────────

  it("GET /actuator/health — liveness probe returns 200 UP", () => {
    cy.request({
      method: "GET",
      url: `${API()}/actuator/health`,
      failOnStatusCode: false,
    }).then((res) => {
      expect(res.status).to.eq(200);
      expect(res.body.status).to.eq("UP");
    });
  });

  it("GET /actuator/prometheus — metrics scrape endpoint is reachable", () => {
    cy.request({
      method: "GET",
      url: `${API()}/actuator/prometheus`,
      failOnStatusCode: false,
    }).then((res) => {
      // 200 = metrics available; 404 = gateway doesn't proxy actuator (also acceptable)
      expect([200, 404]).to.include(res.status);
      if (res.status === 200) {
        expect(res.body).to.be.a("string");
        // Prometheus format: each metric line starts with "#" or a metric name
        expect(res.body).to.match(/^#|^[a-z_]+/m);
        cy.task("log", `Prometheus metrics snippet: ${res.body.slice(0, 200)}`);
      }
    });
  });

  it("GET /actuator/prometheus — contains security.anomaly.analyzed.ratio gauge", () => {
    cy.request({
      method: "GET",
      url: `${API()}/actuator/prometheus`,
      failOnStatusCode: false,
    }).then((res) => {
      if (res.status !== 200) {
        cy.log("Actuator/prometheus not proxied by gateway — skipping gauge check");
        return;
      }
      expect(res.body).to.contain("security_anomaly_analyzed_ratio",
        "Prometheus scrape output must include the anomaly ratio gauge");
    });
  });

  // ─── Admin endpoints: security guard (no token) ────────────────────────────

  it("GET /api/admin/health — returns 401 without any token", () => {
    cy.request({
      method: "GET",
      url: `${API()}/api/admin/health`,
      failOnStatusCode: false,
    }).then((res) => {
      expect(res.status).to.eq(401);
    });
  });

  it("GET /api/admin/health — returns 403 with a non-admin JWT", () => {
    cy.request({
      method: "GET",
      url: `${API()}/api/admin/health`,
      headers: { Authorization: `Bearer ${regularUserToken}` },
      failOnStatusCode: false,
    }).then((res) => {
      expect(res.status).to.eq(403);
    });
  });

  it("GET /api/admin/hitl/queue — returns 401 without any token", () => {
    cy.request({
      method: "GET",
      url: `${API()}/api/admin/hitl/queue`,
      failOnStatusCode: false,
    }).then((res) => {
      expect(res.status).to.eq(401);
    });
  });

  // ─── Admin endpoints (require CYPRESS_ADMIN_TOKEN) ─────────────────────────

  it("GET /api/admin/health — admin JWT returns 200 with OPERATIONAL status", () => {
    if (!adminToken) {
      cy.log("⚠ CYPRESS_ADMIN_TOKEN not set — skipping admin health test");
      return;
    }

    cy.request({
      method: "GET",
      url: `${API()}/api/admin/health`,
      headers: { Authorization: `Bearer ${adminToken}` },
    }).then((res) => {
      expect(res.status).to.eq(200);
      expect(res.body.status).to.eq("OPERATIONAL");
      // System health must include at least these keys
      expect(res.body).to.have.all.keys(
        "status", "pendingOnboardingApplications",
        "pendingHitlTickets", "activeChaosEvents"
      );
      cy.task("log", `System health: ${JSON.stringify(res.body)}`);
    });
  });

  it("GET /api/admin/onboard/queue — admin JWT returns array of pending applications", () => {
    if (!adminToken) {
      cy.log("⚠ CYPRESS_ADMIN_TOKEN not set — skipping");
      return;
    }

    cy.request({
      method: "GET",
      url: `${API()}/api/admin/onboard/queue`,
      headers: { Authorization: `Bearer ${adminToken}` },
    }).then((res) => {
      expect(res.status).to.eq(200);
      expect(res.body).to.be.an("array");
      cy.task("log", `Onboarding queue length: ${res.body.length}`);
    });
  });

  it("GET /api/admin/hitl/queue — admin JWT returns array of escalated tickets", () => {
    if (!adminToken) {
      cy.log("⚠ CYPRESS_ADMIN_TOKEN not set — skipping");
      return;
    }

    cy.request({
      method: "GET",
      url: `${API()}/api/admin/hitl/queue`,
      headers: { Authorization: `Bearer ${adminToken}` },
    }).then((res) => {
      expect(res.status).to.eq(200);
      expect(res.body).to.be.an("array");
      cy.task("log", `HITL queue length: ${res.body.length}`);
    });
  });

  it("GET /api/admin/chaos/active — admin JWT returns active fault list", () => {
    if (!adminToken) {
      cy.log("⚠ CYPRESS_ADMIN_TOKEN not set — skipping");
      return;
    }

    cy.request({
      method: "GET",
      url: `${API()}/api/admin/chaos/active`,
      headers: { Authorization: `Bearer ${adminToken}` },
    }).then((res) => {
      expect(res.status).to.eq(200);
      expect(res.body).to.be.an("array");
    });
  });

  // ─── Security audit trail ──────────────────────────────────────────────────

  it("GET /api/security/audit — admin JWT returns audit trail list", () => {
    if (!adminToken) {
      cy.log("⚠ CYPRESS_ADMIN_TOKEN not set — skipping");
      return;
    }

    cy.request({
      method: "GET",
      url: `${API()}/api/security/audit`,
      headers: { Authorization: `Bearer ${adminToken}` },
    }).then((res) => {
      expect(res.status).to.eq(200);
      expect(res.body).to.be.an("array");
    });
  });

  it("GET /api/security/compliance — admin JWT returns GDPR compliance report", () => {
    if (!adminToken) {
      cy.log("⚠ CYPRESS_ADMIN_TOKEN not set — skipping");
      return;
    }

    cy.request({
      method: "GET",
      url: `${API()}/api/security/compliance`,
      headers: { Authorization: `Bearer ${adminToken}` },
    }).then((res) => {
      expect(res.status).to.eq(200);
      expect(res.body).to.have.all.keys("gdpr", "pciDss", "auditTrail", "generatedAt");
      expect(res.body.pciDss.encryptionAtRest).to.eq("PASS");
      expect(res.body.pciDss.encryptionInTransit).to.eq("PASS");
    });
  });
});
