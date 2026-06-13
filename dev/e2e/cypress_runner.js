import { spawn } from "child_process";
import fs from "fs";
import http from "http";
import net from "net";
import path from "path";

import { fileURLToPath } from "url";
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const WORKSPACE_DIR = path.resolve(path.join(__dirname, "../.."));
const LOGS_DIR = path.join(WORKSPACE_DIR, "dev/e2e");

const processes = [];

// Helper to check if a port is open
function isPortOpen(port) {
	return new Promise((resolve) => {
		const checkAddress = (host) => {
			const socket = new net.Socket();
			const onError = () => {
				socket.destroy();
				if (host === "127.0.0.1") {
					// If IPv4 failed, fall back to checking IPv6 loopback
					checkAddress("::1");
				} else {
					resolve(false);
				}
			};
			socket.setTimeout(500);
			socket.once("error", onError);
			socket.once("timeout", onError);
			socket.connect(port, host, () => {
				socket.end();
				resolve(true);
			});
		};
		checkAddress("127.0.0.1");
	});
}

// Helper to wait for all required ports
async function waitForPorts(ports, timeoutMs = 60000) {
	const start = Date.now();
	console.log(`[CYPRESS RUNNER] Waiting for ports: ${ports.join(", ")}...`);
	while (Date.now() - start < timeoutMs) {
		const statuses = await Promise.all(ports.map((port) => isPortOpen(port)));
		if (statuses.every((status) => status === true)) {
			console.log("[CYPRESS RUNNER] All services are online!");
			return true;
		}
		await new Promise((resolve) => setTimeout(resolve, 2000));
	}
	return false;
}

// Helper to spawn background processes
function runService(name, command, args, cwd, logFile, envOverrides = {}) {
	console.log(
		`[CYPRESS RUNNER] Starting service [${name}] in directory: ${cwd}...`,
	);
	const logStream = fs.createWriteStream(logFile);

	const proc = spawn(command, args, {
		cwd,
		shell: true,
		env: {
			...process.env,
			...envOverrides
		}
	});
	proc.stdout.pipe(logStream);
	proc.stderr.pipe(logStream);

	proc.on("close", (code) => {
		console.log(`[CYPRESS RUNNER] Service [${name}] exited with code: ${code}`);
	});

	processes.push(proc);
	return proc;
}

async function cleanUp() {
	console.log("[CYPRESS RUNNER] Cleaning up background services...");

	// Terminate all processes
	for (const proc of processes) {
		if (proc && !proc.killed) {
			try {
				if (process.platform === "win32") {
					spawn("taskkill", ["/pid", proc.pid, "/f", "/t"]);
				} else {
					proc.kill("SIGTERM");
				}
			} catch (err) {
				console.error(
					`[CYPRESS RUNNER] Error killing process ${proc.pid}:`,
					err.message,
				);
			}
		}
	}
	console.log("[CYPRESS RUNNER] Services terminated.");
}

async function run() {
	// Ensure log directory exists
	if (!fs.existsSync(LOGS_DIR)) {
		fs.mkdirSync(LOGS_DIR, { recursive: true });
	}

	// Clear previous log files
	[
		"backend.log",
		"bff.log",
		"host.log",
		"customer.log",
		"rider.log",
		"admin.log",
		"core_business_engine.log",
	].forEach((file) => {
		const logPath = path.join(LOGS_DIR, file);
		if (fs.existsSync(logPath)) {
			fs.writeFileSync(logPath, "");
		}
	});

	try {
		const isWin = process.platform === "win32";
		const mvnCmd = isWin ? "mvn.cmd" : "mvn";
		const npmCmd = isWin ? "npm.cmd" : "npm";

		const commonEnv = {
			JWT_SECRET: "my-secret-key-that-is-long-enough-to-be-secure-for-jwt-signature-verification-32bytes-long",
			ADMIN_EMAIL: "admin@swish.local",
			ADMIN_PASSWORD: "swiss-secure-password"
		};

		// 1. Boot Backend Monolith
		runService(
			"Backend",
			mvnCmd,
			["spring-boot:run"],
			path.join(WORKSPACE_DIR, "backend"),
			path.join(LOGS_DIR, "backend.log"),
			commonEnv
		);

		// 2. Boot Platform Gateway (port 8080)
		runService(
			"Platform Gateway",
			mvnCmd,
			["spring-boot:run"],
			path.join(WORKSPACE_DIR, "platform-gateway"),
			path.join(LOGS_DIR, "bff.log"),
			commonEnv
		);

		// 3. Boot frontend App Shell Host
		runService(
			"Host App Shell",
			npmCmd,
			["run", "dev"],
			path.join(WORKSPACE_DIR, "frontend-host"),
			path.join(LOGS_DIR, "host.log"),
			{
				VITE_MOCK_MODE: "true",
			}
		);

		// 4. Boot Customer remote MFE (serve pre-built dist folder with remoteEntry.js)
		runService(
			"Customer Remote",
			npmCmd,
			[
				"run",
				"preview",
				"--",
				"--port",
				"3001",
				"--strictPort",
			],
			path.join(WORKSPACE_DIR, "frontend-customer"),
			path.join(LOGS_DIR, "customer.log"),
		);

		// 5. Boot Rider remote MFE
		runService(
			"Rider Remote",
			npmCmd,
			[
				"run",
				"preview",
				"--",
				"--port",
				"3002",
				"--strictPort",
			],
			path.join(WORKSPACE_DIR, "frontend-rider"),
			path.join(LOGS_DIR, "rider.log"),
		);

		// 6. Boot Admin remote MFE
		runService(
			"Admin Remote",
			npmCmd,
			[
				"run",
				"preview",
				"--",
				"--port",
				"3003",
				"--strictPort",
			],
			path.join(WORKSPACE_DIR, "frontend-admin"),
			path.join(LOGS_DIR, "admin.log"),
		);

		// 7. Wait for all target ports to be open
		const online = await waitForPorts(
			[8080, 8083, 3000, 3001, 3002, 3003],
			120000,
		);
		if (!online) {
			throw new Error(
				"Timeout: Not all ports came online in time. Check the log files in dev/e2e.",
			);
		}

		// HTTP health check — ensure backend API is truly ready (Spring context loaded)
		console.log("[CYPRESS RUNNER] Running HTTP health check on backend...");
		let apiReady = false;
		for (let i = 0; i < 15; i++) {
			try {
				await new Promise((resolve, reject) => {
					const req = http.request(
						{
							hostname: "127.0.0.1",
							port: 8080,
							path: "/api/health",
							method: "GET",
							timeout: 2000,
						},
						(res) => {
							resolve(res.statusCode);
						},
					);
					req.on("error", reject);
					req.on("timeout", () => {
						req.destroy();
						reject(new Error("timeout"));
					});
					req.end();
				});
				apiReady = true;
				console.log("[CYPRESS RUNNER] Backend API health check passed.");
				break;
			} catch (e) {
				await new Promise((resolve) => setTimeout(resolve, 2000));
			}
		}
		if (!apiReady) {
			console.warn(
				"[CYPRESS RUNNER] Backend API health check did not pass. Proceeding anyway...",
			);
		}

		// Allow extra seconds for full context readiness
		await new Promise((resolve) => setTimeout(resolve, 5000));

		// 8. Run Cypress script
		console.log("[CYPRESS RUNNER] Spawning Cypress E2E execution tests...");
		const runner = spawn(npmCmd, ["run", "e2e"], {
			cwd: path.join(WORKSPACE_DIR, "frontend-host"),
			shell: true,
			stdio: "inherit",
		});

		await new Promise((resolve, reject) => {
			runner.on("close", (code) => {
				if (code === 0) {
					console.log(
						"[CYPRESS RUNNER] Cypress E2E execution finished successfully!",
					);
					resolve();
				} else {
					reject(
						new Error(`Cypress E2E exited with non-zero exit code: ${code}`),
					);
				}
			});
		});
	} catch (err) {
		console.error("[CYPRESS RUNNER] Execution failed:", err);
	} finally {
		await cleanUp();
		process.exit(0);
	}
}

run();
