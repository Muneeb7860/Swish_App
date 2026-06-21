import type React from "react";

interface ConnectionConfigProps {
	isOpen: boolean;
	gatewayUrl: string;
	onGatewayUrlChange: (value: string) => void;
	userId: string;
	onUserIdChange: (value: string) => void;
	accessToken: string;
	onAccessTokenChange: (value: string) => void;
	onReconnect: () => void;
	onDisconnect: () => void;
}

/**
 * Collapsible WebSocket connection settings drawer.
 * Allows configuration of Gateway URL, User ID, and JWT token.
 */
const ConnectionConfig: React.FC<ConnectionConfigProps> = ({
	isOpen,
	gatewayUrl,
	onGatewayUrlChange,
	userId,
	onUserIdChange,
	accessToken,
	onAccessTokenChange,
	onReconnect,
	onDisconnect,
}) => {
	if (!isOpen) return null;

	return (
		<div className="config-drawer flex flex-col gap-4">
			<h3
				className="m-0 text-sm font-bold"
				style={{ color: "var(--text-primary)" }}
			>
				WebSocket Connection Settings
			</h3>
			<div className="grid grid-cols-1 md:grid-cols-3 gap-4">
				<div className="flex flex-col gap-1">
					<label
						htmlFor="gateway-url-input"
						className="text-xs font-semibold"
						style={{ color: "var(--text-muted)" }}
					>
						Gateway URL
					</label>
					<input
						id="gateway-url-input"
						type="text"
						className="input-field"
						value={gatewayUrl}
						onChange={(e) => onGatewayUrlChange(e.target.value)}
						aria-label="Gateway URL"
					/>
				</div>
				<div className="flex flex-col gap-1">
					<label
						htmlFor="user-id-input"
						className="text-xs font-semibold"
						style={{ color: "var(--text-muted)" }}
					>
						User ID
					</label>
					<input
						id="user-id-input"
						type="text"
						className="input-field"
						value={userId}
						onChange={(e) => onUserIdChange(e.target.value)}
						aria-label="User ID"
					/>
				</div>
				<div className="flex flex-col gap-1">
					<label
						htmlFor="access-token-input"
						className="text-xs font-semibold"
						style={{ color: "var(--text-muted)" }}
					>
						Access Token (JWT)
					</label>
					<input
						id="access-token-input"
						type="password"
						className="input-field"
						value={accessToken}
						onChange={(e) => onAccessTokenChange(e.target.value)}
						aria-label="Access Token"
					/>
				</div>
			</div>
			<div className="flex gap-3 mt-1">
				<button className="btn-primary" onClick={onReconnect} type="button">
					Reinitialize Socket
				</button>
				<button className="btn-danger" onClick={onDisconnect} type="button">
					Disconnect
				</button>
			</div>
		</div>
	);
};

export default ConnectionConfig;
