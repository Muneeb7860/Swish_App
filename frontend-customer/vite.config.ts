import federation from "@originjs/vite-plugin-federation";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
	plugins: [
		react(),
		federation({
			name: "customer",
			filename: "remoteEntry.js",
			exposes: {
				"./CustomerApp": "./src/components/CustomerApp.tsx",
			},
			shared: ["react", "react-dom"],
		}),
	],
	build: {
		modulePreload: false,
		target: "esnext",
		minify: false,
		cssCodeSplit: false,
	},
	server: {
		port: 3001,
		cors: true,
	},
	preview: {
		port: 3001,
		host: "127.0.0.1",
		cors: true,
	},
});
