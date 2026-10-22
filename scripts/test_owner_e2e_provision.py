"""Guards for the owner E2E provisioning on the shared preview: finalize keeps passwords only for added users and refuses id mismatches;
the script refuses non-preview stacks, takes the shared lock, takes the Keycloak dump and never traces."""
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import owner_e2e_fixtures as fx  # noqa: E402

SCRIPT = (ROOT / "deploy" / "web-preview" / "owner-e2e-provision.sh").read_text()


class FinalizeTest(unittest.TestCase):
    def run_finalize(self, added, existing_ids):
        d = pathlib.Path(tempfile.mkdtemp())
        users = fx.to_provision()
        creds = {u["username"]: {"actor": u["key"], "username": u["username"], "password": "x" * 24} for u in users}
        (d / "credentials.private.json").write_text(json.dumps(creds))
        result = {"results": [{"action": "ADDED", "resourceType": "USER", "resourceName": n} for n in added]}
        (d / "r.json").write_text(json.dumps(result))
        (d / "u.json").write_text(json.dumps([{"username": u["username"], "id": existing_ids.get(u["username"], u["kc"])} for u in users]))
        rc = fx.finalize(d, d / "r.json", d / "u.json")
        return rc, json.loads((d / "credentials.private.json").read_text()), oct(os.stat(d / "credentials.private.json").st_mode & 0o777)

    def test_only_added_users_keep_a_password_and_the_file_stays_private(self):
        names = [u["username"] for u in fx.to_provision()]
        rc, creds, mode = self.run_finalize(names[:3], {})
        self.assertEqual((rc, mode), (0, "0o600"))
        self.assertTrue(all(creds[n]["password"] for n in names[:3]))
        self.assertTrue(all(creds[n]["password"] is None for n in names[3:]))

    def test_a_different_keycloak_id_stops_the_run(self):
        names = [u["username"] for u in fx.to_provision()]
        rc, _, _ = self.run_finalize(names, {names[0]: "00000000-0000-4000-8000-00000000dead"})
        self.assertEqual(rc, 1)


class ScriptTest(unittest.TestCase):
    def test_script_properties(self):
        self.assertTrue(SCRIPT.startswith("#!/bin/sh"))
        self.assertIn("set +x", SCRIPT)
        self.assertIn("printenv APP_ENV", SCRIPT)
        self.assertIn("= PREVIEW", SCRIPT)
        self.assertIn(".preview-mutation.lock", SCRIPT)
        self.assertIn("pg_dump", SCRIPT)
        self.assertLess(SCRIPT.index("./deploy.sh backup"), SCRIPT.index("partialImport"))
        self.assertLess(SCRIPT.index("fixtures.py finalize") if "fixtures.py finalize" in SCRIPT else SCRIPT.index("owner_e2e_fixtures.py finalize"), SCRIPT.index("psql_app < seed-owner-e2e.sql"))
        self.assertNotIn("| head", SCRIPT, "a pipe would hide the import's exit status")
        self.assertNotIn("security.mfa_policy_update", SCRIPT)
        for sql in ("seed-owner-e2e.sql", "seed-owner-e2e-mfa.sql", "refresh-slots.sql"):
            self.assertNotRegex((ROOT / "deploy" / "web-preview" / sql).read_text().upper(), r"\bDELETE\s+FROM\b|\bTRUNCATE\b|\bDROP\b", sql)
        self.assertEqual(subprocess.run(["sh", "-n", str(ROOT / "deploy/web-preview/owner-e2e-provision.sh")]).returncode, 0)


if __name__ == "__main__":
    unittest.main()
