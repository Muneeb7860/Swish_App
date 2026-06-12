import * as Lucide from "lucide-react";
import React, { useState } from "react";
import { useAiStream } from "../hooks/useAiStream";

export default function BusinessApp({
	products,
	merchantWallet,
	ledger,
	trustLogs,
	customerTrustScore,
	riderTrustScore,
	pickerTrustScore,
	wholesalerTrustScore,
	centralCapacity,
	eastCapacity,
	centralScalingCount,
	eastScalingCount,
	handleScaleCapacity,
	downloadRegulatoryReport,
}) {
	const [aiPanelOpen, setAiPanelOpen] = useState(false);
	const { streamData, isStreaming, error, startStream } = useAiStream();

	// Calculate total stock in stores
	const totalStockCentral = products.reduce((sum, p) => sum + p.stock, 0);
	const totalStockEast = products.reduce((sum, p) => sum + p.stockEast, 0);

	const centralFillPct = Math.min(
		100,
		(totalStockCentral / centralCapacity) * 100,
	);
	const eastFillPct = Math.min(100, (totalStockEast / eastCapacity) * 100);

	// AI Diagnostic Auditor Trigger
	const handleTriggerAudit = () => {
		const activeLedgerCount = ledger.length;
		const latestLedgerEntry =
			ledger.length > 0 ? ledger[ledger.length - 1] : null;
		const ledgerDesc = latestLedgerEntry
			? latestLedgerEntry.desc
			: "No transactions recorded yet.";

		const prompt = `Analyze the current Swiss Q-Commerce operational telemetry and provide a brief executive audit report:
- Central MFC Capacity: ${totalStockCentral}/${centralCapacity} units (${Math.round(centralFillPct)}% full).
- East MFC Capacity: ${totalStockEast}/${eastCapacity} units (${Math.round(eastFillPct)}% full).
- Merchant Wallet Balance: $${merchantWallet.toFixed(2)}.
- System Trust Vectors: Wholesaler ${wholesalerTrustScore}/100, Customer ${customerTrustScore}/100, Picker Accuracy ${pickerTrustScore}/100, Rider Score ${riderTrustScore}/100.
- Latest OLAP Ledger Transaction: "${ledgerDesc}".

Provide a highly concise, professional business health assessment, flag any structural/trust bottlenecks, and list 2 key recommendations. Use bullet points. Keep it professional.`;

		startStream("/api/ai/local", prompt);
	};
	// Surcharges dynamically mapped from scaling count
	const getScalingFee = (count) => {
		if (count === 0) return 15.0;
		if (count === 1) return 25.0;
		if (count === 2) return 35.0;
		return null;
	};

	const centralFee = getScalingFee(centralScalingCount);
	const eastFee = getScalingFee(eastScalingCount);

	return (
		<div
			className="business-dashboard"
			style={{ display: "flex", flexDirection: "column", gap: "1.25rem" }}
		>
			{/* Wallet and summary bar */}
			<div
				className="glass-card"
				style={{
					padding: "1rem",
					borderLeft: "3px solid var(--color-business)",
				}}
			>
				<div
					style={{
						display: "flex",
						justifyContent: "space-between",
						alignItems: "center",
					}}
				>
					<h3 style={{ fontWeight: 800 }}>Business Web Console</h3>
					<div style={{ display: "flex", gap: "1.5rem", alignItems: "center" }}>
						<span>
							Merchant Wallet:{" "}
							<strong style={{ color: "var(--color-business)" }}>
								${merchantWallet.toFixed(2)}
							</strong>
						</span>
						<button
							className="btn-secondary-glow"
							style={{ fontSize: "0.75rem", cursor: "pointer" }}
							onClick={downloadRegulatoryReport}
						>
							Download Regulatory Audit Report (CSV)
						</button>
					</div>
				</div>
			</div>

			{/* Trust scores overview */}
			<div
				className="product-shelf-grid"
				style={{ gridTemplateColumns: "repeat(4, 1fr)", gap: "1rem" }}
			>
				{/* Customer Trust Card */}
				<div
					className="glass-card"
					style={{
						padding: "1rem",
						display: "flex",
						flexDirection: "column",
						gap: "0.5rem",
						borderLeft: "3px solid var(--color-customer)",
					}}
				>
					<div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<span style={{ fontSize: "0.6rem", color: "var(--text-muted)", fontWeight: 700, letterSpacing: "0.05em" }}>
							CUSTOMER TRUST
						</span>
						<Lucide.UserCheck size={14} style={{ color: "var(--color-customer)" }} />
					</div>
					<h3 style={{ color: "var(--color-customer)", fontWeight: 800, margin: 0, fontSize: "1.25rem", fontFamily: "var(--font-mono)" }}>
						{customerTrustScore}
						<span style={{ fontSize: "0.75rem", color: "var(--text-muted)", fontWeight: 500 }}>/100</span>
					</h3>
					<div style={{ height: "4px", background: "rgba(255,255,255,0.05)", borderRadius: "2px", overflow: "hidden" }}>
						<div
							style={{
								height: "100%",
								background: "var(--color-customer)",
								width: `${customerTrustScore}%`,
								transition: "width 0.5s ease",
							}}
						/>
					</div>
				</div>

				{/* Rider Trust Card */}
				<div
					className="glass-card"
					style={{
						padding: "1rem",
						display: "flex",
						flexDirection: "column",
						gap: "0.5rem",
						borderLeft: "3px solid var(--color-rider)",
					}}
				>
					<div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<span style={{ fontSize: "0.6rem", color: "var(--text-muted)", fontWeight: 700, letterSpacing: "0.05em" }}>
							RIDER TRUST
						</span>
						<Lucide.Bike size={14} style={{ color: "var(--color-rider)" }} />
					</div>
					<h3 style={{ color: "var(--color-rider)", fontWeight: 800, margin: 0, fontSize: "1.25rem", fontFamily: "var(--font-mono)" }}>
						{riderTrustScore}
						<span style={{ fontSize: "0.75rem", color: "var(--text-muted)", fontWeight: 500 }}>/100</span>
					</h3>
					<div style={{ height: "4px", background: "rgba(255,255,255,0.05)", borderRadius: "2px", overflow: "hidden" }}>
						<div
							style={{
								height: "100%",
								background: "var(--color-rider)",
								width: `${riderTrustScore}%`,
								transition: "width 0.5s ease",
							}}
						/>
					</div>
				</div>

				{/* Picker Accuracy Card */}
				<div
					className="glass-card"
					style={{
						padding: "1rem",
						display: "flex",
						flexDirection: "column",
						gap: "0.5rem",
						borderLeft: "3px solid var(--color-inventory)",
					}}
				>
					<div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<span style={{ fontSize: "0.6rem", color: "var(--text-muted)", fontWeight: 700, letterSpacing: "0.05em" }}>
							PICKER ACCURACY
						</span>
						<Lucide.ClipboardCheck size={14} style={{ color: "var(--color-inventory)" }} />
					</div>
					<h3 style={{ color: "var(--color-inventory)", fontWeight: 800, margin: 0, fontSize: "1.25rem", fontFamily: "var(--font-mono)" }}>
						{pickerTrustScore}
						<span style={{ fontSize: "0.75rem", color: "var(--text-muted)", fontWeight: 500 }}>/100</span>
					</h3>
					<div style={{ height: "4px", background: "rgba(255,255,255,0.05)", borderRadius: "2px", overflow: "hidden" }}>
						<div
							style={{
								height: "100%",
								background: "var(--color-inventory)",
								width: `${pickerTrustScore}%`,
								transition: "width 0.5s ease",
							}}
						/>
					</div>
				</div>

				{/* Wholesaler Trust Card */}
				<div
					className="glass-card"
					style={{
						padding: "1rem",
						display: "flex",
						flexDirection: "column",
						gap: "0.5rem",
						borderLeft: "3px solid var(--color-business)",
					}}
				>
					<div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<span style={{ fontSize: "0.6rem", color: "var(--text-muted)", fontWeight: 700, letterSpacing: "0.05em" }}>
							WHOLESALER TRUST
						</span>
						<Lucide.Truck size={14} style={{ color: "var(--color-business)" }} />
					</div>
					<h3 style={{ color: "var(--color-business)", fontWeight: 800, margin: 0, fontSize: "1.25rem", fontFamily: "var(--font-mono)" }}>
						{wholesalerTrustScore}
						<span style={{ fontSize: "0.75rem", color: "var(--text-muted)", fontWeight: 500 }}>/100</span>
					</h3>
					<div style={{ height: "4px", background: "rgba(255,255,255,0.05)", borderRadius: "2px", overflow: "hidden" }}>
						<div
							style={{
								height: "100%",
								background: "var(--color-business)",
								width: `${wholesalerTrustScore}%`,
								transition: "width 0.5s ease",
							}}
						/>
					</div>
				</div>
			</div>

			{/* Store capacity progress trackers */}
			<div style={{ display: "flex", gap: "1.25rem" }}>
				{/* Central Store capacity */}
				<div className="glass-card bento-card capacity-card" style={{ flex: 1 }}>
					<div className="capacity-title-row">
						<span>Central MFC Capacity</span>
						{centralFillPct > 90 && (
							<span className="warning-pill animate-pulse">⚠️ STORAGE BOTTLENECK</span>
						)}
					</div>
					<div className="capacity-value">
						{totalStockCentral} / {centralCapacity} <span style={{ fontSize: "0.8rem", color: "var(--text-secondary)" }}>Units ({Math.round(centralFillPct)}% Full)</span>
					</div>
					<div className="capacity-bar-outer">
						<div
							className={`capacity-bar-inner ${centralFillPct > 90 ? 'warning' : 'normal'}`}
							style={{ width: `${centralFillPct}%` }}
						/>
					</div>
					{centralFee !== null ? (
						<button
							aria-label="Rent Central Overflow Storage Bay"
							id="btn-rent-central"
							className="btn-secondary-glow"
							style={{
								width: "100%",
								fontSize: "0.7rem",
								padding: "0.45rem",
								cursor: "pointer",
								borderRadius: "6px",
								marginTop: "0.25rem"
							}}
							onClick={() => handleScaleCapacity("Central")}
						>
							Rent Central Overflow Storage Bay (Surcharge: ${centralFee.toFixed(2)})
						</button>
					) : (
						<span style={{ fontSize: "0.65rem", color: "var(--text-muted)", marginTop: "0.25rem" }}>
							Central Storage Capacity Max Scaled (+120 bay limit)
						</span>
					)}
				</div>

				{/* East Store capacity */}
				<div className="glass-card bento-card capacity-card" style={{ flex: 1 }}>
					<div className="capacity-title-row">
						<span>East MFC Capacity</span>
						{eastFillPct > 90 && (
							<span className="warning-pill animate-pulse">⚠️ STORAGE BOTTLENECK</span>
						)}
					</div>
					<div className="capacity-value">
						{totalStockEast} / {eastCapacity} <span style={{ fontSize: "0.8rem", color: "var(--text-secondary)" }}>Units ({Math.round(eastFillPct)}% Full)</span>
					</div>
					<div className="capacity-bar-outer">
						<div
							className={`capacity-bar-inner ${eastFillPct > 90 ? 'warning' : 'normal'}`}
							style={{ width: `${eastFillPct}%` }}
						/>
					</div>
					{eastFee !== null ? (
						<button
							aria-label="Rent East Overflow Storage Bay"
							id="btn-rent-east"
							className="btn-secondary-glow"
							style={{
								width: "100%",
								fontSize: "0.7rem",
								padding: "0.45rem",
								cursor: "pointer",
								borderRadius: "6px",
								marginTop: "0.25rem"
							}}
							onClick={() => handleScaleCapacity("East")}
						>
							Rent East Overflow Storage Bay (Surcharge: ${eastFee.toFixed(2)})
						</button>
					) : (
						<span style={{ fontSize: "0.65rem", color: "var(--text-muted)", marginTop: "0.25rem" }}>
							East Storage Capacity Max Scaled (+120 bay limit)
						</span>
					)}
				</div>
			</div>

			{/* AI Telemetry & Operations Auditor Widget */}
			<div
				className="glass-card"
				style={{
					padding: "1.25rem",
					borderLeft: "4px solid var(--color-business)",
					background: "rgba(255,255,255,0.01)",
					borderRadius: "12px",
					transition: "all 0.3s ease",
					marginBottom: "1.25rem",
				}}
			>
				<div
					style={{
						display: "flex",
						justifyContent: "space-between",
						alignItems: "center",
						cursor: "pointer",
					}}
					onClick={() => setAiPanelOpen(!aiPanelOpen)}
				>
					<h3
						style={{
							margin: 0,
							fontSize: "0.95rem",
							fontWeight: 800,
							color: "var(--color-business)",
							display: "flex",
							alignItems: "center",
							gap: "0.5rem",
						}}
					>
						<Lucide.Sparkles
							size={16}
							className="animate-pulse"
							style={{ color: "var(--color-business)" }}
						/>
						🔮 Swiss AI Operational Telemetry & Financial Auditor
					</h3>
					<button
						className="btn-secondary-glow"
						style={{
							padding: "0.2rem 0.5rem",
							fontSize: "0.7rem",
							border: "none",
							cursor: "pointer",
						}}
					>
						{aiPanelOpen ? "Hide Auditor" : "Show Auditor"}
					</button>
				</div>

				{aiPanelOpen && (
					<div
						style={{
							marginTop: "1rem",
							display: "flex",
							flexDirection: "column",
							gap: "0.75rem",
						}}
					>
						<p
							style={{
								fontSize: "0.75rem",
								color: "var(--text-muted)",
								margin: 0,
							}}
						>
							The Local LLM (Qwen 2.5) executes on-premises, scanning merchant
							wallets, warehouse capacities, and ledger anomalies to generate
							live corporate recommendations.
						</p>

						<div>
							<button
								className="btn-primary-glow"
								style={{
									background: "var(--color-business)",
									color: "white",
									border: "none",
									padding: "0.5rem 1rem",
									fontSize: "0.8rem",
									cursor: "pointer",
									display: "flex",
									alignItems: "center",
									gap: "0.4rem",
								}}
								onClick={handleTriggerAudit}
								disabled={isStreaming}
							>
								<Lucide.ShieldCheck size={14} />
								{isStreaming
									? "Analyzing Operations..."
									: "Run On-Premises Operational Audit"}
							</button>
						</div>

						{error && (
							<div
								style={{
									color: "var(--color-admin)",
									fontSize: "0.75rem",
									marginTop: "0.25rem",
								}}
							>
								⚠️ Local Audit Failure: {error}
							</div>
						)}

						{streamData && (
							<div
								style={{
									background: "rgba(0,0,0,0.25)",
									padding: "1rem",
									borderRadius: "8px",
									border: "1px solid rgba(255,255,255,0.05)",
									fontSize: "0.8rem",
									lineHeight: "1.45",
									color: "var(--text-primary)",
									maxHeight: "250px",
									overflowY: "auto",
									whiteSpace: "pre-wrap",
									position: "relative",
									fontFamily: "var(--font-mono)",
								}}
							>
								{streamData}
								{isStreaming && (
									<span
										className="animate-ping"
										style={{
											color: "var(--color-business)",
											fontWeight: "bold",
											marginLeft: "2px",
										}}
									>
										▋
									</span>
								)}
							</div>
						)}
					</div>
				)}
			</div>

			<div style={{ display: "flex", gap: "1.25rem" }}>
				{/* double entry ledger table */}
				<div
					className="glass-card"
					style={{ flex: 1.5, padding: "1rem", overflowX: "auto" }}
				>
					<h4 style={{ fontWeight: 800, marginBottom: "0.5rem" }}>
						OLAP Financial Ledger
					</h4>
					<table className="ledger-table">
						<thead>
							<tr className="ledger-header-row">
								<th className="ledger-th">Time</th>
								<th className="ledger-th">Type</th>
								<th className="ledger-th">Ref Code</th>
								<th className="ledger-th">Description</th>
								<th className="ledger-th">Debit</th>
								<th className="ledger-th">Credit</th>
							</tr>
						</thead>
						<tbody>
							{ledger
								.slice()
								.reverse()
								.map((l) => (
									<tr key={l.id} className="ledger-row">
										<td className="ledger-td ledger-time">{l.time}</td>
										<td className="ledger-td">{l.type}</td>
										<td className="ledger-td ledger-ref">{l.ref}</td>
										<td className="ledger-td ledger-desc">{l.desc}</td>
										<td className="ledger-td ledger-debit">
											{l.debit > 0 ? `$${l.debit.toFixed(2)}` : ""}
										</td>
										<td className="ledger-td ledger-credit">
											{l.credit > 0 ? `$${l.credit.toFixed(2)}` : ""}
										</td>
									</tr>
								))}
						</tbody>
					</table>
				</div>

				{/* security logs */}
				<div className="glass-card" style={{ flex: 1, padding: "1rem" }}>
					<h4 style={{ fontWeight: 800, marginBottom: "0.5rem" }}>
						🛡️ Security Trust & Fraud Log
					</h4>
					<div
						className="kafka-log-list"
						style={{ height: "240px", overflowY: "auto" }}
					>
						{trustLogs
							.slice()
							.reverse()
							.map((log) => (
								<div
									key={log.id}
									style={{
										fontSize: "0.7rem",
										padding: "0.35rem 0",
										borderBottom: "1px solid rgba(255,255,255,0.02)",
									}}
								>
									<span style={{ color: "var(--text-muted)" }}>
										[{log.time}]
									</span>{" "}
									<span className={`kafka-log-event event-${log.actor}`}>
										{log.actor.toUpperCase()}
									</span>
									:{" "}
									<span style={{ color: "var(--text-primary)" }}>
										{log.event}
									</span>{" "}
									<span
										style={{
											color:
												log.delta >= 0
													? "var(--color-customer)"
													: "var(--color-admin)",
										}}
									>
										({log.delta >= 0 ? "+" : ""}
										{log.delta})
									</span>
								</div>
							))}
					</div>
				</div>
			</div>
		</div>
	);
}
