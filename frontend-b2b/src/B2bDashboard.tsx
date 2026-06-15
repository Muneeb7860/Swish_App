import React, { useState, useEffect } from "react";
import { useResilientWebSocket } from "./useResilientWebSocket";

interface NotificationPayload {
	ai_status?: string;
	amount?: number;
	orderId?: string;
	status?: string;
	[key: string]: any;
}

interface NotificationEnvelope {
	id: string;
	type: string;
	timestamp: string;
	recipientId: string;
	priority?: string;
	correlationId?: string;
	payload?: NotificationPayload;
}

interface LogEntry {
	text: string;
	type: "info" | "success" | "warning" | "error";
	time: string;
}

const B2bDashboard: React.FC = () => {
	// Configurable connection settings
	const [gatewayUrl, setGatewayUrl] = useState("http://localhost:8080");
	const [userId, setUserId] = useState("b2b-customer-123");
	const [accessToken, setAccessToken] = useState(
		() => localStorage.getItem("jwt_token") || "mock_token_for_now",
	);
	const [isConfigOpen, setIsConfigOpen] = useState(false);

	// Business state
	const [orderId, setOrderId] = useState(
		"ORD-" + Math.floor(100000 + Math.random() * 900000),
	);
	const [orderStatus, setOrderStatus] = useState("PENDING"); // PENDING, PAYMENT_PROCESSING, PROCESSING, APPROVED, HUMAN_TRIAGE, PAYMENT_FAILED
	const [lastTraceId, setLastTraceId] = useState<string | null>(null);
	const [copiedIndex, setCopiedIndex] = useState<number | string | null>(null);

	// E2E Simulation state
	const [simulationMode, setSimulationMode] = useState<"AUTO" | "LOCAL_MOCK">(
		"AUTO",
	);
	const [simulationLog, setSimulationLog] = useState<LogEntry[]>([]);
	const [isSimulating, setIsSimulating] = useState(false);

	// Navigation Tab
	const [activeSubTab, setActiveSubTab] = useState<"wholesale" | "onboarding">(
		"wholesale",
	);

	// Retailer Onboarding States (FR-01)
	const [retailerName, setRetailerName] = useState("");
	const [retailerEmail, setRetailerEmail] = useState("");
	const [retailerStoreId, setRetailerStoreId] = useState("store-valora-01");
	const [billingTier, setBillingTier] = useState<
		"BASIC" | "PRO" | "ENTERPRISE"
	>("PRO");
	const [currentRetailer, setCurrentRetailer] = useState<any>(null);
	const [revealedApiKey, setRevealedApiKey] = useState<string | null>(null);

	// Sensor Provisioning States (FR-01 Device provisioning)
	const [sensorType, setSensorType] = useState<
		"TEMPERATURE" | "HUMIDITY" | "GPS"
	>("TEMPERATURE");
	const [sensorsList, setSensorsList] = useState<any[]>([]);

	// Loading & Action Flags
	const [isRegistering, setIsRegistering] = useState(false);
	const [isProvisioning, setIsProvisioning] = useState(false);

	const getRequestHeaders = (customHeaders: Record<string, string> = {}) => {
		const headers: Record<string, string> = {
			"Content-Type": "application/json",
			...customHeaders,
		};
		if (accessToken && accessToken !== "mock_token_for_now") {
			headers["Authorization"] = `Bearer ${accessToken}`;
		}
		if (typeof window !== "undefined" && (window as any).getActiveTraceParent) {
			try {
				headers["traceparent"] = (window as any).getActiveTraceParent();
			} catch (e) {
				console.warn("Failed to retrieve traceparent from window:", e);
			}
		}
		return headers;
	};

	// ── Retailer / Wholesaler API Integrations ──
	const handleRegisterRetailer = async (e: React.FormEvent) => {
		e.preventDefault();
		if (!retailerName || !retailerEmail || !retailerStoreId) {
			addLog("Please fill out all registration fields", "error");
			return;
		}

		setIsRegistering(true);
		addLog(`Registering Retailer: ${retailerName} (${billingTier})...`, "info");

		try {
			const res = await fetch(`${gatewayUrl}/api/v1/retailers/register`, {
				method: "POST",
				headers: getRequestHeaders(),
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

			// Dispatch action to event bus for state decoupling
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
			// Local Mock Fallback
			const mockId = "RTL-" + Math.floor(100000 + Math.random() * 900000);
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
				billingAccountId: "ACC-" + Math.floor(1000 + Math.random() * 9000),
			};
			setCurrentRetailer(mockRetailer);
			addLog(
				`[Local Sandbox Mock] Retailer registered. ID: ${mockId}. Awaiting 3-gate approval.`,
				"success",
			);

			// Dispatch mock action to event bus for state decoupling
			window.dispatchEvent(
				new CustomEvent("swish:action", {
					detail: { type: "REGISTER_RETAILER", payload: mockRetailer },
				}),
			);
		} finally {
			setIsRegistering(false);
		}
	};

	const handleApproveOnboardingGate = async (
		gate: "ops" | "compliance" | "admin",
	) => {
		if (!currentRetailer) return;

		const retailerId = currentRetailer.retailerId;
		addLog(`Simulating approval for Gate: ${gate.toUpperCase()}...`, "info");

		try {
			const res = await fetch(
				`${gatewayUrl}/api/v1/retailers/${retailerId}/gates/${gate}/approve`,
				{
					method: "POST",
					headers: getRequestHeaders(),
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
				addLog(`Gate ${gate.toUpperCase()} approved successfully.`, "success");
			}

			// Dispatch action to event bus for state decoupling
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

			// Local Mock State Update
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

				// Dispatch action to event bus for state decoupling
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
	};

	const handleProvisionSensor = async (e: React.FormEvent) => {
		e.preventDefault();
		if (!currentRetailer || currentRetailer.status !== "ACTIVE") {
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
				headers: getRequestHeaders(),
				body: JSON.stringify({
					retailerId,
					storeId,
					type: sensorType,
				}),
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

			// Prompt to save key
			alert(
				`IoT Device Key Provisioned Successfully:\n\n${data.deviceKey}\n\nRecord this key. It will not be displayed again.`,
			);
		} catch (err: any) {
			addLog(
				`API Provisioning Failed: ${err.message}. Fallback to Sandbox mock.`,
				"warning",
			);

			const mockSensorId = "SNS-" + Math.floor(100000 + Math.random() * 900000);
			const mockDeviceKey =
				"dev_key_" + Math.random().toString(36).substring(2, 15);
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
	};

	const handleVerifySensorIntegrity = async (sensorId: string) => {
		addLog(
			`Verifying SHA-256 Telemetry Hash-Chain Integrity for ${sensorId}...`,
			"info",
		);
		try {
			const res = await fetch(
				`${gatewayUrl}/api/v1/sensors/${sensorId}/verify-integrity`,
				{
					method: "GET",
					headers: getRequestHeaders(),
				},
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

			// Mock verification always succeeds unless simulated otherwise
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
	};

	const handleCalibrateSensor = async (sensorId: string) => {
		addLog(`Calibrating sensor ${sensorId}...`, "info");
		try {
			const res = await fetch(
				`${gatewayUrl}/api/v1/sensors/${sensorId}/calibrate?success=true`,
				{
					method: "POST",
					headers: getRequestHeaders(),
				},
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
	};

	// Fetch sensors list when retailer changes
	useEffect(() => {
		if (currentRetailer && currentRetailer.status === "ACTIVE") {
			const fetchSensors = async () => {
				try {
					const res = await fetch(
						`${gatewayUrl}/api/v1/sensors?retailerId=${currentRetailer.retailerId}`,
						{
							method: "GET",
							headers: getRequestHeaders(),
						},
					);
					if (res.ok) {
						const data = await res.json();
						setSensorsList(data);
					}
				} catch (e) {
					// Silent ignore since local mock provides the state
				}
			};
			fetchSensors();
		} else {
			setSensorsList([]);
		}
	}, [currentRetailer, gatewayUrl, accessToken]);

	// Derive WebSocket URL from HTTP Gateway URL
	const getWsUrl = (httpUrl: string) => {
		try {
			const urlObj = new URL(httpUrl);
			const wsProtocol = urlObj.protocol === "https:" ? "wss:" : "ws:";
			return `${wsProtocol}//${urlObj.host}/ws/notifications/b2b`;
		} catch (e) {
			return "ws://localhost:8080/ws/notifications/b2b";
		}
	};

	const wsUrl = getWsUrl(gatewayUrl);

	const addLog = (
		message: string,
		type: "info" | "success" | "warning" | "error" = "info",
	) => {
		setSimulationLog((prev) =>
			[
				{ text: message, type, time: new Date().toLocaleTimeString() },
				...prev,
			].slice(0, 20),
		);
	};

	// Handle incoming websocket messages
	const handleWsMessage = (envelope: NotificationEnvelope) => {
		console.log("[B2bDashboard] Received message envelope:", envelope);
		if (envelope.correlationId) {
			setLastTraceId(envelope.correlationId);
			const event = new CustomEvent("ws-packet-received", {
				detail: { ...envelope, source: "B2bDashboard" },
			});
			window.dispatchEvent(event);
		}

		const payload = envelope.payload || {};

		switch (envelope.type) {
			case "ORDER_EVALUATED":
				addLog(
					`AI credit evaluation completed: ${payload.ai_status || "UNKNOWN"}`,
					"success",
				);
				setOrderStatus(payload.ai_status || "UNKNOWN");
				break;
			case "PAYMENT_CONFIRMED":
				addLog("Payment confirmed. Order sent to AI credit check.", "success");
				setOrderStatus("PROCESSING");
				break;
			case "PAYMENT_FAILED":
				addLog("Payment failed. Transaction aborted.", "error");
				setOrderStatus("PAYMENT_FAILED");
				break;
			default:
				if (payload.ai_status) {
					setOrderStatus(payload.ai_status);
				}
		}
	};

	// Connect via resilient hook
	const {
		status: wsStatus,
		notifications,
		reconnectAttempts,
		reconnect,
		disconnect,
		clearNotifications,
	} = useResilientWebSocket(wsUrl, {
		userId,
		accessToken,
		onMessage: handleWsMessage,
	});

	// Handle simulated E2E payment and webhook
	const handleCheckout = async () => {
		setIsSimulating(true);
		setOrderStatus("PAYMENT_PROCESSING");
		const idempotencyKey = "idemp-" + Math.floor(Math.random() * 100000000);
		const traceId = "trace-" + Math.floor(Math.random() * 100000000);

		addLog(
			`Initiating checkout. Order: ${orderId}, Idempotency: ${idempotencyKey}`,
			"info",
		);

		if (simulationMode === "LOCAL_MOCK") {
			runLocalMock(idempotencyKey, traceId);
			return;
		}

		try {
			addLog(`Calling Gateway: POST /api/v1/checkout/intents...`, "info");
			const intentRes = await fetch(`${gatewayUrl}/api/v1/checkout/intents`, {
				method: "POST",
				headers: getRequestHeaders({
					"X-Idempotency-Key": idempotencyKey,
				}),
				body: JSON.stringify({
					customerId: userId,
					orderId: orderId,
					amount: 1250000,
				}),
			});

			if (!intentRes.ok) {
				throw new Error(`Intent API returned status ${intentRes.status}`);
			}

			const intentData = await intentRes.json();
			addLog(
				`PaymentIntent created. ID: ${intentData.paymentId}. Client Secret acquired.`,
				"success",
			);

			// Now trigger Stripe Webhook
			addLog(`Calling Gateway: POST /api/webhooks/payments/stripe...`, "info");
			const webhookRes = await fetch(
				`${gatewayUrl}/api/webhooks/payments/stripe`,
				{
					method: "POST",
					headers: {
						"Content-Type": "application/json",
						"Stripe-Signature": "mock_sig_for_dev",
					},
					body: JSON.stringify({
						id: "evt_" + Math.floor(Math.random() * 100000),
						type: "payment_intent.succeeded",
						paymentIntentId: intentData.paymentId,
						correlationId: traceId,
					}),
				},
			);

			if (!webhookRes.ok) {
				throw new Error(`Webhook API returned status ${webhookRes.status}`);
			}

			addLog(
				`Simulated Stripe Webhook dispatched successfully. Waiting for WebSocket message...`,
				"success",
			);
			setLastTraceId(traceId);
		} catch (error: any) {
			addLog(
				`API Connection Failed: ${error.message}. Falling back to Local Mock.`,
				"warning",
			);
			runLocalMock(idempotencyKey, traceId);
		} finally {
			setIsSimulating(false);
		}
	};

	const runLocalMock = (idempotencyKey: string, traceId: string) => {
		setTimeout(() => {
			addLog(`[Local Mock] Payment succeeded.`, "success");
			setOrderStatus("PROCESSING");

			window.dispatchEvent(
				new CustomEvent("ws-packet-received", {
					detail: {
						id: "mock-msg-" + Math.random(),
						type: "PAYMENT_CONFIRMED",
						timestamp: new Date().toISOString(),
						recipientId: userId,
						correlationId: traceId,
						payload: { status: "CONFIRMED", orderId },
					},
				}),
			);

			setTimeout(() => {
				const isApproved = Math.random() > 0.3;
				const statusOutcome = isApproved ? "APPROVED" : "HUMAN_TRIAGE";

				setOrderStatus(statusOutcome);
				addLog(
					`[Local Mock] AI Evaluation complete: ${statusOutcome}`,
					"success",
				);

				window.dispatchEvent(
					new CustomEvent("ws-packet-received", {
						detail: {
							id: "mock-msg-" + Math.random(),
							type: "ORDER_EVALUATED",
							timestamp: new Date().toISOString(),
							recipientId: userId,
							correlationId: traceId,
							payload: { ai_status: statusOutcome, orderId },
						},
					}),
				);
			}, 3000);
		}, 1500);
		setIsSimulating(false);
	};

	const resetOrder = () => {
		setOrderId("ORD-" + Math.floor(100000 + Math.random() * 900000));
		setOrderStatus("PENDING");
		addLog("Order state reset.");
	};

	const copyToClipboard = (text: string, index: number | string) => {
		navigator.clipboard.writeText(text);
		setCopiedIndex(index);
		setTimeout(() => setCopiedIndex(null), 1500);
	};

	return (
		<div className="flex flex-col gap-6 animate-fade-in">
			{/* Top Bar with Status and Config Toggle */}
			<div className="flex flex-col sm:flex-row justify-between items-start sm:items-center border-b border-slate-200 dark:border-slate-800 pb-5 gap-4">
				<div className="flex flex-col">
					<h2 className="m-0 text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
						B2B Wholesale Portal
					</h2>
					<p className="m-0 mt-1 text-sm text-slate-500 dark:text-slate-400">
						High-value order clearing & real-time credit underwriting
					</p>
				</div>
				<div className="flex items-center gap-4">
					<button
						className={`px-4 py-2 text-sm font-semibold rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 cursor-pointer transition ${isConfigOpen ? "border-indigo-400 dark:border-indigo-700 text-indigo-600 dark:text-indigo-400 bg-indigo-50 dark:bg-indigo-950/40" : ""}`}
						onClick={() => setIsConfigOpen(!isConfigOpen)}
					>
						⚙️ Configure Socket
					</button>
					<div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-100 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-800 text-xs font-semibold text-slate-700 dark:text-slate-300">
						<span
							className={`w-2 h-2 rounded-full inline-block ${
								wsStatus === "CONNECTED"
									? "bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)]"
									: wsStatus.startsWith("RECONNECTING") ||
											wsStatus === "CONNECTING"
										? "bg-amber-500 animate-pulse"
										: "bg-rose-500"
							}`}
						></span>
						<span className="capitalize">{wsStatus.toLowerCase()}</span>
						{reconnectAttempts > 0 && wsStatus === "RECONNECTING" && (
							<span className="text-[10px] text-slate-400">
								({reconnectAttempts}/10)
							</span>
						)}
					</div>
				</div>
			</div>

			{/* Socket Configuration Drawer */}
			{isConfigOpen && (
				<div className="bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 rounded-xl p-5 shadow-lg animate-slide-up flex flex-col gap-4">
					<h3 className="m-0 text-sm font-bold text-slate-900 dark:text-slate-100">
						WebSocket Connection Settings
					</h3>
					<div className="grid grid-cols-1 md:grid-cols-3 gap-4">
						<div className="flex flex-col gap-1">
							<label className="text-xs font-semibold text-slate-500">
								Gateway URL:
							</label>
							<input
								type="text"
								className="px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 text-sm focus:outline-none focus:border-indigo-500"
								value={gatewayUrl}
								onChange={(e) => setGatewayUrl(e.target.value)}
							/>
						</div>
						<div className="flex flex-col gap-1">
							<label className="text-xs font-semibold text-slate-500">
								User ID:
							</label>
							<input
								type="text"
								className="px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 text-sm focus:outline-none focus:border-indigo-500"
								value={userId}
								onChange={(e) => setUserId(e.target.value)}
							/>
						</div>
						<div className="flex flex-col gap-1">
							<label className="text-xs font-semibold text-slate-500">
								Access Token (JWT):
							</label>
							<input
								type="password"
								className="px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 text-sm focus:outline-none focus:border-indigo-500"
								value={accessToken}
								onChange={(e) => setAccessToken(e.target.value)}
							/>
						</div>
					</div>
					<div className="flex gap-3 mt-1">
						<button
							className="px-3 py-1.5 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg cursor-pointer transition shadow"
							onClick={reconnect}
						>
							Reinitialize Socket
						</button>
						<button
							className="px-3 py-1.5 text-xs font-semibold text-rose-600 bg-rose-50 dark:bg-rose-950/20 border border-rose-200 dark:border-rose-800/60 hover:bg-rose-100 rounded-lg cursor-pointer transition"
							onClick={disconnect}
						>
							Disconnect
						</button>
					</div>
				</div>
			)}

			{/* Sub-Tab Navigation */}
			<div className="flex border-b border-slate-200 dark:border-slate-800 gap-4 mb-4">
				<button
					className={`pb-3 text-sm font-semibold cursor-pointer transition ${activeSubTab === "wholesale" ? "border-b-2 border-indigo-500 text-indigo-600 dark:text-indigo-400" : "text-slate-500 hover:text-slate-800 dark:hover:text-slate-200"}`}
					onClick={() => setActiveSubTab("wholesale")}
				>
					📦 Wholesale Orders Desk
				</button>
				<button
					className={`pb-3 text-sm font-semibold cursor-pointer transition ${activeSubTab === "onboarding" ? "border-b-2 border-indigo-500 text-indigo-600 dark:text-indigo-400" : "text-slate-500 hover:text-slate-800 dark:hover:text-slate-200"}`}
					onClick={() => setActiveSubTab("onboarding")}
				>
					🏢 Retailer Onboarding & IoT Desk
				</button>
			</div>

			{activeSubTab === "wholesale" && (
				/* Main Grid */
				<div className="grid grid-cols-1 lg:grid-cols-[1fr_340px] gap-6">
					{/* Left Column: Order Flow */}
					<div className="upgrade-glow-card p-6 flex flex-col">
						<div className="flex justify-between items-center mb-2">
							<h3 className="m-0 text-base font-bold text-slate-900 dark:text-slate-100">
								Wholesale Order Checkout
							</h3>
							{orderStatus !== "PENDING" && (
								<button
									className="text-xs text-slate-400 hover:text-slate-950 dark:hover:text-slate-100 cursor-pointer"
									onClick={resetOrder}
								>
									🔄 Reset Sandbox
								</button>
							)}
						</div>

						<div className="bg-slate-50 dark:bg-slate-800/40 border border-slate-200 dark:border-slate-800/60 rounded-xl p-4 my-4 flex flex-col gap-2.5">
							<div className="flex justify-between text-xs text-slate-500 dark:text-slate-400">
								<span>Order Number:</span>
								<strong className="font-mono text-slate-800 dark:text-slate-200">
									{orderId}
								</strong>
							</div>
							<div className="flex justify-between text-xs text-slate-500 dark:text-slate-400">
								<span>Order Total:</span>
								<strong className="text-indigo-600 dark:text-indigo-400 font-bold text-sm">
									$1,250,000.00 USD
								</strong>
							</div>
							<div className="flex justify-between text-xs text-slate-500 dark:text-slate-400">
								<span>Customer ID:</span>
								<span className="font-mono text-slate-800 dark:text-slate-200">
									{userId}
								</span>
							</div>
						</div>

						{/* Checkout Status Timeline Visualizer */}
						<div className="flex justify-between my-6 relative px-2.5 timeline-visualizer">
							<div className="absolute top-4 left-[30px] right-[30px] h-[2px] bg-slate-200 dark:bg-slate-800/40 z-10"></div>

							<div
								className={`flex flex-col items-center gap-2 z-20 flex-1 timeline-step ${orderStatus === "PENDING" ? "active" : "completed"}`}
							>
								<div className="w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold transition duration-300 border-2 step-circle">
									1
								</div>
								<div className="text-[10px] font-semibold step-label">
									Draft
								</div>
							</div>

							<div
								className={`flex flex-col items-center gap-2 z-20 flex-1 timeline-step ${
									orderStatus === "PAYMENT_PROCESSING"
										? "active"
										: orderStatus === "PAYMENT_FAILED"
											? "failed"
											: orderStatus !== "PENDING"
												? "completed"
												: ""
								}`}
							>
								<div className="w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold transition duration-300 border-2 step-circle">
									2
								</div>
								<div className="text-[10px] font-semibold step-label">
									Stripe Payment
								</div>
							</div>

							<div
								className={`flex flex-col items-center gap-2 z-20 flex-1 timeline-step ${
									orderStatus === "PROCESSING"
										? "active"
										: (
													orderStatus === "APPROVED" ||
														orderStatus === "HUMAN_TRIAGE"
												)
											? "completed"
											: orderStatus === "PAYMENT_FAILED"
												? "failed"
												: ""
								}`}
							>
								<div className="w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold transition duration-300 border-2 step-circle">
									3
								</div>
								<div className="text-[10px] font-semibold step-label">
									AI Risk Check
								</div>
							</div>

							<div
								className={`flex flex-col items-center gap-2 z-20 flex-1 timeline-step ${
									orderStatus === "APPROVED"
										? "completed"
										: orderStatus === "HUMAN_TRIAGE"
											? "failed" // We can treat human triage as warning or failed state styling
											: ""
								}`}
							>
								<div className="w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold transition duration-300 border-2 step-circle">
									4
								</div>
								<div className="text-[10px] font-semibold step-label">
									Final Release
								</div>
							</div>
						</div>

						{/* Interactive Actions */}
						<div className="glass-panel p-5 mb-5">
							{orderStatus === "PENDING" ? (
								<div className="flex flex-col">
									<h4 className="m-0 mb-3 text-xs font-bold text-text-secondary uppercase tracking-wider">
										Secure Credit Card Input (Stripe Simulator)
									</h4>

									{/* Premium Credit Card Mockup */}
									<div className="premium-card-wrapper flex justify-center mb-5">
										<div className="premium-credit-card">
											<div className="flex justify-between items-center w-full mb-2">
												<span className="text-xs font-extrabold tracking-wider text-slate-300">SWISH WHOLESALE</span>
												<div className="premium-credit-card-hologram" />
											</div>
											<div className="flex justify-start w-full">
												<div className="premium-credit-card-chip" />
											</div>
											<div className="text-lg font-bold tracking-widest text-center my-3 text-slate-100 font-mono">
												4242 •••• •••• 4242
											</div>
											<div className="flex justify-between items-end w-full text-[10px] text-slate-400">
												<div className="flex flex-col">
													<span className="uppercase text-[8px] tracking-wider text-slate-500">Card Holder</span>
													<strong className="text-slate-200">B2B Merchant Client</strong>
												</div>
												<div className="flex flex-col items-end">
													<span className="uppercase text-[8px] tracking-wider text-slate-500">Expires</span>
													<strong className="text-slate-200">12 / 28</strong>
												</div>
											</div>
										</div>
									</div>

									<div className="mb-4">
										<label className="text-xs font-semibold text-text-secondary flex items-center gap-2">
											Simulation Mode:
											<select
												className="px-2 py-1 rounded border border-border bg-slate-900 text-text-h text-xs"
												value={simulationMode}
												onChange={(e) =>
													setSimulationMode(
														e.target.value as "AUTO" | "LOCAL_MOCK",
													)
												}
											>
												<option value="AUTO">
													Auto (Gateway API &rarr; Webhooks)
												</option>
												<option value="LOCAL_MOCK">
													Offline Client-Side Mock
												</option>
											</select>
										</label>
									</div>
									<button
										className="w-full btn-premium-action cursor-pointer"
										disabled={isSimulating}
										onClick={handleCheckout}
									>
										{isSimulating ? "Sending Request..." : "Pay $1,250,000"}
									</button>
								</div>
							) : (
								<div className="flex flex-col gap-4">
									<div className="flex justify-between items-center">
										<span className="text-sm text-text-secondary">
											Current Clearance Status:
										</span>
										<div
											className={`px-2.5 py-1 rounded-full text-xs font-bold tracking-wider uppercase ${
												orderStatus === "PAYMENT_PROCESSING"
													? "bg-purple-950/40 text-purple-400"
													: orderStatus === "PROCESSING"
														? "bg-amber-950/40 text-amber-400"
														: orderStatus === "APPROVED"
															? "bg-emerald-950/40 text-emerald-400"
															: orderStatus === "HUMAN_TRIAGE"
																? "bg-amber-950/40 text-amber-400"
																: orderStatus === "PAYMENT_FAILED"
																	? "bg-rose-950/40 text-rose-400"
																	: "bg-slate-800 text-text"
											}`}
										>
											{orderStatus}
										</div>
									</div>

									{orderStatus === "PAYMENT_PROCESSING" && (
										<div className="px-4 py-2.5 bg-purple-950/30 text-purple-400 rounded-lg text-xs flex items-center gap-2 font-medium">
											<span className="w-3.5 h-3.5 border-2 border-purple-800 border-t-purple-400 rounded-full animate-spin"></span>
											Processing payment authorization with Stripe Gateway...
										</div>
									)}

									{orderStatus === "PROCESSING" && (
										<div className="px-4 py-2.5 bg-amber-950/30 text-amber-400 rounded-lg text-xs flex items-center gap-2 font-medium">
											<span className="w-2.5 h-2.5 rounded-full bg-amber-500 animate-ping"></span>
											Credit limit exceeds $1M. Invoking n8n AI Engine + LLM
											Credit Score evaluator...
										</div>
									)}

									{orderStatus === "HUMAN_TRIAGE" && (
										<div className="px-4 py-2.5 bg-rose-950/30 text-rose-400 rounded-lg text-xs font-semibold">
											⚠️ Order blocked from automated release. Placed in
											underwriting queue.
										</div>
									)}

									{orderStatus === "APPROVED" && (
										<div className="px-4 py-2.5 bg-emerald-950/30 text-emerald-400 rounded-lg text-xs font-semibold">
											✅ Credit approved. Shipping labels created and sent to
											Cold-Chain Rider team.
										</div>
									)}

									{lastTraceId && (
										<div className="flex items-center justify-between bg-slate-950 border border-border px-3 py-2 rounded-lg text-xs">
											<span className="text-text-muted">
												Active correlationId:
											</span>
											<code className="font-mono text-purple-400 text-[10px]">
												{lastTraceId}
											</code>
											<button
												className="px-2 py-0.5 border border-border bg-slate-900 rounded text-[10px] text-text hover:text-purple-400 cursor-pointer"
												onClick={() => copyToClipboard(lastTraceId, "trace")}
											>
												{copiedIndex === "trace" ? "Copied!" : "Copy"}
											</button>
										</div>
									)}
								</div>
							)}
						</div>

						{/* Sandbox Logs */}
						<div className="flex flex-col">
							<h4 className="m-0 mb-2 text-xs font-bold text-text-muted uppercase tracking-wider">
								Sandbox Simulation Logs
							</h4>
							<div className="logs-output h-[180px] p-3 overflow-y-auto font-mono text-[10px] flex flex-col gap-1.5">
								{simulationLog.length === 0 ? (
									<div className="text-text-muted text-center py-12 italic">
										No logs generated. Click "Pay" to start checkout events.
									</div>
								) : (
									simulationLog.map((log, index) => (
										<div key={index} className="leading-relaxed">
											<span className="text-text-muted mr-2">[{log.time}]</span>
											<span
												className={
													log.type === "success"
														? "text-emerald-400 font-medium"
														: log.type === "warning"
															? "text-amber-400 font-medium"
															: log.type === "error"
																? "text-rose-400 font-medium"
																: "text-text"
												}
											>
												{log.text}
											</span>
										</div>
									))
								)}
							</div>
						</div>
					</div>

					{/* Right Column: Live Push Inbox */}
					<div className="glass-panel max-h-[700px] flex flex-col">
						<div className="flex justify-between items-center mb-1">
							<h3 className="m-0 text-base font-bold text-text-h">
								Live Push Inbox
							</h3>
							<div className="flex items-center gap-2">
								<span className="bg-purple-600 text-white text-[10px] font-bold rounded-full px-2 py-0.5">
									{notifications.length}
								</span>
								{notifications.length > 0 && (
									<button
										className="text-xs text-text-muted hover:text-text-h cursor-pointer"
										onClick={clearNotifications}
									>
										Clear
									</button>
								)}
							</div>
						</div>
						<p className="text-xs text-text-secondary mt-0 mb-4 leading-relaxed">
							Messages received in real-time from the notification-engine via
							Gateway.
						</p>

						<div className="notifications-list flex-1 overflow-y-auto flex flex-col gap-3 pr-1">
							{notifications.length === 0 ? (
								<div className="empty-state flex flex-col items-center justify-center text-center py-20">
									<div className="bell-icon text-3xl mb-3">🔔</div>
									<p className="font-semibold text-sm text-text-h mb-1">
										No active notifications
									</p>
									<span className="text-[10px] text-text-muted">
										Waiting for real-time transactions to trigger events...
									</span>
								</div>
							) : (
								notifications.map((notif, index) => (
									<div
										key={index}
										className={`notification-card flex flex-col gap-1.5 p-3.5 ${
											notif.priority === "HIGH"
												? "priority-high"
												: notif.priority === "MEDIUM"
													? "priority-medium"
													: "priority-low"
										}`}
									>
										<div className="card-row flex justify-between items-center">
											<span className="type-badge text-[9px] font-bold px-2 py-0.5 rounded tracking-wide">
												{notif.type}
											</span>
											<span className="time-ago text-[9px]">
												{new Date(notif.timestamp).toLocaleTimeString()}
											</span>
										</div>
										<p className="notif-desc text-xs m-0 leading-relaxed font-medium">
											{notif.type === "ORDER_EVALUATED" &&
												`AI evaluation complete: ${notif.payload?.ai_status}`}
											{notif.type === "PAYMENT_CONFIRMED" &&
												`Mock payment of $${(notif.payload?.amount || 1250000).toLocaleString()} confirmed`}
											{notif.type === "PAYMENT_FAILED" &&
												`Stripe reported payment failure`}
											{![
												"ORDER_EVALUATED",
												"PAYMENT_CONFIRMED",
												"PAYMENT_FAILED",
											].includes(notif.type) &&
												JSON.stringify(notif.payload || {})}
										</p>
										<div className="trace-row text-[10px] flex items-center gap-1.5 pt-2 mt-1">
											<span className="trace-label">trace:</span>
											<span
												className="trace-id cursor-pointer underline font-mono text-[9px]"
												onClick={() =>
													copyToClipboard(notif.correlationId || "none", index)
												}
											>
												{copiedIndex === index
													? "Copied!"
													: (notif.correlationId || "none").substring(0, 15) +
														"..."}
											</span>
										</div>
									</div>
								))
							)}
						</div>
					</div>
				</div>
			)}

			{activeSubTab === "onboarding" && (
				<div className="grid grid-cols-1 lg:grid-cols-[1fr_360px] gap-6 animate-fade-in text-slate-800 dark:text-slate-200">
					{/* Left Column: Register and Provision */}
					<div className="flex flex-col gap-6">
						{/* Retailer Self-Service Onboarding Form (FR-01) */}
						<div className="upgrade-glow-card p-6 flex flex-col gap-4">
							<div className="flex justify-between items-center border-b border-slate-100 dark:border-slate-800 pb-3">
								<h3 className="m-0 text-base font-bold text-slate-900 dark:text-slate-100 flex items-center gap-2">
									🏢 Retailer Tenant Onboarding Portal
								</h3>
								{currentRetailer && (
									<span
										className={`px-2 py-0.5 rounded text-[10px] font-bold ${
											currentRetailer.status === "ACTIVE"
												? "bg-emerald-950/40 text-emerald-400 border border-emerald-500/20"
												: currentRetailer.status === "PENDING"
													? "bg-amber-950/40 text-amber-400 border border-amber-500/20"
													: "bg-slate-800 text-slate-400"
										}`}
									>
										{currentRetailer.status}
									</span>
								)}
							</div>

							{!currentRetailer ? (
								<form
									onSubmit={handleRegisterRetailer}
									className="flex flex-col gap-4"
								>
									<p className="text-xs text-slate-500 mt-0 leading-relaxed">
										Register your retail convenience-store network hub with
										Swish OS. Self-signup initiates a PENDING onboarding
										application that must pass 3 ops-gated compliance
										validations before activation.
									</p>
									<div className="grid grid-cols-1 md:grid-cols-2 gap-4">
										<div className="flex flex-col gap-1">
											<label className="text-xs font-semibold text-slate-500">
												Retailer Entity Name
											</label>
											<input
												type="text"
												required
												placeholder="e.g. Valora Kiosk HB"
												className="px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-850 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 text-sm focus:outline-none focus:border-indigo-500"
												value={retailerName}
												onChange={(e) => setRetailerName(e.target.value)}
											/>
										</div>
										<div className="flex flex-col gap-1">
											<label className="text-xs font-semibold text-slate-500">
												Corporate Billing Email
											</label>
											<input
												type="email"
												required
												placeholder="ops@valora.ch"
												className="px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-850 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 text-sm focus:outline-none focus:border-indigo-500"
												value={retailerEmail}
												onChange={(e) => setRetailerEmail(e.target.value)}
											/>
										</div>
									</div>
									<div className="grid grid-cols-1 md:grid-cols-2 gap-4">
										<div className="flex flex-col gap-1">
											<label className="text-xs font-semibold text-slate-500">
												Assigned Store Hub ID
											</label>
											<input
												type="text"
												required
												placeholder="store-valora-01"
												className="px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-850 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 text-sm focus:outline-none focus:border-indigo-500"
												value={retailerStoreId}
												onChange={(e) => setRetailerStoreId(e.target.value)}
											/>
										</div>
										<div className="flex flex-col gap-1">
											<label className="text-xs font-semibold text-slate-500">
												Select Subscription Tier
											</label>
											<select
												className="px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-850 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 text-sm focus:outline-none focus:border-indigo-500 cursor-pointer"
												value={billingTier}
												onChange={(e) => setBillingTier(e.target.value as any)}
											>
												<option value="BASIC">
													BASIC ($1,000/mo - IoT Telemetry only)
												</option>
												<option value="PRO">
													PRO ($1,500/mo - Telemetry + Procurement)
												</option>
												<option value="ENTERPRISE">
													ENTERPRISE (SLA Guarantees + Audits)
												</option>
											</select>
										</div>
									</div>
									<button
										type="submit"
										disabled={isRegistering}
										className="w-full mt-2 py-2.5 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg cursor-pointer transition shadow"
									>
										{isRegistering
											? "Registering Tenant..."
											: "Register Retailer (Self-Service)"}
									</button>
								</form>
							) : (
								<div className="flex flex-col gap-4 text-sm">
									<div className="bg-slate-50 dark:bg-slate-800/40 border border-slate-200 dark:border-slate-800/60 rounded-xl p-4 flex flex-col gap-2">
										<div className="flex justify-between text-xs">
											<span className="text-slate-500">Retailer tenantId:</span>
											<strong className="font-mono text-slate-800 dark:text-slate-200">
												{currentRetailer.retailerId}
											</strong>
										</div>
										<div className="flex justify-between text-xs">
											<span className="text-slate-500">Corporate Name:</span>
											<span className="font-medium">
												{currentRetailer.name}
											</span>
										</div>
										<div className="flex justify-between text-xs">
											<span className="text-slate-500">
												Provisioned Store Hub:
											</span>
											<span className="font-mono text-slate-700 dark:text-slate-300">
												{currentRetailer.storeId}
											</span>
										</div>
										<div className="flex justify-between text-xs">
											<span className="text-slate-500">Subscription Tier:</span>
											<span className="font-semibold text-indigo-600 dark:text-indigo-400">
												{currentRetailer.tier}
											</span>
										</div>
										{currentRetailer.billingAccountId && (
											<div className="flex justify-between border-t border-slate-200 dark:border-slate-800/60 pt-2 mt-1 text-xs">
												<span className="text-slate-500">
													Billing Account ID:
												</span>
												<span className="font-mono text-slate-800 dark:text-slate-200">
													{currentRetailer.billingAccountId}
												</span>
											</div>
										)}
									</div>

									{/* Onboarding 3-Gate Approval Checks (FR-01) */}
									<div className="border border-slate-200 dark:border-slate-800 rounded-xl p-4 flex flex-col gap-3">
										<h4 className="m-0 text-xs font-bold text-slate-900 dark:text-slate-100 uppercase tracking-wider">
											Administrative Onboarding Gates
										</h4>
										<p className="text-[10px] text-slate-400 m-0 leading-relaxed">
											In accordance with corporate governance protocols, new
											retailers are reviewed across three validation
											checkpoints. Activating the final Admin Gate activates
											database records and triggers billing accounts.
										</p>
										<div className="grid grid-cols-1 sm:grid-cols-3 gap-2 mt-1">
											{/* Gate 1: Ops */}
											<div
												className={`flex flex-col items-center justify-center p-3.5 rounded-lg border text-center transition ${
													currentRetailer.approvalOps
														? "bg-emerald-950/20 border-emerald-800 text-emerald-400"
														: "bg-slate-50 dark:bg-slate-900/60 border-slate-200 dark:border-slate-800 text-slate-400"
												}`}
											>
												<span className="text-xs font-bold block mb-1.5">
													Ops Vetting
												</span>
												{currentRetailer.approvalOps ? (
													<span className="text-[10px] font-semibold">
														Approved ✅
													</span>
												) : (
													<button
														className="px-2.5 py-1 text-[10px] font-semibold text-indigo-400 hover:text-indigo-300 border border-indigo-500/30 hover:border-indigo-400/60 rounded bg-indigo-500/10 cursor-pointer"
														onClick={() => handleApproveOnboardingGate("ops")}
													>
														Approve
													</button>
												)}
											</div>

											{/* Gate 2: Compliance */}
											<div
												className={`flex flex-col items-center justify-center p-3.5 rounded-lg border text-center transition ${
													currentRetailer.approvalCompliance
														? "bg-emerald-950/20 border-emerald-800 text-emerald-400"
														: "bg-slate-50 dark:bg-slate-900/60 border-slate-200 dark:border-slate-800 text-slate-400"
												}`}
											>
												<span className="text-xs font-bold block mb-1.5">
													Compliance
												</span>
												{currentRetailer.approvalCompliance ? (
													<span className="text-[10px] font-semibold">
														Approved ✅
													</span>
												) : (
													<button
														className="px-2.5 py-1 text-[10px] font-semibold text-indigo-400 hover:text-indigo-300 border border-indigo-500/30 hover:border-indigo-400/60 rounded bg-indigo-500/10 cursor-pointer"
														onClick={() =>
															handleApproveOnboardingGate("compliance")
														}
													>
														Approve
													</button>
												)}
											</div>

											{/* Gate 3: Admin */}
											<div
												className={`flex flex-col items-center justify-center p-3.5 rounded-lg border text-center transition ${
													currentRetailer.approvalAdmin
														? "bg-emerald-950/20 border-emerald-800 text-emerald-400"
														: "bg-slate-50 dark:bg-slate-900/60 border-slate-200 dark:border-slate-800 text-slate-400"
												}`}
											>
												<span className="text-xs font-bold block mb-1.5">
													Admin Gate
												</span>
												{currentRetailer.approvalAdmin ? (
													<span className="text-[10px] font-semibold">
														Approved ✅
													</span>
												) : (
													<button
														className="px-2.5 py-1 text-[10px] font-semibold text-indigo-400 hover:text-indigo-300 border border-indigo-500/30 hover:border-indigo-400/60 rounded bg-indigo-500/10 cursor-pointer"
														onClick={() => handleApproveOnboardingGate("admin")}
													>
														Approve
													</button>
												)}
											</div>
										</div>

										{/* Revealed API Key (Shown Once) */}
										{revealedApiKey && (
											<div className="bg-slate-950 border border-indigo-850/60 rounded-xl p-3.5 mt-2 flex flex-col gap-2 relative overflow-hidden">
												<div className="absolute top-0 right-0 w-24 h-24 bg-indigo-500/5 rounded-full filter blur-md"></div>
												<span className="text-xs font-bold text-indigo-400 flex items-center gap-1.5">
													🔑 Secure API Authorization Key
												</span>
												<p className="text-[10px] text-slate-400 m-0 leading-relaxed">
													This key will be hashed downstream. Record it securely
													to establish connection adapters.
												</p>
												<div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 mt-1 bg-slate-900 border border-slate-800 px-3 py-2.5 rounded-lg text-xs font-mono text-indigo-300">
													<code className="break-all flex-1 select-all">{revealedApiKey}</code>
													<button
														className="sm:ml-auto px-3 py-1 border border-slate-700 bg-slate-800 rounded text-[10px] text-slate-200 hover:text-indigo-400 hover:border-indigo-400/30 cursor-pointer whitespace-nowrap self-start sm:self-auto"
														onClick={() =>
															copyToClipboard(revealedApiKey, "api-key")
														}
													>
														{copiedIndex === "api-key" ? "Copied!" : "Copy Key"}
													</button>
												</div>
											</div>
										)}
									</div>

									<button
										className="text-xs text-slate-400 hover:text-slate-900 dark:hover:text-slate-100 cursor-pointer self-end underline"
										onClick={() => {
											setCurrentRetailer(null);
											setRevealedApiKey(null);
										}}
									>
										Reset & Onboard New Retailer
									</button>
								</div>
							)}
						</div>

						{/* IoT Device / Sensor Provisioning (FR-01 Device provisioning) */}
						{currentRetailer && currentRetailer.status === "ACTIVE" && (
							<div className="upgrade-glow-card p-6 flex flex-col gap-4">
								<h3 className="m-0 text-base font-bold text-slate-900 dark:text-slate-100 flex items-center gap-2 border-b border-slate-100 dark:border-slate-800 pb-3">
									📡 IoT Sensor Device Provisioning
								</h3>

								<form
									onSubmit={handleProvisionSensor}
									className="flex flex-col sm:flex-row items-end gap-4"
								>
									<div className="flex flex-col gap-1 flex-1">
										<label className="text-xs font-semibold text-slate-500">
											Provision Device Type
										</label>
										<select
											className="px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 text-sm focus:outline-none focus:border-indigo-500 cursor-pointer"
											value={sensorType}
											onChange={(e) => setSensorType(e.target.value as any)}
										>
											<option value="TEMPERATURE">
												Cold-chain Temperature Sensor (TimescaleDB)
											</option>
											<option value="HUMIDITY">
												Warehouse Ambient Humidity Sensor (RH%)
											</option>
											<option value="GPS">
												Rider Fleet GPS Coordinate Tracker
											</option>
										</select>
									</div>
									<button
										type="submit"
										disabled={isProvisioning}
										className="py-2 px-5 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg cursor-pointer transition shadow whitespace-nowrap h-[38px]"
									>
										{isProvisioning ? "Provisioning..." : "Provision Device"}
									</button>
								</form>

								{/* Provisioned Devices List */}
								<div className="flex flex-col mt-2">
									<h4 className="m-0 mb-3 text-xs font-bold text-slate-900 dark:text-slate-100 uppercase tracking-wider">
										Active Store Hub Sensors ({sensorsList.length})
									</h4>
									{sensorsList.length === 0 ? (
										<div className="text-center py-8 text-xs text-slate-400 italic bg-slate-50 dark:bg-slate-800/20 border border-dashed border-slate-200 dark:border-slate-800 rounded-xl">
											No active sensors provisioned for store hub{" "}
											{currentRetailer.storeId} yet.
										</div>
									) : (
										<div className="flex flex-col gap-3">
											{sensorsList.map((sensor) => (
												<div
													key={sensor.sensorId}
													className="bg-slate-50 dark:bg-slate-850/40 border border-slate-200 dark:border-slate-800 rounded-xl p-4 flex flex-col gap-2"
												>
													<div className="flex justify-between items-start">
														<div className="flex flex-col gap-0.5">
															<span className="text-xs font-bold text-slate-900 dark:text-slate-100 flex items-center gap-1.5">
																📟 {sensor.sensorId}
																<span className="text-[10px] px-1.5 py-0.5 bg-indigo-950/40 text-indigo-400 rounded-full font-semibold font-mono">
																	{sensor.sensorType}
																</span>
															</span>
															<span className="text-[10px] text-slate-400 font-mono">
																Hub Owner: {sensor.retailerId} | Store:{" "}
																{sensor.storeId}
															</span>
														</div>
														<div className="flex items-center gap-2">
															<span className="w-2 h-2 rounded-full bg-emerald-500 inline-block shadow-[0_0_6px_rgba(16,185,129,0.4)]"></span>
															<span className="text-xs font-medium text-emerald-400 capitalize">
																{sensor.status.toLowerCase()}
															</span>
														</div>
													</div>

													<div className="flex flex-col sm:flex-row justify-between items-start sm:items-center border-t border-slate-200/50 dark:border-slate-800/40 pt-2 mt-1 gap-2 text-xs">
														<div className="flex flex-col gap-0.5">
															<span className="text-slate-500 text-[9px] uppercase tracking-wider">
																Last Calibration
															</span>
															<span className="font-semibold text-slate-700 dark:text-slate-300">
																{sensor.lastCalibratedAt
																	? new Date(
																			sensor.lastCalibratedAt,
																		).toLocaleString()
																	: "Never"}
															</span>
														</div>

														<div className="flex gap-2 self-end">
															<button
																className="px-2.5 py-1 text-[10px] font-semibold text-indigo-400 border border-indigo-500/20 hover:border-indigo-400/40 bg-indigo-500/5 rounded hover:bg-indigo-500/10 cursor-pointer"
																onClick={() =>
																	handleCalibrateSensor(sensor.sensorId)
																}
															>
																Calibrate
															</button>
															<button
																className="px-2.5 py-1 text-[10px] font-semibold text-emerald-400 border border-emerald-500/20 hover:border-emerald-400/40 bg-emerald-500/5 rounded hover:bg-emerald-500/10 cursor-pointer"
																onClick={() =>
																	handleVerifySensorIntegrity(sensor.sensorId)
																}
															>
																Verify Chain
															</button>
														</div>
													</div>

													{/* Calibration and integrity status tags */}
													<div className="flex gap-4 mt-1 border-t border-slate-200/20 dark:border-slate-800/20 pt-1.5 text-[9px] font-mono text-slate-500">
														<span>
															Calibration status:{" "}
															<strong
																className={
																	sensor.calibrationStatus === "SUCCESS"
																		? "text-emerald-400 font-bold"
																		: "text-amber-400 font-bold"
																}
															>
																{sensor.calibrationStatus}
															</strong>
														</span>
														<span>
															SHA-256 chain integrity:{" "}
															<strong
																className={
																	sensor.integrityValid
																		? "text-emerald-400 font-bold"
																		: "text-rose-400 font-bold"
																}
															>
																{sensor.integrityValid
																	? "VALID (SECURE)"
																	: "INVALID"}
															</strong>
														</span>
													</div>
												</div>
											))}
										</div>
									)}
								</div>
							</div>
						)}
					</div>

					{/* Right Column: Console Logs */}
					<div className="flex flex-col gap-6">
						<div className="upgrade-glow-card p-6 flex flex-col h-[500px]">
							<h3 className="m-0 text-base font-bold text-slate-900 dark:text-slate-100 mb-2 border-b border-slate-100 dark:border-slate-800 pb-3">
								Sandbox Logs
							</h3>
							<div className="logs-output flex-1 p-3 overflow-y-auto font-mono text-[10px] flex flex-col gap-1.5">
								{simulationLog.length === 0 ? (
									<div className="text-text-muted text-center py-20 italic">
										No sandbox logs generated yet.
									</div>
								) : (
									simulationLog.map((log, index) => (
										<div key={index} className="leading-relaxed">
											<span className="text-text-muted mr-2">[{log.time}]</span>
											<span
												className={
													log.type === "success"
														? "text-emerald-400 font-medium"
														: log.type === "warning"
															? "text-amber-400 font-medium"
															: log.type === "error"
																? "text-rose-400 font-medium"
																: "text-text"
												}
											>
												{log.text}
											</span>
										</div>
									))
								)}
							</div>
						</div>
					</div>
				</div>
			)}
		</div>
	);
};

export default B2bDashboard;
