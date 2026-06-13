import federation from "@originjs/vite-plugin-federation";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";

export default defineConfig({
	plugins: [
		react(),
		tailwindcss(),
		federation({
			name: "rider",
			filename: "remoteEntry.js",
			exposes: {
				"./RiderApp": "./src/components/RiderApp.tsx",
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
		port: 3002,
		cors: true,
	},
	preview: {
		port: 3002,
		host: "127.0.0.1",
		cors: true,
	},
});
