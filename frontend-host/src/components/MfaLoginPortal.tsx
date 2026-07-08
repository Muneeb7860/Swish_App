import * as Lucide from "lucide-react";

interface ActiveProfile {
	logLevel: string;
	mfaRequired: boolean;
	[key: string]: unknown;
}

interface MfaLoginPortalProps {
	isAuthenticated: boolean;
	activeProfile: ActiveProfile | null;
	mfaStep: "credentials" | "otp";
	setMfaStep: (step: "credentials" | "otp") => void;
	mfaRole: string;
	setMfaRole: (role: string) => void;
	mfaPassword: string;
	setMfaPassword: (pw: string) => void;
	mfaOtpInput: string;
	setMfaOtpInput: (otp: string) => void;
	mfaMethod: "sms" | "totp";
	setMfaMethod: (method: "sms" | "totp") => void;
	totpSecretCode: string;
	totpTimer: number;
	handleMfaSendOtp: () => void;
	handleMfaVerify: () => void;
}

export default function MfaLoginPortal({
	isAuthenticated,
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
}: MfaLoginPortalProps) {
	if (isAuthenticated) return null;

	return (
		<div className="mfa-overlay" id="mfa-login-portal">
			<div className="mfa-card glow-card" data-glow-theme="engine">
				<div className="mfa-logo-box">
					<div className="mfa-logo-icon">
						<Lucide.ShieldCheck size={28} />
					</div>
					<h3 className="text-white font-extrabold text-lg tracking-wider mt-2">
						SWISS SECURE SHIELD
					</h3>
					<span className="text-xs font-bold tracking-wider text-cyan-400 mt-1">
						MULTI-FACTOR AUTHENTICATION PORTAL
					</span>
				</div>

				{mfaStep === "credentials" ? (
					<div className="mfa-form-layout">
						<div className="mfa-field-group">
							<label htmlFor="mfa-select-role" className="mfa-label">
								Authentication Profile (Role)
							</label>
							<select
								id="mfa-select-role"
								aria-label="Select Authentication Profile"
								value={mfaRole}
								onChange={(e) => setMfaRole(e.target.value)}
								className="mfa-select"
							>
								<option value="customer">Customer Super App</option>
								<option value="rider">Rider Light</option>
								<option value="inventory">Dark Store Inventory</option>
								<option value="business">Business Web Console</option>
								<option value="admin">System Admin Cockpit</option>
							</select>
						</div>

						<div className="mfa-field-group">
							<label htmlFor="input-mfa-password" className="mfa-label">
								Account Password
							</label>
							<input
								id="input-mfa-password"
								type="password"
								placeholder="Enter account security key"
								value={mfaPassword}
								onChange={(e) => setMfaPassword(e.target.value)}
								className="mfa-input"
							/>
						</div>

						<div className="mfa-field-group">
							<label htmlFor="select-mfa-method" className="mfa-label">
								MFA Verification Path
							</label>
							<select
								id="select-mfa-method"
								value={mfaMethod}
								onChange={(e) => setMfaMethod(e.target.value as "sms" | "totp")}
								className="mfa-select"
							>
								<option value="sms">Mock SMS Gateway Pin Broadcast</option>
								<option value="totp">Google Authenticator TOTP Rotation</option>
							</select>
						</div>

						<button
							type="button"
							aria-label="Button"
							id="btn-mfa-request-otp"
							className="mfa-button-primary mt-2"
							onClick={handleMfaSendOtp}
						>
							Verify Credentials & Proceed
						</button>
					</div>
				) : (
					<div className="mfa-form-layout">
						<div className="mfa-info-panel">
							{mfaMethod === "sms" ? (
								<p className="mfa-info-text">
									We sent a 6-digit OTP to your registered phone. Check the
									Kafka console or browser toast notifications for the
									broadcast.
								</p>
							) : (
								<div className="flex flex-col gap-1">
									<p className="mfa-info-text">
										Enter the 6-digit Google Authenticator TOTP code.
									</p>
									<div className="mfa-totp-token">
										Active Token Key: {totpSecretCode} ({totpTimer}s remaining)
									</div>
								</div>
							)}
						</div>

						<div className="mfa-field-group">
							<label htmlFor="input-mfa-otp" className="mfa-label">
								6-Digit Verification Code
							</label>
							<input
								id="input-mfa-otp"
								type="text"
								maxLength={6}
								placeholder="••••••"
								value={mfaOtpInput}
								onChange={(e) => setMfaOtpInput(e.target.value)}
								className="mfa-input mfa-input-otp"
							/>
						</div>

						<button
							type="button"
							aria-label="Button"
							id="btn-mfa-verify-otp"
							className="mfa-button-primary"
							onClick={handleMfaVerify}
						>
							Authenticate and Unlock
						</button>

						<button
							type="button"
							aria-label="Button"
							id="btn-mfa-back"
							className="mfa-button-secondary"
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
