# Live Preview access and interactive journeys

> **Owner role:** Infrastructure and deployment
> **Status:** PARTIALLY IMPLEMENTED — the six Preview URLs answer as described below (observed from the VPS itself); account provisioning, network allow-lists and the journeys from other networks are NOT VERIFIED LIVE.
> **Language / اللغة:** English · [العربية](live-preview-access-ar.md)

**Evidence legend.** VERIFIED LIVE: observed on 2026-10-21 by a user without administrative privileges on the Contabo VPS itself, read-only (the live-facts record of this handbook). VERIFIED IN CODE: read in this repository. DOCUMENTED ONLY: a repository document says it, not confirmed. OUTDATED / INCORRECT: conflicts with newer evidence. NOT VERIFIED: nobody has confirmed it; the text says how an authorized person can.

This page is for a developer, tester or owner who needs to open the running Preview and walk a business journey on it. It does not repeat the screen-by-screen detail of the journey pages; it adds the live addresses, the expected HTTP answers, and for each journey the account, the entry point and the exact clicks. The Preview is a synthetic-data, demo-labelled environment, not staging and not production (see [Preview environment](preview-environment.md)). Server layout is in the [Contabo server inventory](../08-infrastructure/contabo-server-inventory.md) and [Runtime architecture](../08-infrastructure/runtime-architecture.md).

## The six live URLs

The domain is the documented sslip.io name `167-86-90-174.sslip.io`, which resolves to the VPS address (VERIFIED LIVE). DNS, certificates and routing are explained in [DNS, HTTPS and sslip.io](../08-infrastructure/dns-https-sslip-io.md).

| Name | URL | Purpose | Observed answer (from the VPS itself) | Status | Authentication and network | Who should use it |
|---|---|---|---|---|---|---|
| Customer Web | `https://app.167-86-90-174.sslip.io/` | Customer single-page app (browse, basket, checkout, orders) | 200 | VERIFIED LIVE | Browse is anonymous; ordering needs a Keycloak sign-in. Customer IP class (`CUSTOMER_ALLOWED_IPS`) | Customers, testers, developers |
| Operations Web | `https://ops.167-86-90-174.sslip.io/` | Cook workspace and back office (one app, tabs depend on the account) | 200 | VERIFIED LIVE | Sign-in required for data. Staff IP class (`STAFF_ALLOWED_IPS`) and also the customer class (sign-in and API calls go through customer-class hosts) | Cooks, staff, testers |
| Public API configuration | `https://api.167-86-90-174.sslip.io/api/v1/configuration/public` | Public Preview configuration read by both web apps | 200 with JSON containing `orderingAvailable`, `orderLimits` and `environment` with name `PREVIEW` and `demo` true | VERIFIED LIVE | Anonymous. Customer IP class; paths under `/api/v1/ops` and `/api/v1/cook` need the staff class | Developers, smoke tests |
| Keycloak | `https://id.167-86-90-174.sslip.io/` | Sign-in for realm `matbakhy` | 302 with `Location` set to `https://kc-admin.167-86-90-174.sslip.io/admin/` | VERIFIED LIVE | Customer IP class; the realm sign-in pages are reached through the apps, not by typing this root | Nobody types it; the apps redirect to it |
| Keycloak administration | `https://kc-admin.167-86-90-174.sslip.io/` | Keycloak admin console, Preview only | 302 with `Location` set to `/admin/master/console/` | VERIFIED LIVE | Password plus one-time password, not IP restricted | Authorized administrators only |
| Media | `https://media.167-86-90-174.sslip.io/` | Public meal photos from the object store | 403, XML body, header `x-amz-request-id` | VERIFIED LIVE | Customer IP class; objects under `/matbakhy-public/` are anonymously readable | Browsers load images; nobody opens the root |

Other facts about the same hosts:

* `https://api.167-86-90-174.sslip.io/actuator/health` answers 404 (VERIFIED LIVE). That is deliberate: the proxy forwards only `/api/*`, `/oauth2/*`, `/login/oauth2/*` and `/logout` (VERIFIED IN CODE: `deploy/web-preview/Caddyfile`).
* The certificate of `app.*` is issued by Let's Encrypt and valid from 2026-10-18 to 2027-01-16 (VERIFIED LIVE). Automatic renewal is DOCUMENTED ONLY; the renewal itself is NOT VERIFIED.
* All requests above were made from the server's own address. Answers depend on the caller's IP class (see below), so another network can see different results (VERIFIED IN CODE for the rule; the allow-list values are NOT VERIFIED and are in the server's private `.env`).

### How to read each answer

| Answer | Meaning | Is it an outage? |
|---|---|---|
| 403 with the plain text "Not available" | Caddy's IP access policy: your public IP is not in the class that host needs (`respond ... "Not available" 403` in `deploy/web-preview/Caddyfile`). Ask the owner to add your IP to the right class; for Operations Web and cook or staff work that means both the staff and the customer class. | No |
| 302 on `id.*` to `kc-admin.*` | Keycloak sends its own root to the admin URL because the admin hostname is configured as the kc-admin host. The Caddyfile answers `/admin` and `/realms/master` on `id.*` with 404, so the customer host never serves the console. Expected Keycloak behaviour (VERIFIED LIVE for the redirect; the hostname setting is VERIFIED IN CODE in `deploy/web-preview/compose.yaml`). | No |
| 302 on `kc-admin.*` to `/admin/master/console/` | The Caddyfile redirects the root there. The host answers 404 "Not available" while the switch file `/srv/flags/admin-console-public` is absent, so a 302 means the console is currently published (VERIFIED IN CODE; the flag location inside the Caddy container is NOT VERIFIED). | No |
| 403 on `media.*` root | The request reached SeaweedFS (the response carries `x-amz-request-id` and an XML body, so it is not Caddy's "Not available" text). The anonymous identity may only `Read` the bucket `matbakhy-public` (VERIFIED IN CODE: `deploy/web-preview/storage/s3.json.template`), so listing the root is denied while an object URL such as `.../matbakhy-public/<object>` is readable. The exact error code in the body was not stored: do not claim more than "S3 access denied style XML". | No |
| 404 on `api.*/actuator/health` | Actuator is not proxied by design. | No |
| 404 on `id.*` for `/admin` | Admin console is never served on the customer host. | No |
| Timeout or certificate error | Real problem: see [Infrastructure troubleshooting](../08-infrastructure/infrastructure-troubleshooting.md). | Possibly |

**Warning for `kc-admin`.** The Keycloak administration console is for authorized administrators only. Signing in needs the master-realm password and a one-time password, failed sign-ins are throttled, and the host is deliberately not IP restricted (VERIFIED IN CODE: comment in the Caddyfile). Do not probe it casually, do not try sign-ins to "see if it works", and do not post its address with credentials. Procedure: [Keycloak admin console](../../operations/14-keycloak-admin-console.md).

## Access classes and accounts

| Class | Hosts and paths | Source |
|---|---|---|
| Customer | `app.*`, customer paths of `api.*`, `id.*`, `media.*` | `CUSTOMER_ALLOWED_IPS` (VERIFIED IN CODE) |
| Staff | `ops.*`, `/api/v1/ops/**`, `/api/v1/cook/**` | `STAFF_ALLOWED_IPS` (VERIFIED IN CODE) |
| Administrator console | `kc-admin.*` | Keycloak's own password plus one-time password (VERIFIED IN CODE) |

Both lists default to `ALLOWED_CLIENT_IPS`; nothing in the customer and staff classes is open to the world until the owner opens it. The exception is `kc-admin.*`, which is in neither class: the Caddyfile does not IP-restrict it and its exposure is controlled only by the `admin-console-public` switch file (VERIFIED IN CODE). The live 302 shows that switch is currently on, so that console is published, protected by password plus one-time password.

**Staff testers need both lists.** `ops.*` and the `/api/v1/ops/**` and `/api/v1/cook/**` paths check `STAFF_ALLOWED_IPS`, but the same API requests then also pass `customer_access` (`CUSTOMER_ALLOWED_IPS`), and sign-in goes through `api.*` (`/oauth2/*`) and `id.*`, which are customer-class hosts (VERIFIED IN CODE: `deploy/web-preview/Caddyfile`, host `api.*` imports `customer_access` after the staff check, host `id.*` imports `customer_access`). A tester whose address is in the staff list only can load Operations Web and still fail at sign-in or at the cook and staff API calls. When a staff or cook tester sees a failed login or missing tabs, check that their public IP is in both lists before suspecting accounts or permissions. Which addresses are in each list is NOT VERIFIED LIVE. Current values: NOT VERIFIED (private `.env`; an authorized administrator can read them following the [administrator read-only verification](../08-infrastructure/admin-readonly-verification.md)).

Synthetic account names documented for the Preview realm `matbakhy` (DOCUMENTED ONLY: `docs/operations/06-web-preview.md`, `deploy/web-preview/seed-preview.sql`, `docs/quality/owner-acceptance/fixtures.json`). Whether each account exists on the live realm is NOT VERIFIED LIVE.

| Account | Role in practice | Notes |
|---|---|---|
| `preview-customer-1`, `preview-customer-2` | Customer | Phone pre-verified in the seed |
| `preview-cook-1` | Cook of the seeded kitchen "مطبخ المعاينة" | Pickup and delivery to Test zone A; 50% deposit profile; two meals |
| `preview-ops-1` | First staff administrator | Receives only `staff.grant` and `audit.read` from the bootstrap setting |
| `preview-ops-2` | Staff-style account with no grants | Sees no staff tab |
| `owner-e2e-support`, `owner-e2e-ops`, `owner-e2e-finance`, `owner-e2e-admin-b`, `owner-e2e-cook-b`, `owner-e2e-cook-pending`, `owner-e2e-multirole`, `owner-e2e-cust-new-<n>` | Extra owner test accounts | Exist only once provisioned by an operator with the steps in [fixtures.md](../../quality/owner-acceptance/fixtures.md); provisioning state on the live Preview is NOT VERIFIED |

**Passwords are never written in this repository.** The seeded users share the value of `PREVIEW_USER_PASSWORD` in the server's private `.env` (VERIFIED IN CODE: `deploy/web-preview/deploy.sh` generates it with `init-secrets`). Authorized users get it from the owner. The owner-e2e accounts have individually generated passwords handed over the same way.

## Reading the journey tables

Each journey below has the same fields. Detailed step lists, error tables and tests are on the linked journey page; this page only adds live entry points.

* Customer Web uses hash routing (`window.location.hash`, VERIFIED IN CODE: `apps/customer-web/src/App.tsx`). Verified routes: `#/`, `#/meal/<uuid>`, `#/kitchen/<slug>`, `#/checkout`, `#/orders`, `#/orders/<uuid>`, `#/account`, `#/support/<uuid>`. Meal and order ids are not stable across environments, so start from the home page and click.
* Operations Web has no URL routes: after sign-in it shows tabs selected in application state (VERIFIED IN CODE: `apps/operations-web/src/App.tsx`). Navigation is always: open `https://ops.167-86-90-174.sslip.io/`, sign in, click the tab. Which tabs appear depends on what the server allows the account: the app sends probe requests after sign-in and shows a tab only when its probe succeeds (the Finance tab appears on the 404 answer for an unknown kitchen). A missing tab therefore usually means the account lacks the permission, but a probe that failed on the server or the network hides its tab too. Before reporting a missing permission, reload and read the status and body of the probe requests in the browser's developer tools: a 401 or 403 with an error code is an authorization answer; a 5xx, a timeout, or a 403 with the plain text "Not available" (the staff IP class) is not a permission problem.
* The top bar of Customer Web holds Cart (and Sign in) and the language toggle; the bottom navigation shows Home and, only when signed in, My orders and My account, in English (VERIFIED IN CODE: `apps/customer-web/src/App.tsx`; labels in `apps/customer-web/src/i18n.ts`). The language toggle switches Arabic RTL and English LTR.

### J1 Customer registration and login

| Field | Content |
|---|---|
| Feature and business purpose | Let a customer obtain and use an identity before ordering. Self-registration is disabled in the Preview realm (`registrationAllowed` false in `deploy/web-preview/identity/matbakhy-realm.preview.json`, VERIFIED IN CODE), so "registration" means an administrator pre-creating the account. |
| Role and account | Customer. `preview-customer-1` for login. New-customer case needs `owner-e2e-cust-new-<n>` (provisioned, NOT VERIFIED LIVE). |
| URL | `https://app.167-86-90-174.sslip.io/` |
| Navigation | Home, then "Sign in" in the top bar. |
| Steps | 1. Open the URL. 2. Press "Sign in". 3. The browser goes to the API origin `/oauth2/authorization/keycloak`, then to the `id.*` login page. 4. Enter the username and the password obtained from the owner. 5. You return to the page you came from. 6. To end the session press "Sign out". |
| Expected result | The bottom navigation now shows "My orders" and "My account"; after sign-out, basket and pending-order keys are cleared and the next sign-in asks for credentials again. |
| Backend APIs | `GET /api/v1/me`, `POST /api/v1/auth/logout` (framework route), `GET /oauth2/authorization/keycloak`, `GET /login/oauth2/code/keycloak` |
| Implementation | `apps/customer-web/src/App.tsx`, `backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java`, `backend/modules/identity/src/main/java/com/matbakhy/identity/web/ProfileController.java` |
| Related tests | Owner cases `E2E-DISC-002` (registration decision), `E2E-DISC-003`, `E2E-DISC-004`, `CFG-002`, `CFG-006`; automated: `apps/customer-web/src/App.test.tsx`, `backend/app/src/test/java/com/matbakhy/app/identity/IdentityIntegrationTest.java` |
| Limitations | No sign-up form (by design in the Preview). The default web session lasts 30 minutes with no on-screen warning. Details: [Customer web](../02-user-journeys/customer-web.md), [Authentication and MFA](../02-user-journeys/authentication-and-mfa.md). |

### J2 Meal discovery

| Field | Content |
|---|---|
| Feature and purpose | Anyone can see what approved kitchens offer before creating an account. |
| Role and account | Guest, no account. |
| URL | `https://app.167-86-90-174.sslip.io/#/` |
| Navigation | The home page is the discovery page. |
| Steps | 1. Open the URL. 2. Optionally pick an intent tile, type in the search box, choose a category chip or a zone, change the sort. 3. Switch between dishes and kitchens. 4. Open a card. |
| Expected result | Cards update from the server result with prices in EGP. An unavailable meal is marked and cannot be ordered. The demo notice is visible (the page reads `environment.demo` from the public configuration). |
| Backend APIs | `GET /api/v1/meals`, `GET /api/v1/zones`, `GET /api/v1/configuration/public`, `GET /api/v1/kitchens/{slug}` |
| Implementation | `apps/customer-web/src/Browse.tsx`, `backend/modules/catalog/src/main/java/com/matbakhy/catalog/internal/discovery/DiscoveryService.java` |
| Related tests | `E2E-DISC-001`, TC-016, TC-017; `backend/app/src/test/java/com/matbakhy/app/catalog/DiscoveryIntegrationTest.java` |
| Limitations | The web asks for 50 results once; there is no "more" control (see [Customer web](../02-user-journeys/customer-web.md)). The seed has two kitchens; the second has no login. |

### J3 Meal detail

| Field | Content |
|---|---|
| Feature and purpose | Show one meal, its slots and remaining portions so the customer can choose before adding. |
| Role and account | Guest to view; a signed-in customer to favourite or order. |
| URL | `#/meal/<uuid>` is a verified route, but the uuid is environment data: do not hard-code it. |
| Navigation | Home, click a meal card. |
| Steps | 1. Open a card. 2. Read photo, description, price and reviews. 3. Choose a slot; the quantity is bounded by the smaller of the server limit and remaining portions. |
| Expected result | The page can still show a slot with no portions left, but an exhausted slot cannot be added. |
| Backend APIs | `GET /api/v1/meals/{mealId}`, `GET /api/v1/kitchens/{kitchenId}/slots`, `GET /api/v1/kitchens/{kitchenId}/reviews` |
| Implementation | `apps/customer-web/src/Meal.tsx`, `backend/modules/catalog/src/main/java/com/matbakhy/catalog/web/MealController.java` |
| Related tests | `E2E-BASKET-012`, `E2E-DISC-107`; `backend/app/src/test/java/com/matbakhy/app/catalog/MealIntegrationTest.java` |
| Limitations | Photos load from `media.*`; if images are blank, check the customer IP class for `media.*` first. |

### J4 Basket

| Field | Content |
|---|---|
| Feature and purpose | Build a single-kitchen, single-slot basket (business rule BR-018). |
| Role and account | Guest or customer; the basket needs no session. |
| URL | `#/checkout` (the top-bar "Cart"). |
| Navigation | Meal page, choose slot and quantity, press add; then "Cart". |
| Steps | 1. Add a meal. 2. Add a meal of another kitchen or slot: a replace-or-keep prompt appears. 3. Edit quantities or remove lines at the cart page. |
| Expected result | The basket persists in the browser (`localStorage` key `matbakhy.cart`) and shows an estimate that is explicitly not the price. |
| Backend APIs | `GET /api/v1/meals/{mealId}`, `GET /api/v1/configuration/public` (line limits) |
| Implementation | `apps/customer-web/src/cart.ts`, `apps/customer-web/src/Checkout.tsx`, `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/QuoteService.java` (re-checks one kitchen and slot at quote time) |
| Related tests | TC-018, TC-147, TC-149; `E2E-BASKET-021`, `E2E-BASKET-024` |
| Limitations | The basket is per browser; it is not shared with the mobile apps. |

### J5 Checkout and quote

| Field | Content |
|---|---|
| Feature and purpose | Turn the basket into a server-priced quote. |
| Role and account | Customer with a verified phone: `preview-customer-1` (phone pre-verified in the seed, DOCUMENTED ONLY). |
| URL | `#/checkout` |
| Navigation | Sign in, then "Cart". |
| Steps | 1. If the phone is not verified, request and enter a code (the code is not exposed on the Preview for new users; see the limitation). 2. Choose pickup or delivery and an address. 3. Choose cash on receipt or deposit. 4. Press "Calculate total". |
| Expected result | The quote panel shows lines, delivery fee and total with no commission or service fee line; for a deposit it shows the amount, the remainder and the kitchen's approved recipient. A quote is valid for 5 minutes and reserves nothing. |
| Backend APIs | `POST /api/v1/me/phone/challenges`, `POST /api/v1/quotes`, `GET/POST /api/v1/me/addresses` |
| Implementation | `apps/customer-web/src/Checkout.tsx`, `apps/customer-web/src/Payment.tsx`, `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/QuoteService.java` |
| Related tests | TC-020, TC-158; `E2E-BASKET-013`, `E2E-DISC-105`; `backend/app/src/test/java/com/matbakhy/app/orders/QuoteIntegrationTest.java` |
| Limitations | The Preview has no SMS provider, so a phone number can only be verified where the seed pre-verified it (reason documented in [fixtures.md](../../quality/owner-acceptance/fixtures.md)). Real money is gated: a deposit is a manual transfer outside Matbakhy. |

### J6 Order creation

| Field | Content |
|---|---|
| Feature and purpose | Place exactly one order from an unexpired quote, even if the network drops. |
| Role and account | Customer (`preview-customer-1`). |
| URL | `#/checkout` |
| Navigation | After J5. |
| Steps | 1. Tick the acceptance of terms and cancellation policy. 2. Press "Place order". 3. If the answer is lost, reload `#/checkout` and use the retry shown for the unconfirmed order. |
| Expected result | The app opens `#/orders/<id>`; the order is `PENDING_ACCEPTANCE`, stock is reserved, prices are copied into immutable snapshots (VERIFIED IN CODE: `order_item.meal_snapshot`, unit and line prices written in `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderService.java`). A repeat of the same request does not create a second order. |
| Backend APIs | `POST /api/v1/orders` with header `Idempotency-Key`, `GET /api/v1/orders/by-request/{clientRequestId}` |
| Implementation | `OrderController.java`, `OrderService.java` under `backend/modules/orders/src/main/java/com/matbakhy/orders/` |
| Related tests | TC-022, TC-023, TC-024, TC-044; `E2E-ORDER-021`; `backend/app/src/test/java/com/matbakhy/app/orders/OrderPlacementIntegrationTest.java` |
| Limitations | The order sits in the cook's queue; with no cook acting it expires at the acceptance deadline (system job). Do not place orders on the shared Preview for load or abuse testing. |

### J7 Order tracking

| Field | Content |
|---|---|
| Feature and purpose | Let the customer follow an order and act where the server allows. |
| Role and account | Order owner. |
| URL | `#/orders` and `#/orders/<uuid>` |
| Navigation | Bottom navigation "My orders", then click an order. |
| Steps | 1. Read the timeline (it refreshes every 10 seconds). 2. While pending, cancel directly. 3. For a deposit order press "I sent the deposit". 4. Show the handover code when the order is ready. |
| Expected result | Buttons appear only if the server lists them as allowed actions; the handover code appears only in state READY or OUT_FOR_DELIVERY and only to the owner. Another customer's order id answers 404. |
| Backend APIs | `GET /api/v1/orders`, `GET /api/v1/orders/{orderId}`, `POST /api/v1/orders/{orderId}/cancel`, `POST /api/v1/orders/{orderId}/payment/transfer-report` |
| Implementation | `apps/customer-web/src/Orders.tsx`, `backend/modules/orders/src/main/java/com/matbakhy/orders/web/OrderController.java` |
| Related tests | TC-026, TC-028, TC-031; `E2E-ORDER-027`, `E2E-ORDER-120`; `backend/app/src/test/java/com/matbakhy/app/orders/OrderLifecycleIntegrationTest.java` |
| Limitations | Polling, no push; no email or SMS is delivered. |

### J8 Kitchen operations (dashboard, meals, slots, settings)

| Field | Content |
|---|---|
| Feature and purpose | Let an approved cook manage meals, dated slots with capacity, and kitchen settings. |
| Role and account | Cook: `preview-cook-1` (seeded kitchen). |
| URL | `https://ops.167-86-90-174.sslip.io/` (staff IP class required, and the customer class too because sign-in and the API use customer-class hosts) |
| Navigation | Open the URL, sign in, then tabs Home, Orders, Meals, Slots, Statement, Insights, Settings. |
| Steps | 1. Sign in. 2. "Meals": create or edit a meal, publish, pause or archive, read the price history. 3. "Slots": create a slot and set capacity. 4. "Settings": description, pickup, delivery zones, pause or resume, deposit payment profile. |
| Expected result | Only the caller's own kitchen is shown. Paused or draft meals disappear from discovery. A changed payment recipient stays hidden from customers until staff approve it. |
| Backend APIs | `GET /api/v1/cook/dashboard`, `/api/v1/cook/meals`, `/api/v1/cook/slots`, `GET/PUT /api/v1/cook/payment-profile` |
| Implementation | `apps/operations-web/src/cook/Meals.tsx`, `Slots.tsx`, `Settings.tsx`, `Payments.tsx` (same folder), backend `backend/modules/cooks/src/main/java/com/matbakhy/cooks/` |
| Related tests | TC-008, TC-011, TC-153; `E2E-COOK-054`, `E2E-FIN-103`; `apps/operations-web/e2e/cook-workspace.e2e.ts` |
| Limitations | An account with no approved kitchen sees only the application status panel. Detail: [Cook operations](../02-user-journeys/cook-operations.md). |

### J9 Kitchen order processing

| Field | Content |
|---|---|
| Feature and purpose | Move a customer order through accept, prepare, ready, dispatch and handover with server-checked transitions. |
| Role and account | Cook (`preview-cook-1`) plus a customer (`preview-customer-1`) who placed an order for that kitchen. |
| URL | `https://ops.167-86-90-174.sslip.io/` |
| Navigation | Sign in, tab "Orders" (or the shortcut on Home). |
| Steps | 1. Open the pending order before its deadline and press accept (or reject with a reason). 2. For a deposit order, report that the deposit was received before preparing. 3. Prepare, then Ready. 4. Pickup: type the customer's 6-digit handover code. Delivery: dispatch with a courier reference, then the code. |
| Expected result | The customer's tracking page follows within about 10 seconds. Five wrong codes lock the handover challenge and need an operations recovery. |
| Backend APIs | `GET /api/v1/cook/orders`, `POST /api/v1/cook/orders/{orderId}/accept`, `.../prepare`, `.../ready`, `.../dispatch`, `.../handover`, `.../payment/receipt` |
| Implementation | `apps/operations-web/src/cook/Orders.tsx`, `backend/modules/orders/src/main/java/com/matbakhy/orders/web/CookOrderController.java` |
| Related tests | TC-026, TC-027, TC-029, TC-031, TC-159; `E2E-ORDER-025`, `E2E-ORDER-033`; `backend/app/src/test/java/com/matbakhy/app/payments/DepositPaymentIntegrationTest.java` |
| Limitations | The cook web app has no push alert for new orders. Courier is a free-text reference. Detail: [Cook operations](../02-user-journeys/cook-operations.md). |

### J10 Operations administration

| Field | Content |
|---|---|
| Feature and purpose | Staff approve cooks, moderate content, watch orders, and grant permissions. In code there are no staff roles, only permission grants. |
| Role and account | `preview-ops-1` holds only `staff.grant` and `audit.read`, so it sees the Staff permissions tab and the Security tab (own two-step preference; policy and reset controls need the MFA permissions, which this account does not hold). Cook review, moderation and order monitoring need `owner-e2e-ops` or an account granted those permissions (provisioning NOT VERIFIED LIVE). |
| URL | `https://ops.167-86-90-174.sslip.io/` |
| Navigation | Sign in, then tabs: Cook applications, Catalogue moderation, Order monitoring, Operating configuration, Staff permissions, Security (each only if allowed). |
| Steps | 1. Sign in as staff. 2. "Cook applications": start review, then approve, request changes or reject, always with a reason. 3. "Staff permissions": grant or revoke a permission to another account. |
| Expected result | High-risk actions (cook decision, grant and revoke, configuration write) are refused without recent MFA and the page offers a step-up sign-in. Every grant is audited. |
| Backend APIs | `GET /api/v1/ops/cooks`, `/api/v1/ops/catalogue`, `/api/v1/ops/orders`, `/api/v1/ops/staff` and their decision and grant endpoints |
| Implementation | `apps/operations-web/src/ops/` (Reviews, Catalogue, OpsOrders, Configuration, Staff), `backend/modules/identity/src/main/java/com/matbakhy/identity/web/StaffController.java`, `backend/app/src/main/java/com/matbakhy/app/security/StaffAssurance.java` |
| Related tests | TC-111, TC-123, TC-126; `E2E-OPS-105`; `apps/operations-web/src/App.test.tsx` |
| Limitations | `audit.read` and `integration.ops` have API endpoints but no tab. Permission matrix and tab logic: [Backoffice and administrator journeys](../02-user-journeys/backoffice-admin.md). |

### J11 Finance and support

| Field | Content |
|---|---|
| Feature and purpose | Support staff answer customer cases; finance staff approve payment recipients, decide deposit disputes, and handle refunds and statements. |
| Role and account | Support: `owner-e2e-support`. Finance: `owner-e2e-finance`. Customer side of support: `preview-customer-1`. Provisioning of the owner accounts is NOT VERIFIED LIVE. |
| URL | Customer: `#/account` then `#/support/<uuid>`. Staff: `https://ops.167-86-90-174.sslip.io/` |
| Navigation | Customer: "My account", support section. Staff: tab "Support" or "Finance". |
| Steps | Customer opens a case about an order. Support reads the queue, replies, resolves with a code and a reason. Finance opens the payment panels inside "Finance" and approves a recipient or decides a dispute. |
| Expected result | A resolved case closes only with a resolution code and reason; a customer reply reopens it. A deposit order cannot be prepared before the kitchen reports receipt. |
| Backend APIs | `GET/POST /api/v1/support/cases`, `/api/v1/ops/cases`. Payments: `POST /api/v1/cook/orders/{orderId}/payment/receipt`, `GET /api/v1/ops/payment-recipients`, `POST /api/v1/ops/kitchens/{kitchenId}/payment-recipient/approval`, `GET /api/v1/ops/payments`, `POST /api/v1/ops/orders/{orderId}/payment/decision`. Statements, refunds and settlements: `/api/v1/ops/finance/**` |
| Implementation | `apps/operations-web/src/ops/Cases.tsx`, `Finance.tsx`, `Payments.tsx` (same folder), `backend/modules/support/src/main/java/com/matbakhy/support/web/SupportController.java`, `backend/modules/orders/src/main/java/com/matbakhy/orders/web/PaymentController.java` |
| Related tests | TC-041, TC-032, TC-159, TC-160; `E2E-OPS-105`, `E2E-FIN-103` |
| Limitations | The finance cash-verification screen is absent from the web (API only). No bank API; a human reconciles deposits. Detail: [Finance and support](../02-user-journeys/finance-and-support.md). |

### J12 Authentication and multi-factor authentication

| Field | Content |
|---|---|
| Feature and purpose | Protect accounts with an optional (customer) or policy-required (kitchen, staff) one-time password. |
| Role and account | Customer: `preview-customer-1`. Policy administration needs a second administrator with `security.mfa_policy` (such as `owner-e2e-admin-b`, NOT VERIFIED LIVE). |
| URL | Customer Web `#/account` (security section); Operations Web tab "Security". |
| Navigation | Customer: "My account", security. Staff: Security tab. |
| Steps | 1. Open the security section. 2. Read the policy mode and status. 3. If optional and off, turn on; the page sends you to the secure sign-in to enrol an authenticator app if needed. 4. Turning off needs a recent step-up. |
| Expected result | Policy modes are disabled, optional or required. Without a recent MFA proof the API answers 403 `STEP_UP_REQUIRED`. |
| Backend APIs | `GET /api/v1/me/security`, `PUT /api/v1/me/security/mfa`; staff policy and reset endpoints |
| Implementation | `apps/customer-web/src/Security.tsx`, `apps/operations-web/src/ops/Security.tsx`, `backend/app/src/main/java/com/matbakhy/app/mfa/MfaController.java` |
| Related tests | `CFG-008`, `CFG-102`; `backend/app/src/test/java/com/matbakhy/app/mfa/MfaPolicyIntegrationTest.java` |
| Limitations | TOTP only, no recovery codes. Whether the live Keycloak realm already has the step-up migration applied is NOT VERIFIED. The one-time password for the Keycloak master realm is a different thing (administrator console, see the warning above). Detail: [Authentication and MFA](../02-user-journeys/authentication-and-mfa.md). |

### J13 Price changes at checkout

The rule: a completed or placed order keeps the price it was created with; a price that changed between quote and placement is refused with a new quote required. This was confirmed in code and tests.

| Field | Content |
|---|---|
| Feature and purpose | Never charge a price the customer has not seen, and never alter a committed order's price (decision D13). |
| Role and account | Customer `preview-customer-1` with a kitchen cook `preview-cook-1` changing a meal price in another browser. |
| URL | Customer Web `#/checkout`; Operations Web tab "Meals". |
| Navigation | Customer calculates a quote. The cook edits the meal price. The customer then presses "Place order". |
| Steps | 1. Customer: quote and stop before placing. 2. Cook: Meals, edit the price, save. 3. Customer: press "Place order". |
| Expected result | The server answers 409 with code `PRICE_CHANGED` and places nothing; the web app requests a fresh quote and shows what changed; the customer must accept the new total explicitly. An order already placed keeps its original prices. |
| Backend APIs | `POST /api/v1/quotes`, `POST /api/v1/orders` |
| Implementation | VERIFIED IN CODE: `requireValid` in `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/QuoteService.java` throws `PRICE_CHANGED` with a detail of `POLICY_CHANGED`, `MEAL_CHANGED` (revision or unit price differs), `TOTAL_CHANGED` (delivery fee or total differs) or `PAYMENT_CHANGED` (deposit terms or recipient differ); it locks the pricing rows first so a concurrent edit is serialised. `OrderService` copies each item's snapshot and unit and line price into the order at placement. The web reaction is in `apps/customer-web/src/Checkout.tsx` (on `PRICE_CHANGED` it re-quotes instead of resubmitting). |
| Related tests | TC-154, TC-155, TC-156, TC-153; owner cases `E2E-BASKET-014`, `E2E-BASKET-015`; `backend/app/src/test/java/com/matbakhy/app/orders/PriceChangeIntegrationTest.java`, `backend/app/src/test/java/com/matbakhy/app/orders/QuoteIntegrationTest.java`, `apps/customer-web/src/App.test.tsx` (block "D13: changed prices need informed reconfirmation") |
| Limitations | A change of address zone surfaces as `STATE_CONFLICT` with `ADDRESS_CHANGED`, not `PRICE_CHANGED`. Whether the live Preview runs the build that contains this behaviour: confirm through [Deployment pipeline](../08-infrastructure/deployment-pipeline.md); not checked here. |

## Quick checks that are safe

| Command | Machine and user | Read-only | Expected | If it fails |
|---|---|---|---|---|
| `curl -s -o /dev/null -w "%{http_code}\n" https://app.167-86-90-174.sslip.io/` | Your laptop, any user | Yes | 200 from an allow-listed IP, 403 otherwise | 403: your IP is outside the customer class, ask the owner. Timeout: see troubleshooting |
| `curl -s https://api.167-86-90-174.sslip.io/api/v1/configuration/public` | Your laptop, any user | Yes | JSON with `environment.name` PREVIEW | Same as above |
| `curl -s -o /dev/null -w "%{http_code} %{redirect_url}\n" https://id.167-86-90-174.sslip.io/` | Your laptop, any user | Yes | 302 and the kc-admin address | Not an error; do not follow it and sign in unless you are an administrator |

The repository's own check is `deploy/web-preview/smoke.sh <preview-domain>` (VERIFIED IN CODE: its header comment says it is read-only and creates no account, order or upload; it was not run for this page). Use the command that matches the machine:

* A machine in both the customer and the staff class (for example the VPS itself): `sh deploy/web-preview/smoke.sh 167-86-90-174.sslip.io`. It loads the operations app and fails on a 403 from `ops.*`.
* A machine in the customer class but outside the staff class: `SMOKE_OPS=skip sh deploy/web-preview/smoke.sh 167-86-90-174.sslip.io`. With `SMOKE_OPS=skip` the script expects the 403 from `ops.*` instead of loading the operations app.

## Readiness checklist before a tester starts

The journeys above assume a ready fixture. No document or tool guarantees one at any given moment, so the person who asks a tester to start confirms the following and records the date:

1. The owner (the only role that can grant access) has told the tester which IP class applies and has supplied the test account credentials through a private channel. Credentials are never written in this repository.
2. The three safe checks above answer as documented.
3. For an order journey (J6 to J9): the synthetic seed has been loaded once (see the [backup and recovery page](../08-infrastructure/backup-restore-disaster-recovery.md) for why the seed is not repeatable), the test customer's phone is verified, the kitchen has an open slot with stock, and the payment method offered at checkout is eligible for the chosen kitchen. Whether the live fixture currently satisfies this is NOT VERIFIED.
4. If an order journey fails at checkout, stop and report the screen, the time and the error text to the owner. Do not retry in a loop and do not create accounts to work around it.

## Known limitations and documentation gaps

* The owner-e2e accounts and the pre-verified phones are provisioned per run; their presence on the live Preview is NOT VERIFIED LIVE. An authorized administrator can list realm users with the Keycloak admin tooling described in [Keycloak admin console](../../operations/14-keycloak-admin-console.md).
* Values of `CUSTOMER_ALLOWED_IPS` and `STAFF_ALLOWED_IPS`, and whether `ufw` is active, are NOT VERIFIED LIVE; see [administrator read-only verification](../08-infrastructure/admin-readonly-verification.md).
* Existing pages (`docs/operations/06-web-preview.md`, `deploy/web-preview/README.md`) were written for a proposed Hetzner host; the running host is Contabo. Treat host-specific sizing text in those documents as OUTDATED / INCORRECT for the live Preview.
* The existing [Preview environment](preview-environment.md) page lists `kc-admin` as opened by `deploy.sh admin-console enable`; the live 302 confirms it is currently published, but who enabled it and when is NOT VERIFIED.
* Deep links to operations pages do not exist; navigation is by tab only (VERIFIED IN CODE).
