import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Custom React hook for consuming Server-Sent Events (SSE) AI streams.
 * Allows Vite MFEs to receive token-by-token real-time generations.
 *
 * Hardening changes:
 * - `error` typed as `string | null`
 * - `endpoint` and `prompt` parameters explicitly typed as `string`
 * - AbortController ref cancels any in-flight stream when:
 *   a) `startStream` is called again before the previous one finishes, or
 *   b) the component that owns the hook unmounts
 */
export const useAiStream = () => {
	const [streamData, setStreamData] = useState<string>("");
	const [isStreaming, setIsStreaming] = useState<boolean>(false);
	const [error, setError] = useState<string | null>(null);

	// Holds a reference to the AbortController for the currently active stream.
	const abortControllerRef = useRef<AbortController | null>(null);

	// Cancel any active stream when the component unmounts.
	useEffect(() => {
		return () => {
			abortControllerRef.current?.abort();
		};
	}, []);

	const startStream = useCallback(async (endpoint: string, prompt: string) => {
		// Abort any previous in-flight stream before starting a new one.
		abortControllerRef.current?.abort();
		const controller = new AbortController();
		abortControllerRef.current = controller;

		setIsStreaming(true);
		setStreamData("");
		setError(null);

		try {
			// Endpoint routes through the BFF proxy (e.g., /api/ai/orchestrate)
			const response = await fetch(endpoint, {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					Accept: "text/event-stream",
				},
				body: JSON.stringify({ prompt }),
				signal: controller.signal,
			});

			if (!response.ok) {
				throw new Error(`Failed to initiate stream: ${response.statusText}`);
			}

			if (!response.body) {
				throw new Error("Response body is null");
			}

			// Read the SSE stream chunks
			const reader = response.body.getReader();
			const decoder = new TextDecoder("utf-8");

			while (true) {
				const { done, value } = await reader.read();
				if (done) break;

				const chunk = decoder.decode(value, { stream: true });

				// SSE lines start with "data:"
				const lines = chunk.split("\n");
				for (const line of lines) {
					if (line.startsWith("data:")) {
						const data = line.slice(5).trim();
						// Append the new token to our state for real-time UI typing effect
						setStreamData((prev) => prev + data + " ");
					}
				}
			}
		} catch (err: unknown) {
			// AbortError is expected when the stream is intentionally cancelled —
			// don't surface it as a user-visible error.
			if (err instanceof DOMException && err.name === "AbortError") {
				return;
			}
			console.error("AI Stream Error:", err);
			const message =
				err instanceof Error ? err.message : "Unknown error occurred";
			setError(message);
		} finally {
			setIsStreaming(false);
		}
	}, []);

	return { streamData, isStreaming, error, startStream };
};
