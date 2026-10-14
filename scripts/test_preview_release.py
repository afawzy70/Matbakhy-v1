"""Release log, rollback plan and the E2E reset guard of the preview operator script (owner direction 2026-10-14).

Docker is replaced by a stub that records every command and answers the few queries deploy.sh makes (the schema version
from a sequence file, the backup listing, container health); no container runs and no server is touched.
- `up` refuses images that are not pinned by digest, pulls only missing images, backs up first and records one line with the
  schema before and after and the backup;
- `rollback-plan` only prints: it says when an image-only rollback is possible (schema unchanged) and when a restore is
  the safe path (schema moved), and never runs a changing command;
- `e2e-reset` refuses without the marker, with public customer hosts or without the typed confirmation, and takes a
  verified backup before it drops anything.
"""
import os
import pathlib
import shutil
import stat
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
DIGEST = "@sha256:" + "a" * 64
STUB = """#!/bin/sh
echo "$*" >> "$STUB_LOG"
case "$*" in
  *"inspect -f"*) echo healthy ;;
  *" ps -q "*) echo container-id ;;
  *"flyway_schema_history"*) head -n 1 "$STUB_SCHEMA_SEQ"; sed -i 1d "$STUB_SCHEMA_SEQ"; [ -s "$STUB_SCHEMA_SEQ" ] || echo 0 > "$STUB_SCHEMA_SEQ" ;;
  *"ls /backups/"*) echo "/backups/db-X/matbakhy-X.dump" ;;
  *" sh -s "*|*"weed shell"*) cat > /dev/null ;;
esac
exit 0
"""


class PreviewReleaseTest(unittest.TestCase):
    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp(prefix="preview-release-"))
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
        self.preview = self.tmp / "deploy" / "web-preview"
        shutil.copytree(ROOT / "deploy" / "web-preview", self.preview)
        (self.tmp / "scripts").mkdir()
        for name in ("db-backup.sh", "db-restore.sh"):
            shutil.copy(ROOT / "scripts" / name, self.tmp / "scripts" / name)
        (self.tmp / "infra" / "db").mkdir(parents=True)
        shutil.copy(ROOT / "infra" / "db" / "reconcile.sql", self.tmp / "infra" / "db" / "reconcile.sql")
        self.backups = self.tmp / "backups"
        self.write_env()
        bin_dir = self.tmp / "bin"
        bin_dir.mkdir()
        stub = bin_dir / "docker"
        stub.write_text(STUB)
        stub.chmod(stub.stat().st_mode | stat.S_IXUSR)
        self.log = self.tmp / "docker.log"
        self.seq = self.tmp / "schema.seq"
        self.env = dict(os.environ, PATH=f"{bin_dir}:{os.environ['PATH']}", STUB_LOG=str(self.log), STUB_SCHEMA_SEQ=str(self.seq))
        self.env.pop("E2E_RESET_CONFIRM", None)

    def write_env(self, **over):
        values = {
            "RELEASE_COMMIT": "1" * 40, "API_IMAGE": f"registry.test/api{DIGEST}", "FRONT_IMAGE": f"registry.test/front{DIGEST}",
            "PREVIEW_DOMAIN": "preview.example.test", "ACME_EMAIL": "ops@example.test", "ALLOWED_CLIENT_IPS": "203.0.113.10/32",
            "BACKUP_DIR": str(self.backups), "GENERATED_DIR": str(self.tmp / "generated"),
        }
        values.update(over)
        lines = []
        for raw in (self.preview / ".env.example").read_text().splitlines():
            key = raw.split("=", 1)[0]
            if key in values:
                lines.append(f'{key}="{values.pop(key)}"')
            elif "=" in raw and not raw.startswith("#"):
                lines.append(raw.replace("REPLACE_ME", "x"))
        lines += [f'{k}="{v}"' for k, v in values.items()]
        (self.preview / ".env").write_text("\n".join(lines) + "\n")

    def deploy(self, *args, **env):
        return subprocess.run(["sh", "deploy.sh", *args], cwd=self.preview, env=dict(self.env, **env), stdin=subprocess.DEVNULL, capture_output=True, text=True)

    def calls(self):
        return self.log.read_text().splitlines() if self.log.exists() else []

    def index(self, needle, start=0):
        return next(i for i, c in enumerate(self.calls()) if i >= start and needle in c)

    # ---- up and the release log ----

    def test_up_refuses_an_image_that_is_not_pinned_by_digest(self):
        self.write_env(API_IMAGE="registry.test/api:latest")
        r = self.deploy("up")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("pinned by digest", r.stderr)
        self.assertFalse(any(" up -d" in c for c in self.calls()))

    def test_up_backs_up_first_pulls_only_missing_images_and_records_the_release(self):
        self.seq.write_text("27\n28\n28\n")
        r = self.deploy("up")
        self.assertEqual(r.returncode, 0, r.stderr)
        backup = self.index("PGDATABASE=matbakhy")
        pull = self.index("pull --policy missing")
        up = self.index("up -d")
        self.assertLess(backup, pull)
        self.assertLess(pull, up)
        line = (self.backups / "releases.log").read_text().strip().split("\t")
        self.assertEqual(len(line), 7)
        self.assertEqual((line[1], line[4], line[5]), ("1" * 40, "27", "28"))
        self.assertTrue(line[6].startswith("/backups/db-"))
        self.assertIn(f"registry.test/api{DIGEST}", line[2])

    # ---- rollback plan ----

    def log_two_releases(self, schema_first, schema_second):
        self.backups.mkdir(exist_ok=True)
        rows = [("2026-10-14T08:00:00Z", "a" * 40, schema_first - 1, schema_first, "/backups/db-first"),
                ("2026-10-14T09:00:00Z", "b" * 40, schema_first, schema_second, "/backups/db-second")]
        (self.backups / "releases.log").write_text("".join(
            f"{t}\t{c}\tregistry.test/api@sha256:{c[0] * 64}\tregistry.test/front@sha256:{c[0] * 64}\t{b}\t{a}\t{p}\n" for t, c, b, a, p in rows))

    def test_rollback_plan_says_image_only_when_the_schema_has_not_moved(self):
        self.log_two_releases(28, 28)
        self.seq.write_text("28\n")
        r = self.deploy("rollback-plan")
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertIn("Image-only rollback", r.stdout)
        self.assertIn("a" * 40, r.stdout)
        self.assertIn(f"API_IMAGE=registry.test/api@sha256:{'a' * 64}", r.stdout)
        self.assertFalse(any(w in c for c in self.calls() for w in (" up", " stop", "DROP", "restore")), self.calls())

    def test_rollback_plan_points_to_the_backup_when_the_schema_moved(self):
        self.log_two_releases(28, 30)
        self.seq.write_text("30\n")
        r = self.deploy("rollback-plan")
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertIn("schema moved from 28 to 30", r.stdout)
        self.assertIn("V29 to V30", r.stdout)
        self.assertIn("expand-only", r.stdout)
        self.assertIn("/backups/db-second", r.stdout)
        self.assertIn("LOST", r.stdout)
        self.assertFalse(any(w in c for c in self.calls() for w in (" up", " stop", "DROP")), self.calls())

    def test_rollback_plan_refuses_without_history(self):
        self.assertNotEqual(self.deploy("rollback-plan").returncode, 0)
        self.log_two_releases(28, 28)
        self.assertNotEqual(self.deploy("rollback-plan", "2").returncode, 0)
        self.assertNotEqual(self.deploy("rollback-plan", "0").returncode, 0)

    # ---- e2e reset ----

    def test_e2e_reset_is_guarded(self):
        marker = self.preview / ".e2e-enabled"
        r = self.deploy("e2e-reset", E2E_RESET_CONFIRM="preview.example.test")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("not enabled", r.stderr)
        marker.write_text("")
        self.write_env(CUSTOMER_ALLOWED_IPS="0.0.0.0/0 ::/0")
        r = self.deploy("e2e-reset", E2E_RESET_CONFIRM="preview.example.test")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("customer hosts are public", r.stderr)
        self.write_env()
        r = self.deploy("e2e-reset")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("E2E_RESET_CONFIRM", r.stderr)
        r = self.deploy("e2e-reset", E2E_RESET_CONFIRM="another.example.test")
        self.assertNotEqual(r.returncode, 0)
        self.assertFalse(any("DROP DATABASE" in c or " stop" in c for c in self.calls()), "nothing may run when a guard refuses")

    def test_e2e_reset_takes_a_verified_backup_before_it_drops_the_database(self):
        (self.preview / ".e2e-enabled").write_text("")
        self.seq.write_text("28\n")
        r = self.deploy("e2e-reset", E2E_RESET_CONFIRM="preview.example.test")
        self.assertEqual(r.returncode, 0, r.stderr)
        dump = self.index("PGDATABASE=matbakhy")
        restore_check = self.index("RESTORE_CONFIRM_TARGET=matbakhy_restore_check")
        stop = self.index(" stop api")
        drop = self.index("DROP DATABASE IF EXISTS matbakhy WITH (FORCE)")
        start = self.index("up -d api", drop)
        self.assertTrue(dump < restore_check < stop < drop < start, self.calls())


if __name__ == "__main__":
    unittest.main()
