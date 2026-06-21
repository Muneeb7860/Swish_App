import type React from "react";
import CreditCardMockup from "./CreditCardMockup";
import OrderTimeline from "./OrderTimeline";

interface CheckoutPanelProps {
	orderId: string;
	orderStatus: string;
	userId: string;
	lastTraceId: string | null;
	isSimulating: boolean;
	simulationMode: "AUTO" | "LOCAL_MOCK";
	onSimulationModeChange: (mode: "AUTO" | "LOCAL_MOCK") => void;
	onCheckout: () => void;
	onResetOrder: () => void;
	copiedIndex: number | string | null;
	onCopy: (text: string, index: number | string) => void;
}

/**
 * Wholesale order checkout panel.
 * Displays order details, timeline, credit card, payment controls, and status banners.
 */
const CheckoutPanel: React.FC<CheckoutPanelProps> = ({
	orderId,
	orderStatus,
	userId,
	lastTraceId,
	isSimulating,
	simulationMode,
	onSimulationModeChange,
	onCheckout,
	onResetOrder,
	copiedIndex,
	onCopy,
}) => {
	return (
		<div className="upgrade-glow-card p-6 flex flex-col">
			{/* Header */}
			<div className="flex justify-between items-center mb-2">
				<h3
					className="m-0"
					style={{ fontSize: "var(--text-lg)", fontWeight: 700 }}
				>
					Wholesale Order Checkout
				</h3>
				{orderStatus !== "PENDING" && (
					<button className="btn-ghost" onClick={onResetOrder} type="button">
						🔄 Reset Sandbox
					</button>
				)}
			</div>

			{/* Order Summary Card */}
			<div
				className="rounded-xl p-4 my-4 flex flex-col gap-2.5"
				style={{
					background: "var(--bg-glass)",
					border: "1px solid var(--border-default)",
				}}
			>
				<div
					className="flex justify-between text-xs"
					style={{ color: "var(--text-muted)" }}
				>
					<span>Order Number:</span>
					<strong
						className="font-mono"
						style={{ color: "var(--text-primary)" }}
					>
						{orderId}
					</strong>
				</div>
				<div
					className="flex justify-between text-xs"
					style={{ color: "var(--text-muted)" }}
				>
					<span>Order Total:</span>
					<strong
						className="text-sm font-bold"
						style={{ color: "var(--accent-hover)" }}
					>
						$1,250,000.00 USD
					</strong>
				</div>
				<div
					className="flex justify-between text-xs"
					style={{ color: "var(--text-muted)" }}
				>
					<span>Customer ID:</span>
					<span className="font-mono" style={{ color: "var(--text-primary)" }}>
						{userId}
					</span>
				</div>
			</div>

			{/* Timeline */}
			<OrderTimeline orderStatus={orderStatus} />

			{/* Interactive Actions */}
			<div className="glass-panel p-5 mb-5">
				{orderStatus === "PENDING" ? (
					<div className="flex flex-col">
						<h4
							className="m-0 mb-3"
							style={{
								fontSize: "var(--text-xs)",
								color: "var(--text-muted)",
								letterSpacing: "var(--tracking-wider)",
							}}
						>
							SECURE CREDIT CARD INPUT (STRIPE SIMULATOR)
						</h4>

						<CreditCardMockup />

						<div className="mb-4">
							<label
								className="text-xs font-semibold flex items-center gap-2"
								style={{ color: "var(--text-secondary)" }}
							>
								Simulation Mode:
								<select
									className="select-field"
									style={{ width: "auto", minWidth: 220 }}
									value={simulationMode}
									onChange={(e) =>
										onSimulationModeChange(
											e.target.value as "AUTO" | "LOCAL_MOCK",
										)
									}
								>
									<option value="AUTO">Auto (Gateway API → Webhooks)</option>
									<option value="LOCAL_MOCK">Offline Client-Side Mock</option>
								</select>
							</label>
						</div>

						<button
							className="w-full btn-premium-action"
							disabled={isSimulating}
							onClick={onCheckout}
							type="button"
						>
							{isSimulating ? (
								<>
									<span className="spinner" /> Sending Request...
								</>
							) : (
								"Pay $1,250,000"
							)}
						</button>
					</div>
				) : (
					<div className="flex flex-col gap-4">
						<div className="flex justify-between items-center">
							<span
								className="text-sm"
								style={{ color: "var(--text-secondary)" }}
							>
								Current Clearance Status:
							</span>
							<OrderStatusBadge status={orderStatus} />
						</div>

						{orderStatus === "PAYMENT_PROCESSING" && (
							<div className="status-banner status-banner--processing">
								<span className="spinner" />
								Processing payment authorization with Stripe Gateway...
							</div>
						)}

						{orderStatus === "PROCESSING" && (
							<div className="status-banner status-banner--warning">
								<span
									className="w-2.5 h-2.5 rounded-full animate-ping"
									style={{ background: "var(--warning)" }}
								/>
								Credit limit exceeds $1M. Invoking n8n AI Engine + LLM Credit
								Score evaluator...
							</div>
						)}

						{orderStatus === "HUMAN_TRIAGE" && (
							<div className="status-banner status-banner--error font-semibold">
								⚠️ Order blocked from automated release. Placed in underwriting
								queue.
							</div>
						)}

						{orderStatus === "APPROVED" && (
							<div className="status-banner status-banner--success font-semibold">
								✅ Credit approved. Shipping labels created and sent to
								Cold-Chain Rider team.
							</div>
						)}

						{lastTraceId && (
							<div
								className="flex items-center justify-between px-3 py-2 rounded-lg text-xs"
								style={{
									background: "var(--bg-root)",
									border: "1px solid var(--border-default)",
								}}
							>
								<span style={{ color: "var(--text-muted)" }}>
									Active correlationId:
								</span>
								<code
									className="font-mono"
									style={{ color: "var(--purple)", fontSize: "10px" }}
								>
									{lastTraceId}
								</code>
								<button
									className="btn-ghost"
									style={{ fontSize: "10px" }}
									onClick={() => onCopy(lastTraceId, "trace")}
									type="button"
								>
									{copiedIndex === "trace" ? "Copied!" : "Copy"}
								</button>
							</div>
						)}
					</div>
				)}
			</div>
		</div>
	);
};

function OrderStatusBadge({ status }: { status: string }) {
	const classMap: Record<string, string> = {
		PAYMENT_PROCESSING: "order-badge--payment",
		PROCESSING: "order-badge--processing",
		APPROVED: "order-badge--approved",
		HUMAN_TRIAGE: "order-badge--triage",
		PAYMENT_FAILED: "order-badge--failed",
	};

	return (
		<div
			className={`order-badge ${classMap[status] || "order-badge--pending"}`}
		>
			{status}
		</div>
	);
}

export default CheckoutPanel;
