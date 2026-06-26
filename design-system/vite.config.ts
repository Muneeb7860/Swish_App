import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import { resolve } from "path";

export default defineConfig({
	plugins: [react()],
	resolve: {
		alias: {
			"@": resolve(__dirname, "./src"),
		},
	},
	build: {
		lib: {
			entry: resolve(__dirname, "src/index.ts"),
			name: "SwishDesignSystem",
			fileName: (format) => `index.${format === "es" ? "js" : "cjs"}`,
		},
		rollupOptions: {
			external: ["react", "react-dom"],
			output: {
				globals: {
					react: "React",
					"react-dom": "ReactDOM",
				},
			},
		},
	},
});
