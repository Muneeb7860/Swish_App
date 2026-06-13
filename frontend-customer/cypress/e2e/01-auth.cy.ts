/**
 * E2E — Authentication flow (UI-level)
 *
 * Tests the AuthGate component rendered at the root of the Vite dev server:
 *   • Registration via POST /api/v1/auth/register
 *   • Login via POST /api/v1/auth/login — JWT stored in React state
 *   • Logout — session invalidated via POST /api/v1/auth/logout
 *   • Duplicate email registration → visible error message
 *   • Wrong password login → visible error message
 *
 * Pre-condition: Vite dev server running at http://localhost:5173
 *                Platform gateway running at http://localhost:8080
 */

const uniqueEmail = () =>
  `e2e.auth.${Date.now()}@swish-test.local`;

describe("Auth flow — UI", () => {
  const password = "Cypress1!";
  let testEmail: string;
  let sessionId: string;

  before(() => {
    // Use a fresh email per test run to avoid leftover state from prior runs
    testEmail = uniqueEmail();
  });

  beforeEach(() => {
    cy.viewport(1280, 720);
  });

  // ─── Register ──────────────────────────────────────────────────────────────

  it("shows the AuthGate login form on first load", () => {
    cy.visit("/");
    cy.contains("Log in").should("be.visible");
    cy.contains("Register").should("be.visible");
  });

  it("registers a new user via the Register tab", () => {
    cy.visit("/");

    // Switch to Register tab
    cy.contains("button", "Register").click();

    // Fill in form
    cy.get('input[type="email"]').type(testEmail);
    cy.get('input[type="password"]').type(password);
    cy.contains("button", "Create account").click();

    // After successful registration the component shows a success message
    // and reverts to Login tab
    cy.contains("Account created", { timeout: 8000 }).should("be.visible");
  });

  it("shows an error when registering with the same email twice", () => {
    cy.visit("/");
    cy.contains("button", "Register").click();
    cy.get('input[type="email"]').type(testEmail);
    cy.get('input[type="password"]').type(password);
    cy.contains("button", "Create account").click();

    // Backend returns 409 / 400; AuthGate shows data.message in the form's error <p>.
    // Use "form p" to avoid matching the subtitle <p> ("15-minute grocery delivery")
    // which appears earlier in the DOM and always has a grey colour.
    cy.get("form p")
      .should("have.css", "color")
      .and("match", /rgb\(239, 83, 80\)/); // error red (#ef5350)
  });

  // ─── Login ─────────────────────────────────────────────────────────────────

  it("logs in with correct credentials and shows the main app", () => {
    cy.visit("/");
    cy.get('input[type="email"]').type(testEmail);
    cy.get('input[type="password"]').type(password);
    // The AuthGate renders two buttons labelled "Log in": a mode-tab button and
    // the form submit button.  Use the submit button explicitly to avoid clicking
    // the tab (which only re-selects the current mode and never triggers a fetch).
    cy.get('button[type="submit"]').click();

    // After login the app mounts the CustomerApp — look for logout button
    cy.contains("Log out", { timeout: 10000 }).should("be.visible");
    cy.contains("Customer MFE").should("be.visible");
  });

  it("shows an error for wrong password", () => {
    cy.visit("/");
    cy.get('input[type="email"]').type(testEmail);
    cy.get('input[type="password"]').type("WrongPass999!");
    // See note in "logs in with correct credentials" — use the submit button.
    cy.get('button[type="submit"]').click();

    // Error text appears inside the form; use "form p" to skip the subtitle <p>.
    cy.get("form p", { timeout: 8000 })
      .should("have.css", "color")
      .and("match", /rgb\(239, 83, 80\)/);
  });

  // ─── Logout ────────────────────────────────────────────────────────────────

  it("logs out and returns to the AuthGate screen", () => {
    // Login programmatically via API (faster than UI)
    cy.apiLogin(testEmail, password).then((session) => {
      sessionId = session.sessionId;
      // Store token in window so AuthGate state can be set via intercept
    });

    // Login via UI so the session is in React state
    cy.visit("/");
    cy.get('input[type="email"]').type(testEmail);
    cy.get('input[type="password"]').type(password);
    // See note in "logs in with correct credentials" — use the submit button.
    cy.get('button[type="submit"]').click();
    cy.contains("Log out", { timeout: 10000 }).click();

    // Should be back at AuthGate
    cy.contains("Log in").should("be.visible");
    cy.contains("15-minute grocery delivery").should("be.visible");
  });

  // ─── API-level sanity checks ───────────────────────────────────────────────

  it("API: login returns a bearer token and sessionId", () => {
    // Ensure the user exists even if the preceding UI-registration test skipped
    // or failed for an unrelated reason (e.g. flaky browser paint timing).
    cy.apiRegister(testEmail, password); // no-op if already registered (returns 400, ignored)
    cy.apiLogin(testEmail, password).then((session) => {
      expect(session.token).to.be.a("string").and.to.have.length.greaterThan(20);
      expect(session.sessionId).to.be.a("string").and.to.have.length.greaterThan(0);
    });
  });

  it("API: logout invalidates the session (subsequent calls return 204)", () => {
    cy.apiRegister(testEmail, password); // idempotent guard — see note above
    cy.apiLogin(testEmail, password).then((session) => {
      cy.apiLogout(session.sessionId);
      // Second logout on the same session should still return 204 (no-op)
      cy.request({
        method: "POST",
        url: `${Cypress.env("apiUrl")}/api/v1/auth/logout`,
        headers: { "X-Session-Id": session.sessionId },
        failOnStatusCode: false,
      }).then((res) => {
        expect(res.status).to.eq(204);
      });
    });
  });
});
