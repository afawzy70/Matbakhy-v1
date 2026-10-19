# Frequently asked questions

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — short answers for developers, each pointing at the page or file that holds the truth.

## Setup and tools

**Which Node version?** Exactly 24.21.0 (`.node-version`; `package.json` allows `>=24.21.0 <25`, and `.npmrc` enforces engines). Node 22 is not supported. See [prerequisites](../05-developer-workflow/prerequisites.md).

**Why `./mvnw` and not `mvn`?** The wrapper pins Maven 3.9.11 (`backend/.mvn/wrapper/maven-wrapper.properties`).

**Do I need Docker to run backend tests?** Yes. Integration tests start PostgreSQL 16 and an S3 emulator through Testcontainers; there is no H2 fallback.

**Does it work on Windows?** Not verified. Use WSL2; see [prerequisites](../05-developer-workflow/prerequisites.md).

**How do I start everything locally?** `scripts/dev.sh up` then `scripts/dev.sh smoke`, plus the Vite dev servers. See [local environment](../05-developer-workflow/local-environment.md).

**Where are the local passwords?** Generated into your own git-ignored env file by `scripts/dev.sh`. Never copy them into issues, chat or commits.

## Product and code

**Who calculates prices?** The server only. Clients show server quotes; a client-sent price is rejected (BR-019). See [pricing and payments](../01-business/pricing-and-payments.md) and [pricing implementation](../04-codebase/pricing-implementation.md).

**Why are amounts integers?** Money is stored and transmitted in piastres to avoid rounding errors.

**Can an order contain two cooks?** No: one cook and one slot per order (BR-018). Replacing the basket's cook or slot requires explicit confirmation.

**Is there a platform commission or fee?** No, in the MVP: zero Matbakhy commission and no customer platform fee. Deferred growth features are research, not implementation ([implementation status](implementation-status.md)).

**How does payment work?** Cash on receipt where eligible, or a manual direct deposit from the customer to the kitchen that the kitchen reports as received. The platform never claims bank verification. Real money stays gated. See [payment implementation](../04-codebase/payment-implementation.md).

**Where is the order state machine?** Canonical: [04-state-machines.md](../../product/04-state-machines.md); summary: [order lifecycle](../01-business/order-lifecycle.md); code: [order implementation](../04-codebase/order-implementation.md).

**Where do I find which story or test covers a rule?** `planning/rules.json` (rule to story and scenarios), `planning/scenarios.json`, and [requirements traceability](requirements-traceability.md).

**Is the backlog status proof that something is done?** No. A story is DONE only with evidence at a tested commit; always check the code. Handbook status labels are in [implementation status](implementation-status.md).

## Testing and evidence

**What does a green CI run prove?** That the jobs listed in [CI/CD](../05-developer-workflow/ci-cd.md) passed at that commit. It does not prove all scenarios, native device behaviour, or the deployed preview.

**How many owner acceptance cases exist, and have they passed?** The JSON sources hold 200 cases; each is NOT_RUN or BLOCKED until a run record with evidence says otherwise ([test execution](../05-developer-workflow/test-execution.md)). The latest run notes are in `docs/quality/owner-acceptance/run-2026-10-19.md`.

**Can I write PASS in a report if I did not run the test?** No. Use NOT_RUN or NOT VERIFIED.

**Where do I report a bug?** A GitHub issue using `.github/ISSUE_TEMPLATE/defect.md`, with synthetic data and sanitized logs.

## Git, PR and CI

**Which branch do I base on?** `develop`. `main` is not modified. See [git and PR workflow](../05-developer-workflow/git-and-pr-workflow.md).

**Can I merge my own PR?** Not unless the owner or repository policy explicitly allows it; agents never do.

**What happens when `develop` changes?** Product CI, then images by commit SHA, then deploy to the Contabo preview and smoke ([CI/CD](../05-developer-workflow/ci-cd.md)). The database is not rolled back automatically.

**What is the DOC-001 declaration?** A block in the PR description stating the documentation impact; rules in [documentation policy](../05-developer-workflow/documentation-policy.md), checked by `python3 scripts/doc001_check.py`.

## Preview and operations

**Why do I get 403 on the preview?** Your IP is not in the allow-list for that host class ([preview environment](../05-developer-workflow/preview-environment.md)).

**Is the preview production?** No: synthetic data, demo banner, fake providers, one server.

**Where are the operational runbooks?** `docs/operations/` (start with [08-troubleshooting-runbook.md](../../operations/08-troubleshooting-runbook.md) and [11-staging-deployment-and-rollback.md](../../operations/11-staging-deployment-and-rollback.md)).

## Documentation

**The handbook disagrees with the code. What now?** Believe the code, fix the page in your PR, and mention it in the declaration. If you cannot verify, label NOT VERIFIED.

**Where do I put future paths or commands that do not exist yet?** In a fenced code block or with `<placeholders>`; backticked repo paths must exist (checked by `scripts/doc001_check.py`).

**Is there an Arabic guide?** [README-ar.md](../README-ar.md) for developers; [owner-guide-ar.md](../../quality/owner-acceptance/owner-guide-ar.md) for the owner's preview testing.
