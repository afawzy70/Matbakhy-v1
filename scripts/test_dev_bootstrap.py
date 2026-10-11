"""MB-008: the local bootstrap fails clearly when prerequisites or a local marker are missing."""
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEV = str(ROOT / "scripts/dev.sh")


class DevBootstrapGuards(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.bin = Path(self.temp.name) / "bin"
        self.bin.mkdir()
        # Only basic shell utilities: docker, java, node, pnpm, curl and openssl are absent.
        for tool in ("bash", "cat", "grep", "sed", "dirname", "mktemp", "rm", "env"):
            path = shutil.which(tool)
            if path:
                (self.bin / tool).symlink_to(path)

    def tearDown(self):
        self.temp.cleanup()

    def run_dev(self, *args, env_file=None, path=None):
        env = {"PATH": path or str(self.bin), "HOME": self.temp.name}
        if env_file:
            env["MATBAKHY_ENV_FILE"] = env_file
        return subprocess.run(["bash", DEV, *args], env=env, text=True, capture_output=True, timeout=60,
                              executable=shutil.which("bash"))

    def test_doctor_names_each_missing_prerequisite_and_starts_nothing(self):
        result = self.run_dev("doctor")
        self.assertNotEqual(result.returncode, 0)
        for name in ("docker CLI", "docker daemon", "docker compose v2", "JDK 21", "pnpm (corepack)", "curl", "openssl"):
            self.assertIn("MISSING: " + name, result.stderr)
        self.assertIn("nothing was started", result.stderr)

    def test_up_runs_doctor_first(self):
        result = self.run_dev("up")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("prerequisites missing", result.stderr)

    def test_env_file_without_local_marker_is_refused(self):
        env_file = Path(self.temp.name) / "staging.env"
        env_file.write_text("MATBAKHY_ENV_MARKER=staging\nCOMPOSE_PROJECT_NAME=x\n")
        result = self.run_dev("reset", env_file=str(env_file), path=os.environ["PATH"])
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("is not marked MATBAKHY_ENV_MARKER=local; refusing", result.stderr)

    def test_smoke_fails_clearly_when_stack_is_not_running(self):
        env_file = Path(self.temp.name) / "local.env"
        text = (ROOT / "infra/.env.example").read_text().replace("API_PORT=8080", "API_PORT=1")
        env_file.write_text(text)
        result = self.run_dev("smoke", env_file=str(env_file), path=os.environ["PATH"])
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("ERROR: API health is not UP", result.stderr)

    def test_unknown_command_prints_usage(self):
        result = self.run_dev("bogus", path=os.environ["PATH"])
        self.assertEqual(result.returncode, 2)
        self.assertIn("scripts/dev.sh doctor", result.stdout)


if __name__ == "__main__":
    unittest.main()
