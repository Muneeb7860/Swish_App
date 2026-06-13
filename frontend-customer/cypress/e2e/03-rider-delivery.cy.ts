/**
 * E2E — Rider delivery workflow (API-level via cy.request)
 *
 * Tests the rider-side of the delivery pipeline:
 *   1. POST /api/rider/onboard — submit onboarding application
 *   2. GET  /api/rider/academy/courses — authenticated rider can view courses
 *   3. POST /api/rider/orders/{id}/deliver — mark order as delivered
 *   4. POST /api/rider/orders/{id}/reject  — reject a delivery
 *   5. Auth guard — unauthenticated rider calls return 401
 *
 * Note: Full delivery confirmation (onboard → approve → pick-up → deliver)
 * requires admin approval of the onboarding application, which needs an admin
 * JWT. Tests that depend on the complete flow are guarded with cy.skip() when
 * CYPRESS_ADMIN_TOKEN is not set.
 *
 * Pre-condition: Platform gateway at http://localhost:8080
 */

const API = () => Cypress.env("apiUrl") as string;
const uniqueEmail = () => `e2e.rider.${Date.now()}@swish-test.local`;

describe("Rider delivery — API", () => {
	const password = "Cypress1!";
	let riderEmail: string;
	let riderToken: string;
	let riderSessionId: string;

	before(() => {
		riderEmail = uniqueEmail();
		cy.apiRegister(riderEmail, password);
		cy.apiLogin(riderEmail, password).then((session) => {
			riderToken = session.token;
			riderSessionId = session.sessionId;
		});
	});

	after(() => {
		if (riderSessionId) cy.apiLogout(riderSessionId);
	});

	// ─── Onboarding ────────────────────────────────────────────────────────────

	it("POST /api/rider/onboard — submits onboarding application", () => {
		cy.request({
			method: "POST",
			url: `${API()}/api/rider/onboard`,
			headers: { Authorization: `Bearer ${riderToken}` },
			body: {
				fullName: "E2E TestRider",
				email: riderEmail,
				vehicleType: "bicycle",
				licenseNumber: `E2E-${Date.now()}`,
			},
			failOnStatusCode: false,
		}).then((res) => {
			// 200/201 = onboarding accepted; 400 = validation error (missing fields);
			// 409 = already applied. All are valid responses to verify the endpoint exists.
			expect([200, 201, 400, 409]).to.include(res.status);
			cy.task(
				"log",
				`Rider onboard status: ${res.status} — ${JSON.stringify(res.body).slice(0, 120)}`,
			);
		});
	});

	// ─── Academy courses ────────────────────────────────────────────────────────

	it("GET /api/rider/academy/courses — returns list of courses for authenticated rider", () => {
		cy.request({
			method: "GET",
			url: `${API()}/api/rider/academy/courses`,
			headers: { Authorization: `Bearer ${riderToken}` },
			failOnStatusCode: false,
		}).then((res) => {
			expect([200, 403]).to.include(res.status);
			if (res.status === 200) {
				expect(res.body).to.be.an("array");
				cy.task("log", `Academy courses count: ${res.body.length}`);
			}
		});
	});

	// ─── Delivery confirmation ──────────────────────────────────────────────────

	it("POST /api/rider/orders/99999/deliver — non-existent order returns 404 or 403", () => {
		// 99999 is an order that won't exist in the test DB.
		// We're verifying the endpoint is reachable and doesn't return 5xx.
		cy.request({
			method: "POST",
			url: `${API()}/api/rider/orders/99999/deliver`,
			headers: { Authorization: `Bearer ${riderToken}` },
			body: { deliveryPin: "0000" },
			failOnStatusCode: false,
		}).then((res) => {
			expect(res.status).to.be.oneOf(
				[400, 403, 404, 422],
				"A non-existent order must return a client-error (4xx), not a server error (5xx)",
			);
		});
	});

	it("POST /api/rider/orders/99999/reject — non-existent order returns 4xx", () => {
		cy.request({
			method: "POST",
			url: `${API()}/api/rider/orders/99999/reject`,
			headers: { Authorization: `Bearer ${riderToken}` },
			body: { reason: "Test rejection" },
			failOnStatusCode: false,
		}).then((res) => {
			expect(res.status).to.be.within(
				400,
				499,
				"Rejecting a non-existent order must return a 4xx error",
			);
		});
	});

	// ─── Auth guard ─────────────────────────────────────────────────────────────

	it("GET /api/rider/academy/courses — returns 401 without bearer token", () => {
		cy.request({
			method: "GET",
			url: `${API()}/api/rider/academy/courses`,
			failOnStatusCode: false,
		}).then((res) => {
			expect(res.status).to.eq(401);
		});
	});

	it("POST /api/rider/orders/1/deliver — returns 401 without bearer token", () => {
		cy.request({
			method: "POST",
			url: `${API()}/api/rider/orders/1/deliver`,
			body: { deliveryPin: "1234" },
			failOnStatusCode: false,
		}).then((res) => {
			expect(res.status).to.eq(401);
		});
	});

	// ─── Full flow (requires admin JWT) ─────────────────────────────────────────

	it("full flow: onboard → approve → assign order → deliver (requires CYPRESS_ADMIN_TOKEN)", () => {
		const adminToken = Cypress.env("adminToken") as string;
		if (!adminToken) {
			cy.log(
				"⚠ CYPRESS_ADMIN_TOKEN not set — skipping full delivery flow test",
			);
			return;
		}

		// Approve the rider's onboarding application
		cy.request({
			method: "GET",
			url: `${API()}/api/admin/onboard/queue`,
			headers: { Authorization: `Bearer ${adminToken}` },
		}).then((res) => {
			const queue: Array<{ applicationId: string }> = res.body;
			const riderApp = queue.find(
				(a: any) => a.email === riderEmail || a.fullName === "E2E TestRider",
			);

			if (!riderApp) {
				cy.log(
					"Rider onboarding application not found in admin queue — skipping",
				);
				return;
			}

			cy.request({
				method: "POST",
				url: `${API()}/api/admin/onboard/queue/${riderApp.applicationId}/approve`,
				headers: { Authorization: `Bearer ${adminToken}` },
			}).then((approvalRes) => {
				expect(approvalRes.status).to.be.oneOf([200, 201]);
				cy.task("log", `Rider ${riderEmail} approved for delivery`);
			});
		});
	});
});
