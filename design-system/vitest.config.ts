import react from "@vitejs/plugin-react";
import { resolve } from "path";
import { defineConfig } from "vitest/config";

export default defineConfig({
	plugins: [react()],
	resolve: {
		// shared-ui is linked via file: and ships its own react copy; dedupe so the
		// re-exported components and the test renderer share ONE react instance
		// (otherwise hooks fail with "Cannot read properties of null (useState)").
		dedupe: ["react", "react-dom"],
		alias: {
			"@": resolve(__dirname, "./src"),
		},
	},
	test: {
		globals: true,
		environment: "jsdom",
		setupFiles: ["./src/setupTests.ts"],
		coverage: {
			provider: "v8",
			reporter: ["text", "json", "html"],
			exclude: ["node_modules/", "dist/", "**/*.test.ts", "**/*.test.tsx"],
		},
	},
});
