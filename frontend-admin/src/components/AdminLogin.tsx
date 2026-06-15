// Phase 8B — minimal admin login gate. The HITL endpoints require an ADMIN JWT,
// and frontend-admin had no auth, so this exchanges credentials for a token via
// /api/v1/auth/login. No credentials are stored in source; the email is merely
// pre-filled with the conventional dev admin for convenience.
import type React from "react";
import { useState } from "react";

interface Props {
	onLogin: (email: string, password: string) => Promise<void>;
}

export default function AdminLogin({ onLogin }: Props) {
	const [email, setEmail] = useState("admin@swish.local");
	const [password, setPassword] = useState("");
	const [busy, setBusy] = useState(false);
	const [err, setErr] = useState<string | null>(null);

	const submit = async (e: React.FormEvent) => {
		e.preventDefault();
		setBusy(true);
		setErr(null);
		try {
			await onLogin(email, password);
		} catch (e2) {
			setErr(e2 instanceof Error ? e2.message : "Login failed");
		} finally {
			setBusy(false);
		}
	};

	const field: React.CSSProperties = {
		width: "100%",
		padding: "0.6rem 0.7rem",
		marginTop: "0.35rem",
		background: "rgba(255,255,255,0.06)",
		border: "1px solid rgba(255,255,255,0.15)",
		borderRadius: 6,
		color: "#fff",
		fontSize: "0.85rem",
	};

	return (
		<form
			onSubmit={submit}
			style={{
				maxWidth: 360,
				margin: "2rem auto",
				padding: "1.5rem",
				background: "rgba(255,255,255,0.04)",
				border: "1px solid rgba(255,255,255,0.1)",
				borderRadius: 10,
			}}
		>
			<h3 style={{ margin: "0 0 0.25rem", fontWeight: 800 }}>
				🔐 Supervisor Sign-in
			</h3>
			<p
				style={{
					fontSize: "0.72rem",
					color: "rgba(255,255,255,0.5)",
					marginBottom: "1rem",
				}}
			>
				ADMIN credentials required to view and action the HITL governance queue.
			</p>

			<label style={{ fontSize: "0.72rem", color: "rgba(255,255,255,0.7)" }}>
				Email
				<input
					type="email"
					value={email}
					onChange={(e) => setEmail(e.target.value)}
					autoComplete="username"
					style={field}
					required
				/>
			</label>

			<label
				style={{
					fontSize: "0.72rem",
					color: "rgba(255,255,255,0.7)",
					display: "block",
					marginTop: "0.75rem",
				}}
			>
				Password
				<input
					type="password"
					value={password}
					onChange={(e) => setPassword(e.target.value)}
					autoComplete="current-password"
					style={field}
					required
				/>
			</label>

			{err && (
				<div
					style={{
						marginTop: "0.75rem",
						color: "#f87171",
						fontSize: "0.75rem",
					}}
				>
					⚠ {err}
				</div>
			)}

			<button
				type="submit"
				disabled={busy}
				style={{
					marginTop: "1.1rem",
					width: "100%",
					padding: "0.6rem",
					background: busy ? "rgba(59,130,246,0.5)" : "#3b82f6",
					border: "none",
					borderRadius: 6,
					color: "#fff",
					fontWeight: 700,
					cursor: busy ? "default" : "pointer",
				}}
			>
				{busy ? "Signing in…" : "Sign in"}
			</button>
		</form>
	);
}
