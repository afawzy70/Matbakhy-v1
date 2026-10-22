# Kitchen management

> **Owner role:** Business documentation
> **Status:** IMPLEMENTED — cook application, approval, operating states, meals, slots and the cook workspace exist in the backend and `apps/operations-web`; native cook app is not planned for the MVP.
> **Language / اللغة:** English · [العربية](kitchen-management-ar.md)

A "kitchen" is the selling identity of one approved cook. This page covers how someone becomes a seller, what a kitchen can do, and what staff can do to it. The cook-side screens are described in the [cook operations journey](../02-user-journeys/cook-operations.md), the staff side in the [backoffice journey](../02-user-journeys/backoffice-admin.md).

## 1. Becoming a cook: application and approval

```mermaid
sequenceDiagram
  participant A as Applicant (customer account)
  participant API as Backend (cooks module)
  participant R as Reviewer (cook.review + recent MFA)
  A->>API: PUT /cook/application (private draft, API-043)
  A->>API: upload documents (API-039/040), private
  A->>API: POST /cook/application/submit (API-044)
  API-->>A: immutable revision 1, state SUBMITTED
  R->>API: decision START_REVIEW (API-069)
  R->>API: APPROVE / REQUEST_CHANGES / REJECT + reason + revision + version
  API-->>A: APPROVED => kitchen row created; applicant becomes a cook
```

- **Draft.** One application per account (`cook_application`). A draft holds public name, bio, contact phone (normalised to an Egyptian number, `EgyptianPhone`), private production location, whether pickup is offered, service zones and document ids. Saving a draft changes nothing about the person's status: they are **not** a cook (owner case E2E-COOK-048). Files: `backend/modules/cooks/src/main/java/com/matbakhy/cooks/internal/CookApplicationService.java`.
- **Submission** requires a complete draft and creates an immutable `cook_application_revision` (database trigger `cook_revision_immutable`, `backend/app/src/main/resources/db/migration/V5__cook_applications_and_kitchens.sql`). An incomplete draft is refused with a list of missing fields and creates no revision. Zones deactivated after the draft block submission.
- **Review** (`CookReviewService`): decisions are `START_REVIEW`, `APPROVE`, `REQUEST_CHANGES`, `REJECT`. Rules:
  - needs `cook.review` **and** recent MFA (`CookReviewController`);
  - `START_REVIEW` must come first; a decision names the **revision** and the application **version** it is about; a reason is required; one effect per idempotency key;
  - a reviewer can never decide their own application (the attempt is audited; owner case E2E-OPS-108);
  - private documents are readable only by the owner and reviewing staff, and each staff read is audited (E2E-COOK-053);
  - `APPROVE` creates exactly one `kitchen` (concurrent approvals still produce one; test `concurrent_approvals_create_exactly_one_kitchen`, `backend/app/src/test/java/com/matbakhy/app/cooks/CookWorkflowIntegrationTest.java`);
  - `REQUEST_CHANGES` returns the applicant to edit and resubmit as the next revision; `REJECT` keeps all history and never creates a kitchen (E2E-COOK-111).
- **Approval is operational.** It means staff reviewed a complete revision and recorded who decided and why. It is not a food-safety certificate, and public DTOs expose the neutral label `OPERATIONALLY_VERIFIED`.

Owner cases: E2E-COOK-048, E2E-COOK-049, E2E-COOK-111, E2E-OPS-109.

## 2. The kitchen record and its operating states

Table `kitchen` (V5): one per owner (`owner_id UNIQUE`), public slug, display name, description, `operating_state`, `timezone` fixed to `Africa/Cairo`, `pickup_enabled`, private contact phone and private location. Delivery zones are rows in `kitchen_zone`.

| State | Who sets it | New sales | Accepting pending orders | Fulfilling already accepted orders |
|---|---|---|---|---|
| `ACTIVE` | Approval, cook resume, staff restore | Yes | Yes | Yes |
| `PAUSED` | Cook pause, or staff | No | Yes | Yes |
| `SUSPENDED` | Staff only (`cook.review` + MFA, reason) | No | **No** | Yes |
| `CLOSED` | Reserved; no command sets it in the services read for this page | No | No | No |

(From `KitchenDirectory.KitchenStatus.canAcceptOrders` / `canFulfillAcceptedOrders` in `backend/modules/cooks/src/main/java/com/matbakhy/cooks/api/KitchenDirectory.java` and `KitchenService`.)

Commands: staff `PAUSE`, `SUSPEND`, `RESTORE` (API-070); cook pause and resume through `PATCH /api/v1/cook/settings` (API-065). A pause set by staff cannot be undone by the cook (E2E-COOK-107). Staff cannot change their own kitchen's state (audited, `KitchenService`). A suspended or unapproved cook's kitchen is invisible to customers and cannot be quoted (E2E-DISC-104); suspension stops new sales and acceptance but lets accepted orders finish (E2E-COOK-050).

## 3. Kitchen settings

The cook edits only their own kitchen (`KitchenService`, API-065): description, whether pickup is offered, delivery zones **among the approved zones** (`ZONE_NOT_APPROVED` otherwise), and pause/resume. Stale edits are refused by version. Settings must keep the kitchen serviceable (E2E-COOK-112). Payment settings are separate (section 6).

## 4. Meals

State machine (`MealService`, status column on `meal`): `DRAFT → PUBLISHED ↔ PAUSED → ARCHIVED`, plus an orthogonal staff moderation flag `VISIBLE`/`HIDDEN`.

- **Content** is versioned: every edit creates an immutable `meal_revision` snapshot; quotes and orders reference the revision (see [pricing](pricing-and-payments.md#price-versioning-and-snapshots)).
- **Publishing** (API-049) validates that these are present: Arabic title, Arabic description, Arabic portion text, Arabic ingredients, an explicit allergen declaration, a price in 1..1,000,000 piastres and at least one **approved** photo (max 6). Every missing field is named in the error (owner case E2E-COOK-101).
- **Photos** go through the upload pipeline: type allowlist (JPEG, PNG), size limits (8 MB, minimum 320 px side), content sniffing, quarantine, malware-scan adapter; only approved media can be attached (`MediaPolicy`, `MediaService`; E2E-COOK-102). Cook documents are separate, private, and never get public URLs (BR-010).
- **Pause/archive** removes the meal from sale without touching history (E2E-COOK-104). Archive never changes order snapshots.
- **Staff moderation** (`content.moderate`, API-072): hiding a meal with a reason removes it from sale immediately without altering the cook's draft/publication state; restore brings it back (E2E-OPS-056).
- **Price history** (API-099): active price plus previous actual price changes from the revisions (D14).
- Another cook's meal returns 404 (E2E-COOK-054).

Example: Um Ahmed raises "Stuffed vine leaves" from EGP 85.00 to EGP 95.00. A new revision is written. Customers with an unplaced quote at EGP 85.00 must re-accept the new total; existing orders stay at 85.00. The price-history view shows both changes with Cairo times.

## 5. Slots and capacity

A slot (`cook_slot`, V8) is a dated window with a total capacity; meals are attached with their own portion limits (`meal_slot`). Created by the cook in the operations web (E2E-COOK-052). Rules enforced by `SlotService`:

- local date and times are interpreted in `Africa/Cairo`; times that do not exist (spring-forward) are rejected, repeated times need an explicit offset (tests `a_local_time_skipped_by_the_spring_change_is_rejected`, `a_repeated_local_time_needs_an_explicit_offset_and_maps_to_one_instant`, `backend/app/src/test/java/com/matbakhy/app/availability/SlotIntegrationTest.java`); instants are stored in UTC;
- `lead_minutes` 30..1440, cutoff = start − lead and must still be in the future; `max_portions` 1..500; no overlap with the kitchen's other slots;
- meals must belong to the same kitchen (DB trigger `meal_slot_same_kitchen`);
- a slot can be opened only when it offers meals, and not after cutoff; capacity can never be reduced below reserved+allocated+consumed portions; after cutoff capacity edits are refused (E2E-COOK-105, E2E-COOK-106);
- a cook cannot cancel a slot that has order obligations; that needs the operations exception workflow (`SlotService`).

## 6. Payment settings

The cook sets `deposit_bps`, an optional `COD` cap and the deposit recipient (InstaPay, wallet or bank details) through API-102/103. A new or changed recipient is hidden from customers until finance staff approve it. Details: [pricing and payments](pricing-and-payments.md#3-payment-methods). Owner cases E2E-COOK-108, E2E-FIN-105.

## 7. Running orders

The order queue (API-053/054) shows the cook only their own kitchen's orders, with the minimum customer contact needed while the order is active (name, phone, address for delivery) and never the handover code. Actions offered per state (`OrderService.cookActions`): `ACCEPT`/`REJECT`; `PREPARE`/`CANCEL`; `READY`; `HANDOVER` (pickup) or `DISPATCH` (delivery); `HANDOVER`/`DELIVERY_FAILURE` while out for delivery. See [order lifecycle](order-lifecycle.md). After completion the cook reports collected cash (API-063); cooks see a statement (API-064, `StatementService`) and free insights (API-111: counts of impressions, views, quotes, orders by outcome, capacity, own sales; "never claims profit", E2E-COOK-109).

The dashboard (`CookDashboardService`, API-045) lists pending acceptance with deadlines, slot totals and active counts so the cook can act before the deadline.

## 8. Account deletion for a cook

A cook who requests account deletion is disabled at once, but is **held for staff review** instead of being erased, because open orders and money records must be preserved (E2E-COOK-110). Erasure itself is implemented for customers (`AccountErasureService`) and keeps financial audit integrity (BR-055); see [database architecture](../03-architecture/database-architecture.md).

## Known limitations and documentation gaps

- A kitchen has one owner account; multi-person kitchens and delegated kitchen staff do not exist.
- Nothing in the services read sets the `CLOSED` operating state; it exists in the schema and checks. NOT VERIFIED whether any admin path closes a kitchen.
- Specification text mentions "daily pause/cutoff" and "advanced business intelligence"; only slot cutoff, pause/resume and the basic insights are implemented.
- Pilot onboarding requirements (food-business licensing, documents, allergen process) are open owner decision O06; the application form fields are generic.
