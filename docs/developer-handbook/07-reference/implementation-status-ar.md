# حالة التنفيذ

> **Owner role:** Handbook navigation
> **Status:** PARTIALLY IMPLEMENTED — جدول حالة لكل ميزة مستخرج من الكود والاختبارات، مع التعارضات اللي اتلاقت بين الكود ومستندات التخطيط/الحالة
> **Language / اللغة:** [English](implementation-status.md) · العربية

الوسوم بتتبع اصطلاح الدليل (IMPLEMENTED, PARTIALLY IMPLEMENTED, PLANNED, DISABLED, DEPRECATED, NOT VERIFIED).
**IMPLEMENTED معناها إن الكود موجود وفيه اختبار آلي بيشغّله اتلاقى في الـ Repository.** مش معناها إن الاختبار نجح في الجلسة دي (كتّاب الدليل ماشغّلوش الـ Suites) ولا إن مراجع مستقل قبله، ومعناها أبدًا مش "شغّال فعليًا": مفيش بيئة Production، والفلوس الحقيقية متقفّل عليها بـ Gate (D16)، وبيئة PREVIEW ببيانات اصطناعية (Synthetic) بس هي اللي اتنشرت (شوف التعارضات تحت). حالة الـ Story في `planning/backlog.json` وحالة السيناريو في `planning/scenarios.json` تلميحات اتقارنت بغيرها، مش حاجات بنثق فيها.

إزاي اتستخرجت الحالة: الـ Controller موجود والـ Route في `contracts/endpoints.json` مع `implemented: true` (113 من 116 Route)؛ كود الـ Service اتقرا؛ والـ Test class اتلاقى بمعرّف السيناريو أو باسم المنطقة في `backend/app/src/test/java/com/matbakhy/app/`، و`apps/*/src/*.test.tsx`، و`packages/*/test/`؛ والـ Migration موجودة. أعداد الاختبارات أو نتايج النجاح **مش** مؤكَّدة هنا.

## قدرات الـ Backend

| القدرة | الحالة | الدليل (كود / اختبار / Story) |
|---|---|---|
| تسجيل دخول OIDC على الويب، وجلسات PostgreSQL، وCSRF، وCORS allowlist، وتسجيل الخروج | IMPLEMENTED | `SecurityConfiguration` webChain؛ `IdentityIntegrationTest`، `CsrfCookie*IntegrationTest`؛ MB-009 DONE |
| التحقق من Bearer JWT الـ Native (الـ Issuer، الـ Audience) | IMPLEMENTED | `SecurityConfiguration` bearerChain؛ `IdentityIntegrationTest` |
| ربط الحساب، ورفض الحساب الموقوف، والأدوار من حالة السيرفر بس | IMPLEMENTED | `AccountMapper`، `AccountLifecycleService` |
| الملف الشخصي، والموبايل المصري الموثّق (أكواد متخزّنة كـ Hash ولاستخدام واحد، مع حدود)، والعناوين | IMPLEMENTED | `ContactIntegrationTest`، TC-003..005؛ مزوّد SMS الحقيقي **PLANNED** (O07؛ فيه Sender وهمي بس) |
| صلاحيات الموظفين، وحماية آخر أدمن والتصعيد الذاتي، وسجل التدقيق | IMPLEMENTED | `StaffAccessIntegrationTest`، TC-006/007/043 |
| سياسة MFA قابلة للضبط (Keycloak هو اللي بينفّذ MFA؛ DISABLED/OPTIONAL/REQUIRED لكل فئة)، وفلتر الإنفاذ، وإعادة ضبط الأدمن، وتفضيل المستخدم | IMPLEMENTED | `MfaPolicyService`، `MfaEnforcementFilter`، V31؛ `MfaPolicyIntegrationTest`، `MfaPolicyResolveTest`، `MfaIdpTest`، `MfaResetIntegrationTest`، `MfaResetAuditIntegrationTest`؛ D18. مش Story منفصلة في الـ Backlog (شوف التعارضات) |
| طلبات الطبّاخين، وقرار المراجع مع Step-up، وإنشاء المطبخ، والإيقاف المؤقت/التعليق | IMPLEMENTED | `CookWorkflowIntegrationTest`، TC-008/009؛ MB-012 DONE |
| خط الميديا (رفع موقّع، تحقق، فحص، مستندات خاصة، تنظيف) | IMPLEMENTED with fakes | `MediaIntegrationTest`، `MediaCleanupIntegrationTest`؛ فاحص البرمجيات الخبيثة الحقيقي **PLANNED** (O07) |
| الوجبات، ونسخها (Revisions)، وحراس النشر، والإشراف، وتاريخ الأسعار | IMPLEMENTED | `MealIntegrationTest`، `ModerationIntegrationTest`، `PriceHistoryIntegrationTest`؛ MB-014، MB-048 |
| الـ Slots والسعة (جدولة بتوقيت القاهرة) | IMPLEMENTED | `SlotIntegrationTest`، TC-013..015 |
| الاكتشاف (Discovery) والمناطق من جهة السيرفر | IMPLEMENTED | `DiscoveryIntegrationTest` |
| عروض الأسعار (تسعير السيرفر، TTL السياسة 5 دقايق، طبّاخ واحد + Slot واحد) | IMPLEMENTED | `QuoteIntegrationTest`؛ [تنفيذ التسعير](../04-codebase/pricing-implementation-ar.md) |
| رفض عرض السعر اللي مبقاش مواكب للأسعار أو الشروط الحالية وإعادة التأكيد (D13) | IMPLEMENTED | `PriceChangeIntegrationTest` (TC-154..156)؛ إشعارات الويب والموبايل |
| تسجيل الأوردر الذري، والـ Idempotency على طبقتين، والـ Snapshots، وحجز السعة | IMPLEMENTED | `OrderPlacementIntegrationTest`، V29؛ [تنفيذ الأوردر](../04-codebase/order-implementation-ar.md) |
| دورة حياة الأوردر، وأكواد التسليم، وـ Job الانتهاء، وإلغاء الاستثناء من العمليات وتجاوز التسليم | IMPLEMENTED | `OrderLifecycleIntegrationTest`، TC-026..031 |
| الكاش عند الاستلام: تبليغ الطبّاخ، وتحقق المالية، وفصل المهام | IMPLEMENTED | `CashAndLedgerIntegrationTest` |
| دفتر الأستاذ (Ledger) (متوازن، للإضافة فقط)، والاستردادات، والتسويات، وكشوف الحساب | IMPLEMENTED | `CashAndLedgerIntegrationTest`، `FinanceOperationsIntegrationTest`؛ العمولة 0 عند الإطلاق (`ZeroCommissionIntegrationTest`، D10) |
| عمولة المنصة ورسوم المنصة على العميل | DISABLED | قدرة نايمة: الـ Database بترفض أي نسبة مش صفر للسياسات غير الاصطناعية (non-synthetic) (V23)؛ D10 |
| مدفوعات العربون (اعتماد مستلم مدفوعات المطبخ، تبليغ العميل، استلام المطبخ، النزاعات، التسوية، تبليغ الاسترداد) | IMPLEMENTED for synthetic/preview use | `DepositPaymentIntegrationTest`، `PaymentReconciliationIntegrationTest`؛ **الفلوس الحقيقية متقفّل عليها** (D16، مراجعة قانونية/مزوّد) |
| بوابة دفع آلية، وتحقق بنكي، وEscrow، وصندوق ضمان | PLANNED (بحث مستقبلي فقط) | `docs/strategy/mvp-direct-deposit-payment.md` بيوسمهم مؤجَّلين |
| صندوق الإشعارات داخل التطبيق، وـ Outbox Dispatcher بـ Leases/إعادة محاولة/Dead Letters | IMPLEMENTED | `NotificationIntegrationTest` |
| تسليم Push خارجي (FCM/APNs) | DISABLED | `matbakhy.notifications.push-enabled` قيمته الافتراضية false؛ مفيش Adapter حقيقي (O07) |
| تسليم البريد الإلكتروني الخارجي | PLANNED | الموجود بس `LoggingNotificationChannels` (مابيبعتش حاجة) |
| حالات الدعم، وردود الموظفين، والملاحظات الخاصة، وطلبات الإلغاء، وحالة دعم خاصة بالدفع بتتفتح تلقائيًا | IMPLEMENTED | `SupportIntegrationTest`، V17، V28 |
| التقييمات، وبلاغات التقييمات والإشراف عليها؛ والمفضلة | IMPLEMENTED | `ReviewsAndFavoritesIntegrationTest` |
| طلب حذف الحساب ومسح البيانات الشخصية | IMPLEMENTED with a proposed retention default | `AccountErasureIntegrationTest`؛ فترة السماح 14 يوم قيمة PROPOSED (O06 مفتوح) |
| عدّادات النشاط العضوي ورؤى الطبّاخ (من غير ترتيب أو حملات أو فواتير) | IMPLEMENTED | `ActivityInsightsIntegrationTest`، D17 |
| إصدارات إعدادات التجارة، وـ Feature Flags، ومفتاح استقبال الأوردرات | IMPLEMENTED | `ConfigurationIntegrationTest`، `PublicConfigurationIntegrationTest` |
| API قراءة التدقيق | IMPLEMENTED | `AuditController`؛ التصدير (API-095) **PLANNED** |
| لوحة العمليات (API-066)، ومستندات السياسات (API-008) | PLANNED | الـ Routes مسجّلة بـ `implemented: false` ومفيش Controller |
| الـ Observability: معرّفات الطلب، والتنقيح (Redaction)، ومقاييس Management Port، ودور الـ Database وقت التشغيل | IMPLEMENTED locally | `OperationalMetricsIntegrationTest`، `ManagementPortIntegrationTest`، `LogRedactionIntegrationTest`؛ جاهزية النشر (AUD-01/LOG-01/OPS-01..03) NOT VERIFIED |
| أدوات Backup/restore | PARTIALLY IMPLEMENTED | `scripts/db-backup.sh`، `scripts/db-restore.sh`، `BackupRestoreIntegrationTest`؛ TC-057 لسه NOT_RUN في `planning/scenarios.json` (محتاج Staging) |
| تحديد المعدّل (Rate limiting) | PARTIALLY IMPLEMENTED | فيه حدود لكل ميزة (أكواد الموبايل، محاولات التسليم، بلاغات التقييمات، تذاكر الرفع: استخدامات `ErrorCode.RATE_LIMITED`)؛ مالقيناش محدِّد عام للـ Quote/الأوردر/البحث؛ TC-054 NOT_RUN. قفل Keycloak بيغطي تسجيل الدخول |
| ميزانيات الأداء | NOT VERIFIED | TC-059 NOT_RUN (MB-038 TODO) |
| الـ Feed الاجتماعي، والمتابعات، والتعليقات | PLANNED | تأجيل D07، O10 مفتوح؛ مفيش كود |
| فلترة حجم الوجبة | PLANNED/deferred | D11: الأدوات مخفية عمدًا (`apps/customer-web/src/Browse.tsx:93`) |
| AI Concierge، والاكتشاف المموَّل، وفواتير النمو | PLANNED (research) | `docs/strategy/free-core-sponsored-discovery.md`؛ مفيش كود |

## التطبيقات

| التطبيق | الحالة | الدليل |
|---|---|---|
| ويب العميل (التصفح، السلة، الـ Checkout، حالة الدفع، الأوردرات، الحساب، الأمان) | IMPLEMENTED | `apps/customer-web/src/App.test.tsx`؛ Playwright `apps/operations-web/e2e/customer-journey.e2e.ts`؛ MB-027/028 DONE |
| مساحة عمل الطبّاخ (لوحة المتابعة، الأوردرات، الوجبات، الـ Slots، المدفوعات، كشف الحساب، الرؤى، الإعدادات، الـ Onboarding) | IMPLEMENTED | `apps/operations-web/e2e/cook-workspace.e2e.ts`؛ MB-022 DONE |
| الـ Backoffice للموظفين (المراجعات، الكتالوج، الحالات، الأوردرات، المالية، المدفوعات، الإعدادات، الموظفين، الأمان) | IMPLEMENTED | `apps/operations-web/src/App.test.tsx`؛ MB-023..026 DONE |
| التحقق من إمكانية الوصول والتجاوب | PARTIALLY IMPLEMENTED | فيه اختبارات keyboard/RTL؛ اختبار قارئ الشاشة ماتعملش: TC-047 BLOCKED، MB-029 BLOCKED (BLK-003)؛ MB-030 (رحلات كاملة آلية) TODO |
| موبايل العميل: تسجيل الدخول (PKCE، تخزين آمن)، الاكتشاف، السلة، الأوردر، Checkout العربون، صندوق الإشعارات، الدعم | IMPLEMENTED on emulator/simulator | `apps/customer-mobile/src/*.test.ts(x)`؛ التشغيلات الـ Native متسجّلة تحت `verification/runs/`؛ MB-031/032 DONE |
| Push دورة حياة الموبايل، وتوجيه Push بالـ Deep Link، والنص الديناميكي وقارئات الشاشة | PARTIALLY IMPLEMENTED | MB-033 IN_PROGRESS؛ الـ Push متعطّل (O07) |
| نسخ Android و iOS المرشّحة للإصدار، وتقديم المتاجر | PLANNED | MB-034، MB-035، MB-045 TODO؛ TC-051/052/063 NOT_RUN |
| التحقق على الأجهزة الفعلية | NOT VERIFIED | BLK-004 في `docs/status/latest.md` |

## البيئات والعمليات

| البند | الحالة | الدليل |
|---|---|---|
| الـ Stack المحلي (`scripts/dev.sh`، `infra/compose.yaml`) | IMPLEMENTED | MB-008 DONE |
| المعاينة (بيانات اصطناعية، `APP_ENV=PREVIEW`) | IMPLEMENTED (اتنشرت مرة؛ الحالة الحالية NOT VERIFIED) | `deploy/web-preview/`؛ `docs/quality/owner-acceptance/run-2026-10-19.md` بيسجّل تطبيق Fixtures على إصدار معاينة |
| مجموعة قبول المالك (200 حالة) | NOT VERIFIED | `docs/quality/owner-acceptance/generated/traceability.json`: 192 NOT_RUN، 8 BLOCKED |
| بيئات الـ Staging والـ Production | PLANNED | MB-040، MB-044 TODO؛ `APP_ENV=STAGING` بيرفض يشتغل من غير مزوّدين حقيقيين |
| بوابات الأدلة (مرشّح الإصدار) | PLANNED | MB-043 TODO |

## التعارضات اللي اتلاقت بين المستندات والكود

| # | المستند بيقول | الكود أو مصدر تاني بيقول | المصدر الموثوق |
|---|---|---|---|
| 1 | `contracts/endpoints.json` فيه `"status": "DESIGN_NOT_IMPLEMENTED"` | 113 من 116 Route فيها `implemented: true` ومتخدَّمة (`ContractRoutesTest`) | الكود |
| 2 | `planning/backlog.json` على مستوى الملف فيه `"status": "PLANNED_NOT_IMPLEMENTED"` | الـ Stories MB-001..MB-028، 031، 032، 037، 048..054 حالتها DONE | حالة كل Story مع الكود |
| 3 | جدول الـ Stories في `docs/status/latest.md`: "Flyway V1-V20" وأحدث بند بتاريخ 2026-10-14 | الـ Migrations بتوصل لـ `V31__mfa_policy.sql`؛ والـ Commits لحد 2026-10-19 | الكود؛ latest.md قديم بالنسبة لـ MFA (عمره ما بيذكر D18) وبالنسبة لشغل E2E المالك |
| 4 | `docs/status/latest.md` (2026-10-14): "No preview deployed" | `docs/quality/owner-acceptance/run-2026-10-19.md`: اتنشر إصدار معاينة واتطبّقت Fixtures الـ E2E | سجل التشغيل |
| 5 | D18 (MFA القابل للضبط) قرار مقبول وله مستند سياسة | مفيش Story في `planning/backlog.json` موضوعها D18 (MB-009/012/026/036/052 بس بيذكروه) | الكود موجود؛ التتبّع بالـ Story في الـ Backlog ناقص |
| 6 | TC-057 وTC-139 حالتهم NOT_RUN في `planning/scenarios.json` | `BackupRestoreIntegrationTest` (TC-057) و`LogRedactionIntegrationTest` (TC-056) موجودين؛ وTC-139 بيجمّع أدوات MB-039 اللي لسه شغّالة | حالة السيناريو متحفّظة: فيه تغطية جزئية |
| 7 | `docs/engineering/01-architecture.md` ADR-001 بيذكر JPA | مفيش Dependency على JPA؛ `JdbcTemplate` في كل مكان | الكود |
| 8 | ADR-002: مشاركة "translation keys" | تلات ملفات `i18n.ts` منفصلة | الكود |
| 9 | `planning/decisions.json` D05 "Cash at delivery or pickup" | مستند السجل + D16 + الكود: العربون موجود | السجل + الكود |
| 10 | `docs/product/01-context-and-decisions.md` سطر 82: "D01-D10 remain PROPOSED_DEFAULT" | جدول نفس الملف و`planning/decisions.json`: D10 ACCEPTED | الجدول والـ JSON |
| 11 | `docs/quality/owner-acceptance/generated/traceability.json` فيه `scenarios_uncovered: []` | 39 بس من 200 حالة للمالك بتسرد معرّفات سيناريوهات في `case_to_scenarios`؛ والباقي `[]` | العدّ بالسيناريو مش بالحالة؛ ماتقراش على إن "كل حالة مغطّاة" |
| 12 | `deploy/web-preview/identity/matbakhy-realm.preview.json` (الـ Clients هما `matbakhy-web`، `matbakhy-admin`) | الـ Realm المحلي بيعرّف كمان `matbakhy-mobile`؛ و`apps/customer-mobile/src/config.ts` بيحط الـ Client id الافتراضي `matbakhy-mobile` | NOT VERIFIED إزاي بيعمل الـ Native Builds Authentication على المعاينة |

## فين تتأكد من الحالة الحالية بنفسك

`python3 scripts/next_task.py` (الشغل المؤهَّل)، و`python3 scripts/verify.py --phase Pxx`، وSuite الـ Backend من خلال Maven، و`pnpm test`،
و`pnpm contract:check`؛ شوف [تشغيل الاختبارات](../05-developer-workflow/test-execution-ar.md). راجع الصفحة دي كل ما حالة Story تتغيّر
(القاعدة: [صيانة التوثيق](../05-developer-workflow/documentation-maintenance-ar.md)).
