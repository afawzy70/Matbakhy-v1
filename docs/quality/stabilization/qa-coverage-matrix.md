# QA coverage matrix (requirement → scenario → test reference)

Source commit: `9ab17f9d044e9a0f487ad7d8de5e939bc2093004` (planning data + static grep; **no test was executed to produce this file**).

- Rules: 64; scenarios: 128; recorded scenario status (planning/scenarios.json, claims not re-verified): {'PASS': 102, 'BLOCKED': 1, 'NOT_RUN': 25}; levels: {'integration': 65, 'browser': 6, 'native': 6, 'review': 4, 'system': 45, 'component': 2}
- Test files scanned: 231; files referencing TC-/BR- ids: 49

| Rule | Domain | Story | Scenarios | Recorded status | Test files citing rule/scenario id (static) |
|---|---|---|---|---|---|
| BR-001 | Identity | MB-009 | TC-001 | PASS | 1: IdentityIntegrationTest.java |
| BR-002 | Identity | MB-009 | TC-002 | PASS | 1: IdentityIntegrationTest.java |
| BR-003 | Identity | MB-010 | TC-003 | PASS | 1: ContactIntegrationTest.java |
| BR-004 | Identity | MB-010 | TC-004 | PASS | 1: ContactIntegrationTest.java |
| BR-005 | Address | MB-010 | TC-005 | PASS | 1: ContactIntegrationTest.java |
| BR-006 | Access | MB-011 | TC-006 | PASS | 2: StaffAccessIntegrationTest.java, OrderPlacementIntegrationTest.java |
| BR-007 | Access | MB-011 | TC-007 | PASS | 1: StaffAccessIntegrationTest.java |
| BR-008 | Cook | MB-012 | TC-008 | PASS | 1: CookWorkflowIntegrationTest.java |
| BR-009 | Cook | MB-012 | TC-009 | PASS | 1: CookWorkflowIntegrationTest.java |
| BR-010 | Media | MB-013 | TC-010 | PASS | 1: MediaIntegrationTest.java |
| BR-011 | Catalogue | MB-014 | TC-011 | PASS | 1: MealIntegrationTest.java |
| BR-012 | Catalogue | MB-014 | TC-012 | PASS | 1: MealIntegrationTest.java |
| BR-013 | Availability | MB-015 | TC-013 | PASS | 2: SlotIntegrationTest.java, QuoteIntegrationTest.java |
| BR-014 | Availability | MB-015 | TC-014 | PASS | 1: SlotIntegrationTest.java |
| BR-015 | Availability | MB-015 | TC-015 | PASS | 1: SlotIntegrationTest.java |
| BR-016 | Discovery | MB-016 | TC-016 | PASS | 1: DiscoveryIntegrationTest.java |
| BR-017 | Discovery | MB-016 | TC-017 | PASS | 3: App.test.tsx, DiscoveryIntegrationTest.java, QuoteIntegrationTest.java |
| BR-018 | Basket | MB-017 | TC-018 | PASS | 4: ordering.test.ts, App.test.tsx, customer-journey.e2e.ts |
| BR-019 | Pricing | MB-017 | TC-019 | PASS | 1: QuoteIntegrationTest.java |
| BR-020 | Pricing | MB-017 | TC-020 | PASS | 1: QuoteIntegrationTest.java |
| BR-021 | Fulfillment | MB-017 | TC-021 | PASS | 1: QuoteIntegrationTest.java |
| BR-022 | Order | MB-018 | TC-022 | PASS | 1: OrderPlacementIntegrationTest.java |
| BR-023 | Order | MB-018 | TC-023 | PASS | 2: ordering.test.ts, OrderPlacementIntegrationTest.java |
| BR-024 | Order | MB-018 | TC-024 | PASS | 1: OrderPlacementIntegrationTest.java |
| BR-025 | Stock | MB-018 | TC-025 | PASS | 1: OrderPlacementIntegrationTest.java |
| BR-026 | Lifecycle | MB-019 | TC-026 | PASS | 1: OrderLifecycleIntegrationTest.java |
| BR-027 | Lifecycle | MB-019 | TC-027 | PASS | 1: OrderLifecycleIntegrationTest.java |
| BR-028 | Cancellation | MB-019 | TC-028 | PASS | 1: OrderLifecycleIntegrationTest.java |
| BR-029 | Stock | MB-019 | TC-029 | PASS | 1: OrderLifecycleIntegrationTest.java |
| BR-030 | Fulfillment | MB-019 | TC-030 | PASS | 1: OrderLifecycleIntegrationTest.java |
| BR-031 | Fulfillment | MB-019 | TC-031 | PASS | 1: OrderLifecycleIntegrationTest.java |
| BR-032 | Cash | MB-020 | TC-032 | PASS | 1: CashAndLedgerIntegrationTest.java |
| BR-033 | Finance | MB-020 | TC-033 | PASS | 1: CashAndLedgerIntegrationTest.java |
| BR-034 | Finance | MB-025 | TC-034 | PASS | 1: FinanceOperationsIntegrationTest.java |
| BR-035 | Refund | MB-025 | TC-035 | PASS | 1: FinanceOperationsIntegrationTest.java |
| BR-036 | Settlement | MB-025 | TC-036 | PASS | 1: FinanceOperationsIntegrationTest.java |
| BR-037 | Notification | MB-021 | TC-037 | PASS | 1: NotificationIntegrationTest.java |
| BR-038 | Notification | MB-021 | TC-038 | PASS | 1: NotificationIntegrationTest.java |
| BR-039 | Reviews | MB-028 | TC-039 | PASS | 1: ReviewsAndFavoritesIntegrationTest.java |
| BR-040 | Favorites | MB-028 | TC-040 | PASS | 1: ReviewsAndFavoritesIntegrationTest.java |
| BR-041 | Support | MB-024 | TC-041 | PASS | 1: SupportIntegrationTest.java |
| BR-042 | Configuration | MB-026 | TC-042 | PASS | 2: CommerceConfigurationTest.java, ConfigurationIntegrationTest.java |
| BR-043 | Access | MB-026 | TC-043 | PASS | 1: ConfigurationIntegrationTest.java |
| BR-044 | Web | MB-027 | TC-044 | PASS | 1: ordering.test.ts |
| BR-045 | Web | MB-027 | TC-045 | PASS | 0 (none found) |
| BR-046 | UX | MB-029 | TC-046 | PASS | 0 (none found) |
| BR-047 | Accessibility | MB-029 | TC-047 | BLOCKED | 1: keyboard.e2e.ts |
| BR-048 | Mobile | MB-031 | TC-048 | PASS | 3: SignInRoundTripTests.swift, auth.test.ts, signIn.test.ts |
| BR-049 | Mobile | MB-032 | TC-049 | PASS | 5: OrderingTests.swift, side-effects.sql, slow_proxy.py |
| BR-050 | Mobile | MB-033 | TC-050 | PASS | 0 (none found) |
| BR-051 | Mobile | MB-034 | TC-051 | NOT_RUN | 0 (none found) |
| BR-052 | Mobile | MB-035 | TC-052 | NOT_RUN | 0 (none found) |
| BR-053 | Security | MB-036 | TC-053 | NOT_RUN | 0 (none found) |
| BR-054 | Security | MB-036 | TC-054 | NOT_RUN | 0 (none found) |
| BR-055 | Privacy | MB-039 | TC-055 | PASS | 1: AccountErasureIntegrationTest.java |
| BR-056 | Observability | MB-039 | TC-056 | PASS | 1: LogRedactionIntegrationTest.java |
| BR-057 | Recovery | MB-039 | TC-057 | NOT_RUN | 2: BackupRestoreIntegrationTest.java, test_preview_backup.py |
| BR-058 | Reliability | MB-037 | TC-058 | PASS | 2: CashAndLedgerIntegrationTest.java, RestartRecoveryIntegrationTest.java |
| BR-059 | Performance | MB-038 | TC-059 | NOT_RUN | 0 (none found) |
| BR-060 | Deployment | MB-040 | TC-060 | NOT_RUN | 0 (none found) |
| BR-061 | Operations | MB-041 | TC-061 | NOT_RUN | 0 (none found) |
| BR-062 | Release | MB-043 | TC-062 | NOT_RUN | 0 (none found) |
| BR-063 | Release | MB-045 | TC-063 | NOT_RUN | 0 (none found) |
| BR-064 | Operations | MB-046 | TC-064 | NOT_RUN | 0 (none found) |

## Rules with no id-level citation in test sources (13)
These may still be covered by tests that do not cite ids; the QA agent must confirm by reading tests, not assume.
BR-045, BR-046, BR-050, BR-051, BR-052, BR-053, BR-054, BR-059, BR-060, BR-061, BR-062, BR-063, BR-064

## Priority areas for the independent QA agent (from CLAUDE.md invariants)
- Order/pricing/payment integrity: integer EGP, server-owned price, immutable snapshots, deposit state machine (docs/product/09).
- Authorization: object-level (customer/cook/admin), suspended accounts, cross-cook access.
- Reservations/idempotency/concurrency: slot capacity, duplicate submit, double-accept.
- Zero platform commission/fees; Arabic RTL/English LTR; migrations (scripts/check_migration_compat.py).

## Environment limits
No JDK, no Docker socket, Node 22 on VPS (repo needs >=24.21): backend/Testcontainers/E2E results come from GitHub Actions or remain NOT_RUN.
