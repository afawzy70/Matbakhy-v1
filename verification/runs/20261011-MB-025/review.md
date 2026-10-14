# MB-025 review (self-review, not independent)

Reviewer: Claude Code implementation agent (self-review).

## Scope
V18 refunds and settlements, commission reversal, finance statements (cook and API-086), finance actions API-084/085/087/088, operations web finance section.

## Findings
1. Over-refund prevention counts every refund that is not rejected (approved-unpaid refunds are committed). A mutation counting only verified refunds failed the test.
2. Settlement uniqueness relies on a partial unique index over active lines; a concurrent double-batch test yields exactly one success; a mutation inserting inactive lines failed the test. Reviewed lines are immutable by trigger; delete is refused.
3. Separation of duties (approve vs request, verify vs pay, review vs create, reconcile vs record) in service (403) and database checks.
4. Self-review defect fixed before commit: the first UI draft sent a placeholder version for refund/settlement actions, which would always conflict; statement rows now carry their versions and the UI sends them (component test asserts the body).
5. The cook statement wording changed by design: refunds and settlements are now part of it; the earlier test assertion "does not contain REFUND" was replaced by assertions that the statement includes REFUNDS and SETTLEMENTS with direction COOK_PAYS_PLATFORM.

## Proposed defaults (O04 open)
- Full refunds only; a verified full refund reverses the full commission; refunds need a support case reference.

## Residual / not run
- Finance UI is covered by component tests only; the browser workflow does not yet exercise finance (NOT_RUN for that path).
- Partial refunds, refunds paid by the cook vs the platform, settlement cycles and invoices await O04.
