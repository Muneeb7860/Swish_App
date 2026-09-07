import { CheckoutPanel } from "frontend-b2b";

// The compound: CheckoutPanel composes OrderTimeline and CreditCardMockup and
// switches its banner on orderStatus. Prop names and shapes are taken from
// B2bDashboard's own call site; orderStatus values are the ones OrderTimeline
// actually branches on. The variant axis is the order lifecycle.

const base = {
	orderId: "ORD-4417",
	userId: "usr_8814_wholesale",
	lastTraceId: "9f2c1ab4e70d",
	isSimulating: false,
	simulationMode: "AUTO" as const,
	onSimulationModeChange: () => {},
	onCheckout: () => {},
	onResetOrder: () => {},
	copiedIndex: null,
	onCopy: () => {},
};

export const Draft = () => <CheckoutPanel {...base} orderStatus="PENDING" />;

export const PaymentProcessing = () => (
	<CheckoutPanel {...base} orderStatus="PAYMENT_PROCESSING" isSimulating />
);

export const Approved = () => (
	<CheckoutPanel {...base} orderStatus="APPROVED" copiedIndex="trace" />
);

export const HumanTriage = () => (
	<CheckoutPanel {...base} orderStatus="HUMAN_TRIAGE" />
);

export const LocalSandboxMode = () => (
	<CheckoutPanel
		{...base}
		orderStatus="PROCESSING"
		simulationMode="LOCAL_MOCK"
		lastTraceId={null}
	/>
);
