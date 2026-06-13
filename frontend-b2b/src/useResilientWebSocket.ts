import { useState, useEffect, useRef, useCallback } from "react";

export interface WebSocketOptions {
	userId: string;
	accessToken?: string;
	maxReconnectAttempts?: number;
	reconnectIntervalMin?: number;
	reconnectIntervalMax?: number;
	onMessage?: (envelope: any) => void;
}

export type ConnectionStatus =
	| "CONNECTED"
	| "CONNECTING"
	| "RECONNECTING"
	| "DISCONNECTED"
	| "ERROR";

export interface UseResilientWebSocketResult {
	status: ConnectionStatus;
	notifications: any[];
	reconnectAttempts: number;
	reconnect: () => void;
	disconnect: () => void;
	clearNotifications: () => void;
}

/**
 * Typesafe Resilient WebSocket React Hook
 * Implements auto-reconnection with exponential backoff + jitter,
 * message deduplication using envelope ids, and tracks connection status.
 */
export const useResilientWebSocket = (
	url: string,
	options: WebSocketOptions,
): UseResilientWebSocketResult => {
	const {
		userId,
		accessToken,
		maxReconnectAttempts = 5,
		reconnectIntervalMin = 1000,
		reconnectIntervalMax = 30000,
	} = options;

	const [status, setStatus] = useState<ConnectionStatus>("DISCONNECTED");
	const [notifications, setNotifications] = useState<any[]>([]);
	const [reconnectAttempts, setReconnectAttempts] = useState(0);

	const wsRef = useRef<WebSocket | null>(null);
	const reconnectAttemptRef = useRef(0);
	const reconnectTimeoutRef = useRef<any>(null);
	const seenMessageIds = useRef<Set<string>>(new Set());

	const onMessageReceived = useRef(options.onMessage);
	useEffect(() => {
		onMessageReceived.current = options.onMessage;
	}, [options.onMessage]);

	const connect = useCallback(() => {
		if (!userId) {
			console.warn("[useResilientWebSocket] Missing userId, cannot connect");
			setStatus("ERROR");
			return;
		}

		if (
			wsRef.current &&
			(wsRef.current.readyState === WebSocket.OPEN ||
				wsRef.current.readyState === WebSocket.CONNECTING)
		) {
			return;
		}

		if (reconnectAttemptRef.current > 0) {
			setStatus("RECONNECTING");
		} else {
			setStatus("CONNECTING");
		}

		const queryParams = new URLSearchParams();
		queryParams.append("userId", userId);
		if (accessToken) {
			queryParams.append("access_token", accessToken);
		}

		const wsUrl = `${url}?${queryParams.toString()}`;
		console.log(`[WebSocket] Connecting to ${url} for user ${userId}...`);

		let ws: WebSocket;
		try {
			ws = new WebSocket(wsUrl);
		} catch (err) {
			console.error("[WebSocket] Failed to instantiate WebSocket:", err);
			setStatus("ERROR");
			handleReconnect();
			return;
		}

		ws.onopen = () => {
			console.log(`[WebSocket] Connected successfully for user ${userId}`);
			setStatus("CONNECTED");
			reconnectAttemptRef.current = 0;
			setReconnectAttempts(0);
		};

		ws.onmessage = (event) => {
			try {
				const rawData = event.data;
				if (typeof rawData !== "string") {
					throw new Error("WebSocket message data is not a string");
				}
				// Size Gating: Max 128KB
				if (rawData.length > 131072) {
					throw new Error("WebSocket payload size limit exceeded");
				}
				// JSON Nesting Depth Guard: Max 20 levels
				let depth = 0;
				for (let i = 0; i < rawData.length; i++) {
					const char = rawData[i];
					if (char === "{" || char === "[") {
						depth++;
						if (depth > 20) {
							throw new Error("WebSocket payload nesting depth limit exceeded");
						}
					} else if (char === "}" || char === "]") {
						depth--;
					}
				}

				const rawPayload = JSON.parse(rawData);

				// Recursive Sanitization helper to secure text values
				const sanitizeValue = (val: any): any => {
					if (typeof val === "string") {
						return val
							.replace(/<script[^>]*>([\s\S]*?)<\/script>/gi, "")
							.replace(/<[^>]+>/g, "");
					}
					if (val !== null && typeof val === "object") {
						const cleanObj: any = Array.isArray(val) ? [] : {};
						for (const key in val) {
							if (Object.prototype.hasOwnProperty.call(val, key)) {
								cleanObj[key] = sanitizeValue(val[key]);
							}
						}
						return cleanObj;
					}
					return val;
				};

				const payload = sanitizeValue(rawPayload);

				if (payload.type === "HEARTBEAT") {
					return;
				}

				if (payload.id) {
					if (seenMessageIds.current.has(payload.id)) {
						console.warn(
							`[WebSocket] Duplicate message dropped: ${payload.id}`,
						);
						return;
					}
					seenMessageIds.current.add(payload.id);
				}

				if (payload.type && payload.type !== "WELCOME") {
					setNotifications((prev) => {
						const updated = [payload, ...prev];
						return updated.slice(0, 50);
					});
				}

				if (onMessageReceived.current) {
					onMessageReceived.current(payload);
				}
			} catch (err: any) {
				console.error(
					"[WebSocket] Failed to validate/parse message:",
					err.message,
				);
			}
		};

		ws.onclose = (event) => {
			console.log(
				`[WebSocket] Connection closed. Code: ${event.code}, Reason: ${event.reason}`,
			);
			wsRef.current = null;

			if (event.code === 1000 || event.code === 4003) {
				setStatus("DISCONNECTED");
				reconnectAttemptRef.current = 0;
				setReconnectAttempts(0);
				return;
			}

			handleReconnect();
		};

		ws.onerror = (err) => {
			console.error("[WebSocket] Error detected:", err);
			ws.close();
		};

		wsRef.current = ws;
	}, [
		url,
		userId,
		accessToken,
		maxReconnectAttempts,
		reconnectIntervalMin,
		reconnectIntervalMax,
	]);

	const handleReconnect = () => {
		if (reconnectAttemptRef.current < maxReconnectAttempts) {
			const backoff =
				reconnectIntervalMin * Math.pow(2, reconnectAttemptRef.current);
			const jitter = Math.random() * 500;
			const delay = Math.min(backoff + jitter, reconnectIntervalMax);

			reconnectAttemptRef.current += 1;
			setReconnectAttempts(reconnectAttemptRef.current);
			setStatus("RECONNECTING");

			console.log(
				`[WebSocket] Will attempt reconnect #${reconnectAttemptRef.current} in ${Math.round(delay)}ms`,
			);

			if (reconnectTimeoutRef.current)
				clearTimeout(reconnectTimeoutRef.current);
			reconnectTimeoutRef.current = setTimeout(() => {
				connect();
			}, delay);
		} else {
			console.error("[WebSocket] Max reconnection attempts reached.");
			setStatus("ERROR");
		}
	};

	const disconnect = useCallback(() => {
		console.log("[WebSocket] Manually disconnecting...");
		if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
		reconnectAttemptRef.current = 0;
		setReconnectAttempts(0);

		if (wsRef.current) {
			wsRef.current.close(1000, "Manual disconnect");
			wsRef.current = null;
		}
		setStatus("DISCONNECTED");
	}, []);

	const reconnect = useCallback(() => {
		disconnect();
		connect();
	}, [disconnect, connect]);

	useEffect(() => {
		connect();
		return () => {
			if (reconnectTimeoutRef.current)
				clearTimeout(reconnectTimeoutRef.current);
			if (wsRef.current) {
				wsRef.current.close(1000, "Component unmounting");
				wsRef.current = null;
			}
		};
	}, [connect]);

	return {
		status,
		notifications,
		reconnectAttempts,
		reconnect,
		disconnect,
		clearNotifications: () => {
			seenMessageIds.current.clear();
			setNotifications([]);
		},
	};
};
