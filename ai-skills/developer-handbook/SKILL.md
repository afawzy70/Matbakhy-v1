---
name: developer-handbook
description: Create, audit, maintain, and validate a complete bilingual software engineering and infrastructure handover handbook for any software repository, including architecture, business rules, development workflow, CI/CD, deployment, infrastructure reconstruction, operational procedures, live application walkthroughs, and disaster recovery.
---

# developer-handbook

Produce and maintain a handover handbook that lets a new software developer **and** a new infrastructure engineer work from the repository alone, in English and a complete mirror in a second language (Egyptian Arabic by default). It works for any stack: it documents only what it detects and never invents project facts.

## When to use

Use it when the user says "use the developer-handbook skill", or asks to create, audit, extend, translate, validate or hand over engineering documentation for a repository. Do not use it for a single README tweak or for application changes.

## Operating rules (non-negotiable defaults)

1. **Documentation only.** Do not change application code, deployment behaviour, CI/CD semantics, branch protection or live infrastructure. Work on a dedicated branch (and a separate git worktree when other work is in progress); open a pull request against the project's designated development branch (find it; do not assume `develop`); never merge, deploy or force-push.
2. **Evidence before prose.** Label claims with one of four classes: `VERIFIED LIVE` (observed on the running environment by an authorized read-only check), `VERIFIED IN CODE` (read in the repository), `DOCUMENTED ONLY` (a document says so), `OUTDATED / INCORRECT` (conflicts with newer evidence). Anything else is `NOT VERIFIED`. Never present documented or simulated behaviour as live-verified.
3. **Read-only discovery.** Use only non-privileged, read-only inspection. No sudo, no user switching, no reading secret files or private data, no restarts, package updates, firewall/DNS/SSH/permission changes, deployments or destructive restore tests. If access is missing, write the limitation and prepare a minimal read-only command sheet for an authorized administrator instead.
4. **No secrets.** Never commit passwords, keys, tokens, private env contents or customer data. Describe how an authorized person obtains a secret, not its value. Use placeholders.
5. **Preserve existing verified documentation**; mark outdated material as historical and link to the current replacement instead of deleting it.
6. **Only detected technology.** No chapters for stacks the project does not use. No invented business rules, URLs, commands, ports, accounts or responsibilities (do not infer an account's role from its name).
7. **Never claim more than was shown.** A passing structural validator is not semantic equivalence; a documented rebuild that was never executed is `DOCUMENTATION VERIFIED, NOT EXECUTION VERIFIED`.

## Workflow (stages A to H)

Run the stages in order; load a reference only when you reach the stage that needs it.

| Stage | Goal | Load |
|---|---|---|
| A Discovery | Inventory product, stack, components, persistence, auth, integrations, tests, git conventions, CI/CD, environments, existing docs; classify evidence | `references/discovery-methodology.md` |
| B Developer handbook | Business, journeys, architecture, codebase map, local setup, tests, PR workflow, deployment verification, onboarding labs | `references/developer-handover.md`, `templates/handbook-index.md` |
| C Infrastructure handbook | Hosting reality, accounts and permissions, network, containers/storage, rebuild, secrets, DNS/TLS, CI/CD hookup, monitoring, troubleshooting, backup, restore, rollback, disaster recovery | `references/infrastructure-handover.md`, `references/deployment-and-recovery.md`, `templates/infrastructure-inventory.md`, `templates/deployment-runbook.md`, `templates/disaster-recovery.md` |
| D Interactive documentation | Real environment URLs (safely verified), roles, test-account names without passwords, navigation, journeys with expected results | `references/interactive-documentation.md`, `templates/live-environments.md` |
| E Bilingual mirror | Complete second-language pages next to each source page, language-switch navigation | `references/bilingual-parity.md` |
| F Quality assurance | Parity validator with positive and negative tests, link/identifier/diagram checks, CI integration, independent semantic review | `references/quality-gates.md`, `scripts/` |
| G Handover simulation | Two fresh-engineer walk-throughs, every gap recorded and fixed, review repeated | `references/quality-gates.md`, `templates/verification-report.md` |
| H Delivery | Branch, meaningful commits, pull request, completion and verification report | `templates/verification-report.md` |

## Inputs to establish first

Detect, then confirm only what cannot be detected: documentation directory (default `docs/handbook`), source language, mirror language and suffix (default `-ar`), development branch, whether authorized read-only access to a running environment exists, and which existing pages must be preserved. Record decisions in the verification report.

## Tools bundled here

- `scripts/validate_bilingual_parity.py`: structural parity validator (markdown-it-py based; options `--docs-dir`, `--suffix=-ar`, `--require-metadata`, `--evidence-dir`, `--fix-anchors`, `--update-sync`, `--base`). `scripts/test_bilingual_parity.py` are its positive and negative tests.
- `scripts/scan_handbook_facts.py`: read-only repository fact scanner that lists detected stacks, workflows, container files and documentation, so discovery starts from evidence.
- `templates/`: skeletons to copy and fill (never leave placeholder text in delivered pages).
- `examples/`: a tiny fixture project used by the skill's dry-run test.

## Completion criteria

Report PASS only with evidence: every source page has a mirror; the validator and its tests pass; links resolve; documentation checks of the project pass; an independent semantic review was run (or its absence is stated); two handover simulations are recorded; nothing outside documentation changed; live systems untouched. Otherwise report PARTIAL or BLOCKED with the exact blocker. Recommend, but never create, further skills without owner approval.
