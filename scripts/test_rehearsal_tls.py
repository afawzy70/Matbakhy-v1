"""SEC-003: the rehearsal load helper verifies TLS unless the local-only opt-in is set for a *.localhost domain."""
import os
import subprocess
import sys
import unittest

HELPER = os.path.join(os.path.dirname(__file__), "rehearsal", "load.py")


def run(domain, env_extra):
    env = {**os.environ, **env_extra}
    return subprocess.run([sys.executable, "-I", HELPER, domain, "0", "1"], capture_output=True, text=True, env=env, timeout=60)


class RehearsalTlsTest(unittest.TestCase):
    def test_source_has_no_unconditional_verification_override(self):
        text = open(HELPER).read()
        head = text.split("REHEARSAL_INSECURE_LOCAL_TLS")[0]
        self.assertNotIn("CERT_NONE", head)
        self.assertNotIn("check_hostname = False", head)

    def test_insecure_opt_in_is_refused_for_a_public_domain(self):
        result = run("example.test", {"REHEARSAL_INSECURE_LOCAL_TLS": "1"})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("only allowed for *.localhost", result.stderr + result.stdout)

    def test_default_run_with_no_duration_does_not_disable_verification(self):
        result = run("mb.localhost", {})
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
