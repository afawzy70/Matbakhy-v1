# DOC-001 — Mandatory Documentation Synchronization Policy

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — permanent, owner-approved governance rule; the CI gate named below enforces its structural part

**NO DOCUMENTATION ASSESSMENT — NO MERGE. OUTDATED OR CONTRADICTORY REQUIRED DOCUMENTATION — NO MERGE.**

The Developer Handbook is a living, mandatory part of the software deliverable. A change is not complete until code, tests, contracts, architecture and the affected handbook pages agree. This applies to every developer, coding agent, reviewer and future contributor.

## 1. What is covered

Every PR: features, enhancements, bug fixes, hotfixes, refactoring, architecture, backend, frontend, Android/iOS, security, Keycloak, API, database migrations, business rules, pricing and financial logic, UI/UX, infrastructure, CI/CD, configuration, dependency upgrades, test changes and documentation-only changes. No category is exempt.

## 2. Procedure

1. **Before coding** — find the affected areas in [doc001-matrix.json](../doc001-matrix.json) (area → code paths → documentation that must change; roles in [documentation-maintenance.md](documentation-maintenance.md)). Read those pages and the code they describe.
2. **While coding** — update the pages, diagrams (`../03-architecture/diagrams.md`), API contracts (`contracts/openapi.yaml`, `contracts/endpoints.json`), test cases (`docs/quality/owner-acceptance/cases/`), and traceability rows ([requirements-traceability](../07-reference/requirements-traceability.md)) in the same PR. Keep status labels accurate in [implementation-status](../07-reference/implementation-status.md). If a rule changes, add a new decision that references its predecessor ([architecture-decisions](../07-reference/architecture-decisions.md)); never silently rewrite history.
3. **Before the PR** — run `python3 scripts/doc001_check.py docs` and `python3 scripts/test_doc001_gate.py`; compare the documentation against the code and tests yourself.
4. **In the PR** — fill the *Documentation Impact* section of the PR template, including the machine-readable declaration.
5. **Review** — the reviewer compares the documentation with the code, tests and requirements and rejects misleading or contradictory text. Independent Codex review can be used, but it is never presented as human approval.

## 3. The PR declaration

The PR description contains exactly one block:

```
<!-- DOC-001:BEGIN -->
impact: updated
summary: what changed and why
handbook_pages: docs/developer-handbook/<page>.md, ...
other_docs: docs/product/..., ...
diagrams: ...
contracts: ...
tests: ...
traceability: ...
consistency: what you compared (code, contract, tests) to verify agreement
justification: (only for impact none)
governance_review: (only for governance changes) actual author/reviewer roles, Codex review evidence or NOT_RUN
<!-- DOC-001:END -->
```

`impact` is `updated` or `none`; values must not carry inline comments. Write each key on one line (indented continuation lines are allowed); put the block in the visible PR description, not inside a code fence or another HTML comment (both are rejected).

Rules enforced by `scripts/doc001_check.py pr`:

- Unknown keys, duplicate keys, a missing/duplicated/unbalanced marker pair, or an `impact` other than `updated`/`none` fail.
- `updated`: needs `summary`, `consistency`, and at least one of `handbook_pages`/`other_docs`; every listed path must exist **and have a substantive change in the PR** (deleted files and whitespace-only edits do not count; `handbook_pages`/`other_docs`/`diagrams`/`traceability` must be documentation files, `contracts` contract files, `tests` non-documentation files); `handbook_pages` must live under `docs/developer-handbook/`; for every matrix area whose code changed, at least one of that area's documentation files must be changed.
- `none`: only for PRs that change non-documentation files and no documentation files; `justification` of at least 80 characters, not generic, citing at least one existing documentation page under `docs/`, a top-level guide or the API contract (repo-relative path) that stays accurate; citing `package.json` or source code does not count. Generic text ("No docs needed", "Minor change", "Internal only", "Bug fix", "No user impact") fails. A structurally valid `none` is **accepted only for reviewer evaluation** — the gate prints `REVIEWER MUST EVALUATE`.
- Files outside every matrix area are never silent: they need a documentation change or a specific justification.
- A documentation-only PR must declare `updated`.

## 4. Documentation validation (always runs)

Broken relative links and anchors; links leaving the repository; inline-code references to repository paths that do not exist (`path` or `path:line`); missing metadata (`Owner role`, `Status` with an allowed label) on handbook pages; handbook pages unreachable from `README.md`; missing required entry points (README, README-ar, this policy, the matrix, the PR template); `TC-`/`MB-`/`BR-` identifiers and owner-E2E case ids that do not exist in `planning/` or `docs/quality/owner-acceptance/cases/`; an invalid matrix.

**What the gate cannot do:** prove that a sentence is true. Passing the gate means "structure and references are intact". When documenting something that does not exist (a missing file, a planned path), do not format it as inline code (the gate treats code-formatted repository paths as claims that the file exists). Semantic accuracy needs the reviewer's comparison (section 2, step 5). Never read a green check as an accuracy certificate.

## 5. Governance of the gate itself

Changes to `.github/workflows/doc001.yml`, `scripts/doc001_check.py`, `scripts/test_doc001_gate.py`, `scripts/doc001_trusted_verifier.py`, `scripts/test_doc001_trusted_verifier.py`, the matrix, this policy, `AGENTS.md`, `CLAUDE.md`, `.github/pull_request_template.md` or `.github/CODEOWNERS` are **governance changes**. They require, in addition to a valid declaration:

- `governance_review`: the **actual roles** (who authored: owner, Mohamed or which AI agent session; who reviewed), the Codex code-review and security-review evidence as supporting evidence (link, or `NOT_RUN`), and the statement that GitHub cannot independently verify identities; and
- an **owner authorization** comment by the repository owner on the PR whose entire text is `OWNER-AUTHORIZES-GOVERNANCE <first 12 characters of the head commit>`, bound to the current head commit (a new push needs a new comment). Agents never post it. Re-run the check after commenting (editing the PR description also re-runs it).

The project currently has **one GitHub account** shared by the owner, the new developer and AI agents, so a separate GitHub approval is neither required nor possible, and Codex review is never treated as equivalent to a separate human approval. The gate verifies the commenter login and `author_association`, not who typed; see [trusted enforcement](doc001-trusted-enforcement.md#5-governance-changes-in-the-single-account-model) for the limits.

The workflow uses the `pull_request` event (`pull_request_target` reads its workflow from `main`, which this project does not modify; verified on PR #25). The in-repository job runs the gate script checked out at the **base** SHA and reads the PR only as data, but a PR can still replace the job itself (demonstrated by disposable PR #33). The fix is the external verifier in [trusted enforcement](doc001-trusted-enforcement.md): a GitHub App posts the required check from the protected base branch's gate and the ruleset pins the check to that App. Until it is installed and the #33 scenario is observed blocked, the hole is open and every diff touching `.github/workflows/` needs the owner's diff review.

Emergency exceptions: only the repository owner may authorize one, in writing on the PR; the exception and a follow-up remediation issue are recorded in the PR.

## 6. Branch protection status

The check context is **`Documentation Consistency / DOC-001`** (job name in `.github/workflows/doc001.yml`; regression-tested in `scripts/test_doc001_gate.py`). It becomes merge-enforced only when the repository ruleset/branch protection for `develop` lists that context as required. See [documentation-maintenance.md](documentation-maintenance.md#branch-protection-status) for the verified current state and the exact settings steps.

## 7. Related

[Git and PR workflow](git-and-pr-workflow.md) · [CI/CD](ci-cd.md) · [Documentation map](../07-reference/documentation-map.md) · `AGENTS.md` · `CLAUDE.md` · `.github/pull_request_template.md`
