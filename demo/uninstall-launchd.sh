#!/usr/bin/env bash
PLIST="$HOME/Library/LaunchAgents/ch.swissqcommerce.demo-backend.plist"
launchctl unload "$PLIST" 2>/dev/null || true
rm -f "$PLIST"
echo "🛑 launchd backend uninstalled (staged jar in ~/swish-demo kept; rm -rf ~/swish-demo to remove)."
