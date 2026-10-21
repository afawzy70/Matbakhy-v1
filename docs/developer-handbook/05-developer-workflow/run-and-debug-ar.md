# التشغيل والـ Debug

> **Owner role:** Testing and QA
> **Status:** REFERENCE — إزاي تشغّل كل جزء، وتنادي على الـ API، وتقرا الـ Logs، وتفحص الـ Database وKeycloak. الأوامر اتقرت من السكريبتات والـ Workflows؛ ماتنفّذتش في البيئة اللي اتكتبت فيها الصفحة دي (مفيهاش Docker ولا Java ولا pnpm)، فاعتبرها NOT VERIFIED لحد ما تشغّلها انت.
> **Language / اللغة:** [English](run-and-debug.md) · العربية

جهّز البيئة الأول: [local-environment.md](local-environment-ar.md). الاختصارات المستخدمة تحت:

```bash
DC="docker compose --env-file infra/.env -f infra/compose.yaml"
set -a; . infra/.env; set +a     # loads ports, POSTGRES_*, DEV_USER_PASSWORD into your shell only
```

## إيه بيشتغل فين

| الجزء | إزاي تبدأه | إزاي تتأكد إنه شغّال |
|---|---|---|
| الـ Stack المحلي كله | `scripts/dev.sh up` | `scripts/dev.sh smoke` |
| الـ API | جزء من الـ Stack (خدمة `api`) | `curl -fsS http://127.0.0.1:8080/actuator/health` بيرجّع `"status":"UP"` |
| ويب العميل | `pnpm --filter @matbakhy/customer-web dev` | http://localhost:5173 |
| ويب العمليات (مساحة عمل الطبّاخ + الـ Back Office) | `pnpm --filter @matbakhy/operations-web dev` | http://localhost:5174 |
| الموبايل | `pnpm --filter @matbakhy/customer-mobile start` | مخرجات Expo dev server |
| PostgreSQL | خدمة `postgres` | `$DC ps` بيعرض `healthy` |
| Keycloak | خدمة `identity` | `curl -fsS http://localhost:8180/realms/matbakhy/.well-known/openid-configuration` |

## النداء على الـ API

العقد هو `contracts/openapi.yaml` (قايمة العمليات في `contracts/endpoints.json`)؛ والـ Client المتولّد بـ TypeScript موجود في `packages/api-client`. شوف [api-contracts.md](../04-codebase/api-contracts-ar.md).

- الـ Endpoints العامة مش محتاجة Session، مثلًا `GET /api/v1/configuration/public` و`GET /api/v1/meals?limit=5` (الـ Smoke Test بتاع بيئة المعاينة بينادي على الاتنين).
- أي حاجة خاصة محتاجة الـ Session Cookie من تسجيل الدخول OIDC على الويب، ولو الطلب بيغيّر حالة محتاجة كمان CSRF Header: الأمر `scripts/dev.sh smoke` بيوريك التسلسل الشغّال بـ curl (cookie jar، وCookie `XSRF-TOKEN` منسوخة في `X-XSRF-TOKEN`). اقرا دالة `smoke` في `scripts/dev.sh` وأعد استخدامها بدل ما تخمّن.
- النداء المجهول على مسار خاص لازم يرجّع 401 (`/api/v1/me`)؛ والـ Smoke Test بيتحقق من ده.
- محتوى رسائل الخطأ فيه `{code, messageKey, fieldErrors, requestId, retryable}`؛ وكل Response فيه `X-Request-Id`. اذكر الـ id ده لما تدوّر على السبب في الـ Logs ([toolchain.md](../../engineering/toolchain.md)، القسم الخاص بـ MB-006).
- الـ Endpoints اللي بتغيّر حاجة وبتعمل أوردرات أو حجوزات بتاخد `Idempotency-Key`؛ ولو أعدت إرسال نفس المفتاح بيرجّع نتيجة المرة الأولى. ماتعيدش محاولة نداء أوردر فشل بمفتاح جديد قبل ما تعرف إذا كان الأول نجح ولا لأ (النداء `GET /api/v1/orders/by-request/{id}` بيدوّر عليه للمالك).
- الفلوس أرقام صحيحة بالقرش (piastres) في كل Payload. ماتبعتش ولا تحسب أسعار في الـ Client أبدًا: عرض السعر من السيرفر هو المعتمد ([pricing-and-payments.md](../01-business/pricing-and-payments-ar.md)).

طريقة عن طريق المتصفح تشوف بيها النداءات: افتح أدوات المطوّر في المتصفح على تبويب Network في تطبيق الويب وانسخ `X-Request-Id` بتاع الطلب اللي فشل.

## الـ Logs

```bash
$DC logs --tail 200 api            # API: plain text lines locally, each with the request id in brackets, e.g. "INFO [<request-id>] ..."
$DC logs --tail 100 identity       # Keycloak
$DC logs --tail 100 postgres
$DC logs api | grep -F '[<id>]'     # local default text format (logging.pattern.level in application.yaml)
```

الـ Compose المحلي مابيضبطش `LOGGING_STRUCTURED_FORMAT_CONSOLE`، فـ Logs الـ API المحلية نص عادي. الـ Deployments اللي بتضبطه بس (`deploy/web-preview/compose.yaml` بيضبط `ecs`) هي اللي بتطلّع سطور JSON بصيغة ECS (الحقول `service.environment` و`service.version` و`log.logger` و`message`، والـ request id كحقل MDC اسمه `requestId`)؛ وهناك فلتر بـ `grep '"requestId":"<id>"'` أو في Grafana/Loki.

Logs اختبارات الـ Backend: `backend/**/target/surefire-reports/` (الملفات اللي بيقراها `scripts/check_backend_suite.sh`). Traces ولقطات شاشة اختبارات المتصفح عند الفشل: لما تشغّل عن طريق `scripts/dev.sh e2e` مجلد المخرجات الافتراضي هو `$VERIFY_TMP/results` جوّه مجلد مؤقت السكريبت بيمسحه عند الخروج، فمفيش حاجة بتفضل افتراضيًا. حدّد مكان ثابت ومتجاهَل قبل التشغيل، مثلًا `E2E_OUTPUT_DIR="$PWD/test-results/e2e" scripts/dev.sh e2e` (المجلد `test-results/` متجاهَل من git، `.gitignore`)، وبعدين افتح الـ Trace بـ `pnpm exec playwright show-trace <trace.zip>` من `apps/operations-web`. لو شغّلت Playwright مباشرة (من غير `dev.sh`) بيكتب `test-results/` تحت `apps/operations-web`.

على بيئة المعاينة نفس أسلوب الـ request id بيشتغل عن طريق Grafana/Loki؛ شوف [troubleshooting.md](troubleshooting-ar.md) و`docs/operations/08-troubleshooting-runbook.md`.

## الـ Debug على الـ Database

```bash
$DC exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"
\dt                                   -- tables
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;
```

- الـ Migrations ملفات بتطبّق تغييرات لقدّام بس `backend/app/src/main/resources/db/migration/V<n>__*.sql`. ماتعدّلش Migration اتطبّقت أبدًا؛ ضيف واحدة جديدة. الـ Migrations اللي بعد الـ Baseline لازم تكون Expand-only أو تحتوي على تعليق `-- rollback-compat:`، وده بيفرضه `scripts/check_migration_compat.py` ([ci-cd.md](ci-cd-ar.md)).
- صفوف الأوردر Snapshots غير قابلة للتعديل و`order_event` للإضافة بس؛ وصفوف الـ Audit مينفعش تتعدّل ولا تتمسح (Triggers في الـ Database، حسب `docs/operations/08-troubleshooting-runbook.md`). ماتصلّحش البيانات بتعديل الجداول دي؛ دوّر على السبب في الكود.
- السكريبتات `scripts/db-backup.sh` و`scripts/db-restore.sh` بتاخد نسخة احتياطية منطقية وبتتحقق منها؛ ولازم مجلد المخرجات يكون برّه أي git working tree لأن الـ Dumps فيها بيانات شخصية.
- خلّي التحقيق للقراءة بس: استخدم `SELECT` وانت بتحقق. لو لازم تغيّر بيانات محلية عشان تعمل Reproduce، اعمل ده على Stack مؤقت (على طريقة `scripts/dev.sh verify`) أو بعد `scripts/dev.sh reset`.

## الـ Debug على Keycloak

- لوحة الإدارة المحلية: `http://localhost:8180` بمستخدم الأدمن من ملف الـ env بتاعك.
- ملف الـ Realm: `infra/identity/matbakhy-realm.json` (قالب، والأسرار بتتملى بواسطة `render_realm` في `scripts/dev.sh`). الـ Clients اللي ظاهرة في السكريبتات: `matbakhy-web` (Client سري للويب)، و`matbakhy-mobile` (عام، PKCE مطلوب، الـ redirect `matbakhy-dev://auth/callback`)، و`matbakhy-admin` (Service Account لإعادة ضبط MFA مع الـ Audit).
- الأعراض المعتادة: لو في Redirect Loops أو "invalid redirect" يبقى التطبيقات اتفتحت على Host أو بورت مختلف عن اللي بيتوقعه `WEB_POST_LOGIN_REDIRECT` و`KC_HOSTNAME`؛ ولو تسجيل الدخول نجح لكن `/api/v1/me` رجّع 401 يبقى الـ Session Cookie ماتبعتتش (Origin غلط) أو الـ Issuer مختلف بين المتصفح والـ API (`OIDC_ISSUER` مقابل `OIDC_BACKCHANNEL_ISSUER` في `infra/compose.yaml`).
- قيم سياسة MFA وهي OPTIONAL / REQUIRED / DISABLED لكل فئة مستخدمين متوصفة في [authentication-and-mfa.md](../02-user-journeys/authentication-and-mfa-ar.md) و`docs/security/mfa-policy.md`.

## الـ Debug على الكود

- الـ Backend: الاختبارات هي أسرع دورة. شغّل Class واحد (`(cd backend && ./mvnw -B -ntp verify -Dtest=<ClassName> -Dsurefire.failIfNoSpecifiedTests=false)`؛ الـ Subshell بيخلّي الـ Shell بتاعك في جذر الـ Repository). اختبارات التكامل بتستخدم الـ Annotation `IntegrationTest` (`backend/app/src/test/java/com/matbakhy/app/support/IntegrationTest.java`) مع حاويات PostgreSQL وS3-emulator حقيقية، فلازم Docker يكون شغّال. توصيل IDE Debugger بالـ API اللي شغّال في حاوية: NOT VERIFIED (مفيش بورت Debug منشور في `infra/compose.yaml`)؛ اعمل Debug عن طريق اختبار بدل كده.
- الويب: `pnpm --filter @matbakhy/customer-web test` (Vitest، jsdom) وأدوات المطوّر في المتصفح؛ والـ Source Maps شغّالة في Vite dev server.
- الموبايل: اختبارات Jest في `apps/customer-mobile/src/*.test.ts(x)`؛ والسلوك الـ Native عن طريق السكريبتات في `apps/customer-mobile/scripts/` بس.

## أنماط الفشل الشائعة

شوف [troubleshooting.md](troubleshooting-ar.md).

## نظافة الـ Repository

الأمر `python3 scripts/branch_audit.py --gh --local` بيطبع تصنيف للـ Branches للتقرير بس (مابيمسحش حاجة أبدًا). سياسة التنضيف وقواعدها موجودة في [صيانة الـ Branches](branch-maintenance-ar.md).
