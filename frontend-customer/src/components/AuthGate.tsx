import type React from "react";
import { useState } from "react";

// VITE_API_URL is injected by the dev-server at start time.
// • Full-stack (docker-compose with gateway): defaults to http://localhost:8080
// • E2E CI / local testing (direct backend, no gateway): set VITE_API_URL=http://localhost:8083
const API = `${import.meta.env.VITE_API_URL ?? "http://localhost:8080"}/api/v1/auth`;

interface AuthSession {
	token: string;
	sessionId: string;
}

interface AuthGateProps {
	onAuth: (session: AuthSession) => void;
}

export default function AuthGate({ onAuth }: AuthGateProps) {
	const [mode, setMode] = useState<"login" | "register">("login");
	const [email, setEmail] = useState("");
	const [password, setPassword] = useState("");
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(false);

	const submit = async (e: React.FormEvent) => {
		e.preventDefault();
		setError("");
		setLoading(true);
		try {
			const endpoint = mode === "login" ? "/login" : "/register";
			const body: Record<string, string> = { email, password };
			if (mode === "login") body.deviceFingerprint = navigator.userAgent.slice(0, 64);

			const res = await fetch(`${API}${endpoint}`, {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify(body),
			});

			const data = await res.json();
			if (!res.ok) {
				setError(data.message ?? `Error ${res.status}`);
				return;
			}

			if (mode === "register") {
				// auto-login after register
				setMode("login");
				setError("Account created — please log in.");
				return;
			}

			onAuth({ token: data.token, sessionId: data.sessionId });
		} catch (err) {
			setError("Could not reach server. Is the backend running?");
		} finally {
			setLoading(false);
		}
	};

	return (
		<div className="auth-container">
			<style>{`
				.auth-container {
					display: grid;
					grid-template-columns: 1.2fr 1fr;
					min-height: 100vh;
					background: #070a13;
					color: #fff;
					font-family: "Outfit", "Inter", sans-serif;
					overflow: hidden;
					position: relative;
				}

				.auth-container::before {
					content: "";
					position: absolute;
					top: -10%;
					left: -10%;
					width: 50vw;
					height: 50vw;
					border-radius: 50%;
					background: radial-gradient(circle, rgba(16, 185, 129, 0.08) 0%, transparent 70%);
					z-index: 0;
					pointer-events: none;
				}

				.auth-left-panel {
					padding: 4rem;
					display: flex;
					flex-direction: column;
					justify-content: center;
					z-index: 1;
					background-image: 
						radial-gradient(at 0% 0%, rgba(6, 182, 212, 0.05) 0px, transparent 50%),
						radial-gradient(at 100% 100%, rgba(139, 92, 246, 0.05) 0px, transparent 50%);
				}

				.auth-right-panel {
					padding: 4rem;
					display: flex;
					align-items: center;
					justify-content: center;
					background: rgba(255, 255, 255, 0.005);
					border-left: 1px solid rgba(255, 255, 255, 0.04);
					backdrop-filter: blur(30px);
					-webkit-backdrop-filter: blur(30px);
					z-index: 1;
					position: relative;
				}

				@media (max-width: 1024px) {
					.auth-container {
						grid-template-columns: 1fr;
					}
					.auth-left-panel {
						display: none;
					}
					.auth-right-panel {
						border-left: none;
						padding: 2rem;
					}
				}

				/* Bento Grid */
				.auth-bento-grid {
					display: grid;
					grid-template-columns: repeat(2, 1fr);
					gap: 1.5rem;
					margin-top: 2rem;
				}

				.auth-bento-card {
					background: rgba(17, 24, 39, 0.55);
					border: 1px solid rgba(255, 255, 255, 0.05);
					border-radius: 16px;
					padding: 1.5rem;
					backdrop-filter: blur(20px);
					transition: all 0.4s cubic-bezier(0.165, 0.84, 0.44, 1);
				}

				.auth-bento-card:hover {
					transform: translateY(-4px);
					border-color: rgba(16, 185, 129, 0.25);
					box-shadow: 0 12px 24px rgba(16, 185, 129, 0.12);
				}

				.auth-bento-span-2 {
					grid-column: span 2;
				}

				/* Glass Form */
				.auth-glass-form {
					background: rgba(15, 23, 42, 0.75);
					border: 1px solid rgba(255, 255, 255, 0.08);
					border-radius: 24px;
					padding: 2.5rem;
					width: 100%;
					max-width: 400px;
					box-shadow: 
						0 20px 40px rgba(0, 0, 0, 0.5), 
						inset 0 0 0 1px rgba(255, 255, 255, 0.05);
					backdrop-filter: blur(20px);
				}

				.auth-input {
					width: 100%;
					padding: 0.75rem 1rem;
					border-radius: 12px;
					border: 1px solid rgba(255, 255, 255, 0.08);
					background: rgba(7, 10, 19, 0.6);
					color: #fff;
					font-size: 0.95rem;
					outline: none;
					transition: all 0.3s ease;
				}

				.auth-input:focus {
					border-color: #10b981;
					box-shadow: 0 0 15px rgba(16, 185, 129, 0.2);
					background: rgba(7, 10, 19, 0.8);
				}

				.auth-btn-primary {
					padding: 0.75rem;
					border-radius: 12px;
					border: none;
					background: linear-gradient(135deg, #10b981 0%, #059669 100%);
					color: #070a13;
					font-weight: 800;
					font-size: 0.95rem;
					cursor: pointer;
					transition: all 0.3s ease;
					box-shadow: 0 4px 15px rgba(16, 185, 129, 0.3);
				}

				.auth-btn-primary:hover {
					transform: translateY(-2px);
					box-shadow: 0 8px 25px rgba(16, 185, 129, 0.5);
				}

				.auth-btn-primary:active {
					transform: translateY(0);
				}

				.auth-mode-tab {
					flex: 1;
					padding: 0.55rem;
					border-radius: 10px;
					border: none;
					cursor: pointer;
					font-weight: 600;
					font-size: 0.85rem;
					transition: all 0.3s ease;
				}

				/* Custom animations */
				@keyframes countdown-pulse {
					0% { opacity: 0.8; }
					50% { opacity: 0.3; }
					100% { opacity: 0.8; }
				}
				.pulsing-time {
					font-family: monospace;
					font-size: 1.5rem;
					font-weight: 800;
					color: #fbbf24;
					animation: countdown-pulse 1.5s infinite;
				}

				@keyframes node-blink {
					0%, 100% { box-shadow: 0 0 4px #8b5cf6; background: #8b5cf6; }
					50% { box-shadow: 0 0 12px #06b6d4; background: #06b6d4; }
				}
				.telemetry-node {
					width: 8px;
					height: 8px;
					border-radius: 50%;
					animation: node-blink 2s infinite ease-in-out;
				}
			`}</style>

			{/* Left Column: Feature Bento Grid */}
			<div className="auth-left-panel">
				<div style={{ maxWidth: 640 }}>
					<span style={{
						color: "#10b981",
						fontSize: "0.8rem",
						fontWeight: 800,
						letterSpacing: "0.15em",
						textTransform: "uppercase"
					}}>
						Enterprise Q-Commerce Platform
					</span>
					<h2 style={{
						fontSize: "2.5rem",
						fontWeight: 800,
						margin: "0.5rem 0",
						letterSpacing: "-0.03em",
						background: "linear-gradient(135deg, #fff 30%, #94a3b8)",
						WebkitBackgroundClip: "text",
						WebkitTextFillColor: "transparent"
					}}>
						Swish Agentic OS
					</h2>
					<p style={{ color: "#94a3b8", fontSize: "0.95rem", lineHeight: 1.5, margin: "0 0 2rem" }}>
						Decentralized micro-frontends, event-driven hexagonal core, and governed AI agents guaranteeing 15-minute grocery delivery.
					</p>
				</div>

				<div className="auth-bento-grid">
					{/* Card 1: 15-Min SLA */}
					<div className="auth-bento-card">
						<div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem" }}>
							<span style={{ fontSize: "0.7rem", fontWeight: 700, color: "#94a3b8" }}>DELIVERY SLA</span>
							<span className="pulsing-time">09:59</span>
						</div>
						<h4 style={{ fontWeight: 800, fontSize: "0.95rem", margin: "0 0 0.25rem" }}>15-Minute Guarantee</h4>
						<p style={{ fontSize: "0.75rem", color: "#64748b", margin: 0 }}>
							Dynamic rider dispatching and dark store automation mapping routes instantly.
						</p>
					</div>

					{/* Card 2: AI Governance */}
					<div className="auth-bento-card">
						<div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem" }}>
							<span style={{ fontSize: "0.7rem", fontWeight: 700, color: "#94a3b8" }}>GOVERNANCE SHIELD</span>
							<span style={{
								background: "rgba(16, 185, 129, 0.1)",
								color: "#10b981",
								padding: "0.15rem 0.4rem",
								borderRadius: 4,
								fontSize: "0.6rem",
								fontWeight: 700
							}}>
								100% OK
							</span>
						</div>
						<h4 style={{ fontWeight: 800, fontSize: "0.95rem", margin: "0 0 0.25rem" }}>Llm Safety Guardrails</h4>
						<p style={{ fontSize: "0.75rem", color: "#64748b", margin: 0 }}>
							Obfuscation filters, nesting limits, and real-time RAG grounding audit.
						</p>
					</div>

					{/* Card 3: Hexagonal & SSE Telemetry */}
					<div className="auth-bento-card auth-bento-span-2">
						<div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem" }}>
							<span style={{ fontSize: "0.7rem", fontWeight: 700, color: "#94a3b8" }}>CONNECTION INTEGRITY</span>
							<div style={{ display: "flex", gap: 6, alignItems: "center" }}>
								<div className="telemetry-node" />
								<span style={{ fontSize: "0.65rem", color: "#8b5cf6", fontWeight: 700 }}>SSE Stream: ACTIVE</span>
							</div>
						</div>
						<h4 style={{ fontWeight: 800, fontSize: "0.95rem", margin: "0 0 0.25rem" }}>Federated Hexagonal Core</h4>
						<p style={{ fontSize: "0.75rem", color: "#64748b", margin: 0 }}>
							Micro-frontends synced with the Spring Boot backend via Server-Sent Events, processing real-time telemetry flows and cold chain logistics seamlessly.
						</p>
					</div>
				</div>
			</div>

			{/* Right Column: Glassmorphic Auth Form */}
			<div className="auth-right-panel">
				<div className="auth-glass-form">
					<div style={{ textAlign: "center", marginBottom: "2rem" }}>
						<h3 style={{ fontSize: "1.5rem", fontWeight: 800, margin: "0 0 0.25rem", color: "#fff" }}>
							{mode === "login" ? "Welcome Back" : "Register Account"}
						</h3>
						<p style={{ fontSize: "0.8rem", color: "#94a3b8", margin: 0 }}>
							{mode === "login" ? "Enter credentials to unlock cockpit" : "Create profile to start ordering"}
						</p>
					</div>

					{/* Mode Tabs */}
					<div style={{
						display: "flex",
						background: "rgba(255, 255, 255, 0.03)",
						padding: 4,
						borderRadius: 12,
						marginBottom: "1.5rem",
						border: "1px solid rgba(255, 255, 255, 0.05)"
					}}>
						<button
							type="button"
							onClick={() => { setMode("login"); setError(""); }}
							className="auth-mode-tab"
							style={{
								background: mode === "login" ? "#10b981" : "transparent",
								color: mode === "login" ? "#070a13" : "#94a3b8"
							}}
						>
							Log in
						</button>
						<button
							type="button"
							onClick={() => { setMode("register"); setError(""); }}
							className="auth-mode-tab"
							style={{
								background: mode === "register" ? "#10b981" : "transparent",
								color: mode === "register" ? "#070a13" : "#94a3b8"
							}}
						>
							Register
						</button>
					</div>

					<form onSubmit={submit} style={{ display: "flex", flexDirection: "column", gap: 14 }}>
						<div>
							<input
								type="email"
								placeholder="Email address"
								value={email}
								onChange={(e) => setEmail(e.target.value)}
								required
								className="auth-input"
								id="email-input"
							/>
						</div>
						<div>
							<input
								type="password"
								placeholder="Password"
								value={password}
								onChange={(e) => setPassword(e.target.value)}
								required
								className="auth-input"
								id="password-input"
							/>
						</div>
						
						{error && (
							<p style={{
								margin: 0,
								color: error.startsWith("Account") ? "#10b981" : "#ef5350",
								fontSize: "0.8rem",
								fontWeight: 600,
								background: error.startsWith("Account") ? "rgba(16, 185, 129, 0.08)" : "rgba(239, 83, 80, 0.08)",
								padding: "0.5rem 0.75rem",
								borderRadius: 8,
								border: `1px solid ${error.startsWith("Account") ? "rgba(16, 185, 129, 0.2)" : "rgba(239, 83, 80, 0.2)"}`
							}}>
								{error}
							</p>
						)}

						<button
							type="submit"
							disabled={loading}
							className="auth-btn-primary"
							style={{ marginTop: "0.5rem" }}
						>
							{loading ? "Please wait…" : mode === "login" ? "Log in" : "Create account"}
						</button>
					</form>
				</div>
			</div>
		</div>
	);
}
