# Glossary

> **Owner role:** Business documentation
> **Status:** REFERENCE — terms used in the code, the UI and the product documents, with the Arabic strings that exist in the front-end i18n files.

Arabic strings below were copied from the i18n files and checked there: `apps/customer-web/src/i18n.ts` (customer web), `apps/customer-mobile/src/i18n.ts` (customer mobile) and `apps/operations-web/src/i18n.ts` (cook workspace and backoffice). Where a term has no UI string it is marked "no UI string". Money is always integer piastres (`1 EGP = 100 piastres`). See also [project overview](project-overview.md) and [business rules](../01-business/business-rules.md).

## Business and UI terms (Arabic / English)

| English term | Arabic UI string | Where used | Meaning |
|---|---|---|---|
| Basket / cart | السلة | customer web `nav.cart`, mobile `replaceBasket` | Client-side list of meals from one kitchen and one slot; reserves nothing |
| Replace basket | استبدال السلة | customer web `replaceTitle` | Confirmation shown when adding from another kitchen or slot |
| My orders | طلباتي | customer web `nav.orders` | Customer's order list |
| Home / browse | الرئيسية | customer web `nav.browse` | Discovery page |
| Kitchen | مطبخ (المطبخ) | operations web `settings: "إعدادات المطبخ"` | The selling identity of an approved cook; table `kitchen` |
| Cook / chef | الطاهي | operations web `section: "مساحة الطاهي"` (cook workspace); customer web `handoverCode` text | The person who owns a kitchen |
| Meal / dish | الأكلات (list label) | customer web `home.dishes` | A published item with revisions; table `meal` |
| Slot | no dedicated UI term; shown as a date and time window | `SlotView` in `OrderService` | A dated window with limited portions; table `cook_slot` |
| Quote | العرض / عروض الأسعار | customer web `QUOTE_EXPIRED` ("انتهت صلاحية العرض"); operations web `newQuotesOnly` ("عروض الأسعار الجديدة") | Server-calculated, immutable price offer valid 5 minutes |
| Total | الإجمالي | customer web `newTotalReview` | Subtotal + delivery fee + tax |
| Price changed | تغيّر السعر | customer web `priceChangeTitle` | Title of the notice shown after `PRICE_CHANGED` (D13) |
| Accept new total | أوافق على الإجمالي الجديد | customer web `acceptNewTotal` | Explicit re-acceptance required before placing |
| Cash on receipt (COD) | الدفع عند الاستلام | customer web `payment.cod` | Customer pays the full amount to the kitchen at hand-over |
| Deposit | عربون | customer web `payment.deposit` ("عربون يُحوّل للمطبخ مباشرة") | Part of the total sent directly to the kitchen's approved account |
| I transferred the deposit | حوّلت العربون | customer web `payment.iSent` | Customer's report; information, not payment |
| Waiting for deposit | في انتظار تحويل العربون | `WAITING_FOR_DEPOSIT` | Payment state |
| Kitchen reported receipt | المطبخ أبلغ باستلام العربون | `KITCHEN_RECEIPT_REPORTED` | The kitchen's statement; never "bank verified" |
| Refund owed | المطبخ عليه رد العربون | `REFUND_OWED` | Staff decided the kitchen must return the deposit |
| Handover code | رمز التسليم | customer web `handoverCode` | Six-digit code shown to the customer, entered by the cook at hand-over |
| Waiting for the kitchen | بانتظار قبول المطبخ | `PENDING_ACCEPTANCE` | Order state |
| Accepted | تم القبول | `ACCEPTED` | Order state |
| Preparing | قيد التحضير | `PREPARING` | Order state |
| Ready | جاهز | `READY` | Order state |
| On the way | في الطريق إليك | `OUT_FOR_DELIVERY` | Order state |
| Delivered / completed | تم التسليم | `COMPLETED` | Order state (the UI wording is the same for pickup) |
| Rejected by the kitchen | رفضه المطبخ | `REJECTED` | Order state |
| Not accepted in time | انتهت مهلة القبول | `EXPIRED` | Order state |
| Cancelled | ملغي | `CANCELLED` | Order state |
| Delivery failed | تعذر التوصيل | `DELIVERY_FAILED` | Order state |
| Support case | حالات الدعم | customer web `supportCases` | Order-scoped complaint or request |
| Cancellation request | طلب إلغاء | customer web `caseCategories.CANCELLATION_REQUEST` | Case a customer opens after acceptance |
| Deposit review | مراجعة العربون | `caseCategories.PAYMENT` | System-opened payment case |
| Food safety | سلامة الغذاء | `caseCategories.FOOD_SAFETY` | Critical support category |
| Accept / Reject / Start preparation | قبول / رفض / بدء التحضير | operations web `ACCEPT`, `REJECT`, `PREPARE` | Cook actions |
| Kitchen performance | أداء المطبخ | operations web `title` of the insights block | Free insights page for cooks |
| Staff permissions | صلاحيات الموظفين | operations web | Backoffice staff-grant screen |
| Finance | المالية | operations web | Backoffice finance section |
| Support | الدعم | operations web `cases` | Backoffice case queue |
| Start review / Approve / Request changes / Reject | بدء المراجعة / اعتماد / طلب تعديلات / رفض | operations web `decision` | Cook-application decisions |
| Demo environment | بيئة تجريبية | customer web `demoNoticeTitle` | Persistent notice that data and money are simulated |
| Sign in / Sign out | تسجيل الدخول / تسجيل الخروج | customer web | Authentication |
| Intent tiles | وجبة ليا أو لينا / أكمل سفرتي / أجهز عزومة | customer web `home.intents` | Home entry points: a meal for me or us, complete my table, prepare a gathering |

## Domain and technical terms

| Term | Meaning | Where to look |
|---|---|---|
| Piastre / minor unit | 1/100 EGP; all money is stored as integer `*_minor` | `Money` records in `backend/modules/orders` |
| Egypt time | `Africa/Cairo`; instants stored in UTC | `backend/modules/availability/src/main/java/com/matbakhy/availability/internal/CairoTime.java` |
| Quote | Row in `quote` (+ `quote_item`); holds no stock; expires; immutable | `QuoteService` |
| Stale quote | A quote whose price, policy, address or payment terms changed since it was calculated; placement is refused with `PRICE_CHANGED` | [pricing and payments](../01-business/pricing-and-payments.md) |
| Snapshot | Frozen copy of meal content, prices, policy versions, contact and address stored on the order | `order_item.meal_snapshot`, `customer_order` |
| Meal revision | Immutable version of a meal's content, incl. price | `meal_revision` |
| Commerce policy | Versioned business configuration: tax, commission, quote TTL, acceptance windows, terms and cancellation versions | `commerce_policy`, `CommercePolicies` |
| Synthetic policy | Fake policy allowed only in local/CI/preview; real policies need an approval reference | `SyntheticPolicyPermission` |
| Reservation / allocation / consumption | The three stock stages: placed, accepted, preparation started | `SlotInventory.Stage` |
| Cutoff | Slot start minus lead time; after it, no new orders or capacity edits | `cook_slot.cutoff_at` |
| Acceptance deadline (`acceptBy`) | When a pending order expires | `customer_order.accept_by` |
| Idempotency key | `Idempotency-Key` header; scope actor + command + key; 24 h | `JdbcIdempotentCommands` |
| `clientRequestId` | Customer-generated id that permanently identifies a placement attempt | `customer_order_request_uq` |
| Request fingerprint | SHA-256 over quote, request id, terms versions and note, to detect a changed retry | `OrderService.fingerprint` |
| Optimistic version / `expectedVersion` | Commands carry the version they saw; stale ones get `VERSION_CONFLICT` | all lifecycle commands |
| Outbox | Table `outbox_event` written in the same transaction as the change; a dispatcher turns events into notifications | `OutboxDispatcher` |
| Notification / inbox | In-app message per recipient, deduplicated | `notification`, `notification_delivery` (V14) |
| Grant (permission grant) | Row in `user_grant` giving an account one named staff permission, optionally expiring | `GrantService` |
| Permission | One of 17 values, e.g. `finance.verify`; becomes authority `PERM_<code>` | `Permission` |
| Bootstrap administrator | First administrator created from environment configuration; holds `staff.grant` and `audit.read` | `GrantService.bootstrapAdministratorIfNone` |
| Object-level authorization | Checking the caller's right to the specific record, not just the role; foreign records are 404 | [actors and permissions](../01-business/actors-and-permissions.md) |
| Separation of duties | Two steps that must be done by different people (report vs verify, request vs approve) | `CashService`, `FinanceOpsService` |
| MFA policy | Mode `DISABLED`, `OPTIONAL` or `REQUIRED` per population `CUSTOMER`, `KITCHEN`, `STAFF` | `MfaPolicyService`, `docs/security/mfa-policy.md` |
| Step-up | Re-authentication with a second factor demanded by the API (`STEP_UP_REQUIRED`) | `MfaEnforcementFilter`, `StaffAssurance` |
| Recent MFA | Provider-asserted MFA within the step-up window, after the last admin reset | `StaffAssurance.recentMfa` |
| OIDC / PKCE | OpenID Connect sign-in via Keycloak; PKCE protects native code exchange | `SecurityConfiguration` |
| Handover challenge | Server record for the handover code (keyed hash + encrypted display value, attempts, expiry) | `handover_challenge` |
| Cash obligation | Amount the customer owes in cash at hand-over (total minus deposit) | `cash_obligation` |
| Cash report | Cook's declaration of cash collected on a completed order | `cash_report` |
| Verification | Finance staff confirming the cash report (never the reporter) | `CashService.verify` |
| Deposit recipient | Kitchen's InstaPay / wallet / bank details, staff-approved before customers see them | `kitchen_payment_profile` |
| Reconciliation | Human comparison of reports and statements; states `RECONCILE_PENDING`, `DISPUTED` | `PaymentService` |
| Ledger | Balanced, append-only commission entries (zero amounts at launch) | `ledger_group`, `ledger_entry` |
| Settlement | Batch of ledger lines to pay out; dormant under zero commission | `FinanceOpsService` |
| Operational approval | Staff reviewed a cook application; label `OPERATIONALLY_VERIFIED`; not a food-safety guarantee | `KitchenService` |
| Moderation (`HIDDEN`/`VISIBLE`) | Staff visibility flag on meals and reviews, independent of the cook's own state | `MealService`, `ReviewService` |
| Owner E2E case | Numbered manual/automated acceptance case, e.g. `E2E-ORDER-021` | `docs/quality/owner-acceptance/cases/*.json` |
| Synthetic data | Obviously fake accounts, zones, policies and payment recipients, allowed only in local/CI/preview | `backend/app/src/main/resources/db/devdata/` |
| BR / TC / MB | Business rule, test scenario, backlog story ids | `planning/rules.json`, `planning/scenarios.json`, `planning/backlog.json` |
| D-number | Owner decision id (D10 zero commission, D13 price change, D14 price history, D16 payments, D18 MFA) | `docs/product/01-context-and-decisions.md` |

## Known limitations and documentation gaps

- "Slot" has no customer-facing Arabic term in the files checked; the UI shows dates and times. NOT VERIFIED whether a label exists in a component not inspected.
- Arabic terms for staff-only states (for example `RECONCILE_PENDING`) exist in `apps/operations-web/src/i18n.ts` but were not all copied here.
