# البنية التحتية والـ Deployment

> **Owner role:** Infrastructure and deployment
> **Status:** PARTIALLY IMPLEMENTED — فيه Stack محلي ومعاينة ببيانات اصطناعية (Synthetic) محدودة الوصول؛ ومفيش Deployment للـ Staging أو الـ Production، والمعاينة مش Production
> **Language / اللغة:** [English](infrastructure-and-deployment.md) · العربية

الرسومات: [CI/CD](diagrams-ar.md#13-cicd) و[البنية التحتية للمعاينة](diagrams-ar.md#14-بنية-المعاينة). الإجراءات اليومية مكانها [بيئة المعاينة](../05-developer-workflow/preview-environment-ar.md) و[CI/CD](../05-developer-workflow/ci-cd-ar.md)؛ والصفحة دي بتشرح الأجزاء إيه وإزاي بتتركب مع بعض.

## البيئات في نظرة سريعة

| البيئة | بتشتغل فين | الإعدادات | البيانات | الغرض |
|---|---|---|---|---|
| التطوير المحلي | جهاز المطوّر، Docker Compose + JDK/Node محلي | `infra/compose.yaml`, `scripts/dev.sh` | Fixtures اصطناعية من الـ Profile اسمه `dev` | الشغل اليومي |
| CI | Runners مستضافة على GitHub | `.github/workflows/*.yml` | Testcontainers وStacks Compose مؤقتة | البناء، والاختبار، وفحوصات الـ Native |
| المعاينة (Preview) | VPS واحد بيشغّل Docker Compose (المشروع `matbakhy-preview`) | `deploy/web-preview/` | اصطناعية بس، وعربون محاكى، وإشعارات وهمية، وإشعار ديمو ظاهر | معاينة ويب مبكرة محدودة الوصول للمالك والمختبرين المدعوّين |
| Staging, Production | مفيش | الأسماء موجودة في الكود بس | n/a | PLANNED؛ والتطبيق بيرفض يشتغل هناك من غير مزوّدين حقيقيين |

متوصفش المعاينة إنها Production: هي بتشتغل بـ `APP_ENV=PREVIEW` مع مزوّدين وهميين وFixtures الـ Profile بتاع التطوير ومن غير فلوس حقيقية وعلى Host واحد من غير Redundancy.

## الـ Stack المحلي (`infra/compose.yaml`)

الأمر `scripts/dev.sh up` (شوف الـ Header بتاعه لـ `doctor` و`up` و`smoke` و`down` و`reset` و`verify` و`e2e`) بيولّد أسرار محلية عشوائية في ملف infra/.env المتجاهَل في Git، وبيعمل Render للـ Realm في مجلد متولّد، وبيبني الـ API jar وبيشغّل الخدمات دي، وكلها مربوطة على `127.0.0.1`:

| الخدمة | الصورة | البورت (الافتراضي) |
|---|---|---|
| `postgres` | `postgres:16.15-alpine` (مثبّتة بالـ Digest) | 5432 |
| `identity` | Keycloak 26.8.0, `start-dev --import-realm` | 8180 |
| `object-storage` | Adobe s3mock 5.2.3 مع Buckets اسمها `matbakhy-private` و`matbakhy-public` | 9090 |
| `mail` | Mailpit | 8025 للـ UI، و 1025 لـ SMTP |
| `api` | متبني من `backend/Dockerfile`، و`APP_ENV=LOCAL`، والـ Profile اسمه `dev` | 8080 |

تطبيقات الويب بتشتغل برّه Docker عن طريق Vite (`pnpm --filter ... dev`، البورتات 5173 و 5174). التفاصيل: [البيئة المحلية](../05-developer-workflow/local-environment-ar.md). الـ Mail sink موجود لشغل الإيميل بعدين؛ و Adapter الإيميل النهارده بيكتب في الـ Log بس (شوف [التكامل والأحداث](integration-and-events-ar.md)).

## Stack المعاينة (`deploy/web-preview`)

الملف `compose.yaml` بيعرّف (كل الصور مثبّتة بالـ Digest إلا لو متبنية بواسطة الـ CI):

| الخدمة | الدور | ملاحظات |
|---|---|---|
| `proxy` | Caddy 2.10 جوه صورة الـ "front": بيقدّم الـ Builds بتاعة الـ SPA الاتنين، وReverse proxy، وTLS (ACME تلقائي)، وقوايم السماح، وHeaders الأمان | الخدمة الوحيدة اللي بتنشر بورتات (80 و 443)؛ ذاكرة 256 MB |
| `api` | صورة Spring Boot اسمها `ghcr.io/<owner>/matbakhy-api@sha256:...` | `APP_ENV=PREVIEW`، والـ Profile اسمه `dev`، وLogs بصيغة ECS JSON، وبورت الإدارة 8081، ومستخدم الـ Database هو `matbakhy_app`، ومستخدم Flyway هو `matbakhy`؛ والحد الأقصى 2 GB |
| `postgres` | PostgreSQL 16 مع Databaseين (`matbakhy` و`keycloak`) وسكريبتات تهيئة `postgres/10-keycloak.sh` و`postgres/20-runtime-role.sh` | الـ Backups متركّبة على `/backups` |
| `identity` | Keycloak بأمر `start` مع Database على Postgres؛ والـ Hostnames هي `id.<domain>` و`kc-admin.<domain>` | الـ Realm بيتستورد من Realm المعاينة بعد الـ Render |
| `storage` | SeaweedFS S3 (:8333) مع `s3.json` متولّد؛ والـ Buckets بتتعمل بشكل Idempotent بواسطة `deploy.sh up` | الـ Bucket العام مقروء لأي حد من غير Authentication |
| `loki`, `alloy`, `prometheus`, `alertmanager`, `alert-sink`, `node-exporter` | الـ Logging والـ Metrics والتنبيهات | الإعدادات في `deploy/web-preview/observability`؛ والاحتفاظ من `.env` (الـ Logs 30 يوم، والـ Debug 7 أيام، والـ Metrics 15 يوم؛ افتراضيات مقترحة للـ Staging، مش سياسة Production) |
| `grafana` | لوحات على `127.0.0.1:3000` بس (توصّل ليه بـ SSH tunnel) | الـ Profile اسمه `tools` في الـ Override بتاع 4 GB |

الشبكات: `edge` (الـ Proxy)، و`internal` (معرّفة `internal: true`: مفيش طريق للإنترنت للـ Database والهوية والتخزين والـ API)، و`ops` (Grafana على الـ Loopback بس). والملف `compose.4gb.yaml` بيخفّض حدود الذاكرة لـ Host بـ 2 vCPU / 4 GB (حوالي 3.3 GiB إجمالي، مش استخدام متقاس). الملفين بيتستخدموا مع بعض عن طريق `COMPOSE_OVERRIDE` في `.env` على السيرفر.

### الـ Hostnames والتوجيه (`Caddyfile`)

| الـ Host | بيقدّم إيه | فئة الوصول |
|---|---|---|
| `app.<domain>` | ويب العميل (ثابت، مع SPA fallback) | العميل |
| `ops.<domain>` | ويب العمليات | الموظفين |
| `api.<domain>` | `/api/*` و`/oauth2/*` و`/login/oauth2/*` و`/logout` إلى `api:8080`؛ و`/api/v1/ops/**` و`/api/v1/cook/**` بالإضافة لكده للموظفين بس | العميل + الموظفين للمسارات ذات الصلاحيات |
| `id.<domain>` | الـ Realm بتاع Keycloak اسمه `matbakhy` (الـ admin والـ master realm محجوبين) | العميل |
| `media.<domain>` | SeaweedFS S3 (ميديا عامة، وروابط Presigned) | العميل |
| `kc-admin.<domain>` | الـ Admin console بتاع Keycloak، طالما ملف الـ Flag موجود بس | مش مقيّد بـ IP؛ ومحمي بـ OTP |

فئات الوصول والـ Headers والـ CSP وحدود الـ Admin console متشرحة في [الهوية والأمان](identity-and-security-ar.md#تأمين-المتصفح-والـ-proxy). والسكريبت `scripts/test_proxy_access.py` بيشغّل الـ Caddyfile على القواعد دي.

### الأسرار والإعدادات على السيرفر

الأمر `deploy.sh init-secrets` بيعمل الملف deploy/web-preview/.env المتجاهَل في Git من `deploy/web-preview/.env.example` بقيم متولّدة (صلاحية 600، عمره ما بيتعمل له Commit؛ والمثال فيه Placeholders اسمها `REPLACE_ME` بس). والأمر `deploy.sh render` بيكتب إعدادات الـ Realm والتخزين (اللي فيها أسرار) في `GENERATED_DIR` على السيرفر. و`compose.yaml` بيرفض يشتغل من غير كل متغيّر مطلوب. مفاتيح `.env` اللي بتأثر على السلوك: `PREVIEW_DOMAIN` و`ALLOWED_CLIENT_IPS` و`CUSTOMER_ALLOWED_IPS` و`STAFF_ALLOWED_IPS` و`PUSH_DELIVERY_ENABLED` (false) و`COMPOSE_OVERRIDE` وقيم الاحتفاظ و`RELEASE_COMMIT` و`API_IMAGE` و`FRONT_IMAGE`.

## خط التسليم (Delivery pipeline)

### التكامل المستمر (`.github/workflows/ci.yml`، "Product CI")

بيتشغّل مع كل Push وPull Request. الـ Jobs: **workspace** (`scripts/check_workspace.sh`: Clone نضيف، وفحص Node/pnpm/JDK 21 المثبّتين، و`./mvnw verify`، و`pnpm install --frozen-lockfile`، وLint للـ OpenAPI وDrift للـ Client، وtypecheck، وlint، واختبارات الوحدة، والـ Builds، وHermes bundles للـ Android و iOS)، و**local-stack** (تهيئة وSmoke على نمط `scripts/dev.sh verify`)، و**design** (الـ Tokens والـ Specimens والرحلات بـ Chrome الخاص بالـ Runner)، و**secrets** (Gitleaks على التاريخ والشجرة). وNode بيتثبّت من Tarball متحقق من الـ Checksum بتاعه. Workflows تانية: `blueprint-integrity.yml` (ملفات التخطيط)، و`native-build.yml` (Builds الـ Native للـ Android/iOS وفحوصات الـ Emulator/Simulator؛ شوف [معمارية الموبايل](mobile-architecture-ar.md#أصول-الـ-native-build-واختبار-الأجهزة))، و`owner-e2e-fixtures.yml` (بيتحقق من Fixtures قبول المالك)، و`preview-images.yml` (Build يدوي للصور لدومين مختار، ومبيعملش Deployment لحاجة).

### النشر المستمر (Continuous deployment) للمعاينة (`.github/workflows/deploy-preview.yml`)

بيتشغّل مع كل Push على `develop` (وبالتشغيل اليدوي). متسلسل بمجموعة التزامن `matbakhy-preview-deploy` (من غير إلغاء).

1. Job الـ **build**: بيستنى (بيعمل Polling لحد حوالي 30 دقيقة) *تشغيل Product CI ناجح من نوع push على نفس الـ Commit SHA بالظبط*؛ وبيبني الـ API jar بـ Maven؛ وبيبني صورتين (`matbakhy-api` و`matbakhy-preview-front`) متعلّمين بالـ SHA الكامل وبيعمل لهم Push على GHCR؛ وبيطلّع الـ Digests. وصورة الـ front بتدمج دومين المعاينة جوه Bundles الويب عن طريق `VITE_API_BASE_URL`، فصورة front واحدة بتناسب دومين واحد.
2. Job الـ **deploy** (البيئة `preview`، على `develop` بس): بيركّب مفتاح الـ SSH وHost key مثبّت من أسرار الـ Repository، وبيتأكد إن `.env` على السيرفر موجود، وبينسخ `deploy/` و`infra/` و`scripts/` وملف الـ Fixtures بـ `tar` على SSH، وبعدين بيشغّل سكريبت بعيد بيعيد كتابة `RELEASE_COMMIT` و`API_IMAGE` و`FRONT_IMAGE` في `.env` على السيرفر، وبيسجّل دخول GHCR بـ Token الـ Job، وبيشغّل `./deploy.sh up` و`./deploy.sh smoke`. ولو أي منهم فشل بيتم استرجاع `.env` السابق؛ **ومفيش Rollback تلقائي للـ Database**.
3. وأخيرًا `./deploy.sh admin-console enable` بيأمّن وينشر الـ Admin console بتاع Keycloak (للمعاينة بس)، وبيتحقق منه حي وبيقفله تاني لو فشل.

أما `./deploy.sh up` نفسه: بيفحص الصور المثبّتة والأسرار، وبياخد قفل التغيير المشترك (`flock` على `.preview-mutation.lock` في مجلد الـ Backup)، وبيعمل Render للإعدادات، وبياخد **Backup للـ Database قبل الـ Deployment** لو الـ Database موجودة، وبيعمل Pull للصور الناقصة، و`compose up -d`، وبيستنى الـ Health، وبيعمل الـ Buckets، وبيضيف سطر لسجل الإصدارات (`releases.log` في مجلد الـ Backup) فيه الـ Commit وDigests الصور ونسخة الـ Schema قبل وبعد ومسار الـ Backup. وحاوية الـ API بتطبّق Flyway migrations عند البدء.

الـ Workflow اسمه `owner-e2e-provision.yml` بيتفاعل مع تغييرات `deploy/web-preview/owner-e2e-run.request` على `develop`: بعد Deployment نفس الـ Commit، بيشغّل تجهيز E2E بتاع المالك على المعاينة (الخطة = قراءة بس؛ والتطبيق = Backup متحقق منه، وPartial import لـ Keycloak، وSQL seeds، وتحديث الـ Slots، وتسوية قراءة بس) تحت مجموعة التزامن الخاصة بيه؛ و`flock` على السيرفر بيسلسله مع الـ Deployments والـ Admin console.

عنوان الـ Workflow بيقول "Contabo" بينما مستندات العمليات بتوصف سيرفر Hetzner؛ شوف الفجوة تحت.

## فحوصات الـ Smoke والـ Health

الأمر `deploy.sh smoke` بيشغّل `deploy/web-preview/smoke.sh <domain>`: فحوصات HTTPS قراءة بس لويب العميل (الصفحة، والـ Headers، وإشعار الديمو في الـ Build)، وويب العمليات، والـ API، والـ Authentication، وحدود العمليات ذات الصلاحيات؛ وهو مبيعملش أي بيانات. أما الـ Smoke بالمتصفح مع أوردر عربون محاكى فهو `apps/operations-web/e2e-staging/smoke.staging.ts`. الـ Health: فحوصات صحة الحاويات (الـ API: `GET :8081/actuator/health` على بورت الإدارة؛ وKeycloak: مستند OIDC discovery؛ وPostgres: `pg_isready`)؛ والـ Compose بيستناهم (`wait_healthy`).

## النسخ الاحتياطي والاسترجاع والـ Rollback

* **الـ Backup** (`deploy.sh backup`، وكمان قبل كل Deployment): `pg_dump` بصيغة Custom مع Checksum بـ SHA-256 وManifest، ومتحقق منه بالاسترجاع في Database مؤقتة وتشغيل `infra/db/reconcile.sql`، بالإضافة لأرشيف لتخزين الصور. الناتج بيروح لـ `BACKUP_DIR` (برّه Git). وعمر الـ Backup بيتصدّر كـ Metric وعليه تنبيه (`BackupStale`).
* **الاسترجاع** (`deploy.sh restore-db <dump>`): بيسترجع في `matbakhy_restore` وبيعمل له تسوية؛ وتحويل التطبيق ليه خطوة يدوية على المالك. البيانات اللي اتكتبت بعد الـ Backup بتضيع.
* **الـ Rollback** (`deploy.sh rollback-plan [N]`، قراءة بس): بيطبع قيم `.env` التلاتة للإصدار اللي قبل بـ N مرحلة وحُكم. لو نسخة الـ Schema ماتحركتش، الـ Rollback للصور بس آمن (عدّل `.env`، و`./deploy.sh up`). ولو اتحركت، الـ Rollback للصور بس آمن بس لو كل Migration في النص هي expand-only؛ و`scripts/check_migration_compat.py` بيجبر كاتبي الـ Migrations بعد V28 إنهم يعلّموا بـ `-- rollback-compat: breaking|tightening|expand-only`، والـ CI بيفرض ده. غير كده استرجع الـ Backup بتاع ما قبل الـ Deployment. الـ Runbook: `docs/operations/11-staging-deployment-and-rollback.md`؛ والتدريب: `scripts/rehearse-preview.sh rollback`.
* **إعادة ضبط الـ E2E** (`deploy.sh e2e-reset`): بيمسح Database التطبيق؛ وبيرفض إلا لو الـ Host عليه ملف علامة E2E، وHosts العملاء مش عامة، والمشغّل كتب الدومين؛ وبياخد Backup الأول.
* الاختبارات: `backend/app/src/test/java/com/matbakhy/app/reliability/BackupRestoreIntegrationTest.java` و`scripts/test_preview_backup.py` و`scripts/test_preview_release.py`.

## المراقبة والتنبيهات

الـ Logs بتاعة الـ API بصيغة ECS JSON مع Request IDs؛ وAlloy بيقرا Logs الحاويات لـ Loki؛ وPrometheus بيسحب من بورت إدارة الـ API وnode-exporter وLoki وAlloy وAlertmanager؛ والقواعد في `deploy/web-preview/observability/alerts.yml` (واختباراتها في `alerts.test.yml`) فيها `ApiDown` و`DatabaseUnavailable` و`LedgerUnbalanced` و`AuditWriteFailed` و`OutboxStale` و`OutboxFailed` و`NotificationsDeadLettered` و`AcceptanceExpiryLagging` و`OrderPlacementFailures` و`BackupStale` و`DiskPressure` و`LogPipelineDown`. وAlertmanager بيوجّه لـ `alert-sink` (مستقبِل Python محلي صغير بيخزّن التنبيهات)، فـ **مفيش حد بشري بيتبلّغ**؛ ومفيش قناة تنبيه خارجية متظبطة (NOT VERIFIED على الـ Host الحي). معايير الجاهزية: `docs/operations/05-observability-readiness.md` و`docs/operations/07-audit-and-log-readiness.md`.

## حدود جاهزية الـ Production (إيه اللي مش موجود)

* مفيش بيئة Production أو Staging ولا دومين ولا تصميم TLS ولا WAF ولا CDN ولا Database مُدارة؛ Host واحد، ومفيش Redundancy، ومفيش Autoscaling.
* مفيش مزوّدين حقيقيين للـ SMS أو الإيميل أو الـ Push، ولا خدمة فاحص Malware، ولا خدمة تخزين Objects متحددة (قرارات المالك O07 و O08).
* الفلوس الحقيقية مقفولة بالامتثال (قرار المالك D16)؛ ومفيش تكامل دفع.
* وصول العملاء محصور بقايمة سماح IPs لحد ما المالك يفتح `CUSTOMER_ALLOWED_IPS` صراحةً؛ والـ CSP في وضع Report-Only؛ وHost الـ Admin console عام بالتصميم (للمعاينة بس).
* سياسات الاحتفاظ والخصوصية والنسخ الاحتياطي خارج الجهاز هي افتراضيات مقترحة في انتظار قرار المالك O06؛ والـ Backups بتفضل على نفس الـ Host (ومتلقاش نسخة خارج الـ Host في السكريبتات: NOT VERIFIED).
* الموبايل: معرّفات التطوير بس؛ ومفيش تقديم على المتاجر؛ ومفيش تحقق على أجهزة فعلية.

## القيود المعروفة وفجوات التوثيق

* **تعارض في تسمية المزوّد.** الملفين `.github/workflows/deploy-preview.yml` و`.github/workflows/owner-e2e-provision.yml` بيقولوا "Contabo"، و`docs/operations/09-staging-hetzner-preparation.md` و`12-owner-server-setup.md` و`deploy/web-preview/README.md` بيقولوا "Hetzner". الـ Workflow هو اللي بيعمل الـ Deployment فعلًا (الـ Host من سر `STAGING_SSH_HOST`، والدومين `167-86-90-174.sslip.io` مدموج في Build صورة الـ front)؛ والمزوّد NOT VERIFIED من الـ Repository. ومسار السيرفر اللي بيستخدمه الـ CD هو `/srv/matbakhy-staging`، بينما `.env.example` بيقول `/srv/matbakhy-preview`: بقية تانية من تسمية "staging".
* `deploy/web-preview/README.md` لسه بيقول "Nothing here has been deployed"، وده قديم دلوقتي إن الـ CD بيعمل Deployment مع كل Push على `develop`.
* المعاينة بتشغّل Spring Profile اسمه `dev` (مسموح لإن `PREVIEW` بتتحسب بيئة اصطناعية)، فـ Migrations الـ Fixtures الخاصة بالتطوير شغّالة هناك.
