import { context, trace } from "@opentelemetry/api";
import { OTLPTraceExporter } from "@opentelemetry/exporter-trace-otlp-http";
import { registerInstrumentations } from "@opentelemetry/instrumentation";
import { UserInteractionInstrumentation } from "@opentelemetry/instrumentation-user-interaction";
import {
	SimpleSpanProcessor,
	WebTracerProvider,
} from "@opentelemetry/sdk-trace-web";

// Initialize WebTracerProvider
const provider = new WebTracerProvider();

// Configure the exporter to send traces to the OTel Collector
const exporter = new OTLPTraceExporter({
	url: "http://localhost:4318/v1/traces",
});

// Process spans sequentially for low overhead in developer build
(provider as any).addSpanProcessor(new SimpleSpanProcessor(exporter as any));

// Register the provider globally
provider.register();

// Instrument clicks to capture interactions like "Checkout" and "Calibrate Sensor"
registerInstrumentations({
	instrumentations: [
		new UserInteractionInstrumentation({
			eventNames: ["click"],
		}),
	],
});

export const tracer = provider.getTracer("swish-qcommerce-frontend");

/**
 * Generates a standard W3C traceparent header.
 * Format: 00-traceId-spanId-traceFlags
 */
export function generateTraceparent(): string {
	const randomHex = (length: number) => {
		let result = "";
		while (result.length < length) {
			result += Math.random().toString(16).substring(2);
		}
		return result.substring(0, length);
	};
	const traceId = randomHex(32);
	const spanId = randomHex(16);
	return `00-${traceId}-${spanId}-01`;
}

/**
 * Retrieves the traceparent of the active OpenTelemetry span if available,
 * otherwise returns a newly generated traceparent to correlate requests.
 */
export function getActiveTraceParent(): string {
	const activeSpan = trace.getSpan(context.active());
	if (activeSpan) {
		const spanContext = activeSpan.spanContext();
		if (spanContext && spanContext.traceId && spanContext.spanId) {
			const flags = spanContext.traceFlags.toString(16).padStart(2, "0");
			return `00-${spanContext.traceId}-${spanContext.spanId}-${flags}`;
		}
	}
	return generateTraceparent();
}

if (typeof window !== "undefined") {
	(window as any).getActiveTraceParent = getActiveTraceParent;
}
