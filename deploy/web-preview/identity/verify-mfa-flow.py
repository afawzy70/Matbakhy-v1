#!/usr/bin/env python3
"""Verification of the D18 MFA realm migration from outside the container, through the public proxy like a browser.

It prints no secret and never touches a real account. The only OTP it ever submits belongs to a synthetic fixture user that
migrate-mfa.sh creates for the run and deletes afterwards (VERIFY_FIXTURE_FILE, mode 600). Environment: PREVIEW_DOMAIN, PREVIEW_USER_PASSWORD (the synthetic
users' password), OIDC_ADMIN_CLIENT_SECRET. For a rehearsal against a local Keycloak: VERIFY_ID_BASE=http://127.0.0.1:18080,
VERIFY_API_BASE=https://api.example.test, VERIFY_LOCAL=1 (skips the "admin console unpublished" check, which is the proxy's job).
"""
import base64
import hmac
import struct
import time
import hashlib
import html
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

DOMAIN = os.environ.get("PREVIEW_DOMAIN", "")
ID_BASE = os.environ.get("VERIFY_ID_BASE") or f"https://id.{DOMAIN}"
API_BASE = os.environ.get("VERIFY_API_BASE") or f"https://api.{DOMAIN}"
API_CALL_BASE = os.environ.get("VERIFY_API_CALL_BASE") or API_BASE  # rehearsal only: where API requests go (redirect URIs keep API_BASE)
REALM = os.environ.get("VERIFY_REALM", "matbakhy")
USER = os.environ.get("VERIFY_USER", "preview-customer-1")
PASSWORD = os.environ.get("PREVIEW_USER_PASSWORD", "")
ADMIN_SECRET = os.environ.get("OIDC_ADMIN_CLIENT_SECRET", "")
LOCAL = os.environ.get("VERIFY_LOCAL") == "1"
MOBILE_REDIRECT = os.environ.get("VERIFY_MOBILE_REDIRECT", "matbakhy-beta://auth/callback")
FIXTURE_FILE = os.environ.get("VERIFY_FIXTURE_FILE", "")
WEB_SECRET = os.environ.get("OIDC_WEB_CLIENT_SECRET", "")
MFA_ACR = {"mfa", "2"}  # the API's OIDC_MFA_ACR_VALUES default
failures = []
not_run = []


def check(name, ok, detail=""):
    print(("PASS" if ok else "FAIL"), name, detail if not ok else "")
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
        self.opener = urllib.request.build_opener(NoRedirect)

    def req(self, url, data=None):
        rq = urllib.request.Request(url, data=urllib.parse.urlencode(data).encode() if data else None)
        if self.cookies:
            rq.add_header("Cookie", "; ".join(f"{k}={v}" for k, v in self.cookies.items()))
        try:
            r = self.opener.open(rq, timeout=30)
            status, headers, body = r.status, r.headers, r.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            status, headers, body = e.code, e.headers, e.read().decode("utf-8", "replace")
        for sc in headers.get_all("Set-Cookie") or []:
            k, _, v = sc.split(";")[0].partition("=")
            if v == "" or "Max-Age=0" in sc:
                self.cookies.pop(k, None)
            else:
                self.cookies[k] = v
        return status, dict(headers), body


def authorize_url(client, redirect, step_up=False):
    verifier = "v" * 50
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    q = {"response_type": "code", "client_id": client, "scope": "openid", "redirect_uri": redirect, "state": "verify",
         "code_challenge": challenge, "code_challenge_method": "S256"}
    if step_up:
        q["acr_values"] = "mfa 2"
    return f"{ID_BASE}/realms/{REALM}/protocol/openid-connect/auth?" + urllib.parse.urlencode(q)


def login_form_action(body):
    m = re.search(r'<form[^>]*id="kc-form-login"[^>]*action="([^"]+)"', body)
    return html.unescape(m.group(1)) if m else None


def sign_in(client, redirect, step_up):
    b = Browser()
    status, _, body = b.req(authorize_url(client, redirect, step_up))
    action = login_form_action(body) if status == 200 else None
    if not action:
        return "no-login-form", status
    status, headers, body = b.req(action, {"username": USER, "password": PASSWORD, "credentialId": ""})
    location = headers.get("Location", "")
    if location.startswith(redirect) and "code=" in location:
        return "code", status
    if location:
        status, headers, body = b.req(urllib.parse.urljoin(action, location))
        if "code=" in headers.get("Location", ""):
            return "code", status
    if 'name="otp"' in body:
        return "otp-form", status
    if 'name="totpSecret"' in body:
        return "enrolment-form", status
    return "other", status


# ---- Authentication assurance: real tokens from Keycloak, accepted or refused by the real API -------------------------------------
def totp(secret, at=None):
    """RFC 6238 (HmacSHA1, 30 s, 6 digits). Keycloak keys the HMAC with the stored secret string's bytes."""
    counter = int((at if at is not None else time.time()) // 30)
    digest = hmac.new(secret.encode(), struct.pack(">Q", counter), hashlib.sha1).digest()
    offset = digest[-1] & 15
    return "%06d" % ((struct.unpack(">I", digest[offset:offset + 4])[0] & 0x7FFFFFFF) % 10**6)


def form_action(body, form_id):
    m = re.search(r'<form[^>]*id="%s"[^>]*action="([^"]+)"' % form_id, body)
    return html.unescape(m.group(1)) if m else None


def authorization_code(user, password, step_up, otp_secret=None):
    """Signs the synthetic user in through the browser flow; returns (code, outcome). Answers the OTP form only when asked to."""
    redirect = f"{API_BASE}/login/oauth2/code/keycloak"
    b = Browser()
    status, _, body = b.req(authorize_url("matbakhy-web", redirect, step_up))
    action = login_form_action(body) if status == 200 else None
    if not action:
        return None, f"no-login-form ({status})"
    status, headers, body = b.req(action, {"username": user, "password": password, "credentialId": ""})
    location = headers.get("Location", "")
    if not location.startswith(redirect) and location:
        status, headers, body = b.req(urllib.parse.urljoin(action, location))
        location = headers.get("Location", "")
    if location.startswith(redirect) and "code=" in location:
        return urllib.parse.parse_qs(urllib.parse.urlparse(location).query)["code"][0], "code"
    otp_action = form_action(body, "kc-otp-login-form")
    if otp_action and otp_secret:
        status, headers, body = b.req(otp_action, {"otp": totp(otp_secret), "login": "Sign In"})
        location = headers.get("Location", "")
        if location.startswith(redirect) and "code=" in location:
            return urllib.parse.parse_qs(urllib.parse.urlparse(location).query)["code"][0], "code"
        return None, f"otp-rejected ({status})"
    return None, "otp-form" if otp_action else f"other ({status})"


def exchange(code):
    redirect = f"{API_BASE}/login/oauth2/code/keycloak"
    basic = base64.b64encode(f"matbakhy-web:{WEB_SECRET}".encode()).decode()
    data = urllib.parse.urlencode({"grant_type": "authorization_code", "code": code, "redirect_uri": redirect, "code_verifier": "v" * 50}).encode()
    rq = urllib.request.Request(f"{ID_BASE}/realms/{REALM}/protocol/openid-connect/token", data=data,
                                headers={"Authorization": f"Basic {basic}", "Content-Type": "application/x-www-form-urlencoded"})
    return json.loads(urllib.request.urlopen(rq, timeout=30).read())["access_token"]


def claims_of(token):
    part = token.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(part + "=" * (-len(part) % 4)))


def api(method, path, token, body=None):
    rq = urllib.request.Request(API_CALL_BASE + path, method=method, data=json.dumps(body).encode() if body is not None else None,
                                headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json", "Accept": "application/json"})
    try:
        r = urllib.request.urlopen(rq, timeout=30)
        status, raw = r.status, r.read()
    except urllib.error.HTTPError as e:
        status, raw = e.code, e.read()
    try:
        return status, json.loads(raw or b"{}")
    except ValueError:
        return status, {}


def assurance():
    """Proves, with tokens and API answers (never with configuration alone): level 1 carries no MFA evidence and is refused once the
    account needs MFA; a step-up with a real OTP carries acr in the API's MFA values and amr otp and is accepted."""
    if not (FIXTURE_FILE and os.path.exists(FIXTURE_FILE)):
        return skipped("mfa assurance (tokens and API)", "no synthetic fixture user (run through ./migrate-mfa.sh verify)")
    if not WEB_SECRET:
        return skipped("mfa assurance (tokens and API)", "OIDC_WEB_CLIENT_SECRET not set")
    with open(FIXTURE_FILE) as f:
        fx = json.load(f)
    user, password, otp_secret = fx["username"], fx["password"], fx["totp_secret"]
    mfa_token = None
    try:
        code, outcome = authorization_code(user, password, step_up=False)
        check("assurance: the synthetic user signs in at level 1 without a second factor", code is not None, outcome)
        if code is None:
            return
        level1 = exchange(code)
        c1 = claims_of(level1)
        amr1 = c1.get("amr") or []
        check("assurance: level 1 token has the API audience and NO mfa evidence (acr outside the API's MFA values, no otp in amr)",
              "matbakhy-api" in (c1.get("aud") if isinstance(c1.get("aud"), list) else [c1.get("aud")]) and str(c1.get("acr")) not in MFA_ACR and "otp" not in amr1,
              f"aud={c1.get('aud')} acr={c1.get('acr')} amr={amr1}")
        status, _ = api("GET", "/api/v1/me/security", level1)
        check("assurance: the API accepts the level 1 token for the open security endpoint", status == 200, f"status {status}")

        code, outcome = authorization_code(user, password, step_up=True, otp_secret=otp_secret)
        check("assurance: a step-up request answered with the user's real OTP issues a code", code is not None, outcome)
        if code is None:
            return
        mfa_token = exchange(code)
        c2 = claims_of(mfa_token)
        amr2 = c2.get("amr") or []
        check("assurance: the step-up token asserts acr in the API's MFA values and amr includes otp",
              str(c2.get("acr")) in MFA_ACR and "otp" in amr2, f"acr={c2.get('acr')} amr={amr2}")
        status, body = api("PUT", "/api/v1/me/security/mfa", mfa_token, {"enabled": True})
        check("assurance: the API accepts the elevated token when the user turns two-step verification on", status == 200 and body.get("enabled") is True, f"status {status} {body.get('code', '')}")
        status, body = api("GET", "/api/v1/orders?limit=1", level1)
        check("assurance: the API REJECTS the level 1 token with STEP_UP_REQUIRED once the account needs MFA", status == 403 and body.get("code") == "STEP_UP_REQUIRED", f"status {status} {body.get('code', '')}")
        status, _ = api("GET", "/api/v1/orders?limit=1", mfa_token)
        check("assurance: the API ACCEPTS the elevated token for the same call", status == 200, f"status {status}")
    except (urllib.error.URLError, KeyError, ValueError) as e:
        check("assurance: the checks could run", False, type(e).__name__)
    finally:
        if mfa_token:
            try:
                status, _ = api("PUT", "/api/v1/me/security/mfa", mfa_token, {"enabled": False})
                check("assurance: the fixture user's preference is switched back off", status == 200, f"status {status}")
            except urllib.error.URLError:
                check("assurance: the fixture user's preference is switched back off", False, "unreachable")


def fixture_body(directory):
    """Writes the fixture file (mode 600) into DIRECTORY and prints the Keycloak user representation for `fixture-create` (stdin)."""
    import secrets
    username = "verify-mfa-" + secrets.token_hex(4)
    password = secrets.token_urlsafe(24)
    otp_secret = "".join(secrets.choice("abcdefghijklmnopqrstuvwxyz0123456789") for _ in range(20))
    fd = os.open(os.path.join(directory, "fixture.json"), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as f:
        json.dump({"username": username, "password": password, "totp_secret": otp_secret}, f)
    print(json.dumps({
        "username": username, "enabled": True, "emailVerified": True, "email": f"{username}@preview.matbakhy.invalid",
        "firstName": "Verify", "lastName": "Synthetic",
        "credentials": [
            {"type": "password", "value": password, "temporary": False},
            {"type": "otp", "secretData": json.dumps({"value": otp_secret}),
             "credentialData": json.dumps({"subType": "totp", "digits": 6, "period": 30, "algorithm": "HmacSHA1", "counter": 0})},
        ]}))


def main():
    if len(sys.argv) == 3 and sys.argv[1] == "fixture-body":
        return fixture_body(sys.argv[2])
    if not PASSWORD:
        print("PREVIEW_USER_PASSWORD missing: sign-in checks cannot run", file=sys.stderr)
        sys.exit(2)
    web_redirect = f"{API_BASE}/login/oauth2/code/keycloak"
    status = Browser().req(f"{ID_BASE}/realms/{REALM}/.well-known/openid-configuration")[0]
    check("realm discovery answers", status == 200, f"status {status}")
    if not LOCAL:
        for path in ("/admin/", "/realms/master"):
            check(f"admin surface {path} is not published", Browser().req(ID_BASE + path)[0] == 404)

    outcome, status = sign_in("matbakhy-web", web_redirect, step_up=False)
    check("web: a normal sign-in issues a code without any second factor", outcome == "code", f"{outcome} ({status})")
    outcome, status = sign_in("matbakhy-web", web_redirect, step_up=True)
    check("web: a step-up request asks for a second factor and issues no code", outcome in ("otp-form", "enrolment-form"), f"{outcome} ({status})")

    # The beta mobile client is registered by the separate iOS beta change (docs/mobile/ios-testflight-beta.md); the checks run
    # only when VERIFY_MOBILE=1, so a realm without it is reported as skipped, never as passed.
    if os.environ.get("VERIFY_MOBILE", "0") != "1":
        print("SKIP mobile checks (set VERIFY_MOBILE=1 once matbakhy-mobile is registered for the beta)")
    else:
        st, _, body = Browser().req(authorize_url("matbakhy-mobile", MOBILE_REDIRECT))
        registered = st == 200 and login_form_action(body) is not None
        check("mobile: the beta redirect URI is registered (login page shown)", registered, f"status {st}")
        st, _, body = Browser().req(authorize_url("matbakhy-mobile", "matbakhy-evil://auth/callback"))
        check("mobile: an unregistered redirect URI is refused", login_form_action(body) is None, f"status {st}")
        outcome, status = sign_in("matbakhy-mobile", MOBILE_REDIRECT, step_up=False)
        check("mobile: a normal sign-in issues a code without any second factor", outcome == "code", f"{outcome} ({status})")
        outcome, status = sign_in("matbakhy-mobile", MOBILE_REDIRECT, step_up=True)
        check("mobile: a step-up request asks for a second factor and issues no code", outcome in ("otp-form", "enrolment-form"), f"{outcome} ({status})")

    if ADMIN_SECRET:
        basic = base64.b64encode(f"matbakhy-admin:{ADMIN_SECRET}".encode()).decode()
        rq = urllib.request.Request(f"{ID_BASE}/realms/{REALM}/protocol/openid-connect/token", data=b"grant_type=client_credentials",
                                    headers={"Authorization": f"Basic {basic}", "Content-Type": "application/x-www-form-urlencoded"})
        try:
            token = json.loads(urllib.request.urlopen(rq, timeout=30).read())["access_token"]
            claims = json.loads(base64.urlsafe_b64decode(token.split(".")[1] + "=" * (-len(token.split(".")[1]) % 4)))
            roles = set(claims.get("resource_access", {}).get("realm-management", {}).get("roles", []))
            check("admin service account authenticates with exactly the user-management roles", {"view-users", "manage-users"} <= roles and not ({"manage-realm", "manage-clients", "realm-admin"} & roles), f"roles {sorted(roles)}")
        except (urllib.error.URLError, KeyError, ValueError) as e:
            check("admin service account authenticates", False, type(e).__name__)
    else:
        check("admin service account secret available", False, "OIDC_ADMIN_CLIENT_SECRET not set")
    assurance()
    if failures:
        print("RESULT: FAIL", ", ".join(failures))
        sys.exit(1)
    if not_run:
        print("RESULT: INCOMPLETE (NOT_RUN: " + "; ".join(not_run) + "). Not a PASS.")
        sys.exit(3)
    print("RESULT: PASS")
    sys.exit(0)


if __name__ == "__main__":
    main()
