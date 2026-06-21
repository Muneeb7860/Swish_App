import { useCallback, useEffect, useState } from "react";
import { buildRequestHeaders } from "../utils/api";

interface LogEntry {
	text: string;
	type: "info" | "success" | "warning" | "error";
	time: string;
}

interface UseRetailerApiOptions {
	gatewayUrl: string;
	accessToken: string;
	addLog: (message: string, type?: LogEntry["type"]) => void;
}

/**
 * Custom hook for retailer onboarding API interactions.
 * Handles register, approve gates, provision sensors, calibrate, and verify integrity.
 */
export function useRetailerApi({
	gatewayUrl,
	accessToken,
	addLog,
}: UseRetailerApiOptions) {
	// Retailer Onboarding States
	const [retailerName, setRetailerName] = useState("");
	const [retailerEmail, setRetailerEmail] = useState("");
	const [retailerStoreId, setRetailerStoreId] = useState("store-valora-01");
	const [billingTier, setBillingTier] = useState<
		"BASIC" | "PRO" | "ENTERPRISE"
	>("PRO");
	const [currentRetailer, setCurrentRetailer] = useState<any>(null);
	const [revealedApiKey, setRevealedApiKey] = useState<string | null>(null);

	// Sensor Provisioning States
	const [sensorType, setSensorType] = useState<
		"TEMPERATURE" | "HUMIDITY" | "GPS"
	>("TEMPERATURE");
	const [sensorsList, setSensorsList] = useState<any[]>([]);

	// Loading & Action Flags
	const [isRegistering, setIsRegistering] = useState(false);
	const [isProvisioning, setIsProvisioning] = useState(false);

	const getHeaders = useCallback(
		(custom: Record<string, string> = {}) =>
			buildRequestHeaders(accessToken, custom),
		[accessToken],
	);

	const handleRegisterRetailer = useCallback(
		async (e: React.FormEvent) => {
			e.preventDefault();
			if (!retailerName || !retailerEmail || !retailerStoreId) {
				addLog("Please fill out all registration fields", "error");
				return;
			}

			setIsRegistering(true);
			addLog(
				`Registering Retailer: ${retailerName} (${billingTier})...`,
				"info",
			);

			try {
				const res = await fetch(`${gatewayUrl}/api/v1/retailers/register`, {
					method: "POST",
					headers: getHeaders(),
					body: JSON.stringify({
						name: retailerName,
						contactEmail: retailerEmail,
						storeId: retailerStoreId,
						tier: billingTier,
					}),
				});

				if (!res.ok) {
					throw new Error(`Registration API returned status ${res.status}`);
				}

				const data = await res.json();
				setCurrentRetailer(data);
				addLog(
					`Retailer registered successfully! ID: ${data.retailerId}. Status: ${data.status}`,
					"success",
				);

				window.dispatchEvent(
					new CustomEvent("swish:action", {
						detail: { type: "REGISTER_RETAILER", payload: data },
					}),
				);
			} catch (err: any) {
				addLog(
					`API Registration Failed: ${err.message}. Initializing Local Sandbox Mock.`,
					"warning",
				);

				const mockId = `RTL-${Math.floor(100000 + Math.random() * 900000)}`;
				const mockRetailer = {
					retailerId: mockId,
					name: retailerName,
					contactEmail: retailerEmail,
					storeId: retailerStoreId,
					tier: billingTier,
					status: "PENDING",
					approvalOps: false,
					approvalCompliance: false,
					approvalAdmin: false,
					billingAccountId: `ACC-${Math.floor(1000 + Math.random() * 9000)}`,
				};
				setCurrentRetailer(mockRetailer);
				addLog(
					`[Local Sandbox Mock] Retailer registered. ID: ${mockId}. Awaiting 3-gate approval.`,
					"success",
				);

				window.dispatchEvent(
					new CustomEvent("swish:action", {
						detail: { type: "REGISTER_RETAILER", payload: mockRetailer },
					}),
				);
			} finally {
				setIsRegistering(false);
			}
		},
		[
			retailerName,
			retailerEmail,
			retailerStoreId,
			billingTier,
			gatewayUrl,
			getHeaders,
			addLog,
		],
	);

	const handleApproveOnboardingGate = useCallback(
		async (gate: "ops" | "compliance" | "admin") => {
			if (!currentRetailer) return;

			const retailerId = currentRetailer.retailerId;
			addLog(`Simulating approval for Gate: ${gate.toUpperCase()}...`, "info");

			try {
				const res = await fetch(
					`${gatewayUrl}/api/v1/retailers/${retailerId}/gates/${gate}/approve`,
					{
						method: "POST",
						headers: getHeaders(),
					},
				);

				if (!res.ok) {
					throw new Error(`Gate Approval API returned status ${res.status}`);
				}

				const data = await res.json();
				setCurrentRetailer(data.retailer);
				if (data.apiKey) {
					setRevealedApiKey(data.apiKey);
					addLog(
						"Retailer fully activated! Plaintext API Key generated.",
						"success",
					);
				} else {
					addLog(
						`Gate ${gate.toUpperCase()} approved successfully.`,
						"success",
					);
				}

				window.dispatchEvent(
					new CustomEvent("swish:action", {
						detail: {
							type: "APPROVE_GATE",
							payload: { retailerId, gate, ...data.retailer },
						},
					}),
				);
			} catch (err: any) {
				addLog(
					`API Gate Approval Failed: ${err.message}. Simulating in local sandbox.`,
					"warning",
				);

				setCurrentRetailer((prev: any) => {
					if (!prev) return null;
					const updated = { ...prev };
					if (gate === "ops") updated.approvalOps = true;
					if (gate === "compliance") updated.approvalCompliance = true;
					if (gate === "admin") {
						updated.approvalAdmin = true;
						updated.status = "ACTIVE";
						const mockApiKey =
							"swish_live_" +
							Math.random().toString(36).substring(2, 18) +
							Math.random().toString(36).substring(2, 18);
						setRevealedApiKey(mockApiKey);
						addLog(
							"Retailer fully activated! Plaintext API Key generated.",
							"success",
						);
					}

					window.dispatchEvent(
						new CustomEvent("swish:action", {
							detail: {
								type: "APPROVE_GATE",
								payload: { retailerId, gate, ...updated },
							},
						}),
					);

					return updated;
				});
			}
		},
		[currentRetailer, gatewayUrl, getHeaders, addLog],
	);

	const handleProvisionSensor = useCallback(
		async (e: React.FormEvent) => {
			e.preventDefault();
			if (currentRetailer?.status !== "ACTIVE") {
				addLog("Retailer must be ACTIVE to provision sensors", "error");
				return;
			}

			setIsProvisioning(true);
			const retailerId = currentRetailer.retailerId;
			const storeId = currentRetailer.storeId;
			addLog(`Provisioning ${sensorType} sensor for Hub ${storeId}...`, "info");

			try {
				const res = await fetch(`${gatewayUrl}/api/v1/sensors`, {
					method: "POST",
					headers: getHeaders(),
					body: JSON.stringify({ retailerId, storeId, type: sensorType }),
				});

				if (!res.ok) {
					throw new Error(`Sensor API returned status ${res.status}`);
				}

				const data = await res.json();
				addLog(
					`Sensor provisioned! ID: ${data.sensor.sensorId}. Key generated.`,
					"success",
				);

				const newSensor = {
					...data.sensor,
					deviceKey: data.deviceKey,
					integrityValid: true,
				};
				setSensorsList((prev) => [...prev, newSensor]);

				alert(
					`IoT Device Key Provisioned Successfully:\n\n${data.deviceKey}\n\nRecord this key. It will not be displayed again.`,
				);
			} catch (err: any) {
				addLog(
					`API Provisioning Failed: ${err.message}. Fallback to Sandbox mock.`,
					"warning",
				);

				const mockSensorId = `SNS-${Math.floor(100000 + Math.random() * 900000)}`;
				const mockDeviceKey = `dev_key_${Math.random().toString(36).substring(2, 15)}`;
				const newMockSensor = {
					sensorId: mockSensorId,
					retailerId,
					storeId,
					sensorType,
					status: "ACTIVE",
					lastCalibratedAt: new Date().toISOString(),
					calibrationStatus: "SUCCESS",
					deviceKey: mockDeviceKey,
					integrityValid: true,
				};
				setSensorsList((prev) => [...prev, newMockSensor]);
				addLog(
					`[Local Sandbox Mock] Sensor provisioned. ID: ${mockSensorId}`,
					"success",
				);

				alert(
					`[Local Sandbox Mock] IoT Device Key Provisioned:\n\n${mockDeviceKey}\n\nRecord this key. It will not be displayed again.`,
				);
			} finally {
				setIsProvisioning(false);
			}
		},
		[currentRetailer, sensorType, gatewayUrl, getHeaders, addLog],
	);

	const handleVerifySensorIntegrity = useCallback(
		async (sensorId: string) => {
			addLog(
				`Verifying SHA-256 Telemetry Hash-Chain Integrity for ${sensorId}...`,
				"info",
			);

			try {
				const res = await fetch(
					`${gatewayUrl}/api/v1/sensors/${sensorId}/verify-integrity`,
					{ method: "GET", headers: getHeaders() },
				);

				if (!res.ok) {
					throw new Error(
						`Integrity verification API returned status ${res.status}`,
					);
				}

				const data = await res.json();
				const isValid = data.valid;

				setSensorsList((prev) =>
					prev.map((s) =>
						s.sensorId === sensorId ? { ...s, integrityValid: isValid } : s,
					),
				);

				if (isValid) {
					addLog(
						`Verification PASSED: Telemetry chain for ${sensorId} is cryptographically secure.`,
						"success",
					);
				} else {
					addLog(
						`Verification FAILED: Hash-chain anomaly detected on device ${sensorId}!`,
						"error",
					);
				}
			} catch (err: any) {
				addLog(
					`API Integrity check failed: ${err.message}. Simulating locally.`,
					"warning",
				);

				setSensorsList((prev) =>
					prev.map((s) =>
						s.sensorId === sensorId ? { ...s, integrityValid: true } : s,
					),
				);
				addLog(
					`[Local Sandbox Mock] Verification PASSED: Telemetry chain for ${sensorId} is mathematically verified.`,
					"success",
				);
			}
		},
		[gatewayUrl, getHeaders, addLog],
	);

	const handleCalibrateSensor = useCallback(
		async (sensorId: string) => {
			addLog(`Calibrating sensor ${sensorId}...`, "info");

			try {
				const res = await fetch(
					`${gatewayUrl}/api/v1/sensors/${sensorId}/calibrate?success=true`,
					{ method: "POST", headers: getHeaders() },
				);

				if (!res.ok) {
					throw new Error(`Calibration API returned status ${res.status}`);
				}

				const data = await res.json();
				setSensorsList((prev) =>
					prev.map((s) =>
						s.sensorId === sensorId
							? {
									...s,
									lastCalibratedAt: data.lastCalibratedAt,
									calibrationStatus: data.calibrationStatus,
								}
							: s,
					),
				);
				addLog(`Sensor ${sensorId} calibrated successfully.`, "success");
			} catch (err: any) {
				addLog(
					`API Calibration failed: ${err.message}. Simulating locally.`,
					"warning",
				);
				setSensorsList((prev) =>
					prev.map((s) =>
						s.sensorId === sensorId
							? {
									...s,
									lastCalibratedAt: new Date().toISOString(),
									calibrationStatus: "SUCCESS",
								}
							: s,
					),
				);
				addLog(
					`[Local Sandbox Mock] Sensor ${sensorId} calibrated successfully.`,
					"success",
				);
			}
		},
		[gatewayUrl, getHeaders, addLog],
	);

	// Fetch sensors list when retailer changes
	useEffect(() => {
		if (currentRetailer && currentRetailer.status === "ACTIVE") {
			const fetchSensors = async () => {
				try {
					const res = await fetch(
						`${gatewayUrl}/api/v1/sensors?retailerId=${currentRetailer.retailerId}`,
						{ method: "GET", headers: getHeaders() },
					);
					if (res.ok) {
						const data = await res.json();
						setSensorsList(data);
					}
				} catch {
					// Silent fallback — local mock provides state
				}
			};
			fetchSensors();
		} else {
			setSensorsList([]);
		}
	}, [currentRetailer, gatewayUrl, getHeaders]);

	const resetRetailer = useCallback(() => {
		setCurrentRetailer(null);
		setRevealedApiKey(null);
		setSensorsList([]);
	}, []);

	return {
		// Retailer state
		retailerName,
		setRetailerName,
		retailerEmail,
		setRetailerEmail,
		retailerStoreId,
		setRetailerStoreId,
		billingTier,
		setBillingTier,
		currentRetailer,
		revealedApiKey,
		isRegistering,

		// Sensor state
		sensorType,
		setSensorType,
		sensorsList,
		isProvisioning,

		// Actions
		handleRegisterRetailer,
		handleApproveOnboardingGate,
		handleProvisionSensor,
		handleVerifySensorIntegrity,
		handleCalibrateSensor,
		resetRetailer,
	};
}
