# المتطلبات المسبقة

> **Owner role:** Infrastructure and deployment
> **Status:** REFERENCE — الحاجات اللي لازم المطوّر يسطّبها قبل ما يبني Matbakhy أو يشغّله، مع الإصدارات اللي الـ Repository مثبّتها.
> **Language / اللغة:** [English](prerequisites.md) · العربية

الإصدارات اللي تحت مأخوذة من `package.json` و`.node-version` و`backend/pom.xml` و`backend/.mvn/wrapper/maven-wrapper.properties` و`infra/compose.yaml` و`.github/workflows/ci.yml`. الشرح الأطول للأسباب موجود في [toolchain.md](../../engineering/toolchain.md)؛ ولو الصفحة دي والملفات اختلفوا، صدّق الملفات (لأنها اللي بيقرأها الـ CI).

## الأدوات المطلوبة

| الأداة | الإصدار | متثبّتة فين | بتتستخدم في |
|---|---|---|---|
| Git | أي إصدار حديث | غير منطبق | الـ Clone والـ Branches والـ PRs |
| GitHub CLI `gh` | أي إصدار حديث | غير منطبق | عرض الـ Issues وفتح الـ PRs وقراءة تشغيلات الـ CI (`gh run list`) |
| JDK | 21 (LTS) | `backend/pom.xml` (compiler release والـ enforcer) | بناء واختبارات الـ Backend |
| Maven | 3.9.11 | `backend/.mvn/wrapper/maven-wrapper.properties` | شغّل دايمًا `./mvnw`، وماتستخدمش `mvn` بتاع السيستم أبدًا |
| Node.js | 24.21.0 بالظبط | `.node-version`؛ و`engines.node` في `package.json` هو `>=24.21.0 <25` | تطبيقات الويب والموبايل والـ Packages المشتركة |
| pnpm | 10.34.6 | `packageManager` و`engines.pnpm` في `package.json` | بيتفعّل بـ `corepack enable` |
| Docker Engine أو Docker Desktop مع Compose v2 | حديث | `infra/compose.yaml` | الـ Stack المحلي واختبارات التكامل بـ Testcontainers وفحص الأسرار |
| curl وopenssl | أي إصدار | بيستخدمهم `scripts/dev.sh` | الـ Smoke Test وكلمات سر محلية عشوائية |
| Python 3 | 3.x | `scripts/*.py` | أدوات التحقق من التخطيط وفحص DOC-001 وأدوات كتالوج الـ E2E بتاع المالك |
| Chromium / Google Chrome | أي إصدار حديث | `CHROMIUM_PATH` و`E2E_CHROMIUM` | فحوصات التصميم وPlaywright (Playwright 1.56.1 ممكن كمان ينزّل متصفح خاص بيه؛ NOT VERIFIED بدون إنترنت) |

الأمر `scripts/dev.sh doctor` بيفحص Docker (الـ CLI والـ Daemon وcompose v2) وJDK 21 وإصدار Node بالظبط من `.node-version` وpnpm وcurl وopenssl، وبيطبع رسالة واضحة تقولك تعمل إيه لكل حاجة ناقصة.

نتيجة مهمة: الملف `.npmrc` بيضبط `engine-strict=true` (و`save-exact=true`)، فـ pnpm بيرفض يسطّب على Node برّه `engines.node`؛ وكمان `scripts/check_workspace.sh` بيفشل إلا لو `node --version` بيساوي `.node-version` و`pnpm --version` بيساوي إصدار `packageManager`. إصدار Node رئيسي مختلف (مثلًا 22) مش مدعوم للـ Workspace كله، حتى لو شوية أوامر بدت شغّالة.

## أدوات اختيارية (للمهمة المناسبة بس)

| الأداة | امتى | ملاحظات |
|---|---|---|
| Android Studio / Android SDK، وJDK 17 لـ Gradle | بناء Android الـ Native والـ Emulator | الـ CI بيستخدم JDK 17 لـ Android Gradle Plugin وصور API level 35 (`apps/customer-mobile/scripts/launch-android.sh`) |
| macOS مع Xcode، CocoaPods | بناء iOS الـ Native | الـ CI بيستخدم `macos-26`؛ وXcode 16.4 بيفشل مع Expo SDK 57 حسب [toolchain.md](../../engineering/toolchain.md) |
| عميل `psql` | فحص الـ Database مباشرة من الجهاز | بورت الـ Database منشور على `127.0.0.1` بس |
| Gitleaks | مش محتاجه على الجهاز | `scripts/check_secrets.sh` بيشغّل صورة Gitleaks مثبّتة بالـ Digest عن طريق Docker |

## أنظمة التشغيل

| نظام التشغيل | الحالة |
|---|---|
| Linux (الـ CI بيستخدم Ubuntu 24.04) | IMPLEMENTED: كل الأوامر في الدليل ده مكتوبة لـ bash على Linux |
| macOS | NOT VERIFIED لأوامر JavaScript والـ Backend؛ وشغل iOS الـ Native في `.github/workflows/native-build.yml` هو الدليل الوحيد على macOS |
| Windows | NOT VERIFIED. السكريبتات `scripts/dev.sh` و`scripts/check_workspace.sh` وباقي سكريبتات `.sh` محتاجة bash: استخدم WSL2 مع تكامل WSL بتاع Docker Desktop وخلّي الـ Clone جوّه نظام ملفات WSL. [toolchain.md](../../engineering/toolchain.md) فيه المقابلات بتاعت PowerShell للتسطيب والـ Typecheck والاختبار والبناء و`mvnw.cmd`، ومعلّمة NOT_RUN هناك |

الملف `docs/status/latest.md` كمان بيسجّل "Windows/macOS developer commands" على إنها NOT_RUN. لو شغّلتها، بلّغ بالنتيجة على إنها إصلاح لعيب في التوثيق (شوف [documentation-policy.md](documentation-policy-ar.md)).

## الحسابات والوصول

- حساب GitHub عنده وصول للـ Repository (هو عام، لكن الـ Push محتاج صلاحيات Collaborator).
- الوصول لبيئة المعاينة بيتم عن طريق قايمة IP مسموحة والمالك هو اللي بيديه؛ شوف [preview-environment.md](preview-environment-ar.md). مش محتاج وصول SSH للسيرفر عشان تطوّر.
- ماتطلبش Credentials ولا تخزّنها ولا تعمل لها Commit أبدًا. كلمات السر المحلية بيولّدها `scripts/dev.sh` عشوائيًا في ملف متجاهَل من git.

## الفحوصات الأولى

```bash
git clone <repository-url> matbakhy && cd matbakhy
node --version        # must print v24.21.0
corepack enable && pnpm --version   # must print 10.34.6
java -version         # must report 21
docker compose version
python3 scripts/validate_blueprint.py   # planning integrity only, not product verification
scripts/dev.sh doctor
```

الخطوة الجاية: [local-environment.md](local-environment-ar.md).
