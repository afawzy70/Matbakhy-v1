#!/usr/bin/env python3
"""Generates a Keycloak master-realm administrator for a person, on the server (admin-console.sh create-admin NAME).

  provision-admin.py new-admin DIR NAME    writes DIR/NAME.private (mode 600) and prints the user representation (JSON, one line) for
                                           `admin_console.py admin-create` on standard input.

The private file holds the generated password, the one-time-password key (base32, for "enter a setup key" in an authenticator app) and the
otpauth:// URI. Nothing secret is printed. The account is created with both credentials, so no enrolment ever happens on the public page.
"""
import base64
import json
import os
import re
import secrets
import sys
import urllib.parse

ISSUER = "Matbakhy Preview Keycloak"


def main():
    if len(sys.argv) != 4 or sys.argv[1] != "new-admin":
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    directory, name = sys.argv[2], sys.argv[3]
    if not re.fullmatch(r"[a-z][a-z0-9-]{2,30}", name) or name.startswith("verify-admin-"):
        sys.exit("administrator name: 3-31 characters, lower-case letters, digits and '-', starting with a letter, not 'verify-admin-*'")
    password = secrets.token_urlsafe(24)
    alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
    key = "".join(secrets.choice(alphabet) for _ in range(32))  # Keycloak keys the HMAC with this string's bytes
    base32 = base64.b32encode(key.encode()).decode().rstrip("=")
    uri = "otpauth://totp/%s:%s?%s" % (urllib.parse.quote(ISSUER), name, urllib.parse.urlencode(
        {"secret": base32, "issuer": ISSUER, "algorithm": "SHA1", "digits": 6, "period": 30}, quote_via=urllib.parse.quote))
    os.makedirs(directory, mode=0o700, exist_ok=True)
    path = os.path.join(directory, name + ".private")
    if os.environ.get("REPLACE") == "1" and os.path.exists(path):
        os.replace(path, path + ".replaced")  # kept until the new account exists; the operator deletes it
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as f:
        f.write("Keycloak administrator for the Matbakhy Preview (keep private; delete this file once the authenticator app is set up)\n"
                f"username: {name}\npassword: {password}\n"
                f"one-time password key (base32, enter as a setup key): {base32}\notpauth URI: {uri}\n"
                "sign in at the kc-admin host of the preview; the key above is the second factor, there is no enrolment page.\n")
    print(json.dumps({
        "username": name, "enabled": True, "emailVerified": True, "email": f"{name}@preview.matbakhy.invalid",
        "firstName": name, "lastName": "Administrator",
        "credentials": [
            {"type": "password", "value": password, "temporary": False},
            {"type": "otp", "secretData": json.dumps({"value": key}),
             "credentialData": json.dumps({"subType": "totp", "digits": 6, "period": 30, "algorithm": "HmacSHA1", "counter": 0})},
        ]}))


if __name__ == "__main__":
    main()
