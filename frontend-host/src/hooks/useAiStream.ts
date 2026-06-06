import { useCallback, useState } from "react";

/**
 * Custom React hook for consuming Server-Sent Events (SSE) AI streams.
 * Allows Vite MFEs to receive token-by-token real-time generations.
 */
export const useAiStream = () => {
	const [streamData, setStreamData] = useState("");
	const [isStreaming, setIsStreaming] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const startStream = useCallback(async (endpoint: string, prompt: string) => {
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
			});

			if (!response.ok) {
				throw new Error(`Failed to initiate stream: ${response.statusText}`);
			}

			// Read the SSE stream chunks
			if (!response.body) {
				throw new Error("Response body is null");
			}
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
		} catch (err) {
			console.error("AI Stream Error:", err);
			const errMsg = err instanceof Error ? err.message : String(err);
			setError(errMsg);
		} finally {
			setIsStreaming(false);
		}
	}, []);

	return { streamData, isStreaming, error, startStream };
};
