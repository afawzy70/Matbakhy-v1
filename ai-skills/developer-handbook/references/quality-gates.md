# Quality gates (Stages F and G)

## F1 Structural parity (automated)
Use `./scripts/validate_bilingual_parity.py` (or the project's equivalent). It compares per pair: missing/orphan mirrors, heading levels, code blocks (language and exact content), Mermaid structure with labels masked, tables (rows, columns), lists and checklists, blockquotes/paragraphs/rules/HTML blocks, images, link destinations and anchors, inline-code identifiers, protected tokens and numbers, arrow direction, language-switch line, secret patterns, optional metadata and evidence labels, a reviewed-pair record (`translation-sync.json`), a same-PR rule (`--base`) and a documented-exception file. Word counts are never a quality metric.

**Validate the validator.** Keep positive tests (a faithful pair passes) and negative tests (each defect class fails). Run `./scripts/test_bilingual_parity.py`.

## F2 Documentation reference checks
The project's own documentation checks (links, anchors, source-path references, identifiers existing in their registries, metadata, navigation reachability). Reuse them; do not weaken them.

## F3 CI integration
Add a documentation-only workflow limited to documentation paths: install the parser pinned, run the validator tests, the validator, the same-PR rule on pull requests, and the project's documentation checks. Do not change existing required checks, branch protection or deployment workflows; recommend, but do not apply, making the new check required.

## F4 Independent semantic review (mandatory when any reviewer is available)
Use a reviewer that did not translate the pages (another agent or session, or a human). Read each pair side by side; report material findings (omission, meaning change, wrong number/command/identifier/warning, attribution or direction reversal, dropped qualifier) and minor findings, with file, section, evidence and suggested fix. Fix, then re-review; repeat until a full pass reports no material finding. Use different page groupings per round and include the known defect classes in later rounds. Say plainly that the reviewers were AI sessions if they were; do not call that a human approval. A structural pass never proves semantic equivalence.

## F4b Technical-accuracy review checklist (lessons from repeated review rounds)
Reviewers repeatedly found these in infrastructure and delivery documentation; check each before delivery:
- A command or subcommand is documented that the tool's dispatcher does not expose (internal functions are not commands). Open the dispatcher.
- A tool command is shown with default output that does not contain the promised field (for example a CLI list that needs explicit JSON fields).
- An evidence label is stronger than the observation (a time zone observed does not verify cron or log timestamps; a package seen on one host does not verify a fresh image has it; membership of a privileged group does not verify effective privileges).
- A procedure claimed "isolated" reuses live secrets, environments or automation triggers.
- A raw archive or copy step that ignores version-control ignore rules and so includes secrets or generated files.
- A failure branch missing from the "what happens on failure" text (a later step that fails after the switch was already opened or the release already deployed).
- A recovery table presented as a rehearsable sequence although a store, a cutover or an ordering problem is missing: say what exists, what is missing, NOT VERIFIED, and the owner decision needed. Never invent the missing sequence.
- Numbering and "latest" semantics (rollback numbering over recorded successes only; "latest successful run" older than a newer run that deployed and then failed a later step).

## F5 Egyptian Arabic editorial QA (mandatory, stage E2)
Order: read the full source page; write the full Arabic page; run structural parity; native-style editorial review (read the Arabic aloud as an Egyptian engineer; list unnatural phrases and literal translations using [egyptian-arabic-anti-patterns.md](egyptian-arabic-anti-patterns.md)); fix grammar, punctuation, clarity and terminology per [egyptian-arabic-style-guide.md](egyptian-arabic-style-guide.md); compare the corrected Arabic with the source again for meaning and completeness; rerun parity and link validation; independent final review where available. `./scripts/check_arabic_style.py` may list known problematic phrases as warnings: do not use keyword matching as the only measure, do not auto-replace terminology without reading the context, and never weaken parity checks.

Language-quality gates, recorded per document as PASS, FAIL or NOT VERIFIED: naturalness · grammar · clarity · technical accuracy · terminology consistency · completeness · readability · Markdown and right-to-left presentation. A material mistranslation, a misleading technical instruction, an omitted detail or unresolved unnatural wording prevents an unconditional PASS. Record who reviewed (AI session or human); AI-only review is reported as AI-only, never as human verification. A green script is not a language-quality PASS.

## G Handover simulation
Two walk-throughs by reviewers who have not seen the project:
- **Software engineer**: understand the business, navigate the code, run locally, change something small, run tests, prepare a pull request, trace deployment, open the live application, verify.
- **Infrastructure engineer**: understand the topology, identify accounts and permissions, rebuild from a clean machine, configure DNS and TLS, understand the CI/CD deployment, monitor, diagnose, restore, verify health.
Record every missing step and unresolved assumption, fix them, repeat. If no isolated rebuild was executed, classify the rebuild as `DOCUMENTATION VERIFIED, NOT EXECUTION VERIFIED`.

## Verdict rules
PASS only when every claim above has evidence in the verification report. PARTIAL when documented limits remain (no human review, not executed rebuild, NOT VERIFIED runtime facts). BLOCKED when access or an input is missing. List remaining exceptions, risks and unverified assumptions.
