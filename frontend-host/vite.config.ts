import federation from "@originjs/vite-plugin-federation";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
	define: {
		"import.meta.env.VITE_MOCK_MODE": JSON.stringify(
			process.env.VITE_MOCK_MODE || "false",
		),
	},
	plugins: [
		react(),
		tailwindcss(),
		federation({
			name: "host",
			remotes: {
				customer:
					process.env.VITE_REMOTE_CUSTOMER ||
					"http://127.0.0.1:3001/assets/remoteEntry.js",
				rider:
					process.env.VITE_REMOTE_RIDER ||
					"http://127.0.0.1:3002/assets/remoteEntry.js",
				admin:
					process.env.VITE_REMOTE_ADMIN ||
					"http://127.0.0.1:3003/assets/remoteEntry.js",
				b2b:
					process.env.VITE_REMOTE_B2B ||
					"http://127.0.0.1:5002/assets/remoteEntry.js",
			},
			exposes: {
				"./api": "./src/api/endpoints.ts",
				"./apiTypes": "./src/api/types.ts",
				"./websocket": "./src/api/websocket.ts",
				"./LoadingSkeleton": "./src/components/LoadingSkeleton.tsx",
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
		host: "127.0.0.1",
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
