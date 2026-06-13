/**
 * Global Cypress setup — runs once before every spec.
 *
 * Import custom commands so they are available to all specs.
 * Add any global `beforeEach` / `afterEach` hooks here.
 */
import "./commands";

// Silence uncaught exceptions from the React dev build (async fetch errors
// when the backend isn't reachable, module-federation chunk loading, etc.)
// that would otherwise fail tests prematurely.
Cypress.on("uncaught:exception", (_err, _runnable) => {
	// Returning false prevents Cypress from failing the test on unhandled errors
	// coming from the application (not from the test code).
	return false;
});
