import { defineConfig } from "cypress";

export default defineConfig({
	e2e: {
		setupNodeEvents(on, config) {
			// implement node event listeners here
		},
		baseUrl: "http://127.0.0.1:3000",
		supportFile: false,
		chromeWebSecurity: false,
		defaultCommandTimeout: 10000,
		pageLoadTimeout: 30000,
		responseTimeout: 15000,
	},
});
