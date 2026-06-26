# CheckoutPanel

Wholesale order checkout interface combining order timeline, credit card display, and payment controls.

## Props
- `orderId` (string) — order identifier
- `orderStatus` (string) — status badge text (e.g., "payment", "processing", "approved")
- `userId` (string) — associated user ID
- `lastTraceId` (string | null) — debugging/trace reference
- `isSimulating` (boolean) — whether simulation is active
- `simulationMode` ('AUTO' | 'LOCAL_MOCK') — current simulation mode
- `onSimulationModeChange` (callback) — fires when mode toggles
- `onCheckout` (callback) — fires on "Process Payment" click
- `onResetOrder` (callback) — fires on reset button
- `copiedIndex` (number | string | null) — which field was copied (for feedback)
- `onCopy` (callback) — fires when user clicks copy on a field

## Layout
- Header: Order ID, status badge, simulation mode toggle
- Main: Order timeline (3-4 steps), credit card mockup side-by-side
- Footer: Control buttons (Checkout, Reset), status messages

## Use Cases
- B2B/wholesale payment flow
- Order simulation/testing interface
- Payment processing UI with live trace tracking

## Composition
Wraps `OrderTimeline` and `CreditCardMockup` internally. Pair with form components for payment method entry.
