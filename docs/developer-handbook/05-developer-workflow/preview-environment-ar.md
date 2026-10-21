# بيئة المعاينة

> **Owner role:** Infrastructure and deployment
> **Status:** PARTIALLY IMPLEMENTED — بيئة معاينة ببيانات اصطناعية (Synthetic) على VPS واحد بتتنشر من `develop` بواسطة `.github/workflows/deploy-preview.yml`؛ هي مش Staging ومش Production. حالة السيرفر ماتفحصتش وقت كتابة الصفحة (NOT VERIFIED).
> **Language / اللغة:** [English](preview-environment.md) · العربية

## هي إيه

بيئة الوصول ليها مقيّد، ومعلّمة إنها Demo، وبياناتها اصطناعية، وبتشتغل بـ Docker Compose من `deploy/web-preview` (`compose.yaml`، وملف `compose.4gb.yaml` الاختياري لتعديل الأحجام، وCaddy كـ Reverse Proxy في `deploy/web-preview/Caddyfile`، وقالب Keycloak realm تحت `deploy/web-preview/identity`، وتخزين متوافق مع S3 تحت `deploy/web-preview/storage`، وإعدادات Grafana/Loki/Prometheus تحت `deploy/web-preview/observability`). الـ API بيشتغل بـ `APP_ENV=PREVIEW`، والكود بيعتبره بيئة اصطناعية (مسموح فيها الـ Fixtures ومزوّدي الخدمة اللي مابيبعتوش فعلًا؛ شوف `DeploymentEnvironment` في `backend/app/src/main/java/com/matbakhy/app/config/MatbakhyProperties.java`). الإعدادات العامة بترجّع `{"environment":{"name":"PREVIEW","demo":true}}`، وده اللي بيتحقق منه `deploy/web-preview/smoke.sh`.

الخلفية: [06-web-preview.md](../../operations/06-web-preview.md)، [09-staging-hetzner-preparation.md](../../operations/09-staging-hetzner-preparation.md)، [11-staging-deployment-and-rollback.md](../../operations/11-staging-deployment-and-rollback.md)، [12-owner-server-setup.md](../../operations/12-owner-server-setup.md).

## العناوين

الـ Hosts ماشية على النمط اللي تحت. الـ `<PREVIEW_DOMAIN>` الفعلي موجود في ملف `.env` على السيرفر ومتحط جوّه صورة الـ Front بواسطة الـ Deploy Workflow؛ وماتنسخش هنا في الدليل عن قصد. اسأل المالك، أو اقرا دليل المالك [owner-guide-ar.md](../../quality/owner-acceptance/owner-guide-ar.md).

| الـ Host | الغرض | فئة الوصول |
|---|---|---|
| `https://app.<PREVIEW_DOMAIN>` | ويب العميل | العميل |
| `https://ops.<PREVIEW_DOMAIN>` | مساحة عمل الطبّاخ والـ Back Office (ويب العمليات) | الموظفين |
| `https://api.<PREVIEW_DOMAIN>` | الـ API (`/api/v1/*`؛ ومساري `/api/v1/ops` و`/api/v1/cook` من فئة الموظفين) | العميل، ومسارات الموظفين للموظفين |
| `https://id.<PREVIEW_DOMAIN>` | Keycloak realm `matbakhy` (تسجيل الدخول) | العميل |
| `https://kc-admin.<PREVIEW_DOMAIN>` | لوحة إدارة Keycloak، في بيئة Preview بس، وبتتفتح بـ `deploy.sh admin-console enable`؛ بتتعمل بتشديد أمني وبتتقفل تاني لو التحقق فشل | شوف [14-keycloak-admin-console.md](../../operations/14-keycloak-admin-console.md) |
| `media.<PREVIEW_DOMAIN>` | الـ Bucket العام للميديا | العميل |

مفيش Credentials مكتوبة هنا. أسماء الحسابات الوهمية: `preview-customer-1` و`preview-customer-2` و`preview-cook-1` و`preview-ops-1` و`preview-ops-2`، ومجموعة `owner-e2e-*` بعد ما تتجهّز. كلمات السر محفوظة في الملفات الخاصة بالسيرفر بس والمالك هو اللي بيسلّمها.

## مين يقدر يوصلها

الرفض هو الافتراضي. الـ Proxy فيه فئتين (التعليق في أول `deploy/web-preview/Caddyfile`):

- فئة العميل: `CUSTOMER_ALLOWED_IPS` للـ `app.` والـ API الخاص بالعميل و`id.` و`media.`؛
- فئة الموظفين: `STAFF_ALLOWED_IPS` للـ `ops.` و`/api/v1/ops` و`/api/v1/cook`.

الاتنين قيمتهم الافتراضية `ALLOWED_CLIENT_IPS`؛ ومفيش حاجة عامة لحد ما المالك يضبط `CUSTOMER_ALLOWED_IPS` عشان يفتح الوصول. أي حد من برّه بياخد HTTP 403 "Not available". لو شفت 403، يبقى الـ IP بتاعك مش في القايمة: ابعت للمالك الـ IP العام بتاعك والـ Host اللي محتاجه؛ وماتطلبش أبدًا إن القايمة تتفتح للكل. مسارات الـ Master-realm و`/admin` على `id.` بترد 404.

## استخدامها

1. خلّي المالك يضيف الـ IP بتاعك في القايمة المسموحة ويدّيك الحساب اللي محتاجه.
2. افتح `https://app.<PREVIEW_DOMAIN>` (عربي RTL افتراضيًا؛ ولازم إشعار الـ Demo يكون ظاهر).
3. سجّل دخول عن طريق Keycloak؛ ميزات الموظفين محتاجة حساب عنده الصلاحيات الصح ([actors-and-permissions.md](../01-business/actors-and-permissions-ar.md)).
4. شغّل الـ Smoke للقراءة بس من جهازك: `sh deploy/web-preview/smoke.sh <PREVIEW_DOMAIN>` (استخدم `SMOKE_OPS=skip` لو انت مش في فئة الموظفين).
5. الـ Smoke عن طريق المتصفح: `pnpm --filter @matbakhy/operations-web e2e:staging` مع متغيرات `STAGING_*` ([test-execution.md](test-execution-ar.md)). بيعمل أوردر Demo واحد في كل تشغيل.
6. معرفة الإصدار المنشور: اطلب من المسؤول عن التشغيل إنه يشغّل `./deploy.sh status` (الصور الشغّالة، وإصدار الـ Schema، وآخر الإصدارات). قارن الـ Commit مع `git log`. ممكن بيئة المعاينة تكون أقدم من `develop` لو في Deployment واقف في الطابور.

## هي مش إيه

- مش Production: مفيش عملاء حقيقيين، ولا فلوس حقيقية، ولا SMS/email/push فعلي (المزوّدين Fakes؛ و`PUSH_DELIVERY_ENABLED` قيمته الافتراضية false في `application.yaml`)، ولا اعتماد قانوني/امتثال لمزوّدي الخدمة.
- مش Staging بتكاملات حقيقية (ده MB-040 في `planning/backlog.json`، والحالة تتراجع هناك).
- مش عالية التوافر: سيرفر واحد، والنسخ الاحتياطية محلية على الجهاز لحد ما تتنسخ برّه، والتنبيهات بتروح لمكان اختبار محلي لحد ما المالك يوافق على مستلم ([08-troubleshooting-runbook.md](../../operations/08-troubleshooting-runbook.md)).
- مش مكان لبيانات شخصية حقيقية ولا لاختبارات استكشافية مدمّرة؛ الحالات المدمّرة وحالات الـ Race مكانها Stack معزول ([owner-e2e-execution-guide.md](../../quality/owner-e2e-execution-guide.md)).
- مش تصريح بإنك تكشف Keycloak admin أو تضعّف التحكم في الوصول عشان اختبار ينجح.

## تشغيلها (للمسؤولين عن التشغيل بس)

بتتشغّل على السيرفر من مجلد `deploy/web-preview` المنشور؛ المطوّرين عادةً مش محتاجين ده. ترويسة السكريبت في `deploy/web-preview/deploy.sh`. أهم الأوامر: `status` و`smoke` و`backup` و`rollback-plan` و`prune-qa`، و`./migrate-mfa.sh status` قبل أي حالة MFA (شوف [13-keycloak-mfa-migration.md](../../operations/13-keycloak-mfa-migration.md)). تجهيز الـ Fixtures: `owner-e2e-provision.sh` عن طريق ملف الطلب ([ci-cd.md](ci-cd-ar.md)). الـ Logs: Grafana (عن طريق Tunnel بس) و`docker compose logs` ([troubleshooting.md](troubleshooting-ar.md)).

## التجربة المحلية

السكريبت `scripts/rehearse-preview.sh` (`build|up|smoke|load|persist|rollback|e2e|down`) بيجرّب حزمة بيئة المعاينة على جهاز المطوّر بـ Certificate Authority داخلية. هو مش دليل على السيرفر الحقيقي (ترويسة السكريبت بتقول كده). نتايجه سجّلها الـ Agent بتاع التنفيذ؛ وتشغيلك انت ليه: NOT VERIFIED هنا.

## القيود المعروفة وفجوات التوثيق

- الملفين `docs/operations/06-web-preview.md` و`deploy/web-preview/README.md` لسه بيوصفوا بيئة المعاينة بإنها "prepared, not deployed"، في حين إن الـ Workflow بينشرها مع كل Push على `develop`. صدّق الـ Workflow و`docs/quality/owner-acceptance/run-2026-10-19.md` للحالة الحالية؛ وتحقق بـ `deploy.sh status`.
