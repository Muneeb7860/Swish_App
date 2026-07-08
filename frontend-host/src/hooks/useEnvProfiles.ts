import { useEffect, useState } from "react";

// Hardcoded fallback environment profiles
const FALLBACK_ENV_PROFILES = {
	development: {
		envName: "development",
		apiUrl: "http://localhost:5000/api",
		logLevel: "debug",
		mfaRequired: false,
		rateLimit: 100,
		dbLatencyDefault: 4,
		restockVelocityDefault: 20,
		circuitBreakerLimit: 5,
	},
	staging: {
		envName: "staging",
		apiUrl: "https://staging.swiss-app.ch/api",
		logLevel: "info",
		mfaRequired: true,
		rateLimit: 15,
		dbLatencyDefault: 150,
		restockVelocityDefault: 20,
		circuitBreakerLimit: 3,
	},
	production: {
		envName: "production",
		apiUrl: "https://api.swiss-app.ch/v1",
		logLevel: "error",
		mfaRequired: true,
		rateLimit: 4,
		dbLatencyDefault: 4,
		restockVelocityDefault: 20,
		circuitBreakerLimit: 3,
	},
};

export function useEnvProfiles() {
	const [envProfiles, setEnvProfiles] = useState(FALLBACK_ENV_PROFILES);
	const [activeProfileKey, setActiveProfileKey] = useState("development");

	useEffect(() => {
		// 1. Check if Vite's import.meta.env is available and populated
		const isViteEnv = typeof import.meta !== "undefined" && import.meta.env;
		if (isViteEnv && import.meta.env.VITE_APP_ENV) {
			const mode = import.meta.env.MODE || "development";
			setEnvProfiles((prev) => {
				const updated = { ...prev };
				const target = updated[mode] || (updated[mode] = { envName: mode });

				target.envName = import.meta.env.VITE_APP_ENV || target.envName;
				target.apiUrl = import.meta.env.VITE_API_URL || target.apiUrl;
				target.logLevel = (
					import.meta.env.VITE_LOG_LEVEL || target.logLevel
				).toLowerCase();
				target.mfaRequired =
					import.meta.env.VITE_MFA_REQUIRED === "true" ||
					import.meta.env.VITE_MFA_REQUIRED === true;
				target.rateLimit =
					parseInt(import.meta.env.VITE_RATE_LIMIT, 10) || target.rateLimit;
				target.dbLatencyDefault =
					parseInt(import.meta.env.VITE_DB_LATENCY_DEFAULT, 10) ||
					target.dbLatencyDefault;
				target.restockVelocityDefault =
					parseInt(import.meta.env.VITE_RESTOCK_VELOCITY_DEFAULT, 10) ||
					target.restockVelocityDefault;
				target.circuitBreakerLimit =
					parseInt(import.meta.env.VITE_CIRCUIT_BREAKER_LIMIT, 10) ||
					target.circuitBreakerLimit;

				return updated;
			});
			setActiveProfileKey(mode);
			console.log(
				`Loaded environment profile: ${mode} dynamically from Vite metadata.`,
			);
			return;
		}

		// 2. Otherwise, fetch files dynamically (standalone browser mode)
		async function loadEnvFiles() {
			try {
				const envResponse = await fetch("./.env");
				if (!envResponse.ok) return;
				const envText = await envResponse.text();

				const activeProfileMatch = envText.match(
					/ACTIVE_PROFILE\s*=\s*([a-zA-Z0-9_-]+)/i,
				);
				let profileKey = "development";
				if (activeProfileMatch?.[1]) {
					profileKey = activeProfileMatch[1].trim().toLowerCase();
				}

				if (!["development", "staging", "production"].includes(profileKey)) {
					profileKey = "development";
				}

				const profileResponse = await fetch(`./.env.${profileKey}`);
				if (!profileResponse.ok) {
					setActiveProfileKey(profileKey);
					return;
				}
				const profileText = await profileResponse.text();

				const parsedConfig: Record<string, string> = {};
				const lines = profileText.split("\n");
				lines.forEach((line) => {
					const cleaned = line.trim();
					if (cleaned.startsWith("#") || !cleaned.includes("=")) return;
					const eqIndex = cleaned.indexOf("=");
					const key = cleaned.substring(0, eqIndex).trim();
					const value = cleaned.substring(eqIndex + 1).trim();
					parsedConfig[key] = value;
				});

				setEnvProfiles((prev) => {
					const updated = { ...prev };
					const target =
						updated[profileKey] ||
						(updated[profileKey] = { envName: profileKey });

					if (parsedConfig.VITE_APP_ENV)
						target.envName = parsedConfig.VITE_APP_ENV;
					if (parsedConfig.VITE_API_URL)
						target.apiUrl = parsedConfig.VITE_API_URL;
					if (parsedConfig.VITE_LOG_LEVEL)
						target.logLevel = parsedConfig.VITE_LOG_LEVEL.toLowerCase();
					if (parsedConfig.VITE_MFA_REQUIRED)
						target.mfaRequired =
							parsedConfig.VITE_MFA_REQUIRED.trim().toLowerCase() === "true";
					if (parsedConfig.VITE_RATE_LIMIT)
						target.rateLimit = parseInt(parsedConfig.VITE_RATE_LIMIT, 10);
					if (parsedConfig.VITE_DB_LATENCY_DEFAULT)
						target.dbLatencyDefault = parseInt(
							parsedConfig.VITE_DB_LATENCY_DEFAULT,
							10,
						);
					if (parsedConfig.VITE_RESTOCK_VELOCITY_DEFAULT)
						target.restockVelocityDefault = parseInt(
							parsedConfig.VITE_RESTOCK_VELOCITY_DEFAULT,
							10,
						);
					if (parsedConfig.VITE_CIRCUIT_BREAKER_LIMIT)
						target.circuitBreakerLimit = parseInt(
							parsedConfig.VITE_CIRCUIT_BREAKER_LIMIT,
							10,
						);

					return updated;
				});

				setActiveProfileKey(profileKey);
				console.log(
					`Loaded environment profile: ${profileKey} dynamically from file system.`,
				);
			} catch (err) {
				console.warn(
					"Could not load local .env files dynamically. Using hardcoded defaults.",
					err,
				);
			}
		}
		loadEnvFiles();
	}, []);

	return {
		envProfiles,
		setEnvProfiles,
		activeProfileKey,
		setActiveProfileKey,
		activeProfile: envProfiles[activeProfileKey],
	};
}
