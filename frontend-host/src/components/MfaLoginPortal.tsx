import * as Lucide from "lucide-react";
import React from "react";

export default function MfaLoginPortal({
	isAuthenticated,
	activeProfile,
	mfaStep,
	setMfaStep,
	mfaRole,
	setMfaRole,
	mfaPassword,
	setMfaPassword,
	mfaOtpInput,
	setMfaOtpInput,
	mfaMethod,
	setMfaMethod,
	totpSecretCode,
	totpTimer,
	handleMfaSendOtp,
	handleMfaVerify,
}) {
	if (isAuthenticated) return null;

	return (
		<div className="mfa-overlay" id="mfa-login-portal">
			<div className="mfa-card">
				<div className="mfa-logo-box">
					<div className="mfa-logo-icon">
						<Lucide.ShieldCheck size={28} />
					</div>
					<h3
						style={{
							color: "#ffffff",
							fontWeight: 800,
							margin: "0.5rem 0 0 0",
							letterSpacing: "1px",
							fontFamily: "var(--font-display)",
						}}
					>
						SWISS SECURE SHIELD
					</h3>
					<span
						style={{
							fontSize: "0.65rem",
							color: "var(--color-engine)",
							fontWeight: "bold",
							fontFamily: "var(--font-sans)",
						}}
					>
						MULTI-FACTOR AUTHENTICATION PORTAL
					</span>
				</div>

				{mfaStep === "credentials" ? (
					<div
						style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}
					>
						<div
							style={{
								display: "flex",
								flexDirection: "column",
								gap: "0.25rem",
							}}
						>
							<label
								style={{
									fontSize: "0.65rem",
									fontWeight: 700,
									color: "var(--text-secondary)",
								}}
							>
								AUTHENTICATION PROFILE (ROLE)
							</label>
							<select
								id="mfa-select-role"
								aria-label="Select Authentication Profile"
								value={mfaRole}
								onChange={(e) => setMfaRole(e.target.value)}
								style={{
									width: "100%",
									background: "#020408",
									border: "1px solid var(--border-color)",
									borderRadius: "6px",
									color: "var(--text-primary)",
									padding: "0.4rem",
									fontSize: "0.85rem",
									fontFamily: "var(--font-sans)",
								}}
							>
								<option value="customer">Customer Super App</option>
								<option value="rider">Rider Light</option>
								<option value="inventory">Dark Store Inventory</option>
								<option value="business">Business Web Console</option>
								<option value="admin">System Admin Cockpit</option>
							</select>
						</div>

						<div
							style={{
								display: "flex",
								flexDirection: "column",
								gap: "0.25rem",
							}}
						>
							<label
								style={{
									fontSize: "0.65rem",
									fontWeight: 700,
									color: "var(--text-secondary)",
								}}
							>
								ACCOUNT PASSWORD
							</label>
							<input
								id="input-mfa-password"
								type="password"
								placeholder="Enter account security key"
								value={mfaPassword}
								onChange={(e) => setMfaPassword(e.target.value)}
								style={{
									width: "100%",
									background: "#020408",
									border: "1px solid var(--border-color)",
									borderRadius: "6px",
									color: "var(--text-primary)",
									padding: "0.4rem",
									fontSize: "0.85rem",
									fontFamily: "var(--font-mono)",
								}}
							/>
						</div>

						<div
							style={{
								display: "flex",
								flexDirection: "column",
								gap: "0.25rem",
							}}
						>
							<label
								style={{
									fontSize: "0.65rem",
									fontWeight: 700,
									color: "var(--text-secondary)",
								}}
							>
								MFA VERIFICATION PATH
							</label>
							<select
								id="select-mfa-method"
								value={mfaMethod}
								onChange={(e) => setMfaMethod(e.target.value)}
								style={{
									width: "100%",
									background: "#020408",
									border: "1px solid var(--border-color)",
									borderRadius: "6px",
									color: "var(--text-primary)",
									padding: "0.4rem",
									fontSize: "0.85rem",
									fontFamily: "var(--font-sans)",
								}}
							>
								<option value="sms">Mock SMS Gateway Pin Broadcast</option>
								<option value="totp">Google Authenticator TOTP Rotation</option>
							</select>
						</div>

						<button
							aria-label="Button"
							id="btn-mfa-request-otp"
							className="btn-primary-glow"
							style={{
								background: "var(--color-engine)",
								color: "#ffffff",
								border: "none",
								padding: "0.5rem",
								width: "100%",
								cursor: "pointer",
								marginTop: "0.5rem",
								fontFamily: "var(--font-sans)",
								fontWeight: "bold",
							}}
							onClick={handleMfaSendOtp}
						>
							Verify Credentials & Proceed
						</button>
					</div>
				) : (
					<div
						style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}
					>
						<div
							style={{
								background: "rgba(6, 182, 212, 0.05)",
								border: "1px solid rgba(6, 182, 212, 0.2)",
								padding: "0.5rem",
								borderRadius: "6px",
								textAlign: "center",
							}}
						>
							{mfaMethod === "sms" ? (
								<p
									style={{
										fontSize: "0.75rem",
										color: "var(--text-secondary)",
										margin: 0,
										fontFamily: "var(--font-sans)",
									}}
								>
									We sent a 6-digit OTP to your registered phone. Check the
									Kafka console or browser toast notifications for the
									broadcast.
								</p>
							) : (
								<div
									style={{
										display: "flex",
										flexDirection: "column",
										gap: "0.2rem",
									}}
								>
									<p
										style={{
											fontSize: "0.75rem",
											color: "var(--text-secondary)",
											margin: 0,
											fontFamily: "var(--font-sans)",
										}}
									>
										Enter the 6-digit Google Authenticator TOTP code.
									</p>
									<div
										style={{
											fontSize: "0.85rem",
											color: "var(--color-engine)",
											fontWeight: 800,
											fontFamily: "var(--font-mono)",
										}}
									>
										Active Token Key: {totpSecretCode} ({totpTimer}s remaining)
									</div>
								</div>
							)}
						</div>

						<div
							style={{
								display: "flex",
								flexDirection: "column",
								gap: "0.25rem",
							}}
						>
							<label
								style={{
									fontSize: "0.65rem",
									fontWeight: 700,
									color: "var(--text-secondary)",
								}}
							>
								6-DIGIT VERIFICATION CODE
							</label>
							<input
								id="input-mfa-otp"
								type="text"
								maxLength={6}
								placeholder="••••••"
								value={mfaOtpInput}
								onChange={(e) => setMfaOtpInput(e.target.value)}
								style={{
									width: "100%",
									background: "#020408",
									border: "1px solid var(--border-color)",
									borderRadius: "6px",
									color: "var(--text-primary)",
									padding: "0.4rem",
									fontSize: "0.85rem",
									letterSpacing: "4px",
									textAlign: "center",
									fontFamily: "var(--font-mono)",
								}}
							/>
						</div>

						<button
							aria-label="Button"
							id="btn-mfa-verify-otp"
							className="btn-primary-glow"
							style={{
								background: "var(--color-engine)",
								color: "#ffffff",
								border: "none",
								padding: "0.5rem",
								width: "100%",
								cursor: "pointer",
								fontFamily: "var(--font-sans)",
								fontWeight: "bold",
							}}
							onClick={handleMfaVerify}
						>
							Authenticate and Unlock
						</button>

						<button
							aria-label="Button"
							id="btn-mfa-back"
							className="btn-secondary-glow"
							style={{
								width: "100%",
								padding: "0.5rem",
								cursor: "pointer",
								fontFamily: "var(--font-sans)",
							}}
							onClick={() => setMfaStep("credentials")}
						>
							Back to Credentials
						</button>
					</div>
				)}
			</div>
		</div>
	);
}
