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
							className="search-input"
							placeholder="Full Legal Name"
							value="Rider Dave"
							readOnly
						/>
						<input
							type="text"
							className="search-input"
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
				<div style={{ display: "flex", gap: "1.25rem" }}>
					{/* Main Rider Console */}
					<div
						style={{
							flex: 2,
							display: "flex",
							flexDirection: "column",
							gap: "1.25rem",
						}}
					>
						<div
							className="glass-card"
							style={{
								padding: "1rem",
								borderLeft: "3px solid var(--color-rider)",
							}}
						>
							<div
								style={{
									display: "flex",
									justifyContent: "space-between",
									alignItems: "center",
								}}
							>
								<h3 style={{ fontWeight: 800 }}>Rider Operations Console</h3>
								<div
									style={{ display: "flex", gap: "1rem", fontSize: "0.8rem" }}
								>
									<span>
										⭐ Trust Rating:{" "}
										<strong style={{ color: "var(--color-rider)" }}>
											{riderTrustScore}/100
										</strong>
									</span>
									<span>
										Wallet Balance:{" "}
										<strong style={{ color: "var(--color-rider)" }}>
											${riderWallet.toFixed(2)}
										</strong>
									</span>
								</div>
							</div>
						</div>

						{/* Active order transit status */}
						{!activeOrder ? (
							<div
								className="glass-card"
								style={{
									padding: "2rem",
									textAlign: "center",
									color: "var(--text-muted)",
								}}
							>
								<Lucide.Bike
									size={32}
									style={{ opacity: 0.3, marginBottom: "0.5rem" }}
								/>
								<span style={{ fontSize: "0.75rem" }}>
									Rider Dave is standby in Central Dark Store. Awaiting customer
									dispatches...
								</span>
							</div>
						) : (
							<div className="glass-card" style={{ padding: "1.25rem" }}>
								<h4
									style={{
										fontWeight: 700,
										color: "var(--color-rider)",
										marginBottom: "0.75rem",
									}}
								>
									LIVE TRANSIT DISPATCH: Order #{activeOrder.id} (
									{activeOrder.status
										? activeOrder.status.toUpperCase()
										: "TRANSIT"}
									)
								</h4>

								<div
									style={{
										display: "flex",
										gap: "1.25rem",
										marginTop: "1rem",
										flexDirection: "column",
									}}
								>
									{activeOrder.status === "arrived" ? (
										<div
											className="glass-card"
											style={{
												padding: "1rem",
												background: "rgba(16, 185, 129, 0.05)",
												border: "1px dashed var(--color-rider)",
												borderRadius: "8px",
											}}
										>
											<h5
												style={{
													fontWeight: 800,
													color: "var(--color-rider)",
													display: "flex",
													alignItems: "center",
													gap: "0.5rem",
													marginBottom: "0.5rem",
												}}
											>
												<Lucide.CheckCircle size={18} /> 📍 Arrived at
												Destination Doorstep!
											</h5>
											<p
												style={{
													fontSize: "0.7rem",
													color: "var(--text-secondary)",
													marginBottom: "1rem",
												}}
											>
												Swiss regulatory compliance requires a cryptographic
												Proof-of-Delivery (PoD) final handshake. Scan the
												customer's QR code or upload a doorstep photo.
											</p>

											<div
												style={{
													display: "flex",
													gap: "0.75rem",
													marginBottom: "1rem",
												}}
											>
												<button
													className="btn-secondary-glow"
													style={{
														flex: 1,
														fontSize: "0.75rem",
														display: "flex",
														alignItems: "center",
														justifyContent: "center",
														gap: "0.25rem",
														cursor: "pointer",
														padding: "0.5rem",
													}}
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
																`Rider Dave captured doorstep delivery verification photo.`,
															);
													}}
												>
													<Lucide.Camera size={14} /> Doorstep Photo
												</button>
												<button
													className="btn-secondary-glow"
													style={{
														flex: 1,
														fontSize: "0.75rem",
														display: "flex",
														alignItems: "center",
														justifyContent: "center",
														gap: "0.25rem",
														cursor: "pointer",
														padding: "0.5rem",
													}}
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
																`Rider Dave scanned customer receipt verification QR code.`,
															);
													}}
												>
													<Lucide.QrCode size={14} /> Scan QR Code
												</button>
											</div>

											{podHash && (
												<div
													style={{
														background: "#020408",
														padding: "0.5rem",
														borderRadius: "4px",
														marginBottom: "1rem",
														border: "1px solid rgba(255,255,255,0.05)",
													}}
												>
													<div
														style={{
															display: "flex",
															justifyContent: "space-between",
															fontSize: "0.6rem",
															color: "var(--text-muted)",
														}}
													>
														<span>PROOF TYPE: {proofType.toUpperCase()}</span>
														<span>STATUS: GENERATED</span>
													</div>
													<div
														style={{
															fontFamily: "var(--font-mono)",
															fontSize: "0.65rem",
															wordBreak: "break-all",
															marginTop: "0.25rem",
															color: "var(--color-rider)",
														}}
													>
														{podHash}
													</div>
												</div>
											)}

											<button
												className="btn-primary-glow"
												disabled={!podHash}
												style={{
													width: "100%",
													background: podHash
														? "var(--color-rider)"
														: "#27293d",
													color: podHash ? "#070a13" : "var(--text-muted)",
													border: "none",
													padding: "0.6rem",
													cursor: podHash ? "pointer" : "not-allowed",
													fontWeight: 800,
													fontSize: "0.75rem",
												}}
												onClick={() => {
													if (handleCompleteDelivery) {
														handleCompleteDelivery(activeOrder, podHash);
														setPodHash("");
														setProofType("");
													}
												}}
											>
												🚀 Sign & Complete Delivery Handshake
											</button>
										</div>
									) : (
										<div style={{ display: "flex", gap: "1.25rem" }}>
											{/* Transit progress */}
											<div style={{ flex: 1 }}>
												<span
													style={{
														fontSize: "0.65rem",
														fontWeight: 700,
														color: "var(--text-secondary)",
													}}
												>
													DELIVERY TRANSIT PROGRESS
												</span>
												<div
													style={{
														background: "#020408",
														height: "10px",
														borderRadius: "99px",
														marginTop: "0.25rem",
														overflow: "hidden",
													}}
												>
													<div
														style={{
															background: "var(--color-rider)",
															height: "100%",
															width: `${activeOrder.progress || 0}%`,
															transition: "width 0.5s ease",
														}}
													/>
												</div>
												<span
													style={{
														fontSize: "0.7rem",
														color: "var(--text-muted)",
														display: "block",
														marginTop: "0.25rem",
													}}
												>
													SLA Timer: {activeOrder.slaRemaining || 0}s remaining
												</span>
											</div>

											{/* Cold Chain IoT telemetry block */}
											{isPerishable && (
												<div
													style={{
														flex: 1,
														background: "rgba(255,255,255,0.01)",
														border: "1px solid var(--border-color)",
														borderRadius: "8px",
														padding: "0.75rem",
													}}
												>
													<div
														style={{
															display: "flex",
															justifyContent: "space-between",
															alignItems: "center",
														}}
													>
														<span
															style={{
																fontSize: "0.65rem",
																fontWeight: 700,
																color: "var(--text-secondary)",
															}}
														>
															IoT COLD CHAIN TEMP
														</span>
														{temp > 8.0 && (
															<span
																className="warning-flag"
																style={{ animation: "pulse 1s infinite" }}
															>
																⚠️ OVERHEATING
															</span>
														)}
													</div>
													<div
														style={{
															fontSize: "1.5rem",
															fontWeight: 800,
															color:
																temp > 8.0
																	? "var(--color-admin)"
																	: "var(--color-rider)",
															fontFamily: "var(--font-mono)",
															margin: "0.25rem 0",
														}}
													>
														{temp.toFixed(1)} °C
													</div>
													{temp > 8.0 && (
														<button
															id="btn-inject-dry-ice"
															className="btn-primary-glow"
															style={{
																background: "var(--color-rider)",
																color: "#070a13",
																border: "none",
																padding: "0.3rem 0.5rem",
																width: "100%",
																fontSize: "0.7rem",
																cursor: "pointer",
																fontWeight: 800,
															}}
															onClick={handleInjectDryIce}
														>
															❄️ Inject Dry Ice ($2.00 Surcharge)
														</button>
													)}
												</div>
											)}
										</div>
									)}
								</div>
							</div>
						)}
					</div>

					{/* Academy Certifications */}
					<div
						style={{
							width: "280px",
							display: "flex",
							flexDirection: "column",
							gap: "1.25rem",
						}}
					>
						<div className="glass-card" style={{ padding: "1rem" }}>
							<h4 style={{ fontWeight: 800, marginBottom: "0.5rem" }}>
								Rider Training Hub
							</h4>
							<p style={{ fontSize: "0.7rem", color: "var(--text-muted)" }}>
								Complete courses on cargo handling, cold chain sensors, and
								logistics security to earn certificates and bonuses.
							</p>
							<button
								className="btn-secondary-glow"
								style={{
									width: "100%",
									fontSize: "0.75rem",
									marginTop: "0.75rem",
									cursor: "pointer",
								}}
								onClick={() => generateCertificate("rider")}
							>
								View IoT Logistics Cert
							</button>
						</div>
					</div>
				</div>
			)}
		</div>
	);
}
