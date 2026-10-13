"""MB-039 local alert test sink: records each Alertmanager notification as one JSON line (stdout and a file).

It sends nothing anywhere. Only alert names, states, severities and annotations are kept.
"""
import json
import os
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

OUT = os.environ.get("SINK_FILE", "/data/alerts.jsonl")


class Sink(BaseHTTPRequestHandler):
    def do_POST(self):
        length = min(int(self.headers.get("Content-Length", "0")), 1_000_000)
        body = json.loads(self.rfile.read(length) or b"{}")
        for alert in body.get("alerts", []):
            line = json.dumps({"received": self.date_time_string(), "status": alert.get("status"),
                               "alertname": alert.get("labels", {}).get("alertname"),
                               "severity": alert.get("labels", {}).get("severity"),
                               "summary": alert.get("annotations", {}).get("summary"),
                               "startsAt": alert.get("startsAt"), "endsAt": alert.get("endsAt")})
            print("ALERT_RECEIVED " + line, flush=True)
            with open(OUT, "a", encoding="utf-8") as f:
                f.write(line + "\n")
        self.send_response(200)
        self.end_headers()

    def do_GET(self):
        self.send_response(200)
        self.end_headers()
        if os.path.exists(OUT):
            with open(OUT, "rb") as f:
                self.wfile.write(f.read())

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    print("alert sink listening on 8080", file=sys.stderr, flush=True)
    HTTPServer(("0.0.0.0", 8080), Sink).serve_forever()
