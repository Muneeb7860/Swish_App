/**
 * Cypress custom commands.
 *
 * These helpers talk directly to the platform-gateway API via cy.request()
 * so tests can set up state (register, login) without going through the UI.
 *
 * Usage:
 *   cy.apiRegister("e2e+test@swish.ch", "TestPass1!")
 *   cy.apiLogin("e2e+test@swish.ch", "TestPass1!").then(session => { ... })
 *   cy.apiLogout(sessionId)
 */

const API = () => Cypress.env("apiUrl") as string;

// ─── Types returned by commands ───────────────────────────────────────────────

export interface CySession {
  token: string;
  sessionId: string;
}

// ─── Register a new user via the API (skips UI) ───────────────────────────────

Cypress.Commands.add("apiRegister", (email: string, password: string) => {
  cy.request({
    method: "POST",
    url: `${API()}/api/v1/auth/register`,
    body: { email, password },
    failOnStatusCode: false,
  });
});

// ─── Login via the API and return { token, sessionId } ───────────────────────

Cypress.Commands.add("apiLogin", (email: string, password: string) => {
  return cy
    .request<CySession>({
      method: "POST",
      url: `${API()}/api/v1/auth/login`,
      body: { email, password, deviceFingerprint: "cypress-e2e" },
      failOnStatusCode: true,
    })
    .then((res) => ({
      token: res.body.token,
      sessionId: res.body.sessionId,
    }));
});

// ─── Logout via the API ───────────────────────────────────────────────────────

Cypress.Commands.add("apiLogout", (sessionId: string) => {
  cy.request({
    method: "POST",
    url: `${API()}/api/v1/auth/logout`,
    headers: { "X-Session-Id": sessionId },
    failOnStatusCode: false,
  });
});

// ─── Augment Cypress namespace ────────────────────────────────────────────────

declare global {
  namespace Cypress {
    interface Chainable {
      apiRegister(email: string, password: string): Chainable<void>;
      apiLogin(email: string, password: string): Chainable<CySession>;
      apiLogout(sessionId: string): Chainable<void>;
    }
  }
}
