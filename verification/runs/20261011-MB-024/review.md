# MB-024 review (self-review, not independent)

Reviewer: Claude Code implementation agent (self-review).

## Scope
V17 support cases and messages, customer case and cancellation-request APIs, staff support queue, ops order monitoring, operations web support and order screens, browser workflow extension.

## Findings
1. Privacy: customer reads filter to PUBLIC messages and own cases; the database forbids customer-authored staff notes and edits to messages. Mutations exposing staff notes or dropping case ownership failed SupportIntegrationTest.
2. A cancellation request never changes the order; resolving a case as ORDER_CANCELLED requires the order to be CANCELLED (mutation removing the check failed the test). The actual cancellation remains the audited exception cancellation (MB-019) with the case reference.
3. Module ownership: support reads order numbers/state through `OrderDirectory`; orders reads case links through the `OrderCases` port implemented by support. No cross-module table reads were added.
4. Ops order view shows customer contact only while pending or being fulfilled and never the handover code.

## Proposed defaults recorded (owner may revise; MB-026 makes them configurable)
- Categories, CRITICAL/HIGH/NORMAL severity mapping, SLA 30 min / 2 h / 24 h to the first public staff response, resolution codes, one open cancellation request per order.

## Residual
- Attachments on cases are not accepted yet (field must be empty).
- Cooks have no case access; kitchen follow-up is a staff action (resolution KITCHEN_FOLLOW_UP).
- SLA breaches are visible in the queue (overdue flag/filter) but do not page anyone yet (notifications to staff need provider decisions, O07).
- Handover override with step-up is covered by MB-019 tests; in the browser it can only reach the step-up prompt locally.
