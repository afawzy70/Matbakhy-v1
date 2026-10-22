"""Access classes of the preview proxy (owner decision 2026-10-14): customer hosts may be public, privileged operations never.

Runs the real Caddyfile in a Caddy container on a throwaway Docker network, with stub upstreams standing in for the API,
Keycloak and the storage, and three clients with fixed addresses. No image is pulled and nothing is published: the test
is skipped when Docker or a Caddy image is not already there. It checks:
- default (CUSTOMER_ALLOWED_IPS / STAFF_ALLOWED_IPS = the allow-list): a client outside the list gets 403 everywhere;
- customer class public, staff class restricted: an outsider reaches app., the customer API, id. (matbakhy realm) and
  media., but gets 403 on ops. and on /api/v1/ops and /api/v1/cook, and 404 on the Keycloak admin console and master realm;
- the staff client reaches everything the outsider does and the privileged hosts and paths;
- kc-admin.* (the public Keycloak admin console, owner approval 2026-10-19): answers 404 for everyone until the switch file exists, then forwards
  only the console's paths to Keycloak for ANY client address (IP-independent), and never the matbakhy realm, health or metrics.
"""
import os
import pathlib
import random
import shutil
import subprocess
import tempfile
import time
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
CADDYFILE = ROOT / "deploy" / "web-preview" / "Caddyfile"
CLIENT = """
import socket, ssl, sys
ip, host, path = sys.argv[1:4]
ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE
s = ctx.wrap_socket(socket.create_connection((ip, 443), timeout=10), server_hostname=host)
s.sendall(f"GET {path} HTTP/1.1\\r\\nHost: {host}\\r\\nConnection: close\\r\\n\\r\\n".encode())
data = b""
while chunk := s.recv(65536):
    data += chunk
head, _, body = data.partition(b"\\r\\n\\r\\n")
print(head.split(b" ")[1].decode(), body.decode(errors="replace").strip()[:80])
"""
STUB = """
import http.server, threading
def serve(port, name):
    class H(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            body = f"stub-{name} {self.path}".encode()
            self.send_response(200); self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)
        def log_message(self, *a): pass
    http.server.ThreadingHTTPServer(("0.0.0.0", port), H).serve_forever()
for port, name in ((8080, "http"), (8333, "storage")):
    threading.Thread(target=serve, args=(port, name), daemon=True).start()
threading.Event().wait()
"""


def docker(*args, check=True, **kw):
    return subprocess.run(["docker", *args], check=check, capture_output=True, text=True, **kw)


def caddy_image():
    for tag in ("caddy:2.10.2-alpine", "caddy:2-alpine"):
        if docker("image", "inspect", tag, check=False).returncode == 0:
            return tag
    return None


class ProxyAccessTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if shutil.which("docker") is None or docker("info", check=False).returncode != 0:
            raise unittest.SkipTest("Docker is not available")
        cls.image = caddy_image()
        if cls.image is None or docker("image", "inspect", "python:3.13-alpine", check=False).returncode != 0:
            raise unittest.SkipTest("caddy and python:3.13-alpine images are not present locally (nothing is pulled)")
        n = random.randint(20, 220)
        cls.net, cls.prefix = f"mb-proxy-test-{os.getpid()}", f"172.29.{n}"
        cls.names = []
        docker("network", "create", "--subnet", f"{cls.prefix}.0/24", cls.net)
        cls.tmp = pathlib.Path(tempfile.mkdtemp(prefix="proxy-test-"))
        (cls.tmp / "flags").mkdir()
        for site in ("customer-web", "operations-web"):
            (cls.tmp / site).mkdir()
            (cls.tmp / site / "index.html").write_text(f"<div id=root>{site}</div>")
        stub = f"mb-proxy-stub-{os.getpid()}"
        docker("run", "-d", "--rm", "--name", stub, "--network", cls.net, "--network-alias", "api", "--network-alias", "identity",
               "--network-alias", "storage", "python:3.13-alpine", "python", "-I", "-c", STUB)
        cls.names.append(stub)
        time.sleep(1.5)

    @classmethod
    def tearDownClass(cls):
        for name in getattr(cls, "names", []):
            docker("rm", "-f", name, check=False)
        if hasattr(cls, "net"):
            docker("network", "rm", cls.net, check=False)
        if hasattr(cls, "tmp"):
            shutil.rmtree(cls.tmp, ignore_errors=True)

    def start_caddy(self, customer, staff, allowed):
        name = f"mb-proxy-caddy-{os.getpid()}"
        docker("rm", "-f", name, check=False)
        docker("run", "-d", "--rm", "--name", name, "--network", self.net, "--ip", f"{self.prefix}.10",
               "-v", f"{CADDYFILE}:/etc/caddy/Caddyfile:ro", "-v", f"{self.tmp / 'customer-web'}:/srv/customer-web:ro",
               "-v", f"{self.tmp / 'operations-web'}:/srv/operations-web:ro", "-v", f"{self.tmp / 'flags'}:/srv/flags:ro",
               "-e", "PREVIEW_DOMAIN=mb.localhost", "-e", "ACME_EMAIL=ops@example.test", "-e", f"ALLOWED_CLIENT_IPS={allowed}",
               "-e", f"CUSTOMER_ALLOWED_IPS={customer}", "-e", f"STAFF_ALLOWED_IPS={staff}", self.image)
        self.addCleanup(docker, "rm", "-f", name, check=False)
        for _ in range(40):
            if self.get(f"{self.prefix}.20", "app.mb.localhost", "/", check=False) is not None:
                return
            time.sleep(0.5)
        self.fail("caddy did not start: " + docker("logs", name, check=False).stdout[-500:])

    def get(self, client_ip, host, path, check=True):
        r = docker("run", "--rm", "--network", self.net, "--ip", client_ip, "python:3.13-alpine", "python", "-I", "-c", CLIENT,
                   f"{self.prefix}.10", host, path, check=False)
        if r.returncode != 0:
            if check:
                self.fail(f"request failed: {r.stderr[-300:]}")
            return None
        status, _, body = r.stdout.strip().partition(" ")
        return int(status), body

    def test_default_keeps_every_host_restricted_to_the_allow_list(self):
        allowed = f"{self.prefix}.30/32"
        self.start_caddy(allowed, allowed, allowed)
        outsider, listed = f"{self.prefix}.20", f"{self.prefix}.30"
        for host, path in (("app.mb.localhost", "/"), ("ops.mb.localhost", "/"), ("api.mb.localhost", "/api/v1/meals"),
                           ("api.mb.localhost", "/api/v1/ops/cases"), ("id.mb.localhost", "/realms/matbakhy/x"), ("media.mb.localhost", "/")):
            self.assertEqual(self.get(outsider, host, path)[0], 403, f"outsider {host}{path}")
        self.assertEqual(self.get(listed, "app.mb.localhost", "/")[0], 200)
        self.assertEqual(self.get(listed, "ops.mb.localhost", "/")[0], 200)
        self.assertEqual(self.get(listed, "api.mb.localhost", "/api/v1/ops/cases"), (200, "stub-http /api/v1/ops/cases"))

    def test_public_customer_class_never_opens_privileged_operations(self):
        staff = f"{self.prefix}.30"
        self.start_caddy("0.0.0.0/0 ::/0", f"{staff}/32", f"{staff}/32")
        outsider = f"{self.prefix}.20"
        # Customer-facing hosts and paths are reachable by anyone.
        self.assertEqual(self.get(outsider, "app.mb.localhost", "/orders")[0], 200)
        self.assertEqual(self.get(outsider, "api.mb.localhost", "/api/v1/meals"), (200, "stub-http /api/v1/meals"))
        self.assertEqual(self.get(outsider, "api.mb.localhost", "/api/v1/orders"), (200, "stub-http /api/v1/orders"))
        self.assertEqual(self.get(outsider, "api.mb.localhost", "/oauth2/authorization/keycloak")[0], 200)
        self.assertEqual(self.get(outsider, "id.mb.localhost", "/realms/matbakhy/.well-known/openid-configuration")[0], 200)
        self.assertEqual(self.get(outsider, "media.mb.localhost", "/matbakhy-public/x.jpg")[0], 200)
        # Privileged hosts and paths are refused, with the exact prefix and a lookalike that is not privileged.
        self.assertEqual(self.get(outsider, "ops.mb.localhost", "/")[0], 403)
        for path in ("/api/v1/ops/cases", "/api/v1/ops", "/api/v1/cook/orders", "/api/v1/cook"):
            self.assertEqual(self.get(outsider, "api.mb.localhost", path)[0], 403, path)
        self.assertEqual(self.get(outsider, "api.mb.localhost", "/api/v1/opsx")[0], 200)
        # Never published, for anyone: actuator, Keycloak admin console and the master realm.
        self.assertEqual(self.get(outsider, "api.mb.localhost", "/actuator/health")[0], 404)
        for path in ("/admin/", "/admin", "/realms/master", "/realms/master/protocol/openid-connect/auth"):
            self.assertEqual(self.get(outsider, "id.mb.localhost", path)[0], 404, path)
        # The staff client reaches the privileged side as well.
        self.assertEqual(self.get(staff, "ops.mb.localhost", "/")[0], 200)
        self.assertEqual(self.get(staff, "api.mb.localhost", "/api/v1/ops/cases")[0], 200)
        self.assertEqual(self.get(staff, "api.mb.localhost", "/api/v1/cook/orders")[0], 200)
        self.assertEqual(self.get(staff, "id.mb.localhost", "/admin/")[0], 404)

    def test_admin_console_host_is_closed_until_the_switch_exists_then_serves_only_the_console(self):
        flag = self.tmp / "flags" / "admin-console-public"
        flag.unlink(missing_ok=True)
        staff = f"{self.prefix}.30"
        # Customer and staff classes restricted to one client: the console must NOT depend on the client address.
        self.start_caddy(f"{staff}/32", f"{staff}/32", f"{staff}/32")
        outsider = f"{self.prefix}.20"
        for client in (outsider, staff):
            for path in ("/", "/admin/master/console/", "/admin/realms", "/realms/master/protocol/openid-connect/auth", "/resources/x/y.js"):
                self.assertEqual(self.get(client, "kc-admin.mb.localhost", path)[0], 404, f"closed switch, {client} {path}")
        flag.write_text("")
        try:
            for client in (outsider, staff):
                status, body = self.get(client, "kc-admin.mb.localhost", "/")
                self.assertEqual(status, 302, f"{client} /")
                for path in ("/admin/master/console/", "/admin/realms", "/admin/realms/matbakhy/users", "/admin", "/realms/master/.well-known/openid-configuration",
                             "/realms/master/protocol/openid-connect/token", "/resources/zlu30/admin/keycloak.v2/assets/main.js", "/js/keycloak.js"):
                    self.assertEqual(self.get(client, "kc-admin.mb.localhost", path), (200, f"stub-http {path}"), f"open switch, {client} {path}")
                for path in ("/realms/matbakhy/.well-known/openid-configuration", "/realms/matbakhy/protocol/openid-connect/auth", "/health", "/health/ready",
                             "/metrics", "/q/metrics", "/robots.txt", "/welcome", "/realms/masterx", "/adminx", "/resourcesx/a", "/jsx/a", "/auth/admin/"):
                    self.assertEqual(self.get(client, "kc-admin.mb.localhost", path)[0], 404, f"open switch, {client} {path}")
            # The customer host still never serves the master realm or the console, whatever the switch says.
            for path in ("/admin/", "/realms/master", "/realms/master/protocol/openid-connect/auth"):
                self.assertEqual(self.get(staff, "id.mb.localhost", path)[0], 404, path)
            self.assertEqual(self.get(staff, "id.mb.localhost", "/realms/matbakhy/x"), (200, "stub-http /realms/matbakhy/x"))
            self.assertEqual(self.get(outsider, "id.mb.localhost", "/realms/matbakhy/x")[0], 403)
        finally:
            flag.unlink(missing_ok=True)
        # Removing the switch closes the host again without restarting the proxy.
        self.assertEqual(self.get(staff, "kc-admin.mb.localhost", "/admin/master/console/")[0], 404)


if __name__ == "__main__":
    unittest.main()
