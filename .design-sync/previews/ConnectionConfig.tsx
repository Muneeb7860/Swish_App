import { ConnectionConfig } from "frontend-b2b";

// A drawer: `isOpen={false}` returns null, so there is no closed story to show —
// an empty cell would be a blank card, not a variant. The axis that does change
// appearance is whether the socket is already configured.

const handlers = {
	onGatewayUrlChange: () => {},
	onUserIdChange: () => {},
	onAccessTokenChange: () => {},
	onReconnect: () => {},
	onDisconnect: () => {},
};

export const Configured = () => (
	<ConnectionConfig
		isOpen
		gatewayUrl="wss://gateway.swish.io/v1/socket"
		userId="usr_8814_wholesale"
		accessToken="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.b2b-sandbox"
		{...handlers}
	/>
);

export const LocalSandbox = () => (
	<ConnectionConfig
		isOpen
		gatewayUrl="ws://127.0.0.1:8080/socket"
		userId="usr_local_dev"
		accessToken=""
		{...handlers}
	/>
);
