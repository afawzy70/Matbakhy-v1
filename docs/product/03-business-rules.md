# Business rules and acceptance catalogue

All rules below are explicit proposed MVP defaults unless confirmed by the decision register. They define implementable behavior; they do not claim owner approval of commercial/legal policy. See the state-machine and data-model documents for transaction details.

The machine-readable source is [rules.json](../../planning/rules.json). Observable Given/When/Then tests are in [scenarios.json](../../planning/scenarios.json). Every rule maps to a delivery story. All product scenarios start NOT_RUN.

## Identity

- **BR-001** — Only authenticated active accounts may perform private operations; public browsing remains anonymous. Implementation: `MB-009`; verification: `TC-001`.
- **BR-002** — Web mutations require CSRF protection and native auth uses authorization code with PKCE; clients contain no secret. Implementation: `MB-009`; verification: `TC-002`.
- **BR-003** — First checkout requires verified email and verified normalized Egyptian phone; changing phone invalidates its verification. Implementation: `MB-010`; verification: `TC-003`.
- **BR-004** — OTP is single use, hashed, valid 5 minutes, max 5 attempts; resend at least 60 seconds and bounded hourly limits are configurable. Implementation: `MB-010`; verification: `TC-004`.

## Address

- **BR-005** — Address ownership is enforced; street/building/zone/recipient/contact required for delivery; optional coordinates cannot override service zone. Implementation: `MB-010`; verification: `TC-005`.

## Access

- **BR-006** — Every kitchen/order/case/list/count/export checks object scope; UUID secrecy is not authorization. Implementation: `MB-011`; verification: `TC-006`.
- **BR-007** — Staff privileges are assigned server-side; MFA required; revoked grants cannot perform sensitive commands. Implementation: `MB-011`; verification: `TC-007`.
- **BR-043** — Staff cannot self-escalate or remove last recoverable administrator; high-risk changes require step-up. Implementation: `MB-026`; verification: `TC-043`.

## Cook

- **BR-008** — Cook approval is required before selling; approval requires a complete revision and reviewer/reason record. Implementation: `MB-012`; verification: `TC-008`.
- **BR-009** — Cook pause stops new sales; suspension stops new sales/acceptance and triggers ops review while preserving existing accepted order access. Implementation: `MB-012`; verification: `TC-009`.

## Media

- **BR-010** — Public media must pass type/size/content checks and quarantine; private verification documents never get public URLs. Implementation: `MB-013`; verification: `TC-010`.

## Catalogue

- **BR-011** — Published meals require title, description, portion, positive price, ingredients, explicit allergen declaration and approved image/cook. Implementation: `MB-014`; verification: `TC-011`.
- **BR-012** — Meal updates are versioned; archive removes new ordering but never changes historical item/allergen/price snapshots. Implementation: `MB-014`; verification: `TC-012`.

## Availability

- **BR-013** — Capacity is bounded at both meal-slot and total cook-slot portions; reductions below reserved+allocated+consumed are rejected. Implementation: `MB-015`; verification: `TC-013`.
- **BR-014** — Store instants in UTC and schedule in Africa/Cairo; reject nonexistent local times and disambiguate repeated times. Implementation: `MB-015`; verification: `TC-014`.
- **BR-015** — Slots require end after start and cutoff before start; default lead 120 minutes is fixture policy; closed/past slots cannot order. Implementation: `MB-015`; verification: `TC-015`.

## Discovery

- **BR-016** — Search/filter/sort/pagination execute on the server over all eligible data; a filter change resets cursor and sends a new query. Implementation: `MB-016`; verification: `TC-016`.
- **BR-017** — Only approved active kitchens and published serviceable meals are purchasable; paused/archived favorites show unavailable. Implementation: `MB-016`; verification: `TC-017`.

## Basket

- **BR-018** — A basket/order contains exactly one cook and one slot; replacing either requires explicit client confirmation before clearing. Implementation: `MB-017`; verification: `TC-018`.

## Pricing

- **BR-019** — Server calculates amounts in integer EGP minor units; client prices/fees/roles are rejected, not trusted. Implementation: `MB-017`; verification: `TC-019`.
- **BR-020** — Quote lasts 5 minutes, reserves no stock, snapshots versions and exposes complete cost/policy before confirmation. Implementation: `MB-017`; verification: `TC-020`.

## Fulfillment

- **BR-021** — Delivery requires active cook-zone coverage and complete own address; pickup only if enabled and has zero delivery fee. Implementation: `MB-017`; verification: `TC-021`.
- **BR-030** — Pickup completes only from READY; delivery dispatches READY to OUT_FOR_DELIVERY before completion; wrong-mode commands rejected. Implementation: `MB-019`; verification: `TC-030`.
- **BR-031** — Completion requires single-use customer handover code with bounded attempts; ops override requires fresh MFA, reason and evidence. Implementation: `MB-019`; verification: `TC-031`.

## Order

- **BR-022** — Placement atomically validates quote, reserves all stock, writes snapshots/idempotency/events/outbox and cash due record. Implementation: `MB-018`; verification: `TC-022`.
- **BR-023** — Permanent unique customer+clientRequestId prevents duplicate orders even after cached idempotency response expires. Implementation: `MB-018`; verification: `TC-023`.
- **BR-024** — Idempotency scope is actor+command+key; different payload with same key returns conflict. Implementation: `MB-018`; verification: `TC-024`.

## Stock

- **BR-025** — Concurrent last-portion requests must serialize/check stock and never oversell either meal or kitchen totals. Implementation: `MB-018`; verification: `TC-025`.
- **BR-029** — Reject/expire/preparation-not-started cancellation releases once; after preparation capacity stays consumed unless audited adjustment. Implementation: `MB-019`; verification: `TC-029`.

## Lifecycle

- **BR-026** — Only legal actor-scoped transitions are accepted with expected version; terminal order state is immutable. Implementation: `MB-019`; verification: `TC-026`.
- **BR-027** — Acceptance deadline is min(created+15m, slotStart-lead), with 5m minimum remaining at placement; expiry uses server time. Implementation: `MB-019`; verification: `TC-027`.

## Cancellation

- **BR-028** — Customer can directly cancel pending orders only; after acceptance customer opens a request; preparing/ready cancellation needs ops reason. Implementation: `MB-019`; verification: `TC-028`.

## Cash

- **BR-032** — Order completion and collection are independent; cook report and finance verification are separate events. Implementation: `MB-020`; verification: `TC-032`.

## Finance

- **BR-033** — Commission rate/rounding are snapshot; exclude delivery; balanced immutable entries post once per source event. Implementation: `MB-020`; verification: `TC-033`.
- **BR-034** — Cook-collected cash creates commission receivable, not gross platform cash/revenue; remittance requires evidence. Implementation: `MB-025`; verification: `TC-034`.

## Refund

- **BR-035** — Full refunds are bounded by verified collection less prior verified refunds; approval does not mean paid; commission reversal follows snapshot policy. Implementation: `MB-025`; verification: `TC-035`.

## Settlement

- **BR-036** — Settlement lines are unique and reviewed batches immutable; payment recorded only with actual external reference and reconciliation. Implementation: `MB-025`; verification: `TC-036`.

## Notification

- **BR-037** — Order/inbox persists despite notification-provider failure; retries are bounded and dead letters visible. Implementation: `MB-021`; verification: `TC-037`.
- **BR-038** — Use transactional outbox and recipient/event deduplication with leased workers; notification cannot regress order state. Implementation: `MB-021`; verification: `TC-038`.

## Reviews

- **BR-039** — Only owning customer can review a COMPLETED order once, integer 1..5; visible reviews alone affect average/count. Implementation: `MB-028`; verification: `TC-039`.

## Favorites

- **BR-040** — Favorite/unfavorite is idempotent per user+meal; sharing exposes only public content. Implementation: `MB-028`; verification: `TC-040`.

## Support

- **BR-041** — Cases are order-scoped; private staff notes never appear to customer/cook; safety complaints escalate operationally. Implementation: `MB-024`; verification: `TC-041`.

## Configuration

- **BR-042** — Fee/rate/zone/policy changes are versioned and audited; only new quotes use new policy; live unapproved config blocks ordering. Implementation: `MB-026`; verification: `TC-042`.

## Web

- **BR-044** — Order submission uses stable key persisted before request; unknown response resolves via request lookup then same-key retry. Implementation: `MB-027`; verification: `TC-044`.
- **BR-045** — Every critical screen has loading/empty/error/offline/denied/success states and an actionable recovery; preserve valid form input. Implementation: `MB-027`; verification: `TC-045`.

## UX

- **BR-046** — Arabic RTL and English LTR work at phone/tablet/desktop widths, long text and mixed numbers; all actions remain usable. Implementation: `MB-029`; verification: `TC-046`.

## Accessibility

- **BR-047** — Keyboard, visible focus, labels/errors, dialog focus restoration and screen readers support every critical journey. Implementation: `MB-029`; verification: `TC-047`.

## Mobile

- **BR-048** — Use system-browser PKCE and OS secure storage; logout/account switch clears private data and registrations. Implementation: `MB-031`; verification: `TC-048`.
- **BR-049** — Offline cannot submit orders or pretend stock is current; foreground resume refreshes quote/order; app-kill retry preserves request ID. Implementation: `MB-032`; verification: `TC-049`.
- **BR-050** — Push permission optional; route deep links after authentication and object authorization; handle cold/warm start. Implementation: `MB-033`; verification: `TC-050`.
- **BR-051** — Android acceptance requires native build/install/test evidence with version, device/OS and artifact checksum. Implementation: `MB-034`; verification: `TC-051`.
- **BR-052** — iOS acceptance requires native simulator/device and signing evidence; absent Apple tooling is BLOCKED. Implementation: `MB-035`; verification: `TC-052`.

## Security

- **BR-053** — Input is validated/encoded; uploaded files, rich text, URLs and CSV exports cannot execute script/formula injection. Implementation: `MB-036`; verification: `TC-053`.
- **BR-054** — Rate limits cover login/OTP/quote/order/upload/search; errors avoid enumeration and secrets. Implementation: `MB-036`; verification: `TC-054`.

## Privacy

- **BR-055** — Deletion requests revoke access and apply approved retention/anonymization across stores; financial audit integrity preserved. Implementation: `MB-039`; verification: `TC-055`.

## Observability

- **BR-056** — Logs/metrics exclude tokens/OTP/contact/address/document content; request IDs join diagnosis safely. Implementation: `MB-039`; verification: `TC-056`.

## Recovery

- **BR-057** — Backup is only accepted after restore test; migrations validate empty and prior-release database; recovery reconciles ledger/stock. Implementation: `MB-039`; verification: `TC-057`.

## Reliability

- **BR-058** — Jobs use leases and idempotent effects; restart around commit/send and multi-instance races cannot duplicate side effects. Implementation: `MB-037`; verification: `TC-058`.

## Performance

- **BR-059** — Measure documented load/hardware and preserve correctness; excessive queries/list limits cannot exhaust server. Implementation: `MB-038`; verification: `TC-059`.

## Deployment

- **BR-060** — Dev/test/staging/production credentials and data are separate; required fake adapters prevent production startup. Implementation: `MB-040`; verification: `TC-060`.

## Operations

- **BR-061** — Operators must rehearse late/failed/safety/refund/cash-variance cases and know escalation contacts before pilot. Implementation: `MB-041`; verification: `TC-061`.
- **BR-064** — Pilot closure requires accepted surfaces, observed metrics and reconciled incidents; residual risks are explicit. Implementation: `MB-046`; verification: `TC-064`.

## Release

- **BR-062** — Required gate evidence is tied to exact source/build and cannot pass with skipped/failed/blocked mandatory checks. Implementation: `MB-043`; verification: `TC-062`.
- **BR-063** — Android and iOS publication is distinct from build/upload; store credentials and explicit release authorization required. Implementation: `MB-045`; verification: `TC-063`.

