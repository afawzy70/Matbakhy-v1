#!/usr/bin/env python3
"""Regression tests for the DOC-001 gate (scripts/doc001_check.py).

Every test builds a throw-away git repository, so the gate is exercised through its real command line
against real diffs. The gate must fail on bad input as well as pass on good input.
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GATE = ROOT / "scripts/doc001_check.py"
HB = "docs/developer-handbook"

PAGE = "# {t}\n\n> **Owner role:** Backend architecture\n> **Status:** REFERENCE — fixture\n\n{body}\n"
MATRIX = {
    "schema_version": 1,
    "areas": [
        {"id": "backend-orders", "code": ["backend/modules/orders/**"],
         "docs": [f"{HB}/04-codebase/order-implementation.md"], "owner_role": "Backend architecture"},
        {"id": "customer-web", "code": ["apps/customer-web/**"],
         "docs": [f"{HB}/02-user-journeys/customer-web.md"], "owner_role": "Frontend and mobile"},
    ],
}
GOOD_JUSTIFICATION = (
    "The change only renames a private local variable inside the order service; the behaviour, state names and "
    f"endpoints described in {HB}/04-codebase/order-implementation.md stay exactly as documented and the existing tests cover it."
)


def decl(**kw: str) -> str:
    rows = "\n".join(f"{k}: {v}" for k, v in kw.items())
    return f"## Documentation Impact\n<!-- DOC-001:BEGIN -->\n{rows}\n<!-- DOC-001:END -->\n"


UPDATED = dict(
    impact="updated",
    summary="Order placement now rejects expired quotes with a distinct error; documented in the order implementation page.",
    handbook_pages=f"{HB}/04-codebase/order-implementation.md",
    consistency="Compared the OrderService change, the OpenAPI error list and the unit test.",
)


class Repo:
    def __init__(self, tmp: Path) -> None:
        self.p = tmp
        self.git("init", "-q", "-b", "develop")
        self.git("config", "user.email", "t@example.invalid")
        self.git("config", "user.name", "t")
        pages = {
            "README.md": PAGE.format(t="Handbook", body="[a](04-codebase/order-implementation.md) [b](02-user-journeys/customer-web.md) "
                                     "[c](05-developer-workflow/documentation-policy.md) [ar](README-ar.md)"),
            "README-ar.md": PAGE.format(t="ar", body="x"),
            "04-codebase/order-implementation.md": PAGE.format(t="Orders", body="Orders live in `backend/modules/orders/Service.java`."),
            "02-user-journeys/customer-web.md": PAGE.format(t="Web", body="Customer web."),
            "05-developer-workflow/documentation-policy.md": PAGE.format(t="Policy", body="DOC-001."),
        }
        for rel, text in pages.items():
            self.write(f"{HB}/{rel}", text)
        self.write(f"{HB}/doc001-matrix.json", json.dumps(MATRIX))
        self.write(".github/pull_request_template.md", "template\n")
        self.write("backend/modules/orders/Service.java", "class Service {}\n")
        self.write("apps/customer-web/src/App.tsx", "export {}\n")
        self.write(".github/workflows/doc001.yml", "name: Documentation Consistency\njobs:\n  doc001:\n    name: Documentation Consistency / DOC-001\n")
        self.write("scripts/doc001_check.py", "# gate\n")
        self.git("add", "-A")
        self.git("commit", "-qm", "base")
        self.base = self.git("rev-parse", "HEAD").strip()
        self.git("checkout", "-q", "-b", "feature")

    def git(self, *a: str) -> str:
        return subprocess.run(["git", *a], cwd=self.p, check=True, capture_output=True, text=True).stdout

    def write(self, rel: str, text: str) -> None:
        f = self.p / rel
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(text, encoding="utf-8")

    def commit(self) -> None:
        self.git("add", "-A")
        self.git("commit", "-qm", "change")

    def run(self, body: str, reviews: list | None = None, author: str = "dev", bootstrap: bool = False):
        bf = self.p.parent / (self.p.name + "-body.md")
        bf.write_text(body, encoding="utf-8")
        cmd = [sys.executable, str(GATE), "pr", "--root", str(self.p), "--base", self.base, "--head", "HEAD",
               "--body-file", str(bf), "--author", author]
        if reviews is not None:
            rf = self.p.parent / (self.p.name + "-reviews.json")
            rf.write_text(json.dumps(reviews))
            cmd += ["--reviews-file", str(rf)]
        if bootstrap:
            cmd.append("--bootstrap")
        r = subprocess.run(cmd, capture_output=True, text=True)
        return r.returncode, r.stdout + r.stderr


class GateTests(unittest.TestCase):
    def setUp(self) -> None:
        self._t = tempfile.TemporaryDirectory()
        self.addCleanup(self._t.cleanup)
        d = Path(self._t.name) / "repo"
        d.mkdir()
        self.r = Repo(d)

    def code_change(self) -> None:
        self.r.write("backend/modules/orders/Service.java", "class Service { int x; }\n")

    def doc_change(self) -> None:
        self.r.write(f"{HB}/04-codebase/order-implementation.md",
                     PAGE.format(t="Orders", body="Orders live in `backend/modules/orders/Service.java`; quotes now expire."))

    def assertFails(self, res, text: str = "") -> None:
        code, out = res
        self.assertEqual(code, 1, out)
        self.assertIn("DOC-001 RESULT: FAIL", out)
        if text:
            self.assertIn(text, out)

    def assertPasses(self, res) -> None:
        code, out = res
        self.assertEqual(code, 0, out)
        self.assertIn("DOC-001 RESULT: PASS", out)

    # 1
    def test_01_code_change_without_assessment_fails(self) -> None:
        self.code_change(); self.r.commit()
        self.assertFails(self.r.run("Fixes a thing.\n"), "declaration is required")

    # 2
    def test_02_code_change_missing_required_documentation_fails(self) -> None:
        self.code_change()
        self.r.write(f"{HB}/02-user-journeys/customer-web.md", PAGE.format(t="Web", body="Edited, but not the order page."))
        self.r.commit()
        body = decl(**{**UPDATED, "handbook_pages": f"{HB}/02-user-journeys/customer-web.md"})
        self.assertFails(self.r.run(body), "area 'backend-orders' changed")

    # 3
    def test_03_valid_documentation_update_passes(self) -> None:
        self.code_change(); self.doc_change(); self.r.commit()
        self.assertPasses(self.r.run(decl(**UPDATED)))

    # 4
    def test_04_specific_neutral_justification_is_accepted_for_review(self) -> None:
        self.code_change(); self.r.commit()
        res = self.r.run(decl(impact="none", justification=GOOD_JUSTIFICATION))
        self.assertPasses(res)
        self.assertIn("REVIEWER MUST EVALUATE", res[1])

    # 5
    def test_05_empty_justification_fails(self) -> None:
        self.code_change(); self.r.commit()
        self.assertFails(self.r.run(decl(impact="none", justification="")), "justification")

    # 6
    def test_06_generic_justification_fails(self) -> None:
        self.code_change(); self.r.commit()
        for text in ("No docs needed. Minor change, internal only, bug fix with no user impact whatsoever here today.",
                     "Bug fix. Internal only. No user impact. Minor change. Refactoring and cleanup, nothing else to add here ok."):
            self.assertFails(self.r.run(decl(impact="none", justification=text)), "justification")

    # 7
    def test_07_broken_markdown_link_fails(self) -> None:
        self.code_change(); self.doc_change()
        self.r.write(f"{HB}/04-codebase/order-implementation.md",
                     PAGE.format(t="Orders", body="See [nowhere](../01-business/missing.md). `backend/modules/orders/Service.java`"))
        self.r.commit()
        self.assertFails(self.r.run(decl(**UPDATED)), "broken relative link")

    # 8
    def test_08_missing_referenced_source_file_fails(self) -> None:
        self.code_change()
        self.r.write(f"{HB}/04-codebase/order-implementation.md",
                     PAGE.format(t="Orders", body="Implemented in `backend/modules/orders/DoesNotExist.java`."))
        self.r.commit()
        self.assertFails(self.r.run(decl(**UPDATED)), "referenced source path does not exist")

    # 9
    def test_09_documentation_only_pr_with_invalid_links_fails(self) -> None:
        self.r.write("docs/notes.md", "# Notes\n[x](./gone.md)\n")
        self.r.commit()
        self.assertFails(self.r.run(decl(impact="updated", summary="Adds notes.", other_docs="docs/notes.md",
                                         consistency="Read the page against the code.")), "broken relative link")

    # 10
    def test_10_malformed_or_tampered_declaration_fails(self) -> None:
        self.code_change(); self.doc_change(); self.r.commit()
        good = decl(**UPDATED)
        self.assertFails(self.r.run(good.replace("<!-- DOC-001:END -->", "")), "declaration is required")
        self.assertFails(self.r.run(good + good), "declaration is required")
        self.assertFails(self.r.run(good.replace("impact: updated", "impact: updated\nbypass: true")), "unknown key")
        self.assertFails(self.r.run(good.replace("impact: updated", "impact: skipped")), "'impact' must be")
        self.assertFails(self.r.run(decl(**{**UPDATED, "handbook_pages": f"{HB}/02-user-journeys/customer-web.md"})),
                         "is not changed in this PR")
        self.assertFails(self.r.run(decl(**{**UPDATED, "handbook_pages": f"{HB}/ghost.md"})), "does not exist")
        self.assertFails(self.r.run(good.replace("impact: updated", "impact: updated\nimpact: none")), "duplicate key")

    # 11
    def test_11_disabling_the_gate_requires_governance_review(self) -> None:
        self.r.write(".github/workflows/doc001.yml", "name: Documentation Consistency\njobs:\n  doc001:\n    if: false\n")
        self.doc_change(); self.r.commit()
        body = decl(**UPDATED)
        self.assertFails(self.r.run(body, reviews=[]), "governance_review")
        gov = decl(**{**UPDATED, "governance_review": "owner approval requested in the PR"})
        self.assertFails(self.r.run(gov, reviews=[]), "APPROVED review")
        self.assertFails(self.r.run(gov, reviews=[{"user": "dev", "state": "APPROVED"}], author="dev"), "APPROVED review")
        self.assertFails(self.r.run(gov, reviews=[{"user": "owner", "state": "APPROVED"}, {"user": "owner", "state": "CHANGES_REQUESTED"}]),
                         "APPROVED review")
        self.assertFails(self.r.run(gov), "no review information")
        head = self.r.git("rev-parse", "HEAD").strip()
        self.assertFails(self.r.run(gov, reviews=[{"user": "owner", "state": "APPROVED"}]), "CURRENT head")
        self.assertPasses(self.r.run(gov, reviews=[{"user": "owner", "state": "APPROVED", "commit_id": head}]))

    # 12
    def test_12_multi_application_change_with_docs_and_traceability_passes(self) -> None:
        self.code_change()
        self.r.write("apps/customer-web/src/App.tsx", "export const quote = 1\n")
        self.doc_change()
        self.r.write(f"{HB}/02-user-journeys/customer-web.md", PAGE.format(t="Web", body="Quote error is shown."))
        self.r.write(f"{HB}/07-reference-trace.md", PAGE.format(t="Trace", body="[orders](04-codebase/order-implementation.md)"))
        self.r.write(f"{HB}/README.md", PAGE.format(t="Handbook", body="[a](04-codebase/order-implementation.md) [b](02-user-journeys/customer-web.md) "
                     "[c](05-developer-workflow/documentation-policy.md) [ar](README-ar.md) [t](07-reference-trace.md)"))
        self.r.commit()
        body = decl(**{**UPDATED,
                       "handbook_pages": f"{HB}/04-codebase/order-implementation.md, {HB}/02-user-journeys/customer-web.md",
                       "traceability": f"{HB}/07-reference-trace.md"})
        self.assertPasses(self.r.run(body))

    # extra negatives that keep the gate honest
    def test_13_impact_none_with_docs_changed_is_contradictory(self) -> None:
        self.code_change(); self.doc_change(); self.r.commit()
        self.assertFails(self.r.run(decl(impact="none", justification=GOOD_JUSTIFICATION)), "contradicts")

    def test_14_orphan_handbook_page_fails(self) -> None:
        self.r.write(f"{HB}/orphan.md", PAGE.format(t="Orphan", body="nobody links here"))
        self.r.commit()
        self.assertFails(self.r.run(decl(impact="updated", summary="Adds a page.", handbook_pages=f"{HB}/orphan.md",
                                         consistency="Compared with the code.")), "orphan")

    def test_15_missing_metadata_fails(self) -> None:
        self.r.write(f"{HB}/04-codebase/order-implementation.md", "# Orders\n\nno metadata\n")
        self.r.commit()
        self.assertFails(self.r.run(decl(impact="updated", summary="Edit.", handbook_pages=f"{HB}/04-codebase/order-implementation.md",
                                         consistency="Compared.")), "Owner role")

    def test_16_unclassified_file_needs_documentation(self) -> None:
        self.r.write("newtop/thing.txt", "x\n"); self.r.commit()
        self.assertFails(self.r.run(decl(impact="updated", summary="Adds a thing.", other_docs=f"{HB}/README.md",
                                         consistency="x")), "is not changed")
        res = self.r.run(decl(impact="none", justification=GOOD_JUSTIFICATION))
        self.assertPasses(res)  # structurally acceptable; the reviewer decides

    def test_17_comment_review_does_not_cancel_approval_and_stale_approval_fails(self) -> None:
        self.r.write("scripts/doc001_check.py", "# changed\n"); self.doc_change(); self.r.commit()
        head = self.r.git("rev-parse", "HEAD").strip()
        gov = decl(**{**UPDATED, "governance_review": "owner approval"})
        approved = {"user": "owner", "state": "APPROVED", "commit_id": head}
        self.assertPasses(self.r.run(gov, reviews=[approved, {"user": "owner", "state": "COMMENTED", "commit_id": head}]))
        self.assertFails(self.r.run(gov, reviews=[{**approved, "commit_id": "0" * 40}]), "CURRENT head")
        self.assertFails(self.r.run(gov, reviews=[approved, {"user": "owner", "state": "DISMISSED", "commit_id": head}]), "CURRENT head")

    def test_18_deleting_required_documentation_does_not_count(self) -> None:
        self.code_change()
        self.r.write("docs/extra.md", "# Extra\nx\n")
        self.r.git("add", "-A"); self.r.git("commit", "-qm", "extra"); self.r.base = self.r.git("rev-parse", "HEAD").strip()
        (self.r.p / f"{HB}/04-codebase/order-implementation.md").unlink()
        (self.r.p / "docs/extra.md").write_text("# Extra\nchanged\n")
        self.r.commit()
        self.assertFails(self.r.run(decl(**{**UPDATED, "handbook_pages": f"{HB}/04-codebase/order-implementation.md"})))

    def test_19_whitespace_only_documentation_change_does_not_count(self) -> None:
        self.code_change()
        path = self.r.p / f"{HB}/04-codebase/order-implementation.md"
        path.write_text(path.read_text() + "\n\n   \n")
        self.r.commit()
        self.assertFails(self.r.run(decl(**UPDATED)), "no substantive change")

    def test_20_source_file_cannot_pose_as_documentation(self) -> None:
        self.code_change(); self.doc_change(); self.r.commit()
        self.assertFails(self.r.run(decl(**{**UPDATED, "other_docs": "backend/modules/orders/Service.java"})),
                         "not a documentation file")

    def test_21_none_justification_must_cite_documentation_not_config(self) -> None:
        self.code_change(); self.r.commit()
        just = ("The change only renames a private local variable in the order service so behaviour and endpoints stay "
                "exactly the same as before; see apps/customer-web/src/App.tsx and the existing tests for confirmation.")
        self.assertFails(self.r.run(decl(impact="none", justification=just)), "must cite")

    def test_22_declaration_hidden_in_comment_or_fence_fails(self) -> None:
        self.code_change(); self.doc_change(); self.r.commit()
        good = decl(**UPDATED)
        self.assertFails(self.r.run("<!-- hidden\n" + good + "\n-->"), "hidden inside another HTML comment")
        self.assertFails(self.r.run("```\n" + good + "\n```"), "declaration is required")

    def test_22b_nested_fences_cannot_hide_a_declaration(self) -> None:
        self.code_change(); self.doc_change(); self.r.commit()
        good = decl(**UPDATED)
        self.assertFails(self.r.run("````\n```\n" + good + "\n```\n````"), "declaration is required")
        self.assertFails(self.r.run("~~~\n```\n" + good + "\n```\n~~~"), "declaration is required")
        self.assertPasses(self.r.run("```\nexample only\n```\n" + good))

    def test_22c_repository_root_links_count_for_navigation(self) -> None:
        self.r.write(f"{HB}/README.md", PAGE.format(t="Handbook",
            body="[a](/docs/developer-handbook/04-codebase/order-implementation.md) [b](02-user-journeys/customer-web.md) "
                 "[c](05-developer-workflow/documentation-policy.md) [ar](README-ar.md)"))
        self.r.commit()
        self.assertPasses(self.r.run(decl(impact="updated", summary="Root links.", handbook_pages=f"{HB}/README.md",
                                          consistency="Compared the links.")))

    def test_23_inline_comment_in_value_is_rejected(self) -> None:
        self.code_change(); self.doc_change(); self.r.commit()
        self.assertFails(self.r.run(decl(**UPDATED).replace("impact: updated", "impact: updated   # or none")), "'impact' must be")

    def test_24_repository_root_links_and_anchor_rules_follow_github(self) -> None:
        self.r.write("docs/a.md", "# A\n## _Hello_ World\n## Foo\n## Foo\n## Foo-1\n<a name=\"custom\"></a>\n"
                     "[ok](/docs/a.md#hello-world) [ok2](#foo-1) [ok3](#foo-1-1) [ok4](#custom) [bad](#nope)\n")
        self.r.commit()
        code, out = self.r.run(decl(impact="updated", summary="Adds notes.", other_docs="docs/a.md", consistency="Read."))
        self.assertEqual(code, 1, out)
        self.assertEqual(out.count("link anchor not found"), 1, out)
        self.assertIn("#nope", out)


class RepositoryContractTests(unittest.TestCase):
    def test_matrix_covers_every_tracked_non_documentation_file(self) -> None:
        import fnmatch
        matrix = json.loads((ROOT / "docs/developer-handbook/doc001-matrix.json").read_text())
        tracked = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True, check=True).stdout.split()
        uncovered = [f for f in tracked if not (f.endswith(".md") or f.startswith("docs/"))
                     and not any(fnmatch.fnmatchcase(f, g.replace("**", "*")) for a in matrix["areas"] for g in a["code"])]
        self.assertEqual(uncovered, [], "map these files to a documentation area in doc001-matrix.json")

    def test_workflow_keeps_the_stable_check_name_and_safe_triggers(self) -> None:
        wf = (ROOT / ".github/workflows/doc001.yml").read_text()
        self.assertIn("name: Documentation Consistency / DOC-001", wf)
        self.assertIn("pull_request_target", wf)
        self.assertNotRegex(wf, r"(?m)^\s*(secrets|id-token):")
        self.assertRegex(wf, r"(?m)^permissions:\n  contents: read\n  pull-requests: read")
        # the pull request is only ever read as data
        self.assertNotIn("pr/scripts", wf)  # nothing from the pull request is ever executed
        self.assertIn("base/scripts/doc001_check.py", wf)
        self.assertNotIn("--bootstrap", wf)

    def test_pr_template_carries_the_declaration_block(self) -> None:
        t = (ROOT / ".github/pull_request_template.md").read_text()
        self.assertEqual(len(re.findall(r"<!-- DOC-001:BEGIN -->", t)), 1)
        self.assertEqual(len(re.findall(r"<!-- DOC-001:END -->", t)), 1)

    def test_agent_instructions_name_doc001(self) -> None:
        for f in ("AGENTS.md", "CLAUDE.md"):
            self.assertIn("DOC-001", (ROOT / f).read_text(), f)

    def test_real_matrix_is_valid(self) -> None:
        r = subprocess.run([sys.executable, str(GATE), "docs"], cwd=ROOT, capture_output=True, text=True)
        bad = [l for l in r.stdout.splitlines() if l.startswith("FAIL") and "doc001-matrix.json" in l]
        self.assertEqual(bad, [])


if __name__ == "__main__":
    unittest.main()
