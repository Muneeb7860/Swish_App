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
		<div
			style={{
				display: "flex",
				flexDirection: "column",
				alignItems: "center",
				justifyContent: "center",
				minHeight: "100vh",
				background: "#0b0f19",
				color: "#fff",
				fontFamily: "sans-serif",
			}}
		>
			<div
				style={{
					background: "#161b2e",
					borderRadius: 12,
					padding: "2rem 2.5rem",
					width: 360,
					boxShadow: "0 4px 32px rgba(0,0,0,0.4)",
				}}
			>
				<h1 style={{ margin: "0 0 0.25rem", fontSize: "1.6rem" }}>Swish</h1>
				<p style={{ margin: "0 0 1.5rem", color: "#8b9abf", fontSize: "0.9rem" }}>
					15-minute grocery delivery
				</p>

				<div style={{ display: "flex", gap: 8, marginBottom: "1.5rem" }}>
					{(["login", "register"] as const).map((m) => (
						<button
							key={m}
							type="button"
							onClick={() => { setMode(m); setError(""); }}
							style={{
								flex: 1,
								padding: "0.5rem",
								borderRadius: 8,
								border: "none",
								cursor: "pointer",
								background: mode === m ? "#5b6ef7" : "#252d47",
								color: mode === m ? "#fff" : "#8b9abf",
								fontWeight: mode === m ? 600 : 400,
								fontSize: "0.9rem",
							}}
						>
							{m === "login" ? "Log in" : "Register"}
						</button>
					))}
				</div>

				<form onSubmit={submit} style={{ display: "flex", flexDirection: "column", gap: 12 }}>
					<input
						type="email"
						placeholder="Email"
						value={email}
						onChange={(e) => setEmail(e.target.value)}
						required
						style={inputStyle}
					/>
					<input
						type="password"
						placeholder="Password"
						value={password}
						onChange={(e) => setPassword(e.target.value)}
						required
						style={inputStyle}
					/>
					{error && (
						<p style={{ margin: 0, color: error.startsWith("Account") ? "#4caf50" : "#ef5350", fontSize: "0.85rem" }}>
							{error}
						</p>
					)}
					<button
						type="submit"
						disabled={loading}
						style={{
							padding: "0.65rem",
							borderRadius: 8,
							border: "none",
							background: "#5b6ef7",
							color: "#fff",
							fontWeight: 600,
							fontSize: "1rem",
							cursor: loading ? "not-allowed" : "pointer",
							opacity: loading ? 0.7 : 1,
							marginTop: 4,
						}}
					>
						{loading ? "Please wait…" : mode === "login" ? "Log in" : "Create account"}
					</button>
				</form>
			</div>
		</div>
	);
}

const inputStyle: React.CSSProperties = {
	padding: "0.6rem 0.75rem",
	borderRadius: 8,
	border: "1px solid #2d3754",
	background: "#0d1120",
	color: "#fff",
	fontSize: "0.95rem",
	outline: "none",
};
