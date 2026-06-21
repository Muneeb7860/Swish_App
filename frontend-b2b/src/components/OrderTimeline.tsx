import type React from "react";

interface OrderTimelineProps {
	orderStatus: string;
}

const STEPS = [
	{ label: "Draft", step: 1 },
	{ label: "Stripe Payment", step: 2 },
	{ label: "AI Risk Check", step: 3 },
	{ label: "Final Release", step: 4 },
];

function getStepState(
	stepIndex: number,
	orderStatus: string,
): "active" | "completed" | "failed" | "" {
	switch (stepIndex) {
		case 0:
			return orderStatus === "PENDING" ? "active" : "completed";
		case 1:
			if (orderStatus === "PAYMENT_PROCESSING") return "active";
			if (orderStatus === "PAYMENT_FAILED") return "failed";
			if (orderStatus !== "PENDING") return "completed";
			return "";
		case 2:
			if (orderStatus === "PROCESSING") return "active";
			if (orderStatus === "APPROVED" || orderStatus === "HUMAN_TRIAGE")
				return "completed";
			if (orderStatus === "PAYMENT_FAILED") return "failed";
			return "";
		case 3:
			if (orderStatus === "APPROVED") return "completed";
			if (orderStatus === "HUMAN_TRIAGE") return "failed";
			return "";
		default:
			return "";
	}
}

function getProgressWidth(orderStatus: string): string {
	switch (orderStatus) {
		case "PENDING":
			return "0%";
		case "PAYMENT_PROCESSING":
			return "33%";
		case "PAYMENT_FAILED":
			return "33%";
		case "PROCESSING":
			return "66%";
		case "APPROVED":
			return "100%";
		case "HUMAN_TRIAGE":
			return "100%";
		default:
			return "0%";
	}
}

/**
 * 4-step order progress timeline with animated transitions.
 * Shows Draft → Stripe Payment → AI Risk Check → Final Release.
 */
const OrderTimeline: React.FC<OrderTimelineProps> = ({ orderStatus }) => {
	return (
		<div
			className="timeline my-6"
			role="progressbar"
			aria-label="Order progress"
		>
			<div className="timeline__track">
				<div
					className="timeline__progress"
					style={{ width: getProgressWidth(orderStatus) }}
				/>
			</div>

			{STEPS.map((step, i) => {
				const state = getStepState(i, orderStatus);
				const stateClass = state ? `timeline__step--${state}` : "";

				return (
					<div key={step.step} className={`timeline__step ${stateClass}`}>
						<div className="timeline__circle">
							{state === "completed" ? "✓" : step.step}
						</div>
						<div className="timeline__label">{step.label}</div>
					</div>
				);
			})}
		</div>
	);
};

export default OrderTimeline;
