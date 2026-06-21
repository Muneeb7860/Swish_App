import type React from "react";

interface StatusIndicatorProps {
	status: string;
	reconnectAttempts: number;
}

/**
 * Animated WebSocket connection status indicator pill.
 * Displays connected/connecting/reconnecting/disconnected states.
 */
const StatusIndicator: React.FC<StatusIndicatorProps> = ({
	status,
	reconnectAttempts,
}) => {
	const isConnected = status === "CONNECTED";
	const isConnecting =
		status.startsWith("RECONNECTING") || status === "CONNECTING";

	const badgeClass = isConnected
		? "status-badge--connected"
		: isConnecting
			? "status-badge--connecting"
			: "status-badge--disconnected";

	const dotClass = isConnected
		? "status-dot--connected"
		: isConnecting
			? "status-dot--connecting"
			: "status-dot--disconnected";

	return (
		<div className={`status-badge ${badgeClass}`}>
			<span className={`status-dot ${dotClass}`} />
			<span style={{ textTransform: "capitalize" }}>
				{status.toLowerCase()}
			</span>
			{reconnectAttempts > 0 && status === "RECONNECTING" && (
				<span
					style={{ fontSize: "9px", color: "var(--text-muted)", marginLeft: 2 }}
				>
					({reconnectAttempts}/10)
				</span>
			)}
		</div>
	);
};

export default StatusIndicator;
