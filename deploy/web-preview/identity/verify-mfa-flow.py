#!/usr/bin/env python3
"""Verification of the D18 MFA realm migration from outside the container, through the public proxy like a browser.

It never submits an enrolment or an OTP and prints no secret. Environment: PREVIEW_DOMAIN, PREVIEW_USER_PASSWORD (the synthetic
users' password), OIDC_ADMIN_CLIENT_SECRET. For a rehearsal against a local Keycloak: VERIFY_ID_BASE=http://127.0.0.1:18080,
VERIFY_API_BASE=https://api.example.test, VERIFY_LOCAL=1 (skips the "admin console unpublished" check, which is the proxy's job).
"""
import base64
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
REALM = os.environ.get("VERIFY_REALM", "matbakhy")
USER = os.environ.get("VERIFY_USER", "preview-customer-1")
PASSWORD = os.environ.get("PREVIEW_USER_PASSWORD", "")
ADMIN_SECRET = os.environ.get("OIDC_ADMIN_CLIENT_SECRET", "")
LOCAL = os.environ.get("VERIFY_LOCAL") == "1"
MOBILE_REDIRECT = os.environ.get("VERIFY_MOBILE_REDIRECT", "matbakhy-beta://auth/callback")
failures = []


def check(name, ok, detail=""):
    print(("PASS" if ok else "FAIL"), name, detail if not ok else "")
    if not ok:
        failures.append(name)


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


def main():
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
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
