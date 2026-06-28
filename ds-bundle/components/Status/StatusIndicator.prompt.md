# StatusIndicator

Animated status pill for displaying WebSocket connection state.

## Props
- `status` (string) — status value ("CONNECTED", "CONNECTING", "RECONNECTING", "DISCONNECTED")
- `reconnectAttempts` (number) — number of reconnection attempts

## States
- **CONNECTED** — green dot + glow + "connected"
- **CONNECTING / RECONNECTING** — amber dot + pulse animation + "reconnecting" + attempt count
- **DISCONNECTED** — red dot + "disconnected"

## Styling
Uses `.status-badge` and `.status-dot` classes with role-specific colors:
- Connected: success green (`#10b981`)
- Connecting: warning amber (`#f59e0b`)
- Disconnected: error red (`#ef4444`)

Animated dot pulsing when connecting.

## Use Cases
- Live connection monitoring
- Server state indicator
- API health display
- Real-time app status UI
