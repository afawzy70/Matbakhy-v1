"""Static guards for the D18 Keycloak migration of the existing preview realm (docs/operations/13-keycloak-mfa-migration.md).

The live run needs Docker on the server and is not exercised here; these checks keep the scripts syntactically valid and keep the
properties the runbook promises: the new browser flow is bound last, secrets never travel as host command-line arguments, and the
public verification never submits an enrolment or an OTP.
"""
import ast
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WP = ROOT / "deploy" / "web-preview"
MIGRATION = WP / "identity" / "mfa-migration.kcadm.sh"
WRAPPER = WP / "kcadm-in-container.sh"
OPERATOR = WP / "migrate-mfa.sh"
STATE_LIB = WP / "identity" / "mfa-state.sh"
VERIFY = WP / "identity" / "verify-mfa-flow.py"


class KeycloakMfaMigrationTest(unittest.TestCase):
    def test_shell_scripts_are_valid_posix_sh(self):
        for script in (MIGRATION, WRAPPER, OPERATOR, STATE_LIB):
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
        self.assertNotIn("--password", wrapper)  # kcadm reads KC_CLI_PASSWORD from the container environment: no password argument
        self.assertIn("-e ADMIN_USER -e KC_CLI_PASSWORD", wrapper)  # forwarded by name, not by value

    def test_the_rollback_baseline_survives_a_repeated_apply_and_the_state_is_never_sourced(self):
        text = OPERATOR.read_text()
        self.assertIn('[ "$prev" = "matbakhy browser" ] && [ -f "$STATE" ]', text)
        self.assertIn("state_flow", text)
        self.assertNotRegex(text, r'(?m)^\s*\.\s+"?\$STATE', "the state file must be parsed as data, not sourced")
        self.assertNotIn("PREVIOUS_BROWSER_FLOW='%s'", text)

    def test_unusual_flow_names_round_trip_without_executing_anything(self):
        names = ["browser", "matbakhy browser", "owner's browser", 'say "hi" $(touch PWNED) `touch PWNED2` ; touch PWNED3', "عربي flow", "a\\b $HOME *"]
        with tempfile.TemporaryDirectory() as d:
            for i, name in enumerate(names):
                f = Path(d) / f"state{i}.env"
                # pass the name through the environment so the test itself never builds shell text from it
                w = subprocess.run(["sh", "-c", f". '{STATE_LIB}'; state_write '{f}' 20261018T101010Z \"$FLOW\""], capture_output=True, text=True, env={"FLOW": name, "PATH": "/usr/bin:/bin"}, cwd=d)
                self.assertEqual(w.returncode, 0, w.stderr)
                r = subprocess.run(["sh", "-c", f". '{STATE_LIB}'; state_flow '{f}'"], capture_output=True, text=True, env={"PATH": "/usr/bin:/bin"}, cwd=d)
                self.assertEqual((r.returncode, r.stdout), (0, name), r.stderr)
            self.assertEqual(sorted(p.name for p in Path(d).iterdir() if p.name.startswith("PWNED")), [])

    def test_a_tampered_state_file_is_refused_and_nothing_runs(self):
        with tempfile.TemporaryDirectory() as d:
            f = Path(d) / "state.env"
            for body in (
                "STAMP=20261018T101010Z\nPREVIOUS_BROWSER_FLOW_B64=$(touch PWNED)\n",
                "PREVIOUS_BROWSER_FLOW_B64='x'; touch PWNED\n",
                "PREVIOUS_BROWSER_FLOW_B64=YQpi\n",  # decodes to "a\nb": a flow name is a single line
                "PREVIOUS_BROWSER_FLOW_B64=\n",
                "STAMP=x; touch PWNED\n",
            ):
                f.write_text(body)
                r = subprocess.run(["sh", "-c", f". '{STATE_LIB}'; state_flow '{f}' && state_read '{f}' STAMP"], capture_output=True, text=True, env={"PATH": "/usr/bin:/bin"}, cwd=d)
                self.assertNotEqual(r.returncode, 0, body)
                self.assertFalse((Path(d) / "PWNED").exists(), body)

    def test_state_write_refuses_an_unexpected_stamp(self):
        with tempfile.TemporaryDirectory() as d:
            r = subprocess.run(["sh", "-c", f". '{STATE_LIB}'; state_write '{d}/s.env' 'x;touch PWNED' browser"], capture_output=True, text=True, env={"PATH": "/usr/bin:/bin"}, cwd=d)
            self.assertNotEqual(r.returncode, 0)
            self.assertFalse((Path(d) / "PWNED").exists())

    def _fake_kcadm(self, directory):
        fake = Path(directory) / "kcadm.sh"
        fake.write_text(
            "#!/bin/sh\n"
            'case "$1 $2" in\n'
            '  "config credentials") exit 0 ;;\n'
            '  "get realms/matbakhy") echo matbakhy; exit 0 ;;\n'
            '  "get users") [ -z "$FAKE_FAIL_LIST" ] || exit 1; printf "%s\\n" "$FAKE_ROWS"; exit 0 ;;\n'
            '  "delete users/"*) cat >/dev/null; echo "$2" >> "$FAKE_LOG"; case "$2" in "users/$FAKE_FAIL_ID") exit 1 ;; esac; exit 0 ;;\n'
            "esac\nexit 0\n"
        )
        fake.chmod(0o755)
        return fake

    def _fixture_delete(self, d, rows, **extra):
        env = {"PATH": "/usr/bin:/bin", "ADMIN_USER": "a", "ADMIN_PASSWORD": "b", "KCADM": str(self._fake_kcadm(d)), "KCADM_CONFIG": f"{d}/cfg",
               "FAKE_ROWS": rows, "FAKE_LOG": f"{d}/log", **extra}
        r = subprocess.run(["sh", str(MIGRATION), "fixture-delete"], capture_output=True, text=True, env=env, cwd=d)
        log = Path(d, "log")
        return r, (log.read_text().split() if log.exists() else [])

    def test_fixture_cleanup_deletes_only_prefixed_users_and_reports_every_failure(self):
        rows = "id1,verify-mfa-aaaa\nid2,preview-customer-1\nid3,verify-mfa-bbbb\nid4,verify-mfa-cccc"
        with tempfile.TemporaryDirectory() as d:
            r, deleted = self._fixture_delete(d, rows)
            self.assertEqual(r.returncode, 0, r.stderr)
            self.assertEqual(deleted, ["users/id1", "users/id3", "users/id4"])
        with tempfile.TemporaryDirectory() as d:
            r, deleted = self._fixture_delete(d, rows, FAKE_FAIL_ID="id1")
            self.assertNotEqual(r.returncode, 0, "a failed delete must not look like a clean cleanup")
            self.assertEqual(deleted, ["users/id1", "users/id3", "users/id4"], "the loop must go on after one failure")
        with tempfile.TemporaryDirectory() as d:
            r, deleted = self._fixture_delete(d, rows, FAKE_FAIL_LIST="1")
            self.assertNotEqual(r.returncode, 0, "a failed listing must not look like a clean cleanup")
            self.assertEqual(deleted, [])

    def test_apply_keeps_the_incomplete_verification_exit_status(self):
        text = OPERATOR.read_text()
        self.assertIn("exit 3", text)
        self.assertIn("cleanup_rc", text)

    def test_nothing_deletes_users_or_credentials(self):
        text = MIGRATION.read_text()
        for forbidden in ("delete users", "delete realms", "credentials/", "reset-password"):
            self.assertNotIn(forbidden, text)

    def test_verification_proves_assurance_with_real_tokens_and_never_passes_without_them(self):
        text = VERIFY.read_text()
        self.assertIn("RESULT: INCOMPLETE", text)
        self.assertIn("sys.exit(3)", text)
        for needle in ('"STEP_UP_REQUIRED"', '"otp" in amr2', "MFA_ACR", "exchange(code)", "grant_type"):
            self.assertIn(needle, text)
        operator = OPERATOR.read_text()
        self.assertIn("VERIFY_FIXTURE_FILE", operator)
        self.assertIn("fixture-delete", operator)
        self.assertIn("NOT proven", operator, "apply must say plainly when assurance was not proven")

    def test_only_the_synthetic_fixture_user_can_be_removed_and_an_otp_is_only_ever_submitted_for_it(self):
        migration = MIGRATION.read_text()
        self.assertIn('FIXTURE_PREFIX="verify-mfa-"', migration)
        self.assertIn('"$FIXTURE_PREFIX"*)', migration)
        text = VERIFY.read_text()
        self.assertEqual(text.count('{"otp": totp(otp_secret)'), 1)
        self.assertIn("if otp_action and otp_secret:", text)
        self.assertIn("sign_in(\"matbakhy-web\"", text)  # the public part still never answers an OTP form
        self.assertIn("SKIP mobile checks", text)



if __name__ == "__main__":
    unittest.main()
