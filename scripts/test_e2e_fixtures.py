"""Guardrails for the staging-only E2E setup channel (owner decision 2026-10-14).

The browser suite must work without any database access from the test runner, and without unsafe test endpoints:
- the suite sends fixture NAMES and parameters, never SQL (no raw sql() helper and no E2E_PSQL left in the specs);
- every fixture used by a spec exists, takes exactly the parameters the spec passes, and uses each as a psql variable;
- fixtures are plain SELECT / INSERT / UPDATE statements: no DDL, no DELETE or TRUNCATE, no COPY, no psql meta-commands;
- the server-side script refuses without its marker file, validates names and parameters, and hands parameters to psql as
  -v variables (a value is never executed or spliced into the SQL). A stub psql records what it receives.
"""
import os
import pathlib
import re
import shutil
import stat
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
PREVIEW = ROOT / "deploy" / "web-preview"
FIXTURES = PREVIEW / "e2e-fixtures"
SPECS = ROOT / "apps" / "operations-web" / "e2e"
FORBIDDEN = re.compile(r"\b(DROP|TRUNCATE|DELETE|ALTER|CREATE|GRANT|REVOKE|COPY|VACUUM|CALL|EXECUTE|DBLINK|SET_CONFIG)\b|\bpg_\w*file\w*|\\\\", re.I)


def strip_sql(text):
    """The statements without comments and string literals (so words inside synthetic text do not count)."""
    text = re.sub(r"--[^\n]*", "", text)
    return re.sub(r"'(?:[^']|'')*'", "''", text)


class FixtureFilesTest(unittest.TestCase):
    def files(self):
        found = sorted(FIXTURES.glob("*.sql"))
        self.assertGreater(len(found), 10)
        return found

    def test_every_fixture_declares_its_parameters_and_uses_each_as_a_psql_variable(self):
        for f in self.files():
            text = f.read_text(encoding="utf-8")
            header = text.splitlines()[0]
            self.assertTrue(header.startswith("-- params:"), f.name)
            declared = header[len("-- params:"):].split()
            used = set(re.findall(r":'([a-z0-9_]+)'", text))
            self.assertEqual(set(declared), used, f"{f.name}: declared {declared}, used {sorted(used)}")
            self.assertRegex(f.stem, r"^[a-z][a-z0-9_]*$")

    def test_fixtures_are_plain_select_insert_and_update_statements(self):
        for f in self.files():
            body = strip_sql(f.read_text(encoding="utf-8"))
            self.assertIsNone(FORBIDDEN.search(body), f"{f.name}: {FORBIDDEN.search(body)}")
            for statement in [s.strip() for s in body.split(";") if s.strip()]:
                self.assertRegex(statement, r"^(SELECT|INSERT|UPDATE|WITH)\b", f"{f.name}: {statement[:40]}")

    def test_the_specs_send_names_and_parameters_and_never_sql(self):
        names = {f.stem: set(f.read_text(encoding="utf-8").splitlines()[0][len("-- params:"):].split()) for f in self.files()}
        calls = 0
        for spec in sorted(SPECS.glob("*.ts")):
            text = spec.read_text(encoding="utf-8")
            self.assertIsNone(re.search(r"\bsql\(", text), f"{spec.name} still sends SQL")
            self.assertNotIn("E2E_PSQL", text, spec.name)
            for m in re.finditer(r"\bfixture\(\s*\"([a-z0-9_]+)\"\s*(?:,\s*\{([^}]*)\})?\s*\)", text):
                calls += 1
                name, params = m.group(1), m.group(2) or ""
                self.assertIn(name, names, f"{spec.name}: unknown fixture {name}")
                passed = {m.group(1) for part in params.split(",") if (m := re.match(r"\s*([a-z0-9_]+)\s*(?::|$)", part))}
                self.assertEqual(passed, names[name], f"{spec.name}: {name} takes {sorted(names[name])}, got {sorted(passed)}")
        self.assertGreater(calls, 20)
        unused = [n for n in names if not any(re.search(rf'"{n}"', s.read_text(encoding="utf-8")) for s in SPECS.glob("*.ts"))]
        self.assertEqual(unused, [], "fixtures no spec uses")


STUB_PSQL = """#!/bin/sh
# Records what psql would receive: one argument per line, then the SQL read from stdin.
printf 'ARG %s\\n' "$@" > "$STUB_OUT"
printf 'SQL-BEGIN\\n' >> "$STUB_OUT"
cat >> "$STUB_OUT"
echo stub-result
"""


class FixtureScriptTest(unittest.TestCase):
    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp(prefix="e2e-fixture-test-"))
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
        (self.tmp / "e2e-fixtures").mkdir()
        shutil.copy(FIXTURES / "customer_phone_verified.sql", self.tmp / "e2e-fixtures")
        shutil.copy(FIXTURES / "kitchen_operating_state.sql", self.tmp / "e2e-fixtures")
        shutil.copy(PREVIEW / "e2e-fixture.sh", self.tmp / "e2e-fixture.sh")
        self.stub = self.tmp / "psql-stub"
        self.stub.write_text(STUB_PSQL)
        self.stub.chmod(self.stub.stat().st_mode | stat.S_IXUSR)
        self.out = self.tmp / "psql-received.txt"
        self.marker = self.tmp / ".e2e-enabled"

    def run_script(self, request, enabled=True):
        if enabled:
            self.marker.write_text("")
        env = dict(os.environ, E2E_PSQL_PREFIX=str(self.stub), STUB_OUT=str(self.out), E2E_ENABLED_MARKER=str(self.marker))
        return subprocess.run(["sh", str(self.tmp / "e2e-fixture.sh")], input=request, env=env, capture_output=True, text=True)

    def received(self):
        return self.out.read_text(encoding="utf-8") if self.out.exists() else ""

    def test_it_refuses_unless_the_host_has_enabled_it(self):
        r = self.run_script("kitchen_operating_state\n", enabled=False)
        self.assertEqual(r.returncode, 3)
        self.assertEqual(self.received(), "")

    def test_a_valid_request_reaches_psql_as_variables_and_the_checked_in_sql(self):
        r = self.run_script("customer_phone_verified\nsubject=00000000-0000-4000-8000-0000000000b1\nphone=+201005556667\n")
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertEqual(r.stdout.strip(), "stub-result")
        got = self.received()
        for expected in ("ARG -X", "ARG -1", "ARG ON_ERROR_STOP=1", "ARG subject=00000000-0000-4000-8000-0000000000b1", "ARG phone=+201005556667"):
            self.assertIn(expected, got)
        self.assertIn("UPDATE app_user SET phone_e164 = :'phone'", got.split("SQL-BEGIN", 1)[1])

    def test_hostile_values_are_passed_as_data_and_never_executed_or_expanded(self):
        evil = "x'; DROP TABLE app_user; --"
        for value in (evil, "$(touch " + str(self.tmp / "pwned") + ")", "`touch " + str(self.tmp / "pwned") + "`", "*", "a b  c", "كشري تجريبي", "=="):
            r = self.run_script(f"customer_phone_verified\nsubject={value}\nphone=+201000000001\n")
            self.assertEqual(r.returncode, 0, f"{value!r}: {r.stderr}")
            self.assertIn(f"ARG subject={value}", self.received(), value)
            self.assertNotIn("DROP TABLE", self.received().split("SQL-BEGIN", 1)[1])
        self.assertFalse((self.tmp / "pwned").exists())

    def test_it_refuses_bad_names_unknown_fixtures_and_wrong_parameters(self):
        good = "subject=a\nphone=b\n"
        cases = {
            "path in the name": f"../etc/passwd\n{good}",
            "upper case": f"Customer_Phone_Verified\n{good}",
            "unknown fixture": f"drop_everything\n{good}",
            "empty": "\n",
            "missing parameter": "customer_phone_verified\nsubject=a\n",
            "extra parameter": f"customer_phone_verified\n{good}sql=select 1\n",
            "duplicate parameter": f"customer_phone_verified\n{good}phone=c\n",
            "bad parameter name": "customer_phone_verified\nSubject=a\nphone=b\n",
            "parameter on a fixture without any": "kitchen_operating_state\nx=1\n",
            "control character": "customer_phone_verified\nsubject=a\x07b\nphone=b\n",
            "too long": "customer_phone_verified\nsubject=" + "a" * 513 + "\nphone=b\n",
        }
        for label, request in cases.items():
            if self.out.exists():
                self.out.unlink()
            r = self.run_script(request)
            self.assertNotEqual(r.returncode, 0, label)
            self.assertEqual(self.received(), "", f"{label}: psql must not have been called")


if __name__ == "__main__":
    unittest.main()
