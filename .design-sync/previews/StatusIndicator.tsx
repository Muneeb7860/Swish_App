import { StatusIndicator } from "frontend-b2b";

// Status strings come from the B2B dashboard's websocket hook: the component
// treats "CONNECTED" specially, anything starting "RECONNECTING" (or
// "CONNECTING") as in-flight, and everything else as down. Sweeping those three
// branches is the whole visual axis.

export const Connected = () => (
	<StatusIndicator status="CONNECTED" reconnectAttempts={0} />
);

export const Connecting = () => (
	<StatusIndicator status="CONNECTING" reconnectAttempts={0} />
);

export const Reconnecting = () => (
	<StatusIndicator status="RECONNECTING (3/8)" reconnectAttempts={3} />
);

export const Disconnected = () => (
	<StatusIndicator status="DISCONNECTED" reconnectAttempts={8} />
);
