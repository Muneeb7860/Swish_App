# NotificationInbox

Container for displaying animated notification cards with priority indicators.

## Features
- Slide-in animation on card appearance
- Priority-based left border (high=red, medium=amber, low=indigo)
- Type badge with background
- Timestamp ("5m ago" style)
- Trace ID link for debugging
- Hover: border strengthens, background brightens

## Styling Classes
- `.notification-card` — main card container
- `.notification-card.priority-high/medium/low` — border color
- `.type-badge` — type label with background
- `.time-ago` — timestamp text
- `.trace-id` — clickable trace reference

## Use Cases
- Real-time notification center
- Error/warning feeds
- System event display
- Debug trace collection UI
