/**
 * E2E — Consent Management Flow
 *
 * Tests the ConsentManager component rendered at the root of the customer app:
 *   • First-visit consent banner is displayed to new users
 *   • "Accept All" stores all-true consent and dismisses the banner
 *   • "Reject All" stores all-false consent and dismisses the banner
 *   • "Customize" opens the preferences dialog from the banner
 *   • Preferences dialog toggling + "Save preferences" persists correctly
 *   • "Revisit Consent" button appears after initial consent is given
 *   • "Revisit Consent" button opens the preferences dialog
 *   • Preferences are pre-loaded with the current saved values when reopened
 *   • Changing preferences via Revisit Consent persists the new values
 *   • Consent survives page reload (localStorage persistence)
 *   • swish:consent-changed CustomEvent is dispatched on every decision
 *
 * Pre-condition: customer Vite dev server running at http://localhost:3001
 *
 * localStorage key: swish.consent.v1
 */

const STORAGE_KEY = "swish.consent.v1";

// ── Helpers ─────────────────────────────────────────────────────────────────

/** Clear consent so each test starts as a first-time visitor. */
function clearConsent() {
	cy.clearLocalStorage(STORAGE_KEY);
}

/** Read and parse the stored consent from localStorage. */
function readStoredConsent() {
	// eslint-disable-next-line @typescript-eslint/no-explicit-any
	return cy.window().then((win: any) => {
		const raw = win.localStorage.getItem(STORAGE_KEY);
		if (!raw) return null;
		return JSON.parse(raw);
	});
}

/** Listen for the swish:consent-changed event and store it on window.__lastConsentEvent. */
function listenForConsentEvent() {
	// eslint-disable-next-line @typescript-eslint/no-explicit-any
	cy.window().then((win: any) => {
		win.__lastConsentEvent = null;
		win.addEventListener("swish:consent-changed", (e: CustomEvent) => {
			win.__lastConsentEvent = e.detail;
		});
	});
}

/** Assert that the swish:consent-changed event was fired with the given values. */
function assertConsentEvent(
	analytics: boolean,
	marketing: boolean,
	personalization: boolean,
) {
	// eslint-disable-next-line @typescript-eslint/no-explicit-any
	cy.window().should((win: any) => {
		const ev = win.__lastConsentEvent;
		expect(ev).to.not.be.null;
		expect(ev.analytics).to.equal(analytics);
		expect(ev.marketing).to.equal(marketing);
		expect(ev.personalization).to.equal(personalization);
		expect(ev.necessary).to.be.true;
	});
}

// ── First Visit ──────────────────────────────────────────────────────────────

describe("Consent Management — First visit", () => {
	beforeEach(() => {
		clearConsent();
		cy.viewport(1280, 720);
		cy.visit("/");
	});

	it("shows the consent banner on first load (no prior consent)", () => {
		cy.get("[role='dialog'][aria-label='Cookie consent']").should("be.visible");
		cy.contains("We value your privacy").should("be.visible");
		cy.contains("button", "Accept all").should("be.visible");
		cy.contains("button", "Reject all").should("be.visible");
		cy.contains("button", "Customize").should("be.visible");
	});

	it("does NOT show the Revisit Consent button before consent is given", () => {
		cy.get(".consent-revisit-btn").should("not.exist");
	});

	it("accepts all — banner dismisses, all consent stored as true", () => {
		listenForConsentEvent();

		cy.contains("button", "Accept all").click();

		cy.get("[role='dialog'][aria-label='Cookie consent']").should("not.exist");
		cy.get(".consent-revisit-btn").should("be.visible");

		readStoredConsent().should((stored) => {
			expect(stored.necessary).to.be.true;
			expect(stored.analytics).to.be.true;
			expect(stored.marketing).to.be.true;
			expect(stored.personalization).to.be.true;
		});

		assertConsentEvent(true, true, true);
	});

	it("rejects all — banner dismisses, all optional consent stored as false", () => {
		listenForConsentEvent();

		cy.contains("button", "Reject all").click();

		cy.get("[role='dialog'][aria-label='Cookie consent']").should("not.exist");
		cy.get(".consent-revisit-btn").should("be.visible");

		readStoredConsent().should((stored) => {
			expect(stored.necessary).to.be.true;
			expect(stored.analytics).to.be.false;
			expect(stored.marketing).to.be.false;
			expect(stored.personalization).to.be.false;
		});

		assertConsentEvent(false, false, false);
	});

	it("customize — opens preferences dialog with all toggles initially off", () => {
		cy.contains("button", "Customize").click();

		cy.contains("Consent Preferences").should("be.visible");

		cy.get("[aria-label='Analytics'][role='switch']").should(
			"have.attr",
			"aria-checked",
			"false",
		);
		cy.get("[aria-label='Marketing'][role='switch']").should(
			"have.attr",
			"aria-checked",
			"false",
		);
		cy.get("[aria-label='Personalization'][role='switch']").should(
			"have.attr",
			"aria-checked",
			"false",
		);

		cy.get("[aria-label='Strictly necessary'][role='switch']")
			.should("have.attr", "aria-checked", "true")
			.should("be.disabled");
	});
});

// ── Preferences Dialog (via Customize) ──────────────────────────────────────

describe("Consent Management — Preferences dialog via Customize", () => {
	beforeEach(() => {
		clearConsent();
		cy.viewport(1280, 720);
		cy.visit("/");
		cy.contains("button", "Customize").click();
		cy.contains("Consent Preferences").should("be.visible");
	});

	it("saves custom preferences correctly", () => {
		listenForConsentEvent();

		cy.get("[aria-label='Analytics'][role='switch']").click();
		cy.get("[aria-label='Analytics'][role='switch']").should(
			"have.attr",
			"aria-checked",
			"true",
		);

		cy.get("[aria-label='Personalization'][role='switch']").click();

		cy.contains("button", "Save preferences").click();

		cy.contains("Consent Preferences").should("not.exist");

		readStoredConsent().should((stored) => {
			expect(stored.analytics).to.be.true;
			expect(stored.marketing).to.be.false;
			expect(stored.personalization).to.be.true;
		});

		assertConsentEvent(true, false, true);
	});

	it("'Accept all' in dialog turns all toggles on and saves", () => {
		listenForConsentEvent();

		cy.contains("button", "Accept all").click();

		cy.get("[aria-label='Analytics'][role='switch']").should(
			"have.attr",
			"aria-checked",
			"true",
		);
		cy.get("[aria-label='Marketing'][role='switch']").should(
			"have.attr",
			"aria-checked",
			"true",
		);

		cy.contains("button", "Save preferences").click();

		readStoredConsent().should((stored) => {
			expect(stored.analytics).to.be.true;
			expect(stored.marketing).to.be.true;
			expect(stored.personalization).to.be.true;
		});

		assertConsentEvent(true, true, true);
	});

	it("'Reject all' in dialog turns all optional toggles off", () => {
		cy.contains("button", "Accept all").click();
		cy.contains("button", "Reject all").click();

		cy.get("[aria-label='Analytics'][role='switch']").should(
			"have.attr",
			"aria-checked",
			"false",
		);

		cy.contains("button", "Save preferences").click();

		readStoredConsent().should((stored) => {
			expect(stored.analytics).to.be.false;
			expect(stored.marketing).to.be.false;
			expect(stored.personalization).to.be.false;
		});
	});

	it("closing the dialog without saving does not persist any consent", () => {
		cy.get("[aria-label='Analytics'][role='switch']").click();

		cy.get('[aria-label="Close"]').click();

		cy.get("[role='dialog'][aria-label='Cookie consent']").should("be.visible");

		readStoredConsent().should((stored) => {
			expect(stored).to.be.null;
		});
	});
});

// ── Revisit Consent Button ───────────────────────────────────────────────────

describe("Consent Management — Revisit Consent button", () => {
	beforeEach(() => {
		clearConsent();
		cy.viewport(1280, 720);
		cy.visit("/");
	});

	it("Revisit Consent button appears after accepting all", () => {
		cy.contains("button", "Accept all").click();

		cy.get(".consent-revisit-btn")
			.should("be.visible")
			.and("contain.text", "Revisit Consent");
	});

	it("Revisit Consent button appears after rejecting all", () => {
		cy.contains("button", "Reject all").click();

		cy.get(".consent-revisit-btn").should("be.visible");
	});

	it("Revisit Consent button opens the preferences dialog", () => {
		cy.contains("button", "Accept all").click();

		cy.get(".consent-revisit-btn").click();

		cy.contains("Consent Preferences").should("be.visible");
	});

	it("preferences dialog pre-loads with current saved values (all accepted)", () => {
		cy.contains("button", "Accept all").click();

		cy.get(".consent-revisit-btn").click();

		cy.get("[aria-label='Analytics'][role='switch']").should(
			"have.attr",
			"aria-checked",
			"true",
		);
		cy.get("[aria-label='Marketing'][role='switch']").should(
			"have.attr",
			"aria-checked",
			"true",
		);
		cy.get("[aria-label='Personalization'][role='switch']").should(
			"have.attr",
			"aria-checked",
			"true",
		);
	});

	it("preferences dialog pre-loads with current saved values (all rejected)", () => {
		cy.contains("button", "Reject all").click();

		cy.get(".consent-revisit-btn").click();

		cy.get("[aria-label='Analytics'][role='switch']").should(
			"have.attr",
			"aria-checked",
			"false",
		);
		cy.get("[aria-label='Marketing'][role='switch']").should(
			"have.attr",
			"aria-checked",
			"false",
		);
	});

	it("updating preferences via Revisit Consent persists new values", () => {
		listenForConsentEvent();

		cy.contains("button", "Reject all").click();

		cy.get(".consent-revisit-btn").click();
		cy.get("[aria-label='Marketing'][role='switch']").click();
		cy.get("[aria-label='Marketing'][role='switch']").should(
			"have.attr",
			"aria-checked",
			"true",
		);
		cy.contains("button", "Save preferences").click();

		readStoredConsent().should((stored) => {
			expect(stored.analytics).to.be.false;
			expect(stored.marketing).to.be.true;
			expect(stored.personalization).to.be.false;
		});

		assertConsentEvent(false, true, false);
	});

	it("shows last-updated timestamp inside preferences when revisiting", () => {
		cy.contains("button", "Accept all").click();
		cy.get(".consent-revisit-btn").click();

		cy.contains("Last updated").should("be.visible");
	});

	it("Revisit Consent button has aria-haspopup='dialog' for accessibility", () => {
		cy.contains("button", "Accept all").click();

		cy.get(".consent-revisit-btn").should(
			"have.attr",
			"aria-haspopup",
			"dialog",
		);
	});
});

// ── Persistence across page reload ──────────────────────────────────────────

describe("Consent Management — Persistence", () => {
	it("consent survives a full page reload", () => {
		clearConsent();
		cy.viewport(1280, 720);
		cy.visit("/");

		cy.contains("button", "Accept all").click();
		cy.get(".consent-revisit-btn").should("be.visible");

		cy.reload();

		cy.get("[role='dialog'][aria-label='Cookie consent']").should("not.exist");
		cy.get(".consent-revisit-btn").should("be.visible");

		readStoredConsent().should((stored) => {
			expect(stored.analytics).to.be.true;
			expect(stored.marketing).to.be.true;
			expect(stored.personalization).to.be.true;
		});
	});

	it("partial consent survives reload and Revisit opens with correct values", () => {
		clearConsent();
		cy.viewport(1280, 720);
		cy.visit("/");

		cy.contains("button", "Customize").click();
		cy.get("[aria-label='Analytics'][role='switch']").click();
		cy.contains("button", "Save preferences").click();

		cy.reload();

		cy.get(".consent-revisit-btn").click();

		cy.get("[aria-label='Analytics'][role='switch']").should(
			"have.attr",
			"aria-checked",
			"true",
		);
		cy.get("[aria-label='Marketing'][role='switch']").should(
			"have.attr",
			"aria-checked",
			"false",
		);
		cy.get("[aria-label='Personalization'][role='switch']").should(
			"have.attr",
			"aria-checked",
			"false",
		);
	});

	it("pre-seeded consent via localStorage skips the banner on reload", () => {
		clearConsent();
		cy.viewport(1280, 720);
		cy.visit("/");

		// eslint-disable-next-line @typescript-eslint/no-explicit-any
		cy.window().then((win: any) => {
			win.localStorage.setItem(
				STORAGE_KEY,
				JSON.stringify({
					necessary: true,
					analytics: true,
					marketing: false,
					personalization: true,
					updatedAt: new Date().toISOString(),
				}),
			);
		});

		cy.reload();

		cy.get("[role='dialog'][aria-label='Cookie consent']").should("not.exist");
		cy.get(".consent-revisit-btn").should("be.visible");
	});
});
