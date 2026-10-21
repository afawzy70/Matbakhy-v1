# البيئة المحلية

> **Owner role:** Infrastructure and deployment
> **Status:** PARTIALLY IMPLEMENTED — الـ Local Stack المبني على Docker وخوادم Vite للتطوير موجودين (IMPLEMENTED) والـ CI بيجرّبهم على Linux؛ وإجراءات Windows/macOS وتشغيل الـ API برّه Docker هي NOT VERIFIED.
> **Language / اللغة:** [English](local-environment.md) · العربية

سطّب الأدوات الأول ([prerequisites.md](prerequisites-ar.md)). كل حاجة هنا بتستخدم بيانات تجريبية غير حقيقية (Synthetic) وSecrets محلية بس.

## اللي بيوفّره `scripts/dev.sh`

`scripts/dev.sh` (bash) بيسهّل تشغيل وإدارة الخدمات المتعرّفة في `infra/compose.yaml`. الخدمات، من ملف الـ Compose:

| الخدمة | الصورة (مثبّتة بالـ Digest) | بورت الجهاز (الافتراضي، على 127.0.0.1 بس) | الغرض |
|---|---|---|---|
| `postgres` | `postgres:16.15-alpine` | 5432 | قاعدة بيانات التطبيق، وFlyway Migrations بتشتغل مع تشغيل الـ API |
| `identity` | `keycloak/keycloak:26.8.0` في وضع `start-dev` | 8180 | مزود OIDC محلي، والـ Realm بيتعمل له Import من `infra/identity/matbakhy-realm.json` |
| `object-storage` | `adobe/s3mock:5.2.3` | 9090 | محاكي S3 فيه Buckets اسمها `matbakhy-private` و`matbakhy-public` |
| `mail` | `axllent/mailpit:v1.31.5` | 8025 (الواجهة)، 1025 (SMTP) | خدمة بتستقبل رسائل البريد التجريبية محليًا؛ مفيش حاجة بتخرج من الجهاز |
| `api` | متبني من `backend/Dockerfile` | 8080 | الـ API بتاع Spring Boot، `APP_ENV=LOCAL`، البروفايل `dev` |

الأوامر (كلها من جذر الـ Repository):

| الأمر | التأثير |
|---|---|
| `scripts/dev.sh doctor` | بيتحقق من المتطلبات المسبقة؛ مابيشغّلش حاجة |
| `scripts/dev.sh up` | `doctor`، وبينشئ ملف الـ env لو ناقص، وبيبني الـ API jar (`./mvnw -DskipTests package`)، وبيرندر الـ Realm، و`docker compose up --build --detach --wait` |
| `scripts/dev.sh smoke` | الصحة، وFlyway baseline، ومناطق التطوير، وOIDC discovery، وS3 buckets، والتقاط البريد، وإنفاذ PKCE، واستجابة 401 للمستخدم المجهول، وتسجيل دخول OIDC من الويب بمستخدم تجريبي، واختبار دورة رفع ملف خاص باستخدام رابط موقّع |
| `scripts/dev.sh down` | بيوقف الـ Containers وبيحتفظ بـ Volume البيانات |
| `scripts/dev.sh reset` | بيوقف ويمسح Volume الـ Stack ده (وبيرفض ملفات env مش معلّمة إنها محلية) |
| `scripts/dev.sh verify` | `doctor` + `up` + `smoke` في مشروع Compose مؤقت على بورتات بديلة (15432، 18180، 18080 ...)، وبيتشال عند الخروج |
| `scripts/dev.sh e2e` | Stack مؤقت، وتطبيقي الويب الاتنين، وبعدها Workflows المتصفح بـ Playwright؛ شوف [test-execution.md](test-execution-ar.md) |

أول تشغيل:

```bash
scripts/dev.sh up
scripts/dev.sh smoke      # prints PASS only if every check passed
```

`up` بينشئ ملف الـ env المحلي (infra/.env، متجاهل من Git) من `infra/.env.example` بكلمات سر عشوائية (صلاحية 600). الملف متجاهل من Git (`.gitignore` بيتجاهل `.env` و`.env.*`) ولازم يحمل `MATBAKHY_ENV_MARKER=local`؛ والسكريبت بيرفض أي ملف تاني. الـ Realm المترندر بيروح على infra/.generated/ (متجاهل من Git). عشان تفصل أكتر من Stack عن بعض اظبط `MATBAKHY_ENV_FILE` و`COMPOSE_PROJECT_NAME`.

## المستخدمين الوهميين

الـ Realm المحلي (`infra/identity/matbakhy-realm.json`) بيعرّف `customer-a` و`customer-b` و`cook-a` و`ops-a`، بالإضافة لحساب الخدمة `service-account-matbakhy-admin`. كل المستخدمين ليهم نفس كلمة السر، `DEV_USER_PASSWORD` في ملف الـ env المحلي بتاعك. اقراها من ملفك لما تحتاجها؛ وماتلصقهاش في أي مكان. الـ SMS مش متاح محليًا، وتسجيل الـ MFA التفاعلي المحلي NOT VERIFIED (الـ Realm بيعرّف تدفق OTP في `infra/identity/matbakhy-realm.json`)؛ واختبارات المتصفح بتحاكي التحقق من التليفون بـ SQL (شوف `apps/operations-web/e2e/fixtures.ts`).

بيانات التطوير بتيجي من سكريبتات Flyway المتكررة في `backend/app/src/main/resources/db/devdata` (مناطق خدمة اسمها `test-zone-*`، وسياسة تجارية ببيانات تجريبية غير حقيقية). `EnvironmentGuard` (`backend/app/src/main/java/com/matbakhy/app/config/EnvironmentGuard.java`) بيرفض البروفايل `dev` أو مواقع devdata لما `APP_ENV` يكون STAGING أو PRODUCTION؛ وLOCAL وCI وPREVIEW بيتحسبوا بيئات ببيانات تجريبية غير حقيقية.

## الـ Frontends

```bash
corepack enable
pnpm install --frozen-lockfile
VITE_API_BASE_URL=http://localhost:8080 pnpm --filter @matbakhy/customer-web dev      # http://localhost:5173
VITE_API_BASE_URL=http://localhost:8080 pnpm --filter @matbakhy/operations-web dev    # http://localhost:5174
```

الخادمين الاتنين بيستخدموا `strictPort`: لو 5173 أو 5174 مشغول بيفشلوا بدل ما ينتقلوا، لأن الـ API بيسمح بالـ Origins دي بالظبط (القيمة الافتراضية لـ `WEB_ALLOWED_ORIGINS` في `backend/app/src/main/resources/application.yaml`) وملف الـ Compose بيظبط التحويل بعد تسجيل الدخول على `http://localhost:5173/`. `VITE_DEMO_NOTICE` بيتحكم في بانر بيئة الـ Demo (شوف `apps/customer-web/src`). تسجيل الدخول بيستخدم تدفق Session Cookie بتاع الـ API (`/oauth2/authorization/keycloak`)، فافتح التطبيقات على `localhost` مش `127.0.0.1` — لأن الـ OIDC issuer هو `http://localhost:8180/realms/matbakhy`.

## الموبايل (تطبيق العميل)

`apps/customer-mobile` تطبيق Expo SDK 57 / React Native 0.86. الإعدادات بتيجي من متغيرات `EXPO_PUBLIC_*` (`EXPO_PUBLIC_API_URL` و`EXPO_PUBLIC_OIDC_ISSUER` و`EXPO_PUBLIC_ENVIRONMENT` و`EXPO_PUBLIC_OIDC_CLIENT_ID` و`EXPO_PUBLIC_NOTIFICATION_PROMPT`) بقيم افتراضية في `apps/customer-mobile/app.json` وتحقق في `apps/customer-mobile/src/config.ts`.

```bash
pnpm --filter @matbakhy/customer-mobile start     # Expo dev server
(cd apps/customer-mobile && pnpm bundle:native)    # Android + iOS JS bundles, no native toolchain needed
```

التشغيل على Emulator أو Simulator محتاج الـ Native Toolchains وDevelopment Build؛ ومجلدات `android/` و`ios/` بتتولّد بـ `expo prebuild` ومش بتتعمل لها Commit. Expo Go أو حزمة JS أو معاينة ويب عمرها ما بتتحسب تحقق Native (قاعدة المشروع، شوف [toolchain.md](../../engineering/toolchain.md)). خطوات الـ Native المؤتمتة بالسكريبتات موجودة في `apps/customer-mobile/scripts/` وبتشتغل في الـ CI ([ci-cd.md](ci-cd-ar.md)). على الـ Emulator التطبيق بيوصل لـ Stack الجهاز عن طريق الاسم البديل للـ Loopback بتاع الـ Emulator؛ والقيم بالظبط اللي الـ CI بيستخدمها موجودة في `apps/customer-mobile/scripts/auth-roundtrip-android.sh`. جلسة Emulator تفاعلية محلية بإيدك: NOT VERIFIED.

## تشغيل الـ API برّه Docker

`./mvnw spring-boot:run` من `backend/app` محتمل (الـ Spring Boot plugin متظبط في `backend/app/pom.xml`) لكنه NOT VERIFIED هنا. المتطلبات المقروءة من `application.yaml`: `APP_ENV` ملوش قيمة افتراضية ولازم يتظبط (`LOCAL` للتطوير)؛ و`DATABASE_URL` و`DATABASE_USERNAME` و`DATABASE_PASSWORD` افتراضيهم `localhost:5432/matbakhy`؛ و`OIDC_ISSUER` افتراضيه `http://localhost:8180/realms/matbakhy`؛ و`OIDC_WEB_CLIENT_SECRET` والـ Realm لازم يطابقوا اللي `scripts/dev.sh` رندره. أبسط طريقة مدعومة لتطوير الـ Backend وتجربة التعديلات هي `scripts/dev.sh up` وتعيد تشغيله بعد التغييرات (بيعيد بناء الـ jar والصورة).

## الوصول للـ Database وKeycloak (محليًا)

```bash
# psql inside the container (variables come from your env file)
set -a; . infra/.env; set +a
docker compose --env-file infra/.env -f infra/compose.yaml exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"
# migrations applied
docker compose --env-file infra/.env -f infra/compose.yaml exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5"
```

كونسول أدمن Keycloak: `http://localhost:8180`، المستخدم `KEYCLOAK_ADMIN_USER` (الافتراضي `local-admin`)، وكلمة السر `KEYCLOAK_ADMIN_PASSWORD` من ملف الـ env بتاعك. واجهة مصرف البريد: `http://localhost:8025`. تفاصيل أكتر في [run-and-debug.md](run-and-debug-ar.md).

## التنضيف

`scripts/dev.sh down` بيحتفظ بالبيانات؛ و`scripts/dev.sh reset` بيمسح الـ Volume المحلي. الاتنين آمنين للـ Stacks المحلية بس: ماتوجّهش `MATBAKHY_ENV_FILE` لملف مش محلي أبدًا.

## القيود المعروفة وفجوات التوثيق

- [toolchain.md](../../engineering/toolchain.md) بيقول "only Linux has been exercised"؛ وده لسه صحيح لكل إجراء في الصفحة دي.
- مفيش إجراء موثّق لتوصيل تليفون حقيقي بـ Stack شغّال على لاب توب.
