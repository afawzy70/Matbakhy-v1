# Architecture diagrams

> **Owner role:** Backend architecture
> **Status:** PARTIALLY IMPLEMENTED — diagrams show what the code does today; planned or disabled parts are marked in the node text

All diagrams are Mermaid text. Edit them in place; GitHub renders them. Every diagram has a caption naming the files it was derived from, so a change in those files is a reason to re-check the diagram (policy: [documentation maintenance](../05-developer-workflow/documentation-maintenance.md)). Prose for each area lives in the architecture pages: [system overview](system-overview.md), [backend](backend-architecture.md), [frontend](frontend-architecture.md), [mobile](mobile-architecture.md), [database](database-architecture.md), [identity and security](identity-and-security.md), [integration and events](integration-and-events.md), [infrastructure](infrastructure-and-deployment.md).

Contents: [1 system context](#1-system-context), [2 containers](#2-container-architecture), [3 backend components](#3-backend-components), [4 web](#4-web-architecture), [5 mobile](#5-mobile-architecture), [6 OIDC](#6-authentication-oidc), [7 MFA step-up](#7-mfa-step-up), [8 order placement](#8-order-placement-sequence), [9 order states](#9-order-fulfilment-states), [10 price change](#10-price-change-and-stale-quote-reconfirmation), [11 deposits](#11-deposits-cash-and-settlement), [12 ER](#12-database-relationships), [13 CI/CD](#13-cicd), [14 preview infrastructure](#14-preview-infrastructure).

## 1 System context

```mermaid
flowchart LR
    customer["Customer<br/>(browser or phone)"]
    cook["Cook / kitchen owner<br/>(operations web)"]
    staff["Platform staff<br/>(operations web)"]
    owner["Owner / operator<br/>(SSH, GitHub)"]
    sys["Matbakhy platform<br/>customer web + customer mobile + operations web<br/>+ one Spring Boot API"]
    idp["Keycloak<br/>(OIDC identity provider)"]
    store["S3-compatible object storage<br/>(SeaweedFS in preview, s3mock locally)"]
    mail["Email / SMS / push providers<br/>PLANNED, none wired (fakes only)"]
    pay["Payment provider / bank<br/>NOT INTEGRATED (manual deposits only)"]
    customer --> sys
    cook --> sys
    staff --> sys
    sys <--> idp
    sys <--> store
    sys -.-> mail
    sys -.-> pay
    owner --> sys
```

Caption: derived from `backend/app/src/main/java/com/matbakhy/app/providers/ProviderConfiguration.java` (fake or refused providers outside LOCAL/CI/PREVIEW), `deploy/web-preview/compose.yaml`, `infra/compose.yaml`, `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/PaymentService.java` (no gateway call; reports only).

## 2 Container architecture

```mermaid
flowchart TB
    subgraph clients["Clients"]
        cw["customer-web<br/>React 19 + Vite SPA<br/>apps/customer-web"]
        ow["operations-web<br/>React 19 + Vite SPA<br/>apps/operations-web"]
        cm["customer-mobile<br/>Expo / React Native<br/>apps/customer-mobile"]
    end
    subgraph shared["Shared TypeScript packages"]
        ac["packages/api-client<br/>generated from contracts/openapi.yaml"]
        dt["packages/design-tokens"]
    end
    subgraph server["Server side"]
        proxy["Caddy proxy (preview only)<br/>TLS, allow-lists, headers, serves SPAs"]
        api["Spring Boot API<br/>backend/app + 9 modules + shared"]
        mgmt["Management port 8081<br/>health + Prometheus (internal)"]
        pg[("PostgreSQL 16<br/>app data, sessions, outbox")]
        kc["Keycloak 26.8<br/>realm matbakhy"]
        s3[("Object storage<br/>private + public buckets")]
        obs["Loki, Alloy, Prometheus,<br/>Alertmanager, Grafana (optional)"]
    end
    cw --> ac
    ow --> ac
    cm --> ac
    cw -->|"cookie session + CSRF"| proxy
    ow -->|"cookie session + CSRF"| proxy
    cm -->|"Bearer JWT"| proxy
    proxy --> api
    api --> pg
    api -->|"JWKS, token, admin REST"| kc
    api -->|"S3 API, presigned URLs"| s3
    kc --> pg
    api --- mgmt
    mgmt --> obs
```

Caption: derived from `deploy/web-preview/compose.yaml`, `deploy/web-preview/Caddyfile`, `backend/pom.xml`, `apps/customer-web/package.json`, `apps/operations-web/package.json`, `apps/customer-mobile/package.json`, `packages/api-client/package.json`. In local development there is no Caddy: the SPAs run on Vite ports and talk to the API directly (`infra/compose.yaml`).

## 3 Backend components

```mermaid
flowchart TB
    subgraph app["backend/app (composition layer: com.matbakhy.app)"]
        sec["security: SecurityConfiguration, AccountMapper,<br/>MfaEnforcementFilter, StaffAssurance"]
        mfa["mfa: MfaPolicyService, MfaController,<br/>MfaAdminController, KeycloakAdmin"]
        web["web: ApiExceptionHandler, RequestIdFilter"]
        prov["providers: S3ObjectStorage, Fake* adapters"]
        idem["idempotency: JdbcIdempotentCommands"]
        obsv["observability: OperationalMetrics"]
    end
    subgraph modules["backend/modules (each: web, api, internal)"]
        identity
        cooks
        catalog
        availability
        orders
        finance
        support
        notifications
        operations
    end
    shared["backend/shared<br/>Money, ErrorCode, ApiException,<br/>IdempotentCommands, DomainModule"]
    app --> modules
    modules --> shared
    orders -->|api| identity
    orders -->|api| catalog
    orders -->|api| cooks
    orders -->|api| availability
    orders -->|api| operations
    catalog -->|api| availability
    catalog -->|api| identity
    cooks -->|api| catalog
    finance -->|api| cooks
    support -->|api| orders
    notifications -->|api| orders
    notifications -->|api| cooks
    identity -->|api| operations
```

Caption: edges are the Maven dependencies declared in each `backend/modules/*/pom.xml`, always used through the other module's `api` package (rule enforced by `backend/app/src/test/java/com/matbakhy/app/architecture/ModuleRules.java`). Only the main dependencies are drawn; every module also depends on `identity` and `operations` for permissions and audit as its pom declares.

## 4 Web architecture

```mermaid
flowchart LR
    subgraph cwb["customer-web (hash routes)"]
        capp["App.tsx<br/>route + locale + me state"]
        cscreens["Browse, Meal, Kitchen, Checkout,<br/>Orders, Payment, Account, Security"]
        ccart["cart.ts<br/>localStorage basket + pending placement"]
        capi["api.ts createApi()"]
    end
    subgraph owb["operations-web (tab shell)"]
        oapp["App.tsx<br/>checkSession probes + allowedTabs"]
        cooks["cook/*: Dashboard, Orders, Meals,<br/>Slots, Payments, Statement, Settings"]
        ops["ops/*: Reviews, Catalogue, Cases, OpsOrders,<br/>Finance, Payments, Configuration, Staff, Security"]
        oapi["api.ts createApi()"]
    end
    client["packages/api-client<br/>openapi-fetch + generated schema.ts"]
    tokens["packages/design-tokens<br/>tokens.css, Cairo font, direction()"]
    api["Spring Boot API"]
    capp --> cscreens --> capi
    cscreens --> ccart
    oapp --> cooks --> oapi
    oapp --> ops --> oapi
    capi --> client
    oapi --> client
    client -->|"credentials: include<br/>X-XSRF-TOKEN, Accept-Language"| api
    cwb --> tokens
    owb --> tokens
```

Caption: derived from `apps/customer-web/src/App.tsx`, `apps/customer-web/src/api.ts`, `apps/customer-web/src/cart.ts`, `apps/operations-web/src/App.tsx`, `apps/operations-web/src/api.ts`, `packages/api-client/package.json`.

## 5 Mobile architecture

```mermaid
flowchart TB
    subgraph rn["apps/customer-mobile (Expo / React Native)"]
        entry["index.ts + App.tsx<br/>NavigationContainer + linking"]
        nav["Tabs: Discover, Favorites, Orders, Account<br/>Stack: Meal, Cart, Order, Notifications, Support"]
        ctx["AppContext.tsx<br/>session, locale, step-up flag"]
        sess["session.ts + tokenStore.ts<br/>refresh + expo-secure-store"]
        auth["auth.ts<br/>expo-auth-session, PKCE, system browser"]
        mapi["mobileApi.ts<br/>Bearer token middleware"]
        basket["basket.ts + placement.ts<br/>secure-store basket, pending placement"]
    end
    kc["Keycloak (client matbakhy-mobile)"]
    api["Spring Boot API (bearer chain)"]
    entry --> nav --> ctx
    ctx --> sess
    ctx --> auth
    ctx --> mapi
    nav --> basket
    auth -->|"auth code + PKCE"| kc
    sess -->|"refresh_token grant"| kc
    mapi -->|"Authorization: Bearer"| api
```

Caption: derived from `apps/customer-mobile/src/App.tsx`, `apps/customer-mobile/src/AppContext.tsx`, `apps/customer-mobile/src/auth.ts`, `apps/customer-mobile/src/tokenStore.ts`, `apps/customer-mobile/src/mobileApi.ts`, `apps/customer-mobile/src/basket.ts`, `apps/customer-mobile/src/screens/navigation.ts`.

## 6 Authentication (OIDC)

```mermaid
sequenceDiagram
    autonumber
    participant B as Browser (customer-web or operations-web)
    participant A as API (webChain)
    participant K as Keycloak
    participant D as PostgreSQL
    B->>A: GET /oauth2/authorization/keycloak?returnTo=...
    A->>B: 302 to Keycloak authorize (code, S256 PKCE, scope openid profile email)
    B->>K: login (password, OTP only if acr mfa asked)
    K->>B: 302 to /login/oauth2/code/keycloak?code=...
    B->>A: callback with code
    A->>K: back-channel token exchange (client secret basic)
    K->>A: id_token + access_token
    A->>D: INSERT session (Spring Session JDBC), cookie MATBAKHY_SESSION
    A->>B: 302 to post-login redirect, sets XSRF-TOKEN cookie
    B->>A: GET /api/v1/me (cookie)
    A->>D: AccountMapper: resolve app_user by issuer+subject, load grants
    A->>B: 200 profile
    Note over B,A: Mobile instead uses PKCE against Keycloak directly and sends Authorization Bearer to bearerChain, which validates issuer, audience matbakhy-api, signature and expiry.
```

Caption: derived from `backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java`, `backend/app/src/main/java/com/matbakhy/app/security/AccountMapper.java`, `backend/app/src/main/java/com/matbakhy/app/security/WebAccountFilter.java`, `apps/customer-mobile/src/auth.ts`, `infra/identity/matbakhy-realm.json`.

## 7 MFA step-up

```mermaid
sequenceDiagram
    autonumber
    participant U as Client
    participant A as API
    participant P as MfaPolicyService
    participant K as Keycloak
    U->>A: protected call (token or session without MFA assertion)
    A->>P: decide(accountId) reads mfa_policy, kitchen, grants, preference, reset
    P-->>A: required = true
    A-->>U: 403 STEP_UP_REQUIRED
    U->>K: authorize with acr_values "mfa 2" (web adds max_age=0, mobile prompt=login)
    K->>U: password if needed, then OTP or TOTP enrolment
    U->>A: retry with new token or session (acr, amr, auth_time)
    A->>P: satisfiedBy(claims) acr in mfa-acr-values or amr otp/mfa/hwk, and auth_time not older than last reset
    A-->>U: 200
    Note over A: High-risk staff endpoints also call @staffAssurance.recentMfa which requires auth_time within step-up-max-age (default 10 minutes).
```

Caption: derived from `backend/app/src/main/java/com/matbakhy/app/security/MfaEnforcementFilter.java`, `backend/app/src/main/java/com/matbakhy/app/security/StaffAssurance.java`, `backend/app/src/main/java/com/matbakhy/app/mfa/MfaPolicyService.java`, `backend/app/src/main/resources/db/migration/V31__mfa_policy.sql`, `apps/customer-mobile/src/auth.ts`.

## 8 Order placement (sequence)

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant Q as QuoteController / QuoteService
    participant O as OrderController / OrderService
    participant I as JdbcIdempotentCommands
    participant S as JdbcSlotInventory
    participant D as PostgreSQL
    C->>Q: POST quote (meals, slot, mode, address, payment method)
    Q->>D: INSERT quote + quote_item (prices from server, expires in ttl, holds no stock)
    Q-->>C: quoteId, totals, expiresAt
    C->>O: POST /api/v1/orders (Idempotency-Key, quoteId, clientRequestId, accepted policy versions)
    O->>I: execute(customer, order.place, key)
    I->>D: one transaction begins
    O->>D: advisory lock on customer + clientRequestId
    O->>Q: requireValid(quote): ttl, policy version, meal revision and price, address, total, payment terms
    O->>S: reserve(slot, meals) locks cook_slot then meal_slot rows in id order
    S->>D: reserved += n
    O->>D: INSERT customer_order PENDING_ACCEPTANCE, order_item snapshots, stock_allocation RESERVED
    O->>D: INSERT order_event 1, outbox_event ORDER_PLACED, order_payment, cash_obligation DUE
    I->>D: store idempotency record, commit
    O-->>C: 201 order view
```

Caption: derived from `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderService.java` (`place`, `placeOnce`), `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/QuoteService.java`, `backend/modules/availability/src/main/java/com/matbakhy/availability/internal/JdbcSlotInventory.java`, `backend/app/src/main/java/com/matbakhy/app/idempotency/JdbcIdempotentCommands.java`.

## 9 Order fulfilment states

```mermaid
stateDiagram-v2
    [*] --> PENDING_ACCEPTANCE: place (customer)
    PENDING_ACCEPTANCE --> ACCEPTED: cook accepts before accept_by
    PENDING_ACCEPTANCE --> REJECTED: cook rejects
    PENDING_ACCEPTANCE --> CANCELLED: customer cancels
    PENDING_ACCEPTANCE --> EXPIRED: OrderExpiryJob after accept_by
    ACCEPTED --> PREPARING: cook prepare
    ACCEPTED --> CANCELLED: cook cancels
    PREPARING --> READY: cook ready (handover code created)
    PREPARING --> CANCELLED: staff exception cancel
    READY --> OUT_FOR_DELIVERY: cook dispatch (DELIVERY only)
    READY --> COMPLETED: handover code (PICKUP only) or staff override
    READY --> CANCELLED: staff exception cancel
    OUT_FOR_DELIVERY --> COMPLETED: handover code or staff override
    OUT_FOR_DELIVERY --> DELIVERY_FAILED: cook reports failure
    COMPLETED --> [*]
    REJECTED --> [*]
    CANCELLED --> [*]
    EXPIRED --> [*]
    DELIVERY_FAILED --> [*]
```

Caption: derived from `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderLifecycleService.java`, `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderExpiryJob.java` and the state check in `backend/app/src/main/resources/db/migration/V11__orders.sql`; terminal states are frozen by the trigger in `backend/app/src/main/resources/db/migration/V12__order_lifecycle.sql`. Business meaning: [order lifecycle](../01-business/order-lifecycle.md).

## 10 Price change and stale-quote reconfirmation

```mermaid
sequenceDiagram
    autonumber
    participant K as Kitchen (cook web)
    participant C as Customer client
    participant S as Server
    participant D as PostgreSQL
    C->>S: POST quote
    S->>D: quote rows (meal_revision, unit price, total, policy_version)
    S-->>C: quote A (total 100.00 EGP)
    K->>S: edit meal price
    S->>D: new immutable meal_revision, old rows untouched
    C->>S: POST /api/v1/orders with quote A
    S->>D: lock policy + pricing rows, recompute terms
    S-->>C: 409 PRICE_CHANGED (reason MEAL_CHANGED, POLICY_CHANGED, TOTAL_CHANGED or PAYMENT_CHANGED)
    Note over C: Checkout keeps nothing ordered, calls POST quote again
    C->>S: POST quote
    S-->>C: quote B (new total)
    Note over C: Customer must accept the new total explicitly
    C->>S: POST /api/v1/orders with quote B and a fresh attempt
    S-->>C: 201 order at the quoted price
```

Caption: derived from `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/QuoteService.java` (`requireValid`), `apps/customer-web/src/Checkout.tsx` (`PRICE_CHANGED` branch), `apps/customer-web/src/i18n.ts` (price change strings), `packages/api-client/test/priceChanges.test.ts`. A QUOTE_EXPIRED or STOCK_CHANGED answer takes the same re-quote path in the UI: NOT VERIFIED for every client screen.

## 11 Deposits, cash and settlement

```mermaid
stateDiagram-v2
    direction LR
    state "order_payment (deposit)" as dep {
        [*] --> WAITING_FOR_DEPOSIT: placement with method DEPOSIT
        WAITING_FOR_DEPOSIT --> CUSTOMER_TRANSFER_REPORTED: customer reports transfer
        WAITING_FOR_DEPOSIT --> KITCHEN_RECEIPT_REPORTED: kitchen reports receipt
        CUSTOMER_TRANSFER_REPORTED --> KITCHEN_RECEIPT_REPORTED: kitchen reports receipt (amount equals deposit)
        DISPUTED --> KITCHEN_RECEIPT_REPORTED: kitchen reports receipt
        WAITING_FOR_DEPOSIT --> DISPUTED: kitchen reports not received or mismatch
        CUSTOMER_TRANSFER_REPORTED --> DISPUTED: kitchen reports problem
        WAITING_FOR_DEPOSIT --> EXPIRED: order ended unsuccessfully (REJECTED, EXPIRED, CANCELLED, DELIVERY_FAILED) with no report
        CUSTOMER_TRANSFER_REPORTED --> RECONCILE_PENDING: order ended unsuccessfully
        KITCHEN_RECEIPT_REPORTED --> RECONCILE_PENDING: order ended unsuccessfully
        DISPUTED --> RECONCILE_PENDING: order ended unsuccessfully
        DISPUTED --> REFUND_OWED: staff decision with reason
        RECONCILE_PENDING --> REFUND_OWED: staff decision with reason
        DISPUTED --> CLOSED_NO_FUNDS: staff decision with reason
        RECONCILE_PENDING --> CLOSED_NO_FUNDS: staff decision with reason
        REFUND_OWED --> REFUNDED: kitchen reports refund sent
        RECONCILE_PENDING --> REFUNDED: kitchen reports refund sent
    }
    state "cash_obligation" as cash {
        [*] --> DUE: placement
        DUE --> REPORTED: kitchen reports cash collected, amount as expected
        DUE --> DISPUTED_CASH: reported amount differs
        REPORTED --> VERIFIED: finance verifies
        REPORTED --> DISPUTED_CASH: finance disputes
        DUE --> NOT_DUE: order rejected, cancelled or expired
    }
```

Caption: derived from `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/PaymentService.java`, `backend/app/src/main/resources/db/migration/V24__deposit_payments.sql`, `backend/app/src/main/resources/db/migration/V25__payment_reconciliation.sql`, `backend/app/src/main/resources/db/migration/V13__cash_and_ledger.sql`, `backend/modules/finance/src/main/java/com/matbakhy/finance/internal/CashService.java`. `DISPUTED_CASH` is the `DISPUTED` value of `cash_obligation` and `cash_report`, renamed here only because Mermaid state names must be unique. The "order ended unsuccessfully" transitions run only for REJECTED, EXPIRED, CANCELLED and DELIVERY_FAILED (`OrderLifecycleService.java:369` skips COMPLETED), so a completed order keeps its receipt state. Every deposit state is a statement by the customer, kitchen or staff; none means "bank verified" and no Matbakhy account holds the money. Settlement batches (`settlement_batch`: DRAFT, REVIEWED, PAYMENT_RECORDED, RECONCILED, CANCELLED) group commission ledger groups; with the zero-commission rule (`backend/app/src/main/resources/db/migration/V23__launch_commission.sql`) they are dormant for live data. See [pricing and payments](../01-business/pricing-and-payments.md).

## 12 Database relationships

```mermaid
erDiagram
    app_user ||--o{ user_grant : "staff permissions"
    app_user ||--o| kitchen : "owns (owner_id unique)"
    app_user ||--o{ address : has
    app_user ||--o{ customer_order : places
    app_user ||--o{ idempotency_record : "actor"
    app_user ||--o| user_mfa_preference : "preference"
    app_user ||--o| user_mfa_reset : "last reset"
    kitchen ||--o{ meal : sells
    kitchen ||--o{ cook_slot : schedules
    kitchen ||--o| kitchen_payment_profile : "payment terms"
    kitchen }o--o{ service_zone : "kitchen_zone"
    meal ||--o{ meal_revision : "immutable revisions"
    cook_slot ||--o{ meal_slot : "capacity per meal"
    meal ||--o{ meal_slot : "offered in"
    quote ||--o{ quote_item : lines
    quote ||--o| customer_order : "used once"
    customer_order ||--|{ order_item : "snapshot lines"
    customer_order ||--o{ stock_allocation : "reserves"
    meal_slot ||--o{ stock_allocation : "counts"
    customer_order ||--|{ order_event : "append-only history"
    customer_order ||--|| order_payment : "terms + state"
    order_payment ||--o{ payment_event : "append-only"
    customer_order ||--|| cash_obligation : "cash due"
    customer_order ||--o| cash_report : "kitchen report"
    customer_order ||--o| handover_challenge : "code hash"
    customer_order ||--o{ support_case : "disputes"
    support_case ||--o{ case_message : thread
    customer_order ||--o{ ledger_group : "commission posting"
    ledger_group ||--|{ ledger_entry : "double entry"
    settlement_batch ||--o{ settlement_line : contains
    ledger_group ||--o{ settlement_line : "history, at most one active line"
    outbox_event ||--o{ notification : "source_event_id"
    notification ||--o{ notification_delivery : channels
    customer_order ||--o| review : "one per order"
```

Caption: derived from the foreign keys in `backend/app/src/main/resources/db/migration/V1__baseline_identity_and_idempotency.sql` through `V31__mfa_policy.sql` (for example `V11__orders.sql`, `V8__slots.sql`, `V13__cash_and_ledger.sql`, `V14__notifications.sql`, `V17__support.sql`, `V18__finance_operations.sql`, `V20__favorites_and_reviews.sql`, `V24__deposit_payments.sql`). Only main tables are drawn. Columns are listed in [database architecture](database-architecture.md).

## 13 CI/CD

```mermaid
flowchart LR
    dev["Developer pushes branch / PR"] --> ci["ci.yml Product CI<br/>workspace, local-stack, design, secrets"]
    dev --> bp["blueprint-integrity.yml"]
    dev --> nb["native-build.yml<br/>Android + iOS builds and native checks"]
    dev --> of["owner-e2e-fixtures.yml<br/>(fixture validation)"]
    merge["Merge / push to develop"] --> dp["deploy-preview.yml"]
    dp --> wait["Wait for green Product CI at same SHA"]
    wait --> build["Build jar, build + push images to GHCR<br/>tag = commit SHA"]
    build --> ssh["SSH to preview host: copy deploy/ infra/ scripts/"]
    ssh --> up["deploy.sh up: backup, pull by digest, compose up, wait healthy"]
    up --> smoke["deploy.sh smoke (HTTPS)"]
    smoke --> kcadm["deploy.sh admin-console enable"]
    smoke -->|failure| restore["restore previous .env; no automatic DB rollback"]
    req["Edit deploy/web-preview/owner-e2e-run.request on develop"] --> prov["owner-e2e-provision.yml<br/>plan or apply on the preview"]
    manual["Manual dispatch"] --> pi["preview-images.yml<br/>build images only, no deploy"]
```

Caption: derived from `.github/workflows/ci.yml`, `.github/workflows/deploy-preview.yml`, `.github/workflows/native-build.yml`, `.github/workflows/blueprint-integrity.yml`, `.github/workflows/owner-e2e-fixtures.yml`, `.github/workflows/owner-e2e-provision.yml`, `.github/workflows/preview-images.yml`, `deploy/web-preview/deploy.sh`.

## 14 Preview infrastructure

```mermaid
flowchart TB
    internet(("Internet")) -->|"80, 443"| proxy
    subgraph host["One VPS, Docker Compose project matbakhy-preview"]
        direction TB
        subgraph edge["network edge"]
            proxy["proxy: Caddy image with both SPA builds"]
        end
        subgraph internal["network internal (no internet route)"]
            api["api (2 GB ceiling; mgmt port 8081)"]
            kc["identity: Keycloak"]
            pg[("postgres: databases matbakhy, keycloak")]
            st["storage: SeaweedFS S3 :8333"]
            loki["loki"]
            alloy["alloy (reads container logs)"]
            prom["prometheus + node-exporter"]
            am["alertmanager + alert-sink"]
        end
        graf["grafana (profile tools, 127.0.0.1:3000 only)"]
    end
    proxy -->|"app. / ops. static"| proxy
    proxy -->|"api.* /api /oauth2 /login/oauth2"| api
    proxy -->|"id.* (not admin)"| kc
    proxy -->|"kc-admin.* only if flag file exists"| kc
    proxy -->|"media.*"| st
    api --> pg
    api --> kc
    api --> st
    kc --> pg
    alloy --> loki
    prom -->|"scrape api:8081"| api
    prom --> am
    graf --> prom
    graf --> loki
```

Caption: derived from `deploy/web-preview/compose.yaml`, `deploy/web-preview/compose.4gb.yaml`, `deploy/web-preview/Caddyfile`, `deploy/web-preview/observability/prometheus.yml`. The preview is not production: see [infrastructure and deployment](infrastructure-and-deployment.md).
