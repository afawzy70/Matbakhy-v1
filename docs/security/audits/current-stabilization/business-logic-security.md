# Business logic security assessment

Source baseline `b7ec0e9052a612f7a8aab38f00519107b1dce213`; subsequent CR-003/CR-001 patch `2be5a953319a5fef51efdf7b97f3fd6b9b6528eb` inspected separately. **No business scenario below has an independently executed authenticated end-to-end PASS in this run.** Existing test source was reviewed, not counted as test execution.

| Required scenario | Observed server control / mapped regression | Independent runtime status |
| --- | --- | --- |
| Customer cannot change another order | OrderLifecycleService customerCancel locks by customer_id; OrderService owned views/events; OrderLifecycleIntegrationTest | NOT TESTED; need customers A/B and dedicated order fixtures |
| Cook cannot read other cook private data | Owner-to-kitchen resolution, kitchen-scoped reads/locks, scoped document access; StaffAccessIntegrationTest kitchen_scope_matrix | NOT TESTED; need cooks A/B and their kitchens |
| Cook/admin authorization server-side | Current grants and method permissions, owned kitchen lookup, recent MFA on selected privileged writes | NOT TESTED with valid roles; only anonymous boundary sampled |
| Client cannot set price | Quote request excludes amounts; strict unknown fields/coercion, server computes integer minor units and validates quantities | NOT TESTED; QuoteIntegrationTest and StrictJsonIntegrationTest mapped |
| Confirmed orders keep prices | Snapshot fields on customer_order/order_item/payment; retries return prior committed order | NOT TESTED; PriceChangeIntegrationTest immutable/recovery scenarios mapped |
| Price changes require fresh quote/acknowledgement | requireValid compares meal revision/unit price, policy, delivery total and payment terms under shared pricing locks; stale quote produces PRICE_CHANGED; client fresh-quote flow reviewed | NOT TESTED; PriceChangeIntegrationTest, Checkout flow and native ordering regressions required |
| Capacity cannot be raced | JdbcSlotInventory MANDATORY transaction, slot FOR UPDATE, meal rows ordered FOR UPDATE, checks before counters update | NOT TESTED; OrderPlacementIntegrationTest concurrency cases mapped; no preview race traffic |
| Duplicate financial/order effects prevented | Actor+command+key idempotency row/hash, advisory lock permanent clientRequestId, unique order/quote constraints, payment version/state updates | NOT TESTED; duplicate/retry/concurrent placement/payment/ledger regressions mapped |
| Staff functions unavailable to normal users | @PreAuthorize permissions and scope-aware service paths; UI is not authority | Anonymous 401 sampled; authenticated normal-user matrix NOT TESTED |
| Sensitive data never leaks in responses/logs | Scoped DTOs, no traces in error mapping, RedactedThrowable, phone challenge identifiers rather than codes, token-free client logging | Public response metadata/errors sampled; authenticated fields/provider/server logs NOT TESTED |

## Transaction and money review

OrderService places inside one transaction: actor-owned valid quote, current policy acceptance, verified contact, capacity reservation, immutable item/address/contact snapshots, event/outbox and payment/cash record. QuoteService serializes pricing/policy reads with updates and compares revisions/terms before placement. Money uses integer minor units and checked arithmetic, not client totals or floating-point prices. Order transitions lock the owned row and check expected version/state; wrong handover attempts deliberately persist with bounded attempt counters. JdbcIdempotentCommands uses transactional uniqueness and request hashes, cleans failed outcomes and returns committed snapshots.

PaymentService scopes customer/kitchen reports and uses state/version checks; manual receipt is explicitly a kitchen statement, not purported bank verification. No real payments were processed. Repository invariants require zero launch commission/platform fees; independent runtime verification is still needed. Other reviewer’s CR-001 (cash due after deposit) was patched by Claude in `2be5a953319a5fef51efdf7b97f3fd6b9b6528eb`; it is cross-referenced rather than duplicated as an independently executed finding. Financial end-to-end regression remains NOT TESTED here.

## Document integrity and retest

SEC-002 corroborates baseline approved-private-document mutability, overlapping CR-003. Claude’s patch separates approved storage from the upload capability. Static review supports the remediation direction; only a replay-before-ticket-expiry test with digest comparison can establish its runtime result. We did not misuse an existing document or overwrite any preview object. SEC-001 is a separate structural PDF validation issue and remains reproducible against the unchanged inspector despite sealing: immutable unsafe bytes are still unsafe bytes.

## Execution limitations

Docker API is denied to the audit user; JDK was initially absent, then installed temporarily for the isolated PDF check only. Full Spring/Postgres/S3 integration tests were not run; no database services or application stacks were started, and no host privileges changed. Dedicated preview accounts and disposable fixture access are required for the missing checks. Existing tests or QA claims do not substitute for this reviewer’s execution evidence. The final pricing/order/authorization gates remain NOT TESTED.

## Concurrent consolidation observations

At follow-up commit `1a7d6a9da2af2acfa4accfdcc8133ece276817ca`, the other agents also report QA-002 (permanent request fingerprint mismatch, Medium), QA-003 (payment expiry race coverage) and QA-004 (cross-kitchen coverage), plus IdP staff-session MFA verification. The early return in OrderService.placeOnce compares quoteId only; this audit corroborates that static limitation but did not execute the retention scenario. Those results are external findings, separately referenced in the register and not counted as extra independently reproduced vulnerabilities. No authorization or pricing gate may pass while their required scenarios remain untested.
