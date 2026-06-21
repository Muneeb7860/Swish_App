import type React from "react";

/**
 * Premium 3D-hoverable glassmorphic credit card mockup.
 * Displays the Swish Wholesale card with chip, hologram, and card details.
 */
const CreditCardMockup: React.FC = () => {
	return (
		<div className="flex justify-center mb-5">
			<div className="premium-credit-card">
				<div className="flex justify-between items-center w-full mb-2">
					<span
						className="text-xs font-extrabold tracking-wider"
						style={{ color: "var(--text-muted)" }}
					>
						SWISH WHOLESALE
					</span>
					<div className="premium-credit-card-hologram" />
				</div>
				<div className="flex justify-start w-full">
					<div className="premium-credit-card-chip" />
				</div>
				<div
					className="text-lg font-bold tracking-widest text-center my-3 font-mono"
					style={{ color: "var(--text-primary)" }}
				>
					4242 •••• •••• 4242
				</div>
				<div
					className="flex justify-between items-end w-full"
					style={{ fontSize: "10px" }}
				>
					<div className="flex flex-col">
						<span
							className="uppercase tracking-wider"
							style={{ fontSize: "8px", color: "var(--text-muted)" }}
						>
							Card Holder
						</span>
						<strong style={{ color: "var(--text-secondary)" }}>
							B2B Merchant Client
						</strong>
					</div>
					<div className="flex flex-col items-end">
						<span
							className="uppercase tracking-wider"
							style={{ fontSize: "8px", color: "var(--text-muted)" }}
						>
							Expires
						</span>
						<strong style={{ color: "var(--text-secondary)" }}>12 / 28</strong>
					</div>
				</div>
			</div>
		</div>
	);
};

export default CreditCardMockup;
