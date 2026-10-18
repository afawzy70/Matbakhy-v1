"""Guards for the iPhone TestFlight beta preparation (docs/mobile/ios-testflight-beta.md).

Release engineering only: these checks keep the beta identity separate from the development identity, the endpoints on HTTPS, the OIDC
redirect URI registered exactly once in the preview realm, the distribution workflow manual and owner-gated, and every credential,
certificate and provisioning profile out of Git. They do not build or upload anything.
"""
import json
import re
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "apps" / "customer-mobile"
REALM = ROOT / "deploy" / "web-preview" / "identity" / "matbakhy-realm.preview.json"
WORKFLOW = ROOT / ".github" / "workflows" / "ios-beta.yml"
SECRET_WORDS = re.compile(r"secret|password|token|private|p8|p12|cert|provision|apikey|api_key", re.I)


def resolved(variant=None):
    env = {"PATH": __import__("os").environ["PATH"]}
    if variant:
        env["APP_VARIANT"] = variant
    script = 'const f=require("./app.config.cjs"); const base=require("./app.json").expo; process.stdout.write(JSON.stringify(f({config:base})))'
    out = subprocess.run(["node", "-e", script], cwd=APP, env=env, capture_output=True, text=True, check=True).stdout
    return json.loads(out)


class IosBetaConfigTest(unittest.TestCase):
    def test_development_identity_is_untouched(self):
        dev = resolved()
        self.assertEqual(dev["scheme"], "matbakhy-dev")
        self.assertEqual(dev["ios"]["bundleIdentifier"], "com.matbakhy.customer.dev")

    def test_beta_has_its_own_identity(self):
        dev, beta = resolved(), resolved("preview")
        self.assertEqual(beta["ios"]["bundleIdentifier"], "com.matbakhy.customer.beta")
        self.assertEqual(beta["scheme"], "matbakhy-beta")
        self.assertEqual(beta["android"]["package"], "com.matbakhy.customer.beta")
        self.assertNotEqual(beta["ios"]["bundleIdentifier"], dev["ios"]["bundleIdentifier"])
        self.assertNotEqual(beta["scheme"], dev["scheme"])
        self.assertEqual(beta["extra"]["environment"], "staging")
        self.assertFalse(beta["ios"]["infoPlist"]["ITSAppUsesNonExemptEncryption"])
        self.assertRegex(beta["version"], r"^\d+\.\d+\.\d+$")
        self.assertEqual(beta["ios"]["buildNumber"], "1")

    def test_an_unknown_variant_is_refused(self):
        result = subprocess.run(["node", "-e", 'require("./app.config.cjs")({config:{}})'], cwd=APP,
                                env={"PATH": __import__("os").environ["PATH"], "APP_VARIANT": "production"}, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)

    def test_eas_profile_uses_https_preview_endpoints_and_holds_no_credential(self):
        eas = json.loads((APP / "eas.json").read_text())
        profile = eas["build"]["testflight"]
        self.assertEqual(profile["distribution"], "store")
        self.assertTrue(profile.get("autoIncrement"))
        self.assertEqual(eas["cli"]["appVersionSource"], "remote")
        env = profile["env"]
        self.assertEqual(env["APP_VARIANT"], "preview")
        self.assertEqual(env["EXPO_PUBLIC_ENVIRONMENT"], "staging")
        self.assertTrue(env["EXPO_PUBLIC_API_URL"].startswith("https://api."))
        self.assertTrue(env["EXPO_PUBLIC_OIDC_ISSUER"].startswith("https://id."))
        self.assertTrue(env["EXPO_PUBLIC_OIDC_ISSUER"].endswith("/realms/matbakhy"))
        self.assertEqual(env["EXPO_PUBLIC_OIDC_CLIENT_ID"], "matbakhy-mobile")
        for name in env:
            self.assertFalse(SECRET_WORDS.search(name), f"EAS env name {name} looks like a credential")
        submit = json.dumps(eas.get("submit", {}))
        for word in ("ascApiKey", "appleId", "appleTeamId", "keyId", "issuerId", ".p8"):
            self.assertNotIn(word, submit, "Apple credentials belong in EAS, not in eas.json")

    def test_the_redirect_uri_is_registered_exactly_in_the_preview_realm(self):
        beta = resolved("preview")
        redirect = f"{beta['scheme']}://auth/callback"
        realm = json.loads(REALM.read_text())
        client = next(c for c in realm["clients"] if c["clientId"] == "matbakhy-mobile")
        self.assertEqual(client["redirectUris"], [redirect])
        self.assertTrue(client["publicClient"])
        self.assertEqual(client["attributes"]["pkce.code.challenge.method"], "S256")
        self.assertFalse(client.get("directAccessGrantsEnabled", False))
        self.assertNotIn("secret", client)
        audiences = [m["config"].get("included.custom.audience") for m in client["protocolMappers"] if m["protocolMapper"] == "oidc-audience-mapper"]
        self.assertEqual(audiences, ["matbakhy-api"])
        self.assertEqual(sum(1 for c in realm["clients"] if "matbakhy-dev://auth/callback" in c.get("redirectUris", [])), 0)

    def test_the_distribution_workflow_is_manual_and_owner_gated(self):
        text = WORKFLOW.read_text()
        triggers = text.split("jobs:")[0]
        self.assertIn("workflow_dispatch:", triggers)
        self.assertNotIn("push:", triggers)
        self.assertNotIn("schedule:", triggers)
        build = text[text.index("  build:"):text.index("  submit:")]
        submit = text[text.index("  submit:"):]
        self.assertIn("environment: ios-beta\n", build)
        self.assertIn("environment: ios-beta-submit", submit)
        self.assertIn("BUILD-IOS-BETA", build)
        self.assertIn("SUBMIT-TO-TESTFLIGHT", submit)
        self.assertIn("--non-interactive", build + submit)
        self.assertIn('--id "$BUILD_ID"', submit)
        self.assertNotIn("--latest", submit, "the reviewer must approve one named build, not whatever is newest")
        self.assertIn("vars.ASC_APP_ID", submit)
        for line in text.splitlines():
            if "inputs.confirm" in line and not line.lstrip().startswith(("#", "description:")):
                self.assertRegex(line, r"^\s+CONFIRM: \$\{\{ inputs\.confirm \}\}$", "the confirmation text must reach the shell through env, not interpolation")
        used = set(re.findall(r"secrets\.([A-Z0-9_]+)", text))
        self.assertEqual(used, {"EXPO_TOKEN"})

    def test_no_signing_material_is_tracked(self):
        tracked = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True, check=True).stdout.splitlines()
        bad = [f for f in tracked if re.search(r"\.(p12|p8|mobileprovision|cer|certSigningRequest|keystore|jks)$", f, re.I)]
        self.assertEqual(bad, [])
        gitignore = (ROOT / ".gitignore").read_text()
        for pattern in ("*.p12", "*.p8", "*.mobileprovision"):
            self.assertIn(pattern, gitignore)


if __name__ == "__main__":
    unittest.main()
