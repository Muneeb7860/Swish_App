import federation from "@originjs/vite-plugin-federation";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";

export default defineConfig({
	plugins: [
		react(),
		tailwindcss(),
		federation({
			name: "host",
			remotes: {
				customer: "http://localhost:3001/assets/remoteEntry.js",
				rider: "http://localhost:3002/assets/remoteEntry.js",
				admin: "http://localhost:3003/assets/remoteEntry.js",
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
		port: 3000,
		cors: true,
		proxy: {
			"/api": {
				target: "http://localhost:8083",
				changeOrigin: true,
				secure: false,
			},
		},
	},
});
