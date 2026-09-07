import { SandboxLogs } from "frontend-b2b";

// Log lines mirror the dashboard's own sandbox trace (useOrderSimulation /
// useRetailerApi addLog calls): a checkout run, then the retailer-registration
// fallback path. The four `type` values are the colour axis, so the primary
// story exercises all of them rather than a single tone.

const checkoutRun = [
	{
		text: "POST /v1/orders → 201 Created (orderId ORD-4417)",
		type: "info" as const,
		time: "14:22:31",
	},
	{
		text: "Payment authorised — trace 9f2c1ab4e70d",
		type: "success" as const,
		time: "14:22:33",
	},
	{
		text: "Risk score 0.62 — routing to human triage",
		type: "warning" as const,
		time: "14:22:34",
	},
	{
		text: "Gateway timeout after 5000ms. Initializing Local Sandbox Mock.",
		type: "error" as const,
		time: "14:22:39",
	},
	{
		text: "[Local Sandbox Mock] Order cleared. Awaiting settlement.",
		type: "success" as const,
		time: "14:22:40",
	},
];

export const SimulationRun = () => <SandboxLogs logs={checkoutRun} />;

export const Empty = () => <SandboxLogs logs={[]} />;

export const CustomEmptyMessage = () => (
	<SandboxLogs
		logs={[]}
		emptyMessage="No sensor telemetry yet. Provision a device to begin streaming."
	/>
);

export const Scrolling = () => (
	<SandboxLogs
		logs={[
			...checkoutRun,
			{
				text: "GET /v1/retailers/RTL-408912 → 200 OK",
				type: "info" as const,
				time: "14:23:02",
			},
			{
				text: "Ops gate approved by m.raza@swish.io",
				type: "success" as const,
				time: "14:23:05",
			},
			{
				text: "Compliance gate still pending — 2 of 3 approvals",
				type: "warning" as const,
				time: "14:23:06",
			},
		]}
		maxHeight="180px"
	/>
);
