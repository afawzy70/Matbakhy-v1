# Requirements-to-test traceability matrix

Manual owner cases per planning scenario (TC) and business rule (BR). `AUTOMATED_ONLY` = the scenario is verified by CI at a level an owner cannot perform by hand (see its level); `UNCOVERED` rows carry the reason.

## Scenarios

| TC | Level | Rule | Title | Owner cases | Coverage |
|---|---|---|---|---|---|
| TC-001 | integration | BR-001 | Identity: Only authenticated active accounts may perform private operations | CFG-001, CFG-002, CFG-003, CFG-004, CFG-005, CFG-006, CFG-101, CFG-102, CFG-008, CFG-011, CFG-012, CFG-014, CFG-016, CFG-030, CFG-033, CFG-034, CFG-120, E2E-OPS-062, E2E-DISC-003, E2E-ORDER-037, E2E-ORDER-120, E2E-FIN-112 | MANUAL |
| TC-002 | integration | BR-002 | Identity: Web mutations require CSRF protection and native auth uses authorization code with PKCE | CFG-002, CFG-003, CFG-102, CFG-008, CFG-030, CFG-036, CFG-037, E2E-DISC-003 | MANUAL |
| TC-003 | integration | BR-003 | Identity: First checkout requires verified email and verified normalized Egyptian phone | E2E-DISC-002, E2E-DISC-105 | MANUAL |
| TC-004 | integration | BR-004 | Identity: OTP is single use, hashed, valid 5 minutes, max 5 attempts | E2E-DISC-105 | MANUAL |
| TC-005 | integration | BR-005 | Address: Address ownership is enforced | E2E-DISC-007, E2E-DISC-106 | MANUAL |
| TC-006 | integration | BR-006 | Access: Every kitchen/order/case/list/count/export checks object scope | CFG-005, CFG-006, E2E-COOK-053, E2E-COOK-054, E2E-OPS-055, E2E-OPS-057, E2E-COOK-103, E2E-COOK-106, E2E-DISC-004, E2E-DISC-106, E2E-BASKET-027, E2E-ORDER-025, E2E-ORDER-030, E2E-ORDER-037, E2E-ORDER-113, E2E-ORDER-118, E2E-ORDER-119, E2E-ORDER-120, E2E-FIN-044, E2E-FIN-112, E2E-FIN-114 | MANUAL |
| TC-007 | integration | BR-007 | Access: Staff privileges are assigned server-side | CFG-005, CFG-101, CFG-011, CFG-012, CFG-016, CFG-018, CFG-019, CFG-020, CFG-021, CFG-104, CFG-105, CFG-106, E2E-COOK-049, E2E-OPS-055, E2E-OPS-058, E2E-OPS-059, E2E-OPS-060, E2E-OPS-062, E2E-OPS-101, E2E-OPS-102, E2E-OPS-108, E2E-OPS-109, E2E-OPS-110, E2E-OPS-111 | MANUAL |
| TC-008 | integration | BR-008 | Cook: Cook approval is required before selling | E2E-COOK-048, E2E-COOK-049, E2E-COOK-050, E2E-COOK-101, E2E-COOK-111, E2E-OPS-109, E2E-DISC-104 | MANUAL |
| TC-009 | integration | BR-009 | Cook: Cook pause stops new sales | E2E-COOK-050, E2E-OPS-056, E2E-COOK-107, E2E-COOK-112, E2E-DISC-005 | MANUAL |
| TC-010 | integration | BR-010 | Media: Public media must pass type/size/content checks and quarantine | E2E-COOK-051, E2E-COOK-053, E2E-COOK-102 | MANUAL |
| TC-011 | integration | BR-011 | Catalogue: Published meals require title, description, portion, positive price, ingredients, explicit allergen declaration and approved image/cook. | E2E-COOK-051, E2E-COOK-101 | MANUAL |
| TC-012 | integration | BR-012 | Catalogue: Meal updates are versioned | E2E-COOK-103, E2E-COOK-104 | MANUAL |
| TC-013 | integration | BR-013 | Availability: Capacity is bounded at both meal-slot and total cook-slot portions | E2E-COOK-106, E2E-BASKET-012, E2E-BASKET-017, E2E-ORDER-114, E2E-ORDER-115 | MANUAL |
| TC-014 | integration | BR-014 | Availability: Store instants in UTC and schedule in Africa/Cairo | E2E-COOK-052, E2E-COOK-105, E2E-BASKET-023 | MANUAL |
| TC-015 | integration | BR-015 | Availability: Slots require end after start and cutoff before start | E2E-COOK-105, E2E-BASKET-023 | MANUAL |
| TC-016 | integration | BR-016 | Discovery: Search/filter/sort/pagination execute on the server over all eligible data | E2E-DISC-001, E2E-DISC-006, E2E-DISC-101, E2E-DISC-102, E2E-DISC-103 | MANUAL |
| TC-017 | integration | BR-017 | Discovery: Only approved active kitchens and published serviceable meals are purchasable | E2E-DISC-001, E2E-DISC-005, E2E-DISC-104, E2E-DISC-107 | MANUAL |
| TC-018 | integration | BR-018 | Basket: the server rejects any basket or quote mixing cooks or slots | E2E-BASKET-009, E2E-BASKET-010, E2E-BASKET-021, E2E-ORDER-113 | MANUAL |
| TC-019 | integration | BR-019 | Pricing: Server calculates amounts in integer EGP minor units | E2E-BASKET-011, E2E-BASKET-019, E2E-BASKET-020 | MANUAL |
| TC-020 | integration | BR-020 | Pricing: Quote lasts 5 minutes, reserves no stock, snapshots versions and exposes complete cost/policy before confirmation. | E2E-BASKET-029, E2E-BASKET-023, E2E-BASKET-013, E2E-BASKET-014, E2E-BASKET-024, E2E-ORDER-111, E2E-ORDER-112 | MANUAL |
| TC-021 | integration | BR-021 | Fulfillment: Delivery requires active cook-zone coverage and complete own address | E2E-DISC-101, E2E-DISC-007, E2E-ORDER-102 | MANUAL |
| TC-022 | integration | BR-022 | Order: Placement atomically validates quote, reserves all stock, writes snapshots/idempotency/events/outbox and cash due record. | E2E-BASKET-015, E2E-BASKET-018, E2E-BASKET-028, E2E-ORDER-021, E2E-ORDER-036, E2E-ORDER-101, E2E-ORDER-121 | MANUAL |
| TC-023 | integration | BR-023 | Order: Permanent unique customer+clientRequestId prevents duplicate orders even after cached idempotency response expires. | E2E-ORDER-022, E2E-ORDER-024, E2E-ORDER-105, E2E-ORDER-106, E2E-ORDER-123 | MANUAL |
| TC-024 | integration | BR-024 | Order: Idempotency scope is actor+command+key | E2E-BASKET-028, E2E-ORDER-022, E2E-ORDER-023 | MANUAL |
| TC-025 | integration | BR-025 | Stock: Concurrent last-portion requests must serialize/check stock and never oversell either meal or kitchen totals. | E2E-BASKET-029, E2E-BASKET-012, E2E-BASKET-016, E2E-BASKET-017, E2E-BASKET-018 | MANUAL |
| TC-026 | integration | BR-026 | Lifecycle: Only legal actor-scoped transitions are accepted with expected version | E2E-ORDER-036 | MANUAL |
| TC-027 | integration | BR-027 | Lifecycle: Acceptance deadline is min(created+15m, slotStart-lead), with 5m minimum remaining at placement | E2E-ORDER-029 | MANUAL |
| TC-028 | integration | BR-028 | Cancellation: Customer can directly cancel pending orders only | E2E-ORDER-027, E2E-ORDER-035, E2E-ORDER-101, E2E-ORDER-109 | MANUAL |
| TC-029 | integration | BR-029 | Stock: Reject/expire/preparation-not-started cancellation releases once | E2E-BASKET-012, E2E-ORDER-026, E2E-ORDER-027, E2E-ORDER-028, E2E-ORDER-029, E2E-ORDER-109, E2E-ORDER-110, E2E-ORDER-114 | MANUAL |
| TC-030 | integration | BR-030 | Fulfillment: Pickup completes only from READY | E2E-ORDER-031 | MANUAL |
| TC-031 | integration | BR-031 | Fulfillment: Completion requires single-use customer handover code with bounded attempts | E2E-ORDER-030, E2E-ORDER-031, E2E-ORDER-032, E2E-ORDER-033, E2E-ORDER-101, E2E-ORDER-102, E2E-ORDER-108 | MANUAL |
| TC-032 | integration | BR-032 | Cash: Order completion and collection are independent | E2E-ORDER-031, E2E-ORDER-101, E2E-FIN-038, E2E-FIN-039, E2E-FIN-043, E2E-FIN-102 | MANUAL |
| TC-033 | integration | BR-033 | Finance: Commission rate/rounding are snapshot | E2E-ORDER-031, E2E-ORDER-121, E2E-FIN-045 | MANUAL |
| TC-034 | integration | BR-034 | Finance: Cook-collected cash creates commission receivable, not gross platform cash/revenue | E2E-FIN-045, E2E-FIN-046 | MANUAL |
| TC-035 | integration | BR-035 | Refund: Full refunds are bounded by verified collection less prior verified refunds | E2E-FIN-044 | MANUAL |
| TC-036 | integration | BR-036 | Settlement: Settlement lines are unique and reviewed batches immutable | E2E-FIN-110 | MANUAL |
| TC-037 | integration | BR-037 | Notification: Order/inbox persists despite notification-provider failure | E2E-DISC-108, E2E-ORDER-117 | MANUAL |
| TC-038 | integration | BR-038 | Notification: Use transactional outbox and recipient/event deduplication with leased workers | E2E-DISC-108, E2E-ORDER-117 | MANUAL |
| TC-039 | integration | BR-039 | Reviews: Only owning customer can review a COMPLETED order once, integer 1..5 | E2E-DISC-110, E2E-DISC-111 | MANUAL |
| TC-040 | integration | BR-040 | Favorites: Favorite/unfavorite is idempotent per user+meal | E2E-DISC-107 | MANUAL |
| TC-041 | integration | BR-041 | Support: Cases are order-scoped | E2E-OPS-057, E2E-OPS-105, E2E-ORDER-035, E2E-ORDER-109, E2E-ORDER-118, E2E-FIN-040 | MANUAL |
| TC-042 | integration | BR-042 | Configuration: Fee/rate/zone/policy changes are versioned and audited | CFG-101, CFG-011, CFG-015, CFG-109, CFG-033, E2E-OPS-061, E2E-OPS-106, E2E-OPS-107, E2E-BASKET-022, E2E-BASKET-024, E2E-ORDER-112, E2E-FIN-103 | MANUAL |
| TC-043 | integration | BR-043 | Access: Staff cannot self-escalate or remove last recoverable administrator | CFG-016, CFG-018, CFG-019, CFG-104, CFG-105, E2E-OPS-060, E2E-OPS-101, E2E-OPS-102 | MANUAL |
| TC-044 | browser | BR-044 | Web: Order submission uses stable key persisted before request | E2E-DISC-004, E2E-BASKET-013, E2E-BASKET-030, E2E-ORDER-022, E2E-ORDER-024, E2E-ORDER-105, E2E-ORDER-106 | MANUAL |
| TC-045 | browser | BR-045 | Web: Every critical screen has loading/empty/error/offline/denied/success states and an actionable recovery | — | GAP — No single case walks every critical screen through loading/empty/error/offline/denied/success; per-screen states appear inside other cases. To be written once the screen list is frozen. |
| TC-046 | browser | BR-046 | UX: Arabic RTL and English LTR work at phone/tablet/desktop widths, long text and mixed numbers | E2E-DISC-001, E2E-DISC-008 | MANUAL |
| TC-047 | browser | BR-047 | Accessibility: Keyboard, visible focus, labels/errors, dialog focus restoration and screen readers support every critical journey. | E2E-DISC-113 | MANUAL |
| TC-048 | native | BR-048 | Mobile: Use system-browser PKCE and OS secure storage | CFG-024, CFG-025, CFG-026, CFG-108, CFG-109, CFG-046, CFG-112, E2E-BASKET-027 | MANUAL |
| TC-049 | native | BR-049 | Mobile: Offline cannot submit orders or pretend stock is current | E2E-DISC-115, E2E-ORDER-107 | MANUAL |
| TC-050 | native | BR-050 | Mobile: Push permission optional | — | GAP — Native push permission, lifecycle push and accessibility need a physical-device case; none is authored yet (push delivery is an open owner decision, O07). |
| TC-051 | native | BR-051 | Mobile: Android acceptance requires native build/install/test evidence with version, device/OS and artifact checksum. | E2E-DISC-115 | MANUAL |
| TC-052 | native | BR-052 | Mobile: iOS acceptance requires native simulator/device and signing evidence | CFG-024, CFG-025, CFG-026, CFG-108, CFG-109, CFG-046, CFG-112, E2E-DISC-115 | MANUAL |
| TC-053 | integration | BR-053 | Security: Input is validated/encoded | CFG-038, E2E-BASKET-020 | MANUAL |
| TC-054 | integration | BR-054 | Security: Rate limits cover login/OTP/quote/order/upload/search | CFG-022, CFG-039, E2E-DISC-105 | MANUAL |
| TC-055 | integration | BR-055 | Privacy: Deletion requests revoke access and apply approved retention/anonymization across stores | E2E-COOK-110, E2E-DISC-114 | MANUAL |
| TC-056 | integration | BR-056 | Observability: Logs/metrics exclude tokens/OTP/contact/address/document content | CFG-040 | MANUAL |
| TC-057 | integration | BR-057 | Recovery: Backup is only accepted after restore test | CFG-028, CFG-032, CFG-041, CFG-042, CFG-043, CFG-044, CFG-118 | MANUAL |
| TC-058 | integration | BR-058 | Reliability: Jobs use leases and idempotent effects | E2E-ORDER-123, E2E-FIN-045, E2E-FIN-102 | MANUAL |
| TC-059 | integration | BR-059 | Performance: Measure documented load/hardware and preserve correctness | — | PROCESS_GATE — Load/performance measurement is an engineering release gate on documented hardware, not an owner click-through. |
| TC-060 | integration | BR-060 | Deployment: Dev/test/staging/production credentials and data are separate | CFG-001, CFG-023, CFG-027, CFG-028, CFG-029, CFG-030, CFG-031, CFG-032, CFG-034, CFG-040, CFG-041, CFG-042, CFG-045, CFG-115, CFG-118, CFG-119, CFG-121 | MANUAL |
| TC-061 | integration | BR-061 | Operations: Operators must rehearse late/failed/safety/refund/cash-variance cases and know escalation contacts before pilot. | E2E-FIN-043 | MANUAL |
| TC-062 | integration | BR-062 | Release: Required gate evidence is tied to exact source/build and cannot pass with skipped/failed/blocked mandatory checks. | — | PROCESS_GATE — Release, publication and pilot-closure gates are decided by evidence of builds and operations, not by an owner functional case. |
| TC-063 | integration | BR-063 | Release: Android and iOS publication is distinct from build/upload | — | PROCESS_GATE — Release, publication and pilot-closure gates are decided by evidence of builds and operations, not by an owner functional case. |
| TC-064 | integration | BR-064 | Operations: Pilot closure requires accepted surfaces, observed metrics and reconciled incidents | — | PROCESS_GATE — Release, publication and pilot-closure gates are decided by evidence of builds and operations, not by an owner functional case. |
| TC-101 | review |  | Reconcile scope and repository baseline acceptance | — | AUTOMATED_ONLY — Planning/toolchain/CI acceptance verified by the repository checks (blueprint integrity, Product CI); no owner action exists. |
| TC-102 | review |  | Freeze traceable MVP backlog acceptance | — | AUTOMATED_ONLY — Planning/toolchain/CI acceptance verified by the repository checks (blueprint integrity, Product CI); no owner action exists. |
| TC-103 | review |  | Prepare editable brand system acceptance | — | AUTOMATED_ONLY — Planning/toolchain/CI acceptance verified by the repository checks (blueprint integrity, Product CI); no owner action exists. |
| TC-104 | review |  | Produce reviewable UX package acceptance | — | AUTOMATED_ONLY — Planning/toolchain/CI acceptance verified by the repository checks (blueprint integrity, Product CI); no owner action exists. |
| TC-105 | system |  | Pin workspace and compatible toolchain acceptance | — | AUTOMATED_ONLY — Planning/toolchain/CI acceptance verified by the repository checks (blueprint integrity, Product CI); no owner action exists. |
| TC-106 | system |  | Establish database and module boundaries acceptance | — | AUTOMATED_ONLY — Planning/toolchain/CI acceptance verified by the repository checks (blueprint integrity, Product CI); no owner action exists. |
| TC-107 | system |  | Implement contract toolchain acceptance | — | AUTOMATED_ONLY — Planning/toolchain/CI acceptance verified by the repository checks (blueprint integrity, Product CI); no owner action exists. |
| TC-108 | system |  | Create local stack and required CI commands acceptance | — | AUTOMATED_ONLY — Planning/toolchain/CI acceptance verified by the repository checks (blueprint integrity, Product CI); no owner action exists. |
| TC-109 | system | BR-001, BR-002 | Integrate identity and sessions acceptance | CFG-002, CFG-004, E2E-OPS-058, E2E-DISC-002, E2E-DISC-003, E2E-DISC-004, E2E-BASKET-030 | MANUAL |
| TC-110 | system | BR-003, BR-004, BR-005 | Build profile address and phone verification acceptance | E2E-DISC-002, E2E-DISC-105, E2E-DISC-106, E2E-DISC-109 | MANUAL |
| TC-111 | system | BR-006, BR-007 | Enforce scoped permissions and auditing acceptance | CFG-104, E2E-COOK-054, E2E-OPS-055, E2E-OPS-058, E2E-OPS-059, E2E-OPS-102, E2E-OPS-110, E2E-OPS-111 | MANUAL |
| TC-112 | system | BR-008, BR-009 | Implement cook application workflow acceptance | E2E-COOK-048, E2E-COOK-049, E2E-COOK-111, E2E-OPS-108, E2E-OPS-109, E2E-DISC-104 | MANUAL |
| TC-113 | system | BR-010 | Implement safe media lifecycle acceptance | E2E-COOK-053, E2E-COOK-102 | MANUAL |
| TC-114 | system | BR-011, BR-012 | Implement meals and publication acceptance | E2E-COOK-051, E2E-COOK-101, E2E-COOK-104 | MANUAL |
| TC-115 | system | BR-013, BR-014, BR-015 | Implement dated slots and capacity acceptance | E2E-COOK-052, E2E-COOK-105, E2E-COOK-106, E2E-COOK-112, E2E-BASKET-023, E2E-ORDER-114 | MANUAL |
| TC-116 | system | BR-016, BR-017 | Implement serviceable discovery acceptance | E2E-DISC-001, E2E-DISC-005, E2E-DISC-006, E2E-DISC-101, E2E-DISC-102 | MANUAL |
| TC-117 | system | BR-018, BR-019, BR-020, BR-021 | Implement authoritative quotes acceptance | E2E-DISC-007, E2E-BASKET-021, E2E-BASKET-011, E2E-BASKET-013, E2E-BASKET-019, E2E-ORDER-021, E2E-ORDER-102, E2E-ORDER-111, E2E-ORDER-113, E2E-FIN-041, E2E-FIN-046 | MANUAL |
| TC-118 | system | BR-022, BR-023, BR-024, BR-025 | Implement atomic order placement acceptance | E2E-BASKET-029, E2E-BASKET-016, E2E-BASKET-018, E2E-ORDER-021, E2E-ORDER-022, E2E-ORDER-115, E2E-ORDER-123 | MANUAL |
| TC-119 | system | BR-026, BR-027, BR-028, BR-029, BR-030, BR-031 | Implement lifecycle and handover acceptance | E2E-ORDER-025, E2E-ORDER-026, E2E-ORDER-028, E2E-ORDER-029, E2E-ORDER-030, E2E-ORDER-032, E2E-ORDER-033, E2E-ORDER-034, E2E-ORDER-036, E2E-ORDER-101, E2E-ORDER-102, E2E-ORDER-108, E2E-ORDER-110, E2E-FIN-101, E2E-FIN-106 | MANUAL |
| TC-120 | system | BR-032, BR-033 | Implement cash and ledger core acceptance | E2E-OPS-103, E2E-FIN-038, E2E-FIN-043, E2E-FIN-045, E2E-FIN-102, E2E-FIN-111, E2E-FIN-114 | MANUAL |
| TC-121 | system | BR-037, BR-038 | Implement outbox and notifications acceptance | E2E-DISC-108, E2E-ORDER-025, E2E-ORDER-117 | MANUAL |
| TC-122 | system |  | Build cook workspace acceptance | E2E-COOK-052, E2E-COOK-054, E2E-COOK-107, E2E-OPS-103 | MANUAL |
| TC-123 | system |  | Build operations approval and moderation acceptance | E2E-COOK-049, E2E-COOK-050, E2E-COOK-053, E2E-OPS-056, E2E-OPS-104, E2E-OPS-112, E2E-DISC-104 | MANUAL |
| TC-124 | system | BR-041 | Build support and order exceptions acceptance | E2E-OPS-057, E2E-OPS-103, E2E-OPS-105, E2E-ORDER-034, E2E-ORDER-035, E2E-ORDER-101, E2E-ORDER-118, E2E-ORDER-119 | MANUAL |
| TC-125 | system | BR-034, BR-035, BR-036 | Build finance operations acceptance | E2E-FIN-112 | MANUAL |
| TC-126 | system | BR-042, BR-043 | Build operating configuration and staff controls acceptance | CFG-105, CFG-106, E2E-OPS-055, E2E-OPS-059, E2E-OPS-060, E2E-OPS-061, E2E-OPS-101, E2E-OPS-106, E2E-OPS-107, E2E-BASKET-022 | MANUAL |
| TC-127 | system | BR-044, BR-045 | Complete customer web purchase flow acceptance | E2E-OPS-103, E2E-BASKET-030 | MANUAL |
| TC-128 | system | BR-039, BR-040 | Complete customer account and trust features acceptance | E2E-OPS-104, E2E-DISC-008, E2E-DISC-107, E2E-DISC-108, E2E-DISC-109, E2E-DISC-110, E2E-DISC-111, E2E-DISC-114 | MANUAL |
| TC-129 | system | BR-046, BR-047 | Verify accessibility RTL and responsive behavior acceptance | E2E-DISC-113 | MANUAL |
| TC-130 | system |  | Automate complete web and backoffice journeys acceptance | — | PROCESS_GATE — Automating these journeys is an engineering story; this catalog is its specification. |
| TC-131 | system | BR-048 | Build native shell and authentication acceptance | CFG-024, E2E-BASKET-027 | MANUAL |
| TC-132 | system | BR-049 | Implement native discovery and ordering acceptance | E2E-DISC-115, E2E-BASKET-026, E2E-ORDER-107, E2E-ORDER-122, E2E-FIN-113 | MANUAL |
| TC-133 | system | BR-050 | Implement native lifecycle push and accessibility acceptance | — | GAP — Native push permission, lifecycle push and accessibility need a physical-device case; none is authored yet (push delivery is an open owner decision, O07). |
| TC-134 | system | BR-051 | Verify Android native release candidate acceptance | E2E-DISC-115, E2E-BASKET-026 | MANUAL |
| TC-135 | system | BR-052 | Verify iOS native release candidate acceptance | E2E-DISC-115, E2E-BASKET-026 | MANUAL |
| TC-136 | system | BR-053, BR-054 | Complete adversarial security verification acceptance | E2E-OPS-062, E2E-BASKET-020 | MANUAL |
| TC-137 | system | BR-058 | Prove concurrency retries and recovery acceptance | E2E-BASKET-016, E2E-BASKET-017 | MANUAL |
| TC-138 | system | BR-059 | Measure capacity and performance budgets acceptance | E2E-ORDER-115 | MANUAL |
| TC-139 | system | BR-055, BR-056, BR-057 | Implement privacy observability and recovery tooling acceptance | E2E-OPS-110, E2E-DISC-114 | MANUAL |
| TC-140 | system | BR-060 | Deploy controlled staging environment acceptance | CFG-001, CFG-027, CFG-028, CFG-034, CFG-038, CFG-041, CFG-044, CFG-045, CFG-121 | MANUAL |
| TC-141 | system | BR-061 | Rehearse business operations acceptance | — | PROCESS_GATE — Release, publication and pilot-closure gates are decided by evidence of builds and operations, not by an owner functional case. |
| TC-142 | system |  | Perform owner UAT and corrective cycle acceptance | — | OWNER_UAT — Executing this suite with retained evidence IS the owner UAT; the TC closes only from recorded runs. |
| TC-143 | system | BR-062 | Assemble release candidate and gate evidence acceptance | — | PROCESS_GATE — Release, publication and pilot-closure gates are decided by evidence of builds and operations, not by an owner functional case. |
| TC-144 | system |  | Execute authorized production pilot release acceptance | — | PROCESS_GATE — Release, publication and pilot-closure gates are decided by evidence of builds and operations, not by an owner functional case. |
| TC-145 | system | BR-063 | Submit authorized mobile store releases acceptance | CFG-046 | MANUAL |
| TC-146 | system | BR-064 | Observe pilot and close MVP acceptance | — | PROCESS_GATE — Release, publication and pilot-closure gates are decided by evidence of builds and operations, not by an owner functional case. |
| TC-147 | browser | BR-018 | Basket replacement confirmation in the customer web | E2E-BASKET-009, E2E-BASKET-010 | MANUAL |
| TC-148 | native | BR-018 | Basket replacement confirmation in the native apps | E2E-BASKET-026, E2E-BASKET-027 | MANUAL |
| TC-149 | browser | BR-018, BR-019 | Customer web basket editing with an estimated subtotal (CW05; QA finding FG-001) | E2E-BASKET-009 | MANUAL |
| TC-150 | component | BR-046 | Language switch loads catalogue content in the new language (QA finding MBQA-001) | E2E-DISC-008 | MANUAL |
| TC-151 | component | BR-013, BR-017 | An exhausted selected slot cannot be added while another slot has stock (QA finding MBQA-002) | E2E-BASKET-009, E2E-BASKET-012 | MANUAL |
| TC-152 | integration | BR-019 | Quantity controls follow the server's published per-line limit (Q1 option A, API-001) | E2E-BASKET-011, E2E-BASKET-024 | MANUAL |
| TC-153 | integration | BR-012 | Kitchen price history shows the active price and actual previous changes (D14) | E2E-COOK-103, E2E-BASKET-014, E2E-BASKET-015 | MANUAL |
| TC-154 | system | BR-019, BR-020 | Changed prices need informed customer reconfirmation before placement (D13) | E2E-OPS-106, E2E-BASKET-014, E2E-BASKET-022, E2E-BASKET-026 | MANUAL |
| TC-155 | integration | BR-019, BR-020 | Concurrent price update and placement are serialized (D13) | E2E-BASKET-014 | MANUAL |
| TC-156 | integration | BR-012, BR-020 | Committed orders keep their prices; lost responses recover at the original price (D13) | E2E-BASKET-015, E2E-ORDER-112, E2E-ORDER-121 | MANUAL |
| TC-157 | system | BR-033, BR-019 | No commission or platform fee anywhere at launch (D10) | E2E-COOK-108, E2E-OPS-103, E2E-OPS-107, E2E-DISC-117, E2E-BASKET-019, E2E-ORDER-021, E2E-ORDER-031, E2E-FIN-038, E2E-FIN-039, E2E-FIN-045, E2E-FIN-046, E2E-FIN-047, E2E-FIN-110, E2E-FIN-111 | MANUAL |
| TC-158 | integration | BR-019, BR-022 | Deposit-required quote and immutable snapshot (D16) | E2E-COOK-108, E2E-FIN-039, E2E-FIN-041, E2E-FIN-101, E2E-FIN-103, E2E-FIN-104, E2E-FIN-105 | MANUAL |
| TC-159 | integration | BR-026 | Customer transfer report is not payment; no preparation before kitchen receipt and acceptance (D16) | E2E-FIN-039, E2E-FIN-040, E2E-FIN-042, E2E-FIN-101, E2E-FIN-106, E2E-FIN-108 | MANUAL |
| TC-160 | integration | BR-027, BR-029 | Deposit expiry after a reported transfer reconciles instead of closing silently (D16) | E2E-FIN-107, E2E-FIN-115 | MANUAL |
| TC-161 | integration | BR-026 | Kitchen reports not received or a mismatched amount (D16) | E2E-OPS-108, E2E-FIN-040, E2E-FIN-041, E2E-FIN-108, E2E-FIN-109, E2E-FIN-115 | MANUAL |
| TC-162 | integration | BR-035 | Kitchen cannot fulfil after reported receipt: refund obligation (D16) | E2E-FIN-107, E2E-FIN-115 | MANUAL |
| TC-163 | system | BR-019 | Deposit checkout and honest payment status on web, Android and iOS (D16) | E2E-COOK-108, E2E-BASKET-019, E2E-ORDER-122, E2E-FIN-039, E2E-FIN-042, E2E-FIN-047, E2E-FIN-101, E2E-FIN-103, E2E-FIN-104, E2E-FIN-105, E2E-FIN-107, E2E-FIN-113 | MANUAL |
| TC-164 | integration | BR-019 | Organic activity metrics are deduplicated, private and never claim profit (D17) | E2E-COOK-109, E2E-DISC-112 | MANUAL |

## Business rules

| BR | Domain | Owner cases |
|---|---|---|
| BR-001 | Identity | CFG-001, CFG-002, CFG-003, CFG-004, CFG-005, CFG-006, CFG-101, CFG-102, CFG-008, CFG-011, CFG-012, CFG-014, CFG-016, CFG-030, CFG-034, CFG-120, E2E-OPS-058, E2E-OPS-062, E2E-DISC-002, E2E-DISC-003, E2E-DISC-004, E2E-DISC-114, E2E-BASKET-030, E2E-ORDER-037, E2E-ORDER-120, E2E-FIN-112 |
| BR-002 | Identity | CFG-002, CFG-003, CFG-102, CFG-008, CFG-030, CFG-036, CFG-037, E2E-OPS-062, E2E-DISC-003 |
| BR-003 | Identity | E2E-DISC-002, E2E-DISC-105, E2E-DISC-109 |
| BR-004 | Identity | E2E-DISC-105 |
| BR-005 | Address | E2E-DISC-007, E2E-DISC-106 |
| BR-006 | Access | CFG-005, CFG-006, CFG-025, E2E-COOK-053, E2E-COOK-054, E2E-OPS-055, E2E-OPS-057, E2E-COOK-103, E2E-OPS-103, E2E-DISC-004, E2E-DISC-106, E2E-DISC-109, E2E-BASKET-027, E2E-ORDER-037, E2E-ORDER-119, E2E-ORDER-120, E2E-FIN-044, E2E-FIN-112, E2E-FIN-114 |
| BR-007 | Access | CFG-005, CFG-101, CFG-102, CFG-011, CFG-012, CFG-016, CFG-018, CFG-019, CFG-020, CFG-021, CFG-104, CFG-105, CFG-106, E2E-COOK-049, E2E-OPS-055, E2E-OPS-058, E2E-OPS-059, E2E-OPS-060, E2E-OPS-062, E2E-OPS-101, E2E-OPS-102, E2E-OPS-108, E2E-OPS-110, E2E-OPS-111 |
| BR-008 | Cook | E2E-COOK-048, E2E-COOK-049, E2E-COOK-051, E2E-COOK-053, E2E-COOK-054, E2E-COOK-111, E2E-OPS-108, E2E-OPS-109, E2E-DISC-104 |
| BR-009 | Cook | E2E-COOK-050, E2E-OPS-056, E2E-COOK-107, E2E-COOK-112, E2E-OPS-108, E2E-DISC-005, E2E-DISC-104 |
| BR-010 | Media | E2E-COOK-051, E2E-COOK-053, E2E-COOK-102 |
| BR-011 | Catalogue | E2E-COOK-051, E2E-OPS-056, E2E-COOK-101 |
| BR-012 | Catalogue | E2E-COOK-101, E2E-COOK-103, E2E-COOK-104, E2E-BASKET-014, E2E-BASKET-015, E2E-ORDER-112, E2E-ORDER-121 |
| BR-013 | Availability | E2E-COOK-052, E2E-COOK-105, E2E-COOK-106, E2E-BASKET-012, E2E-BASKET-016, E2E-BASKET-017, E2E-ORDER-114, E2E-ORDER-115 |
| BR-014 | Availability | E2E-COOK-052, E2E-COOK-105, E2E-BASKET-023 |
| BR-015 | Availability | E2E-COOK-052, E2E-COOK-105, E2E-BASKET-023 |
| BR-016 | Discovery | E2E-DISC-001, E2E-DISC-006, E2E-DISC-101, E2E-DISC-102, E2E-DISC-103 |
| BR-017 | Discovery | E2E-DISC-001, E2E-DISC-005, E2E-DISC-104, E2E-DISC-107, E2E-BASKET-012 |
| BR-018 | Basket | E2E-BASKET-009, E2E-BASKET-010, E2E-BASKET-021, E2E-BASKET-030, E2E-BASKET-026, E2E-ORDER-113 |
| BR-019 | Pricing | E2E-COOK-108, E2E-COOK-109, E2E-DISC-112, E2E-BASKET-009, E2E-BASKET-021, E2E-BASKET-011, E2E-BASKET-014, E2E-BASKET-019, E2E-BASKET-020, E2E-BASKET-022, E2E-BASKET-024, E2E-ORDER-021, E2E-ORDER-113, E2E-ORDER-122, E2E-FIN-039, E2E-FIN-041, E2E-FIN-042, E2E-FIN-046, E2E-FIN-047, E2E-FIN-101, E2E-FIN-103, E2E-FIN-104, E2E-FIN-105, E2E-FIN-113 |
| BR-020 | Pricing | E2E-OPS-106, E2E-BASKET-011, E2E-BASKET-029, E2E-BASKET-023, E2E-BASKET-013, E2E-BASKET-014, E2E-BASKET-019, E2E-BASKET-022, E2E-BASKET-024, E2E-ORDER-021, E2E-ORDER-111, E2E-ORDER-112, E2E-FIN-103 |
| BR-021 | Fulfillment | E2E-DISC-101, E2E-DISC-007, E2E-BASKET-019, E2E-ORDER-102, E2E-ORDER-113, E2E-FIN-101 |
| BR-022 | Order | E2E-BASKET-015, E2E-BASKET-018, E2E-BASKET-028, E2E-ORDER-021, E2E-ORDER-101, E2E-FIN-039 |
| BR-023 | Order | E2E-OPS-109, E2E-ORDER-022, E2E-ORDER-024, E2E-ORDER-105, E2E-ORDER-106, E2E-ORDER-123 |
| BR-024 | Order | E2E-BASKET-028, E2E-ORDER-022, E2E-ORDER-023, E2E-ORDER-123, E2E-FIN-042, E2E-FIN-102 |
| BR-025 | Stock | E2E-BASKET-029, E2E-BASKET-016, E2E-BASKET-017, E2E-BASKET-018, E2E-ORDER-114, E2E-ORDER-115, E2E-ORDER-123 |
| BR-026 | Lifecycle | E2E-OPS-103, E2E-ORDER-025, E2E-ORDER-026, E2E-ORDER-028, E2E-ORDER-030, E2E-ORDER-036, E2E-ORDER-101, E2E-ORDER-121, E2E-FIN-039, E2E-FIN-040, E2E-FIN-106, E2E-FIN-108, E2E-FIN-109 |
| BR-027 | Lifecycle | E2E-BASKET-023, E2E-ORDER-021, E2E-ORDER-025, E2E-ORDER-028, E2E-ORDER-029, E2E-FIN-107, E2E-FIN-115 |
| BR-028 | Cancellation | E2E-ORDER-027, E2E-ORDER-035, E2E-ORDER-109, E2E-ORDER-110 |
| BR-029 | Stock | E2E-BASKET-012, E2E-ORDER-025, E2E-ORDER-026, E2E-ORDER-027, E2E-ORDER-028, E2E-ORDER-029, E2E-ORDER-030, E2E-ORDER-034, E2E-ORDER-109, E2E-ORDER-110, E2E-ORDER-114, E2E-FIN-107, E2E-FIN-115 |
| BR-030 | Fulfillment | E2E-ORDER-032, E2E-ORDER-101, E2E-ORDER-102, E2E-FIN-101 |
| BR-031 | Fulfillment | E2E-ORDER-030, E2E-ORDER-031, E2E-ORDER-032, E2E-ORDER-033, E2E-ORDER-101, E2E-ORDER-102, E2E-ORDER-108 |
| BR-032 | Cash | E2E-OPS-103, E2E-ORDER-031, E2E-ORDER-034, E2E-ORDER-101, E2E-FIN-038, E2E-FIN-039, E2E-FIN-043, E2E-FIN-045, E2E-FIN-102, E2E-FIN-111, E2E-FIN-114 |
| BR-033 | Finance | E2E-COOK-108, E2E-OPS-103, E2E-OPS-107, E2E-ORDER-031, E2E-ORDER-121, E2E-FIN-038, E2E-FIN-045, E2E-FIN-046, E2E-FIN-110, E2E-FIN-111 |
| BR-034 | Finance | E2E-FIN-045 |
| BR-035 | Refund | E2E-FIN-044, E2E-FIN-107 |
| BR-036 | Settlement | E2E-FIN-110 |
| BR-037 | Notification | E2E-DISC-108, E2E-ORDER-117 |
| BR-038 | Notification | E2E-DISC-108, E2E-ORDER-117 |
| BR-039 | Reviews | E2E-OPS-104, E2E-DISC-110, E2E-DISC-111 |
| BR-040 | Favorites | E2E-OPS-104, E2E-DISC-107 |
| BR-041 | Support | E2E-OPS-057, E2E-OPS-105, E2E-ORDER-035, E2E-ORDER-109, E2E-ORDER-118, E2E-ORDER-119, E2E-FIN-040 |
| BR-042 | Configuration | CFG-101, CFG-011, CFG-015, CFG-109, CFG-033, E2E-OPS-061, E2E-OPS-106, E2E-OPS-107, E2E-BASKET-022, E2E-BASKET-024 |
| BR-043 | Access | CFG-016, CFG-018, CFG-104, CFG-105, E2E-OPS-059, E2E-OPS-060, E2E-OPS-101, E2E-OPS-102 |
| BR-044 | Web | E2E-DISC-004, E2E-BASKET-013, E2E-ORDER-024, E2E-ORDER-105, E2E-ORDER-106, E2E-ORDER-107 |
| BR-045 | Web | E2E-DISC-003, E2E-DISC-117 |
| BR-046 | UX | E2E-DISC-001, E2E-DISC-008 |
| BR-047 | Accessibility | E2E-DISC-113 |
| BR-048 | Mobile | CFG-024, CFG-025, CFG-026, CFG-108, CFG-109, CFG-046, CFG-112, E2E-BASKET-027 |
| BR-049 | Mobile | E2E-DISC-115, E2E-BASKET-026, E2E-BASKET-027, E2E-ORDER-107, E2E-ORDER-122, E2E-FIN-113 |
| BR-050 | Mobile | — |
| BR-051 | Mobile | E2E-DISC-115, E2E-BASKET-026 |
| BR-052 | Mobile | CFG-024, CFG-046, CFG-112, E2E-DISC-115, E2E-BASKET-026 |
| BR-053 | Security | CFG-038, E2E-BASKET-020 |
| BR-054 | Security | CFG-022, CFG-039, E2E-DISC-105 |
| BR-055 | Privacy | E2E-COOK-110, E2E-DISC-114 |
| BR-056 | Observability | CFG-040, E2E-OPS-110 |
| BR-057 | Recovery | CFG-028, CFG-032, CFG-041, CFG-042, CFG-043, CFG-044, CFG-118 |
| BR-058 | Reliability | — |
| BR-059 | Performance | — |
| BR-060 | Deployment | CFG-001, CFG-023, CFG-027, CFG-028, CFG-029, CFG-031, CFG-032, CFG-034, CFG-040, CFG-041, CFG-042, CFG-045, CFG-115, CFG-119, CFG-121 |
| BR-061 | Operations | — |
| BR-062 | Release | — |
| BR-063 | Release | — |
| BR-064 | Operations | — |

## Case to requirement (reverse)

| Case | Scenarios | Rules | Decisions | Operations |
|---|---|---|---|---|
| CFG-001 | TC-001, TC-060, TC-140 | BR-001, BR-060 |  | deploy.sh smoke, deploy/web-preview/Caddyfile id. site block |
| CFG-002 | TC-001, TC-002, TC-109 | BR-001, BR-002 |  | getMe, POST /api/v1/auth/logout, GET /oauth2/authorization/keycloak |
| CFG-003 | TC-001, TC-002 | BR-001, BR-002 |  | GET /oauth2/authorization/{registrationId}, GET /login/oauth2/code/{registrationId} |
| CFG-004 | TC-001, TC-109 | BR-001 |  | getMe |
| CFG-005 | TC-001, TC-006, TC-007 | BR-001, BR-006, BR-007 |  | getOpsStaff, getOpsSecurityMfaPolicy |
| CFG-006 | TC-001, TC-006 | BR-001, BR-006 |  | getMe |
| CFG-101 | TC-001, TC-007, TC-042 | BR-001, BR-007, BR-042 | D18 | getMeSecurity, putOpsSecurityMfaPolicyByCategory, getOpsSecurityMfaPolicy, patchMe |
| CFG-102 | TC-001, TC-002 | BR-001, BR-002, BR-007 | D18 | getMeSecurity, GET /oauth2/authorization/keycloak?stepUp=true |
| CFG-008 | TC-001, TC-002 | BR-001, BR-002 | D18 | putMeSecurityMfa, getMeSecurity |
| CFG-011 | TC-001, TC-007, TC-042 | BR-001, BR-007, BR-042 | D18 | putOpsSecurityMfaPolicyByCategory, putMeSecurityMfa, patchMe |
| CFG-012 | TC-001, TC-007 | BR-001, BR-007 | D18 | putMeSecurityMfa, putOpsSecurityMfaPolicyByCategory |
| CFG-014 | TC-001 | BR-001 | D18 | getMeSecurity |
| CFG-015 | TC-042 | BR-042 | D18 | putOpsSecurityMfaPolicyByCategory |
| CFG-016 | TC-001, TC-007, TC-043 | BR-001, BR-007, BR-043 | D18 | getOpsSecurityMfaPolicy, putOpsSecurityMfaPolicyByCategory |
| CFG-018 | TC-007, TC-043 | BR-007, BR-043 | D18 | postOpsAccountsMfaReset |
| CFG-019 | TC-007, TC-043 | BR-007 | D18 | postOpsAccountsMfaReset |
| CFG-020 | TC-007 | BR-007 | D18 | postOpsAccountsMfaReset |
| CFG-021 | TC-007 | BR-007 | D18 | postOpsAccountsMfaReset, patchMe |
| CFG-022 | TC-054 | BR-054 | D18 | GET /oauth2/authorization/keycloak?stepUp=true |
| CFG-023 | TC-060 | BR-060 | D18 | postOpsAccountsMfaReset, putMeSecurityMfa |
| CFG-104 | TC-007, TC-043, TC-111 | BR-007, BR-043 |  | postOpsStaffGrants, getOpsStaff |
| CFG-105 | TC-007, TC-043, TC-126 | BR-007, BR-043 |  | postOpsStaffGrants, postOpsStaffGrantRevocations |
| CFG-106 | TC-007, TC-126 | BR-007 |  | postOpsStaffGrants, getMeSecurity |
| CFG-024 | TC-048, TC-052, TC-131 | BR-048, BR-052 | D18 | putMeSecurityMfa, POST /api/v1/orders |
| CFG-025 | TC-048, TC-052 | BR-048, BR-006 | D18 | getMe |
| CFG-026 | TC-048, TC-052 | BR-048 |  | getMe, POST /api/v1/orders |
| CFG-108 | TC-048, TC-052 | BR-048 | D18 | getMeSecurity |
| CFG-109 | TC-048, TC-052, TC-042 | BR-048, BR-042 | D18 | postOpsAccountsMfaReset, putOpsSecurityMfaPolicyByCategory |
| CFG-027 | TC-060, TC-140 | BR-060 | D18 | ./migrate-mfa.sh status |
| CFG-028 | TC-057, TC-060, TC-140 | BR-057, BR-060 | D18 | ./migrate-mfa.sh apply, ./migrate-mfa.sh verify |
| CFG-029 | TC-060 | BR-060 | D18 | ./migrate-mfa.sh apply |
| CFG-030 | TC-001, TC-002, TC-060 | BR-001, BR-002 | D18 | ./migrate-mfa.sh verify |
| CFG-031 | TC-060 | BR-060 | D18 | ./migrate-mfa.sh rollback |
| CFG-032 | TC-060, TC-057 | BR-057, BR-060 | D18 | ./migrate-mfa.sh apply |
| CFG-033 | TC-001, TC-042 | BR-042 | D18 | getOpsSecurityMfaPolicy, getMeSecurity |
| CFG-034 | TC-001, TC-060, TC-140 | BR-001, BR-060 |  | deploy.sh smoke (SMOKE_OPS=skip), deploy/web-preview/Caddyfile staff_access / customer_access |
| CFG-036 | TC-002 | BR-002 |  | patchMe, SecurityConfiguration corsConfigurationSource |
| CFG-037 | TC-002 | BR-002 |  | SecurityConfiguration sessionCookie / csrfTokenRepository |
| CFG-038 | TC-053, TC-140 | BR-053 |  | docs/security/csp-rollout.md, deploy/web-preview/Caddyfile (web_csp, headers) |
| CFG-039 | TC-054 | BR-054 |  | postMePhoneChallenges |
| CFG-040 | TC-056, TC-060 | BR-056, BR-060 |  | scripts/check_secrets.sh |
| CFG-041 | TC-060, TC-140, TC-057 | BR-060, BR-057 |  | gh workflow deploy-preview.yml, ./deploy.sh up, ./deploy.sh smoke |
| CFG-042 | TC-060, TC-057 | BR-060, BR-057 |  | ./deploy.sh up, ./deploy.sh rollback-plan |
| CFG-043 | TC-057 | BR-057 |  | ./deploy.sh backup, ./deploy.sh restore-db |
| CFG-044 | TC-057, TC-140 | BR-057 |  | ./deploy.sh status, ./deploy.sh smoke |
| CFG-045 | TC-060, TC-140 | BR-060 |  | deploy/web-preview/smoke.sh (config reports PREVIEW demo true) |
| CFG-046 | TC-048, TC-052, TC-145 | BR-048, BR-052 |  | getMe, GET /api/v1/meals |
| CFG-112 | TC-048, TC-052 | BR-048, BR-052 | D18 | putMeSecurityMfa |
| CFG-115 | TC-060 | BR-060 | D18 | ./migrate-mfa.sh fixture-clean |
| CFG-118 | TC-057, TC-060 | BR-057 |  | ./deploy.sh rollback-plan |
| CFG-119 | TC-060 | BR-060 |  | ./deploy.sh e2e-reset |
| CFG-120 | TC-001 | BR-001 | D18 | getMeSecurity |
| CFG-121 | TC-140, TC-060 | BR-060 |  | ./deploy.sh smoke |
| E2E-COOK-048 | TC-008, TC-112 | BR-008 |  | GET /api/v1/cook/application, PUT /api/v1/cook/application, POST /api/v1/cook/application/submit, GET /api/v1/cook/dashboard |
| E2E-COOK-049 | TC-008, TC-112, TC-123, TC-007 | BR-008, BR-007 | D18 | POST /api/v1/ops/cooks/{applicationId}/decision, GET /api/v1/ops/cooks, GET /api/v1/ops/cooks/{applicationId} |
| E2E-COOK-050 | TC-009, TC-123, TC-008 | BR-009 |  | POST /api/v1/ops/kitchens/{kitchenId}/operating-state, POST /api/v1/cook/orders/{orderId}/accept, POST /api/v1/quotes |
| E2E-COOK-051 | TC-011, TC-114, TC-010 | BR-011, BR-010, BR-008 |  | POST /api/v1/cook/meals, POST /api/v1/cook/meals/{mealId}/publication, POST /api/v1/uploads, POST /api/v1/uploads/{uploadId}/complete, GET /api/v1/meals/{mealId} |
| E2E-COOK-052 | TC-115, TC-122, TC-014 | BR-013, BR-014, BR-015 |  | POST /api/v1/cook/slots, PATCH /api/v1/cook/slots/{slotId}, GET /api/v1/kitchens/{kitchenId}/slots |
| E2E-COOK-053 | TC-010, TC-113, TC-123, TC-006 | BR-010, BR-006, BR-008 |  | POST /api/v1/uploads, POST /api/v1/uploads/{uploadId}/complete, GET /api/v1/uploads/{uploadId} |
| E2E-COOK-054 | TC-006, TC-111, TC-122 | BR-006, BR-008 |  | PATCH /api/v1/cook/meals/{mealId}, POST /api/v1/cook/meals/{mealId}/publication, PATCH /api/v1/cook/slots/{slotId}, GET /api/v1/cook/orders/{orderId}, GET /api/v1/uploads/{uploadId} |
| E2E-OPS-055 | TC-007, TC-006, TC-111, TC-126 | BR-006, BR-007 |  | GET /api/v1/ops/cooks, GET /api/v1/ops/catalogue, GET /api/v1/ops/cases, GET /api/v1/ops/orders, GET /api/v1/ops/finance/statements, GET /api/v1/ops/payments, GET /api/v1/ops/configuration, GET /api/v1/ops/staff, GET /api/v1/ops/audit, POST /api/v1/ops/kitchens/{kitchenId}/operating-state |
| E2E-OPS-056 | TC-123, TC-009 | BR-009, BR-011 |  | GET /api/v1/ops/catalogue, POST /api/v1/ops/meals/{mealId}/moderation |
| E2E-OPS-057 | TC-041, TC-124, TC-006 | BR-041, BR-006 |  | GET /api/v1/ops/cases, GET /api/v1/ops/cases/{caseId}, POST /api/v1/ops/cases/{caseId}/messages, PATCH /api/v1/ops/cases/{caseId}, POST /api/v1/support/cases |
| E2E-OPS-058 | TC-007, TC-109, TC-111 | BR-007, BR-001 |  | PATCH /api/v1/me, POST /api/v1/ops/staff/{userId}/grants, GET /api/v1/ops/staff |
| E2E-OPS-059 | TC-007, TC-111, TC-126 | BR-007, BR-043 | D18 | POST /api/v1/ops/staff/{userId}/grants, POST /api/v1/ops/staff/{userId}/grant-revocations, GET /api/v1/ops/audit |
| E2E-OPS-060 | TC-043, TC-126, TC-007 | BR-043, BR-007 |  | POST /api/v1/ops/staff/{userId}/grant-revocations |
| E2E-OPS-061 | TC-126, TC-042 | BR-042 |  | POST /api/v1/ops/configuration/{key}/versions, GET /api/v1/ops/configuration |
| E2E-OPS-062 | TC-001, TC-007, TC-136 | BR-001, BR-002, BR-007 |  | GET /api/v1/ops/staff, GET /api/v1/me, POST /api/v1/ops/cooks/{applicationId}/decision |
| E2E-COOK-101 | TC-011, TC-008, TC-114 | BR-011, BR-012 |  | POST /api/v1/cook/meals, PATCH /api/v1/cook/meals/{mealId}, POST /api/v1/cook/meals/{mealId}/publication |
| E2E-COOK-102 | TC-010, TC-113 | BR-010 |  | POST /api/v1/uploads, POST /api/v1/uploads/{uploadId}/complete |
| E2E-COOK-103 | TC-153, TC-012, TC-006 | BR-012, BR-006 | D14 | GET /api/v1/cook/meals/{mealId}/price-history, PATCH /api/v1/cook/meals/{mealId} |
| E2E-COOK-104 | TC-012, TC-114 | BR-012 |  | POST /api/v1/cook/meals/{mealId}/publication |
| E2E-COOK-105 | TC-015, TC-115, TC-014 | BR-013, BR-014, BR-015 |  | POST /api/v1/cook/slots |
| E2E-COOK-106 | TC-013, TC-115, TC-006 | BR-013 |  | PATCH /api/v1/cook/slots/{slotId}, POST /api/v1/orders |
| E2E-COOK-107 | TC-009, TC-122 | BR-009 |  | PATCH /api/v1/cook/settings, POST /api/v1/ops/kitchens/{kitchenId}/operating-state |
| E2E-COOK-108 | TC-158, TC-163, TC-157 | BR-019, BR-033 | D16, D10 | PUT /api/v1/cook/payment-profile, GET /api/v1/ops/payment-recipients, POST /api/v1/ops/kitchens/{kitchenId}/payment-recipient/approval, POST /api/v1/quotes |
| E2E-COOK-109 | TC-164 | BR-019 | D17 | GET /api/v1/cook/insights, POST /api/v1/activity |
| E2E-COOK-110 | TC-055 | BR-055 |  | POST /api/v1/me/deletion-requests |
| E2E-COOK-111 | TC-112, TC-008 | BR-008 |  | POST /api/v1/ops/cooks/{applicationId}/decision, PUT /api/v1/cook/application |
| E2E-COOK-112 | TC-009, TC-115 | BR-009 |  | PATCH /api/v1/cook/settings, GET /api/v1/cook/dashboard |
| E2E-OPS-101 | TC-043, TC-126, TC-007 | BR-043, BR-007 |  | POST /api/v1/ops/staff/{userId}/grants |
| E2E-OPS-102 | TC-007, TC-043, TC-111 | BR-007, BR-043 |  | POST /api/v1/ops/staff/{userId}/grant-revocations, POST /api/v1/ops/staff/{userId}/grants |
| E2E-OPS-103 | TC-157, TC-120, TC-124, TC-122, TC-127 | BR-032, BR-033, BR-026, BR-006 | D10 | GET /api/v1/ops/orders, GET /api/v1/ops/orders/{orderId}, GET /api/v1/ops/finance/statements, GET /api/v1/ops/audit |
| E2E-OPS-104 | TC-123, TC-128 | BR-039, BR-040 |  | GET /api/v1/ops/reviews, POST /api/v1/ops/reviews/{reviewId}/moderation, POST /api/v1/reviews/{reviewId}/reports |
| E2E-OPS-105 | TC-124, TC-041 | BR-041 |  | PATCH /api/v1/ops/cases/{caseId}, POST /api/v1/support/cases/{caseId}/messages |
| E2E-OPS-106 | TC-042, TC-126, TC-154 | BR-042, BR-020 | D13 | POST /api/v1/ops/configuration/{key}/versions, POST /api/v1/quotes, POST /api/v1/orders |
| E2E-OPS-107 | TC-157, TC-126, TC-042 | BR-042, BR-033 | D10 | POST /api/v1/ops/configuration/{key}/versions, GET /api/v1/ops/configuration |
| E2E-OPS-108 | TC-007, TC-112, TC-161 | BR-007, BR-008, BR-009 |  | POST /api/v1/ops/kitchens/{kitchenId}/operating-state, POST /api/v1/ops/cooks/{applicationId}/decision, POST /api/v1/ops/kitchens/{kitchenId}/payment-recipient/approval |
| E2E-OPS-109 | TC-112, TC-008, TC-007 | BR-008, BR-023 |  | POST /api/v1/ops/cooks/{applicationId}/decision |
| E2E-OPS-110 | TC-007, TC-111, TC-139 | BR-007, BR-056 |  | GET /api/v1/ops/audit, GET /api/v1/ops/audit/export |
| E2E-OPS-111 | TC-007, TC-111 | BR-007 | D18 | GET /api/v1/ops/security/mfa-policy, PUT /api/v1/ops/security/mfa-policy/{category}, POST /api/v1/ops/accounts/{accountId}/mfa-reset, GET /api/v1/me/security |
| E2E-OPS-112 | TC-123 |  |  | GET /api/v1/ops/dashboard, GET /api/v1/ops/audit/export |
| E2E-DISC-001 | TC-016, TC-017, TC-046, TC-116 | BR-016, BR-017, BR-046 | D12 | getMeals, getKitchensById, getConfigurationPublic |
| E2E-DISC-002 | TC-003, TC-109, TC-110 | BR-001, BR-003 |  | getMe |
| E2E-DISC-003 | TC-001, TC-002, TC-109 | BR-001, BR-002, BR-045 |  | getMe, getOrders, getMeFavorites |
| E2E-DISC-004 | TC-006, TC-044, TC-109 | BR-006, BR-044, BR-001 |  | getOrdersById, getOrdersByRequestById, postQuotes, postOrders, postOrdersCancel |
| E2E-DISC-005 | TC-009, TC-017, TC-116 | BR-009, BR-017 |  | getMealsById, getMeals, getKitchensById, postQuotes, patchCookSettings, postCookMeals |
| E2E-DISC-104 | TC-008, TC-017, TC-112, TC-123 | BR-008, BR-009, BR-017 |  | postOpsKitchensOperatingState, getKitchensById, getMeals, postQuotes |
| E2E-DISC-006 | TC-016, TC-116 | BR-016 | D11 | getMeals |
| E2E-DISC-101 | TC-016, TC-116, TC-021 | BR-016, BR-021 |  | getZones, getMeals |
| E2E-DISC-102 | TC-016, TC-116 | BR-016 |  | getMeals |
| E2E-DISC-103 | TC-016 | BR-016 |  | getMeals |
| E2E-DISC-007 | TC-021, TC-117, TC-005 | BR-021, BR-005 |  | postQuotes, postMeAddresses, deleteMeAddressesById, getZones |
| E2E-DISC-008 | TC-046, TC-150, TC-128 | BR-046 |  | getMealsById, getKitchensById, patchMe, postQuotes |
| E2E-DISC-113 | TC-047, TC-129 | BR-047 |  |  |
| E2E-DISC-105 | TC-003, TC-004, TC-054, TC-110 | BR-003, BR-004, BR-054 |  | postMePhoneChallenges, postMePhoneChallengesVerify, postQuotes |
| E2E-DISC-106 | TC-005, TC-006, TC-110 | BR-005, BR-006 |  | postMeAddresses, getMeAddresses, patchMeAddressesById, deleteMeAddressesById |
| E2E-DISC-107 | TC-040, TC-128, TC-017 | BR-040, BR-017 |  | getMeFavorites, putMeFavoritesById, deleteMeFavoritesById |
| E2E-DISC-108 | TC-037, TC-038, TC-121, TC-128 | BR-037, BR-038 |  | getNotifications, putNotificationsRead |
| E2E-DISC-109 | TC-110, TC-128 | BR-003, BR-006 |  | getMe, patchMe |
| E2E-DISC-110 | TC-039, TC-128 | BR-039 |  | postOrdersReviews, getKitchensReviews |
| E2E-DISC-111 | TC-039, TC-128 | BR-039 |  | postReviewsReports, getKitchensReviews |
| E2E-DISC-112 | TC-164 | BR-019 | D17 | postActivity |
| E2E-DISC-114 | TC-055, TC-128, TC-139 | BR-055, BR-001 |  | postMeDeletionRequests, getMe |
| E2E-DISC-117 | TC-157 | BR-045 | D10 | getConfigurationPublic |
| E2E-DISC-115 | TC-049, TC-051, TC-052, TC-132, TC-134, TC-135 | BR-049, BR-051, BR-052 |  | getMeals, getMealsById |
| E2E-BASKET-009 | TC-149, TC-147, TC-151, TC-018 | BR-018, BR-019 |  | getMealsById, postActivity |
| E2E-BASKET-010 | TC-147, TC-018 | BR-018 |  | getMealsById |
| E2E-BASKET-021 | TC-018, TC-117 | BR-018, BR-019 |  | postQuotes |
| E2E-BASKET-011 | TC-152, TC-117, TC-019 | BR-019, BR-020 |  | getConfigurationPublic, postQuotes |
| E2E-BASKET-029 | TC-020, TC-025, TC-118 | BR-020, BR-025 |  | postQuotes, postOrders, postOrdersCancel |
| E2E-BASKET-012 | TC-013, TC-151, TC-025, TC-029 | BR-013, BR-017, BR-029 |  | getMealsById, postQuotes, postOrders, postOrdersCancel |
| E2E-BASKET-023 | TC-014, TC-015, TC-115, TC-020 | BR-014, BR-015, BR-020, BR-027 |  | postCookSlots, patchCookSlotsById, postQuotes, postOrders, getMealsById |
| E2E-BASKET-013 | TC-020, TC-044, TC-117 | BR-020, BR-044 |  | postQuotes, postOrders |
| E2E-BASKET-014 | TC-154, TC-155, TC-020, TC-153 | BR-019, BR-020, BR-012 | D13, D14 | postQuotes, postOrders, patchCookMealsById, getCookMealsPriceHistory |
| E2E-BASKET-015 | TC-156, TC-153, TC-022 | BR-012, BR-022 | D13 | getOrdersById, patchCookMealsById |
| E2E-BASKET-016 | TC-025, TC-137, TC-118 | BR-025, BR-013 |  | postQuotes, postOrders |
| E2E-BASKET-017 | TC-013, TC-025, TC-137 | BR-013, BR-025 |  | postQuotes, postOrders |
| E2E-BASKET-018 | TC-022, TC-025, TC-118 | BR-022, BR-025 |  | postQuotes, postOrders |
| E2E-BASKET-019 | TC-117, TC-019, TC-157, TC-163 | BR-019, BR-021, BR-020 | D10, D16 | postQuotes, postMeAddresses |
| E2E-BASKET-020 | TC-019, TC-053, TC-136 | BR-019, BR-053 |  | postQuotes, postOrders |
| E2E-BASKET-028 | TC-022, TC-024 | BR-022, BR-024 |  | postOrders |
| E2E-BASKET-030 | TC-109, TC-127, TC-044 | BR-001, BR-018 |  | getMe |
| E2E-BASKET-022 | TC-154, TC-042, TC-126 | BR-019, BR-020, BR-042 | D13 | postOpsConfigurationVersions, postQuotes, postOrders |
| E2E-BASKET-024 | TC-152, TC-042, TC-020 | BR-019, BR-042, BR-020 |  | postOpsConfigurationVersions, getConfigurationPublic, postQuotes |
| E2E-BASKET-026 | TC-148, TC-132, TC-134, TC-135, TC-154 | BR-018, BR-049, BR-051, BR-052 | D13 | postQuotes, postOrders |
| E2E-BASKET-027 | TC-148, TC-131, TC-048, TC-006 | BR-048, BR-006, BR-049 |  | getMe, getOrders, getMeFavorites |
| E2E-ORDER-021 | TC-022, TC-117, TC-118, TC-157 | BR-019, BR-020, BR-022, BR-027 | D05, D10 | postQuotes, postOrders, getOrdersById, getOrdersEvents |
| E2E-ORDER-022 | TC-023, TC-024, TC-044, TC-118 | BR-023, BR-024 | D05 | postOrders, getOrdersByRequestById, getOrders |
| E2E-ORDER-023 | TC-024 | BR-024 | D05 | postOrders, postOrdersCancel |
| E2E-ORDER-024 | TC-044, TC-023 | BR-044, BR-023 | D13 | postOrders, getOrdersByRequestById |
| E2E-ORDER-025 | TC-119, TC-121, TC-006 | BR-026, BR-027, BR-029 | D05 | postCookOrdersAccept, getCookOrdersById, getNotifications |
| E2E-ORDER-026 | TC-029, TC-119 | BR-029, BR-026 | D05, D10 | postCookOrdersReject |
| E2E-ORDER-027 | TC-028, TC-029 | BR-028, BR-029 | D05 | postOrdersCancel |
| E2E-ORDER-028 | TC-119, TC-029 | BR-026, BR-029, BR-027 | D05 | postCookOrdersAccept, postOrdersCancel |
| E2E-ORDER-029 | TC-119, TC-029, TC-027 | BR-027, BR-029 | D05 | postCookOrdersAccept |
| E2E-ORDER-030 | TC-031, TC-119, TC-006 | BR-029, BR-031, BR-026 | D05 | postCookOrdersPrepare, postCookOrdersReady, getCookOrdersById, getOrdersById |
| E2E-ORDER-031 | TC-031, TC-032, TC-033, TC-157, TC-030 | BR-031, BR-032, BR-033 | D10 | postCookOrdersHandover |
| E2E-ORDER-032 | TC-119, TC-031 | BR-030, BR-031 | D05 | postCookOrdersDispatch, postCookOrdersHandover |
| E2E-ORDER-033 | TC-031, TC-119 | BR-031 | D05 | postCookOrdersHandover |
| E2E-ORDER-034 | TC-119, TC-124 | BR-029, BR-032 | D05 | postCookOrdersDeliveryFailure, getCookOrders, getOpsOrders |
| E2E-ORDER-035 | TC-028, TC-041, TC-124 | BR-028, BR-041 | D05 | postOrdersCancellationRequests, postOrdersCancel, getOpsCases |
| E2E-ORDER-036 | TC-119, TC-022, TC-026 | BR-026 | D05 | postCookOrdersPrepare, postCookOrdersAccept, postCookOrdersReady, postOrdersCancel |
| E2E-ORDER-037 | TC-006, TC-001 | BR-006, BR-001 | D05 | getCookOrdersById, postCookOrdersAccept, postCookOrdersPaymentReceipt, getOpsOrders |
| E2E-ORDER-101 | TC-022, TC-028, TC-031, TC-032, TC-119, TC-124 | BR-022, BR-026, BR-030, BR-031, BR-032 | D05, D10 | postQuotes, postOrders, getOrdersById, postCookOrdersAccept, postCookOrdersPrepare, postCookOrdersReady, postCookOrdersHandover, getOpsOrdersById |
| E2E-ORDER-102 | TC-021, TC-031, TC-119, TC-117 | BR-021, BR-030, BR-031 | D05, D10 | postQuotes, postOrders, postCookOrdersDispatch, postCookOrdersHandover |
| E2E-ORDER-105 | TC-023, TC-044 | BR-023, BR-044 | D05 | postOrders, getOrdersByRequestById |
| E2E-ORDER-106 | TC-023, TC-044 | BR-023, BR-044 | D13 | postOrders |
| E2E-ORDER-107 | TC-049, TC-132 | BR-049, BR-044 | D13 | postOrders, getOrdersByRequestById |
| E2E-ORDER-108 | TC-031, TC-119 | BR-031 | D05, D18 | postOpsOrdersHandoverOverride |
| E2E-ORDER-109 | TC-028, TC-029, TC-041 | BR-028, BR-029, BR-041 | D05 | postOpsOrdersExceptionCancel, patchOpsCasesById |
| E2E-ORDER-110 | TC-029, TC-119 | BR-029, BR-028 | D05 | postCookOrdersCancel |
| E2E-ORDER-111 | TC-020, TC-117 | BR-020 | D13 | postQuotes, postOrders |
| E2E-ORDER-112 | TC-020, TC-156, TC-042 | BR-012, BR-020 | D13, D14 | postQuotes, postOrders, getCookMealsPriceHistory |
| E2E-ORDER-113 | TC-117, TC-018, TC-006 | BR-018, BR-019, BR-021 | D05 | postQuotes, postOrders |
| E2E-ORDER-114 | TC-013, TC-115, TC-029 | BR-013, BR-025, BR-029 | D05 | postQuotes, postOrders, postOrdersCancel |
| E2E-ORDER-115 | TC-118, TC-013, TC-138 | BR-025, BR-013 | D05 | postOrders |
| E2E-ORDER-117 | TC-037, TC-038, TC-121 | BR-037, BR-038 | D05 | getNotifications, putNotificationsRead |
| E2E-ORDER-118 | TC-041, TC-124, TC-006 | BR-041 | D05 | postSupportCases, getSupportCases, getOpsCases, postOpsCasesMessages |
| E2E-ORDER-119 | TC-006, TC-124 | BR-006, BR-041 | D05 | getOpsOrders, getOpsOrdersById |
| E2E-ORDER-120 | TC-006, TC-001 | BR-006, BR-001 | D05 | getOrdersById, getOrdersEvents, postOrdersCancel, postOrdersPaymentTransferReport, postSupportCases |
| E2E-ORDER-121 | TC-022, TC-033, TC-156 | BR-012, BR-026, BR-033 | D05 |  |
| E2E-ORDER-122 | TC-132, TC-163 | BR-049, BR-019 | D05 | postQuotes, postOrders |
| E2E-ORDER-123 | TC-023, TC-118, TC-058 | BR-023, BR-024, BR-025 | D05 | postOrders |
| E2E-FIN-038 | TC-032, TC-120, TC-157 | BR-032, BR-033 | D05, D10 | postCookOrdersCashReports, getOpsFinanceCashReports, postOpsFinanceCashReportsVerification, getOpsFinanceStatements, getCookStatements |
| E2E-FIN-039 | TC-158, TC-159, TC-163, TC-032, TC-157 | BR-019, BR-022, BR-026, BR-032 | D16, D10, D05 | postQuotes, postOrders, postOrdersPaymentTransferReport, postCookOrdersPaymentReceipt, postCookOrdersCashReports, postOpsFinanceCashReportsVerification, getOpsFinanceStatements |
| E2E-FIN-040 | TC-161, TC-159, TC-041 | BR-026, BR-041 | D16 | postCookOrdersPaymentProblem, getOpsPayments, postSupportCases |
| E2E-FIN-041 | TC-158, TC-117, TC-161 | BR-019 | D16 | postQuotes, postCookOrdersPaymentReceipt, putCookPaymentProfile |
| E2E-FIN-042 | TC-159, TC-163 | BR-024, BR-019 | D16 | postOrdersPaymentTransferReport |
| E2E-FIN-043 | TC-032, TC-120, TC-061 | BR-032 | D05 | postCookOrdersCashReports, postOpsFinanceCashReportsVerification, getOpsOrders |
| E2E-FIN-044 | TC-035, TC-006 | BR-035, BR-006 | D05, D10 | postOpsFinanceRefunds, postOpsFinanceRefundsAction |
| E2E-FIN-045 | TC-120, TC-033, TC-034, TC-058, TC-157 | BR-032, BR-033, BR-034 | D05, D10 | getOpsFinanceStatements |
| E2E-FIN-046 | TC-157, TC-034, TC-117 | BR-033, BR-019 | D10, D05 | postQuotes, getOpsConfiguration, getCookStatements |
| E2E-FIN-047 | TC-163, TC-157 | BR-019 | D16, D10 |  |
| E2E-FIN-101 | TC-158, TC-159, TC-163, TC-119 | BR-019, BR-021, BR-030 | D16, D10 | postQuotes, postOrders, postCookOrdersDispatch, postCookOrdersPaymentReceipt, postCookOrdersCashReports |
| E2E-FIN-102 | TC-032, TC-120, TC-058 | BR-032, BR-024 | D05 | postCookOrdersCashReports |
| E2E-FIN-103 | TC-158, TC-163, TC-042 | BR-019, BR-020 | D16, D13, D18 | putCookPaymentProfile, getOpsPaymentRecipients, postOpsKitchensPaymentRecipientApproval, postQuotes, postOrders |
| E2E-FIN-104 | TC-158, TC-163 | BR-019 | D16, D05 | putCookPaymentProfile, postQuotes |
| E2E-FIN-105 | TC-158, TC-163 | BR-019 | D16 | postQuotes |
| E2E-FIN-106 | TC-159, TC-119 | BR-026 | D16 | postCookOrdersPrepare, postCookOrdersPaymentReceipt, postOrdersPaymentTransferReport |
| E2E-FIN-107 | TC-160, TC-162, TC-163 | BR-027, BR-029, BR-035 | D16, D18 | postCookOrdersCancel, getOpsPayments, postOpsOrdersPaymentDecision, postCookOrdersPaymentRefund |
| E2E-FIN-108 | TC-161, TC-159 | BR-026 | D16 | postCookOrdersPaymentReceipt |
| E2E-FIN-109 | TC-161 | BR-026 | D16 | postCookOrdersPaymentProblem |
| E2E-FIN-110 | TC-036, TC-157 | BR-036, BR-033 | D10 | postOpsFinanceSettlements |
| E2E-FIN-111 | TC-120, TC-157 | BR-032, BR-033 | D10 | getCookStatements |
| E2E-FIN-112 | TC-006, TC-001, TC-125 | BR-006, BR-001 | D05, D18 | getOpsPayments, getOpsPaymentRecipients, getOpsFinanceCashReports, getOpsFinanceStatements, postOpsOrdersPaymentDecision, postOpsFinanceRefunds, postOpsFinanceSettlements |
| E2E-FIN-113 | TC-163, TC-132 | BR-019, BR-049 | D16 | postOrdersPaymentTransferReport |
| E2E-FIN-114 | TC-006, TC-120 | BR-032, BR-006 | D05 | postOpsFinanceCashReportsVerification |
| E2E-FIN-115 | TC-160, TC-161, TC-162 | BR-027, BR-029 | D16, D18 | postCookOrdersReject, postOpsOrdersPaymentDecision |
