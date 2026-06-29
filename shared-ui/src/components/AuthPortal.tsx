import type React from "react";
import { useState } from "react";

export interface AuthSession {
	token: string;
	sessionId?: string;
	userId?: string;
	role?: string;
}

export type AuthRole = "customer" | "admin" | "rider";
export type MfaMethod = "sms" | "totp";

export interface AuthPortalProps {
	role: AuthRole;
	mfaEnabled?: boolean;
	mfaMethods?: MfaMethod[];
	apiUrl?: string;
	onAuthSuccess: (session: AuthSession) => void;
	onAuthError?: (error: Error) => void;
	defaultEmail?: string;
	formLabel?: string;
	submitLabel?: string;
}

/**
 * Unified authentication portal for multi-role applications.
 * Handles login, registration, and MFA flows.
 *
 * Consolidates from:
 * - frontend-customer/AuthGate.tsx (login + register dual-mode)
 * - frontend-admin/AdminLogin.tsx (admin JWT exchange)
 * - frontend-host/MfaLoginPortal.tsx (MFA + TOTP + SMS)
 *
 * @example
 * ```tsx
 * // Customer with MFA
 * <AuthPortal
 *   role="customer"
 *   mfaEnabled={true}
 *   onAuthSuccess={(session) => handleAuth(session)}
 * />
 *
 * // Admin login
 * <AuthPortal
 *   role="admin"
 *   defaultEmail="admin@swish.local"
 *   onAuthSuccess={(session) => handleAuth(session)}
 * />
 * ```
 */
export const AuthPortal: React.FC<AuthPortalProps> = ({
	role,
	mfaEnabled = false,
	mfaMethods = ["sms", "totp"],
	apiUrl = `${import.meta.env.VITE_API_URL ?? "http://localhost:8080"}/api/v1/auth`,
	onAuthSuccess,
	onAuthError,
	defaultEmail = role === "admin" ? "admin@swish.local" : "",
	formLabel = `${role.charAt(0).toUpperCase() + role.slice(1)} Login`,
	submitLabel = "Sign In",
}) => {
	// All roles start in "login"; only the customer role exposes the register toggle.
	// (Previously initialised to an invalid "credentials" value for non-customer roles,
	// which mistyped the state and made the rider role POST to /register on submit.)
	const [mode, setMode] = useState<"login" | "register">("login");
	const [step, setStep] = useState<"credentials" | "mfa">("credentials");

	const [email, setEmail] = useState(defaultEmail);
	const [password, setPassword] = useState("");
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(false);

	const [mfaMethod, setMfaMethod] = useState<MfaMethod>("sms");
	const [mfaOtp, setMfaOtp] = useState("");
	const [mfaSecret, setMfaSecret] = useState("");

	const handleSubmit = async (e: React.FormEvent) => {
		e.preventDefault();
		setError("");
		setLoading(true);

		try {
			const endpoint =
				role === "admin" ? "/login" : mode === "login" ? "/login" : "/register";
			const body: Record<string, unknown> = { email, password };

			if (role === "customer" && mode === "login") {
				body.deviceFingerprint = navigator.userAgent.slice(0, 64);
			}

			const response = await fetch(`${apiUrl}${endpoint}`, {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify(body),
			});

			const data = await response.json();

			if (!response.ok) {
				throw new Error(data.message ?? `Error ${response.status}`);
			}

			if (mode === "register") {
				setMode("login");
				setError("Account created — please log in.");
				setLoading(false);
				return;
			}

			if (mfaEnabled && data.mfaRequired) {
				setMfaSecret(data.mfaSecret);
				setStep("mfa");
				setLoading(false);
				return;
			}

			onAuthSuccess({
				token: data.token,
				sessionId: data.sessionId,
				userId: data.userId,
				role: data.role ?? role,
			});
		} catch (err) {
			const error = err instanceof Error ? err : new Error("Unknown error");
			setError(error.message);
			onAuthError?.(error);
		} finally {
			setLoading(false);
		}
	};

	const handleMfaVerify = async () => {
		setLoading(true);
		setError("");

		try {
			const response = await fetch(`${apiUrl}/verify-mfa`, {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({
					mfaSecret,
					otp: mfaOtp,
					method: mfaMethod,
				}),
			});

			const data = await response.json();

			if (!response.ok) {
				throw new Error(data.message ?? "MFA verification failed");
			}

			onAuthSuccess({
				token: data.token,
				sessionId: data.sessionId,
				userId: data.userId,
			});
		} catch (err) {
			const error = err instanceof Error ? err : new Error("Unknown error");
			setError(error.message);
			onAuthError?.(error);
		} finally {
			setLoading(false);
		}
	};

	const containerStyle: React.CSSProperties = {
		maxWidth: 400,
		margin: "2rem auto",
		padding: "2rem",
		background: "var(--bg-glass)",
		border: "1px solid var(--border-default)",
		borderRadius: "var(--radius-lg)",
		backdropFilter: "blur(20px)",
	};

	const fieldStyle: React.CSSProperties = {
		width: "100%",
		padding: "0.75rem",
		marginTop: "0.5rem",
		background: "var(--bg-root)",
		border: "1px solid var(--border-default)",
		borderRadius: "var(--radius-md)",
		color: "var(--text-primary)",
		fontSize: "var(--text-sm)",
		fontFamily: "var(--font-sans)",
	};

	if (step === "mfa") {
		return (
			<div style={containerStyle}>
				<h2 style={{ marginBottom: "1rem", color: "var(--text-primary)" }}>
					Verify Your Identity
				</h2>

				{mfaMethods.includes("sms") && (
					<label
						style={{
							display: "flex",
							alignItems: "center",
							marginBottom: "1rem",
						}}
					>
						<input
							type="radio"
							checked={mfaMethod === "sms"}
							onChange={() => setMfaMethod("sms")}
						/>
						<span
							style={{ marginLeft: "0.5rem", color: "var(--text-secondary)" }}
						>
							SMS Code
						</span>
					</label>
				)}

				{mfaMethods.includes("totp") && (
					<label
						style={{
							display: "flex",
							alignItems: "center",
							marginBottom: "1rem",
						}}
					>
						<input
							type="radio"
							checked={mfaMethod === "totp"}
							onChange={() => setMfaMethod("totp")}
						/>
						<span
							style={{ marginLeft: "0.5rem", color: "var(--text-secondary)" }}
						>
							Authenticator App
						</span>
					</label>
				)}

				<input
					type="text"
					placeholder="Enter 6-digit code"
					value={mfaOtp}
					onChange={(e) => setMfaOtp(e.target.value.slice(0, 6))}
					style={fieldStyle}
					maxLength={6}
				/>

				{error && (
					<div
						style={{
							marginTop: "0.5rem",
							padding: "0.5rem",
							background: "var(--error-muted)",
							color: "var(--error)",
							borderRadius: "var(--radius-sm)",
							fontSize: "var(--text-xs)",
						}}
					>
						{error}
					</div>
				)}

				<button
					type="button"
					onClick={handleMfaVerify}
					disabled={loading || mfaOtp.length < 6}
					style={{
						width: "100%",
						marginTop: "1rem",
						padding: "0.75rem",
						background: loading ? "var(--accent-muted)" : "var(--accent)",
						color: "#fff",
						border: "none",
						borderRadius: "var(--radius-md)",
						cursor: loading ? "not-allowed" : "pointer",
						fontWeight: 600,
						opacity: loading ? 0.6 : 1,
					}}
				>
					{loading ? "Verifying..." : "Verify"}
				</button>
			</div>
		);
	}

	return (
		<div style={containerStyle}>
			<h2
				style={{
					marginBottom: "1rem",
					textAlign: "center",
					color: "var(--text-primary)",
				}}
			>
				{formLabel}
			</h2>

			{role === "customer" && (
				<p
					style={{
						textAlign: "center",
						color: "var(--text-secondary)",
						fontSize: "var(--text-sm)",
						marginTop: "-0.5rem",
						marginBottom: "1.5rem",
					}}
				>
					15-minute grocery delivery
				</p>
			)}

			<form onSubmit={handleSubmit}>
				<div>
					<label
						htmlFor="email-input"
						style={{
							display: "block",
							fontSize: "var(--text-sm)",
							color: "var(--text-secondary)",
						}}
					>
						Email
					</label>
					<input
						id="email-input"
						type="email"
						value={email}
						onChange={(e) => setEmail(e.target.value)}
						required
						style={fieldStyle}
					/>
				</div>

				<div style={{ marginTop: "1rem" }}>
					<label
						htmlFor="password-input"
						style={{
							display: "block",
							fontSize: "var(--text-sm)",
							color: "var(--text-secondary)",
						}}
					>
						Password
					</label>
					<input
						id="password-input"
						type="password"
						value={password}
						onChange={(e) => setPassword(e.target.value)}
						required
						style={fieldStyle}
					/>
				</div>

				{error && (
					<p
						style={{
							marginTop: "1rem",
							padding: "0.75rem",
							background: "var(--error-muted)",
							color: "rgb(239, 83, 80)",
							borderRadius: "var(--radius-sm)",
							fontSize: "var(--text-xs)",
							margin: 0,
						}}
					>
						{error}
					</p>
				)}

				<button
					type="submit"
					disabled={loading}
					style={{
						width: "100%",
						marginTop: "1.5rem",
						padding: "0.75rem",
						background: loading ? "var(--accent-muted)" : "var(--accent)",
						color: "#fff",
						border: "none",
						borderRadius: "var(--radius-md)",
						cursor: loading ? "not-allowed" : "pointer",
						fontWeight: 600,
						fontSize: "var(--text-sm)",
						opacity: loading ? 0.6 : 1,
					}}
				>
					{loading
						? mode === "register"
							? "Creating account..."
							: "Signing in..."
						: role === "customer"
							? mode === "register"
								? "Create account"
								: "Log in"
							: submitLabel}
				</button>
			</form>

			{role === "customer" && (
				<div
					style={{
						marginTop: "1rem",
						textAlign: "center",
						fontSize: "var(--text-xs)",
						color: "var(--text-muted)",
					}}
				>
					{mode === "login" ? (
						<>
							Don't have an account?{" "}
							<button
								type="button"
								onClick={() => setMode("register")}
								style={{
									background: "none",
									border: "none",
									color: "var(--accent)",
									cursor: "pointer",
									textDecoration: "underline",
								}}
							>
								Register
							</button>
						</>
					) : (
						<>
							Already have an account?{" "}
							<button
								type="button"
								onClick={() => setMode("login")}
								style={{
									background: "none",
									border: "none",
									color: "var(--accent)",
									cursor: "pointer",
									textDecoration: "underline",
								}}
							>
								Login
							</button>
						</>
					)}
				</div>
			)}
		</div>
	);
};
