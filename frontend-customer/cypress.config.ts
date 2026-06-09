import { defineConfig } from "cypress";

export default defineConfig({
  // ── UI (component) tests ────────────────────────────────────────────────────
  // Base URL: the customer Vite dev server (npm run dev → port 5173).
  // For CI, start the stack with docker-compose -f infrastructure/docker-compose.yml up
  // and point CYPRESS_BASE_URL at http://localhost:80 (nginx).
  e2e: {
    baseUrl: "http://localhost:5173",

    // Platform gateway — all API calls go here.
    // Override via CYPRESS_API_URL env var in CI.
    env: {
      apiUrl: "http://localhost:8080",
      // Admin JWT — generate once with:
      //   curl -s -X POST http://localhost:8080/api/v1/auth/login \
      //        -H 'Content-Type: application/json' \
      //        -d '{"email":"admin@swish.ch","password":"<admin-pwd>"}' | jq -r .token
      // Then export: CYPRESS_ADMIN_TOKEN=<token>
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

    setupNodeEvents(on, _config) {
      on("task", {
        log(message: string) {
          console.log(message);
          return null;
        },
      });
    },
  },
});
