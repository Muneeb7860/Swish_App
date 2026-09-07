import { OrderTimeline } from "frontend-b2b";

// Four fixed stages (Draft → Stripe Payment → AI Risk Check → Final Release);
// orderStatus decides how far the rail has advanced and whether the last node
// reads as failed. These are the exact status strings the component branches on.

export const Draft = () => <OrderTimeline orderStatus="PENDING" />;

export const StripePayment = () => (
	<OrderTimeline orderStatus="PAYMENT_PROCESSING" />
);

export const AiRiskCheck = () => <OrderTimeline orderStatus="PROCESSING" />;

export const Released = () => <OrderTimeline orderStatus="APPROVED" />;

export const HeldForTriage = () => (
	<OrderTimeline orderStatus="HUMAN_TRIAGE" />
);

export const PaymentFailed = () => (
	<OrderTimeline orderStatus="PAYMENT_FAILED" />
);
