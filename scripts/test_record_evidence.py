"""record_evidence must judge the working tree from git's porcelain output exactly as printed."""
import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location("record_evidence", pathlib.Path(__file__).with_name("record_evidence.py"))
record_evidence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(record_evidence)


class DirtyOutsideRunsTest(unittest.TestCase):
    def test_a_modified_tracked_run_file_on_the_first_line_is_allowed(self):
        # " M <path>": the leading space is part of the status; stripping the whole output used to cut the path.
        porcelain = " M verification/runs/20261012-MB-032/IOS-NATIVE.log\n?? verification/runs/20261012-MB-032/spec.json\n"
        self.assertEqual(record_evidence.dirty_outside_runs(porcelain), [])

    def test_changes_outside_the_run_directory_are_reported(self):
        porcelain = " M verification/runs/x/a.log\n M backend/app/src/Main.java\n?? notes.txt\n"
        self.assertEqual(record_evidence.dirty_outside_runs(porcelain), ["backend/app/src/Main.java", "notes.txt"])


if __name__ == "__main__":
    unittest.main()
