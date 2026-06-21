/**
 * Shared API utilities for the B2B dashboard.
 * Centralizes request headers (auth, tracing) and gateway URL management.
 */

export function buildRequestHeaders(
	accessToken: string,
	customHeaders: Record<string, string> = {},
): Record<string, string> {
	const headers: Record<string, string> = {
		"Content-Type": "application/json",
		...customHeaders,
	};

	if (accessToken && accessToken !== "mock_token_for_now") {
		headers.Authorization = `Bearer ${accessToken}`;
	}

	const win =
		typeof window !== "undefined"
			? (window as unknown as { getActiveTraceParent?: () => string })
			: null;
	if (win?.getActiveTraceParent) {
		try {
			headers.traceparent = win.getActiveTraceParent();
		} catch (e) {
			console.warn("Failed to retrieve traceparent from window:", e);
		}
	}

	return headers;
}

/**
 * Derives the WebSocket URL from the HTTP gateway URL.
 */
export function deriveWsUrl(httpUrl: string): string {
	try {
		const urlObj = new URL(httpUrl);
		const wsProtocol = urlObj.protocol === "https:" ? "wss:" : "ws:";
		return `${wsProtocol}//${urlObj.host}/ws/notifications/b2b`;
	} catch {
		return "ws://localhost:8080/ws/notifications/b2b";
	}
}
