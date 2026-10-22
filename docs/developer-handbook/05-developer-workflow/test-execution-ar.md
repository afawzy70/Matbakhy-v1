# تشغيل الاختبارات

> **Owner role:** Testing and QA
> **Status:** PARTIALLY IMPLEMENTED — اختبارات الوحدة والتكامل والعقود والمتصفح والـ Native كلها IMPLEMENTED وبتشتغل في الـ CI؛ وحالات قبول المالك مجرد مواصفات وحالتها NOT_RUN لحد ما تشغيل يسجّل دليل؛ والأوامر هنا NOT VERIFIED في البيئة اللي اتكتبت فيها الصفحة دي.
> **Language / اللغة:** [English](test-execution.md) · العربية

الخلفية: [01-verification.md](../../quality/01-verification.md) (المستويات وإيه اللي بيثبته كل مستوى)، [02-release-gates.md](../../quality/02-release-gates.md).

## تصنيف الاختبارات (إيه بيثبت إيه)

| المستوى | الأمر / المكان | بيحتاج | بيثبت | مش بيثبت |
|---|---|---|---|---|
| Blueprint / التخطيط | `python3 scripts/validate_blueprint.py`، `python3 scripts/check_scope.py` | Python | إن ملفات التخطيط متسقة | أي سلوك للمنتج (مخرجاته نفسها بتقول كده) |
| حواجز السكريبتات | `python3 -m unittest discover -s scripts -p 'test_*.py' -v` | Python (صور Docker اختيارية: اختبارات الـ Proxy بتتخطّى لما تكون مش موجودة) | إن سكريبتات الـ Deploy والحماية بتتصرف زي ما اتختبرت | السيرفر المنشور |
| وحدة + تكامل الـ Backend | `(cd backend && ./mvnw -B -ntp verify)` | JDK 21، Docker | قواعد الـ Domain على PostgreSQL 16 حقيقي ومحاكي S3 (من غير H2، ومن غير Database وهمية) | سهولة الاستخدام في المتصفح أو الـ Native |
| المعمارية | `ModuleBoundaryTest` في `backend/app/src/test/java/com/matbakhy/app/architecture` | جزء من `verify` | حدود الموديولات (الموديولات التانية بتستخدم Package اسمه `api` بس؛ ومفيش دورات) | السلوك وقت التشغيل |
| عقد الـ API | `pnpm contract:check` | Node، pnpm | إن `contracts/openapi.yaml` سليم في الـ Lint (Redocly) وإن الـ Client المتولّد مفيهوش انحراف | إن السيرفر بينفّذه فعلًا (`ContractRoutesTest` في `backend/app/src/test/java/com/matbakhy/app/web` بيغطي المسارات) |
| وحدة للويب / الـ Packages | `pnpm test` (Vitest، وJest للموبايل) | Node، pnpm | سلوك المكونات والمنطق | التكامل الحقيقي مع الـ API |
| الأنواع والـ Lint | `pnpm typecheck`، `pnpm lint` | Node، pnpm | الصحة الساكنة | السلوك |
| E2E في المتصفح (API حقيقي) | `scripts/dev.sh e2e` | Docker، JDK، Node، Chromium | مسارات العميل والطبّاخ والعمليات على Stack مؤقت | تطبيقات الـ Native، والمزوّدين الحقيقيين |
| Smoke المتصفح على الـ Staging | `pnpm --filter @matbakhy/operations-web e2e:staging` | بيئة معاينة منشورة وIP بتاعك في القايمة المسموحة | أوردر بعربون محاكى على بيئة المعاينة | الجاهزية للـ Production |
| Native | `.github/workflows/native-build.yml`، `apps/customer-mobile/scripts/*.sh` | Android SDK / macOS | الـ Compile والتشغيل وتسجيل الدخول واسترجاع الأوردر على Emulator/Simulator | السلوك على جهاز فعلي، والجاهزية للـ Store |
| قبول المالك | `docs/quality/owner-acceptance/` | بيئة معاينة أو Stack معزول، وFixtures | حالات يدوية متحددة، بعد ما تتنفّذ وتتسجّل | أي حاجة وهي NOT_RUN |

## الأوامر اليومية

```bash
# JavaScript/TypeScript workspace
pnpm install --frozen-lockfile
pnpm typecheck && pnpm lint && pnpm test && pnpm build
pnpm contract:check

# Single package
pnpm --filter @matbakhy/customer-web test
pnpm --filter @matbakhy/operations-web test
pnpm --filter @matbakhy/api-client test
pnpm --filter @matbakhy/design-tokens test
pnpm --filter @matbakhy/customer-mobile test      # jest --ci

# Backend
(cd backend && ./mvnw -B -ntp verify)
(cd backend && ./mvnw -B -ntp verify -Dtest=ModuleBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false)
```

السكريبت `scripts/check_workspace.sh` بيعمل كل اللي فوق من Clone جديد لـ `HEAD` اللي عملتله Commit (الـ Backend verify، وفحص العقد، والـ Typecheck، والـ Lint، والاختبارات، والبناء، وحزم Hermes لـ Android وiOS) وبيفشل لو الإصدارات مش متطابقة. التغييرات اللي ماتعملهاش Commit مش بتتختبر بيه.

## اختبارات بوابة التوثيق

الأمر `python3 scripts/test_doc001_gate.py` بيختبر بوابة DOC-001 (`scripts/doc001_check.py`) من خلال سطر الأوامر بتاعها على Repositories مؤقتة من git: الـ 12 حالة المطلوبة الإيجابية والسلبية، بالإضافة لحالات تشديد (إخفاء الإقرار، وتعديلات المسافات الفاضية بس، والتعامل مع التعليقات/الموافقات، وتغطية المصفوفة لكل ملف متتبَّع). وبيتشغّل مع باقي `scripts/test_*.py` في الـ Blueprint integrity workflow. شغّل `python3 scripts/doc001_check.py docs` لو عايز التحقق من التوثيق لوحده.

`python3 scripts/test_validate_handbook_parity.py` بيختبر الـ Validator بتاع النسخة العربي (`scripts/validate_handbook_parity.py`) على أدلة تجريبية بتتعمل وتتمسح: نسخ ناقصة ونسخ يتيمة، وانحراف العناوين وبلوكات الكود والجداول والقوايم والصور والروابط والـ Identifiers، وترجمة الـ Labels في Mermaid مقابل تغيير البنية، واتجاه الأسهم، والنصوص اللي شكلها شكل Credentials في أي لغة من اللغتين، ووجود وسوم الأدلة وتساوي عددها في صفحات البنية التحتية، والـ Anchors المكسورة، والاستثناءات الموثّقة، وسجل الأزواج المراجعة، وقاعدة نفس الـ PR. محتاج `markdown-it-py` (`python3 -m pip install 'markdown-it-py==3.0.0'`). هو بيثبت إن الفحوصات البنيوية شغّالة؛ لكن مابيثبتش إن أي ترجمة صح دلاليًا ([صيانة التوثيق](documentation-maintenance-ar.md#مزامنة-النسخة-العربي)).

الأمر `python3 scripts/test_branch_audit.py` بيختبر مراجعة الـ Branches اللي للتقرير بس (`scripts/branch_audit.py`، شوف [صيانة الـ Branches](branch-maintenance-ar.md)).

## إثبات إن الاختبارات المسمّاة اتشغّلت فعلًا

```bash
scripts/check_backend_suite.sh ClassA ClassB
```

السكريبت بيمسح تقارير surefire القديمة، وبيشغّل `verify -Dtest=...`، وبعدين بيفشل لو أي Class مذكور ماتشغّلش، أو اشتغل صفر اختبارات، أو فيه فشل/أخطاء. استخدمه لما Story أو عيب يذكر Classes اختبار بعينها: تغيير اسم Class يعتبر فشل، مش نجاح صامت.

## قواعد اختبارات الـ Backend (من الكود)

- الاختبارات اللي بتحمّل السياق كامل بتستخدم `@IntegrationTest`؛ والمساعدات في `backend/app/src/test/java/com/matbakhy/app/support/` (`TestKitchens` و`TestCommerce` و`TestUploads` و`MutableClock` و`OidcBrowser` و`MfaBrowser`). الشغل في الخلفية متعطّل في الـ Annotation ده عشان الاختبارات تتحكم في الوقت بـ `MutableClock`.
- حزم الاختبارات بتعكس الـ Domains: `orders` و`payments` و`catalog` و`availability` و`cooks` و`identity` و`mfa` و`finance` و`support` و`notifications` و`privacy` و`security` و`reliability` و`observability` و`media` و`insights` و`providers` و`db` و`config` و`web`.
- حالات الـ Race (آخر حصة، والسعة، والإرسال المكرر) لازم تستخدم تزامن حقيقي على PostgreSQL، مش sleep ([01-verification.md](../../quality/01-verification.md)).
- ماتضعّفش Assertion، ولا تعمل Mock للـ Database، ولا تمسح اختبار بيفشل عشان تجيب أخضر. دي قاعدة في المشروع ([CLAUDE.md](../../../CLAUDE.md)).

## اختبارات E2E في المتصفح (Playwright)

الـ Specs: `apps/operations-web/e2e/*.e2e.ts` (`customer-journey` و`cook-workspace` و`money-reflow` و`keyboard` و`operations-layout`)، والمساعد `apps/operations-web/e2e/fixtures.ts`، والإعداد `apps/operations-web/playwright.config.ts` (Worker واحد، والـ Trace ولقطة الشاشة بيتحفظوا عند الفشل). شغّلها عن طريق `scripts/dev.sh e2e` بس؛ هو بيجهّز Stack مؤقت بأسرار عشوائية وبيمرر متغيرات `E2E_*`. ماتوجّهوش أبدًا على بيئة مشتركة أو شبه الـ Production؛ الـ Suite بتعمل Reset وبتزرع بيانات. اضبط `E2E_CHROMIUM` لو عايز تستخدم Chromium مسطّب. السكريبت بيمسح مجلده المؤقت (بما فيه مخرجات Playwright الافتراضية `$VERIFY_TMP/results` وLogs سيرفر الويب) عند الخروج؛ ولو عايز تحتفظ بالـ Traces ولقطات الشاشة اضبط `E2E_OUTPUT_DIR` على مسار ثابت ومتجاهَل قبل التشغيل، مثلًا `E2E_OUTPUT_DIR="$PWD/test-results/e2e" scripts/dev.sh e2e`.

الـ Smoke على الـ Staging: `apps/operations-web/e2e-staging/smoke.staging.ts` مع `playwright.staging.config.ts`؛ محتاج `STAGING_APP_URL` و`STAGING_OPS_URL` و`STAGING_API_URL` و`STAGING_USER_PASSWORD` (من المسؤول عن التشغيل؛ وماتعملهمش Commit أبدًا). بيعمل أوردر Demo واحد في كل تشغيل.

## اختبارات الـ Native

المجلد `apps/customer-mobile/scripts/` فيه `launch-android.sh` و`auth-roundtrip-android.sh` و`order-roundtrip-android.sh` ومقابلاتهم لـ iOS (مصادر XCUITest تحت `apps/customer-mobile/e2e-ios`، وSQL الـ Seed تحت `apps/customer-mobile/e2e-native`). هي مكتوبة لـ Runners بتاعت الـ CI؛ وتشغيلها محليًا محتاج Emulator/Simulator والـ Stack المحلي: NOT VERIFIED. دليل Android ودليل iOS منفصلين، ومفيش واحد منهم بيتعوّض بحزمة JS.

## كتالوج قبول المالك (200 حالة)

الملفات `docs/quality/owner-acceptance/cases/*.json` فيها 200 تعريف حالة (معدودة: config-security 54، وcook-ops 39، وdiscovery-basket 45، وorders-finance 62). الـ README بتاع الكتالوج بيقول 199، و 53 لـ config-security؛ والمصدر هو الـ JSON، فصدّق 200. كل حالة حالتها NOT_RUN أو BLOCKED؛ وPASS مكانه في سجل التشغيل بس.

```bash
python3 scripts/owner_e2e_catalog.py check     # sources valid and generated files not stale
python3 scripts/owner_e2e_catalog.py render    # regenerate docs/quality/owner-acceptance/generated
python3 scripts/owner_e2e_fixtures.py check    # manifest, SQL seed and case files agree
```

اقرا بالترتيب ده: [README قبول المالك](../../quality/owner-acceptance/README.md)، و`docs/quality/owner-acceptance/generated/readiness-report.md`، والرحلات المرتبة J0-J8 في `docs/quality/owner-acceptance/generated/core-owner-acceptance-suite.md`، و[fixtures.md](../../quality/owner-acceptance/fixtures.md)، و[owner-e2e-execution-guide.md](../../quality/owner-e2e-execution-guide.md). دليل المالك بالعربي المصري هو [owner-guide-ar.md](../../quality/owner-acceptance/owner-guide-ar.md)؛ وآخر تشغيل متسجّل هو `docs/quality/owner-acceptance/run-2026-10-19.md`.

## الـ Fixtures الاصطناعية (Synthetic)

مفيش غير أطراف وهمية: `customer-a/b` و`cook-a` و`ops-a` محليًا؛ و`preview-customer-1/2` و`preview-cook-1` و`preview-ops-1/2` وحسابات `owner-e2e-*` على بيئة المعاينة. كلمات السر مش موجودة في الـ Repository أبدًا؛ والملفات المتولّدة بتروح لمجلد خاص برّه الـ Checkout. ماتستخدمش أرقام تليفونات ولا عناوين ولا أسامي حقيقية في أي مكان، بما فيها لقطات الشاشة.

## الأدلة والتعامل مع العيوب

- دليل كل Story: `verification/runs/<date>-<story>/evidence.json` بيكتبه `scripts/record_evidence.py` على مصدر نضيف معمول له Commit؛ و`scripts/mark_story.py` بيرفض DONE من غير دليل ناجح ومراجعة PASS. فحوصات الـ Schema: `python3 scripts/validate_blueprint.py`.
- مفردات النتايج: PASS لحاجة اتنفّذت ومعاها مخرجات بس؛ غير كده NOT_RUN أو BLOCKED أو FAIL. الـ CI الأخضر مش معناه "كل السيناريوهات نجحت" ([owner-e2e-execution-guide.md](../../quality/owner-e2e-execution-guide.md)).
- بلّغ عن العيب بقالب GitHub issue اسمه `.github/ISSUE_TEMPLATE/defect.md` (معرّفات الـ Story/القاعدة/السيناريو، والـ Source Commit، والخطورة، ومعرّفات الـ Fixtures الاصطناعية، والخطوات، والمتوقع، والملاحظ، والـ Logs بعد التنضيف، والثوابت المتأثرة).
- إصلاح عيب: اعمل Reproduce باختبار آلي بيفشل الأول لو ممكن، وبعدين صلّح، وأعد تشغيل الـ Suite المتأثرة والمجاورة ليها، وبعدين اذكر الاختبار في الـ PR. تغطية الـ Regression جزء من الإصلاح، مش اختيارية.

## القيود المعروفة وفجوات التوثيق

- عدد الحالات في README الكتالوج (199) مش متفق مع ملفات الحالات (200).
- مفيش أمر واحد بيشغّل "كل حاجة"؛ و`scripts/check_workspace.sh` بيتخطى فحوصات المتصفح والـ Native والتصميم والأسرار.

## مساحة اختبار السيناريوهات

[Testing Workspace](../../../scripts/testing-workspace/README.md) بتجمع كل الـ 200 حالة الأساسية في مسار تنفيذ عربي RTL فيه J0–J8 وبحث ومراجع fixtures وعلامات إتمام وتسجيل نتايج محلي. ولّد البيانات بـ `python3 scripts/testing_workspace.py`؛ وافحصها بـ `python3 scripts/test_testing_workspace.py`. [مصفوفة التغطية](../../quality/testing-workspace/coverage-matrix.md) بتفصل وجود الحالة عن اكتمال دليلها المتراجع. التسليم ده PARTIALLY IMPLEMENTED: الدليل المبدئي NOT VERIFIED وتنفيذ المنتج NOT_RUN. كلمات السر والأدلة النصية بتفضل في ذاكرة التبويب بس؛ النتيجة المستكملة محتاجة تربط دليلها تاني قبل المشاركة. الموقع مابيجهزش حسابات ولا بينفذ طلبات API للتطبيق.
