#!/usr/bin/env python3
"""Local UAT static host on :3100.

Serves the built micro-frontends same-origin, mirroring demo/nginx.demo.conf
(minus the basic-auth gate):
  /                    -> frontend-host/dist (SPA fallback to index.html)
  /remotes/<name>/...  -> frontend-<name>/dist (404 if missing, no fallback)
  /api/...             -> proxied to the backend on 127.0.0.1:8083

Build the dists first (see demo/frontend.Dockerfile for the --base flags).
"""
import posixpath
import urllib.error
import urllib.parse
import urllib.request
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
PORT = 3100
BACKEND = "http://127.0.0.1:8083"

HOST_DIST = REPO / "frontend-host" / "dist"
REMOTES = {
    "customer": REPO / "frontend-customer" / "dist",
    "rider": REPO / "frontend-rider" / "dist",
    "admin": REPO / "frontend-admin" / "dist",
    "b2b": REPO / "frontend-b2b" / "dist",
}

# Headers that must not be blindly forwarded in either direction
HOP_BY_HOP = {
    "host", "connection", "keep-alive", "transfer-encoding",
    "content-length", "content-encoding", "accept-encoding",
}


class UatHandler(SimpleHTTPRequestHandler):
    def _is_api(self):
        return self.path == "/api" or self.path.startswith("/api/")

    def translate_path(self, path):
        path = path.split("?", 1)[0].split("#", 1)[0]
        clean = posixpath.normpath(urllib.parse.unquote(path))
        parts = [p for p in clean.split("/") if p and p not in (".", "..")]
        if len(parts) >= 2 and parts[0] == "remotes" and parts[1] in REMOTES:
            target = REMOTES[parts[1]].joinpath(*parts[2:])
        else:
            target = HOST_DIST.joinpath(*parts)
            # SPA fallback: unknown host-app routes get index.html
            if not target.exists():
                target = HOST_DIST / "index.html"
        return str(target)

    def do_GET(self):
        if self._is_api():
            return self._proxy()
        return super().do_GET()

    def do_HEAD(self):
        if self._is_api():
            return self._proxy()
        return super().do_HEAD()

    def _api_only(self):
        if self._is_api():
            return self._proxy()
        self.send_error(405)

    do_POST = do_PUT = do_PATCH = do_DELETE = do_OPTIONS = _api_only

    def _proxy(self):
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else None
        headers = {
            k: v for k, v in self.headers.items() if k.lower() not in HOP_BY_HOP
        }
        req = urllib.request.Request(
            BACKEND + self.path, data=body, headers=headers, method=self.command
        )
        try:
            with urllib.request.urlopen(req, timeout=300) as resp:
                self._relay(resp.status, resp.getheaders(), resp.read())
        except urllib.error.HTTPError as e:
            self._relay(e.code, e.headers.items(), e.read())
        except (urllib.error.URLError, OSError):
            self.send_error(502, "Backend unreachable on 8083")

    def _relay(self, status, headers, data):
        self.send_response(status)
        for k, v in headers:
            if k.lower() not in HOP_BY_HOP:
                self.send_header(k, v)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Connection", "close")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)

    def log_message(self, fmt, *args):
        print(f"{self.address_string()} {fmt % args}", flush=True)


if __name__ == "__main__":
    missing = [str(p) for p in [HOST_DIST, *REMOTES.values()] if not p.is_dir()]
    if missing:
        raise SystemExit("Missing dist builds:\n  " + "\n  ".join(missing))
    print(f"UAT static host on http://127.0.0.1:{PORT} (api -> {BACKEND})", flush=True)
    ThreadingHTTPServer(("127.0.0.1", PORT), UatHandler).serve_forever()
