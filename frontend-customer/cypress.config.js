import { defineConfig } from "cypress";

// NOTE: This config is plain ESM JavaScript (the package is "type": "module").
// It is intentionally NOT a .ts file: Cypress loads the config via Node
// `require`, which — under "type": "module" — does not transpile TypeScript and
// fails on type annotations ("Unexpected token ':'"). Spec/support files may
// still be .ts; those are transpiled by Cypress's own bundler at runtime.
export default defineConfig({
  // ── E2E tests ───────────────────────────────────────────────────────────────
  // Base URL: the customer Vite dev server (npm run dev → port 5173).
  // For CI, start the stack and point CYPRESS_BASE_URL at the served app.
  e2e: {
    baseUrl: "http://localhost:5173",

    // Platform gateway / backend — all API calls go here.
    // Override via CYPRESS_API_URL env var in CI.
    env: {
      // Backend port: 8083 is the spring-boot default (server.port=${PORT:8083}).
      // Override via CYPRESS_API_URL if the backend runs elsewhere (e.g. behind
      // the platform-gateway on 8080 in full docker-compose mode).
      apiUrl: "http://localhost:8083",
      // Admin JWT — supplied in CI via CYPRESS_ADMIN_TOKEN after logging in as
      // the seeded admin (see DevAdminInitializer + the CI "Fetch admin JWT" step).
      adminToken: "",
    },

    specPattern: "cypress/e2e/**/*.cy.{ts,js}",
    supportFile: "cypress/support/e2e.ts",
    screenshotsFolder: "cypress/screenshots",
    videosFolder: "cypress/videos",
    video: false,
    screenshotOnRunFailure: true,

    // Give the backend enough time to respond on first cold-start.
    defaultCommandTimeout: 10000,
    responseTimeout: 30000,
    requestTimeout: 15000,

    setupNodeEvents(on) {
      on("task", {
        log(message) {
          console.log(message);
          return null;
        },
      });
    },
  },
});
