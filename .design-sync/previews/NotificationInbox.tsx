import { NotificationInbox } from "frontend-b2b";

// The component special-cases three `type` values for its description line and
// falls back to JSON for anything else; the primary story covers all four
// paths. Envelope shape and the $1.25M order amount come from B2bDashboard's
// own websocket payloads.

const feed = [
	{
		id: "ntf_01J8ZQ",
		type: "ORDER_EVALUATED",
		timestamp: "2026-09-07T14:22:34.000Z",
		recipientId: "usr_8814_wholesale",
		priority: "HIGH",
		correlationId: "9f2c1ab4e70d",
		payload: { ai_status: "HUMAN_TRIAGE", orderId: "ORD-4417" },
	},
	{
		id: "ntf_01J8ZP",
		type: "PAYMENT_CONFIRMED",
		timestamp: "2026-09-07T14:22:33.000Z",
		recipientId: "usr_8814_wholesale",
		priority: "NORMAL",
		correlationId: "9f2c1ab4e70d",
		payload: { amount: 1250000, orderId: "ORD-4417" },
	},
	{
		id: "ntf_01J8ZN",
		type: "PAYMENT_FAILED",
		timestamp: "2026-09-07T14:19:02.000Z",
		recipientId: "usr_8814_wholesale",
		priority: "HIGH",
		correlationId: "3ba7761cd018",
		payload: { orderId: "ORD-4416" },
	},
	{
		id: "ntf_01J8ZM",
		type: "SENSOR_CALIBRATED",
		timestamp: "2026-09-07T13:58:11.000Z",
		recipientId: "usr_8814_wholesale",
		correlationId: "c41e0d92aa5f",
		payload: { sensorId: "SNS-77120", status: "ACTIVE" },
	},
];

export const LivePushFeed = () => (
	<NotificationInbox
		notifications={feed}
		onClear={() => {}}
		copiedIndex={null}
		onCopy={() => {}}
	/>
);

export const TraceCopied = () => (
	<NotificationInbox
		notifications={feed.slice(0, 2)}
		onClear={() => {}}
		copiedIndex={0}
		onCopy={() => {}}
	/>
);

export const Empty = () => (
	<NotificationInbox
		notifications={[]}
		onClear={() => {}}
		copiedIndex={null}
		onCopy={() => {}}
	/>
);
