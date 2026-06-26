import type React from "react";

export interface StatusIndicatorProps {
	/** Connection status (e.g., "CONNECTED", "CONNECTING", "RECONNECTING", "DISCONNECTED") */
	status: string;
	/** Number of reconnection attempts */
	reconnectAttempts: number;
}

/**
 * Animated WebSocket connection status indicator.
 * Displays connection state with animated dot and reconnect counter.
 *
 * @example
 * ```tsx
 * <StatusIndicator status="CONNECTED" reconnectAttempts={0} />
 * <StatusIndicator status="RECONNECTING_2" reconnectAttempts={2} />
 * <StatusIndicator status="DISCONNECTED" reconnectAttempts={0} />
 * ```
 */
export const StatusIndicator: React.FC<StatusIndicatorProps>;
