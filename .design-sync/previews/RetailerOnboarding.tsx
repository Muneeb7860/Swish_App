import { RetailerOnboarding } from "frontend-b2b";

// Retailer shape is the sandbox fixture useRetailerApi builds when the gateway
// is unreachable (retailerId / tier / status / the three approval gates), so the
// props here match what the app really passes. The axis is the onboarding
// lifecycle: registration form → pending three-gate approval → active with a
// revealed API key.

const handlers = {
	onRetailerNameChange: () => {},
	onRetailerEmailChange: () => {},
	onRetailerStoreIdChange: () => {},
	onBillingTierChange: () => {},
	onRegister: () => {},
	onApproveGate: () => {},
	onReset: () => {},
	onCopy: () => {},
};

const pending = {
	retailerId: "RTL-408912",
	name: "Northgate Grocers",
	contactEmail: "ops@northgategrocers.co.uk",
	storeId: "store-northgate-01",
	tier: "PRO",
	status: "PENDING",
	approvalOps: true,
	approvalCompliance: false,
	approvalAdmin: false,
	billingAccountId: "ACC-4471",
};

export const RegistrationForm = () => (
	<RetailerOnboarding
		retailerName=""
		retailerEmail=""
		retailerStoreId=""
		billingTier="BASIC"
		currentRetailer={null}
		revealedApiKey={null}
		isRegistering={false}
		copiedIndex={null}
		{...handlers}
	/>
);

export const Submitting = () => (
	<RetailerOnboarding
		retailerName="Northgate Grocers"
		retailerEmail="ops@northgategrocers.co.uk"
		retailerStoreId="store-northgate-01"
		billingTier="PRO"
		currentRetailer={null}
		revealedApiKey={null}
		isRegistering
		copiedIndex={null}
		{...handlers}
	/>
);

export const AwaitingApprovalGates = () => (
	<RetailerOnboarding
		retailerName="Northgate Grocers"
		retailerEmail="ops@northgategrocers.co.uk"
		retailerStoreId="store-northgate-01"
		billingTier="PRO"
		currentRetailer={pending}
		revealedApiKey={null}
		isRegistering={false}
		copiedIndex={null}
		{...handlers}
	/>
);

export const ActiveWithApiKey = () => (
	<RetailerOnboarding
		retailerName="Northgate Grocers"
		retailerEmail="ops@northgategrocers.co.uk"
		retailerStoreId="store-northgate-01"
		billingTier="ENTERPRISE"
		currentRetailer={{
			...pending,
			tier: "ENTERPRISE",
			status: "ACTIVE",
			approvalCompliance: true,
			approvalAdmin: true,
		}}
		revealedApiKey="sk_live_b2b_9f2c1ab4e70d3ba7761cd018c41e0d92"
		isRegistering={false}
		copiedIndex="apiKey"
		{...handlers}
	/>
);
