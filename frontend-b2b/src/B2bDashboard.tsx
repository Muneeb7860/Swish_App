import type React from "react";
import { useCallback, useState } from "react";
import CheckoutPanel from "./components/CheckoutPanel";
import ConnectionConfig from "./components/ConnectionConfig";
import NotificationInbox from "./components/NotificationInbox";
import RetailerOnboarding from "./components/RetailerOnboarding";
import SandboxLogs from "./components/SandboxLogs";
import SensorProvisioning from "./components/SensorProvisioning";
import StatusIndicator from "./components/StatusIndicator";
import { useOrderSimulation } from "./hooks/useOrderSimulation";
import { useRetailerApi } from "./hooks/useRetailerApi";
import { useResilientWebSocket } from "./useResilientWebSocket";
import { deriveWsUrl } from "./utils/api";

interface LogEntry {
	text: string;
	type: "info" | "success" | "warning" | "error";
	time: string;
}

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

/**
 * B2B Wholesale Portal — Main Dashboard
 *
 * Orchestrates the wholesale order checkout flow and retailer onboarding experience.
 * Composes focused sub-components for a clean, modular architecture.
 */
const B2bDashboard: React.FC = () => {
	// ── Connection Settings ──
	const [gatewayUrl, setGatewayUrl] = useState("http://localhost:8080");
	const [userId, setUserId] = useState("b2b-customer-123");
	const [accessToken, setAccessToken] = useState(
		() => localStorage.getItem("jwt_token") || "mock_token_for_now",
	);
	const [isConfigOpen, setIsConfigOpen] = useState(false);

	// ── Navigation ──
	const [activeSubTab, setActiveSubTab] = useState<"wholesale" | "onboarding">(
		"wholesale",
	);

	// ── Shared State ──
	const [simulationLog, setSimulationLog] = useState<LogEntry[]>([]);
	const [copiedIndex, setCopiedIndex] = useState<number | string | null>(null);

	// ── Utilities ──
	const addLog = useCallback(
		(message: string, type: LogEntry["type"] = "info") => {
			setSimulationLog((prev) =>
				[
					{ text: message, type, time: new Date().toLocaleTimeString() },
					...prev,
				].slice(0, 20),
			);
		},
		[],
	);

	const copyToClipboard = useCallback(
		(text: string, index: number | string) => {
			navigator.clipboard.writeText(text);
			setCopiedIndex(index);
			setTimeout(() => setCopiedIndex(null), 1500);
		},
		[],
	);

	// ── Custom Hooks ──
	const orderSim = useOrderSimulation({
		gatewayUrl,
		accessToken,
		userId,
		addLog,
	});

	const retailerApi = useRetailerApi({
		gatewayUrl,
		accessToken,
		addLog,
	});

	// ── WebSocket Message Handler ──
	const handleWsMessage = useCallback(
		(envelope: NotificationEnvelope) => {
			console.log("[B2bDashboard] Received message envelope:", envelope);

			if (envelope.correlationId) {
				orderSim.setLastTraceId(envelope.correlationId);
				window.dispatchEvent(
					new CustomEvent("ws-packet-received", {
						detail: { ...envelope, source: "B2bDashboard" },
					}),
				);
			}

			const payload = envelope.payload || {};

			switch (envelope.type) {
				case "ORDER_EVALUATED":
					addLog(
						`AI credit evaluation completed: ${payload.ai_status || "UNKNOWN"}`,
						"success",
					);
					orderSim.setOrderStatus(payload.ai_status || "UNKNOWN");
					break;
				case "PAYMENT_CONFIRMED":
					addLog(
						"Payment confirmed. Order sent to AI credit check.",
						"success",
					);
					orderSim.setOrderStatus("PROCESSING");
					break;
				case "PAYMENT_FAILED":
					addLog("Payment failed. Transaction aborted.", "error");
					orderSim.setOrderStatus("PAYMENT_FAILED");
					break;
				default:
					if (payload.ai_status) {
						orderSim.setOrderStatus(payload.ai_status);
					}
			}
		},
		[addLog, orderSim],
	);

	// ── WebSocket Connection ──
	const wsUrl = deriveWsUrl(gatewayUrl);
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

	// ── Tab Switch with View Transitions ──
	const handleTabSwitch = useCallback(
		(tab: "wholesale" | "onboarding") => {
			if (tab === activeSubTab) return;

			if ((document as any).startViewTransition) {
				(document as any).startViewTransition({
					update: () => setActiveSubTab(tab),
				});
			} else {
				setActiveSubTab(tab);
			}
		},
		[activeSubTab],
	);

	return (
		<div className="b2b-mfe-container flex flex-col gap-6 animate-fade-in">
			{/* ━━━ Top Bar ━━━ */}
			<header
				className="flex flex-col sm:flex-row justify-between items-start sm:items-center pb-5 gap-4"
				style={{ borderBottom: "1px solid var(--border-default)" }}
			>
				<div className="flex flex-col">
					<h2 className="m-0" style={{ fontSize: "var(--text-xl)" }}>
						B2B Wholesale Portal
					</h2>
					<p
						className="m-0 mt-1 text-sm"
						style={{ color: "var(--text-muted)" }}
					>
						High-value order clearing & real-time credit underwriting
					</p>
				</div>
				<div className="flex items-center gap-4">
					<button
						className={`btn-ghost ${isConfigOpen ? "tab-btn--active" : ""}`}
						style={
							isConfigOpen
								? {
										borderColor: "rgba(99, 102, 241, 0.3)",
										color: "var(--accent-hover)",
										background: "var(--accent-muted)",
									}
								: {}
						}
						onClick={() => setIsConfigOpen(!isConfigOpen)}
						type="button"
						aria-expanded={isConfigOpen}
						aria-controls="socket-config"
					>
						⚙️ Configure Socket
					</button>
					<StatusIndicator
						status={wsStatus}
						reconnectAttempts={reconnectAttempts}
					/>
				</div>
			</header>

			{/* ━━━ Socket Config Drawer ━━━ */}
			<ConnectionConfig
				isOpen={isConfigOpen}
				gatewayUrl={gatewayUrl}
				onGatewayUrlChange={setGatewayUrl}
				userId={userId}
				onUserIdChange={setUserId}
				accessToken={accessToken}
				onAccessTokenChange={setAccessToken}
				onReconnect={reconnect}
				onDisconnect={disconnect}
			/>

			{/* ━━━ Tab Navigation ━━━ */}
			<div
				className="tab-nav mb-4"
				role="tablist"
				aria-label="Dashboard sections"
			>
				<button
					className={`tab-btn ${activeSubTab === "wholesale" ? "tab-btn--active" : ""}`}
					onClick={() => handleTabSwitch("wholesale")}
					role="tab"
					aria-selected={activeSubTab === "wholesale"}
					type="button"
				>
					📦 Wholesale Orders Desk
				</button>
				<button
					className={`tab-btn ${activeSubTab === "onboarding" ? "tab-btn--active" : ""}`}
					onClick={() => handleTabSwitch("onboarding")}
					role="tab"
					aria-selected={activeSubTab === "onboarding"}
					type="button"
				>
					🏢 Retailer Onboarding & IoT Desk
				</button>
			</div>

			{/* ━━━ Wholesale Orders Tab ━━━ */}
			{activeSubTab === "wholesale" && (
				<div
					className="grid grid-cols-1 lg:grid-cols-[1fr_340px] gap-6 animate-fade-in"
					role="tabpanel"
				>
					{/* Left: Order Flow */}
					<div className="flex flex-col gap-6">
						<CheckoutPanel
							orderId={orderSim.orderId}
							orderStatus={orderSim.orderStatus}
							userId={userId}
							lastTraceId={orderSim.lastTraceId}
							isSimulating={orderSim.isSimulating}
							simulationMode={orderSim.simulationMode}
							onSimulationModeChange={orderSim.setSimulationMode}
							onCheckout={orderSim.handleCheckout}
							onResetOrder={orderSim.resetOrder}
							copiedIndex={copiedIndex}
							onCopy={copyToClipboard}
						/>

						<SandboxLogs logs={simulationLog} />
					</div>

					{/* Right: Live Push Inbox */}
					<NotificationInbox
						notifications={notifications}
						onClear={clearNotifications}
						copiedIndex={copiedIndex}
						onCopy={copyToClipboard}
					/>
				</div>
			)}

			{/* ━━━ Retailer Onboarding Tab ━━━ */}
			{activeSubTab === "onboarding" && (
				<div
					className="grid grid-cols-1 lg:grid-cols-[1fr_360px] gap-6 animate-fade-in"
					role="tabpanel"
				>
					{/* Left: Register and Provision */}
					<div className="flex flex-col gap-6">
						<RetailerOnboarding
							retailerName={retailerApi.retailerName}
							onRetailerNameChange={retailerApi.setRetailerName}
							retailerEmail={retailerApi.retailerEmail}
							onRetailerEmailChange={retailerApi.setRetailerEmail}
							retailerStoreId={retailerApi.retailerStoreId}
							onRetailerStoreIdChange={retailerApi.setRetailerStoreId}
							billingTier={retailerApi.billingTier}
							onBillingTierChange={retailerApi.setBillingTier}
							currentRetailer={retailerApi.currentRetailer}
							revealedApiKey={retailerApi.revealedApiKey}
							isRegistering={retailerApi.isRegistering}
							onRegister={retailerApi.handleRegisterRetailer}
							onApproveGate={retailerApi.handleApproveOnboardingGate}
							onReset={retailerApi.resetRetailer}
							copiedIndex={copiedIndex}
							onCopy={copyToClipboard}
						/>

						<SensorProvisioning
							currentRetailer={retailerApi.currentRetailer}
							sensorType={retailerApi.sensorType}
							onSensorTypeChange={retailerApi.setSensorType}
							sensorsList={retailerApi.sensorsList}
							isProvisioning={retailerApi.isProvisioning}
							onProvision={retailerApi.handleProvisionSensor}
							onCalibrate={retailerApi.handleCalibrateSensor}
							onVerifyIntegrity={retailerApi.handleVerifySensorIntegrity}
						/>
					</div>

					{/* Right: Console Logs */}
					<div className="flex flex-col gap-6">
						<div className="upgrade-glow-card p-6 flex flex-col h-[500px]">
							<h3
								className="m-0 mb-2 pb-3"
								style={{
									fontSize: "var(--text-lg)",
									fontWeight: 700,
									borderBottom: "1px solid var(--border-default)",
								}}
							>
								Sandbox Logs
							</h3>
							<SandboxLogs
								logs={simulationLog}
								maxHeight="100%"
								emptyMessage="No sandbox logs generated yet."
							/>
						</div>
					</div>
				</div>
			)}
		</div>
	);
};

export default B2bDashboard;
