"""Local demo server for recording the Conuco video (never deployed).
Serves ../web and adds the autopilot to /app-demo.html and /index-demo.html."""
import http.server, pathlib, sys

WEB = pathlib.Path(__file__).resolve().parent.parent / "web"
HERE = pathlib.Path(__file__).resolve().parent
TAG = '<script src="/__demo/autopilot.js" defer></script></body>'

class H(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **k):
        super().__init__(*a, directory=str(WEB), **k)
    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()
    def do_GET(self):
        path = self.path.split("?")[0]
        if path == "/sw.js":  # no service worker while recording
            return self.send_error(404)
        if path in ("/app-demo.html", "/index-demo.html"):
            src = WEB / ("app.html" if path == "/app-demo.html" else "index.html")
            body = src.read_text(encoding="utf-8").replace("</body>", TAG, 1)
            if path == "/index-demo.html":
                body = body.replace('href="app.html"', 'href="app-demo.html"')
            data = body.encode("utf-8")
        elif path == "/__demo/autopilot.js":
            data = (HERE / "autopilot.js").read_bytes()
        else:
            return super().do_GET()
        self.send_response(200)
        self.send_header("Content-Type", "application/javascript" if path.endswith(".js") else "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

http.server.ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1]) if len(sys.argv) > 1 else 8141), H).serve_forever()
