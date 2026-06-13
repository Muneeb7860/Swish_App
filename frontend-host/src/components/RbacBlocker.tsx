import * as Lucide from "lucide-react";
import React, { useEffect, useState } from "react";
import { useMouseMoveGlow } from "../hooks/useMouseMoveGlow";

export default function RbacBlocker({
	targetRole,
	currentUserSession,
	handleLogout,
	logKafka,
	triggerToast,
}) {
	const glowRef = useMouseMoveGlow<HTMLDivElement>();
	const [securityLogs, setSecurityLogs] = useState<string[]>([]);

	useEffect(() => {
		if (logKafka) {
			logKafka(
				"system",
				"security.rbac_violation",
				`UNAUTHORIZED: Role [${currentUserSession?.role?.toUpperCase()}] blocked from accessing [${targetRole.toUpperCase()}] dashboard.`,
			);
		}
		if (triggerToast) {
			triggerToast(
				`403 FORBIDDEN: Access denied to ${targetRole} environment`,
				"admin",
			);
		}

		// Initialize mockup security logs to look realistic and high-tech
		const timestamp = new Date()
			.toISOString()
			.replace("T", " ")
			.substring(0, 19);
		setSecurityLogs([
			`[${timestamp}] SEC_ENFORCE: Access intercept triggered on /dashboard/${targetRole}`,
			`[${timestamp}] AUTH_AUDIT: Session ID ${currentUserSession?.token || "sess-unknown"} inspected`,
			`[${timestamp}] RBAC_DENY: Subject role [${currentUserSession?.role?.toUpperCase()}] lacks [ACCESS_${targetRole.toUpperCase()}]`,
			`[${timestamp}] GEOSHIELD: Geofence token signature verified`,
			`[${timestamp}] CAPTCHA_AUDIT: Zero-knowledge proofs active`,
		]);
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [targetRole]);

	const handleEscalate = () => {
		if (logKafka) {
			logKafka(
				"system",
				"security.privilege_escalation_attempt",
				`Session role ${currentUserSession?.role} requested privilege elevation for ${targetRole}`,
			);
		}
		if (triggerToast) {
			triggerToast("Elevation request forwarded to Security Ops.", "system");
		}
		const timestamp = new Date()
			.toISOString()
			.replace("T", " ")
			.substring(0, 19);
		setSecurityLogs((prev) => [
			...prev,
			`[${timestamp}] SEC_ALERT: Privilege elevation request generated for role ${targetRole.toUpperCase()}`,
			`[${timestamp}] SEC_ALERT: Dispatched ticket to Security Operations. Status: PENDING`,
		]);
	};

	return (
		<div
			ref={glowRef}
			className="glow-card glass-card rbac-blocker-container"
			data-glow-theme="admin"
		>
			{/* High-tech scanning radar ring wrapper */}
			<div className="rbac-scanner-outer">
				<div className="rbac-scanner-ring ring-1"></div>
				<div className="rbac-scanner-ring ring-2"></div>
				<div className="rbac-scanner-ring ring-3"></div>
				<div className="rbac-warning-icon">
					<Lucide.ShieldAlert size={28} />
				</div>
			</div>

			<h2 className="rbac-title">403 Forbidden - Access Denied</h2>

			<p className="rbac-description">
				Your authenticated session with role{" "}
				<span className="badge-role badge-user-role">
					{currentUserSession?.role?.toUpperCase()}
				</span>{" "}
				is unauthorized to access the{" "}
				<span className="badge-role badge-target-role">
					{targetRole.toUpperCase()}
				</span>{" "}
				environment.
			</p>

			{/* High-tech security terminal display */}
			<div className="rbac-terminal">
				<div className="rbac-terminal-header">
					<span className="terminal-dot red"></span>
					<span className="terminal-dot yellow"></span>
					<span className="terminal-dot green"></span>
					<span className="terminal-title">SECURITY EVENT LOG</span>
				</div>
				<div className="rbac-terminal-body">
					{securityLogs.map((log, i) => (
						<div key={i} className="rbac-terminal-line">
							<span className="line-prefix">&gt;</span> {log}
						</div>
					))}
				</div>
			</div>

			<div className="rbac-action-group">
				<button
					aria-label="Button"
					className="btn-primary-glow rbac-btn-escalate"
					onClick={handleEscalate}
				>
					Request Access Elevation
				</button>
				<button
					aria-label="Button"
					className="btn-secondary-glow rbac-btn-logout"
					onClick={handleLogout}
				>
					Log In as Different Role
				</button>
			</div>
		</div>
	);
}
