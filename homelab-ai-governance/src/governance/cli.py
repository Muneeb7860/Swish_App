"""CLI entry point for running the governance server."""

from __future__ import annotations

import argparse
import uvicorn


def main() -> None:
    parser = argparse.ArgumentParser(description="Run the Homelab AI Governance server.")
    parser.add_argument("--host", default="127.0.0.1", help="Host address to bind to")
    # Default 8000 (FastAPI convention; matches promptfooconfig.yaml and the
    # backend dev profile's swish.governance.api.url). Avoids macOS port 5000,
    # which the AirPlay Receiver binds by default.
    parser.add_argument("--port", type=int, default=8000, help="Port to bind to")
    parser.add_argument("--reload", action="store_true", help="Enable live reload")

    args = parser.parse_args()

    uvicorn.run("governance.server:app", host=args.host, port=args.port, reload=args.reload)


if __name__ == "__main__":
    main()
