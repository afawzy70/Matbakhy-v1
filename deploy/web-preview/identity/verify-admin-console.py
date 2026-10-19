#!/usr/bin/env python3
"""Live verification of the public Preview Keycloak admin console (kc-admin.<domain>), from outside the container like a browser.

Prints no secret. The only credentials it ever uses belong to a synthetic READ-ONLY administrator that admin-console.sh creates for the run
and deletes afterwards (VERIFY_ADMIN_FIXTURE_FILE, mode 600); no real account is touched. Without that fixture the authenticated checks are
NOT_RUN and the exit code is 3 (never PASS).

Environment: PREVIEW_DOMAIN, VERIFY_ADMIN_FIXTURE_FILE. Rehearsal against a throwaway local Keycloak: VERIFY_LOCAL_BACKEND=http://127.0.0.1:18080
(requests are sent there with X-Forwarded-Proto/Host as the proxy would) and VERIFY_SKIP_PROXY=1 (the proxy's own checks are then NOT_RUN).
Exit codes: 0 PASS, 1 FAIL, 2 usage, 3 INCOMPLETE (some check was NOT_RUN).
"""
import base64
import hashlib
import hmac
import html
import json
import os
import re
import socket
import struct
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

DOMAIN = os.environ.get("PREVIEW_DOMAIN", "")
ADMIN = f"https://kc-admin.{DOMAIN}"
ID = f"https://id.{DOMAIN}"
BACKEND = os.environ.get("VERIFY_LOCAL_BACKEND", "")
SKIP_PROXY = os.environ.get("VERIFY_SKIP_PROXY") == "1"
FIXTURE_FILE = os.environ.get("VERIFY_ADMIN_FIXTURE_FILE", "")
CONSOLE = "/admin/master/console/"
failures, not_run = [], []


def check(name, ok, detail=""):
    print(("PASS" if ok else "FAIL"), name, "" if ok else detail)
    if not ok:
        failures.append(name)


def skipped(name, why):
    print("NOT_RUN", name, why)
    not_run.append(name)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


class Browser:
    def __init__(self):
        self.cookies = {}
        self.set_cookie_headers = []
        self.opener = urllib.request.build_opener(NoRedirect)

    def req(self, url, data=None, headers=None, method=None):
        target, extra = url, {}
        if BACKEND:
            parts = urllib.parse.urlsplit(url)
            target = BACKEND + urllib.parse.urlunsplit(("", "", parts.path, parts.query, ""))
            extra = {"X-Forwarded-Proto": "https", "X-Forwarded-Host": parts.netloc, "X-Forwarded-For": "203.0.113.9"}
        body = urllib.parse.urlencode(data).encode() if isinstance(data, dict) else data
        rq = urllib.request.Request(target, data=body, method=method)
        for k, v in {**extra, **(headers or {})}.items():
            rq.add_header(k, v)
        if self.cookies:
            rq.add_header("Cookie", "; ".join(f"{k}={v}" for k, v in self.cookies.items()))
        try:
            r = self.opener.open(rq, timeout=30)
            status, hdrs, raw = r.status, r.headers, r.read()
        except urllib.error.HTTPError as e:
            status, hdrs, raw = e.code, e.headers, e.read()
        for sc in hdrs.get_all("Set-Cookie") or []:
            self.set_cookie_headers.append(sc)
            k, _, v = sc.split(";")[0].partition("=")
            if v == "" or "Max-Age=0" in sc:
                self.cookies.pop(k, None)
            else:
                self.cookies[k] = v
        return status, hdrs, raw.decode("utf-8", "replace")


def totp(secret, at=None):
    """RFC 6238 (HmacSHA1, 30 s, 6 digits). Keycloak keys the HMAC with the stored secret string's bytes."""
    counter = int((at if at is not None else time.time()) // 30)
    digest = hmac.new(secret.encode(), struct.pack(">Q", counter), hashlib.sha1).digest()
    offset = digest[-1] & 15
    return "%06d" % ((struct.unpack(">I", digest[offset:offset + 4])[0] & 0x7FFFFFFF) % 10**6)


def form_action(body, form_id):
    m = re.search(r'<form[^>]*id="%s"[^>]*action="([^"]+)"' % form_id, body)
    return html.unescape(m.group(1)) if m else None


def pkce():
    verifier = "v" * 64
    return verifier, base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()


def authorize_url(challenge):
    q = {"response_type": "code", "client_id": "security-admin-console", "scope": "openid", "state": "verify",
         "redirect_uri": ADMIN + CONSOLE, "code_challenge": challenge, "code_challenge_method": "S256"}
    return f"{ADMIN}/realms/master/protocol/openid-connect/auth?" + urllib.parse.urlencode(q)


def claims_of(token):
    part = token.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(part + "=" * (-len(part) % 4)))


def code_from(location):
    if location.startswith(ADMIN + CONSOLE) and "code=" in location:
        return urllib.parse.parse_qs(urllib.parse.urlparse(location).query)["code"][0]
    return None


def surface():
    """What anyone on the Internet sees, with no credentials."""
    b = Browser()
    status, hdrs, _ = b.req(ADMIN + "/")
    if SKIP_PROXY:
        skipped("the bare host redirects to the admin console", "VERIFY_SKIP_PROXY (the redirect is the proxy's)")
    else:
        check("the bare host redirects to the admin console", status == 302 and (hdrs.get("Location") or "").endswith(CONSOLE), f"status {status} {hdrs.get('Location')}")
    status, hdrs, page = b.req(ADMIN + CONSOLE)
    check("the console page is served over HTTPS", status == 200 and "Keycloak Administration Console" in page, f"status {status}")
    env = re.search(r'<script id="environment" type="application/json">(.*?)</script>', page, re.S)
    cfg = json.loads(env.group(1)) if env else {}
    check("the console is configured for the dedicated host (sign-in and admin API on kc-admin, not on the customer host)",
          cfg.get("authUrl") == ADMIN and cfg.get("adminBaseUrl") == ADMIN, f"authUrl={cfg.get('authUrl')} adminBaseUrl={cfg.get('adminBaseUrl')}")
    low = {k.lower(): v for k, v in hdrs.items()}
    check("security headers: HSTS, nosniff, no Server header",
          "max-age=" in low.get("strict-transport-security", "") and low.get("x-content-type-options") == "nosniff" and "server" not in low,
          str(sorted(low)))
    assets = sorted(set(re.findall(r'(?:src|href)="(/resources/[^"]+)"', page)))
    bad = []
    for path in assets:
        st, h, _ = b.req(ADMIN + path)
        if st != 200:
            bad.append(f"{path}={st}")
    check(f"all {len(assets)} console assets (scripts, styles, icons) load from the same host", assets and not bad, ", ".join(bad) or "no assets found")
    status, _, body = b.req(ADMIN + "/realms/master/.well-known/openid-configuration")
    conf = json.loads(body) if status == 200 else {}
    urls = [conf.get(k, "") for k in ("issuer", "authorization_endpoint", "token_endpoint", "jwks_uri", "userinfo_endpoint", "end_session_endpoint")]
    check("the master realm advertises only kc-admin URLs (issuer, sign-in, token, keys, logout)",
          status == 200 and all(u.startswith(ADMIN + "/realms/master") for u in urls), f"status {status} {urls}")
    for path in ("/admin/realms", "/admin/realms/matbakhy/users", "/admin/realms/master/users", "/admin/realms/matbakhy/clients"):
        status = b.req(ADMIN + path)[0]
        check(f"Admin REST {path} refuses a request without a token", status == 401, f"status {status}")
    status, _, _ = b.req(ADMIN + "/admin/realms", headers={"Authorization": "Bearer not-a-token"})
    check("Admin REST refuses a forged bearer token", status == 401, f"status {status}")
    if SKIP_PROXY:
        skipped("paths outside the console answer 404 on kc-admin; customer host unchanged; unpublished ports", "VERIFY_SKIP_PROXY (rehearsal without the proxy)")
        return
    for path in ("/realms/matbakhy/.well-known/openid-configuration", "/realms/matbakhy/protocol/openid-connect/auth", "/health", "/health/ready",
                 "/metrics", "/q/health", "/robots.txt", "/admin/../health", "/welcome", "/auth/"):
        status = b.req(ADMIN + path)[0]
        check(f"kc-admin does not serve {path}", status == 404, f"status {status}")
    status, _, body = b.req(ID + "/realms/matbakhy/.well-known/openid-configuration")
    issuer = json.loads(body).get("issuer") if status == 200 else None
    check("customer sign-in on id.* is unchanged (issuer https://id.*/realms/matbakhy)", issuer == f"{ID}/realms/matbakhy", f"status {status} issuer {issuer}")
    for path in ("/admin/", "/admin/master/console/", "/realms/master", "/realms/master/protocol/openid-connect/auth"):
        status = b.req(ID + path)[0]
        check(f"the customer host id.* still does not serve {path}", status == 404, f"status {status}")
    try:
        ip = socket.gethostbyname(f"kc-admin.{DOMAIN}")
    except OSError:
        return skipped("infrastructure ports are not published", "cannot resolve the host")
    open_ports = []
    for port in (5432, 8080, 8081, 8333, 9000, 9090, 3100, 3000):
        s = socket.socket()
        s.settimeout(2.5)
        try:
            s.connect((ip, port))
            open_ports.append(port)
        except OSError:
            pass
        finally:
            s.close()
    check("Keycloak 8080/9000, PostgreSQL 5432 and the other internal ports are not reachable on the public address", not open_ports, f"open: {open_ports}")


def authenticated(fx):
    user, password, secret = fx["username"], fx["password"], fx["totp_secret"]
    verifier, challenge = pkce()

    def start():
        b = Browser()
        status, _, page = b.req(authorize_url(challenge))
        return b, status, page, form_action(page, "kc-form-login")

    b, status, page, action = start()
    check("the sign-in page is served on the dedicated host and posts back to it", status == 200 and bool(action) and action.startswith(ADMIN + "/realms/master/"), f"status {status} action={action}")
    if not action:
        return
    cookies = [c for c in b.set_cookie_headers if c.split("=")[0] in ("AUTH_SESSION_ID", "KC_AUTH_SESSION_HASH")]
    check("session cookies are Secure and the session id is HttpOnly",
          bool(cookies) and all("secure" in c.lower() for c in cookies) and any(c.startswith("AUTH_SESSION_ID") and "httponly" in c.lower() for c in cookies), str(cookies)[:200])

    status, hdrs, body = b.req(action, {"username": user, "password": password, "credentialId": ""})
    otp_action = form_action(body, "kc-otp-login-form")
    check("the correct PASSWORD ALONE does not sign in: the one-time password form is required", status == 200 and bool(otp_action) and not code_from(hdrs.get("Location", "")),
          f"status {status} location={hdrs.get('Location')}")
    if not otp_action:
        return
    status, hdrs, body = b.req(otp_action, {"otp": totp(secret), "login": "Sign In"})
    code = code_from(hdrs.get("Location", ""))
    check("password plus the correct one-time password issues the console's authorization code", status == 302 and code is not None, f"status {status} {hdrs.get('Location', '')[:80]}")
    if not code:
        return

    status, _, raw = b.req(f"{ADMIN}/realms/master/protocol/openid-connect/token", {
        "grant_type": "authorization_code", "code": code, "redirect_uri": ADMIN + CONSOLE, "client_id": "security-admin-console", "code_verifier": verifier})
    token = json.loads(raw).get("access_token") if status == 200 else None
    check("the console client exchanges the code for a token (PKCE)", bool(token), f"status {status}")
    if not token:
        return
    claims = claims_of(token)
    check("the token's issuer is the dedicated host and the client is the admin console",
          claims.get("iss") == ADMIN + "/realms/master" and claims.get("azp") == "security-admin-console", f"iss={claims.get('iss')} azp={claims.get('azp')}")

    status, _, raw = Browser().req(f"{ADMIN}/realms/master/protocol/openid-connect/token", {
        "grant_type": "password", "client_id": "admin-cli", "username": user, "password": password})
    check("the password grant is refused (a password alone cannot obtain a token through admin-cli)", status in (400, 401) and "access_token" not in raw, f"status {status}")

    auth = {"Authorization": "Bearer " + token, "Origin": ADMIN, "Accept": "application/json"}
    c = Browser()

    def get(path, headers=None):
        st, h, raw = c.req(ADMIN + path, headers={**auth, **(headers or {})})
        try:
            return st, h, json.loads(raw)
        except ValueError:
            return st, h, raw

    st, _, who = get("/admin/master/console/whoami")
    check("console login: whoami answers for the signed-in administrator", st == 200 and isinstance(who, dict) and who.get("realm") == "master", f"status {st} {str(who)[:160]}")
    st, h, users = get("/admin/realms/matbakhy/users?max=5")
    check("users: the matbakhy realm's users can be listed", st == 200 and isinstance(users, list) and len(users) > 0, f"status {st}")
    print(f"     ({len(users) if isinstance(users, list) else 0} users returned, first page)")
    st, _, roles = get("/admin/realms/matbakhy/roles")
    check("roles: the matbakhy realm's roles can be listed", st == 200 and isinstance(roles, list), f"status {st}")
    print(f"     ({len(roles) if isinstance(roles, list) else 0} roles)")
    st, _, clients = get("/admin/realms/matbakhy/clients")
    ids = {x.get("clientId") for x in clients} if isinstance(clients, list) else set()
    check("clients: the matbakhy realm's clients can be listed and include the web client", st == 200 and "matbakhy-web" in ids, f"status {st} clients={sorted(ids)[:8]}")
    st, _, flows = get("/admin/realms/matbakhy/authentication/flows")
    names = {x.get("alias") for x in flows} if isinstance(flows, list) else set()
    check("authentication flow management: the realm's flows can be read (browser flow present)", st == 200 and "browser" in names, f"status {st}")
    st, _, _ = get("/admin/realms/matbakhy/authentication/flows/browser/executions")
    check("authentication flow executions can be read", st == 200, f"status {st}")
    st, h, _ = get("/admin/realms/matbakhy/users?max=1", {"Origin": "https://evil.example"})
    acao = h.get("Access-Control-Allow-Origin")
    check("CORS: a foreign origin is not allowed (no matching or wildcard Access-Control-Allow-Origin)", acao not in ("*", "https://evil.example"), f"status {st} ACAO={acao}")
    st, h, _ = get("/admin/realms/matbakhy/users?max=1")
    check("CORS: the console's own origin is allowed", h.get("Access-Control-Allow-Origin") == ADMIN, f"status {st} ACAO={h.get('Access-Control-Allow-Origin')}")
    st, _, _ = c.req(ADMIN + "/admin/realms/matbakhy/users", json.dumps({"username": "verify-admin-must-not-exist"}).encode(),
                     {**auth, "Content-Type": "application/json"}, "POST")
    check("least privilege: the read-only verification administrator cannot create users", st == 403, f"status {st}")
    st = c.req(ADMIN + "/admin/realms/master/users", headers=auth)[0]
    check("least privilege: the verification administrator has no rights on the master realm's users", st == 403, f"status {st}")

    # A password-only account can neither sign in nor enrol an authenticator over the public page (no OTP, no enrolment).
    plain = fx.get("plain")
    if plain:
        b, status, page, action = start()
        status, hdrs, body = b.req(action, {"username": plain["username"], "password": plain["password"], "credentialId": ""})
        location = hdrs.get("Location", "")
        check("an account WITHOUT a one-time password cannot sign in with its password (no code)", not code_from(location), f"status {status} {location[:80]}")
        offered = 'name="totpSecret"' in body or "CONFIGURE_TOTP" in location or "required-action" in location
        if not offered and 300 <= status < 400 and location:
            st2, h2, body2 = b.req(urllib.parse.urljoin(action, location))
            offered = 'name="totpSecret"' in body2 or "CONFIGURE_TOTP" in h2.get("Location", "")
        check("... and the public page offers it no authenticator enrolment (no takeover by whoever knows the password)", not offered, f"status {status} {location[:80]}")
    else:
        skipped("password-only account cannot sign in or enrol", "no password-only fixture")

    # Failed attempts last, one second apart (Keycloak locks a user for a minute after failures that follow each other within a second).
    time.sleep(1.5)
    b, status, page, action = start()
    status, hdrs, body = b.req(action, {"username": user, "password": "wrong-" + password, "credentialId": ""})
    check("a wrong password issues no code", status == 200 and not code_from(hdrs.get("Location", "")), f"status {status}")
    time.sleep(1.5)
    b, status, page, action = start()
    status, hdrs, body = b.req(action, {"username": user, "password": password, "credentialId": ""})
    otp_action = form_action(body, "kc-otp-login-form")
    wrong = "000000" if totp(secret) != "000000" else "111111"
    status, hdrs, body = b.req(otp_action, {"otp": wrong, "login": "Sign In"}) if otp_action else (0, {}, "")
    check("the correct password with a wrong one-time password issues no code", bool(otp_action) and status == 200 and not code_from(hdrs.get("Location", "")), f"status {status}")


def main():
    if len(sys.argv) == 3 and sys.argv[1] == "fixture-body":
        return fixture_body(sys.argv[2])
    if not DOMAIN:
        print("PREVIEW_DOMAIN missing", file=sys.stderr)
        sys.exit(2)
    surface()
    if FIXTURE_FILE and os.path.exists(FIXTURE_FILE):
        with open(FIXTURE_FILE) as f:
            authenticated(json.load(f))
    else:
        skipped("authenticated console checks (login with a one-time password, users, roles, clients, flows)", "no synthetic administrator")
    if failures:
        print("RESULT: FAIL", ", ".join(failures))
        sys.exit(1)
    if not_run:
        print("RESULT: INCOMPLETE (NOT_RUN: " + "; ".join(not_run) + "). Not a PASS.")
        sys.exit(3)
    print("RESULT: PASS")
    sys.exit(0)


def fixture_body(directory):
    """Writes the fixture file (mode 600) into DIRECTORY and prints two Keycloak user representations (one per line) for `fixture-create` (stdin):
    an administrator with a password and a one-time password, and an account with a password only (it must NOT be able to sign in or enrol)."""
    import secrets
    tag = secrets.token_hex(4)
    username, plain = "verify-admin-" + tag, "verify-admin-" + secrets.token_hex(4)
    password, plain_password = secrets.token_urlsafe(24), secrets.token_urlsafe(24)
    otp_secret = "".join(secrets.choice("abcdefghijklmnopqrstuvwxyz0123456789") for _ in range(20))
    fd = os.open(os.path.join(directory, "fixture.json"), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as f:
        json.dump({"username": username, "password": password, "totp_secret": otp_secret,
                   "plain": {"username": plain, "password": plain_password}}, f)

    def body(name, creds):
        return json.dumps({"username": name, "enabled": True, "emailVerified": True, "email": f"{name}@preview.matbakhy.invalid",
                           "firstName": "Verify", "lastName": "Synthetic", "credentials": creds})

    print(body(username, [
        {"type": "password", "value": password, "temporary": False},
        {"type": "otp", "secretData": json.dumps({"value": otp_secret}),
         "credentialData": json.dumps({"subType": "totp", "digits": 6, "period": 30, "algorithm": "HmacSHA1", "counter": 0})}]))
    print(body(plain, [{"type": "password", "value": plain_password, "temporary": False}]))


if __name__ == "__main__":
    main()
