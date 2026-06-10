import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Custom React hook for consuming Server-Sent Events (SSE) AI streams.
 * Allows Vite MFEs to receive token-by-token real-time generations.
 *
 * Hardening changes vs. original:
 * - `error` is typed as `string | null` (was implicitly `null` then silently `any`)
 * - `endpoint` and `prompt` parameters are explicitly typed as `string`
 * - An AbortController ref cancels any in-flight stream when:
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
			let buffer = "";
			let accumulatedLength = 0;
			const MAX_STREAM_BYTES = 262144; // 256KB limit

			while (true) {
				const { done, value } = await reader.read();
				if (done) break;

				const chunk = decoder.decode(value, { stream: true });
				buffer += chunk;

				const lines = buffer.split("\n");
				buffer = lines.pop() || "";

				for (const line of lines) {
					if (line.startsWith("data:")) {
						const rawData = line.slice(5).trim();
						// Basic HTML tag stripping and sanitization to prevent XSS
						const cleanData = rawData
							.replace(/<script[^>]*>([\s\S]*?)<\/script>/gi, "")
							.replace(/<[^>]+>/g, "");

						accumulatedLength += cleanData.length;
						if (accumulatedLength > MAX_STREAM_BYTES) {
							throw new Error("AI Stream payload size limit exceeded");
						}

						// Append the new token to our state for real-time UI typing effect
						setStreamData((prev) => prev + cleanData + " ");
					}
				}
			}

			// Process remaining buffer
			if (buffer.startsWith("data:")) {
				const rawData = buffer.slice(5).trim();
				const cleanData = rawData
					.replace(/<script[^>]*>([\s\S]*?)<\/script>/gi, "")
					.replace(/<[^>]+>/g, "");

				accumulatedLength += cleanData.length;
				if (accumulatedLength > MAX_STREAM_BYTES) {
					throw new Error("AI Stream payload size limit exceeded");
				}
				setStreamData((prev) => prev + cleanData + " ");
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
