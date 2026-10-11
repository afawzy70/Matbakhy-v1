# Screen and interaction specification

Implement against real endpoints once the relevant backend gate passes. P00 prototypes may use clearly labelled fixtures. A prototype, screenshot or static success toast is not feature completion.

## Shared screen contract

Every data screen specifies initial loading, empty, loaded, refreshing, error with retry, unauthorized/forbidden and offline behavior. Forms additionally specify field validation, submitting, server conflict, success and unsaved-change handling. Retain valid input after recoverable errors; never retain OTPs or secrets unnecessarily. Critical mutations disable duplicate UI submission but also rely on server idempotency.

Web routes preserve query filters in the URL; back/forward restores them. Changing search/zone/date/price/category/sort requests the server and resets pagination. Ignore stale out-of-order fetch responses. Lists expose current result count semantics; never label loaded-page count as total count. Dates show local zone where ambiguity matters; times and dates stay together.

## Customer web routes

| ID / route | Content and actions | Specific acceptance |
|---|---|---|
| CW01 `/` | Language, service-zone selector, search, featured/available meals, cook entry points | Manual zone selection works without geolocation; featured content is genuine data, not fake sales claims |
| CW02 `/meals` | Search, zone/date/slot/category/price filters, sort, paginated cards | New server query per filter; no client-only subset bug; filter reset restores defined defaults |
| CW03 `/meals/:slug` | Photos, cook, portion, price, ingredients/allergens, slots, quantity, favorite/share, add | Cannot add unavailable selection; quantity cap clear; allergen uncertainty visible |
| CW04 `/cooks/:slug` | Public cook identity/bio, operational approval label, meals, visible reviews | No private address/documents; paused cook has clear unavailable state |
| CW05 `/cart` | Single cook/slot items, quantity/remove, estimated subtotal, checkout | Confirm before replacing cart; no surprise clearing; server quote required later |
| CW06 `/login` + callback | IdP sign-in/register/recovery redirect and safe return route | Prevent open redirects; preserve safe cart draft; accessible failures |
| CW07 `/checkout/contact` | Email status, phone input/OTP/resend, contact details | Countdown based on server limits; OTP paste/autofill accessible; attempts bounded |
| CW08 `/checkout` | Delivery/pickup, address, slot, full quote breakdown, policies, cash instructions, place order | Price/capacity change forces fresh review; unknown submit response uses stable request ID |
| CW09 `/orders/:id` | Public order number, status timeline, slot, items/cost, acceptance deadline, allowed actions, handover code, support | Status reflects server; pending is not accepted; code only customer; cancel/request distinction clear |
| CW10 `/orders` | Active/past tabs, pagination, current status, reorder | Reorder rebuilds with current prices/stock and reports unavailable lines |
| CW11 `/account` | Profile, locale, contact verification, notification preferences, sign-out/deletion | Changing contact invalidates verification; deletion explains process without fake instant purge |
| CW12 `/account/addresses` | Add/edit/archive/default address | Zone validation, internationalized input, own objects only, confirmation on archive |
| CW13 `/favorites` | Saved meals/cooks through meal relationships, remove | Archived/paused meals render with unavailable explanation; no broken links |
| CW14 `/orders/:id/review` | Rating 1–5, bounded text, report guidance | Only completed own order; duplicate response recovers existing review |
| CW15 `/notifications` | Persisted inbox, read/unread, order/case links | Refresh resolves current status; no cross-account links after logout |
| CW16 `/support` and `/support/:id` | Contact info, create order-linked case, attachments, public messages, resolution | Staff-only notes absent from response, not merely CSS-hidden |
| CW17 `/terms`, `/privacy`, `/help` | Versioned approved policies and contact/deletion guidance | Draft labels in dev; approved current content required before public launch |

## Cook workspace routes

Located under `/cook` in operations web. Cook can switch to their customer experience, but no platform role is implied.

| ID / route | Content/actions | Specific acceptance |
|---|---|---|
| CK01 `/cook/onboarding` | Progress, profile, contact/location, documents, zones, submit/revise | Draft save, private upload progress, review reasons and revision history |
| CK02 `/cook` | Pending orders, deadlines, today's preparation totals, availability/pause | Counts from same server scope as queues; overdue actions highlighted without fake timer extensions |
| CK03 `/cook/meals` | Draft/published/paused/archived list; create/edit/preview/status | Search/pagination real; cannot bypass publication validation |
| CK04 `/cook/meals/:id/edit` | Translations, portion, price, ingredients/allergens, photo reorder/alt | Field errors anchored; expectedVersion conflict preserves edits for review |
| CK05 `/cook/availability` | Date/slot table, meal quantity, total cook capacity, cutoff, open/close | Impossible capacity edit rejected with obligations explanation; timezone explicit |
| CK06 `/cook/orders` | Pending/accepted/preparing/ready/delivery tabs, filters, deadline/sort | State refresh after action, real pagination, no missed orders from local filtering |
| CK07 `/cook/orders/:id` | Items, needed fulfillment contact, timeline, allowed transitions, code entry, cash report | Acceptance/rejection reason and state races; cash action separate; code never visible to cook |
| CK08 `/cook/earnings` | GMV, commission receivable, cash reports, refunds, remittances, statements | Label money direction correctly; pending/refunded amounts distinct; export scoped and safe |
| CK09 `/cook/settings` | Public profile, pickup/service zones, pause, notification settings | Approval-sensitive changes re-reviewed according to policy; suspension cannot be self-cleared |

## Platform operations routes

Located under `/ops`; permission-specific navigation and API authorization. No one screen grants all staff permissions.

| ID / route | Content/actions | Specific acceptance |
|---|---|---|
| OP01 `/ops` | Cook-review queue, overdue orders, safety/support cases, cash variance, provider failures | Each KPI has definition/time window and drilldown; no fabricated metrics |
| OP02 `/ops/cooks` and `/:id` | Filters, application revision, private documents, approve/change/reject/pause/suspend | Reviewer and reason required; private viewer expires; impacts on active orders shown |
| OP03 `/ops/catalogue` | Meals and flagged content, moderation reasons, image status | Hide/pause with audit; historical order snapshots preserved |
| OP04 `/ops/orders` and `/:id` | Full permitted order timeline, allocations, amount snapshots, case links, exceptions | Immutable history; cancellation/handover override require correct permission and reason |
| OP05 `/ops/support` and `/:id` | Queues, severity/SLA, assignment, public replies/private notes, resolve/reopen | Explicit audience before message save; high severity visibly escalated |
| OP06 `/ops/finance` | Cash reports/verification, discrepancy, full refunds, batch settlements | Separate recorded/verified/pending states; no fake payment button that simply changes status |
| OP07 `/ops/configuration` | Zones, delivery fees, cutoffs, commission/policy versions, feature flags | Effective time/version preview; changes affect future quotes only; live approval guards |
| OP08 `/ops/staff` | Role grants/revoke and scopes | Fresh MFA, self-escalation/last-admin protection, audit |
| OP09 `/ops/audit` | Actor/object/date/action filters, redacted details, bounded export | Permission and scope apply to search/count/export; CSV formula neutralization |
| OP10 `/ops/integrations` | Health, outbox failures, safe retry action and event IDs | No credentials shown; replay idempotent; operator cannot send arbitrary provider payload |

All operations tables use semantic columns, aligned headers/cells, stable sorting, correct date/time rendering and horizontal overflow only when needed. At narrow widths prefer prioritised cards or an accessible scroll region; never rotate labels into unreadable vertical text. Destructive actions need named objects, consequences and confirmation; ordinary edits do not need extra approval ceremonies.

## Native customer navigation

Tabs: Discover, Favorites, Orders, Account. Stack screens: search/filter, meal, cook, cart, contact verification, checkout, order detail, address editor, review, notifications, support/case, policies. A cart shortcut includes count and available checkout action; preserve platform back behavior. Native equivalents satisfy CW01–CW17 with platform-specific UI.

Test safe areas, Android back gesture, iOS back gesture, keyboard avoidance, password manager/browser handoff, large text, TalkBack/VoiceOver, image errors, slow network, app background/kill, push denial, deep link before/after login and camera/photo permission denial. No forced permission at first launch without an immediate need.

## Field and interaction bounds

Proposed validation defaults: name 2–100 characters; meal title 2–120; description 20–2000; portion text 2–200; order note 0–500; review text 0–2000; support message 1–4000; quantity 1–20 per line; max 20 lines/cart. Configure and match client/server limits. Trim whitespace without corrupting Arabic or legitimate names. Reject control characters, not entire non-Latin alphabets. File defaults: photos ≤5MB, ≤6000px per dimension; attachments ≤10MB; types explicitly allowlisted. Server enforces bounds regardless of UI.

## Design evidence

P00 records prototype screenshots/route inventory and requests owner visual approval. P06 records actual browser viewport/RTL/accessibility evidence. P07 records native evidence separately. A screenshot alone cannot prove interactions or authorization; attach test trace or reproducible steps.
