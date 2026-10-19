#!/usr/bin/env python3
"""Hardening of the Keycloak MASTER realm for the public Preview admin console (owner approval 2026-10-19, Preview only).

Talks to the Keycloak Admin REST API directly (the container's address on the Docker network, never a published port), so a whole run takes
seconds instead of one JVM start per kcadm call. Standard library only. The matbakhy realm, its users, clients and sessions are never touched.

  admin_console.py status      read-only: what is already in place
  admin_console.py harden      idempotent: operations client, master frontend URL, brute-force protection, browser flow with a REQUIRED
                               one-time password, no public OTP enrolment, password grant and flow overrides off, older sessions revoked
  admin_console.py rollback    rebinds the previous master browser flow, re-enables enrolment and the admin-cli password grant
  admin_console.py fixture-create | fixture-delete      synthetic read-only verification administrators (JSON lines on standard input)
  admin_console.py admin-create | admin-delete NAME     a real administrator whose password and one-time password come from the server

Environment: KC_BASE (e.g. http://172.18.0.5:8080), PREVIEW_DOMAIN (harden), OPS_CLIENT_SECRET, ADMIN_USER / ADMIN_PASSWORD (first run only,
while the password grant still exists), PREVIOUS_FLOW (rollback, default "browser"), PURGE=1 (rollback), KC_FORWARD_HOST (rehearsal against a
local Keycloak behind no proxy: sent as X-Forwarded-Host with X-Forwarded-Proto https). Secrets come from the environment or standard input only
and are never printed. Exit code 1 with a message on stderr on any failure.
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

REALM = "master"
BASE = os.environ.get("KC_BASE", "").rstrip("/")
TOP = "matbakhy master browser"
FORMS = "matbakhy master browser forms"
OPS_CLIENT = "matbakhy-ops-cli"
FIXTURE_PREFIX = "verify-admin-"
READ_ROLES = ["view-realm", "view-users", "view-clients", "query-users", "query-clients", "query-groups"]


def die(msg):
    print(f"admin-console: {msg}", file=sys.stderr)
    sys.exit(1)


class Kc:
    def __init__(self):
        if not BASE:
            die("KC_BASE is required")
        self.token = None

    # ---- HTTP -------------------------------------------------------------------------------------------------------------------
    def raw(self, method, path, body=None, form=None, token=True):
        url = BASE + path
        data = None
        headers = {"Accept": "application/json"}
        if form is not None:
            data = urllib.parse.urlencode(form).encode()
            headers["Content-Type"] = "application/x-www-form-urlencoded"
        elif body is not None:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        if token and self.token:
            headers["Authorization"] = "Bearer " + self.token
        if os.environ.get("KC_FORWARD_HOST"):
            headers["X-Forwarded-Proto"] = "https"
            headers["X-Forwarded-Host"] = os.environ["KC_FORWARD_HOST"]
        rq = urllib.request.Request(url, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(rq, timeout=60) as r:
                return r.status, r.read(), r.headers
        except urllib.error.HTTPError as e:
            return e.code, e.read(), e.headers
        except urllib.error.URLError as e:
            die(f"cannot reach Keycloak at {BASE}: {e.reason}")

    def call(self, method, path, body=None, ok=(200, 201, 204)):
        status, raw, headers = self.raw(method, path, body)
        if status == 401 and self.token:  # the token predates a change of issuer or not-before time: sign in again once
            self.login()
            status, raw, headers = self.raw(method, path, body)
        if status not in ok:
            die(f"{method} {path.split('?')[0]} answered {status}")
        try:
            return json.loads(raw) if raw else None, headers
        except ValueError:
            return None, headers

    def get(self, path):
        return self.call("GET", path)[0]

    def put(self, path, body):
        self.call("PUT", path, body)

    def post(self, path, body=None, ok=(200, 201, 204)):
        return self.call("POST", path, body, ok)

    def delete(self, path):
        self.call("DELETE", path)

    # ---- sign-in ----------------------------------------------------------------------------------------------------------------
    def login(self):
        """The operations client first; the bootstrap administrator's password only while that client cannot sign in yet (first run)."""
        self.token = None
        secret = os.environ.get("OPS_CLIENT_SECRET", "")
        if secret:
            status, raw, _ = self.raw("POST", "/realms/master/protocol/openid-connect/token", form={
                "grant_type": "client_credentials", "client_id": OPS_CLIENT, "client_secret": secret}, token=False)
            if status == 200:
                self.token = json.loads(raw)["access_token"]
                return
        user, password = os.environ.get("ADMIN_USER", ""), os.environ.get("ADMIN_PASSWORD", "")
        if not (user and password):
            die("cannot sign in to the master realm (operations client not usable and no ADMIN_USER / ADMIN_PASSWORD)")
        status, raw, _ = self.raw("POST", "/realms/master/protocol/openid-connect/token", form={
            "grant_type": "password", "client_id": "admin-cli", "username": user, "password": password}, token=False)
        if status != 200:
            die("cannot sign in to the master realm")
        self.token = json.loads(raw)["access_token"]

    # ---- helpers ----------------------------------------------------------------------------------------------------------------
    def realm(self):
        return self.get(f"/admin/realms/{REALM}")

    def update_realm(self, fields=None, attributes=None):
        rep = self.realm()
        rep.update(fields or {})
        if attributes:
            rep.setdefault("attributes", {}).update(attributes)
        self.put(f"/admin/realms/{REALM}", rep)

    def attribute(self, name):
        return (self.realm().get("attributes") or {}).get(name, "")

    def paged(self, path, page=100):
        """All rows of a list endpoint (first/max paging)."""
        rows, first = [], 0
        sep = "&" if "?" in path else "?"
        while True:
            chunk = self.get(f"{path}{sep}first={first}&max={page}")
            rows.extend(chunk)
            if len(chunk) < page:
                return rows
            first += page

    def clients(self):
        return self.get(f"/admin/realms/{REALM}/clients")

    def client(self, client_id):
        rows = self.get(f"/admin/realms/{REALM}/clients?clientId={urllib.parse.quote(client_id)}")
        return rows[0] if rows else None

    def update_client(self, rep, **fields):
        rep = dict(rep)
        rep.update(fields)
        self.put(f"/admin/realms/{REALM}/clients/{rep['id']}", rep)

    def flows(self):
        return self.get(f"/admin/realms/{REALM}/authentication/flows")

    def flow_exists(self, alias):
        return any(f["alias"] == alias for f in self.flows())

    def executions(self, alias):
        return self.get(f"/admin/realms/{REALM}/authentication/flows/{urllib.parse.quote(alias, safe='')}/executions")

    def set_requirement(self, flow, execution_id, requirement):
        rows = self.executions(flow)
        row = next(r for r in rows if r["id"] == execution_id)
        row["requirement"] = requirement
        self.put(f"/admin/realms/{REALM}/authentication/flows/{urllib.parse.quote(flow, safe='')}/executions", row)

    def user_by_name(self, name):
        rows = self.get(f"/admin/realms/{REALM}/users?username={urllib.parse.quote(name)}&exact=true")
        return rows[0] if rows else None

    def realm_role(self, name):
        return self.get(f"/admin/realms/{REALM}/roles/{urllib.parse.quote(name)}")

    def add_realm_role(self, user_id, role):
        self.post(f"/admin/realms/{REALM}/users/{user_id}/role-mappings/realm", [self.realm_role(role)])

    def add_client_roles(self, user_id, client_id, roles):
        client = self.client(client_id)
        if not client:
            die(f"client {client_id} does not exist")
        reps = [self.get(f"/admin/realms/{REALM}/clients/{client['id']}/roles/{r}") for r in roles]
        self.post(f"/admin/realms/{REALM}/users/{user_id}/role-mappings/clients/{client['id']}", reps)

    def create_user(self, body):
        status, raw, headers = self.raw("POST", f"/admin/realms/{REALM}/users", body)
        if status == 401:
            self.login()
            status, raw, headers = self.raw("POST", f"/admin/realms/{REALM}/users", body)
        if status != 201:
            die(f"cannot create the user (status {status})")
        return headers["Location"].rstrip("/").split("/")[-1]


# ---- flow handling --------------------------------------------------------------------------------------------------------------
def flow_complete(kc):
    if not kc.flow_exists(TOP):
        return False
    top = kc.executions(TOP)
    if not any(e.get("displayName") == FORMS and e.get("requirement") == "ALTERNATIVE" for e in top):
        return False
    forms = {(e.get("providerId"), e.get("requirement")) for e in kc.executions(FORMS)}
    cookie = {(e.get("providerId"), e.get("requirement")) for e in top}
    return ("auth-username-password-form", "REQUIRED") in forms and ("auth-otp-form", "REQUIRED") in forms and ("auth-cookie", "ALTERNATIVE") in cookie


def build_flow(kc):
    q = urllib.parse.quote
    kc.post(f"/admin/realms/{REALM}/authentication/flows", {
        "alias": TOP, "providerId": "basic-flow", "topLevel": True, "builtIn": False,
        "description": "Master realm browser flow: password AND a required one-time password for every administrator (public Preview admin console)"})
    base = f"/admin/realms/{REALM}/authentication/flows"
    kc.post(f"{base}/{q(TOP, safe='')}/executions/execution", {"provider": "auth-cookie"})
    kc.post(f"{base}/{q(TOP, safe='')}/executions/flow", {"alias": FORMS, "type": "basic-flow", "provider": "registration-page-form", "description": ""})
    kc.post(f"{base}/{q(FORMS, safe='')}/executions/execution", {"provider": "auth-username-password-form"})
    kc.post(f"{base}/{q(FORMS, safe='')}/executions/execution", {"provider": "auth-otp-form"})

    def exec_id(flow, provider=None, display=None):
        for e in kc.executions(flow):
            if (provider and e.get("providerId") == provider) or (display and e.get("displayName") == display):
                return e["id"]
        die(f"execution {provider or display} not found in {flow}")

    kc.set_requirement(TOP, exec_id(TOP, provider="auth-cookie"), "ALTERNATIVE")
    kc.set_requirement(TOP, exec_id(TOP, display=FORMS), "ALTERNATIVE")
    kc.set_requirement(FORMS, exec_id(FORMS, provider="auth-username-password-form"), "REQUIRED")
    kc.set_requirement(FORMS, exec_id(FORMS, provider="auth-otp-form"), "REQUIRED")


def delete_flow(kc):
    for f in kc.flows():
        if f["alias"] == TOP:
            kc.delete(f"/admin/realms/{REALM}/authentication/flows/{f['id']}")


def password_grant_clients(clients):
    return [c["clientId"] for c in clients if c.get("directAccessGrantsEnabled")]


def override_clients(clients):
    return [c for c in clients if any(k in (c.get("authenticationFlowBindingOverrides") or {}) for k in ("browser", "direct_grant"))]


def admin_accounts(kc):
    out = []
    for u in kc.paged(f"/admin/realms/{REALM}/users"):
        name = u["username"]
        if name.startswith("service-account-"):
            continue
        if not u.get("enabled"):
            out.append((name, "disabled"))
            continue
        roles = {r["name"] for r in kc.get(f"/admin/realms/{REALM}/users/{u['id']}/role-mappings/realm")}
        if "admin" not in roles:
            out.append((name, "not-an-administrator"))
            continue
        creds = {c["type"] for c in kc.get(f"/admin/realms/{REALM}/users/{u['id']}/credentials")}
        if "otp" not in creds:
            out.append((name, "no-otp(cannot-sign-in)"))
        elif "password" not in creds or u.get("requiredActions"):
            out.append((name, "needs-action(cannot-sign-in)"))  # no password, or a pending required action the public page cannot complete
        else:
            out.append((name, "otp"))
    return out


# ---- commands -------------------------------------------------------------------------------------------------------------------
def status(kc):
    realm = kc.realm()
    print(f"realm={REALM}")
    print(f"browserFlow={realm.get('browserFlow')}")
    print("flow=" + ("complete" if flow_complete(kc) else "incomplete" if kc.flow_exists(TOP) else "absent"))
    print("opsClient=" + ("present" if kc.client(OPS_CLIENT) else "absent"))
    print("passwordGrantClients=" + (" ".join(password_grant_clients(kc.clients())) or "none"))
    print(f"frontendUrl={(realm.get('attributes') or {}).get('frontendUrl', '')}")
    print(f"bruteForceProtected={str(bool(realm.get('bruteForceProtected'))).lower()}")
    print(f"registrationAllowed={str(bool(realm.get('registrationAllowed'))).lower()}")
    print(f"resetPasswordAllowed={str(bool(realm.get('resetPasswordAllowed'))).lower()}")
    accounts = admin_accounts(kc)
    for name, state in accounts:
        print(f"admin.{name}={state}")
    print(f"signInCapableAdministrators={sum(1 for _, s in accounts if s == 'otp')}")


def harden(kc):
    secret, domain = os.environ.get("OPS_CLIENT_SECRET", ""), os.environ.get("PREVIEW_DOMAIN", "")
    if not secret:
        die("OPS_CLIENT_SECRET is required")
    if not domain:
        die("PREVIEW_DOMAIN is required")
    print(f"previousBrowserFlow={kc.realm().get('browserFlow')}")

    # 1. Operations client first, and proven usable before anything that would lock the administrator's password out.
    ops = kc.client(OPS_CLIENT)
    if not ops:
        kc.post(f"/admin/realms/{REALM}/clients", {
            "clientId": OPS_CLIENT, "protocol": "openid-connect", "publicClient": False, "serviceAccountsEnabled": True, "standardFlowEnabled": False,
            "directAccessGrantsEnabled": False, "implicitFlowEnabled": False, "secret": secret,
            "description": "Operator tooling on the server. Never reachable with a password; secret only in the server .env"})
        print(f"client {OPS_CLIENT}: created")
        ops = kc.client(OPS_CLIENT)
    else:
        kc.update_client(ops, secret=secret)
        print(f"client {OPS_CLIENT}: present, secret set to the configured value")
    sa = kc.get(f"/admin/realms/{REALM}/clients/{ops['id']}/service-account-user")
    kc.add_realm_role(sa["id"], "admin")
    status_code, raw, _ = kc.raw("POST", "/realms/master/protocol/openid-connect/token", form={
        "grant_type": "client_credentials", "client_id": OPS_CLIENT, "client_secret": secret}, token=False)
    if status_code != 200:
        die("the operations client cannot sign in; the password grant was NOT switched off")
    print(f"client {OPS_CLIENT}: sign-in proven")

    # 2. Drift is read BEFORE anything is changed (realm settings, flow binding, enrolment, grants, overrides). If there is any (first run, or
    #    something was re-opened since), the completion marker is cleared first and set again only after the sessions and tokens issued before
    #    the hardening were revoked. A clean re-run revokes nothing, so the console's users stay signed in.
    wanted = {"bruteForceProtected": True, "permanentLockout": False, "failureFactor": 5, "waitIncrementSeconds": 60, "maxFailureWaitSeconds": 900,
              "maxDeltaTimeSeconds": 43200, "minimumQuickLoginWaitSeconds": 60, "registrationAllowed": False, "resetPasswordAllowed": False, "rememberMe": False}
    frontend = f"https://kc-admin.{domain}"
    realm = kc.realm()
    clients = kc.clients()
    totp = kc.get(f"/admin/realms/{REALM}/authentication/required-actions/CONFIGURE_TOTP")
    changed = (kc.attribute("matbakhyHardened") != "yes" or realm.get("browserFlow") != TOP or not flow_complete(kc) or totp.get("enabled") is not False
               or any(realm.get(k) != v for k, v in wanted.items()) or (realm.get("attributes") or {}).get("frontendUrl") != frontend
               or bool(password_grant_clients(clients)) or bool(override_clients(clients)))
    if changed:
        kc.update_realm(attributes={"matbakhyHardened": "no"})

    # 3. Realm: the master realm lives on the dedicated host, failed sign-ins are throttled, no self-service path.
    kc.update_realm(wanted, {"frontendUrl": frontend})
    print("realm settings: frontend URL, brute-force protection, no registration or reset-password")
    kc.login()  # the frontend URL changed this realm's token issuer

    # 4. Browser flow with a REQUIRED one-time password; bound only when the tree is verified complete.
    if flow_complete(kc):
        print("flow: already complete, left as is")
    else:
        if kc.flow_exists(TOP):
            if kc.realm().get("browserFlow") == TOP:
                die("the master flow is bound but incomplete; run rollback first, then harden again")
            print("flow: removing an incomplete earlier attempt")
            delete_flow(kc)
        build_flow(kc)
        if not flow_complete(kc):
            die("the flow tree is not complete after building it; the browser flow was NOT changed")
        print("flow: created and verified")

    clients = kc.clients()
    totp = kc.get(f"/admin/realms/{REALM}/authentication/required-actions/CONFIGURE_TOTP")
    if kc.realm().get("browserFlow") != TOP:
        kc.update_realm({"browserFlow": TOP})
        print(f"browserFlow: bound to '{TOP}'")
    else:
        print(f"browserFlow: already '{TOP}'")

    # 5. Nobody can enrol a one-time password over the Internet: with the required action disabled an account without OTP cannot sign in at all.
    totp.update({"enabled": False, "defaultAction": False})
    kc.put(f"/admin/realms/{REALM}/authentication/required-actions/CONFIGURE_TOTP", totp)
    print("required action CONFIGURE_TOTP: disabled (no public enrolment)")

    # 6. No master client may issue tokens for a user and password alone, and none may bind another browser or direct-grant flow.
    for c in clients:
        if c.get("directAccessGrantsEnabled"):
            kc.update_client(c, directAccessGrantsEnabled=False)
            print(f"client {c['clientId']}: password grant switched off")
    for c in override_clients(clients):
        kc.update_client(kc.get(f"/admin/realms/{REALM}/clients/{c['id']}"), authenticationFlowBindingOverrides={"browser": "", "direct_grant": ""})
        print(f"client {c['clientId']}: flow override removed")
    clients = kc.clients()
    if password_grant_clients(clients):
        die("a master client still accepts the password grant")
    if override_clients(clients):
        die("a master client still overrides the browser or direct-grant flow")
    print("master clients: no password grant, no flow overrides")

    # 7. Sessions and tokens issued before the hardening are revoked (only when something changed, not on every release).
    if changed:
        kc.update_realm({"notBefore": int(time.time())})
        kc.login()  # the token used so far predates the new not-before time
        kc.post(f"/admin/realms/{REALM}/logout-all")
        print("sessions and tokens issued before the hardening: revoked")
        kc.login()  # ending every session also ended this run's own
    kc.update_realm(attributes={"matbakhyHardened": "yes"})
    print("hardened")


def rollback(kc):
    target = os.environ.get("PREVIOUS_FLOW") or "browser"
    if not kc.flow_exists(target):
        die(f"flow '{target}' to bind again does not exist")
    kc.update_realm({"browserFlow": target}, {"matbakhyHardened": "no"})
    print(f"browserFlow: bound back to '{target}'")
    delete_flow(kc)
    print(f"flow '{TOP}': removed")
    totp = kc.get(f"/admin/realms/{REALM}/authentication/required-actions/CONFIGURE_TOTP")
    totp["enabled"] = True
    kc.put(f"/admin/realms/{REALM}/authentication/required-actions/CONFIGURE_TOTP", totp)
    print("required action CONFIGURE_TOTP: enabled again (Keycloak default)")
    cli = kc.client("admin-cli")
    if cli:
        kc.update_client(cli, directAccessGrantsEnabled=True)
        print("client admin-cli: password grant re-enabled")
    if os.environ.get("PURGE") == "1":
        # The frontend URL goes first and the operations client last: the client's own token stops working once it is deleted.
        kc.update_realm(attributes={"frontendUrl": ""})
        print("frontend URL cleared")
        kc.login()
        ops = kc.client(OPS_CLIENT)
        if ops:
            kc.delete(f"/admin/realms/{REALM}/clients/{ops['id']}")
            print(f"client {OPS_CLIENT}: removed")


STDIN_LINES = []


def read_lines():
    return STDIN_LINES


def fixture_create(kc):
    bodies = read_lines()
    if not bodies:
        die("fixture-create needs the user representations on standard input")
    for line in bodies:
        body = json.loads(line)
        name = body.get("username", "")
        if not re.fullmatch(r"verify-admin-[0-9a-f]+", name):
            die(f"the fixture username must start with {FIXTURE_PREFIX}")
        uid = kc.create_user(body)
        kc.add_client_roles(uid, "matbakhy-realm", READ_ROLES)
        # The console's first call (whoami) needs some right on the realm the administrator signs in to; view-realm is read-only.
        kc.add_client_roles(uid, "master-realm", ["view-realm"])
    print("fixture administrators: created")


def fixture_delete(kc):
    failed = False
    victims = [u for u in kc.paged(f"/admin/realms/{REALM}/users?search={urllib.parse.quote(FIXTURE_PREFIX)}") if u["username"].startswith(FIXTURE_PREFIX)]
    for u in victims:
        try:
            kc.delete(f"/admin/realms/{REALM}/users/{u['id']}")
            print(f"fixture administrator {u['username']}: removed")
        except SystemExit:
            print(f"fixture administrator {u['username']}: NOT removed", file=sys.stderr)
            failed = True
    if failed:
        die(f"fixture cleanup incomplete: users named {FIXTURE_PREFIX}* remain")


def admin_create(kc):
    lines = read_lines()
    if len(lines) != 1:
        die("admin-create needs exactly one user representation on standard input")
    body = json.loads(lines[0])
    name = body.get("username", "")
    if not re.fullmatch(r"[a-z][a-z0-9-]{2,30}", name) or name.startswith(FIXTURE_PREFIX):
        die("invalid administrator name")
    if kc.user_by_name(name):
        die(f"the administrator '{name}' already exists (use REPLACE=1 ./admin-console.sh create-admin {name} to replace it)")
    uid = kc.create_user(body)
    try:
        kc.add_realm_role(uid, "admin")
    except SystemExit:
        kc.delete(f"/admin/realms/{REALM}/users/{uid}")
        die("cannot give the administrator the admin role; the partly created account was removed")
    print(f"administrator '{name}': created with a password and a one-time password (both from the private file)")


def admin_delete(kc, name):
    if not re.fullmatch(r"[a-z][a-z0-9-]{2,30}", name or "") or name.startswith(FIXTURE_PREFIX):
        die("invalid administrator name")
    user = kc.user_by_name(name)
    if user:
        kc.delete(f"/admin/realms/{REALM}/users/{user['id']}")
        print(f"administrator '{name}': removed")


def main():
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd not in ("status", "harden", "rollback", "fixture-create", "fixture-delete", "admin-create", "admin-delete"):
        die("usage: admin_console.py status | harden | rollback | fixture-create | fixture-delete | admin-create | admin-delete NAME")
    if cmd in ("fixture-create", "admin-create"):
        STDIN_LINES.extend(line for line in sys.stdin.read().splitlines() if line.strip())
    kc = Kc()
    kc.login()
    {"status": status, "harden": harden, "rollback": rollback, "fixture-create": fixture_create, "fixture-delete": fixture_delete,
     "admin-create": admin_create}.get(cmd, lambda k: admin_delete(k, sys.argv[2] if len(sys.argv) > 2 else ""))(kc)


if __name__ == "__main__":
    main()
