"""Rollback-compatibility policy for migrations after V28 (scripts/check_migration_compat.py)."""
import pathlib
import shutil
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import check_migration_compat as policy  # noqa: E402


class MigrationCompatTest(unittest.TestCase):
    def setUp(self):
        self.dir = pathlib.Path(tempfile.mkdtemp(prefix="migrations-"))
        self.addCleanup(shutil.rmtree, self.dir, ignore_errors=True)

    def migration(self, version, sql):
        (self.dir / f"V{version}__test.sql").write_text(sql, encoding="utf-8")
        return policy.classify(self.dir / f"V{version}__test.sql")

    def test_the_real_migrations_after_the_baseline_pass(self):
        self.assertEqual(policy.main([]), 0)

    def test_additive_changes_are_expand_only(self):
        verdict, problems = self.migration(29, "ALTER TABLE support_case ADD COLUMN note text;\nCREATE INDEX x ON support_case (state);\n"
                                           "ALTER TABLE support_case ADD COLUMN flag boolean NOT NULL DEFAULT false;")
        self.assertEqual((verdict, problems), ("expand-only", []))

    def test_constraints_on_a_table_the_migration_creates_do_not_count(self):
        verdict, problems = self.migration(29, "CREATE TABLE fresh (id uuid PRIMARY KEY, n int);\nCREATE UNIQUE INDEX fresh_n ON fresh (n);\n"
                                           "ALTER TABLE fresh ADD CONSTRAINT n_ck CHECK (n > 0);")
        self.assertEqual((verdict, problems), ("expand-only", []))

    def test_breaking_statements_need_an_annotation_with_a_reason(self):
        for sql in ("ALTER TABLE t DROP COLUMN c;", "DROP TABLE t;", "ALTER TABLE t RENAME COLUMN a TO b;", "ALTER TABLE t ALTER COLUMN a TYPE bigint;",
                    "ALTER TABLE t ADD COLUMN c text NOT NULL;", "DELETE FROM t WHERE x = 1;", "TRUNCATE t;"):
            verdict, problems = self.migration(29, sql)
            self.assertEqual(verdict, "breaking", sql)
            self.assertEqual(len(problems), 1, sql)
            verdict, problems = self.migration(29, "-- rollback-compat: breaking the old API reads column c; restore the backup to roll back\n" + sql)
            self.assertEqual((verdict, problems), ("breaking", []), sql)
            verdict, problems = self.migration(29, "-- rollback-compat: breaking short\n" + sql)
            self.assertEqual(len(problems), 1, "a reason shorter than ten characters is not a reason")

    def test_tightening_on_an_existing_table_needs_an_annotation(self):
        for sql in ("ALTER TABLE t ALTER COLUMN a SET NOT NULL;", "CREATE UNIQUE INDEX u ON t (a);", "ALTER TABLE t ADD CONSTRAINT c CHECK (a > 0);"):
            verdict, problems = self.migration(29, sql)
            self.assertEqual((verdict, len(problems)), ("tightening", 1), sql)
            _, problems = self.migration(29, "-- rollback-compat: tightening only new rows of the new kind are affected\n" + sql)
            self.assertEqual(problems, [], sql)

    def test_a_trigger_that_mentions_truncate_is_not_a_truncate(self):
        verdict, problems = self.migration(29, "CREATE TRIGGER no_t BEFORE UPDATE OR DELETE OR TRUNCATE ON audit FOR EACH STATEMENT EXECUTE FUNCTION refuse();")
        self.assertEqual((verdict, problems), ("expand-only", []))

    def test_expand_only_cannot_be_claimed_for_a_risky_migration(self):
        _, problems = self.migration(29, "-- rollback-compat: expand-only\nDROP TABLE t;")
        self.assertTrue(any("expand-only" in p for p in problems))

    def test_main_checks_only_after_the_baseline_and_fails_on_a_violation(self):
        self.migration(28, "DROP TABLE old_one;")
        self.assertEqual(policy.main(["--dir", str(self.dir)]), 0)
        self.migration(29, "DROP TABLE t;")
        self.assertEqual(policy.main(["--dir", str(self.dir)]), 1)
        self.assertEqual(policy.main(["--dir", str(self.dir), "--baseline", "30"]), 0)


if __name__ == "__main__":
    unittest.main()
