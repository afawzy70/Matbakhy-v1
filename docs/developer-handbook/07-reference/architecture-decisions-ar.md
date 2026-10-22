# فهرس القرارات المعمارية وقرارات المنتج

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — فهرس لأماكن تسجيل القرارات وحالتها وروابط الإحلال (Supersession) اللي ممكن تتلاقي في الـ Repository
> **Language / اللغة:** [English](architecture-decisions.md) · العربية

مفيش فولدر `adr/`. القرارات موجودة في أربع أماكن، لكل مكان دور مختلف:

| المصدر | الدور | بيتفحص آليًا؟ |
|---|---|---|
| `planning/decisions.json` | السجل المقروء آليًا: `D01`..`D18` (متقرر/مقترح) و`O01`..`O10` (مفتوح) مع الحالة والقيمة والمالك والـ Gate ومرجع الموافقة | أيوه — `scripts/check_scope.py` (`check_decisions`) بيقارنه بمستند السجل |
| `docs/product/01-context-and-decisions.md` | السجل البشري: المتطلبات المؤكدة C01..C07، ونفس أرقام D/O مع المبررات، وأقسام "Owner decision/direction" المؤرخة | أيوه، نفس السكريبت (الأرقام لازم تتطابق في الاتجاهين) |
| `docs/engineering/01-architecture.md` | `ADR-001`..`ADR-005` (حدود الموديولات، العملاء والعقود، الـ Authentication، الخدمات الخارجية، تثبيت الاعتماديات) | لا |
| `docs/delivery/07-owner-decisions-implementation-sequence.md`، `docs/strategy/*.md`، `docs/product/rq-004-stale-quote.md`، `docs/product/q1-quantity-limit-decision.md`، `docs/security/mfa-policy.md` | مستندات موضوعية فيها تفاصيل قرارات بعينها | لا |

قاعدة من `CLAUDE.md`: أحدث توجيه صريح من المالك هو الحاكم؛ سجّله وطابق المستندات المتأثرة؛ المتطلبات المؤكدة قبل كده أقوى من المقترحات؛ ونتايج الاختبارات مينفعش تسمح بتغيير النطاق.

## سجل القرارات (`planning/decisions.json`)

قيم الحالة المسموحة من `scripts/check_scope.py`: `PROPOSED_DEFAULT`، `OPEN`، `ACCEPTED`، `REJECTED`، `SUPERSEDED` (القرار `ACCEPTED` لازم يشيل مرجع موافقة؛ والـ `OPEN` ميكونش ليه قيمة وليه Gate).

| الرقم | الموضوع | الحالة في الـ JSON | منفّذ في الكود؟ | التفاصيل |
|---|---|---|---|---|
| D01 | Spring Boot Modular Monolith، وPostgreSQL | PROPOSED_DEFAULT | أيوه — شوف [دليل موديولات الـ Backend](../04-codebase/backend-module-guide-ar.md) | ADR-001 |
| D02 | React + TypeScript، وتطبيقين ويب منفصلين للعميل والعمليات | PROPOSED_DEFAULT | أيوه — [دليل كود الـ Frontend](../04-codebase/frontend-code-guide-ar.md) | ADR-002 |
| D03 | React Native + Expo لـ Android/iOS | PROPOSED_DEFAULT | أيوه — [دليل كود الموبايل](../04-codebase/mobile-code-guide-ar.md) | ADR-002 |
| D04 | طبّاخ واحد وSlot واحد مؤرخ لكل أوردر | PROPOSED_DEFAULT | أيوه — `QuoteService.terms` | BR-018 |
| D05 | الدفع: كاش، ومطابقة يدوية | PROPOSED_DEFAULT؛ ومستند السجل بيقول "Owner-updated 2026-10-13" لإضافة العربونات | اتحل محله جزئيًا بـ D16 (شوف تحت) | `docs/strategy/mvp-direct-deposit-payment.md` |
| D06 | توصيل بترتيب الطبّاخ أو استلام | PROPOSED_DEFAULT | أيوه | |
| D07 | المفضلة وروابط المشاركة دلوقتي؛ الـ Feed/المتابعة/التعليقات مؤجلة | PROPOSED_DEFAULT | المفضلة أيوه؛ الـ Feed/المتابعة/التعليقات مش منفّذة (O10 مفتوح) | |
| D08 | OIDC مع Keycloak محلي؛ تليفون متحقَّق منه عند الدفع | PROPOSED_DEFAULT | أيوه | ADR-003؛ جزء MFA للموظفين اتعدّل بـ D18 |
| D09 | Arabic/English، EGP، Africa/Cairo | PROPOSED_DEFAULT | أيوه | |
| D10 | عمولة 0%، ومفيش رسوم منصة على العميل، ومفيش إعلانات مدفوعة عند الإطلاق؛ العمولة تفضل قدرة تقنية غير مفعّلة | ACCEPTED | أيوه — CHECK في V23، `ZeroCommissionIntegrationTest` | Git Commits بتاعة المالك بتاريخ 2026-10-13 |
| D11 | الفلترة بحجم الحصة مؤجلة من الـ MVP | ACCEPTED | أيوه (الأدوات مخفية) | |
| D12 | وصف كارت الوجبة اختياري | ACCEPTED | أيوه | |
| D13 | تغيّر السعر قبل التسجيل: رفض الـ Quote القديم، وإعادة تأكيد صريحة | ACCEPTED | أيوه — [تنفيذ التسعير](../04-codebase/pricing-implementation-ar.md) | `docs/product/rq-004-stale-quote.md` |
| D14 | تاريخ أسعار المطبخ | ACCEPTED | أيوه — `MealService.priceHistory` | |
| D15 | نافذة التنفيذ اليومية | ACCEPTED | قاعدة عملية، مش كود | `docs/delivery/06-daily-execution.md` |
| D16 | دفع الـ MVP: COD المؤهل + عربون يدوي مباشر بيبلّغ عنه المطبخ؛ مش "متحقق منه بنكيًا" أبدًا؛ والفلوس الحقيقية متقفّلة بـ Gate | ACCEPTED | أيوه (اصطناعي (Synthetic)/معاينة بس) — [تنفيذ الدفع](../04-codebase/payment-implementation-ar.md) | `docs/strategy/mvp-direct-deposit-payment.md` |
| D17 | الأساسيات مجانية؛ قياس النشاط العضوي وأداء المطبخ بشكل بيحافظ على الخصوصية؛ مفيش وظيفة حملات مدفوعة | ACCEPTED | أيوه — `ActivityController`، `InsightsController` | `docs/product/10-organic-activity-and-cook-insights.md` |
| D18 | MFA قابل للضبط بينفّذه Keycloak؛ السياسة DISABLED / OPTIONAL / REQUIRED لكل فئة حساب | ACCEPTED | أيوه — `MfaPolicyService`، V31 | `docs/security/mfa-policy.md` |
| O01..O10 | قرارات مالك مفتوحة: مدينة/مناطق الإطلاق، الكاش مقابل الدفع المسبق عند الإطلاق، مسؤولية التوصيل، سياسة الاسترداد/الضريبة/الفواتير، تصريح العلامة التجارية، سياسة نشاط الغذاء والخصوصية، المزوّدين/الاستضافة، حسابات Apple/Google، جهات اتصال الدعم، الميزات الاجتماعية | OPEN | لا ينطبق — بتقفل التشغيل الفعلي، مش الكود | `docs/product/01-context-and-decisions.md` "Open decisions" |

القرارات المفتوحة اللي بتفاجئ المطوّر أكتر: O04 (سياسة الضريبة والاسترداد — قيم اصطناعية في الكود)، O06 (الاحتفاظ — مهلة محو 14 يوم هي قيمة افتراضية PROPOSED في `application.yaml`)، O07 (المزوّدين — المحوّلات المزيفة (Fake Adapters) مسموحة في LOCAL/CI/PREVIEW بس، وSTAGING/PRODUCTION بترفض التشغيل من غير حقيقية، والـ Push متقفّل).

## الـ ADRs في `docs/engineering/01-architecture.md`

| الـ ADR | القرار | فحص الكود |
|---|---|---|
| ADR-001 | حدود الموديولات؛ موديولات الـ Domain بتتكلم عن طريق حزم `api`؛ Transactions في PostgreSQL لثوابت الطلب؛ Flyway هو اللي بيملك الـ Schema؛ PostgreSQL حقيقي في الاختبارات | `ModuleBoundaryTest`؛ Testcontainers `PostgresTestConfiguration` |
| ADR-002 | تلات تطبيقات، عقد واحد؛ السيرفر هو اللي بيملك قرارات البيزنس؛ مفيش ويب مغلّف في WebView على إنه "Native" | `contracts/`، `packages/api-client`، `apps/customer-mobile` |
| ADR-003 | سلسلتين أمان: Bearer JWT بدون حالة للـ Native، وجلسة OIDC + CSRF للويب؛ الأدوار من حالة السيرفر بس؛ MFA للموظفين عن طريق Claims مستوى الضمان من المزوّد | `SecurityConfiguration`، `StaffAssurance` |
| ADR-004 | الخدمات الخارجية ورا Ports؛ المحوّلات المزيفة في التطوير/الاختبار بس؛ تشغيل Production بيفشل لو مزوّد مطلوب مزيف | `EnvironmentGuard`، `ProviderConfigurationTest` |
| ADR-005 | تثبيت النسخ؛ مفيش وسوم `latest` | النسخ المثبتة في `pom.xml`، `package.json` |

## روابط الإحلال والاستبدال اللي ممكن يتحقق منها

| الأقدم | الأحدث | الدليل |
|---|---|---|
| جغرافيا الكويت، KNET، مزوّدين كويتيين | مصر / EGP | `docs/product/01-context-and-decisions.md` ("Egypt supersedes that geography") |
| خيارات Flutter/FlutterFlow، وReact/Material UI أو Tailwind؛ نموذج اجتماعي تجريبي ببيانات وهمية | D02، D03، D07 | نفس الملف، "Superseded ideas remain as listed above" |
| الدفع كاش بس (خط D05 الأساسي) | D16 عربون يدوي مباشر جنب الـ COD | صف السجل D05 و"Owner decision — 2026-10-13: familiar payment rails in MVP" ("replaces cash-only as the exclusive checkout path") |
| نموذج قائم على العمولة في التخطيط الأقدم (نسبة قابلة للضبط، أمثلة عمولة في الـ Ledger) | D10 صفر عمولة (القدرة الخاملة محتفظ بيها) | `docs/delivery/07-owner-decisions-implementation-sequence.md` المرحلة 0؛ V23 |
| "MFA للموظفين إلزامي عالميًا" | D18 MFA مبني على سياسة | أول سطور `docs/security/mfa-policy.md` ("Supersedes the earlier decision...")؛ نص BR-007 في `planning/rules.json` ماشي على D18 فعلًا |
| السؤال المفتوح RQ-004 (الـ Quotes القديمة) | D13 | `docs/product/rq-004-stale-quote.md` ("replaces the open question") |
| اتجاه النموذج التجريبي الأقدم بالأخضر / IBM Plex | الصفحة الرئيسية Terracotta / Cairo | ثوابت `CLAUDE.md` ("supersedes")، `docs/design/approved/DESIGN.md` |
| اقتراح فصل بناء الـ Native من MB-005 لـ Story جديدة P01 | اتسحب (MB-005 اتقفل على معايير قبوله الأصلية) | `docs/product/01-context-and-decisions.md`، قسم "Proposed planning change" |

`planning/decisions.json` مبيستخدمش الحالة `SUPERSEDED` في أي مكان؛ الإحلال متسجّل في النص بس. شوف الفجوات تحت.

## إزاي تسجّل قرار جديد

1. خد توجيه صريح من المالك (محادثة، Commit، Issue). نتيجة اختبار أو تفضيل وكيل مش قرار.
2. ضيف الإدخال في `planning/decisions.json`: أول رقم `D<nn>` فاضي، و`topic`، و`status` (`ACCEPTED` مع `approval_reference`، أو `PROPOSED_DEFAULT`، أو `OPEN` مع `required_before`)، و`value`، و`owner`. استخدم `SUPERSEDED` وسمّي البديل لما تستبدل قرار؛ وحدّث نص قيمة الصف القديم.
3. ضيف نفس الرقم في الجدول في `docs/product/01-context-and-decisions.md`، وللقرارات الأكبر قسم مؤرخ "Owner decision — <date>" ومستند موضوعي تحت `docs/strategy/` أو `docs/product/`.
4. طابق القواعد المتأثرة (`planning/rules.json`، `docs/product/03-business-rules.md`)، والسيناريوهات (`planning/scenarios.json`)، وStories الـ Backlog (`planning/backlog.json`)، والعقود والكود، وضيف سيناريو Regression.
5. شغّل `python3 scripts/check_scope.py` و`python3 scripts/validate_blueprint.py`، وبعدها `python3 scripts/doc001_check.py docs`.
6. حدّث الصفحة دي، و[حالة التنفيذ](implementation-status-ar.md) وأي صفحة بيزنس أو معمارية بتذكر القاعدة القديمة (السياسة: [سياسة التوثيق](../05-developer-workflow/documentation-policy-ar.md)).

## القيود المعروفة وفجوات التوثيق

- صف D05 في `planning/decisions.json` لسه مكتوب فيه "Cash at delivery or pickup; manual audited reconciliation" (PROPOSED_DEFAULT) في حين إن مستند السجل بيقول إن المالك حدّث D05 بتاريخ 2026-10-13 وD16 شايل نطاق العربون. اعتمد على مستند السجل مع الكود (العربونات موجودة).
- `docs/product/01-context-and-decisions.md` سطر 82 بيقول "D01-D10 remain PROPOSED_DEFAULT" لكن جدول نفس الملف و`planning/decisions.json` معلّمين D10 ACCEPTED؛ الجملة أقدم من موافقة D10. اعتمد على الجدول.
- D01-D09 لسه `PROPOSED_DEFAULT` رغم إن السيستم مبني عليهم؛ ومفيش مرجع موافقة من المالك متسجّل ليهم.
- مفيش حالة `SUPERSEDED` ولا حقل `superseded_by` مستخدم في أي مكان في `planning/decisions.json`، فالإحلال بيتلاقى في المستندات النصية بس.
- نص الـ ADR في `docs/engineering/01-architecture.md` بيذكر JPA؛ والـ Backend بيستخدم JDBC (شوف [دليل موديولات الـ Backend](../04-codebase/backend-module-guide-ar.md)).
