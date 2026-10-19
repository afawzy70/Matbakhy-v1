"""Static guards for the public Preview Keycloak admin console (owner approval 2026-10-19; docs/operations/14-keycloak-admin-console.md).

The live run needs Docker and the server, and the proxy behaviour needs a Caddy image (scripts/test_proxy_access.py runs the real Caddyfile); these checks keep the
files valid and keep the promises the runbook makes: the host is closed unless the switch exists, only the console's paths are forwarded, no infrastructure port
is published, the second factor is REQUIRED (not conditional), the password grant is switched off only after the operations client proved it can sign in, and the
console is opened only after hardening and closed again when the live verification does not pass.
"""
import ast
import re
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WP = ROOT / "deploy" / "web-preview"
CADDYFILE = (WP / "Caddyfile").read_text()
COMPOSE = (WP / "compose.yaml").read_text()
KCADM = WP / "identity" / "admin-console.kcadm.sh"
OPERATOR = WP / "admin-console.sh"
WRAPPER = WP / "kcadm-in-container.sh"
VERIFY = WP / "identity" / "verify-admin-console.py"
SMOKE = WP / "smoke.sh"
DEPLOY = WP / "deploy.sh"


def site_block(text, header):
    start = text.index(header + " {")
    depth, i = 0, start + len(header) + 1
    for j in range(i, len(text)):
        depth += text[j] == "{"
        depth -= text[j] == "}"
        if depth == 0:
            return text[start:j + 1]
    raise AssertionError("unbalanced braces")


class AdminConsoleFilesTest(unittest.TestCase):
    def test_shell_scripts_are_valid_posix_sh(self):
        for script in (KCADM, OPERATOR, WRAPPER, SMOKE, DEPLOY):
            result = subprocess.run(["sh", "-n", str(script)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, f"{script.name}: {result.stderr}")

    def test_verification_script_is_valid_python(self):
        ast.parse(VERIFY.read_text())

    def test_the_admin_host_is_fail_closed_and_forwards_only_the_console_paths(self):
        block = site_block(CADDYFILE, "kc-admin.{$PREVIEW_DOMAIN}")
        self.assertIn("/srv/flags", block)
        self.assertIn("admin-console-public", block)
        self.assertLess(block.index('respond @closed "Not available" 404'), block.index("reverse_proxy"), "the switch must be checked before anything is forwarded")
        proxies = re.findall(r"^\s*reverse_proxy .*$", block, re.M)
        self.assertEqual(len(proxies), 1)
        self.assertTrue(proxies[0].strip().startswith("reverse_proxy @console identity:8080"), "forwarding must be limited to the console matcher")
        paths = re.search(r"@console path (.*)", block).group(1).split()
        self.assertEqual(sorted(paths), sorted(["/admin", "/admin/*", "/realms/master", "/realms/master/*", "/resources/*", "/js/*"]))
        self.assertRegex(block, r"respond 404\s*}\s*}\s*$", "everything else must answer 404")
        self.assertNotIn("matbakhy", block.replace("matbakhy realm", ""), "the matbakhy realm must not be reachable through this host")
        self.assertNotIn("9000", block)

    def test_the_admin_host_is_public_by_design_and_never_follows_an_ip_class(self):
        block = site_block(CADDYFILE, "kc-admin.{$PREVIEW_DOMAIN}")
        self.assertNotIn("remote_ip", block)
        self.assertNotIn("customer_access", block)
        self.assertNotIn("staff_access", block)

    def test_the_customer_host_still_never_serves_the_console_or_the_master_realm(self):
        block = site_block(CADDYFILE, "id.{$PREVIEW_DOMAIN}")
        self.assertIn("@admin path /admin /admin/* /realms/master /realms/master/*", block)
        self.assertIn("respond @admin 404", block)
        self.assertLess(block.index("respond @admin 404"), block.index("reverse_proxy identity:8080"))
        self.assertIn("import customer_access", block)

    def test_keycloak_uses_the_dedicated_admin_hostname_and_keeps_the_customer_hostname(self):
        self.assertIn("KC_HOSTNAME: https://id.${PREVIEW_DOMAIN}", COMPOSE)
        self.assertIn("KC_HOSTNAME_ADMIN: https://kc-admin.${PREVIEW_DOMAIN}", COMPOSE)

    def test_only_the_proxy_publishes_ports_to_the_network_everything_else_is_loopback_or_internal(self):
        services = re.split(r"^  (?=[a-z0-9-]+:\s*$)", COMPOSE.split("\nservices:\n", 1)[1].split("\nnetworks:", 1)[0], flags=re.M)
        for svc in services:
            name = svc.split(":", 1)[0]
            for port in re.findall(r'^\s*-\s*"?([0-9.:]+:\d+:\d+)"?\s*$', svc, re.M):
                if name == "proxy":
                    self.assertIn(port, ("80:80", "443:443"))
                else:
                    self.assertTrue(port.startswith("127.0.0.1:"), f"{name} publishes {port}")
        for name in ("identity", "postgres", "storage", "api"):
            body = next(s for s in services if s.startswith(name + ":"))
            self.assertNotRegex(body, r"^\s*ports:", f"{name} must not publish ports")

    def test_the_switch_directory_is_mounted_read_only_into_the_proxy(self):
        self.assertRegex(COMPOSE, r"/flags:/srv/flags:ro")

    def test_one_time_password_is_required_not_conditional_in_the_master_flow(self):
        text = KCADM.read_text()
        build = text[text.index("build_flow() {"):text.index("delete_flow() {")]
        self.assertIn("auth-username-password-form", build)
        self.assertIn("auth-otp-form", build)
        self.assertNotIn("conditional", build.lower())
        self.assertIn('"$(exec_id_by_provider "$FORMS" auth-otp-form)" REQUIRED', build)
        self.assertIn("auth-otp-form,REQUIRED", text[text.index("flow_complete() {"):text.index("build_flow() {")])

    def test_the_password_grant_is_switched_off_only_after_the_operations_client_signed_in(self):
        text = KCADM.read_text()
        harden = text[text.index("harden() {"):text.index("rollback() {")]
        proven = harden.index("sign-in proven")
        self.assertLess(proven, harden.index("directAccessGrantsEnabled=false"))
        self.assertLess(harden.index("cannot sign in; the password grant was NOT switched off"), harden.index("directAccessGrantsEnabled=false"))
        self.assertLess(harden.index("flow_complete || die"), harden.index('browserFlow="$TOP"'))
        self.assertEqual(harden.count('browserFlow="$TOP"'), 1)
        self.assertIn("a master client still accepts the password grant", harden)
        self.assertIn("bruteForceProtected=true", harden)
        self.assertIn("registrationAllowed=false", harden)
        self.assertIn("kc-admin.$PREVIEW_DOMAIN", harden)

    def test_secrets_are_not_passed_as_host_arguments(self):
        text = KCADM.read_text()
        self.assertNotIn('secret="$OPS_CLIENT_SECRET"', text)
        self.assertIn("kc_body create clients", text, "the client secret must go through standard input")
        wrapper = WRAPPER.read_text()
        self.assertIn("-e KC_CLI_CLIENT_SECRET identity sh -c", wrapper, "inside the container the secret comes from the environment")
        self.assertIn("-e KC_CLI_PASSWORD identity sh -c", wrapper)
        for script in (OPERATOR, KCADM, WRAPPER):
            # kcadm lists in `ps` (container processes are visible on the host): no secret or password may follow --secret / --password.
            self.assertNotRegex(script.read_text(), r"--(secret|password)\b", f"{script.name} passes a credential as an argument")

    def test_nobody_can_enrol_a_one_time_password_on_the_public_page(self):
        text = KCADM.read_text()
        harden = text[text.index("harden() {"):text.index("rollback() {")]
        self.assertIn("authentication/required-actions/CONFIGURE_TOTP", harden)
        self.assertRegex(harden, r"CONFIGURE_TOTP -r \"\$REALM\" -s enabled=false")
        op = OPERATOR.read_text()
        create = op[op.index("  create-admin)"):op.index("  disable)")]
        self.assertIn('(umask 077; mkdir -p "$dir")', create)
        self.assertIn("provision-admin.py new-admin", create)
        self.assertIn("run_kc admin-create", create)
        provision = (WP / "identity" / "provision-admin.py").read_text()
        self.assertIn("0o600", provision)
        self.assertIn("O_EXCL", provision)
        self.assertNotIn("print(password", provision)

    def test_hardening_revokes_older_sessions_and_removes_client_flow_overrides(self):
        text = KCADM.read_text()
        harden = text[text.index("harden() {"):text.index("rollback() {")]
        self.assertIn("authenticationFlowBindingOverrides={}", harden)
        self.assertIn("a master client still overrides the browser or direct-grant flow", harden)
        self.assertLess(harden.index("notBefore="), harden.index("logout-all"))
        self.assertIn('if [ "$changed" = 1 ]', harden, "revocation happens on a change, not on every release")

    def test_revocation_cannot_be_skipped_by_an_interrupted_earlier_run_and_purge_deletes_the_client_last(self):
        text = KCADM.read_text()
        harden = text[text.index("harden() {"):text.index("rollback() {")]
        self.assertIn("matbakhyHardened", harden)
        self.assertLess(harden.index("logout-all"), harden.index("attributes.matbakhyHardened=yes"), "the marker is set only after revocation")
        self.assertIn("CONFIGURE_TOTP -r \"$REALM\" --fields enabled", harden)
        no, bind, grants = harden.index("attributes.matbakhyHardened=no"), harden.index('browserFlow="$TOP"'), harden.index("directAccessGrantsEnabled=false")
        self.assertLess(no, bind, "the marker is cleared before the first mutation that matters for sessions")
        self.assertLess(no, grants)
        self.assertLess(no, harden.index("authentication/required-actions/CONFIGURE_TOTP -r \"$REALM\" -s enabled=false"))
        rollback = text[text.index("rollback() {"):text.index("# Synthetic verification administrators")]
        self.assertIn("attributes.matbakhyHardened=no", rollback)
        self.assertIn("CONFIGURE_TOTP -r \"$REALM\" -s enabled=true", rollback)
        self.assertLess(rollback.index("attributes.frontendUrl="), rollback.index('kc delete "clients/$oid"'))
        self.assertLess(rollback.index("login\n"), rollback.index('kc delete "clients/$oid"'))

    def test_a_failed_read_of_the_master_clients_stops_the_run_instead_of_looking_clean(self):
        text = KCADM.read_text()
        self.assertIn('load_grants() { GRANTS_RAW="$(kc get clients', text)
        self.assertIn('|| die "cannot read the master clients"', text)
        harden = text[text.index("harden() {"):text.index("rollback() {")]
        self.assertLess(harden.index("load_grants"), harden.index("attributes.matbakhyHardened=no"))

    def test_the_env_file_is_never_world_readable_while_the_operations_secret_is_added(self):
        text = OPERATOR.read_text()
        func = text[text.index("ensure_ops_secret() {"):text.index("run_kc() {")]
        self.assertIn("umask 077; mktemp", func)
        self.assertIn('trap \'rm -f "$tmp"\' EXIT', func)
        self.assertLess(func.index("umask 077; mktemp"), func.index("mv \"$tmp\""))

    def test_replacing_an_administrator_prepares_the_new_credentials_first_and_protects_the_last_one(self):
        text = OPERATOR.read_text()
        create = text[text.index("  create-admin)"):text.index("  disable)")]
        self.assertLess(create.index("provision-admin.py new-admin"), create.index("run_kc admin-delete"))
        self.assertIn("ALLOW_LAST_ADMIN", create)
        self.assertLess(create.index("ALLOW_LAST_ADMIN"), create.index("run_kc admin-delete"))
        self.assertIn("the partly created account was removed", KCADM.read_text())

    def test_a_certificate_still_pending_does_not_fail_the_smoke_under_set_e(self):
        text = SMOKE.read_text()
        func = text[text.index("kc_code() {"):text.index("kc_state=")]
        script = "set -eu\ncode() { echo 000; return 7; }\nsleep() { :; }\n" + func + 'echo "got:$(kc_code https://x.invalid/)"\n'
        result = subprocess.run(["sh", "-c", script], capture_output=True, text=True)
        self.assertEqual((result.returncode, result.stdout.strip()), (0, "got:000"), result.stderr)

    def test_the_master_realm_and_matbakhy_realm_users_are_never_modified_by_hardening(self):
        text = KCADM.read_text()
        harden = text[text.index("harden() {"):text.index("rollback() {")]
        self.assertNotIn("-r matbakhy", text)
        self.assertNotIn("delete users", harden)
        self.assertEqual(re.findall(r"^REALM=(\w+)$", text, re.M), ["master"])

    def test_fixtures_are_removed_by_prefix_only_and_are_read_only_administrators(self):
        text = KCADM.read_text()
        self.assertIn('FIXTURE_PREFIX="verify-admin-"', text)
        self.assertIn('"$FIXTURE_PREFIX"*)', text)
        roles = re.findall(r"--rolename (\S+)", text[text.index("fixture_create() {"):text.index("admin_create() {")])
        self.assertTrue(roles and all(r.startswith(("view-", "query-")) for r in roles), roles)

    def test_the_console_is_opened_only_after_hardening_and_closed_again_when_verification_fails(self):
        text = OPERATOR.read_text()
        enable = text[text.index("  enable)"):text.index("  verify) verify ;;")]
        self.assertLess(enable.index("close_switch"), enable.index("run_kc harden"))
        self.assertLess(enable.index("backup"), enable.index("run_kc harden"))
        self.assertLess(enable.index("run_kc harden"), enable.index("open_switch"))
        self.assertLess(enable.index("trap 'rc=$?; [ \"$enabled\" = 1 ] || close_switch; exit \"$rc\"' EXIT"), enable.index("open_switch"),
                        "every exit between opening and a passed verification must close the switch")
        for signal in ("HUP", "INT", "TERM"):
            self.assertLess(enable.index(f"' {signal}"), enable.index("open_switch"))
        self.assertLess(enable.index("open_switch"), enable.index("(verify) || rc=$?"), "verify runs in a subshell so its own traps cannot replace the guard")
        failure = enable[enable.index('if [ "$rc" -ne 0 ]; then'):]
        self.assertLess(failure.index("close_switch"), failure.index("exit"))
        self.assertLess(enable.index('enabled=1'), enable.index("OPEN:"))
        self.assertEqual(enable.count("enabled=1"), 1)
        self.assertIn('create-admin "$owner"', enable, "an owner administrator is created on the server when nobody can sign in")
        self.assertGreater(enable.index('create-admin "$owner"'), enable.index("OPEN:"), "created only after a passed verification")
        accounts = KCADM.read_text()
        accounts = accounts[accounts.index("admin_accounts() {"):accounts.index("status() {")]
        for state in ("disabled", "not-an-administrator", "no-otp(cannot-sign-in)", "otp"):
            self.assertIn(f"={state}", accounts)
        self.assertIn("trap fx_cleanup EXIT", text)
        self.assertIn("fixture-delete", text)

    def test_nothing_secret_is_in_the_repository_files(self):
        for path in (KCADM, OPERATOR, VERIFY, WRAPPER, WP / ".env.example"):
            text = path.read_text()
            self.assertNotRegex(text, r"(?i)(password|secret)\s*=\s*[\"']?[A-Za-z0-9]{20,}", path.name)
        self.assertIn("KEYCLOAK_OPS_CLIENT_SECRET=REPLACE_ME", (WP / ".env.example").read_text())

    def test_smoke_checks_the_admin_host(self):
        text = SMOKE.read_text()
        self.assertIn("kc-admin.$d", text)
        self.assertIn("/admin/realms", text)
        self.assertIn("/realms/matbakhy/.well-known/openid-configuration /health /metrics", text)
        self.assertIn("is not reachable yet", text, "a pending certificate must not fail the customer release")

    def test_deploy_script_exposes_the_command_and_prepares_the_switch_directory(self):
        text = DEPLOY.read_text()
        self.assertIn("admin-console) shift; load; sh ./admin-console.sh", text)
        self.assertIn('mkdir -p "$GENERATED_DIR/flags"', text)

    def test_the_ci_deploy_workflow_enables_and_verifies_the_console_after_the_release(self):
        text = (ROOT / ".github" / "workflows" / "deploy-preview.yml").read_text()
        self.assertIn("deploy.sh admin-console enable", text)
        self.assertLess(text.index("DEPLOYED $sha"), text.index("deploy.sh admin-console enable"))


if __name__ == "__main__":
    unittest.main()
