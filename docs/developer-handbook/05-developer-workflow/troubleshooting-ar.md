# حل المشاكل

> **Owner role:** Testing and QA
> **Status:** REFERENCE — أنماط فشل مأخوذة من السكريبتات وCI workflows ودليل التشغيل (Runbook)؛ ولا واحد منها اتعمله Reproduce وقت كتابة الصفحة دي.
> **Language / اللغة:** [English](troubleshooting.md) · العربية

تشخيص الأوردرات والـ Audit والـ Dead-letter والـ Telemetry على بيئة المعاينة موجود في [08-troubleshooting-runbook.md](../../operations/08-troubleshooting-runbook.md) (وهو نفسه مسودة ماتجرّبتش على بيئة منشورة). الصفحة دي بتغطي الأعطال من ناحية المطوّر.

## الإعداد والـ Toolchain

| العَرَض | السبب (المصدر) | الحل |
|---|---|---|
| `pnpm install` بيفشل بخطأ Engine | الملف `.npmrc` فيه `engine-strict=true`؛ ولازم Node يحقق `>=24.21.0 <25` | سطّب Node 24.21.0 (`.node-version`)، وشغّل `corepack enable` |
| `scripts/check_workspace.sh` بيقول "Node ... required" أو "pnpm ... required" | مقارنة إصدار بالظبط | استخدم الإصدارات المثبّتة ([prerequisites.md](prerequisites-ar.md)) |
| `scripts/dev.sh doctor` بيطبع `MISSING: JDK 21` | `java.specification.version` مش 21 | حط JDK 21 الأول في `PATH` / `JAVA_HOME` |
| الـ Maven wrapper مش قادر ينزّل، أو HTTP 429 من Maven Central | اتشاف بشكل متقطع في الـ Dev container الأصلي ([toolchain.md](../../engineering/toolchain.md)) | أعد المحاولة؛ واضبط `MVNW_REPOURL` على Mirror لـ Central للـ wrapper؛ وماتغيّرش repositories في `backend/pom.xml` |
| `docker info` بيفشل / Testcontainers مش قادر يبدأ | اختبارات التكامل محتاجة Docker daemon شغّال؛ ومفيش بديل H2 | شغّل Docker؛ وفي WSL2 فعّل تكامل Docker Desktop |
| `scripts/check_secrets.sh` بيخرج بـ 2 | Docker daemon مش متاح، والفحص محتاج صورة Gitleaks | شغّل Docker |
| `scripts/check_design.sh` مش لاقي متصفح | محتاج Chromium (`CHROMIUM_PATH`) | سطّب Chrome/Chromium وصدّر `CHROMIUM_PATH` |

## الـ Stack المحلي

| العَرَض | السبب | الحل |
|---|---|---|
| `refusing` / "not marked MATBAKHY_ENV_MARKER=local" | `ensure_env` في `scripts/dev.sh` بيقبل ملفات env المحلية بس | ماتوجّهش `MATBAKHY_ENV_FILE` على `.env` بتاع بيئة منشورة أبدًا |
| البورت مستخدم (5432، 8080، 8180، 9090، 8025، 1025) | القيم الافتراضية في `infra/.env.example`؛ والـ compose بينشر على `127.0.0.1` | وقّف العملية التانية، أو عدّل قيم البورتات في ملف env المحلي بتاعك (وخلّي رابط الـ API في تطبيقات الويب متماشي معاها) |
| `up` بيعلّق وبعدين يبلّغ unhealthy | الـ compose بيستنى لحد 300 ثانية للصحة | `docker compose --env-file infra/.env -f infra/compose.yaml ps` و`logs --tail 100 api identity postgres` |
| الـ Smoke: "Flyway baseline not applied" | الـ API ماشتغلش أو الـ Migrations فشلت | اقرا Logs الـ API؛ الـ Migration الفاشلة بتظهر بـ `success = false` في `flyway_schema_history` |
| الـ Smoke: "Keycloak realm discovery failed" | استيراد الـ realm فشل؛ والـ realm المتولّد موجود في infra/.generated/identity (متجاهَل من git) | `logs identity`؛ وبعدين `scripts/dev.sh reset` ثم `up` (بيولّد الـ realm تاني) |
| أسرار الـ Realm باينة قديمة | قيم `OIDC_*_CLIENT_SECRET` في ملف env مختلفة عن الـ realm المتولّد | `scripts/dev.sh reset` ثم `up` |
| تطبيق الويب: تسجيل الدخول بيحوّل لـ Host غلط أو بيلف في Loop | التطبيقات لازم تشتغل على `localhost:5173` / `5174` (`strictPort`؛ والـ CORS والـ redirect في الـ API متثبتين على دول) | فضّي البورتات؛ وافتح التطبيق على `localhost` |
| الـ API بيرفض يبدأ: `APP_ENV` ناقص | `matbakhy.environment: ${APP_ENV}` مالوش قيمة افتراضية في `application.yaml` | اضبط `APP_ENV=LOCAL` (الـ compose بيعمل كده) |
| "The dev profile is not allowed in ..." | `EnvironmentGuard` بيمنع الـ Profile `dev` وdevdata في STAGING/PRODUCTION | شيل `SPRING_PROFILES_ACTIVE=dev` من البيئات غير الاصطناعية (non-synthetic) |
| الرفع المباشر بيفشل، والـ Signed URL بيشاور على بورت غلط | الـ Signed URLs بتستخدم البورت المنشور على الـ Host (`OBJECT_STORAGE_CLIENT_ENDPOINT`) بينما اتصال الـ API بالتخزين بيستخدم شبكة الـ compose | خلّي `OBJECT_STORAGE_PORT` متسق في ملف env |

## الاختبارات

| العَرَض | السبب | الحل |
|---|---|---|
| `scripts/check_backend_suite.sh` بيطبع `FAIL: X did not run` | اتغيّر اسم الـ Class، أو الاسم غلط، أو اتفلتر | استخدم اسم الـ Class البسيط؛ وراجع `backend/**/target/surefire-reports` |
| اختبار التكامل بيعلّق في البداية | سحب الحاوية، أو Docker بطيء، أو مورد Testcontainers قديم | راجع Docker والمساحة الفاضية وأعد التشغيل؛ وماتستبدلش الحاوية بـ Mock أبدًا |
| اختبار Race بيفشل بشكل متقطع | sleep بدل Barrier | صلّح الاختبار عشان يستخدم تنسيق محدد ([01-verification.md](../../quality/01-verification.md))؛ وماتعيدش التشغيل بس لحد ما يبقى أخضر |
| فحص العقد بيفشل بانحراف | اتغيّر `contracts/openapi.yaml` من غير ما يتولّد `packages/api-client/src/generated/schema.ts` تاني | `pnpm --filter @matbakhy/api-client run generate`، واعمل Commit، وأعد `pnpm contract:check` |
| `scripts/dev.sh e2e` فشل في تشغيل الويب | البورتات 5173/5174 مشغولة | فضّي البورتات؛ وعند الفشل السكريبت بيطبع Log سيرفر الويب (`web.log` / `customer.log`) في الـ Terminal وبعدين بيمسح مجلده المؤقت عند الخروج، فانسخ اللي محتاجه من الـ Terminal؛ ولو عايز تحتفظ بـ Playwright Traces اضبط `E2E_OUTPUT_DIR` على مسار ثابت ومتجاهَل قبل التشغيل |
| Playwright مش قادر يشغّل متصفح | مفيش متصفح منزّل | اضبط `E2E_CHROMIUM` على Chromium مسطّب |
| `check` بتاع كتالوج المالك بيفشل بـ "stale" | اتعدّل ملف متولّد أو حالة من غير ما يتعمل render | `python3 scripts/owner_e2e_catalog.py render`؛ وعدّل `cases/*.json` و`core-suite.json` و`coverage-notes.json` بس |
| فحص الـ Fixtures بيفشل | الـ manifest وSQL الـ Seed والحالات مش متفقين | `python3 scripts/owner_e2e_fixtures.py check` بيسمّي عدم التطابق |
| اختبار توافق الـ Migration بيفشل | Migration جديدة فيها جملة خطرة من غير `-- rollback-compat: <class> <reason>` (والسبب عشر حروف على الأقل) | ضيف التعليق بأمانة؛ ولو الـ Migration كاسرة قول كده في الـ PR |
| اختبارات الـ Proxy في `unittest discover` اتخطّت | صور Docker `caddy` و`python` مش موجودة | `docker pull caddy:2.10.2-alpine python:3.13-alpine` زي ما بيعمل `blueprint-integrity.yml` |

## Git والـ PR والتوثيق

| العَرَض | الحل |
|---|---|
| `doc001_check.py docs` بيفشل على رابط أو `path` | المسار اللي بين Backticks لازم يكون موجود؛ استخدم `<placeholders>` أو Code Block للمسارات المستقبلية |
| `doc001_check.py pr` بيبلّغ "area ... changed but none of its required documentation was updated" | حدّث صفحة من المكتوبة لذلك الـ area في `docs/developer-handbook/doc001-matrix.json` واذكرها في `handbook_pages` |
| الإقرار اترفض: التبرير عام | سمّي السلوك الموثّق اللي ماتغيّرش واذكر صفحة موجودة بمسارها ([documentation-policy.md](documentation-policy-ar.md)) |
| الـ CI بيفشل على Branch رغم إن التشغيل المحلي أخضر | الـ CI بيستخدم Clone نضيف لـ `HEAD` اللي عملتله Commit وإصدارات مثبّتة بالظبط؛ والملفات اللي ماتعملهاش Commit أو مش متتبَّعة، أو Node مختلف، أو تحديث Lockfile ناقص بيفسّروا معظم الاختلافات |
| الـ Push اترفض | اعمل Fetch وMerge أو Rebase للـ Branch بتاعك انت؛ وماتعملش force-push لـ Branch مشترك أبدًا |

## بيئة المعاينة

| العَرَض | الحل |
|---|---|
| HTTP 403 "Not available" | الـ IP بتاعك برّه القايمة المسموحة لفئة الـ Host دي؛ اسأل المالك ([preview-environment.md](preview-environment-ar.md)) |
| `kc-admin` بيرد 404 | مفتاح اللوحة مقفول؛ المسؤول عن التشغيل يشغّل `./deploy.sh admin-console status` |
| الـ Deployment workflow واقف عند "Wait for passing Product CI" | الـ Product CI عند الـ Commit ده لسه مستني أو فشل؛ صلّح الـ CI الأول |
| الـ Deploy فشل، والـ `.env` السابق اترجّع | الـ Database **ماترجعتش**؛ المسؤول عن التشغيل يستخدم `./deploy.sh rollback-plan` وجدول القرار في [11-staging-deployment-and-rollback.md](../../operations/11-staging-deployment-and-rollback.md) |
| الصفحة الرئيسية فاضية | الـ Slots اللي اتضافت بالـ Seed انتهت؛ `refresh-slots.sql` (جزء من `apply` بتاع E2E المالك) |
| الـ Workflow بتاع `owner-e2e-provision` بيرفض | أول كلمة في ملف الطلب لازم تكون `plan` أو `apply`؛ و`apply` كمان محتاج `APP_ENV=PREVIEW` للـ API والـ Schema على الأقل V31 |
| محتاج تتتبّع أوردر فشل | الـ request id من جسم الخطأ أو `X-Request-Id`، وبعدين الـ Logs/الـ Audit زي ما في [08-troubleshooting-runbook.md](../../operations/08-troubleshooting-runbook.md) |

## لما تتعطّل

حاول تصلّح العيب بحد أقصى تلات طرق مختلفة فعلًا، وبعدين اكتب العائق بالظبط (الأمر، والمخرجات، وإيه اللي جرّبته) في الـ PR أو الـ Issue وانتقل لشغل مستقل ([CLAUDE.md](../../../CLAUDE.md)). غلطة في التوثيق بتلاقيها في الطريق تعتبر عيب: صلّح صفحة الدليل في نفس الـ PR.
