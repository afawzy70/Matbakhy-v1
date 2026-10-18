"""Static guards for the D18 Keycloak migration of the existing preview realm (docs/operations/13-keycloak-mfa-migration.md).

The live run needs Docker on the server and is not exercised here; these checks keep the scripts syntactically valid and keep the
properties the runbook promises: the new browser flow is bound last, secrets never travel as host command-line arguments, and the
public verification never submits an enrolment or an OTP.
"""
import ast
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WP = ROOT / "deploy" / "web-preview"
MIGRATION = WP / "identity" / "mfa-migration.kcadm.sh"
WRAPPER = WP / "kcadm-in-container.sh"
OPERATOR = WP / "migrate-mfa.sh"
VERIFY = WP / "identity" / "verify-mfa-flow.py"


class KeycloakMfaMigrationTest(unittest.TestCase):
    def test_shell_scripts_are_valid_posix_sh(self):
        for script in (MIGRATION, WRAPPER, OPERATOR):
            result = subprocess.run(["sh", "-n", str(script)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, f"{script.name}: {result.stderr}")

    def test_verification_script_is_valid_python(self):
        ast.parse(VERIFY.read_text())

    def test_the_new_flow_is_bound_only_after_it_was_verified_complete(self):
        text = MIGRATION.read_text()
        apply_body = text[text.index("apply() {"):text.index("rollback() {")]
        self.assertLess(apply_body.index("flow_complete || die"), apply_body.index('browserFlow="$TOP"'))
        self.assertEqual(apply_body.count('browserFlow="$TOP"'), 1)

    def test_secrets_are_not_passed_as_host_arguments(self):
        text = MIGRATION.read_text()
        self.assertNotIn('secret="$ADMIN_CLIENT_SECRET"', text, "the client secret must go through standard input")
        remote = text[text.index("login() {"):text.index("flow_id()")]
        self.assertIn("KCADM_REMOTE", remote)
        wrapper = WRAPPER.read_text()
        self.assertIn('--password "$ADMIN_PASSWORD"', wrapper)  # expanded inside the container shell (single-quoted here)
        self.assertIn("-e ADMIN_USER -e ADMIN_PASSWORD", wrapper)  # forwarded by name, not by value

    def test_the_rollback_baseline_survives_a_repeated_apply_and_the_state_file_can_be_sourced(self):
        text = OPERATOR.read_text()
        self.assertIn('[ "$prev" = "matbakhy browser" ] && [ -f "$STATE" ]', text)
        self.assertIn("PREVIOUS_BROWSER_FLOW='%s'", text, "values with spaces must be quoted so the state file can be sourced")

    def test_nothing_deletes_users_or_credentials(self):
        text = MIGRATION.read_text()
        for forbidden in ("delete users", "delete realms", "credentials/", "reset-password"):
            self.assertNotIn(forbidden, text)

    def test_public_verification_never_submits_an_enrolment_or_otp(self):
        text = VERIFY.read_text()
        self.assertNotIn('"otp":', text)
        self.assertNotIn("totpSecret\":", text)
        self.assertIn("SKIP mobile checks", text)


if __name__ == "__main__":
    unittest.main()
