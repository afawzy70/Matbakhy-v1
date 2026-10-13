# Independent QA test suite

Entry point for owner/tester review. Product requirements remain in [planning/scenarios.json](../../../planning/scenarios.json), [business rules](../../product/03-business-rules.md), [D13](../../product/rq-004-stale-quote.md), and D14/TC-153. This suite expands those scenarios into independent executable journeys; it does not change product acceptance or their reported completion status. Definitions in `cases.json` and the role documents are separate from [execution evidence](../../../verification/runs/20261013-IQA-d877a15/README.md). All definitions are SELF_REVIEWED; no OWNER_REVIEWED claim.

- [Coverage and eligibility](coverage-matrix.md)
- [Customer web](customer-web.md)
- [Kitchen](kitchen.md)
- [Backoffice](backoffice.md)
- [Android](native-android.md) / [iOS](native-ios.md), separate native executions
- [Cross-cutting and pricing](cross-cutting.md)

Fixture: `qa/regression-d877a15/seed.py`, generated private credentials outside Git. Kitchens A/B/PAUSED; cook-a/b/new; customer-a/b; permissions scoped to ops-review, support-a, finance-a/b, config-a and ops-a. A1=12025, A2=9550, B1=8025 EGP minor; baseline meal caps3, high-stock slot5cap25, kitchen max50; A3soldout. Policy tax0,commission1000bps HALF_UP excluding delivery,max10; zoneAfee3000. These are synthetic test values, not commercial decisions. Absolute UUID mappings in sanitized run fixtures. Native uses documented `apps/customer-mobile/e2e-native/seed.sql`, separate fresh services per platform.

Each FULL REGRESSION starts with newly generated credentials, empty dedicated Compose volumes and one-shot seed. A test must record application SHA and suite SHA and verify its prerequisites. No prior PASS is carried forward. Connected pickup→fulfilment→support→finance cases deliberately share one exact order fixture; independent basket/stock cases clear their own cart or legally cancel reserved stock. Change only test-harness/setup/evidence. Transport faults do not mock pricing, auth or persistence. A failed prerequisite is BLOCKED, not a product FAIL. Zero cases or silently missing scenarios fail the runner.

Review requests: confirm the detailed expected results, especially public support conversations, slot state controls, dual-person finance and D13 old/new component attribution. Commercial values (O04), legally required onboarding documents/retention (O06), provider choices (O07), support operational contacts (O09) remain unresolved. The owner has already decided D13/D14; RQ-004 is no longer an open pricing question. Previously incorrect old-quote oracle stays in historical evidence, with the new D13-based expectation explicitly replacing it for this campaign.
