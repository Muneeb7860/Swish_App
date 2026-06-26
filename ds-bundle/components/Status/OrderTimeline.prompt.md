# OrderTimeline

Multi-step progress timeline for tracking order or workflow progression.

## Props
None (uses internal state or should be passed as children/data in real usage).

## States per Step
- **Pending** — gray circle, default border
- **Active** — indigo circle with glow, pulsing animation
- **Completed** — green circle with success glow
- **Failed** — red circle with error glow

## Visual Elements
- Horizontal track connecting steps
- Animated progress bar (indigo → purple gradient)
- Centered circle with step number/icon
- Label below each step
- Smooth transitions on state changes

## Use Cases
- Order progression (pending → payment → processing → approved)
- Workflow steps (1 → 2 → 3 → complete)
- Process tracking with failure fallbacks

## Composition
Works well nested inside CheckoutPanel or as standalone progress UI.
