import federation from "@originjs/vite-plugin-federation";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
	plugins: [
		react(),
		federation({
			name: "admin",
			filename: "remoteEntry.js",
			exposes: {
				"./AdminPanel": "./src/components/AdminPanel.tsx",
				"./BusinessApp": "./src/components/BusinessApp.tsx",
				"./InventoryApp": "./src/components/InventoryApp.tsx",
				"./SystemEngineRoom": "./src/components/SystemEngineRoom.tsx",
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
		port: 3003,
		cors: true,
	},
	preview: {
		port: 3003,
		host: "127.0.0.1",
		cors: true,
	},
});
