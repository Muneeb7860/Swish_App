import * as Lucide from "lucide-react";
import { useState } from "react";
import { API_BASE } from "../api/governance"; (feat(pricing): support dynamic chaos environmental variables in FMCG catalog imports and pricing)

interface OnboardingApp {
	id: string;
	name: string;
	type: string;
	approvals: { l1: boolean; l2: boolean; l3: boolean };
}

interface HitlTicket {
	id: string;
	type: string;
	amount: number;
	desc: string;
	source?: string;
}

interface AdminPanelProps {
	coldChainBreakdownActive: boolean;
	setColdChainBreakdownActive: (v: boolean) => void;
	wholesalerOutageActive: boolean;
	setWholesalerOutageActive: (v: boolean) => void;
	paymentOutageActive: boolean;
	setPaymentOutageActive: (v: boolean) => void;
	redisCrashActive: boolean;
	setRedisCrashActive: (v: boolean) => void;
	dbLatencyActive: boolean;
	setDbLatencyActive: (v: boolean) => void;
	riderTrafficActive: boolean;
	setRiderTrafficActive: (v: boolean) => void;
	simulateTelemetryFraud: boolean;
	setSimulateTelemetryFraud: (v: boolean) => void;
	onboardingQueue: OnboardingApp[];
	handleApproveOnboard: (id: string, level: "l1" | "l2" | "l3") => void;
	hitlQueue: HitlTicket[];
	handleReleaseHitl: (ticket: HitlTicket) => void;
	handleVoidHitl: (ticket: HitlTicket) => void;
	handleAdjustHitl: (ticket: HitlTicket, newPrice: number) => void;
	hitlLoading?: boolean;
	token?: string | null;
	refreshCatalog?: () => void;
}

export default function AdminPanel({
	coldChainBreakdownActive,
	setColdChainBreakdownActive,
	wholesalerOutageActive,
	setWholesalerOutageActive,
	paymentOutageActive,
	setPaymentOutageActive,
	redisCrashActive,
	setRedisCrashActive,
	dbLatencyActive,
	setDbLatencyActive,
	riderTrafficActive,
	setRiderTrafficActive,
	simulateTelemetryFraud,
	setSimulateTelemetryFraud,
	onboardingQueue,
	handleApproveOnboard,
	hitlQueue,
	handleReleaseHitl,
	handleVoidHitl,
	handleAdjustHitl,
	hitlLoading = false,
token = null,
	refreshCatalog = () => {}, (feat(pricing): support dynamic chaos environmental variables in FMCG catalog imports and pricing)
}: AdminPanelProps) {
	// Local state for the inline "Adjust Bid" price input. Keyed by ticket id.
	const [adjustPrices, setAdjustPrices] = useState<Record<string, string>>({});
	const [adjustOpen, setAdjustOpen] = useState<Record<string, boolean>>({});

// FMCG Catalog Import state
	const [importStatus, setImportStatus] = useState<
		"idle" | "loading" | "success" | "error"
	>("idle");
	const [importResults, setImportResults] = useState<any[]>([]);
	const [importError, setImportError] = useState<string | null>(null);

	const handleImportFmcg = async () => {
		setImportStatus("loading");
		setImportError(null);
		try {
			const params = new URLSearchParams();
			if (riderTrafficActive) {
				params.append("riderToOrderRatio", "0.4");
			} else {
				params.append("riderToOrderRatio", "1.5");
			}
			if (coldChainBreakdownActive) {
				params.append("isRaining", "true");
			} else {
				params.append("isRaining", "false");
			}

			const query = params.toString() ? `?${params.toString()}` : "";
			const res = await fetch(`${API_BASE}/api/v1/products/import-fmcg${query}`, {
				method: "POST",
				headers: {
					Authorization: token ? `Bearer ${token}` : "",
				},
			});
			if (!res.ok) {
				throw new Error(`Import failed: HTTP ${res.status}`);
			}
			const data = await res.json();
			setImportResults(data);
			setImportStatus("success");
			if (refreshCatalog) {
				refreshCatalog();
			}
		} catch (err: any) {
			console.error("Failed to import FMCG products:", err);
			setImportError(err.message || "Failed to import products");
			setImportStatus("error");
		}
	}; (feat(pricing): support dynamic chaos environmental variables in FMCG catalog imports and pricing)
	return (
		<div
			className="admin-dashboard"
			style={{ display: "flex", gap: "1.25rem" }}
		>
			{/* Chaos Panel */}
			<div
				style={{
					flex: 1.2,
					display: "flex",
					flexDirection: "column",
					gap: "1.25rem",
				}}
			>
				<div
					className="glass-card"
					style={{
						padding: "1.25rem",
						borderLeft: "3px solid var(--color-admin)",
					}}
				>
					<h3
						style={{
							fontWeight: 800,
							color: "var(--color-admin)",
							display: "flex",
							alignItems: "center",
							gap: "0.4rem",
							marginBottom: "0.5rem",
						}}
					>
						<Lucide.Flame size={18} />
						Chaos Engineering Control Desk
					</h3>
					<p
						style={{
							fontSize: "0.7rem",
							color: "var(--text-muted)",
							marginBottom: "1rem",
						}}
					>
						Inject database latency spikes, telemetry geofencing mismatches,
						cold chain warming anomalies, wholesaler fallbacks, or gateway
						outages.
					</p>

					<div className="chaos-switches-container">
						{/* Cold Chain Breakdown Switch */}
						<div className="chaos-switch-row">
							<div className="chaos-switch-info">
								<span className="chaos-switch-label">
									Simulate Perishable Cold Chain Outage
								</span>
								<div className="chaos-switch-desc">
									Cargo container warms up by +1.8°C/s in transit.
								</div>
							</div>
							<div>
								<input
									id="switch-cold-chain"
									type="checkbox"
									className="switch-input"
									checked={coldChainBreakdownActive}
									onChange={(e) =>
										setColdChainBreakdownActive(e.target.checked)
									}
								/>
{/* biome-ignore lint/a11y/noLabelWithoutControl: styled toggle switch */} (feat(pricing): support dynamic chaos environmental variables in FMCG catalog imports and pricing)
								<label
									htmlFor="switch-cold-chain"
									className="switch-label"
								></label>
							</div>
						</div>

						{/* Wholesaler Outage Switch */}
						<div className="chaos-switch-row">
							<div className="chaos-switch-info">
								<span className="chaos-switch-label">
									Simulate Primary Wholesaler Supplier Outage
								</span>
								<div className="chaos-switch-desc">
									B2B restocks route to Secondary supplier ($35 surcharge, -20
									trust).
								</div>
							</div>
							<div>
								<input
									id="switch-wholesaler-outage"
									type="checkbox"
									className="switch-input"
									checked={wholesalerOutageActive}
									onChange={(e) => setWholesalerOutageActive(e.target.checked)}
								/>
{/* biome-ignore lint/a11y/noLabelWithoutControl: styled toggle switch */} (feat(pricing): support dynamic chaos environmental variables in FMCG catalog imports and pricing)
								<label
									htmlFor="switch-wholesaler-outage"
									className="switch-label"
								></label>
							</div>
						</div>

						{/* Payment Outage Switch */}
						<div className="chaos-switch-row">
							<div className="chaos-switch-info">
								<span className="chaos-switch-label">
									Simulate Payment Gateways Down
								</span>
								<div className="chaos-switch-desc">
									Triggers gateway failover chain: Swipe ➔ PayPal ➔ COD.
								</div>
							</div>
							<div>
								<input
									id="switch-payment-outage"
									type="checkbox"
									className="switch-input"
									checked={paymentOutageActive}
									onChange={(e) => setPaymentOutageActive(e.target.checked)}
								/>
{/* biome-ignore lint/a11y/noLabelWithoutControl: styled toggle switch */} (feat(pricing): support dynamic chaos environmental variables in FMCG catalog imports and pricing)
								<label
									htmlFor="switch-payment-outage"
									className="switch-label"
								></label>
							</div>
						</div>

						{/* Geotag Fraud Switch */}
						<div className="chaos-switch-row">
							<div className="chaos-switch-info">
								<span className="chaos-switch-label">
									Simulate GPS Geotag / Proximity Fraud
								</span>
								<div className="chaos-switch-desc">
									Fails telemetry refund check, blocking refund bot triggers.
								</div>
							</div>
							<div>
								<input
									id="switch-telemetry-fraud"
									type="checkbox"
									className="switch-input"
									checked={simulateTelemetryFraud}
									onChange={(e) => setSimulateTelemetryFraud(e.target.checked)}
								/>
{/* biome-ignore lint/a11y/noLabelWithoutControl: styled toggle switch */} (feat(pricing): support dynamic chaos environmental variables in FMCG catalog imports and pricing)
								<label
									htmlFor="switch-telemetry-fraud"
									className="switch-label"
								></label>
							</div>
						</div>

						{/* DB latency switch */}
						<div className="chaos-switch-row">
							<div className="chaos-switch-info">
								<span className="chaos-switch-label">
									Inject Database Latency Spike
								</span>
								<div className="chaos-switch-desc">
									Simulates BFF database latency. Circuit breaker caches
									results.
								</div>
							</div>
							<div>
								<input
									id="switch-db-latency"
									type="checkbox"
									className="switch-input"
									checked={dbLatencyActive}
									onChange={(e) => setDbLatencyActive(e.target.checked)}
								/>
{/* biome-ignore lint/a11y/noLabelWithoutControl: styled toggle switch */} (feat(pricing): support dynamic chaos environmental variables in FMCG catalog imports and pricing)
								<label
									htmlFor="switch-db-latency"
									className="switch-label"
								></label>
							</div>
						</div>

						{/* Rider Traffic Congestion Switch */}
						<div className="chaos-switch-row">
							<div className="chaos-switch-info">
								<span className="chaos-switch-label">
									Simulate Rider Traffic Congestion
								</span>
								<div className="chaos-switch-desc">
									Heavy traffic congestion delays route completions.
								</div>
							</div>
							<div>
								<input
									id="switch-rider-traffic"
									type="checkbox"
									className="switch-input"
									checked={riderTrafficActive}
									onChange={(e) => setRiderTrafficActive(e.target.checked)}
								/>
{/* biome-ignore lint/a11y/noLabelWithoutControl: styled toggle switch */} (feat(pricing): support dynamic chaos environmental variables in FMCG catalog imports and pricing)
								<label
									htmlFor="switch-rider-traffic"
									className="switch-label"
								></label>
							</div>
						</div>
					</div>
				</div>
{/* FMCG Import & Dynamic Pricing Hub */}
				<div
					className="glass-card"
					style={{
						padding: "1.25rem",
						borderLeft: "3px solid var(--color-business)",
					}}
				>
					<h3
						style={{
							fontWeight: 800,
							color: "var(--color-business)",
							display: "flex",
							alignItems: "center",
							gap: "0.4rem",
							marginBottom: "0.5rem",
						}}
					>
						<Lucide.ShoppingBag size={18} />
						Global FMCG Import & Dynamic Pricing Hub
					</h3>
					<p
						style={{
							fontSize: "0.7rem",
							color: "var(--text-muted)",
							marginBottom: "1rem",
						}}
					>
						Fetch live FMCG product metadata (Nestlé, Cadbury, Mondelez,
						Coca-Cola, PepsiCo, Unilever, Dabur, Himalaya, ITC, P&G) from global
						Open Food Facts APIs and calculate dynamic zone-surge/perishable
						discounts.
					</p>

					<div
						style={{
							display: "flex",
							gap: "1rem",
							alignItems: "center",
							marginBottom: "1rem",
						}}
					>
						<button
							type="button"
							className="btn-primary-glow"
							style={{
								background: "var(--color-business)",
								color: "#070a13",
								border: "none",
								padding: "0.55rem 1rem",
								fontSize: "0.75rem",
								cursor: importStatus === "loading" ? "not-allowed" : "pointer",
								fontWeight: "bold",
								borderRadius: "6px",
								opacity: importStatus === "loading" ? 0.6 : 1,
							}}
							disabled={importStatus === "loading"}
							onClick={handleImportFmcg}
						>
							{importStatus === "loading" ? (
								<span
									style={{
										display: "flex",
										alignItems: "center",
										gap: "0.3rem",
									}}
								>
									<Lucide.Loader size={13} className="animate-spin" />
									Importing & Recalculating...
								</span>
							) : (
								"Import & Price FMCG Products"
							)}
						</button>

						{importStatus === "success" && (
							<span
								style={{
									fontSize: "0.7rem",
									color: "#34d399",
									fontWeight: "bold",
								}}
							>
								✓ Successfully Imported 10 Items
							</span>
						)}

						{importStatus === "error" && (
							<span
								style={{
									fontSize: "0.7rem",
									color: "#f87171",
									fontWeight: "bold",
								}}
							>
								⚠ {importError}
							</span>
						)}
					</div>

					{importResults.length > 0 && (
						<div
							style={{
								maxHeight: "220px",
								overflowY: "auto",
								border: "1px solid var(--border-default)",
								borderRadius: "8px",
								background: "rgba(0,0,0,0.2)",
							}}
						>
							<table
								style={{
									width: "100%",
									borderCollapse: "collapse",
									fontSize: "0.68rem",
								}}
							>
								<thead>
									<tr
										style={{
											borderBottom: "1px solid var(--border-default)",
											background: "rgba(255,255,255,0.02)",
										}}
									>
										<th
											style={{
												padding: "0.4rem",
												textAlign: "left",
												color: "var(--text-muted)",
											}}
										>
											Item
										</th>
										<th
											style={{
												padding: "0.4rem",
												textAlign: "left",
												color: "var(--text-muted)",
											}}
										>
											Brand
										</th>
										<th
											style={{
												padding: "0.4rem",
												textAlign: "right",
												color: "var(--text-muted)",
											}}
										>
											Base Price
										</th>
										<th
											style={{
												padding: "0.4rem",
												textAlign: "right",
												color: "var(--text-muted)",
											}}
										>
											Dynamic Price
										</th>
										<th
											style={{
												padding: "0.4rem",
												textAlign: "center",
												color: "var(--text-muted)",
											}}
										>
											Source
										</th>
									</tr>
								</thead>
								<tbody>
									{importResults.map((item, idx) => (
										<tr
											key={idx}
											style={{
												borderBottom: "1px solid rgba(255,255,255,0.03)",
											}}
										>
											<td style={{ padding: "0.4rem", fontWeight: 600 }}>
												{item.emoji} {item.name}
											</td>
											<td
												style={{
													padding: "0.4rem",
													color: "var(--text-secondary)",
												}}
											>
												{item.brand}
											</td>
											<td
												style={{
													padding: "0.4rem",
													textAlign: "right",
													color: "var(--text-muted)",
												}}
											>
												{item.basePrice.toFixed(2)} CHF
											</td>
											<td
												style={{
													padding: "0.4rem",
													textAlign: "right",
													color: "var(--color-customer)",
													fontWeight: "bold",
												}}
											>
												{item.dynamicPrice.toFixed(2)} CHF
											</td>
											<td style={{ padding: "0.4rem", textAlign: "center" }}>
												<span
													style={{
														padding: "0.1rem 0.3rem",
														borderRadius: "4px",
														fontSize: "0.55rem",
														fontWeight: "bold",
														background:
															item.source === "API"
																? "rgba(52, 211, 153, 0.1)"
																: "rgba(251, 191, 36, 0.1)",
														color:
															item.source === "API" ? "#34d399" : "#fbbf24",
													}}
												>
													{item.source}
												</span>
											</td>
										</tr>
									))}
								</tbody>
							</table>
						</div>
					)}
				</div> (feat(pricing): support dynamic chaos environmental variables in FMCG catalog imports and pricing)
			</div>

			{/* Verification queues */}
			<div
				style={{
					flex: 1,
					display: "flex",
					flexDirection: "column",
					gap: "1.25rem",
				}}
			>
				{/* Onboarding queue */}
				<div className="glass-card" style={{ padding: "1.25rem" }}>
					<h4 style={{ fontWeight: 800, marginBottom: "0.5rem" }}>
						Onboarding Verification Desk (3-Level Checks)
					</h4>
					<div
						style={{
							display: "flex",
							flexDirection: "column",
							gap: "0.75rem",
							marginTop: "0.75rem",
						}}
					>
						{onboardingQueue
							.filter(
								(app) =>
									!app.approvals.l1 || !app.approvals.l2 || !app.approvals.l3,
							)
							.map((app) => (
								<div
									key={app.id}
									style={{
										background: "rgba(255,255,255,0.01)",
										border: "1px solid var(--border-default)",
										padding: "0.75rem",
										borderRadius: "10px",
									}}
								>
									<div
										style={{
											display: "flex",
											justifyContent: "space-between",
											fontSize: "0.75rem",
											fontWeight: 700,
											marginBottom: "0.5rem",
										}}
									>
										<span>{app.name}</span>
										<span
											style={{
												color: "var(--color-admin)",
												fontFamily: "var(--font-mono)",
												fontSize: "0.65rem",
											}}
										>
											{app.type.toUpperCase()}
										</span>
									</div>
									<div
										style={{
											display: "flex",
											gap: "0.35rem",
											marginTop: "0.5rem",
										}}
									>
										<button
											aria-label="Approve L1 ID"
											className={`onboard-step-btn ${app.approvals.l1 ? "approved" : "pending"}`}
											onClick={() => handleApproveOnboard(app.id, "l1")}
											disabled={app.approvals.l1}
										>
											{app.approvals.l1 ? (
												<Lucide.CheckCircle2 size={12} />
											) : (
												<Lucide.CircleDot size={12} />
											)}
											<span>L1 ID</span>
										</button>
										<button
											aria-label="Approve L2 Vehicle"
											className={`onboard-step-btn ${app.approvals.l2 ? "approved" : "pending"}`}
											onClick={() => handleApproveOnboard(app.id, "l2")}
											disabled={app.approvals.l2}
										>
											{app.approvals.l2 ? (
												<Lucide.CheckCircle2 size={12} />
											) : (
												<Lucide.CircleDot size={12} />
											)}
											<span>L2 Vehicle</span>
										</button>
										<button
											aria-label="Approve L3 Background"
											className={`onboard-step-btn ${app.approvals.l3 ? "approved" : "pending"}`}
											onClick={() => handleApproveOnboard(app.id, "l3")}
											disabled={app.approvals.l3}
										>
											{app.approvals.l3 ? (
												<Lucide.CheckCircle2 size={12} />
											) : (
												<Lucide.CircleDot size={12} />
											)}
											<span>L3 BG</span>
										</button>
									</div>
								</div>
							))}
						{onboardingQueue.every(
							(app) => app.approvals.l1 && app.approvals.l2 && app.approvals.l3,
						) && (
							<p
								style={{
									fontSize: "0.7rem",
									color: "var(--text-muted)",
									textAlign: "center",
									padding: "1rem 0",
								}}
							>
								No pending applications
							</p>
						)}
					</div>
				</div>

				{/* HITL Queue */}
				<div className="glass-card" style={{ padding: "1.25rem" }}>
					<h4 style={{ fontWeight: 800, marginBottom: "0.5rem" }}>
						Human-in-the-Loop (HITL) Queue
					</h4>
					<div
						style={{
							display: "flex",
							flexDirection: "column",
							gap: "0.75rem",
							marginTop: "0.75rem",
						}}
					>
						{hitlLoading ? (
							<div
								style={{
									display: "flex",
									flexDirection: "column",
									gap: "0.6rem",
									padding: "0.5rem",
								}}
							>
								<div
									className="skeleton-shimmer skeleton-text medium"
									style={{ height: 14 }}
								/>
								<div
									className="skeleton-shimmer skeleton-text"
									style={{ height: 10 }}
								/>
								<div
									className="skeleton-shimmer skeleton-text short"
									style={{ height: 10 }}
								/>
							</div>
						) : hitlQueue.length === 0 ? (
							<p
								style={{
									fontSize: "0.7rem",
									color: "var(--text-muted)",
									textAlign: "center",
									padding: "1rem 0",
								}}
							>
								No pending approvals
							</p>
						) : (
							hitlQueue.map((ticket) => (
								<div
									key={ticket.id}
									style={{
										background: "rgba(255,255,255,0.01)",
										border: "1px solid var(--border-default)",
										padding: "0.75rem",
										borderRadius: "10px",
										fontSize: "0.75rem",
									}}
								>
									<div
										style={{
											display: "flex",
											justifyContent: "space-between",
											fontWeight: 700,
											marginBottom: "0.25rem",
										}}
									>
										<span style={{ color: "var(--color-admin)" }}>
											{ticket.type.toUpperCase()}
										</span>
										<span
											style={{
												fontFamily: "var(--font-mono)",
												color: "var(--color-customer)",
											}}
										>
											${ticket.amount.toFixed(2)}
										</span>
									</div>
									<p
										style={{
											color: "var(--text-muted)",
											margin: "0.3rem 0 0.6rem 0",
											fontSize: "0.7rem",
											lineHeight: "1.4",
										}}
									>
										{ticket.desc}
									</p>
									<div
										style={{
											display: "flex",
											gap: "0.5rem",
											flexWrap: "wrap",
										}}
									>
										<button
											type="button"
											className="btn-primary-glow hitl-btn-approve"
											onClick={() => handleReleaseHitl(ticket)}
										>
											<Lucide.CheckCircle size={13} />
											<span>Approve Release</span>
										</button>
										<button
											type="button"
											className="btn-secondary-glow hitl-btn-void"
											onClick={() => handleVoidHitl(ticket)}
										>
											<Lucide.XCircle size={13} />
											<span>Void Ticket</span>
										</button>
										{ticket.source === "B2B_PROCUREMENT" && (
											<button
												type="button"
												className="btn-secondary-glow hitl-btn-adjust"
												onClick={() =>
													setAdjustOpen((prev) => ({
														...prev,
														[ticket.id]: !prev[ticket.id],
													}))
												}
											>
												<Lucide.PencilLine size={13} />
												<span>Adjust Bid</span>
											</button>
										)}
									</div>
									{adjustOpen[ticket.id] && (
										<div
											style={{
												display: "flex",
												gap: "0.4rem",
												marginTop: "0.5rem",
												alignItems: "center",
											}}
										>
											<input
												type="number"
												min="0"
												step="0.01"
												placeholder="New price (CHF)"
												value={adjustPrices[ticket.id] ?? ""}
												onChange={(e) =>
													setAdjustPrices((prev) => ({
														...prev,
														[ticket.id]: e.target.value,
													}))
												}
												style={{
													flex: 1,
													background: "#020408",
													border: "1px solid var(--color-admin)",
													borderRadius: "6px",
													color: "var(--text-primary)",
													padding: "0.3rem 0.4rem",
													fontSize: "0.75rem",
													fontFamily: "var(--font-mono)",
												}}
											/>
											<button
												type="button"
												className="btn-primary-glow hitl-btn-approve"
												disabled={
													!adjustPrices[ticket.id] ||
													Number(adjustPrices[ticket.id]) <= 0
												}
												onClick={() => {
													const price = Number(adjustPrices[ticket.id]);
													if (price > 0) {
														handleAdjustHitl(ticket, price);
														setAdjustOpen((prev) => ({
															...prev,
															[ticket.id]: false,
														}));
														setAdjustPrices((prev) => ({
															...prev,
															[ticket.id]: "",
														}));
													}
												}}
											>
												<Lucide.Send size={13} />
												<span>Confirm</span>
											</button>
											<button
												type="button"
												className="btn-secondary-glow"
												onClick={() =>
													setAdjustOpen((prev) => ({
														...prev,
														[ticket.id]: false,
													}))
												}
											>
												<Lucide.X size={13} />
											</button>
										</div>
									)}
								</div>
							))
						)}
					</div>
				</div>
			</div>
		</div>
	);
}
