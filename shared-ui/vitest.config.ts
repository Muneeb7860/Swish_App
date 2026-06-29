import react from "@vitejs/plugin-react";
import { resolve } from "path";
import { defineConfig } from "vitest/config";

export default defineConfig({
	plugins: [react()],
	resolve: {
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
