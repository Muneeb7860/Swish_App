import * as Lucide from "lucide-react";
import React from "react";

export default function RiderApp({
	riderWallet,
	riderTrustScore,
	riderOnboardStatus,
	setRiderOnboardStatus,
	activeOrder,
	generateCertificate,
	coldChainBreakdownActive,
	handleInjectDryIce,
	handleApplyOnboard,
	handleCompleteDelivery,
	logKafka,
}) {
	const [podHash, setPodHash] = React.useState("");
	const [proofType, setProofType] = React.useState("");

	const isPerishable = activeOrder && activeOrder.perishable;
	const temp = activeOrder ? activeOrder.temperature || 4.0 : 4.0;

	const handleSubmitApplication = () => {
		setRiderOnboardStatus("pending");
		if (handleApplyOnboard) {
			handleApplyOnboard("rider", "Rider Dave");
		}
	};

	return (
		<div
			className="rider-dashboard"
			style={{ display: "flex", flexDirection: "column", gap: "1.25rem" }}
		>
			{/* Onboarding form check */}
			{riderOnboardStatus === "unapplied" && (
				<div
					className="glass-card onboard-form-container"
					style={{
						padding: "1.5rem",
						borderLeft: "4px solid var(--color-rider)",
					}}
				>
					<h3
						style={{
							fontWeight: 800,
							color: "var(--color-rider)",
							marginBottom: "0.5rem",
							fontFamily: "var(--font-display)",
						}}
					>
						Rider Onboarding Application
					</h3>
					<p
						style={{
							fontSize: "0.75rem",
							color: "var(--text-muted)",
							marginBottom: "1rem",
						}}
					>
						Submit your documentation. Registration must clear sequential L1,
						L2, L3 operations checks before you can access transit navigation
						dispatches.
					</p>
					<div
						style={{
							display: "flex",
							flexDirection: "column",
							gap: "0.5rem",
							maxWidth: "300px",
						}}
					>
						<input
							type="text"
							className="rider-form-input"
							placeholder="Full Legal Name"
							value="Rider Dave"
							readOnly
						/>
						<input
							type="text"
							className="rider-form-input"
							placeholder="Vehicle Registration"
							value="Electric Cargo E-Bike (Model S)"
							readOnly
						/>
						<button
							className="btn-primary-glow"
							style={{
								background: "var(--color-rider)",
								color: "#070a13",
								border: "none",
								padding: "0.5rem",
								cursor: "pointer",
								fontWeight: 800,
							}}
							onClick={handleSubmitApplication}
						>
							Submit Application Credentials
						</button>
					</div>
				</div>
			)}

			{riderOnboardStatus === "pending" && (
				<div
					className="glass-card"
					style={{
						padding: "1.5rem",
						textAlign: "center",
						borderLeft: "4px solid var(--color-admin)",
					}}
				>
					<Lucide.Hourglass
						size={24}
						style={{
							color: "var(--color-admin)",
							animation: "spin 3s linear infinite",
							marginBottom: "0.5rem",
						}}
					/>
					<h3 style={{ fontWeight: 800, color: "var(--color-admin)" }}>
						Onboarding Status: Awaiting Validation
					</h3>
					<p style={{ fontSize: "0.75rem", color: "var(--text-muted)" }}>
						Your rider credentials have been submitted. Security Admin must
						approve L1 Identity verification, L2 Vehicle compliance, and L3
						Background check.
					</p>
				</div>
			)}

			{riderOnboardStatus === "active" && (
				<div className="rider-bento-grid">
					{/* Card 1: Operations Header Banner */}
					<div className="glass-card bento-card span-full rider-header-banner">
						<div className="banner-content">
							<div className="banner-badge">OPERATIONS HUB</div>
							<h3 className="banner-title">Rider Control Console</h3>
							<p className="banner-subtitle">
								Active Operator: <strong>Rider Dave</strong> | Node ID:{" "}
								<strong>CH-RIDER-839</strong>
							</p>
						</div>
						<div className="banner-status">
							<span className="status-pulse-green"></span>
							<span className="status-text">SYSTEM ONLINE</span>
						</div>
					</div>

					{/* Card 2: Wallet Balance Widget */}
					<div className="glass-card bento-card rider-wallet-card">
						<div className="card-header-icon text-rider">
							<Lucide.Wallet size={18} />
							<span className="card-header-label">WALLET ACCOUNT</span>
						</div>
						<div className="wallet-balance-container">
							<span className="wallet-currency">$</span>
							<span className="wallet-amount">{riderWallet.toFixed(2)}</span>
						</div>
						<div className="wallet-chip-line">
							<div className="wallet-chip"></div>
							<div className="wallet-card-brand">SWISH PAY</div>
						</div>
						<div className="wallet-footer">
							<span className="wallet-status-indicator">
								● Auto-Payout Active
							</span>
							<button
								className="wallet-mini-btn"
								onClick={() =>
									logKafka &&
									logKafka(
										"rider",
										"wallet.payout_inquiry",
										"Rider Dave requested instant payout simulation.",
									)
								}
							>
								Details
							</button>
						</div>
					</div>

					{/* Card 3: Trust Rating Widget */}
					<div className="glass-card bento-card rider-trust-card">
						<div className="card-header-icon text-rider">
							<Lucide.ShieldCheck size={18} />
							<span className="card-header-label">TRUST INDEX</span>
						</div>
						<div className="trust-radial-container">
							<div
								className="trust-radial-circle"
								style={
									{
										"--progress-pct": `${riderTrustScore}%`,
									} as React.CSSProperties
								}
							>
								<div className="trust-radial-inner">
									<span className="trust-score-num">{riderTrustScore}</span>
									<span className="trust-score-denom">/100</span>
								</div>
							</div>
						</div>
						<div className="trust-badge-status">
							<span className="trust-badge-label">ELITE COURIER</span>
							<span className="trust-badge-desc">
								Premium order priority granted
							</span>
						</div>
					</div>

					{/* Card 4: Active Dispatch Transit Status (Spans 2 columns if order active, else 1) */}
					<div
						className={`glass-card bento-card ${activeOrder ? "span-2" : "span-1"} rider-transit-card`}
					>
						<div className="card-header-icon text-rider">
							<Lucide.Bike size={18} />
							<span className="card-header-label">LIVE DISPATCH</span>
						</div>

						{!activeOrder ? (
							<div className="transit-standby-view flex flex-col items-center gap-4 py-8">
								<div className="flex justify-center my-2">
									<div className="radar-sweep-container flex items-center justify-center">
										<div className="radar-sweep-line" />
										<Lucide.Bike
											size={32}
											className="text-amber-500 animate-pulse z-10"
										/>
									</div>
								</div>
								<h4 className="standby-title m-0 text-sm font-bold text-slate-200">
									Central Store Standby
								</h4>
								<p className="standby-desc m-0 text-xs text-slate-400 max-w-[220px]">
									Scanning logistics gateway for active delivery dispatches...
								</p>
							</div>
						) : (
							<div className="transit-active-view">
								<div className="active-dispatch-header">
									<span className="dispatch-order-id">
										ORDER #{activeOrder.id}
									</span>
									<span className="dispatch-order-status">
										{activeOrder.status
											? activeOrder.status.toUpperCase()
											: "TRANSIT"}
									</span>
								</div>

								{activeOrder.status === "arrived" ? (
									<div className="pod-completion-hub">
										<div className="pod-header">
											<Lucide.FileSignature size={16} className="text-rider" />
											<span>Regulatory Handshake Proof Required</span>
										</div>
										<p className="pod-description">
											Deliveries require a cryptographic proof-of-delivery. Scan
											the customer's QR or upload a doorstep validation photo.
										</p>
										<div className="pod-actions-row">
											<button
												aria-label="Button"
												className="btn-secondary-glow pod-btn"
												onClick={() => {
													setProofType("photo");
													setPodHash(
														"PHOTO-SHA256-" +
															Math.random()
																.toString(36)
																.substring(2, 15)
																.toUpperCase(),
													);
													if (logKafka)
														logKafka(
															"rider",
															"pod.photo_captured",
															"Rider Dave captured doorstep delivery verification photo.",
														);
												}}
											>
												<Lucide.Camera size={14} /> Doorstep Photo
											</button>
											<button
												aria-label="Button"
												className="btn-secondary-glow pod-btn"
												onClick={() => {
													setProofType("qr");
													setPodHash(
														"QR-SHA256-" +
															Math.random()
																.toString(36)
																.substring(2, 15)
																.toUpperCase(),
													);
													if (logKafka)
														logKafka(
															"rider",
															"pod.qr_scanned",
															"Rider Dave scanned customer receipt verification QR code.",
														);
												}}
											>
												<Lucide.QrCode size={14} /> Scan QR Code
											</button>
										</div>

										{podHash && (
											<div className="pod-hash-box">
												<div className="hash-header">
													<span>PROOF TYPE: {proofType.toUpperCase()}</span>
													<span className="text-green">SIGNED</span>
												</div>
												<div className="hash-value">{podHash}</div>
											</div>
										)}

										<button
											aria-label="Button"
											className="btn-primary-glow pod-submit-btn"
											disabled={!podHash}
											style={{
												background: podHash ? "var(--color-rider)" : "#27293d",
												color: podHash ? "#070a13" : "var(--text-muted)",
												cursor: podHash ? "pointer" : "not-allowed",
											}}
											onClick={() => {
												if (handleCompleteDelivery) {
													handleCompleteDelivery(activeOrder, podHash);
													setPodHash("");
													setProofType("");
												}
											}}
										>
											🚀 Sign & Complete Handshake
										</button>
									</div>
								) : (
									<div className="transit-progress-hub">
										<div className="transit-meta-row">
											<span className="transit-meta-item">
												ETA: 15 Mins SLA
											</span>
											<span className="transit-meta-item">
												Remaining: {activeOrder.slaRemaining || 0}s
											</span>
										</div>

										<div className="transit-bar-outer">
											<div
												className="transit-bar-inner"
												style={{ width: `${activeOrder.progress || 0}%` }}
											></div>
										</div>

										<div className="transit-bar-caption">
											<span>Route Progress</span>
											<span>{activeOrder.progress || 0}%</span>
										</div>
									</div>
								)}
							</div>
						)}
					</div>

					{/* Card 5: Cold Chain IoT Telemetry */}
					{isPerishable && (
						<div className="glass-card bento-card rider-telemetry-card">
							<div className="card-header-icon text-rider">
								<Lucide.Thermometer size={18} />
								<span className="card-header-label">COLD CHAIN TELEMETRY</span>
							</div>

							<div className="telemetry-temp-display">
								<span
									className={`temp-value ${temp > 8.0 ? "text-alert" : "text-cool"}`}
								>
									{temp.toFixed(1)} <span className="temp-unit">°C</span>
								</span>
								{temp > 8.0 && (
									<span className="warning-pill animate-pulse">
										⚠️ OVERHEATING
									</span>
								)}
							</div>

							<div className="telemetry-sensor-details">
								<div className="sensor-row">
									<span className="sensor-label">Sensor Status</span>
									<span className="sensor-value text-green">Online</span>
								</div>
								<div className="sensor-row">
									<span className="sensor-label">Cargo Class</span>
									<span className="sensor-value text-rider">Perishable</span>
								</div>
							</div>

							{temp > 8.0 && (
								<button
									id="btn-inject-dry-ice"
									className="btn-primary-glow dry-ice-btn"
									onClick={handleInjectDryIce}
								>
									❄️ Inject Dry Ice ($2.00)
								</button>
							)}
						</div>
					)}

					{/* Card 6: Academy & Certifications Widget */}
					<div className="glass-card bento-card rider-academy-card">
						<div className="card-header-icon text-rider">
							<Lucide.Award size={18} />
							<span className="card-header-label">TRAINING HUB</span>
						</div>
						<div className="academy-badge-display">
							<div className="badge-hexagon">
								<Lucide.Zap size={20} className="badge-icon-inner text-rider" />
							</div>
							<div className="badge-info">
								<span className="badge-name">IoT Logistics Cert</span>
								<span className="badge-status-completed">
									Active Certificate
								</span>
							</div>
						</div>
						<p className="academy-desc">
							Complete cargo safety training to unlock high-priority premium
							payouts.
						</p>
						<button
							aria-label="Button"
							className="btn-secondary-glow academy-btn"
							onClick={() => generateCertificate("rider")}
						>
							View Certified Hash
						</button>
					</div>
				</div>
			)}
		</div>
	);
}
