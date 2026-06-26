describe("Swish App Q-Commerce E2E Shopping Flow", () => {
	beforeEach(() => {
		// Intercept auth request to bypass MFA for direct testing.
		// Path must match the real backend route (/api/v1/auth/login) so the
		// intercept fires both when VITE_MOCK_MODE is false AND as a fallback.
		cy.intercept("POST", "/api/v1/auth/login", {
			statusCode: 200,
			body: {
				mfaRequired: false,
				sessionToken: null,
				token: "mock-cypress-jwt-token",
			},
		}).as("loginRequest");

		// Intercept catalog so products load instantly without the 500 ms mock delay.
		cy.intercept("GET", "/api/customer/catalog", {
			statusCode: 200,
			body: [
				{
					item_id: "p1",
					name: "Organic Fresh Milk",
					price: 3.49,
					stock: 12,
					category: "Dairy & Eggs",
					emoji: "🥛",
					perishable: true,
				},
				{
					item_id: "p2",
					name: "Chiquita Bananas (1kg)",
					price: 1.99,
					stock: 18,
					category: "Fruits & Veggies",
					emoji: "🍌",
					perishable: false,
				},
				{
					item_id: "p3",
					name: "Fresh Hass Avocado (Pair)",
					price: 2.99,
					stock: 8,
					category: "Fruits & Veggies",
					emoji: "🥑",
					perishable: false,
				},
				{
					item_id: "p4",
					name: "Coca Cola Zero 6-Pack",
					price: 5.49,
					stock: 15,
					category: "Snacks & Drinks",
					emoji: "🥤",
					perishable: false,
				},
				{
					item_id: "p5",
					name: "Whole Wheat Sourdough",
					price: 4.29,
					stock: 6,
					category: "Bakery",
					emoji: "🍞",
					perishable: false,
				},
				{
					item_id: "p6",
					name: "Double Chocolate Muffins",
					price: 3.89,
					stock: 2,
					category: "Bakery",
					emoji: "🧁",
					perishable: false,
				},
				{
					item_id: "p7",
					name: "Free Range Eggs (Dozen)",
					price: 4.99,
					stock: 10,
					category: "Dairy & Eggs",
					emoji: "🥚",
					perishable: true,
				},
				{
					item_id: "p8",
					name: "Potato Chips (Sea Salt)",
					price: 2.49,
					stock: 25,
					category: "Snacks & Drinks",
					emoji: "🥔",
					perishable: false,
				},
			],
		}).as("catalogRequest");

		// Intercept agent metrics to silence console warnings.
		cy.intercept("GET", "/api/agent/metrics", {
			statusCode: 200,
			body: { dailyCost: 0.15, totalTokens: 12000, requestsCount: 42 },
		}).as("metricsRequest");

		// Intercept B2C checkout and payment endpoints to avoid 401 on mock tokens
		cy.intercept("POST", "/api/customer/orders", {
			statusCode: 200,
			body: {
				order_id: 1001,
				customer_id: "CUST-Dave",
				store_id: "store-central",
				rider_id: "rid-1",
				total_amount: 7.98,
				weather_surcharge: 0.0,
				payment_method: "Wallet",
				status: "pending",
				sla_countdown_sec: 180,
				bags_returned: 0,
				created_at: new Date().toISOString(),
			},
		}).as("placeOrderRequest");

		cy.intercept("POST", "/api/payments", {
			statusCode: 200,
			body: {
				payment_id: 10001,
				order_id: 1001,
				customer_id: "CUST-Dave",
				amount: 7.98,
				currency: "CHF",
				payment_method: "Wallet",
				status: "authorized",
				idempotency_key: "idem-123",
				created_at: new Date().toISOString(),
			},
		}).as("createPaymentRequest");

		cy.intercept("POST", "/api/payments/*/capture", {
			statusCode: 200,
			body: {
				payment_id: 10001,
				order_id: 1001,
				customer_id: "CUST-Dave",
				amount: 7.98,
				currency: "CHF",
				payment_method: "Wallet",
				status: "captured",
				idempotency_key: "idem-123",
				created_at: new Date().toISOString(),
				captured_at: new Date().toISOString(),
			},
		}).as("capturePaymentRequest");

		cy.visit("/");
	});

	it("performs customer authentication, browses catalog, validates vouchers, triggers low-stock substitutions, applies invoice tip/ESG, and completes E2E checkout", () => {
		// ─── 1. SECURE MFA SIGN-IN JOURNEY ───
		cy.get("#mfa-login-portal", { timeout: 10000 }).should("be.visible");

		cy.get("#mfa-select-role").select("customer");
		cy.get("#input-mfa-password").type("adminpassword");
		cy.get("#btn-mfa-request-otp").click();

		// Wait for MFA portal to disappear (mock auth resolves in ~500 ms)
		cy.get("#mfa-login-portal", { timeout: 8000 }).should("not.exist");
		cy.get(".app-header").should("be.visible");
		cy.contains("swiss_App").should("be.visible");

		// ─── 2. WAIT FOR CUSTOMER MFE TO MOUNT ───
		// React.lazy + Module Federation is async — wait for the customer
		// dashboard to appear before interacting with its elements.
		cy.get(".customer-dashboard", { timeout: 12000 }).should("exist");
		cy.get("#tab-customer").should("have.class", "active");

		// ─── 3. STORE CATALOG BROWSING & ESG / TIP INVOICE CALCULATIONS ───
		// Add "Organic Fresh Milk" ($3.49) to the cart
		cy.contains("Organic Fresh Milk", { timeout: 8000 })
			.parents(".product-card")
			.within(() => {
				cy.get(".add-cart-btn").click();
			});

		// Cart drawer should now show the added item
		cy.get(".customer-cart-drawer")
			.contains("Organic Fresh Milk")
			.should("exist");
		cy.get(".customer-cart-drawer").contains("1x $3.49").should("exist");

		// Select $2 Rider Tip
		cy.contains("ADD RIDER TIP")
			.parent()
			.within(() => {
				cy.contains("$2").click();
			});

		// Assert Rider Tip in invoice breakdown
		cy.get(".customer-cart-drawer").contains("Rider Tip:").should("exist");
		cy.get(".customer-cart-drawer").contains("$2.00").should("exist");

		// Click "Return bags for $0.50 cash rebate offset" (hidden checkbox — force required)
		cy.get("#esg-bags").click({ force: true });

		// Assert paper bag rebate applied
		cy.get(".customer-cart-drawer")
			.contains("Paper Bag Rebate:")
			.should("exist");
		cy.get(".customer-cart-drawer").contains("-$0.50").should("exist");

		// Validate total: $3.49 + $2.99 + $2.00 - $0.50 = $7.98
		cy.get(".customer-cart-drawer")
			.contains("Total cost:")
			.parent()
			.within(() => {
				cy.contains("$7.98").should("exist");
			});

		// ─── 4. VOUCHER DISCOUNT VALIDATION IN PROFILE HUB ───
		cy.contains("My Profile Hub").click();
		cy.contains("My Discount Vouchers").click();

		cy.contains("SWISSWELCOME5").should("exist");
		cy.contains("Get $5.00 cash voucher on your first grocery basket!").should(
			"exist",
		);
		cy.contains("FRESH10").should("exist");
		cy.contains("Flat $10.00 discount coupon on organic dairy orders.").should(
			"exist",
		);

		// ─── 5. MOCK INVENTORY DEPLETION STOCKOUT & SUBSTITUTION FLOW ───
		cy.contains("Browse Store Catalog").click();

		// Scroll into view — product cards use CSS entry animations (opacity: 0 initially)
		cy.contains("Double Chocolate Muffins", { timeout: 8000 })
			.scrollIntoView()
			.parents(".product-card")
			.within(() => {
				cy.contains("🔥 Only 2 left!").should("be.visible");
				cy.get(".add-cart-btn").click();
			});

		// Low-stock substitution modal
		cy.get(".cert-modal-overlay", { timeout: 6000 }).should("be.visible");
		cy.contains("Stock Alert: Running Low!").should("exist");
		cy.contains("Whole Wheat Sourdough").should("exist");

		cy.contains("Swap Item").click();

		// Modal closes; Sourdough appears in cart
		cy.get(".cert-modal-overlay").should("not.exist");
		cy.get(".customer-cart-drawer")
			.contains("Whole Wheat Sourdough")
			.should("exist");

		// ─── 6. E2E CHECKOUT PERSISTENCE & SLA LIFECYCLE ───
		cy.get("#btn-checkout-wallet").click();

		// Checkout makes 3 sequential mock API calls (~1.5 s total).
		// Use a generous timeout so assertions wait for async state updates.
		cy.get(".customer-cart-drawer", { timeout: 8000 })
			.contains("Cart is empty")
			.should("exist");

		// SLA countdown bar is rendered by the HOST once activeOrder is set
		cy.contains("ACTIVE SLA COUNTDOWN", { timeout: 8000 }).should("exist");
		cy.contains("Status: PICKING", { timeout: 8000 }).should("exist");
	});
});
