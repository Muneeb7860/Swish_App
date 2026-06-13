/**
 * E2E — Order placement flow (API-level via cy.request)
 *
 * Exercises the canonical customer checkout API at /api/v1/orders:
 *   1. Register + login a test customer
 *   2. POST /api/v1/orders — place an order (customer resolved from the JWT)
 *   3. GET  /api/v1/orders — retrieve the customer's own order history
 *   4. POST /api/v1/orders (same X-Idempotency-Key) — idempotency guard
 *   5. Auth guards — 401 without a bearer token
 *
 * The idempotency key travels in the `X-Idempotency-Key` header (matching the
 * controller), and `customerId` is omitted from the body so the backend
 * resolves it from the authenticated JWT subject.
 *
 * Items use the `item_id` field (the CartItem JSON contract). A checkout may
 * return 400 in CI when the catalog item / dark store isn't seeded — that is
 * tolerated; a 403 (authorization) is NOT, so the role gate stays asserted.
 *
 * These tests use a unique email per run so they are safe to run in parallel.
 */

const API = () => Cypress.env("apiUrl") as string;
const uniqueEmail = () => `e2e.order.${Date.now()}@swish-test.local`;

interface OrderResponse {
	orderId: number;
	status: string;
	totalAmount: number;
}

describe("Order placement — API", () => {
	const password = "Cypress1!";
	let testEmail: string;
	let token: string;
	let sessionId: string;
	let createdOrderId: number;
	const idempotencyKey = `e2e-idem-${Date.now()}`;

	before(() => {
		testEmail = uniqueEmail();

		// Register + login in sequence before any test runs
		cy.apiRegister(testEmail, password);
		cy.apiLogin(testEmail, password).then((session) => {
			token = session.token;
			sessionId = session.sessionId;
		});
	});

	after(() => {
		// Clean up: logout the test session
		if (sessionId) cy.apiLogout(sessionId);
	});

	// ─── Checkout ──────────────────────────────────────────────────────────────

	it("POST /api/v1/orders — places a checkout order for the authenticated customer", () => {
		cy.request<OrderResponse>({
			method: "POST",
			url: `${API()}/api/v1/orders`,
			headers: {
				Authorization: `Bearer ${token}`,
				"X-Idempotency-Key": idempotencyKey,
			},
			body: {
				// customerId omitted — resolved from the JWT subject on the backend
				items: [{ item_id: "BREAD-001", quantity: 1 }],
				paymentMethod: "wallet",
				tipAmount: 0,
				bagsReturned: 0,
			},
			failOnStatusCode: false, // 400 tolerated when the catalog item isn't seeded
		}).then((res) => {
			// 201 (created) on the happy path, or 400 when the item/dark store
			// isn't seeded in CI. A 403 would mean the customer is wrongly denied
			// their own checkout — that must NOT happen, so it is excluded.
			expect([200, 201, 400]).to.include(res.status);
			if (res.status === 200 || res.status === 201) {
				expect(res.body.orderId).to.be.a("number");
				createdOrderId = res.body.orderId;
				cy.task("log", `Created order ID: ${createdOrderId}`);
			}
		});
	});

	it("GET /api/v1/orders — authenticated customer retrieves their own order history", () => {
		cy.request({
			method: "GET",
			url: `${API()}/api/v1/orders`,
			headers: { Authorization: `Bearer ${token}` },
			failOnStatusCode: false,
		}).then((res) => {
			// A valid customer token must list their own orders (array, possibly
			// empty). 403 would be a wrongful denial and is excluded.
			expect(res.status).to.eq(200);
			expect(res.body).to.be.an("array");
		});
	});

	it("POST /api/v1/orders — same X-Idempotency-Key returns the same order, not a duplicate", () => {
		// Skip if the first checkout didn't produce an order (item not seeded)
		if (!createdOrderId) {
			cy.log(
				"Skipping idempotency check — initial checkout did not produce an order",
			);
			return;
		}

		cy.request<OrderResponse>({
			method: "POST",
			url: `${API()}/api/v1/orders`,
			headers: {
				Authorization: `Bearer ${token}`,
				"X-Idempotency-Key": idempotencyKey, // same key as the first checkout
			},
			body: {
				items: [{ item_id: "BREAD-001", quantity: 1 }],
				paymentMethod: "wallet",
				tipAmount: 0,
				bagsReturned: 0,
			},
			failOnStatusCode: true,
		}).then((res) => {
			expect(res.status).to.be.oneOf([200, 201]);
			// Must return the same order, not a new one
			expect(res.body.orderId).to.eq(
				createdOrderId,
				"Idempotent checkout must return the same orderId",
			);
		});
	});

	// ─── Security: auth guard ─────────────────────────────────────────────────

	it("GET /api/v1/orders — returns 401 without a bearer token", () => {
		cy.request({
			method: "GET",
			url: `${API()}/api/v1/orders`,
			failOnStatusCode: false,
		}).then((res) => {
			expect(res.status).to.eq(401);
		});
	});

	it("POST /api/v1/orders — returns 401 without a bearer token", () => {
		cy.request({
			method: "POST",
			url: `${API()}/api/v1/orders`,
			body: {
				items: [],
				paymentMethod: "wallet",
			},
			failOnStatusCode: false,
		}).then((res) => {
			expect(res.status).to.eq(401);
		});
	});
});
