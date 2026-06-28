import { SpanStatusCode } from "@opentelemetry/api";
import { useStore } from "../store";
import { getActiveTraceParent, tracer } from "./telemetry";

// Default to a same-origin relative base ("") so requests go to "/api/..." and are
// proxied to the backend by the demo nginx (single-container, same-origin) and by the
// vite dev server. The previous absolute "http://localhost:8080" default bypassed that
// proxy, hit a dead port, and silently forced every call onto mock fallback. Set
// VITE_API_BASE_URL to an absolute URL only for split-origin / API-gateway deployments.
export const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";
const generateSecureSessionId = (): string => {
	try {
		const array = new Uint8Array(16);
		window.crypto.getRandomValues(array);
		return `sess-${Array.from(array, (b) => b.toString(16).padStart(2, "0")).join("")}`;
	} catch {
		return `sess-${Math.random().toString(36).substring(2, 15)}`;
	}
};
const SESSION_ID = generateSecureSessionId();

interface FetchOptions extends RequestInit {
	idempotencyKey?: string;
	bypassMock?: boolean;
}

// biome-ignore lint/complexity/noStaticOnlyClass: ApiClient is an intentional static utility namespace for the HTTP layer; converting to free functions is out of scope.
export class ApiClient {
	private static async delay(ms: number): Promise<void> {
		return new Promise((resolve) => setTimeout(resolve, ms));
	}

	private static getHeaders(options?: FetchOptions): HeadersInit {
		const headers: Record<string, string> = {
			"Content-Type": "application/json",
			"X-Session-Id": SESSION_ID,
		};

		// Add OpenTelemetry traceparent header for distributed tracing
		try {
			headers.traceparent = getActiveTraceParent();
		} catch (e) {
			console.warn("Could not inject traceparent header:", e);
		}

		// Safely get authToken at runtime to avoid circular dependency
		try {
			const state = useStore.getState();
			if (state?.authToken) {
				headers.Authorization = `Bearer ${state.authToken}`;
			}
		} catch (e) {
			console.warn("Could not retrieve token from store", e);
		}

		if (options?.idempotencyKey) {
			headers["X-Idempotency-Key"] = options.idempotencyKey;
		}

		return headers;
	}

	public static isMockMode(): boolean {
		const mockMode = import.meta.env.VITE_MOCK_MODE === "true";
		return mockMode;
	}

	private static handleUnauthorized(): void {
		try {
			const state = useStore.getState();
			if (state) {
				state.setIsAuthenticated(false);
				state.setAuthToken("");
				state.setCurrentUserSession(null);
			}
		} catch (e) {
			console.error("Failed to clear auth state on 401", e);
		}
	}

	public static async request<T>(
		path: string,
		options: FetchOptions = {},
		mockFallback?: () => T | Promise<T>,
	): Promise<T> {
		const url = `${BASE_URL}${path}`;

		// If explicitly in mock mode, return mock data
		if (ApiClient.isMockMode() && !options.bypassMock && mockFallback) {
			await ApiClient.delay(500); // Simulate network latency
			return mockFallback();
		}

		const mergedOptions: RequestInit = {
			...options,
			headers: {
				...ApiClient.getHeaders(options),
				...options.headers,
			},
		};

		let retries = 3;
		let delayMs = 1000;

		// Start OTel span for network request
		const method = options.method || "GET";
		const span = tracer.startSpan(`HTTP ${method} ${path}`);
		span.setAttributes({
			"http.method": method,
			"http.url": url,
			"http.target": path,
		});

		while (retries >= 0) {
			try {
				const response = await fetch(url, mergedOptions);

				span.setAttribute("http.status_code", response.status);

				if (response.status === 401) {
					ApiClient.handleUnauthorized();
					span.setStatus({
						code: SpanStatusCode.ERROR,
						message: "Unauthorized",
					});
					span.end();
					throw new Error("Unauthorized");
				}

				if (response.status === 429) {
					if (retries === 0) {
						span.setStatus({
							code: SpanStatusCode.ERROR,
							message: "Rate limit exceeded",
						});
						span.end();
						throw new Error("Rate limit exceeded");
					}
					console.warn(`Rate limited (429). Retrying in ${delayMs}ms...`);
					await ApiClient.delay(delayMs);
					retries--;
					delayMs *= 2;
					continue;
				}

				if (!response.ok) {
					const errorText = await response.text().catch(() => "Unknown error");
					span.setStatus({
						code: SpanStatusCode.ERROR,
						message: `HTTP ${response.status}: ${errorText}`,
					});
					span.end();
					throw new Error(`HTTP ${response.status}: ${errorText}`);
				}

				// If response has no content (e.g. 204 or empty 200)
				const text = await response.text();
				span.setStatus({ code: SpanStatusCode.OK });
				span.end();
				return text ? (JSON.parse(text) as T) : ({} as T);
			} catch (error) {
				span.recordException(error as Error);
				// Fallback to mock data on network error
				if (mockFallback && !options.bypassMock) {
					console.warn(
						`API request to ${path} failed (${(error as Error).message}). Falling back to mock data.`,
					);
					span.setAttribute("api.fallback_to_mock", true);
					span.setStatus({
						code: SpanStatusCode.OK,
						message: "Fallback to mock data",
					});
					span.end();
					await ApiClient.delay(400); // Simulate network latency
					return mockFallback();
				}

				if (retries === 0 || responseStatusNotRetryable(error)) {
					span.setStatus({
						code: SpanStatusCode.ERROR,
						message: (error as Error).message,
					});
					span.end();
					throw error;
				}

				await ApiClient.delay(500);
				retries--;
			}
		}

		span.setStatus({
			code: SpanStatusCode.ERROR,
			message: "Request failed after retries",
		});
		span.end();
		throw new Error("Request failed after retries");
	}

	public static async get<T>(
		path: string,
		options?: FetchOptions,
		mockFallback?: () => T | Promise<T>,
	): Promise<T> {
		return ApiClient.request<T>(
			path,
			{ ...options, method: "GET" },
			mockFallback,
		);
	}

	public static async post<T>(
		path: string,
		body?: any,
		options?: FetchOptions,
		mockFallback?: () => T | Promise<T>,
	): Promise<T> {
		const idempotencyKey =
			options?.idempotencyKey ||
			(options?.method !== "GET"
				? ApiClient.generateIdempotencyKey()
				: undefined);
		return ApiClient.request<T>(
			path,
			{
				...options,
				method: "POST",
				body: body ? JSON.stringify(body) : undefined,
				idempotencyKey,
			},
			mockFallback,
		);
	}

	public static async put<T>(
		path: string,
		body?: any,
		options?: FetchOptions,
		mockFallback?: () => T | Promise<T>,
	): Promise<T> {
		const idempotencyKey =
			options?.idempotencyKey || ApiClient.generateIdempotencyKey();
		return ApiClient.request<T>(
			path,
			{
				...options,
				method: "PUT",
				body: body ? JSON.stringify(body) : undefined,
				idempotencyKey,
			},
			mockFallback,
		);
	}

	public static async delete<T>(
		path: string,
		options?: FetchOptions,
		mockFallback?: () => T | Promise<T>,
	): Promise<T> {
		const idempotencyKey =
			options?.idempotencyKey || ApiClient.generateIdempotencyKey();
		return ApiClient.request<T>(
			path,
			{ ...options, method: "DELETE", idempotencyKey },
			mockFallback,
		);
	}

	private static generateIdempotencyKey(): string {
		return `idem-${Math.random().toString(36).substring(2, 15)}-${Date.now()}`;
	}
}

function responseStatusNotRetryable(error: any): boolean {
	// Don't retry if it is an Auth/Client input error
	const msg = String(error?.message || "");
	return (
		msg.includes("HTTP 400") ||
		msg.includes("HTTP 401") ||
		msg.includes("HTTP 403") ||
		msg.includes("HTTP 404") ||
		msg.includes("Unauthorized")
	);
}
