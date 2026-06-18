import federation from "@originjs/vite-plugin-federation";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
	plugins: [
		react(),
		tailwindcss(),
		federation({
			name: "remoteB2B",
			filename: "remoteEntry.js",
			exposes: {
				"./B2bDashboard": "./src/B2bDashboard.tsx",
			},
			shared: ["react", "react-dom"],
		}),
	],
	server: {
		port: 5002,
		host: "127.0.0.1",
		cors: true,
	},
	preview: {
		port: 5002,
		host: "127.0.0.1",
		cors: true,
	},
	build: {
		target: "esnext",
		minify: false,
		cssCodeSplit: false,
	},
});
