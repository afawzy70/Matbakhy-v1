"""Guardrails for the Hetzner staging preparation (owner direction 2026-10-14; nothing is deployed by these tests).

- The 2 vCPU / 4 GB profile keeps the sum of container memory ceilings below 3.5 GiB, so the OS, page cache and a
  backup fit beside them. Ceilings are not measured use; MB-038 and the staging run give the real numbers.
- Retention defaults are PROPOSALS (logs 30 days, debug logs 7 days, QA artifacts 90 days), not approved policies (O06).
- Push delivery (O07) is off by default in the deployment.
- The QA artifact prune is a dry run unless told to apply, only touches old regular files in its own directory.
Docker is used only for `docker compose config` (no container runs).
"""
import json
import os
import pathlib
import shutil
import subprocess
import tempfile
import time
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
PREVIEW = ROOT / "deploy" / "web-preview"
GIB = 1024 ** 3


def compose_config(*files, profiles=()):
    cmd = ["docker", "compose", "--env-file", str(PREVIEW / ".env.example")]
    for f in files:
        cmd += ["-f", str(PREVIEW / f)]
    for p in profiles:
        cmd += ["--profile", p]
    cmd += ["config", "--format", "json"]
    return json.loads(subprocess.run(cmd, check=True, capture_output=True, text=True, cwd=PREVIEW).stdout)


class StagingProfileTest(unittest.TestCase):
    def test_four_gb_profile_keeps_memory_ceilings_below_three_and_a_half_gib(self):
        cfg = compose_config("compose.yaml", "compose.4gb.yaml")
        services = cfg["services"]
        missing = [n for n, s in services.items() if not s.get("mem_limit")]
        self.assertEqual(missing, [], "every service needs a memory ceiling")
        total = sum(int(s["mem_limit"]) for s in services.values())
        self.assertLess(total, 3.5 * GIB, f"ceilings add up to {total / GIB:.2f} GiB")

    def test_grafana_is_an_optional_tool_in_the_four_gb_profile_and_present_when_asked(self):
        default = compose_config("compose.yaml", "compose.4gb.yaml")["services"]
        self.assertNotIn("grafana", default)
        with_tools = compose_config("compose.yaml", "compose.4gb.yaml", profiles=("tools",))["services"]
        self.assertIn("grafana", with_tools)
        self.assertLess(sum(int(s["mem_limit"]) for s in with_tools.values()), 3.75 * GIB)

    def test_the_default_profile_still_includes_everything_unchanged(self):
        cfg = compose_config("compose.yaml")["services"]
        self.assertIn("grafana", cfg)
        self.assertEqual(int(cfg["api"]["mem_limit"]), 2 * GIB)

    def test_the_jvm_heap_fits_inside_each_memory_ceiling_of_the_four_gb_profile(self):
        # A flag on the java command line beats JAVA_TOOL_OPTIONS, so the image must not hard-code one (found in the local rehearsal:
        # 75% of a 1 GiB ceiling left too little room for metaspace, threads and buffers).
        dockerfile = (ROOT / "backend" / "Dockerfile").read_text()
        entrypoint = next(line for line in dockerfile.splitlines() if line.startswith("ENTRYPOINT"))
        self.assertNotIn("MaxRAMPercentage", entrypoint)
        self.assertIn('ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"', dockerfile)
        services = compose_config("compose.yaml", "compose.4gb.yaml")["services"]
        for name, key, limit in (("api", "JAVA_TOOL_OPTIONS", 60), ("identity", "JAVA_OPTS_KC_HEAP", 70)):
            value = services[name]["environment"][key]
            percent = int(value.split("MaxRAMPercentage=")[1].split()[0])
            self.assertLessEqual(percent, limit, f"{name}: {value}")

    def test_the_identity_container_is_healthy_only_once_the_realm_exists(self):
        # Found in the local rehearsal: Keycloak's management readiness is UP about 1.5 minutes before --import-realm has finished, so the
        # first sign-in (and the first smoke) raced the import. The API waits for this health check.
        for files in (("compose.yaml",), ("compose.yaml", "compose.4gb.yaml")):
            test = " ".join(compose_config(*files)["services"]["identity"]["healthcheck"]["test"])
            self.assertIn("/realms/matbakhy/.well-known/openid-configuration", test)
            self.assertIn("issuer", test)

    def test_the_api_sets_the_csrf_cookie_for_the_parent_domain_so_the_sibling_web_apps_can_read_it(self):
        for files in (("compose.yaml",), ("compose.yaml", "compose.4gb.yaml")):
            env = compose_config(*files)["services"]["api"]["environment"]
            self.assertEqual(env.get("WEB_COOKIE_DOMAIN"), "preview.example.invalid")  # the PREVIEW_DOMAIN of .env.example

    def test_push_delivery_is_off_by_default_in_the_deployment(self):
        for files in (("compose.yaml",), ("compose.yaml", "compose.4gb.yaml")):
            env = compose_config(*files)["services"]["api"]["environment"]
            self.assertEqual(env.get("PUSH_DELIVERY_ENABLED"), "false")

    def test_retention_defaults_are_the_proposed_staging_values(self):
        loki = (PREVIEW / "observability" / "loki.yaml").read_text()
        self.assertIn("${LOG_RETENTION:-720h}", loki)
        self.assertIn("${DEBUG_LOG_RETENTION:-168h}", loki)
        self.assertIn('{level="DEBUG"}', loki)
        env = (PREVIEW / ".env.example").read_text()
        self.assertIn("LOG_RETENTION=720h", env)
        self.assertIn("DEBUG_LOG_RETENTION=168h", env)
        self.assertIn("QA_ARTIFACT_RETENTION_DAYS=90", env)
        loki_env = compose_config("compose.yaml")["services"]["loki"]["environment"]
        self.assertEqual(loki_env["LOG_RETENTION"], "720h")
        self.assertEqual(loki_env["DEBUG_LOG_RETENTION"], "168h")


class QaArtifactPruneTest(unittest.TestCase):
    def run_prune(self, directory, *args, days="90"):
        env = dict(os.environ, QA_ARTIFACT_DIR=str(directory), QA_ARTIFACT_RETENTION_DAYS=days)
        return subprocess.run(["sh", str(PREVIEW / "prune-qa-artifacts.sh"), *args], env=env, capture_output=True, text=True)

    def make_tree(self):
        tmp = pathlib.Path(tempfile.mkdtemp(prefix="qa-artifacts-"))
        self.addCleanup(shutil.rmtree, tmp, ignore_errors=True)
        old, new = tmp / "run-old" / "shot.png", tmp / "run-new" / "shot.png"
        for f in (old, new):
            f.parent.mkdir(parents=True)
            f.write_text("synthetic")
        aged = time.time() - 120 * 86400
        os.utime(old, (aged, aged))
        return tmp, old, new

    def test_dry_run_lists_old_files_and_deletes_nothing(self):
        tmp, old, new = self.make_tree()
        result = self.run_prune(tmp)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("run-old/shot.png", result.stdout)
        self.assertNotIn("run-new/shot.png", result.stdout)
        self.assertTrue(old.exists() and new.exists())

    def test_apply_removes_only_files_older_than_the_retention(self):
        tmp, old, new = self.make_tree()
        result = self.run_prune(tmp, "--apply")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(old.exists())
        self.assertTrue(new.exists())

    def test_it_refuses_an_unset_or_dangerous_directory_and_a_bad_period(self):
        self.assertNotEqual(self.run_prune("").returncode, 0)
        self.assertNotEqual(self.run_prune("/").returncode, 0)
        self.assertNotEqual(self.run_prune("/srv").returncode, 0)
        tmp, old, _ = self.make_tree()
        self.assertNotEqual(self.run_prune(tmp, "--apply", days="0").returncode, 0)
        self.assertNotEqual(self.run_prune(tmp, "--apply", days="abc").returncode, 0)
        self.assertTrue(old.exists())


if __name__ == "__main__":
    unittest.main()
