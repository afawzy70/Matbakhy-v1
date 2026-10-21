# رسومات المعمارية

> **Owner role:** Backend architecture
> **Status:** PARTIALLY IMPLEMENTED — الرسومات بتوضّح اللي الكود بيعمله النهاردة؛ الأجزاء PLANNED أو DISABLED متعلّم عليها في نص الـ Node
> **Language / اللغة:** [English](diagrams.md) · العربية

كل الرسومات نص Mermaid. عدّلها مكانها؛ GitHub بيرسمها. كل رسم ليه Caption بيذكر الملفات اللي اتشتق منها، فأي تغيير في الملفات دي سبب إنك تراجع الرسم (السياسة: [صيانة التوثيق](../05-developer-workflow/documentation-maintenance-ar.md)). الشرح النصي لكل مجال موجود في صفحات المعمارية: [نظرة عامة على السيستم](system-overview-ar.md)، [الـ Backend](backend-architecture-ar.md)، [الـ Frontend](frontend-architecture-ar.md)، [الموبايل](mobile-architecture-ar.md)، [قاعدة البيانات](database-architecture-ar.md)، [الهوية والأمان](identity-and-security-ar.md)، [التكامل والأحداث](integration-and-events-ar.md)، [البنية التحتية](infrastructure-and-deployment-ar.md).

المحتويات: [1 سياق السيستم](#1-سياق-السيستم)، [2 الـ Containers](#2-معمارية-الـ-containers)، [3 مكوّنات الـ Backend](#3-مكونات-الـ-backend)، [4 الويب](#4-معمارية-الويب)، [5 الموبايل](#5-معمارية-الموبايل)، [6 OIDC](#6-الـ-authentication-oidc)، [7 رفع الـ MFA](#7-رفع-الـ-mfa-step-up)، [8 تسجيل الأوردر](#8-تسجيل-الأوردر-sequence)، [9 حالات الأوردر](#9-حالات-تنفيذ-الأوردر)، [10 تغيّر السعر](#10-تغير-السعر-وإعادة-تأكيد-الـ-quote-القديم)، [11 العربون](#11-العربون-والكاش-والتسوية)، [12 ER](#12-علاقات-قاعدة-البيانات)، [13 CI/CD](#13-cicd)، [14 بنية المعاينة](#14-بنية-المعاينة).

## 1 سياق السيستم

```mermaid
flowchart LR
    customer["العميل<br/>(متصفح أو موبايل)"]
    cook["الطبّاخ / صاحب المطبخ<br/>(ويب العمليات)"]
    staff["موظفو المنصة<br/>(ويب العمليات)"]
    owner["المالك / المشغّل<br/>(SSH, GitHub)"]
    sys["منصة مطبخي<br/>ويب العميل + موبايل العميل + ويب العمليات<br/>+ API واحد بـ Spring Boot"]
    idp["Keycloak<br/>(مزوّد هوية OIDC)"]
    store["تخزين Objects متوافق مع S3<br/>(SeaweedFS في المعاينة، s3mock محليًا)"]
    mail["مزوّدو Email / SMS / push<br/>PLANNED، ولا واحد متوصّل (Fakes بس)"]
    pay["مزوّد الدفع / البنك<br/>NOT INTEGRATED (عربون يدوي بس)"]
    customer --> sys
    cook --> sys
    staff --> sys
    sys <--> idp
    sys <--> store
    sys -.-> mail
    sys -.-> pay
    owner --> sys
```

الـ Caption: مشتق من `backend/app/src/main/java/com/matbakhy/app/providers/ProviderConfiguration.java` (مزوّدين Fake أو مرفوضين برّه LOCAL/CI/PREVIEW)، `deploy/web-preview/compose.yaml`، `infra/compose.yaml`، `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/PaymentService.java` (من غير نداء لأي Gateway؛ بيسجّل التبليغات بس).

## 2 معمارية الـ Containers

```mermaid
flowchart TB
    subgraph clients["العملاء (Clients)"]
        cw["customer-web<br/>React 19 + Vite SPA<br/>apps/customer-web"]
        ow["operations-web<br/>React 19 + Vite SPA<br/>apps/operations-web"]
        cm["customer-mobile<br/>Expo / React Native<br/>apps/customer-mobile"]
    end
    subgraph shared["حزم TypeScript المشتركة"]
        ac["packages/api-client<br/>متولّد من contracts/openapi.yaml"]
        dt["packages/design-tokens"]
    end
    subgraph server["ناحية السيرفر"]
        proxy["Caddy proxy (المعاينة بس)<br/>TLS، قوائم سماح، Headers، بيخدم الـ SPAs"]
        api["Spring Boot API<br/>backend/app + 9 موديولات + shared"]
        mgmt["منفذ الإدارة 8081<br/>Health + Prometheus (داخلي)"]
        pg[("PostgreSQL 16<br/>بيانات التطبيق، Sessions، Outbox")]
        kc["Keycloak 26.8<br/>الـ Realm اسمه matbakhy"]
        s3[("تخزين Objects<br/>Buckets خاصة وعامة")]
        obs["Loki, Alloy, Prometheus,<br/>Alertmanager, Grafana (اختياري)"]
    end
    cw --> ac
    ow --> ac
    cm --> ac
    cw -->|"Cookie Session + CSRF"| proxy
    ow -->|"Cookie Session + CSRF"| proxy
    cm -->|"Bearer JWT"| proxy
    proxy --> api
    api --> pg
    api -->|"JWKS, token, admin REST"| kc
    api -->|"S3 API, روابط Presigned"| s3
    kc --> pg
    api --- mgmt
    mgmt --> obs
```

الـ Caption: مشتق من `deploy/web-preview/compose.yaml`، `deploy/web-preview/Caddyfile`، `backend/pom.xml`، `apps/customer-web/package.json`، `apps/operations-web/package.json`، `apps/customer-mobile/package.json`، `packages/api-client/package.json`. في التطوير المحلي مفيش Caddy: الـ SPAs بتشتغل على منافذ Vite وبتكلّم الـ API مباشرة (`infra/compose.yaml`).

## 3 مكوّنات الـ Backend

```mermaid
flowchart TB
    subgraph app["backend/app (طبقة التجميع: com.matbakhy.app)"]
        sec["security: SecurityConfiguration, AccountMapper,<br/>MfaEnforcementFilter, StaffAssurance"]
        mfa["mfa: MfaPolicyService, MfaController,<br/>MfaAdminController, KeycloakAdmin"]
        web["web: ApiExceptionHandler, RequestIdFilter"]
        prov["providers: S3ObjectStorage, Fake* adapters"]
        idem["idempotency: JdbcIdempotentCommands"]
        obsv["observability: OperationalMetrics"]
    end
    subgraph modules["backend/modules (كل واحد فيه: web, api, internal)"]
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

الـ Caption: الخطوط هي الـ Maven Dependencies المعرّفة في كل `backend/modules/*/pom.xml`، ودايمًا بتتستخدم من خلال Package الـ `api` بتاع الموديول التاني (القاعدة بتتفرض بواسطة `backend/app/src/test/java/com/matbakhy/app/architecture/ModuleRules.java`). الاعتماديات الرئيسية بس هي اللي مرسومة؛ وكل موديول كمان بيعتمد على `identity` و`operations` للصلاحيات والـ Audit زي ما الـ pom بتاعه معرّف.

## 4 معمارية الويب

```mermaid
flowchart LR
    subgraph cwb["customer-web (مسارات Hash)"]
        capp["App.tsx<br/>المسار + اللغة + حالة me"]
        cscreens["Browse, Meal, Kitchen, Checkout,<br/>Orders, Payment, Account, Security"]
        ccart["cart.ts<br/>سلة في localStorage + تسجيل أوردر معلّق"]
        capi["api.ts createApi()"]
    end
    subgraph owb["operations-web (هيكل Tabs)"]
        oapp["App.tsx<br/>فحوصات checkSession + allowedTabs"]
        cooks["cook/*: Dashboard, Orders, Meals,<br/>Slots, Payments, Statement, Settings"]
        ops["ops/*: Reviews, Catalogue, Cases, OpsOrders,<br/>Finance, Payments, Configuration, Staff, Security"]
        oapi["api.ts createApi()"]
    end
    client["packages/api-client<br/>openapi-fetch + schema.ts متولّد"]
    tokens["packages/design-tokens<br/>tokens.css، خط Cairo، direction()"]
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

الـ Caption: مشتق من `apps/customer-web/src/App.tsx`، `apps/customer-web/src/api.ts`، `apps/customer-web/src/cart.ts`، `apps/operations-web/src/App.tsx`، `apps/operations-web/src/api.ts`، `packages/api-client/package.json`.

## 5 معمارية الموبايل

```mermaid
flowchart TB
    subgraph rn["apps/customer-mobile (Expo / React Native)"]
        entry["index.ts + App.tsx<br/>NavigationContainer + linking"]
        nav["Tabs: Discover, Favorites, Orders, Account<br/>Stack: Meal, Cart, Order, Notifications, Support"]
        ctx["AppContext.tsx<br/>الـ Session، اللغة، علامة الـ Step-up"]
        sess["session.ts + tokenStore.ts<br/>Refresh + expo-secure-store"]
        auth["auth.ts<br/>expo-auth-session، PKCE، متصفح السيستم"]
        mapi["mobileApi.ts<br/>Middleware لـ Bearer Token"]
        basket["basket.ts + placement.ts<br/>سلة في secure-store، تسجيل أوردر معلّق"]
    end
    kc["Keycloak (الـ Client اسمه matbakhy-mobile)"]
    api["Spring Boot API (سلسلة Bearer)"]
    entry --> nav --> ctx
    ctx --> sess
    ctx --> auth
    ctx --> mapi
    nav --> basket
    auth -->|"auth code + PKCE"| kc
    sess -->|"refresh_token grant"| kc
    mapi -->|"Authorization: Bearer"| api
```

الـ Caption: مشتق من `apps/customer-mobile/src/App.tsx`، `apps/customer-mobile/src/AppContext.tsx`، `apps/customer-mobile/src/auth.ts`، `apps/customer-mobile/src/tokenStore.ts`، `apps/customer-mobile/src/mobileApi.ts`، `apps/customer-mobile/src/basket.ts`، `apps/customer-mobile/src/screens/navigation.ts`.

## 6 الـ Authentication (OIDC)

```mermaid
sequenceDiagram
    autonumber
    participant B as "المتصفح (customer-web أو operations-web)"
    participant A as API (webChain)
    participant K as Keycloak
    participant D as PostgreSQL
    B->>A: GET /oauth2/authorization/keycloak?returnTo=...
    A->>B: 302 لصفحة authorize في Keycloak (code، S256 PKCE، scope openid profile email)
    B->>K: تسجيل الدخول (باسورد، وOTP بس لو طُلب acr mfa)
    K->>B: 302 لـ /login/oauth2/code/keycloak?code=...
    B->>A: callback ومعاه الـ code
    A->>K: تبادل التوكن عبر back-channel (client secret basic)
    K->>A: id_token + access_token
    A->>D: INSERT للـ session (Spring Session JDBC)، وكوكي MATBAKHY_SESSION
    A->>B: 302 لإعادة التوجيه بعد الدخول، ويضبط كوكي XSRF-TOKEN
    B->>A: GET /api/v1/me (مع الكوكي)
    A->>D: AccountMapper: تحديد app_user بالـ issuer+subject وتحميل الصلاحيات
    A->>B: 200 ومعاه البروفايل
    Note over B,A: الموبايل بدل كده بيستخدم PKCE مع Keycloak مباشرة وبيبعت Authorization Bearer لـ bearerChain، اللي بتتحقق من الـ issuer والـ audience matbakhy-api والتوقيع وتاريخ الانتهاء.
```

الـ Caption: مشتق من `backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java`، `backend/app/src/main/java/com/matbakhy/app/security/AccountMapper.java`، `backend/app/src/main/java/com/matbakhy/app/security/WebAccountFilter.java`، `apps/customer-mobile/src/auth.ts`، `infra/identity/matbakhy-realm.json`.

## 7 رفع الـ MFA (Step-up)

```mermaid
sequenceDiagram
    autonumber
    participant U as "العميل"
    participant A as "الـ API"
    participant P as MfaPolicyService
    participant K as Keycloak
    U->>A: نداء محمي (توكن أو session من غير MFA assertion)
    A->>P: decide(accountId) بيقرا mfa_policy والمطبخ والصلاحيات والتفضيل وآخر reset
    P-->>A: required = true
    A-->>U: 403 STEP_UP_REQUIRED
    U->>K: authorize بـ acr_values "mfa 2" (الويب بيضيف max_age=0، والموبايل prompt=login)
    K->>U: الباسورد لو لازم، وبعدين OTP أو تسجيل TOTP
    U->>A: إعادة المحاولة بتوكن أو session جديد (acr, amr, auth_time)
    A->>P: satisfiedBy(claims): الـ acr في mfa-acr-values أو الـ amr هو otp/mfa/hwk، والـ auth_time مش أقدم من آخر reset
    A-->>U: 200
    Note over A: الـ Endpoints عالية الخطورة للموظفين بتنادي كمان @staffAssurance.recentMfa اللي بتطلب إن auth_time يكون في حدود step-up-max-age (الافتراضي 10 دقايق).
```

الـ Caption: مشتق من `backend/app/src/main/java/com/matbakhy/app/security/MfaEnforcementFilter.java`، `backend/app/src/main/java/com/matbakhy/app/security/StaffAssurance.java`، `backend/app/src/main/java/com/matbakhy/app/mfa/MfaPolicyService.java`، `backend/app/src/main/resources/db/migration/V31__mfa_policy.sql`، `apps/customer-mobile/src/auth.ts`.

## 8 تسجيل الأوردر (Sequence)

```mermaid
sequenceDiagram
    autonumber
    participant C as "العميل"
    participant Q as QuoteController / QuoteService
    participant O as OrderController / OrderService
    participant I as JdbcIdempotentCommands
    participant S as JdbcSlotInventory
    participant D as PostgreSQL
    C->>Q: POST quote (الوجبات، السلوت، الوضع، العنوان، طريقة الدفع)
    Q->>D: INSERT للـ quote + quote_item (الأسعار من السيرفر، بتنتهي بعد ttl، ومبتحجزش مخزون)
    Q-->>C: quoteId والإجماليات وexpiresAt
    C->>O: POST /api/v1/orders (Idempotency-Key, quoteId, clientRequestId, نسخ السياسات المقبولة)
    O->>I: execute(customer, order.place, key)
    I->>D: بداية transaction واحدة
    O->>D: advisory lock على العميل + clientRequestId
    O->>Q: requireValid(quote): الـ ttl ونسخة السياسة ونسخة الوجبة والسعر والعنوان والإجمالي وشروط الدفع
    O->>S: reserve(slot, meals) بيقفل صفوف cook_slot وبعدين meal_slot بترتيب الـ id
    S->>D: reserved += n
    O->>D: INSERT للـ customer_order بحالة PENDING_ACCEPTANCE، ولقطات order_item، وstock_allocation بحالة RESERVED
    O->>D: INSERT للـ order_event رقم 1، وoutbox_event ORDER_PLACED، وorder_payment، وcash_obligation بحالة DUE
    I->>D: تخزين سجل الـ idempotency والـ commit
    O-->>C: 201 وعرض الأوردر
```

الـ Caption: مشتق من `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderService.java` (`place`، `placeOnce`)، `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/QuoteService.java`، `backend/modules/availability/src/main/java/com/matbakhy/availability/internal/JdbcSlotInventory.java`، `backend/app/src/main/java/com/matbakhy/app/idempotency/JdbcIdempotentCommands.java`.

## 9 حالات تنفيذ الأوردر

```mermaid
stateDiagram-v2
    [*] --> PENDING_ACCEPTANCE: التسجيل (place) من العميل
    PENDING_ACCEPTANCE --> ACCEPTED: الطبّاخ يقبل قبل accept_by
    PENDING_ACCEPTANCE --> REJECTED: الطبّاخ يرفض
    PENDING_ACCEPTANCE --> CANCELLED: العميل يلغي
    PENDING_ACCEPTANCE --> EXPIRED: OrderExpiryJob بعد accept_by
    ACCEPTED --> PREPARING: الطبّاخ يبدأ التحضير (prepare)
    ACCEPTED --> CANCELLED: الطبّاخ يلغي
    PREPARING --> READY: الطبّاخ يعلن الجاهزية (اتعمل كود التسليم)
    PREPARING --> CANCELLED: إلغاء استثنائي من الموظفين
    READY --> OUT_FOR_DELIVERY: الطبّاخ يخرج للتوصيل (dispatch) (DELIVERY بس)
    READY --> COMPLETED: كود التسليم (PICKUP بس) أو تجاوز من الموظفين
    READY --> CANCELLED: إلغاء استثنائي من الموظفين
    OUT_FOR_DELIVERY --> COMPLETED: كود التسليم أو تجاوز من الموظفين
    OUT_FOR_DELIVERY --> DELIVERY_FAILED: الطبّاخ يبلّغ بفشل التوصيل
    COMPLETED --> [*]
    REJECTED --> [*]
    CANCELLED --> [*]
    EXPIRED --> [*]
    DELIVERY_FAILED --> [*]
```

الـ Caption: مشتق من `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderLifecycleService.java`، `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderExpiryJob.java` وفحص الحالة في `backend/app/src/main/resources/db/migration/V11__orders.sql`؛ والحالات النهائية متجمّدة بالـ Trigger الموجود في `backend/app/src/main/resources/db/migration/V12__order_lifecycle.sql`. المعنى في البيزنس: [دورة حياة الأوردر](../01-business/order-lifecycle-ar.md).

## 10 تغيّر السعر وإعادة تأكيد الـ Quote القديم

```mermaid
sequenceDiagram
    autonumber
    participant K as "المطبخ (ويب الطبّاخ)"
    participant C as "عميل الزبون"
    participant S as "السيرفر"
    participant D as PostgreSQL
    C->>S: POST quote
    S->>D: صفوف الـ quote (meal_revision, سعر الوحدة, الإجمالي, policy_version)
    S-->>C: العرض A (الإجمالي 100.00 EGP)
    K->>S: تعديل سعر الوجبة
    S->>D: meal_revision جديدة غير قابلة للتعديل، والصفوف القديمة زي ما هي
    C->>S: POST /api/v1/orders بالعرض A
    S->>D: قفل صفوف السياسة والتسعير، وإعادة حساب الشروط
    S-->>C: 409 PRICE_CHANGED (السبب MEAL_CHANGED أو POLICY_CHANGED أو TOTAL_CHANGED أو PAYMENT_CHANGED)
    Note over C: الـ Checkout مبيعتبرش أي أوردر اتعمل، وبينادي POST quote تاني
    C->>S: POST quote
    S-->>C: العرض B (إجمالي جديد)
    Note over C: لازم العميل يوافق على الإجمالي الجديد صراحةً
    C->>S: POST /api/v1/orders بالعرض B ومحاولة جديدة
    S-->>C: 201 أوردر بالسعر المعروض
```

الـ Caption: مشتق من `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/QuoteService.java` (`requireValid`)، `apps/customer-web/src/Checkout.tsx` (فرع `PRICE_CHANGED`)، `apps/customer-web/src/i18n.ts` (نصوص تغيّر السعر)، `packages/api-client/test/priceChanges.test.ts`. الرد QUOTE_EXPIRED أو STOCK_CHANGED بيمشي في نفس مسار إعادة الـ Quote في الواجهة: NOT VERIFIED لكل شاشات العميل.

## 11 العربون والكاش والتسوية

```mermaid
stateDiagram-v2
    direction LR
    state "order_payment (العربون)" as dep {
        [*] --> WAITING_FOR_DEPOSIT: التسجيل بطريقة DEPOSIT
        WAITING_FOR_DEPOSIT --> CUSTOMER_TRANSFER_REPORTED: العميل يبلّغ بالتحويل
        WAITING_FOR_DEPOSIT --> KITCHEN_RECEIPT_REPORTED: المطبخ يبلّغ بالاستلام
        CUSTOMER_TRANSFER_REPORTED --> KITCHEN_RECEIPT_REPORTED: المطبخ يبلّغ بالاستلام (المبلغ يساوي العربون)
        DISPUTED --> KITCHEN_RECEIPT_REPORTED: المطبخ يبلّغ بالاستلام
        WAITING_FOR_DEPOSIT --> DISPUTED: المطبخ يبلّغ إنه ماستلمش أو فيه عدم تطابق
        RECONCILE_PENDING --> DISPUTED: المطبخ يبلّغ بمشكلة
        CUSTOMER_TRANSFER_REPORTED --> DISPUTED: المطبخ يبلّغ بمشكلة
        WAITING_FOR_DEPOSIT --> EXPIRED: الأوردر انتهى بدون نجاح (REJECTED, EXPIRED, CANCELLED, DELIVERY_FAILED) ومفيش بلاغ
        CUSTOMER_TRANSFER_REPORTED --> RECONCILE_PENDING: الأوردر انتهى بدون نجاح
        KITCHEN_RECEIPT_REPORTED --> RECONCILE_PENDING: الأوردر انتهى بدون نجاح
        DISPUTED --> RECONCILE_PENDING: الأوردر انتهى بدون نجاح
        DISPUTED --> REFUND_OWED: قرار الموظفين بعد انتهاء الأوردر بس، finance.verify، MFA حديث، وسبب
        RECONCILE_PENDING --> REFUND_OWED: قرار الموظفين بعد انتهاء الأوردر بس، finance.verify، MFA حديث، وسبب
        DISPUTED --> CLOSED_NO_FUNDS: قرار الموظفين بعد انتهاء الأوردر بس، finance.verify، MFA حديث، وسبب
        RECONCILE_PENDING --> CLOSED_NO_FUNDS: قرار الموظفين بعد انتهاء الأوردر بس، finance.verify، MFA حديث، وسبب
        REFUND_OWED --> REFUNDED: المطبخ يبلّغ إنه بعت الاسترداد
        RECONCILE_PENDING --> REFUNDED: المطبخ يبلّغ إنه بعت الاسترداد
    }
    state "cash_obligation" as cash {
        [*] --> DUE: التسجيل
        DUE --> REPORTED: المطبخ يبلّغ بتحصيل الكاش بالمبلغ المتوقع
        DUE --> DISPUTED_CASH: المبلغ المبلّغ عنه مختلف
        REPORTED --> VERIFIED: المالية تتحقق
        REPORTED --> DISPUTED_CASH: المالية تعترض
        DUE --> NOT_DUE: الأوردر اترفض أو انتهت مهلة قبوله، أو العميل/الطبّاخ لغاه قبل التحضير (الإلغاء الاستثنائي من العمليات بيسيب الالتزام زي ما هو للمراجعة اليدوية من المالية)
    }
```

الـ Caption: مشتق من `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/PaymentService.java`، `backend/app/src/main/resources/db/migration/V24__deposit_payments.sql`، `backend/app/src/main/resources/db/migration/V25__payment_reconciliation.sql`، `backend/app/src/main/resources/db/migration/V13__cash_and_ledger.sql`، `backend/modules/finance/src/main/java/com/matbakhy/finance/internal/CashService.java`. الحالة `DISPUTED_CASH` هي القيمة `DISPUTED` في `cash_obligation` و`cash_report`، واتسمّت كده هنا بس لأن أسماء الحالات في Mermaid لازم تكون فريدة. الانتقالات "order ended unsuccessfully" بتشتغل بس لـ REJECTED وEXPIRED وCANCELLED وDELIVERY_FAILED (`OrderLifecycleService.java:369` بيتخطّى COMPLETED)، فالأوردر المكتمل بيحتفظ بحالة الاستلام بتاعته. كل حالة عربون هي إفادة من العميل أو المطبخ أو الموظف؛ ولا واحدة معناها "اتحقق منه بنكيًا" ومفيش حساب لمطبخي بيمسك الفلوس. دفعات التسوية (`settlement_batch`: DRAFT وREVIEWED وPAYMENT_RECORDED وRECONCILED وCANCELLED) بتجمّع مجموعات قيود العمولة (commission ledger groups)؛ ومع قاعدة صفر العمولة (`backend/app/src/main/resources/db/migration/V23__launch_commission.sql`) هي خامدة في البيانات الحية. راجع [التسعير والدفع](../01-business/pricing-and-payments-ar.md).

## 12 علاقات قاعدة البيانات

```mermaid
erDiagram
    app_user ||--o{ user_grant : "صلاحيات الموظفين"
    app_user ||--o| kitchen : "بيملك (owner_id unique)"
    app_user ||--o{ address : "عنده"
    app_user ||--o{ customer_order : "بيسجّل"
    app_user ||--o{ idempotency_record : "الفاعل"
    app_user ||--o| user_mfa_preference : "التفضيل"
    app_user ||--o| user_mfa_reset : "آخر Reset"
    kitchen ||--o{ meal : "بيبيع"
    kitchen ||--o{ cook_slot : "بيجدول"
    kitchen ||--o| kitchen_payment_profile : "شروط الدفع"
    kitchen }o--o{ service_zone : "kitchen_zone"
    meal ||--o{ meal_revision : "نسخ غير قابلة للتعديل"
    cook_slot ||--o{ meal_slot : "السعة لكل وجبة"
    meal ||--o{ meal_slot : "معروضة في"
    quote ||--o{ quote_item : "بنود"
    quote ||--o| customer_order : "بيتستخدم مرة واحدة"
    customer_order ||--|{ order_item : "بنود Snapshot"
    customer_order ||--o{ stock_allocation : "بيحجز"
    meal_slot ||--o{ stock_allocation : "بيعدّ"
    customer_order ||--|{ order_event : "تاريخ Append-only"
    customer_order ||--|| order_payment : "الشروط + الحالة"
    order_payment ||--o{ payment_event : "Append-only"
    customer_order ||--|| cash_obligation : "الكاش المستحق"
    customer_order ||--o| cash_report : "تبليغ المطبخ"
    customer_order ||--o| handover_challenge : "Hash الكود"
    customer_order ||--o{ support_case : "النزاعات"
    support_case ||--o{ case_message : "محادثة"
    customer_order ||--o{ ledger_group : "ترحيل العمولة"
    ledger_group ||--|{ ledger_entry : "قيد مزدوج"
    settlement_batch ||--o{ settlement_line : "بيحتوي"
    ledger_group ||--o{ settlement_line : "تاريخ، بند نشط واحد على الأكتر"
    outbox_event ||--o{ notification : "source_event_id"
    notification ||--o{ notification_delivery : "قنوات"
    customer_order ||--o| review : "واحدة لكل أوردر"
```

الـ Caption: مشتق من الـ Foreign Keys في `backend/app/src/main/resources/db/migration/V1__baseline_identity_and_idempotency.sql` لحد `V31__mfa_policy.sql` (مثلًا `V11__orders.sql`، `V8__slots.sql`، `V13__cash_and_ledger.sql`، `V14__notifications.sql`، `V17__support.sql`، `V18__finance_operations.sql`، `V20__favorites_and_reviews.sql`، `V24__deposit_payments.sql`). الجداول الرئيسية بس هي المرسومة. الأعمدة مذكورة في [معمارية قاعدة البيانات](database-architecture-ar.md).

## 13 CI/CD

```mermaid
flowchart LR
    dev["المطوّر بيعمل Push لـ Branch / PR"] --> ci["ci.yml Product CI<br/>workspace, local-stack, design, secrets"]
    dev --> bp["blueprint-integrity.yml"]
    dev --> nb["native-build.yml<br/>بيلدات Android + iOS وفحوصات Native"]
    dev --> of["owner-e2e-fixtures.yml<br/>(التحقق من الـ Fixtures)"]
    merge["Merge / Push على develop"] --> dp["deploy-preview.yml"]
    dp --> wait["استنى Product CI أخضر على نفس الـ SHA"]
    wait --> build["بناء الـ jar، وبناء ورفع الـ Images على GHCR<br/>الـ Tag = commit SHA"]
    build --> ssh["SSH لـ Host المعاينة: نسخ deploy/ infra/ scripts/"]
    ssh --> up["deploy.sh up: Backup، Pull بالـ Digest، compose up، استنى لحد ما يبقى Healthy"]
    up --> smoke["deploy.sh smoke (HTTPS)"]
    smoke --> kcadm["deploy.sh admin-console enable"]
    smoke -->|"فشل"| restore["استرجاع .env السابق؛ مفيش Rollback تلقائي للـ DB"]
    req["تعديل deploy/web-preview/owner-e2e-run.request على develop"] --> prov["owner-e2e-provision.yml<br/>plan أو apply على المعاينة"]
    manual["تشغيل يدوي (Dispatch)"] --> pi["preview-images.yml<br/>بناء الـ Images بس، من غير Deploy"]
```

الـ Caption: مشتق من `.github/workflows/ci.yml`، `.github/workflows/deploy-preview.yml`، `.github/workflows/native-build.yml`، `.github/workflows/blueprint-integrity.yml`، `.github/workflows/owner-e2e-fixtures.yml`، `.github/workflows/owner-e2e-provision.yml`، `.github/workflows/preview-images.yml`، `deploy/web-preview/deploy.sh`.

## 14 بنية المعاينة

```mermaid
flowchart TB
    internet(("الإنترنت")) -->|"80, 443"| proxy
    subgraph host["VPS واحد، مشروع Docker Compose اسمه matbakhy-preview"]
        direction TB
        subgraph edge["شبكة edge"]
            proxy["proxy: Image بتاع Caddy فيه الـ SPA Builds الاتنين"]
        end
        subgraph internal["شبكة internal (من غير مسار للإنترنت)"]
            api["api (حد 2 GB؛ منفذ الإدارة 8081)"]
            kc["identity: Keycloak"]
            pg[("postgres: قواعد البيانات matbakhy, keycloak")]
            st["storage: SeaweedFS S3 :8333"]
            loki["loki"]
            alloy["alloy (بيقرأ Logs الـ Containers)"]
            prom["prometheus + node-exporter"]
            am["alertmanager + alert-sink"]
        end
        graf["grafana (Profile أدوات، 127.0.0.1:3000 بس)"]
    end
    proxy -->|"app. / ops. ملفات ثابتة"| proxy
    proxy -->|"api.* /api /oauth2 /login/oauth2"| api
    proxy -->|"id.* (مش الأدمن)"| kc
    proxy -->|"kc-admin.* لو ملف العلامة موجود بس"| kc
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

الـ Caption: مشتق من `deploy/web-preview/compose.yaml`، `deploy/web-preview/compose.4gb.yaml`، `deploy/web-preview/Caddyfile`، `deploy/web-preview/observability/prometheus.yml`. المعاينة مش Production: راجع [البنية التحتية والـ Deployment](infrastructure-and-deployment-ar.md).
