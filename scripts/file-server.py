#!/usr/bin/env python3
"""OpenCode Remote File Server - serves project files to the Android app.
Runs on the Mac Mini, accessible through the SSH tunnel (Aliyun:3456 -> MacMini:8083).
"""
import http.server
import os
import signal
import sys
import json

PORT = 8083
PID_FILE = "/tmp/opencode-file-server.pid"


class FileHandler(http.server.BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        sys.stderr.write("[FileServer] %s\n" % (fmt % args))

    def _send(self, code, body, content_type="text/plain"):
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        if body:
            if isinstance(body, str):
                body = body.encode("utf-8")
            self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?")[0]  # strip query params

        # Serve /update.json
        if path == "/update.json" or path == "/":
            update_file = os.path.expanduser("~/opencode-update/update.json")
            if os.path.isfile(update_file):
                with open(update_file) as f:
                    self._send(200, f.read(), "application/json")
            else:
                self._send(404, "update.json not found")
            return

        # Serve /files/<absolute-path>
        if path.startswith("/files/"):
            file_path = path[7:]  # strip "/files/"
        else:
            file_path = path.lstrip("/")

        if not file_path:
            self._send(400, "No file path specified")
            return

        if os.path.isfile(file_path):
            _, ext = os.path.splitext(file_path)
            ext = ext.lower()
            if ext in (".md", ".markdown"):
                ct = "text/markdown"
            elif ext in (".json",):
                ct = "application/json"
            else:
                ct = "text/plain; charset=utf-8"
            try:
                with open(file_path, "rb") as f:
                    self._send(200, f.read(), ct)
            except Exception as e:
                self._send(500, "Error reading file: %s" % str(e))
        else:
            self._send(404, "File not found: " + path)

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.end_headers()


def main():
    # Kill existing instance
    if os.path.isfile(PID_FILE):
        try:
            with open(PID_FILE) as f:
                old_pid = int(f.read().strip())
            os.kill(old_pid, signal.SIGTERM)
            print("Killed old instance (PID %d)" % old_pid)
        except (ValueError, ProcessLookupError, OSError):
            pass

    server = http.server.HTTPServer(("127.0.0.1", PORT), FileHandler)
    with open(PID_FILE, "w") as f:
        f.write(str(os.getpid()))
    print("OpenCode File Server started on port %d" % PORT)
    print("Serving files from the filesystem at /files/<path>")
    sys.stdout.flush()

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("Shutting down...")
        server.shutdown()
        if os.path.isfile(PID_FILE):
            os.unlink(PID_FILE)


if __name__ == "__main__":
    main()
