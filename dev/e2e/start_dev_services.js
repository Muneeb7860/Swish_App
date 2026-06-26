import { spawn } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const WORKSPACE_DIR = path.resolve(path.join(__dirname, "../.."));
const LOGS_DIR = path.join(WORKSPACE_DIR, "dev/e2e");

const processes = [];

function runService(name, command, args, cwd, logFile, envOverrides = {}) {
	console.log(
		`[START SERVICES] Starting service [${name}] in directory: ${cwd}...`,
	);
	const logStream = fs.createWriteStream(logFile);

	const proc = spawn(command, args, {
		cwd,
		shell: true,
		env: {
			...process.env,
			...envOverrides,
		},
	});
	proc.stdout.pipe(logStream);
	proc.stderr.pipe(logStream);

	proc.on("close", (code) => {
		console.log(`[START SERVICES] Service [${name}] exited with code: ${code}`);
	});

	processes.push(proc);
	return proc;
}

async function cleanUp() {
	console.log("[START SERVICES] Cleaning up background services...");
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
					`[START SERVICES] Error killing process ${proc.pid}:`,
					err.message,
				);
			}
		}
	}
	console.log("[START SERVICES] Services terminated.");
}

process.on("SIGINT", async () => {
	await cleanUp();
	process.exit(0);
});

process.on("SIGTERM", async () => {
	await cleanUp();
	process.exit(0);
});

function run() {
	if (!fs.existsSync(LOGS_DIR)) {
		fs.mkdirSync(LOGS_DIR, { recursive: true });
	}

	const isWin = process.platform === "win32";
	const mvnCmd = isWin ? "mvn.cmd" : "mvn";
	const npmCmd = isWin ? "npm.cmd" : "npm";

	const commonEnv = {
		JWT_SECRET:
			"my-secret-key-that-is-long-enough-to-be-secure-for-jwt-signature-verification-32bytes-long",
		ADMIN_EMAIL: "admin@swish.local",
		ADMIN_PASSWORD: "swiss-secure-password",
	};

	// 1. Boot Backend Monolith
	runService(
		"Backend",
		mvnCmd,
		["spring-boot:run"],
		path.join(WORKSPACE_DIR, "backend"),
		path.join(LOGS_DIR, "backend.log"),
		commonEnv,
	);

	// 2. Boot Platform Gateway
	runService(
		"Platform Gateway",
		mvnCmd,
		["spring-boot:run"],
		path.join(WORKSPACE_DIR, "platform-gateway"),
		path.join(LOGS_DIR, "bff.log"),
		commonEnv,
	);

	// 3. Boot frontend App Shell Host
	runService(
		"Host App Shell",
		npmCmd,
		["run", "dev"],
		path.join(WORKSPACE_DIR, "frontend-host"),
		path.join(LOGS_DIR, "host.log"),
	);

	// 4. Boot Customer remote MFE
	runService(
		"Customer Remote",
		npmCmd,
		["run", "preview", "--", "--port", "3001", "--strictPort"],
		path.join(WORKSPACE_DIR, "frontend-customer"),
		path.join(LOGS_DIR, "customer.log"),
	);

	// 5. Boot Rider remote MFE
	runService(
		"Rider Remote",
		npmCmd,
		["run", "preview", "--", "--port", "3002", "--strictPort"],
		path.join(WORKSPACE_DIR, "frontend-rider"),
		path.join(LOGS_DIR, "rider.log"),
	);

	// 6. Boot Admin remote MFE
	runService(
		"Admin Remote",
		npmCmd,
		["run", "preview", "--", "--port", "3003", "--strictPort"],
		path.join(WORKSPACE_DIR, "frontend-admin"),
		path.join(LOGS_DIR, "admin.log"),
	);

	console.log(
		"[START SERVICES] All services launched in background. Keeping process alive. Press Ctrl+C to terminate.",
	);
}

run();
