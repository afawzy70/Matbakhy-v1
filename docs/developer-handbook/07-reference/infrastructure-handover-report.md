# Infrastructure handover report

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — verification record of the infrastructure handbook expansion: scope, validation, review rounds, handover simulation, unverified items and verdict
> **Language / اللغة:** English · [العربية](infrastructure-handover-report-ar.md)

**Evidence legend:** VERIFIED LIVE (an authorized read-only observation made from the server itself by a non-privileged account) · VERIFIED IN CODE (read in the repository) · DOCUMENTED ONLY · OUTDATED / INCORRECT · NOT VERIFIED.

## 1. Verdict

**PARTIAL.** The infrastructure handbook (16 English pages, each with a complete Egyptian Arabic mirror) is written, structurally validated and has passed four independent AI review rounds plus a targeted fifth. It is **not** a complete handover: complete-service disaster recovery is BLOCKED (no supported, rehearsed multi-store recovery sequence exists in the repository), the rebuild guide is DOCUMENTATION VERIFIED, NOT EXECUTION VERIFIED, and every Docker, firewall, sudo and effective SSH fact is NOT VERIFIED because the observing account has no privileges. No human reviewed the English or the Arabic.

## 2. What was added

| Area | Pages |
|---|---|
| Server and accounts | [Contabo server inventory](../08-infrastructure/contabo-server-inventory.md), [administrator read-only verification](../08-infrastructure/admin-readonly-verification.md) |
| Rebuild and operations | [server rebuild guide](../08-infrastructure/server-rebuild-guide.md), [deployment pipeline](../08-infrastructure/deployment-pipeline.md), [DNS, HTTPS and sslip.io](../08-infrastructure/dns-https-sslip-io.md), [runtime architecture](../08-infrastructure/runtime-architecture.md) |
| Recovery and troubleshooting | [backup, restore and disaster recovery](../08-infrastructure/backup-restore-disaster-recovery.md), [infrastructure troubleshooting](../08-infrastructure/infrastructure-troubleshooting.md) |
| Live Preview | [live Preview access and journeys](../05-developer-workflow/live-preview-access.md) |
| Onboarding | five infrastructure labs, [day 1](../06-onboarding-labs/infra-day-1-topology-accounts.md) to [day 5](../06-onboarding-labs/infra-day-5-rebuild-recovery.md) |
| Reference | [documentation coverage and drift report](infrastructure-documentation-coverage.md) and this report |

Also changed: the two README entry points (navigation), the test-execution page (new checks), five operations documents under `docs/operations/` (historical or partly-outdated banners that point to the current pages; these are outside the handbook and have no Arabic mirror), and the parity validator (credential-shape scan, evidence-label presence and equal counts on the infrastructure pages) with its tests.

## 3. Validation

| Check | Result |
|---|---|
| Structural parity of every English page and its Arabic mirror (`scripts/validate_handbook_parity.py`) | PASS for all pairs (80 of 80 at the time of writing) |
| Validator tests (`scripts/test_validate_handbook_parity.py`) | PASS (22 tests, including credential and evidence-label cases) |
| Documentation checks (`python3 scripts/doc001_check.py docs`) and repository link validation (`python3 scripts/validate_blueprint.py`) | PASS |
| Egyptian Arabic phrase checker (reading aid only) | 0 warnings on the new Arabic pages at the last run; a clean run is not evidence of natural Arabic |
| Live HTTP and DNS observations | VERIFIED LIVE only for what the live-facts list records: six Preview hosts answered as documented from the server itself on 2026-10-21; results from other networks can differ because of the IP access classes |
| Execution of the rebuild, restore, rollback or any destructive step | NOT RUN (documentation only; the running server was not modified) |

## 4. Independent review rounds

Reviewers were separate Codex sessions (AI, not human) that read the pages side by side with the repository. Technical reviews checked claims against code and the live-facts list; semantic reviews compared English and Arabic and, from round 2, judged native-style Egyptian Arabic. Counts are material (M) and minor (m) findings; all material findings were verified against the cited file and fixed, none was ignored.

| Round | Technical (M / m) | Semantic and language (M / m) | Notes |
|---|---|---|---|
| 1 | 36 / 9 | 13 / 2 | unsupported live labels, missing failure branches, invented command forms |
| 2 | 16 / 5 | 16 / 43 | Keycloak dump paths discovered, run-list command lacked fields, language findings began |
| 3 | 14 / 8 | 10 / 21 | a documented `backup` subcommand that does not exist, rollback numbering, rehearsal isolation |
| 4 | 8 / 7 | 2 / 12 | SSH value wording, seed not idempotent, restore companions, disaster recovery marked BLOCKED |
| 5 (targeted, 8 pages) | 6 / 7 | 1 / 5 | fixed afterwards; this last fix set was not independently re-reviewed |

The language-quality gates (naturalness, grammar, clarity, technical accuracy, terminology, completeness, readability, Markdown and right-to-left presentation) were recorded per page by the reviewers; the last full pass left naturalness or terminology FAIL on several pages, all of which received the round-5 wording fixes. They are therefore NOT VERIFIED as clean after those fixes.

## 5. Handover simulation

| Role | What was done | Result |
|---|---|---|
| Infrastructure engineer | Independent technical reviewers read the pages as an engineer who had never seen the project, checking commands, flags, paths, labels and missing steps against the repository | Several missing steps were found and added; a clean-server rebuild was **not executed** (DOCUMENTATION VERIFIED, NOT EXECUTION VERIFIED); complete disaster recovery stays BLOCKED |
| Software developer | The live Preview walkthroughs were reviewed for semantic parity and against the application sources | A separate new-developer simulation of the new pages was NOT RUN |

## 6. Unverified items and limits

- Docker state (containers, images, networks, volumes, limits), firewall state, sudo policy, the effective SSH configuration, certificate renewal, backup schedules and the account used by the deployment connection: NOT VERIFIED; the administrator command sheet closes these gaps.
- The recovery gap: no supported sequence restores the application database, the identity database and the object storage together; the owner decides whether to define and rehearse one.
- The pages under `docs/operations/` remain English-only and are partly historical.
- Mermaid and right-to-left rendering were not inspected in a browser.
- The reviewers and the translators were AI sessions; a human native-speaker and infrastructure review is recommended before the pages are treated as final.
- The deferred DOC-001 limitation is unchanged: a pull request can alter its own documentation check workflow.

## 7. Follow-up for the owner

1. Run the administrator command sheet and update the NOT VERIFIED rows (a privileged, read-only task).
2. Decide on and rehearse a complete recovery sequence; until then keep disaster recovery BLOCKED.
3. Decide whether to apply the drift fixes listed in the coverage report (server paths and defaults); none was applied here.

## Known limitations and documentation gaps

- This report summarises counts at the last run; the validator and the CI job are the live source of the structural result.
