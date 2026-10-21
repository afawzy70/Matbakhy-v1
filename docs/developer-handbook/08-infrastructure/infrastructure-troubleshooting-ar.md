# حل مشاكل البنية التحتية

> **Owner role:** Infrastructure and deployment
> **Status:** REFERENCE — 23 سيناريو فشل مرقّمين للـ Contabo Preview، مبنيين على كود الـ Repository وعلى الملاحظات الحيّة بتاريخ 2026-10-21؛ ولا إجراء تصحيحي منهم اتجرّب على السيرفر الحي.
> **Language / اللغة:** [English](infrastructure-troubleshooting.md) · العربية

الصفحة دي هي النظير الخاص بالبنية التحتية لـ [صفحة حل المشاكل](../05-developer-workflow/troubleshooting-ar.md) الخاصة بالمطوّر، واللي بتغطي مشاكل الـ Toolchain والاختبارات والبيئة المحلية، ومش بنكرّرها هنا. تشخيص الأوردرات والـ Audit والـ Dead-letter والـ Telemetry موجود في [دليل حل المشاكل التشغيلي](../../operations/08-troubleshooting-runbook.md)، والـ Runbooks الأصلية في [الـ Runbooks التشغيلية](../../operations/02-runbooks.md). صفحات ليها علاقة: [جرد السيرفر](contabo-server-inventory-ar.md)، [التحقق للقراءة فقط من الأدمن](admin-readonly-verification-ar.md)، [معمارية التشغيل](runtime-architecture-ar.md)، [مسار الـ Deployment](deployment-pipeline-ar.md)، [الـ DNS والـ HTTPS](dns-https-sslip-io-ar.md)، [النسخ الاحتياطي والاستعادة والتعافي من الكوارث](backup-restore-disaster-recovery-ar.md)، [دليل إعادة البناء](server-rebuild-guide-ar.md) و[الوصول للـ Preview الحي](../05-developer-workflow/live-preview-access-ar.md).

## دليل الأوسمة

| الوسم | المعنى |
|---|---|
| VERIFIED LIVE | اتلاحظ بتاريخ 2026-10-21 بواسطة مستخدم لينكس العادي aiagent (من غير صلاحيات مرتفعة) على الـ Contabo VPS نفسه (قراءة فقط، من غير sudo، ومن غير Docker socket). الحقائق الحيّة المسجّلة بس هي اللي بتتحسب. |
| VERIFIED IN CODE | اتقرا في الـ Repository ده (الملف مذكور جنب الادّعاء). |
| DOCUMENTED ONLY | مستند في الـ Repository أو دليل المورّد بيقول كده؛ ومتأكدناش منه هنا ولا على السيرفر الحي. |
| OUTDATED / INCORRECT | بيتعارض مع دليل أحدث، زي نص من أيام Hetzner. |
| NOT VERIFIED | محدّش أكّده. النص بيقول إزاي أدمن مصرّح له يقدر يتأكد منه. |

## إزاي تستخدم الصفحة دي

1. ابدأ بقائمة الأعراض في فهرس السيناريوهات، افتح السيناريو، واقرا أجزاءه السبعة بالترتيب: الأعراض، الأسباب المحتملة، أوامر التشخيص الآمنة، دليل التشخيص المتوقع، الإجراء التصحيحي، مستوى الخطورة، التحقق من التعافي.
2. شغّل أوامر التشخيص بس (أكواد D في القسم اللي بعده). دي للقراءة فقط. أكواد التصحيح (أكواد F) بتغيّر الحالة، وكل واحد منها معاه تحذير.
3. الأوامر اللي محتاجة الـ Docker socket أو مجلد الـ Deployment بيشغّلها أدمن مصرّح له. في جرد Contabo، مستخدم لينكس devops هو العضو الوحيد في مجموعة docker وهو مالك مجلد الـ Deployment (VERIFIED LIVE)، لكن الحساب اللي بيتصل بيه الـ CI Pipeline متخزّن في الـ Secret بتاع الـ Repository اسمه STAGING_SSH_USER وهو NOT VERIFIED. عشان كده الصفحة دي بتقول "أدمن مصرّح له" ومش بتحط مسؤولية على اسم حساب.
4. جلسات الـ Agent والمطوّرين (زي المستخدم aiagent مثلًا) ماينفعش تستخدم sudo، وماينفعش تنضم لمجموعة docker، وماينفعش تقرا ملف .env بتاع السيرفر، وماينفعش تشغّل أي أمر F. ممكن تشغّل أوامر D اللي محتاجة وصول للشبكة بس، وتقرا قوائم المجلدات اللي حسابها يقدر يشوفها أصلًا.
5. ماتشغّلش أوامر التصحيح على الـ Preview الحي من غير تصريح من المالك لما السيناريو يقول كده. تجارب إعادة البناء والاستعادة مكانها سيرفر جديد معزول ([دليل إعادة البناء](server-rebuild-guide-ar.md)).

الاختصارات المستخدمة تحت (كلها VERIFIED IN CODE في `deploy/web-preview/deploy.sh` و`deploy/web-preview/compose.yaml`):

| الاختصار | المعنى |
|---|---|
| `<domain>` | دومين الـ Preview، الموثّق كـ 167-86-90-174.sslip.io (عام في الـ Repository). |
| `<deploy-dir>` | مجلد الـ Deployment على السيرفر. الـ CI Pipeline بيكتب في /srv/matbakhy-staging/deploy/web-preview (VERIFIED IN CODE، `.github/workflows/deploy-preview.yml`؛ والمجلد موجود ومملوك لـ devops، VERIFIED LIVE). |
| `<compose>` | `docker compose --env-file .env -f compose.yaml`، بيتشغّل من `<deploy-dir>`. الـ `deploy.sh` بيضيف `-f <COMPOSE_OVERRIDE>` لما ملف .env يضبطه؛ وهل ملف .env الحي بيضبطه ولا لأ NOT VERIFIED. الـ `--env-file` بيوفّر الـ Interpolation للـ Compose بس؛ مابيضبطش متغيّرات في الـ Shell، فالـ Shell التفاعلي ماعندوش BACKUP_DIR ولا باقي قيم .env إلا لو اتقرت زي D22 أو اتعملها Source زي ما `deploy.sh` بيعمل. |
| `<host>` | واحد من app أو ops أو api أو id أو kc-admin أو media (VERIFIED IN CODE، `deploy/web-preview/Caddyfile`). |

## فهرس السيناريوهات

| الرقم | السيناريو | الرقم | السيناريو |
|---|---|---|---|
| [1](#1-الموقع-مش-متاح) | الموقع مش متاح | [13](#13-فشل-سحب-docker-image) | فشل سحب Docker Image |
| [2](#2-الـ-dns-مش-بيتحل) | الـ DNS مش بيتحلّ | [14](#14-فشل-الـ-deployment-في-github-actions) | فشل الـ Deployment في GitHub Actions |
| [3](#3-أخطاء-شهادة-الـ-https) | أخطاء شهادة الـ HTTPS | [15](#15-نسخة-image-غلط) | نسخة Image غلط |
| [4](#4-caddy-مش-بيوجه) | Caddy مش بيوجّه | [16](#16-فشل-migration-لقاعدة-البيانات) | فشل Migration لقاعدة البيانات |
| [5](#5-http-403) | HTTP 403 | [17](#17-أخطاء-صلاحيات-الـ-persistent-volume) | أخطاء صلاحيات الـ Persistent Volume |
| [6](#6-http-404) | HTTP 404 | [18](#18-القرص-مليان) | القرص مليان |
| [7](#7-http-502-و-503) | HTTP 502 و 503 | [19](#19-ضغط-الذاكرة) | ضغط الذاكرة |
| [8](#8-حاوية-الـ-backend-مش-سليمة-unhealthy) | حاوية الـ Backend مش سليمة (Unhealthy) | [20](#20-فشل-تجديد-الشهادة) | فشل تجديد الشهادة |
| [9](#9-postgresql-مش-متاح) | PostgreSQL مش متاح | [21](#21-روابط-الميديا-مكسورة) | روابط الميديا مكسورة |
| [10](#10-فشل-redirect-تسجيل-الدخول-في-keycloak) | فشل Redirect تسجيل الدخول في Keycloak | [22](#22-فشل-الـ-smoke-tests) | فشل الـ Smoke Tests |
| [11](#11-عدم-تطابق-الـ-issuer-في-oidc) | عدم تطابق الـ Issuer في OIDC | [23](#23-deployment-فاشل-محتاج-rollback) | Deployment فاشل محتاج Rollback |
| [12](#12-مشاكل-mfa-لأدمن-keycloak) | مشاكل MFA لأدمن Keycloak | | |

وصف المطلوب للصفحة دي بيسمّي المجموعة 22 سيناريو بس بيسرد 23 عنوان؛ كل الـ 23 اتحفظوا واترقّموا.

## كتالوج أوامر التشخيص (قراءة فقط)

كل أمر هنا بيقرا بس. عمود "مستخدم لينكس" بيقول مين يقدر يشغّله؛ و"أدمن مصرّح له" يعني شخص المالك صرّح له يستخدم الـ Docker socket ومجلد الـ Deployment على الـ VPS. الأوامر في صفوف "أدمن مصرّح له" هي NOT VERIFIED LIVE (محدّش من غير الوصول ده شغّلها على الجهاز ده)؛ وهي مأخوذة من سكريبتات الـ Repository أو هي أوامر Docker ولينكس قياسية.

| الكود | الأمر | الجهاز | مستخدم لينكس | المتطلب المسبق | النتيجة المتوقعة | الفشل المحتمل والإجراء |
|---|---|---|---|---|---|---|
| D1 | `curl -sS -o /dev/null -w '%{http_code} %{content_type} %{redirect_url}\n' https://<host>.<domain>/<path>` | جهازك أو أي جهاز عليه curl | أي مستخدم | وصول للشبكة؛ ممكن الـ IP العام بتاعك يفرق (شوف السيناريو 5) | كود الحالة ونوع المحتوى ووجهة الـ Redirect للمسار ده؛ شوف الجدول في [الوصول للـ Preview الحي](../05-developer-workflow/live-preview-access-ar.md) | Exit code 6 معناه إن الاسم ماتحلّش (السيناريو 2)؛ و 7 معناه إن الاتصال اترفض؛ و 28 معناه Timeout؛ و 35 أو 60 معناه فشل TLS (السيناريو 3) |
| D2 | `curl -sSI https://<host>.<domain>/<path>` | جهازك | أي مستخدم | زي D1 | هيدرز الرد: Strict-Transport-Security وX-Content-Type-Options وX-Robots-Tag جايين من بلوك الهيدرز في الـ Caddyfile (VERIFIED IN CODE) | غياب الهيدرز عَرَض لمشكلة في الإعدادات أو التوجيه، مش دليل إن سيرفر تاني هو اللي ردّ: الـ Caddyfile بيعرّف الهيدرز جوّه مسارات كل Host لوحده، فأي Host أو مسار بيعدّي على مسار تاني ممكن يطلع من غيرها. أكّد بالشهادة (D3 وD1) وعنوان الوجهة والـ Image الشغّال (D14) ولوجات الـ Proxy (D9) |
| D3 | `getent hosts <host>.<domain>` | جهازك | أي مستخدم | Resolver | السطر بيبدأ بـ 167.86.90.174 (VERIFIED LIVE لأسماء الـ Preview) | مفيش مخرجات معناه مفيش رد للاسم ده؛ شوف السيناريو 2 |
| D4 | الفحص: `openssl s_client -connect <host>.<domain>:443 -servername <host>.<domain> </dev/null 2>/dev/null \| openssl x509 -noout -issuer -subject -dates -ext subjectAltName`. التحقق: `curl -sS -o /dev/null -w '%{http_code} verify=%{ssl_verify_result}\n' https://<host>.<domain>/` | جهازك | أي مستخدم | OpenSSL 1.1.1 أو أحدث وcurl متسطّبين | أمر الفحص بيطبع الشهادة اللي السيرفر بيقدّمها بس، ومابيتحقّقش منها. اقرا سطر subjectAltName للتأكد إن اسم المضيف متغطّي، وشوف الـ Issuer وتاريخ not-after في المستقبل. أمر التحقق بيسيب التحقق من TLS شغّال: بيخرج بـ 0 وبيطبع `verify=0` بس لما سلسلة الشهادات واسم المضيف يتحقّقوا (كود HTTP ممكن يبقى 403 كرد سياسة وصول وده عادي)؛ الخروج بـ 60 أو قيمة verify مش صفر معناه فشل في الشهادة. الـ Issuer هو Let's Encrypt بصلاحية حوالي 90 يوم اتلاحظ لمضيف app بس (VERIFIED LIVE: الـ Issuer CN هو YE1، من 2026-10-18 لحد 2027-01-16)؛ أما Issuer وتواريخ المضيفين التانيين فهي NOT VERIFIED LIVE، وصفحة الـ DNS موثّقة إن ممكن تتستخدم جهة إصدار بديلة، فاقرا الـ Issuer بدل ما تفترضه | نتيجة فحص فاضية معناها إن الـ TLS Handshake فشل؛ ممكن الـ Proxy لسه معندوش شهادة للاسم ده. ماتستبدلش أمر التحقق بـ `curl -k` |
| D5 | `sh deploy/web-preview/smoke.sh <domain>` (ضيف `SMOKE_OPS=skip` قبله لما تشغّله برّه فئة الـ Staff) | جهازك أو السيرفر | أي مستخدم (على السيرفر: أي حد يقدر يوصل للروابط العامة) | نسخة Checkout من الـ Repository؛ وcurl | بينتهي بـ `PASS: web preview smoke test for <domain>`؛ السكريبت بيقول إنه مابيعملش حساب ولا أوردر ولا Upload (VERIFIED IN CODE) | سطر بيبدأ بـ `FAIL:` بيحدد الفحص اللي باظ؛ شوف السيناريو 22 |
| D6 | `ssh <admin-user>@<vps-address>` وبعدها `cd <deploy-dir>` | من جهازك للـ VPS | أدمن مصرّح له | مفتاح SSH مصرّح بيه للحساب ده (الـ Directive المقروء PasswordAuthentication no هو VERIFIED LIVE، لكن إعداد sshd الفعّال NOT VERIFIED LIVE لأن ملف Drop-in مش مقروء؛ والأدمن بيأكّد إن الدخول بالمفتاح بس بـ `sshd -T` بحساب root) | Shell داخل مجلد الـ Deployment | خطأ صلاحيات أو مفتاح معناه إنك مش مصرّح لك؛ وقّف واسأل المالك. ماتحاولش تتجاوز قيود الوصول |
| D7 | `./deploy.sh status` | الـ VPS، داخل `<deploy-dir>` | أدمن مصرّح له (محتاج الـ Docker socket وملف .env) | ملف .env موجود (Mode 600، VERIFIED LIVE؛ والمحتوى ماتقراش) | جدول الحاويات بالحالة والـ Image، ونسخة الـ Schema، وقيم RELEASE_COMMIT وAPI_IMAGE وFRONT_IMAGE المضبوطة، وآخر خمسة صفوف إصدار (VERIFIED IN CODE) | "`.env missing`" معناها إنك في المجلد الغلط؛ وجدول حاويات فاضي معناه إن الـ Stack مش شغّال |
| D8 | `<compose> ps` | الـ VPS، داخل `<deploy-dir>` | أدمن مصرّح له | زي D7 | كل خدمة Up؛ وapi وpostgres وidentity بيظهروا (healthy) | خدمة Restarting أو Exited: اقرا الـ Log بتاعها بـ D9 |
| D9 | `<compose> logs --tail 100 <service>` حيث service واحدة من proxy أو postgres أو identity أو storage أو api أو loki أو alloy أو prometheus أو alertmanager أو alert-sink أو node-exporter أو grafana | الـ VPS، داخل `<deploy-dir>` | أدمن مصرّح له | زي D7 | سطور Log حديثة. Docker بيحتفظ بـ 3 ملفات كحد أقصى بحجم 10 MB لكل حاوية (VERIFIED IN CODE، `x-logging`)، فالسطور القديمة ممكن تكون راحت؛ وLoki محتفظ بتاريخ أطول | الـ Logs ممكن تحمل Request IDs، ماتنسخهاش في مكان عام لو ممكن تحتوي بيانات شخصية |
| D10 | `docker inspect -f '{{.State.Health.Status}}' "$(<compose> ps -q api)"` | الـ VPS، داخل `<deploy-dir>` | أدمن مصرّح له | زي D7 | كلمة healthy (دي نفس العبارة بالظبط اللي `deploy.sh` بيستنّاها، VERIFIED IN CODE) | starting معناها لسه جوّه نافذة الفحص؛ unhealthy: السيناريو 8 |
| D11 | `<compose> exec -T postgres pg_isready -U matbakhy -d matbakhy` | الـ VPS، داخل `<deploy-dir>` | أدمن مصرّح له | حاوية postgres شغّالة | "accepting connections" (نفس فحص الـ Health Check بتاع الحاوية، VERIFIED IN CODE) | "no response" معناها إن PostgreSQL مش جاهز؛ السيناريو 9 |
| D12 | `<compose> exec -T postgres psql -U matbakhy -d matbakhy -At -c "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5"` | الـ VPS، داخل `<deploy-dir>` | أدمن مصرّح له | قاعدة البيانات بتقبل اتصالات | أعلى version وقيمة success فيها t؛ دي قراءة لنفس الجدول اللي `deploy.sh` بيسأل عنه لنسخة الـ Schema (VERIFIED IN CODE) | صف فيه قيمة success فيها f معناه Migration فاشل؛ السيناريو 16 |
| D13 | `<compose> config --images` | الـ VPS، داخل `<deploy-dir>` | أدمن مصرّح له | زي D7 | مراجع الـ Images اللي الـ Stack هيشغّلها، مأخوذة من ملف .env | خطأ "set X in .env" بيحدد المتغيّر الناقص |
| D14 | `docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' <image reference>` | الـ VPS | أدمن مصرّح له | الـ Image موجودة محليًا | الـ Commit SHA الكامل اللي اتبنت منه الـ Image. عملية البناء بتحط الـ Label ده على الـ Docker Images الاتنين بتوعنا (VERIFIED IN CODE، `.github/workflows/deploy-preview.yml` و`.github/workflows/preview-images.yml`)؛ أما الـ Labels على Images الحاويات الشغّالة فهي NOT VERIFIED LIVE | قيمة فاضية معناها Image طرف تالت أو Image مش مبنية بالـ Pipeline ده |
| D15 | `tail -n 5 <backup-dir>/releases.log` | الـ VPS | أدمن مصرّح له (مجلد النسخ الاحتياطية Mode 750 مملوك لـ devops، VERIFIED LIVE) | `<backup-dir>` هو BACKUP_DIR من ملف .env؛ والافتراضي /srv/matbakhy-backups (VERIFIED IN CODE) | صفوف مفصولة بـ Tab: الوقت، الـ Commit، Image الـ API، Image الـ Front، الـ Schema قبل، الـ Schema بعد، مسار النسخة الاحتياطية | مفيش ملف: مفيش إصدار اتسجّل هناك، أو BACKUP_DIR بيشاور على مكان تاني (راجع القيمة، السيناريو 17) |
| D16 | `./deploy.sh rollback-plan [N]` | الـ VPS، داخل `<deploy-dir>` | أدمن مصرّح له | على الأقل N+1 صف في سجل الإصدارات. الترقيم بيعدّ الإصدارات الناجحة المسجّلة بس: N=1 هو الصف اللي قبل الأخير، و`up` فشل قبل ما الـ Stack يبقى سليم مابيبقاش صف (السيناريو 23) | بيطبع الإصدار الهدف، وقيم .env التلاتة، وحكم؛ "prints only; changes nothing" (VERIFIED IN CODE) | "no release log" أو "only N release(s) recorded": مفيش حاجة ترجع لها بالطريقة دي |
| D17 | `<compose> exec -T proxy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile` | الـ VPS، داخل `<deploy-dir>` | أدمن مصرّح له | حاوية الـ Proxy شغّالة؛ والـ Caddyfile متضمّن جوّه Image الـ Front (Baked) في /etc/caddy/Caddyfile (VERIFIED IN CODE، `deploy/web-preview/front.Dockerfile`) | "Valid configuration" | الخطأ بيحدد السطر؛ والإصلاح تغيير في الكود وImage Front جديدة (F9) |
| D18 | `df -h` و`docker system df` | الـ VPS | `df`: أي مستخدم؛ `docker system df`: أدمن مصرّح له | مفيش | القرص /dev/sda1 حجمه 290 GB والمستخدم منه 18 GB (6 في المية) لما اتلاحظ في 2026-10-21 (VERIFIED LIVE؛ أكيد اتغيّر) | الاستخدام فوق 85 في المية: السيناريو 18 |
| D19 | `free -h` و`uptime` | الـ VPS | أي مستخدم | مفيش | حوالي 23 GiB إجمالي وحوالي 3.4 GiB مستخدمة وقت الملاحظة (VERIFIED LIVE) | ذاكرة متاحة قليلة أو Load Average عالي جدًا: السيناريو 19 |
| D20 | `docker stats --no-stream` و`docker inspect -f '{{.State.OOMKilled}} {{.RestartCount}}' <container>` | الـ VPS | أدمن مصرّح له | الحاويات موجودة | استخدام الذاكرة أقل من حد كل خدمة؛ وOOMKilled false | OOMKilled true معناها بس إن عملية جوّه الحاوية دي اتقتلت بسبب الذاكرة. مش بتقول إذا كان السبب حدّ الحاوية نفسها ولا استنفاد ذاكرة الجهاز كله؛ اربطها بدليل من الـ Kernel أو الـ cgroup قبل ما تسمّي السبب (السيناريو 19) |
| D21 | `ls -ld <path>` و`stat -c '%U:%G %a %n' <path>` | الـ VPS | أي مستخدم للمسارات اللي الحساب يقدر يعدّي عليها | المسار موجود | مثلًا /srv/matbakhy-staging هو devops:devops 755 (VERIFIED LIVE) | "Permission denied" على قائمة مجلد هو في حد ذاته دليل؛ ماتغيّرهوش، صعّد الموضوع |
| D22 | `grep -E '^(PREVIEW_DOMAIN\|GENERATED_DIR\|BACKUP_DIR\|COMPOSE_OVERRIDE\|CUSTOMER_ALLOWED_IPS\|STAFF_ALLOWED_IPS\|RELEASE_COMMIT)=' .env` | الـ VPS، داخل `<deploy-dir>` | أدمن مصرّح له | ملف Mode 600؛ النمط بيطبع المفاتيح غير السرّية دي بس | القيم الفعّالة للمفاتيح السبعة دي. ماتطبعش الملف كله أبدًا | طباعة الملف كله هتكشف أسرار؛ ماتعملهاش |
| D23 | `gh run list --workflow deploy-preview.yml --limit 5` وبعدها `gh run view <run id> --log-failed` | جهازك | أي مستخدم عنده وصول GitHub للـ Repository | GitHub CLI متسجّل دخول | قائمة التشغيلات. القائمة الافتراضية مابتعرضش الـ Commit؛ عشان تشوفه استخدم `gh run list --workflow deploy-preview.yml --branch develop --limit 5 --json headSha,status,conclusion,createdAt`. وLog الخطوة الفاشلة | اسم الخطوة بيقولك أنهي مرحلة فشلت (السيناريو 14) |
| D24 | `systemctl is-active docker` و`systemctl is-enabled docker` | الـ VPS | أي مستخدم | مفيش | الاتنين active وenabled (خدمة docker شغّالة، VERIFIED LIVE) | inactive: الـ Stack كله واقع (السيناريو 1) |

## كتالوج أوامر التصحيح (بتغيّر الحالة)

كل صف بيغيّر الحالة. خد تصريح المالك قبل ما تشغّل أي واحد منهم على الـ Preview الحي إلا لو الصف قال غير كده، وسجّل مين شغّله وليه، وأعد التحقق من التعافي الخاص بالسيناريو. ماتشغّلش أبدًا أمر هدّام (أي حاجة بتمسح بيانات أو Volumes) من غير نسخة احتياطية متحقق منها وتصريح صريح من المالك.

| الكود | الأمر | الجهاز ومستخدم لينكس | التحذير | المتطلب المسبق | النتيجة المتوقعة | التحقق والفشل المحتمل |
|---|---|---|---|---|---|---|
| F1 | `./deploy.sh up` | الـ VPS، داخل `<deploy-dir>`، أدمن مصرّح له | بياخد نسخة احتياطية متحقق منها لقاعدة البيانات قبل الـ Deployment، ويعمل Render للإعدادات المولّدة، ويسحب الـ Images الناقصة، ويشغّل الـ Stack وممكن يطبّق Migrations جديدة لقاعدة البيانات. بيرفض يشتغل لو تغيير تاني ماسك القفل المشترك (`.preview-mutation.lock` في BACKUP_DIR). ماتشغّلوش "عشان تشوف إيه اللي هيحصل" (VERIFIED IN CODE) | ملف .env مفيهوش Placeholders، ومراجع الـ Images مثبّتة بالـ Digest، وRELEASE_COMMIT كامل من 40 حرف | بينتهي بـ `up: <commit> (schema A -> B)` | D7، وبعدها `./deploy.sh smoke`. رسائل الفشل: "`.env still has placeholders`"، "must be pinned by digest"، "another preview change holds" (استنى أو دوّر على المشغّل التاني)، "API did not become healthy" (السيناريو 8) |
| F2 | `<compose> up -d --force-recreate <service>` (استخدمه بعد أي تغيير في .env أو compose.yaml) أو `<compose> restart <service>` (لما الإعدادات ماتغيّرتش بس) | الـ VPS، داخل `<deploy-dir>`، أدمن مصرّح له | بيقطع الخدمة دي لفترة قصيرة. `restart` بيسيب متغيّرات البيئة القديمة للحاوية زي ما هي، فمابيطبّقش قيمة اتغيّرت في .env ولا تغيير في ملف compose؛ إعادة الإنشاء بس هي اللي بتطبّقه. شغّله بنفس `<compose>` اللي `deploy.sh` بيستخدمه، بما فيه `-f <COMPOSE_OVERRIDE>` لو ملف .env مفعّله (اتأكد بـ D22)، وإلا الحاوية الجديدة هتفقد الـ Override. إعادة تشغيل أو إنشاء postgres بتقطع كل ترافيك الـ API؛ وproxy بتقطع كل ترافيك الويب | السبب مفهوم ومسجّل | الخدمة بترجع Up (healthy لو ليها Health Check) | D8 وD5. لو رجعت وقعت تاني، وقّف واستخدم الخطوات التانية في السيناريو |
| F3 | عدّل قيم الإصدار التلاتة (RELEASE_COMMIT وAPI_IMAGE وFRONT_IMAGE) في ملف .env للقيم اللي طبعها D16، وبعدها F1 | الـ VPS، أدمن مصرّح له | الرجوع لإصدار Docker Images سابق من غير استرجاع قاعدة البيانات (Image-only Rollback) آمن بس لو بنية أو إصدار الـ Database Schema ماتغيّرش أو كل Migration بعد كده هو Expand-only (السيناريو 23). مطلوب تصريح المالك | مخرجات D16 محفوظة؛ والحكم مقروء | الـ Images السابقة شغّالة على الـ Schema الحالية | D7 بيظهر الـ Commit الهدف؛ وD5 بينجح |
| F4 | `./deploy.sh backup` | الـ VPS، أدمن مصرّح له | بيوقّف خدمة الـ Storage لمدة دقيقة تقريبًا أثناء كتابة أرشيف تخزين الأوبجكتس (`storage-*.tgz`)، فالـ Uploads بتقف (VERIFIED IN CODE). وبيستعيد الـ Dump في قاعدة بيانات مؤقتة عشان يثبت صحته | مساحة قرص فاضية (السيناريو 18) | `backup verified` و`backups in <backup-dir>` | ملف أحدث في مجلد النسخ الاحتياطية؛ وتنبيه BackupStale بيختفي |
| F5 | `./deploy.sh admin-console <status\|enable\|disable\|verify\|create-admin NAME\|rollback>` | الـ VPS، أدمن مصرّح له | enable وcreate-admin بيكتبوا بيانات سرّية في ملف خاص تحت المجلد المولّد؛ ماتلصقهاش في أي مكان. disable بيقفل مضيف الـ Console فورًا. enable كمان بيكتب الأول Dump لقاعدة بيانات Keycloak وملف حالة من خلال دالة داخلية في `admin-console.sh`؛ والـ Dispatcher بتاع `admin-console` مالوش أمر فرعي اسمه `backup` (VERIFIED IN CODE) | حاوية identity شغّالة | شوف [كونسول أدمن Keycloak](../../operations/14-keycloak-admin-console.md) | `status` بيسرد كل حساب Master بـ otp أو no-otp |
| F6 | `docker image prune` (الـ Dangling Images بس) | الـ VPS، أدمن مصرّح له | ماتضيفش أبدًا الـ Flags اللي بتمسح الـ Volumes كمان. الأوامر `docker volume prune` و`docker system prune --volumes` ممكن تدمّر بيانات PostgreSQL وSeaweedFS وCaddy والمراقبة وممنوعة هنا | `docker system df` اتقرا الأول | بيطبع المساحة اللي اتحررت | D18 بيظهر مساحة فاضية أكتر؛ وD8 ماتغيّرش |
| F7 | `./deploy.sh prune-qa` (عرض فقط) و`./deploy.sh prune-qa --apply` (بيمسح) | الـ VPS، أدمن مصرّح له | صيغة `--apply` بتمسح ملفات الـ QA الأقدم من QA_ARTIFACT_RETENTION_DAYS. شغّل صيغة العرض الأول دايمًا (VERIFIED IN CODE، نص الاستخدام في `deploy/web-preview/deploy.sh`) | قائمة اتراجعت | الملفات اللي اتمسحت هي اللي ظهرت في القائمة | D18 |
| F8 | `gh run rerun <run id> --failed` أو `gh workflow run deploy-preview.yml --ref develop` | جهازك، مستخدم عنده صلاحية كتابة على الـ Repository | إعادة التشغيل بتعمل Deploy للـ Commit بتاع التشغيلة دي؛ والتشغيل اليدوي بيعمل Deploy لآخر Commit في develop. الـ Workflow بيشغّل Deployment واحد في المرة (Concurrency Group، VERIFIED IN CODE). ماتعيدش Deployment عشان هو بطيء؛ أعد التشغيل بعد ما الفشل يتفهم ويتصلّح بس | الإصلاح اتعمله Merge أو الـ Secret اتصحّح | تشغيلة جديدة بتوصل لـ `DEPLOYED <sha>` | D23 وD5 |
| F9 | افتح Pull Request بيغيّر ملف الـ Repository (الـ Caddyfile أو compose.yaml أو سكريبت أو Workflow)، سيب الـ CI ينجح، اعمل Merge، وسيب الـ Deploy Workflow يشحنه | جهازك | ماتعدّلش الملفات على السيرفر بنفسك أبدًا: الـ Pipeline بيكتب فوق deploy وinfra وscripts من Git في كل Deployment (VERIFIED IN CODE)، فالتعديل اليدوي بيضيع أو بيعمل Drift | تغيير اتراجع ومعاه إقرار Documentation Impact | الـ Image أو الملف الجديد اتعمله Deploy | D17 وD5 |
| F10 | `./deploy.sh render` | الـ VPS، داخل `<deploy-dir>`، أدمن مصرّح له | بيعيد كتابة الإعدادات المولّدة اللي فيها أسرار (استيراد الـ Realm وملف بيانات اعتماد الـ Storage) ويعيد تطبيق الـ Modes بتاعتها. مابيعيدش تشغيل الخدمات (VERIFIED IN CODE). ماتطبعش الملفات المولّدة أبدًا | ملف .env سليم | ملفات تحت المجلد المولّد بالـ Modes المذكورة في السيناريو 17 | D21 على الملفات دي؛ وF2 (صيغة إعادة الإنشاء) للخدمات اللي بتقراها |
| F11 | استعادة من نسخة احتياطية (`./deploy.sh restore-db <dump>` والخطوات في [الـ Deployment والـ Rollback للـ Staging](../../operations/11-staging-deployment-and-rollback.md)، القسم 4.3) | الـ VPS، أدمن مصرّح له | البيانات المكتوبة بعد النسخة الاحتياطية بتضيع. الاستعادة فوق بيانات حيّة محتاجة تصريح صريح من المالك؛ جرّب على سيرفر جديد معزول الأول ([النسخ الاحتياطي والاستعادة والتعافي من الكوارث](backup-restore-disaster-recovery-ar.md)) | Dump متحقق منه وله Checksum موضوع على الـ Host تحت BACKUP_DIR (الـ Container بتاع postgres شايف المجلد ده باسم /backups، فمرّر مسار /backups، مثلًا `/backups/db-<stamp>/matbakhy-<stamp>.dump`)، وجنبه ملف `.dump.sha256`، وللقبول كمان ملف `.manifest` المطابق؛ والـ API واقف | قاعدة البيانات المستعادة بتتطابق في فحص المطابقة (Reconciliation) (الأوردرات والتخصيصات والـ Ledger). `scripts/db-restore.sh` بيرفض الـ Checksum الغلط، لكن من غير الـ Manifest المطابق بيطبع تحذير وبيتخطى مقارنة عدد الصفوف وممكن يطبع RECONCILED برضه؛ ماتقبلش استعادة غير بوجود الـ Manifest وبدون أي سطر MISMATCH (VERIFIED IN CODE) | مخرجات المطابقة؛ وD5 |

## 1. الموقع مش متاح

**الأعراض.** ويب العميل (مضيف app) أو ويب العمليات (مضيف ops) مابيفتحش: Timeout، أو رفض اتصال، أو صفحة خطأ في المتصفح، أو صفحة فاضية.

**الأسباب المحتملة.**
- الـ VPS واقع، أو مسار الشبكة مكسور، أو المزوّد عنده مشكلة (NOT VERIFIED؛ صفحة حالة المزوّد برّه الـ Repository ده).
- خدمة Docker واقفة، فمفيش أي حاوية شغّالة (خدمة docker كانت شغّالة في 2026-10-21، VERIFIED LIVE).
- حاوية الـ Proxy مش شغّالة. الـ Proxy بيبدأ بعد ما الـ API يبلّغ إنه سليم بس (`depends_on` مع `service_healthy`، VERIFIED IN CODE)، فلو الـ API مش سليم الـ Proxy مش بيشتغل عند البدء.
- البورتات 80 و 443 متقفلة. الاتنين كانوا بيسمعوا على كل الواجهات وقت الملاحظة (VERIFIED LIVE)؛ أما حالة الـ Firewall فهي NOT VERIFIED LIVE.
- مشكلة DNS أو شهادة (السيناريوهات 2 و 3).
- الـ Caller برّه الـ Allow-list فبياخد 403، وده مش انقطاع (السيناريو 5).

**أوامر التشخيص الآمنة.** D1 لمضيفَي app وops؛ وD3؛ وD4؛ وبعدها، بواسطة أدمن مصرّح له، D24 وD8 وD9 للـ Proxy والـ API.

**دليل التشخيص المتوقع.** السليم: app وops بيرجّعوا 200 من شبكة مسموح لها (VERIFIED LIVE، من السيرفر نفسه). لو D1 رجّع Exit code 7 أو 28 وD3 بيحلّ الاسم، يبقى المشكلة في الجهاز أو البورتات. لو D8 بيظهر الـ Proxy ناقص والـ API مش سليم، روح للسيناريو 8. لو الـ Proxy Up وD1 رجّع 403 بالنص "Not available"، روح للسيناريو 5.

**الإجراء التصحيحي.** صلّح السبب اللي بيظهره D8 وD9 باستخدام السيناريو المناسب. لو خدمة Docker واقفة، أدمن مصرّح له بيشغّلها بمدير خدمات النظام؛ وخدمات compose عندها `restart: unless-stopped` (VERIFIED IN CODE)، فالمفروض ترجع بعد ما الـ Daemon يبدأ (لسه ماجرّبناهاش). لو انقطاع من المزوّد أو الشبكة، استنى وكلّم المزوّد عن طريق المالك. ماتعيدش إنشاء الـ Stack من الصفر بسبب انقطاع.

**مستوى الخطورة.** متوسط. إعادة تشغيل الخدمات بتقطع الـ Preview لكن مابتلمسش البيانات؛ أما إعادة إنشاء الـ Volumes فهتدمّر البيانات وماينفعش تتعمل.

**التحقق من التعافي.** D5 بينجح، وD1 بيرجّع 200 لـ app وops، و`./deploy.sh status` بيظهر كل خدمة Up.

## 2. الـ DNS مش بيتحلّ

**الأعراض.** المتصفحات بتبلّغ "server not found"؛ وcurl بيخرج بـ 6؛ وD3 مابيطبعش حاجة لاسم Preview واحد أو أكتر.

**الأسباب المحتملة.**
- الـ Resolver بتاع الـ Caller مش قادر يوصل لخدمة sslip.io أو بيفلترها. الأسماء تحت 167-86-90-174.sslip.io بتتحلّ لـ 167.86.90.174 بالتصميم: sslip.io بيرد بعنوان الـ IP المكتوب جوّه الاسم (DOCUMENTED ONLY، شوف [الـ DNS والـ HTTPS](dns-https-sslip-io-ar.md)). كل الأسماء الستة اتحلّت للعنوان ده لما اتلاحظت من السيرفر (VERIFIED LIVE).
- غلطة كتابة في الاسم، مثلًا رقم غلط في جزء الـ IP. هل الأشكال التانية للعنوان المدمج، زي النقط بدل الشرطات، بتتحلّ ولا لأ NOT VERIFIED.
- دومين الـ Preview اتغيّر في الـ Repository أو Image الـ Front بس العنوان اللي وراه ماتغيّرش (استبدال الدومين متوصّف في [الـ DNS والـ HTTPS](dns-https-sslip-io-ar.md)).
- sslip.io نفسه مش متاح (برّه الـ Repository؛ NOT VERIFIED).

**أوامر التشخيص الآمنة.** D3 لكل واحد من app وops وapi وid وkc-admin وmedia؛ وD3 على Resolver تاني باستخدام شبكة مختلفة أو اتصال موبايل.

**دليل التشخيص المتوقع.** كل اسم بيرد بـ 167.86.90.174. الفشل على شبكتك بس معناه إن المشكلة في الـ Resolver بتاعك؛ والفشل في كل مكان معناه إن المشكلة في الاسم أو في sslip.io.

**الإجراء التصحيحي.** صحّح الاسم اللي كتبته. غيّر الـ Resolver أو الشبكة. مفيش حاجة على الـ VPS محتاجة تتغيّر لمشكلة DNS في أسماء sslip.io، فماتعيدش تشغيل الحاويات. لو الـ Preview اتنقل لعنوان تاني، اتبع Runbook استبدال الدومين في [الـ DNS والـ HTTPS](dns-https-sslip-io-ar.md) عن طريق Pull Request. التغيير ده مش في ملف .env بس: أسماء مواقع Caddy، وImage الـ Front (بتتبني بالدومين)، وعناوين الـ Redirect وPost-logout بتاعة الـ Application Client في الـ Realm المحفوظ في Keycloak، كلها شايلة الدومين. استعادة ملف .env والـ Images السابقة بس مابتعكسش إعدادات الـ Client في الـ Realm، فالرجوع عن تغيير دومين محتاج قرار صريح من المالك، ولسه ماجرّبناهوش بتمرين فعلي (NOT VERIFIED). كمان إعادة إنشاء الـ Proxy بـ PREVIEW_DOMAIN جديد بتشيل أسماء المضيفين القديمة فورًا، فالتحويل ليه Downtime للأسماء دي. الـ Repository مافيهوش Routing بدومينين مع بعض، ولسه ماتصمّمش ولا اتجرّب بتمرين فعلي (NOT VERIFIED)؛ وبناء واحد منهم قرار للمالك.

**مستوى الخطورة.** منخفض للتشخيص. استبدال الدومين خطورته عالية لأنه بيخلّي الشهادات وأسماء المضيفين المتظبّطة في Realm الـ Keycloak والـ Build Argument الخاص بـ Image الـ Front غير صالحين.

**التحقق من التعافي.** D3 بيرجّع العنوان المتوقع لكل الأسماء، وبعدها D1 لكل مضيف بيرجّع أكواد الحالة المتوقعة المذكورة في [الوصول للـ Preview الحي](../05-developer-workflow/live-preview-access-ar.md).

## 3. أخطاء شهادة الـ HTTPS

**الأعراض.** المتصفح بيحذّر من شهادة غير صالحة أو منتهية أو غير موثوقة؛ وcurl بيخرج بـ 35 أو 60؛ وسكريبت الـ Smoke بيفشل في أول Request.

**الأسباب المحتملة.**
- اسم مضيف غلط في الرابط، أو اتستخدم عنوان IP بدل الاسم (الشهادة لأسماء sslip.io).
- الشهادة انتهت لأن التجديد فشل (السيناريو 20). شهادة الـ app كانت صالحة من 2026-10-18 لحد 2027-01-16 وقت الملاحظة (VERIFIED LIVE).
- مضيف جديد (مثلًا kc-admin) لسه معندوش شهادة. سكريبت الـ Smoke بيستنى لحد 90 ثانية لمضيف جديد وبيحذّر بس لو لسه مش واصل (VERIFIED IN CODE، `deploy/web-preview/smoke.sh`).
- ساعة الـ Caller غلط.
- سيرفر تاني هو اللي بيرد على العنوان.
- تحدّي (Challenge) ACME مش قادر يكتمل (البورتات أو الـ DNS أو حدود المعدّل، السيناريو 20).

**أوامر التشخيص الآمنة.** D4 للمضيف الفاشل؛ وD3؛ وساعة الـ Caller نفسها؛ وD9 للـ Proxy (أدمن مصرّح له).

**دليل التشخيص المتوقع.** subjectAltName بيغطي المضيف، وتاريخ not-after في المستقبل، وأمر التحقق (D4) بيخرج بـ 0 مع verify=0، وده اللي بيثبت إن الـ Issuer موثوق. شهادة app بس هي اللي اتلاحظت (Let's Encrypt، VERIFIED LIVE)؛ ماتفترضش نفس الـ Issuer أو التواريخ للمضيفين التانيين (NOT VERIFIED LIVE). مخرجات D4 الفاضية، أو subjectAltName مابيغطيش المضيف، أو فحص اسم المضيف في التحقق بيفشل، معناها إن الـ Proxy معندوش شهادة صالحة له؛ ماتحكمش على تغطية اسم المضيف من الـ Subject. سطور Log الـ Proxy عن الحصول على شهادة بتظهر التقدّم أو نص الخطأ (DOCUMENTED ONLY: Caddy بيكتب أخطاء ACME على خرج الأخطاء القياسي، وDocker بيلتقطه).

**الإجراء التصحيحي.** استخدم اسم المضيف الصح. صحّح ساعة الـ Caller. للشهادات الناقصة أو المنتهية اتبع السيناريو 20. ماتعطّلش التحقق من الشهادة أبدًا (curl -k) إلا في التجربة المحلية اللي سكريبت الـ Smoke موثّقها؛ وماتنسخش أي مفتاح خاص (Private Key) من الـ Volume بتاع caddy-data أبدًا.

**مستوى الخطورة.** متوسط؛ الـ Preview مش صالح للاستخدام عبر HTTPS طول فترة المشكلة، بس مفيش حاجة بتضيع.

**التحقق من التعافي.** لكل مضيف، فحص D4 بيظهر subjectAltName بيغطي المضيف وتاريخ مش منتهي، وأمر التحقق في D4 بيخرج بـ 0 مع verify=0، وD5 بينجح.

## 4. Caddy مش بيوجّه

**الأعراض.** مضيف بيرد بس بمحتوى غلط: مضيف الـ API بيرجّع 404 لمسار /api صالح، أو مضيف id بيرجّع تطبيق الويب، أو تغيير في الـ Caddyfile مابيتطبّقش، أو كل المضيفين بيردوا بنفس الصفحة.

**الأسباب المحتملة.**
- Image الـ Front الشغّالة مفيهاش الـ Caddyfile اللي متوقعه. الـ Caddyfile متضمّن جوّه Image الـ Front (Baked) وقت البناء، مش Mounted (VERIFIED IN CODE، `deploy/web-preview/front.Dockerfile`)، فتعديل الملف على السيرفر مابيغيّرش حاجة.
- Image الـ Front من Commit أقدم (السيناريو 15).
- Site Block غلط: مضيف api بيعرض /api/* و/oauth2/* و/login/oauth2/* و/logout بس وبيرد 404 على أي حاجة تانية؛ ومضيف id بيخفي /admin و/realms/master؛ ومضيف kc-admin مقفول إلا لو ملف الـ Flag موجود (VERIFIED IN CODE، `deploy/web-preview/Caddyfile`).
- متغيّرات البيئة اللي الـ Caddyfile بيقراها (PREVIEW_DOMAIN وACME_EMAIL وCUSTOMER_ALLOWED_IPS وSTAFF_ALLOWED_IPS) قيمها مش متوقعة في ملف .env.
- الطلب وصل لاسم مضيف مختلف عن المقصود، مثلًا مسار تحت المضيف الغلط.

**أوامر التشخيص الآمنة.** D1 بالمسار المقصود على كل مضيف؛ وD2؛ وD14 لـ Image الـ Front؛ وD17؛ وD22 (أدمن مصرّح له).

**دليل التشخيص المتوقع.** قارن الرد بجدول التوجيه في [معمارية التشغيل](runtime-architecture-ar.md) والسلوك الحي في [الوصول للـ Preview الحي](../05-developer-workflow/live-preview-access-ar.md). D17 بيطبع "Valid configuration" للملف السليم. الـ Label على Image الـ Front بيظهر أنهي Commit بناها.

**الإجراء التصحيحي.** القاعدة الغلط بتتصلّح في الـ Caddyfile بتاع الـ Repository عن طريق Pull Request (F9). اختبار الـ Repository `scripts/test_proxy_access.py` بيشغّل الـ Caddyfile الحقيقي في حاوية مؤقتة وهو المكان اللي تضيف فيه اختبار Regression. قيمة متغيّر البيئة الغلط بيصلّحها المالك أو أدمن مصرّح له في ملف .env على السيرفر، وبعدها F2 بصيغة إعادة الإنشاء للـ Proxy (الـ restart العادي بيسيب البيئة القديمة). ماتعدّلش الـ Caddyfile على السيرفر.

**مستوى الخطورة.** متوسط؛ تغيير توجيه غلط ممكن يكشف مسار المفروض يكون مقفول. راجع كل تغيير في فئة الوصول مقابل تعليقات رأس الـ Caddyfile.

**التحقق من التعافي.** D1 على المسارات المتأثرة بيدّي الأكواد الموثّقة؛ وD5 بينجح (بيتأكد إن actuator وRealm الـ master والـ metrics مش منشورين).

## 5. HTTP 403

**الأعراض.** مضيف بيرد 403.

**الأسباب المحتملة.** فيه تلات ردود 403 مختلفة، ومش معناهم نفس الحاجة.
- رد سياسة الوصول في Caddy: الـ Body هو النص "Not available". الـ Caddyfile بيرجّعه لأي حد برّه فئة العميل (مضيفي app وapi وid وmedia) أو فئة الـ Staff (مضيف ops، ومساري /api/v1/ops و/api/v1/cook). الفئتين الافتراضي بتاعهم ALLOWED_CLIENT_IPS، وفئة العميل بتبقى عامة فقط لما المالك يضبط CUSTOMER_ALLOWED_IPS (VERIFIED IN CODE، تعليقات رأس الـ Caddyfile). ده قرار وصول، مش انقطاع. النتيجة بتعتمد على عنوان الـ IP العام للي بيطلب، فالحالة اللي بتتشاف من شبكة تانية ممكن تختلف (VERIFIED LIVE: الطلبات اتعملت من عنوان السيرفر نفسه). مسارات API بتاعة المطبخ والـ Staff (/api/v1/ops و/api/v1/cook) على مضيف api بتتفحص مقابل قائمة الـ Staff وبعدها مقابل قائمة العميل كمان، فلازم الـ Tester بتاع الـ Staff يبقى في القائمتين؛ وتسجيل الدخول بيستخدم مضيفي فئة العميل برضه (VERIFIED IN CODE، بلوك api في الـ Caddyfile). عنوان مسموح له بفئة الـ Staff بس ممكن يفتح Operations Web وبرضه تفشل نداءات الـ API وتسجيل الدخول بتاعه.
- جذر مضيف الميديا: https://media.<domain>/ رجّع 403 بنوع محتوى application/xml وهيدر x-amz-request-id، وده بيبيّن إن الرد جاي من بوابة S3 بتاعة SeaweedFS ورا Caddy مش من نص Caddy نفسه (VERIFIED LIVE). التفسير هو إن بوابة S3 بترفض الطلب المجهول لقائمة الـ Buckets على الجذر. نص الخطأ بالظبط ماتخزّنش، فكود خطأ S3 المحدد NOT VERIFIED. الأوبجكتس تحت /matbakhy-public/ هي العامة (VERIFIED IN CODE: الـ Base URL العام للـ API هو https://media.<domain>/matbakhy-public، والـ Bucket العام مقروء بشكل مجهول بإعدادات S3 المولّدة). فـ 403 على جذر الميديا متوقع؛ وهو في حد ذاته مش معناه إن خدمة الميديا مكسورة.
- 403 على مستوى التطبيق من الـ API، مثلًا Token XSRF ناقص. ملف compose بيذكر إن الـ Cookie لازم تتضبط للدومين الأب وإلا كل Mutation من المتصفح بتتّرفض بـ 403 (VERIFIED IN CODE، تعليق على WEB_COOKIE_DOMAIN في `deploy/web-preview/compose.yaml`).

**أوامر التشخيص الآمنة.** D1 مع نوع المحتوى في المخرجات، وD2؛ قارن نفس الرابط من شبكة مسموح لها وشبكة مش مسموح لها؛ وD22 لـ CUSTOMER_ALLOWED_IPS وSTAFF_ALLOWED_IPS (أدمن مصرّح له).

**دليل التشخيص المتوقع.** 403 الخاص بـ Caddy: نص قصير عادي "Not available" ومن غير x-amz-request-id. 403 الخاص بـ S3: نوع محتوى XML وx-amz-request-id. 403 الخاص بالـ API: Body خطأ JSON فيه requestId (DOCUMENTED ONLY: [دليل حل المشاكل التشغيلي](../../operations/08-troubleshooting-runbook.md)، القسم 1). سكريبت الـ Smoke نفسه بيتوقع 403 من مضيف ops لما يتشغّل برّه فئة الـ Staff مع SMOKE_OPS=skip (VERIFIED IN CODE).

**الإجراء التصحيحي.** ماتوسّعش قائمة وصول عشان 403 يختفي. لو حد مصرّح له بالاختبار مش قادر يدخل، المالك هو اللي يقرر يضيف عنوانه العام للـ Allow-list؛ والتغيير بيتعمل في ملف .env على السيرفر بواسطة أدمن مصرّح له، وبعدها F2 بصيغة إعادة الإنشاء للـ Proxy (الـ Caddyfile بيقرا القيم دي من بيئة الحاوية). الـ Tester اللي محتاج مسارات الـ Staff لازم يتضاف في CUSTOMER_ALLOWED_IPS وSTAFF_ALLOWED_IPS الاتنين. لجذر الميديا، مفيش إجراء مطلوب. لأوبجكت المفروض يكون عام، شوف السيناريو 21. لحالة XSRF، قارن WEB_COOKIE_DOMAIN بدومين الـ Preview.

**مستوى الخطورة.** متوسط لأي تغيير في قائمة الوصول: بيغيّر مين يقدر يوصل للـ Preview. إتاحة مضيفي العميل للعموم خطوة بيقررها المالك صراحةً.

**التحقق من التعافي.** عنوان الـ Tester بياخد 200 على مضيف app؛ وعنوان مش في القائمة لسه بياخد 403 على ops ومسارات الـ API الخاصة بالـ Staff؛ وD5 بينجح.

## 6. HTTP 404

**الأعراض.** مضيف أو مسار بيرد 404.

**الأسباب المحتملة.** عدة ردود 404 هي سلوك مقصود (By Design) (كلها VERIFIED IN CODE، الـ Caddyfile وسكريبت الـ Smoke):
- مضيف api: https://api.<domain>/actuator/health بيرجّع 404 لأن /api/* و/oauth2/* و/login/oauth2/* و/logout بس هي اللي بتتحوّل؛ وactuator شغّال على بورت الإدارة 8081 اللي الـ Proxy مابيحوّلش ليه أبدًا (كمان VERIFIED LIVE لـ /actuator/health).
- مضيف id: /admin و/realms/master ومساراتهم الفرعية بترجّع 404 على مضيف هوية العميل بالتصميم. سكريبت الـ Smoke بيشترط ده.
- مضيف kc-admin: كل مسار بيرجّع 404 بالنص "Not available" إلا لو ملف الـ Flag اسمه admin-console-public موجود في المجلد اللي الـ Proxy بيعمله Mount على /srv/flags. 302 من جذر kc-admin معناه إن الـ Flag موجود والـ Console مفتوح (VERIFIED LIVE: رجّع 302 بيحوّل إلى /admin/master/console/، يعني الـ Flag كان موجود). مجلد الـ Flag بيتقرا من المجلد المولّد في ملف .env؛ ومكانه الحي NOT VERIFIED LIVE.
- تطبيقات الويب بترد على المسارات المجهولة بصفحة الـ Index الخاصة بتطبيق الصفحة الواحدة، مش 404 (`try_files {path} /index.html`).

مشاكل حقيقية بتدّي 404 برضه: Image الـ Front أقدم من المتوقع (السيناريو 15)، أو المسار مكتوب غلط.

**أوامر التشخيص الآمنة.** D1 وD2 للمسار؛ وD14 لنسخة Image الـ Front؛ ولمضيف kc-admin، `./deploy.sh admin-console status` (F5، صيغة الحالة للقراءة فقط، أدمن مصرّح له).

**دليل التشخيص المتوقع.** الأكواد الموثّقة في [الوصول للـ Preview الحي](../05-developer-workflow/live-preview-access-ar.md). لو 404 مش متوقع ظهر على مسار موثّق، قارن D14 بالـ Commit اللي كنت متوقعه.

**الإجراء التصحيحي.** مفيش حاجة لردود 404 المقصودة. لفتح أو قفل Console الـ kc-admin استخدم F5 بتصريح المالك. خطأ توجيه حقيقي بيتصلّح عن طريق Pull Request (F9).

**مستوى الخطورة.** منخفض للتشخيص؛ فتح Console الأدمن خطورته عالية (بينشر سطح إدارة؛ والـ Console بيطلب كلمة سر وكلمة سر لمرة واحدة، DOCUMENTED ONLY في [كونسول أدمن Keycloak](../../operations/14-keycloak-admin-console.md)).

**التحقق من التعافي.** المسار بيرجّع كوده الموثّق، وD5 بينجح.

## 7. HTTP 502 و 503

**الأعراض.** مضيف بيرجّع 502 (Bad Gateway) أو 503. أو الـ API بيرجّع خطأ JSON بالكود DEPENDENCY_UNAVAILABLE.

**الأسباب المحتملة.**
- Caddy مش قادر يوصل للـ Upstream بتاعه (حاوية api أو identity أو storage) لأن الحاوية دي واقعة أو بتعيد التشغيل أو لسه بتبدأ. Caddy بيرد 502 في الحالة دي (DOCUMENTED ONLY: سلوك Caddy كـ Reverse Proxy؛ ماجرّبناش نعيد نفس الحالة هنا).
- الـ API بيرجّع 503 DEPENDENCY_UNAVAILABLE قابل لإعادة المحاولة لما PostgreSQL مش متاح، من غير تفاصيل عن قاعدة البيانات؛ وبيتعافى من غير إعادة تشغيل لما قاعدة البيانات ترجع (DOCUMENTED ONLY: [الـ Runbooks التشغيلية](../../operations/02-runbooks.md)، "Database unavailable"؛ والنص نفسه بيستشهد بـ DatabaseOutageRecoveryIntegrationTest كدليل).
- حاوية اتقتلت بسبب الذاكرة (السيناريو 19) أو مش سليمة (السيناريو 8).
- فيه Deployment شغّال.

**أوامر التشخيص الآمنة.** D1 على المضيف الفاشل؛ وبعدها، بواسطة أدمن مصرّح له، D8 وD9 للـ Proxy والخدمة الـ Upstream، وD10 وD11 وD20.

**دليل التشخيص المتوقع.** D8 بيظهر أنهي Upstream مش Up أو مش سليم. Log الـ Proxy بيسمّي الـ Upstream اللي رفض الاتصال. Body JSON بـ 503 من الـ API مع كود الاعتمادية بيشاور على PostgreSQL (السيناريو 9).

**الإجراء التصحيحي.** صلّح مشكلة الـ Upstream باتباع السيناريو الخاص بيه. 502 قصير أثناء الـ Deployment طبيعي؛ استنى لحد ما الـ Pipeline يخلّص قبل ما تتصرف. ماتعيدش تشغيل Proxy سليم عشان تمسح 502 سببه Upstream واقف.

**مستوى الخطورة.** متوسط.

**التحقق من التعافي.** D1 بيرجّع الكود الموثّق على المضيف المتأثر، وD8 بيظهر كل الخدمات سليمة، وD5 بينجح.

## 8. حاوية الـ Backend مش سليمة (Unhealthy)

**الأعراض.** خدمة api بتظهر unhealthy أو بتفضل تعيد التشغيل؛ و`./deploy.sh up` بينتهي بـ "API did not become healthy" وبيطبع آخر 80 سطر Log لخدمتَي api وidentity؛ والـ Proxy مابيبدأش أبدًا.

**الأسباب المحتملة.**
- الـ API بيبدأ بعد ما postgres وidentity يبقوا سليمين بس (VERIFIED IN CODE). فحص صحة identity بينجح بس لما Realm الـ matbakhy يرد، وده ممكن ياخد دقايق بعد أول استيراد.
- الـ API مش قادر يتصل بقاعدة البيانات بدور وقت التشغيل matbakhy_app، أو Flyway مش قادر يعمل Migrate بدور المالك matbakhy (السيناريوهات 9 و 16).
- متغيّر مطلوب ناقص؛ compose بيرفض يبدأ بـ "set X in .env" للمتغيّرات الإجبارية (VERIFIED IN CODE).
- نفاد الذاكرة (حد الـ api هو 2 GiB، السيناريو 19).
- فحص بورت الإدارة بيفشل: الـ Health Check بيطلب http://127.0.0.1:8081/actuator/health جوّه الحاوية وبيتوقع الحالة UP (VERIFIED IN CODE).
- الـ Image غلط أو مش قادرة تبدأ (السيناريوهات 13 و 15).

**أوامر التشخيص الآمنة.** D8 وD10 وD9 لـ api وidentity وpostgres، وD11 وD12 وD13 وD20 (أدمن مصرّح له).

**دليل التشخيص المتوقع.** سطور الـ Log JSON منظّمة (ECS) وservice.version بتساوي Commit الإصدار (VERIFIED IN CODE، `LOGGING_STRUCTURED_FORMAT_CONSOLE`). دوّر على أول خطأ بعد "Started"، أو فشل Flyway، أو فشل اتصال بـ postgres أو بـ Issuer الهوية، أو OOMKilled true في D20. نافذة صحة الـ API هي 60 محاولة كل واحدة 5 ثواني (5 دقايق) و`deploy.sh` بيستنى لحد 90 فحص كل واحد 5 ثواني (VERIFIED IN CODE).

**الإجراء التصحيحي.** طبّق السيناريو المناسب على أول خطأ تلاقيه. لو السبب مشكلة ترتيب بدء مؤقتة، استخدم F2 على خدمة api بس بعد ما postgres وidentity يبقوا سليمين. ماتمسحش الحاوية عشان تمسح حالة ماقريتهاش، وماتمسحش Volumes.

**مستوى الخطورة.** متوسط؛ الـ Preview كله واقع طول ما الـ API مش سليم، بس البيانات مش في خطر إلا لو Volume اتشال.

**التحقق من التعافي.** D10 بيطبع healthy، والـ Proxy Up، و`./deploy.sh smoke` بينجح.

## 9. PostgreSQL مش متاح

**الأعراض.** نداءات الـ API بترجّع 503 مع DEPENDENCY_UNAVAILABLE؛ وفحص صحة الـ api بيفشل؛ و`pg_isready` مابيردش؛ وتنبيه Prometheus اسمه DatabaseUnavailable (DOCUMENTED ONLY، [دليل حل المشاكل التشغيلي](../../operations/08-troubleshooting-runbook.md)، القسم 4).

**الأسباب المحتملة.**
- حاوية postgres واقفة أو بتعيد التشغيل أو نفدت ذاكرتها (حدها 1536 MiB، VERIFIED IN CODE).
- Volume البيانات تالف أو مليان أو مش مقروء (السيناريوهات 17 و 18).
- قاعدة البيانات لسه بتبدأ. الـ Health Check بتاعها بيعيد المحاولة 30 مرة كل 5 ثواني (VERIFIED IN CODE).
- أول بدء مع سكريبتات Init اتغيّرت: السكريبتات في /docker-entrypoint-initdb.d بتشتغل بس لما مجلد البيانات يكون فاضي (DOCUMENTED ONLY: سلوك Image PostgreSQL)، فتغييرها بعد كده مالوش أثر.
- كلمة سر غلط بعد ما ملف .env اتغيّر: قاعدة البيانات بتحتفظ بكلمات السر اللي اتعملت بيها أول مرة، فتغيير قيم .env لوحده مابيغيّرش كلمات السر المخزّنة (DOCUMENTED ONLY).

**أوامر التشخيص الآمنة.** D8 وD9 لـ postgres وD11 وD20 وD18 (أدمن مصرّح له).

**دليل التشخيص المتوقع.** D11 بيطبع "accepting connections" لما يكون سليم. الانهيار المتكرر (Crash Loop) بيظهر خطأ في Log postgres (صلاحيات أو قرص أو ذاكرة أو تلف). الـ API بيتعافى لوحده لما قاعدة البيانات ترجع، فاستمرار فشل الـ API بعد ما D11 يبقى سليم معناه إن المشكلة في حاجة تانية.

**الإجراء التصحيحي.** شغّل الحاوية لو واقفة (F2 لـ postgres بس، والمالك يكون عارف لأن كل الترافيك بيقف). صلّح القرص أو الصلاحيات الأول لما يكونوا هم السبب. لو مجلد البيانات تالف، الاستعادة هي الحل الأخير: وقّف الـ API، واستعد آخر نسخة احتياطية متحقق منها (F11)، وتقبّل ضياع البيانات من بعد النسخة دي، وخد تصريح المالك الصريح. ماتشيلش الـ Volume بتاع postgres-data أبدًا عشان "تبدأ من الأول".

**مستوى الخطورة.** عالي؛ فيه كل بيانات التطبيق. إجراءات الاستعادة خطورتها عالية وتحتاج المالك.

**التحقق من التعافي.** D11 accepting connections، وD12 بيظهر نسخة الـ Schema المتوقعة، والـ api سليم، و`./deploy.sh smoke` بينجح. بعد الاستعادة، استعلام المطابقة في `./deploy.sh restore-db` بيبلّغ إن كل الفحوص OK؛ والاستعادة من غير الـ Manifest المطابق ماتتقبلش حتى لو اتطبع RECONCILED، لأن عدد الصفوف ماتقارنش.

## 10. فشل Redirect تسجيل الدخول في Keycloak

**الأعراض.** الضغط على تسجيل الدخول بيودّي لصفحة خطأ، أو حلقة Redirect، أو رسالة "Invalid redirect URI" من Keycloak، أو Redirect لمضيف غلط؛ وفحص الـ Smoke "unexpected sign-in redirect" بيفشل.

**الأسباب المحتملة.**
- تسجيل الدخول بيبدأ من https://api.<domain>/oauth2/authorization/keycloak ولازم يعمل Redirect لـ https://id.<domain>/realms/matbakhy/protocol/openid-connect/auth مع redirect_uri هو https://api.<domain>/login/oauth2/code/keycloak (VERIFIED IN CODE، سكريبت الـ Smoke). الـ API بيبني الروابط دي من هيدرز X-Forwarded (SERVER_FORWARD_HEADERS_STRATEGY native)؛ لو هيدرز الـ Proxy ناقصة، بيظهر Scheme أو مضيف غلط.
- الـ Realm المستورد فيه عناوين Redirect لدومين تاني. الـ Realm بيتعمل له Render من القالب بدومين الـ Preview لما `deploy.sh render` يشتغل وبيتستورد عند البدء بـ --import-realm؛ والـ Realm الموجود مابيتعادش كتابته بإعادة استيراد (DOCUMENTED ONLY: سلوك استيراد Keycloak؛ شوف Runbook استبدال الدومين في [الـ DNS والـ HTTPS](dns-https-sslip-io-ar.md)).
- الـ Caller برّه فئة العميل، فمضيف id بيرجّع 403 (السيناريو 5).
- حاوية identity مش سليمة (السيناريو 8).
- المتصفح بيحجب الـ Cookie: دومين Cookie الـ XSRF اللي هو WEB_COOKIE_DOMAIN لازم يكون الدومين الأب (VERIFIED IN CODE).
- ظهور 302 على جذر id مش فشل: https://id.<domain>/ بيرد 302 لـ https://kc-admin.<domain>/admin/ لأن KC_HOSTNAME_ADMIN مضبوط على مضيف kc-admin في compose.yaml (VERIFIED LIVE وVERIFIED IN CODE). ده سلوك Keycloak متوقع، مش خطأ. العملاء مابيستخدموش جذر مضيف id أبدًا.

**أوامر التشخيص الآمنة.** `curl -s -o /dev/null -D - 'https://api.<domain>/oauth2/authorization/keycloak?returnTo=https://app.<domain>/'` (هيدر Location؛ نفس الطلب اللي سكريبت الـ Smoke بيعمله)، وD1 على مسارات مضيف id، وD5، وD9 لـ api وidentity (أدمن مصرّح له).

**دليل التشخيص المتوقع.** هيدر Location بيبدأ بـ `https://id.<domain>/realms/matbakhy/protocol/openid-connect/auth?` وفيه redirect_uri الخاص بالـ api. لو المضيف أو الـ Scheme في الهيدر غلط، يبقى هيدرز X-Forwarded هي المشكلة. لو Keycloak بيظهر صفحة خطأ، Log الـ Keycloak بيسمّي الـ Redirect المرفوض.

**الإجراء التصحيحي.** صحّح إعداد الـ Realm عن طريق قالب الـ Realm الموثّق وإجراء استبدال الدومين؛ وماتعدّلش الـ Clients بنفسك في بيانات شبه الإنتاج من غير المالك. عدم تطابق Redirect URI بعد تغيير دومين محتاج Realm محدّث وقرار بخصوص إعادة الاستيراد، ولازم المالك يوافق عليه لأنه بيأثر على الحسابات.

**مستوى الخطورة.** عالي لو محتاج تغيير في الـ Realm (الحسابات والـ Sessions)؛ غير كده منخفض.

**التحقق من التعافي.** فحص الـ Redirect أعلاه بيرجّع الـ Location المتوقع، وD5 بينجح، وحساب عميل تجريبي غير حقيقي (Synthetic) يقدر يسجّل دخول على جهاز اختبار (أسماء الحسابات بس، مثلًا customer-a؛ وكلمات السر بتتاخد من الأدمن المصرّح له زي ما هو موصوف في [الوصول للـ Preview الحي](../05-developer-workflow/live-preview-access-ar.md)).

## 11. عدم تطابق الـ Issuer في OIDC

**الأعراض.** الـ API بيسجّل فشل تحقق من Token أو Issuer؛ وكل طلب API فيه Authentication بيرجّع 401؛ وفحص الـ Smoke "identity issuer is ..." بيفشل.

**الأسباب المحتملة.**
- نص الـ Issuer اللي الـ API بيتوقعه، OIDC_ISSUER، هو https://id.<domain>/realms/matbakhy (VERIFIED IN CODE، compose.yaml). Keycloak بيحط اسم المضيف العام المضبوط عنده في الـ Issuer: KC_HOSTNAME هو https://id.<domain>. لو PREVIEW_DOMAIN في ملف .env بيختلف عن الدومين اللي Keycloak اتشغّل بيه، أو الـ Proxy مابيمرّرش هيدرز X-Forwarded، الاتنين بيختلفوا.
- الـ API بيجيب المفاتيح عبر العنوان الداخلي OIDC_BACKCHANNEL_ISSUER (http://identity:8080/realms/matbakhy)؛ وKC_HOSTNAME_BACKCHANNEL_DYNAMIC قيمته true فنداءات الـ Back-channel بتستخدم الاسم الداخلي. لو حاوية identity مش قابلة للوصول بالاسم ده، التحقق بيفشل.
- الدومين اتغيّر في مكان واحد بس (الدومين بيظهر في ملفات كتير، شوف [الـ DNS والـ HTTPS](dns-https-sslip-io-ar.md)).
- الـ Caller استخدم Token الـ Realm الـ master على الـ API بتاع matbakhy؛ دول Issuers منفصلة.

**أوامر التشخيص الآمنة.** `curl -s https://id.<domain>/realms/matbakhy/.well-known/openid-configuration` واقرا قيمة الـ Issuer (سكريبت الـ Smoke بيعمل ده بالظبط)؛ وD22 لـ PREVIEW_DOMAIN (أدمن مصرّح له)؛ وD9 لـ api.

**دليل التشخيص المتوقع.** الـ Issuer بيساوي https://id.<domain>/realms/matbakhy حرف بحرف (فحص الـ Smoke بيقارن بـ `[ "$issuer" = "https://id.$d/realms/matbakhy" ]`، VERIFIED IN CODE). أي فرق في المضيف أو الـ Scheme أو المسار الأخير هو عدم التطابق.

**الإجراء التصحيحي.** خلّي PREVIEW_DOMAIN وKC_HOSTNAME وOIDC_ISSUER متفقين بتصحيح قيمة .env (المالك أو أدمن مصرّح له)، وبعدها F10 وF2 بصيغة إعادة الإنشاء لـ identity وapi (الـ restart كان هيسيب قيم الـ Issuer القديمة). لو كان المقصود استبدال دومين، اتبع الـ Runbook الكامل في [الـ DNS والـ HTTPS](dns-https-sslip-io-ar.md) بدل ترقيع قيم فردية.

**مستوى الخطورة.** عالي؛ إعدادات الـ Issuer الغلط بتمنع كل المستخدمين من الدخول، وتغييرات الـ Realm بتأثر على الحسابات.

**التحقق من التعافي.** الـ Issuer في وثيقة الـ Discovery متطابق، والـ api سليم، وD5 بينجح، وتسجيل دخول تجريبي بيشتغل.

## 12. مشاكل MFA لأدمن Keycloak

**الأعراض.** أدمن مش قادر يسجّل دخول لـ https://kc-admin.<domain>/admin/master/console/: كلمة السر لمرة واحدة مرفوضة، أو الحساب معندوش كلمة سر لمرة واحدة، أو التليفون ضاع، أو الحساب اتقفل بعد محاولات فاشلة.

**الأسباب المحتملة (كلها DOCUMENTED ONLY في [كونسول أدمن Keycloak](../../operations/14-keycloak-admin-console.md)؛ والـ Realm الحي ماتفحصش).**
- تسجيل الدخول للـ Realm الـ master بيتطلب كلمة السر وكلمة سر لمرة واحدة؛ ومفيش تسجيل جهاز ذاتي للعموم. حساب من غير كلمة سر لمرة واحدة مش قادر يسجّل دخول ومابيتعرضش عليه خيار تسجيل جهاز. حساب الـ Bootstrap معندوش واحدة، فبعد التقوية مش قادر يسجّل دخول للـ Console بالتصميم.
- تطبيق المصادقة عنده مفتاح غلط أو ساعة التليفون غلط (كل كود 6 أرقام وبيتغيّر كل 30 ثانية).
- الحماية من الـ Brute-force حدّت من معدّل محاولات الدخول للحساب بعد محاولات فاشلة.
- المضيف بيرد 404 لأن ملف التفعيل (Flag) مش موجود (الـ Console مقفول).
- حساب أدمن تجريبي غير حقيقي (Synthetic) للتحقق فضل موجود بعد تشغيلة اتقطعت.

**أوامر التشخيص الآمنة.** D1 على جذر kc-admin وعلى /admin/realms (مسار Admin REST من غير Token المفروض يرد 401 لما الـ Console يكون مفتوح؛ VERIFIED IN CODE، سكريبت الـ Smoke). `./deploy.sh admin-console status` (F5، الصيغة للقراءة فقط) بيسرد كل حساب Master بـ otp أو no-otp(cannot-sign-in)، أدمن مصرّح له.

**دليل التشخيص المتوقع.** Console مفتوح: الجذر 302 لـ /admin/master/console/، ومسار Admin REST بيرد 401 من غير Token. Console مقفول: 404. حساب مدرج بـ no-otp(cannot-sign-in) هو التفسير المتوقع لـ "مش قادر أسجّل دخول".

**الإجراء التصحيحي.** أدمن مصرّح له بس هو اللي بينفّذ دول، بموافقة المالك. أنشئ أو استبدل أدمن بـ F5 `create-admin NAME` (استبدال تليفون ضايع بيستخدم الصيغة الموثّقة REPLACE=1). الأمر بيكتب ملف خاص Mode 600 فيه كلمة السر ومفتاح كلمة السر لمرة واحدة تحت المجلد المولّد؛ الشخص بيقراه على السيرفر، ويضيف المفتاح لتطبيق مصادقة، ويسجّل دخول، ويمسح الملف. ماتلصقش أو تسجّل محتواه أبدًا، وماتبعتوش في شات أو تذكرة. ماتضعّفش السياسة (ماتفعّلش الـ Password Grant تاني، وماتعطّلش كلمة السر لمرة واحدة) عشان تدخّل حد؛ واستخدم rollback بقرار المالك بس.

**مستوى الخطورة.** عالي؛ بيتحكم في مزوّد الهوية للـ Preview كله. فيه بيانات سرّية.

**التحقق من التعافي.** `./deploy.sh admin-console status` (قراءة فقط) بيظهر الحساب كـ otp؛ واختياريًا `./deploy.sh admin-console verify`، وده مش للقراءة فقط لأنه بيعمل وبيمسح أدمن تجريبي غير حقيقي (Synthetic) للقراءة فقط للتشغيلة، بموافقة المالك. بعد أي واحد منهم، الأدمن بيسجّل دخول بكلمة السر والكود؛ والملف الخاص يتمسح.

## 13. فشل سحب Docker Image

**الأعراض.** `./deploy.sh up` أو Job الـ Deployment بيقف أثناء السحب بخطأ زي "manifest unknown" أو "unauthorized" أو "denied" أو "no space left on device" أو "TLS handshake timeout" أو "toomanyrequests".

**الأسباب المحتملة.**
- الـ Docker Images الاتنين بتوعنا بيتسحبوا من GitHub Container Registry (ghcr.io) بالـ Digest. الـ Deployment بيسجّل دخول لـ ghcr.io على السيرفر باستخدام Token الـ Workflow طول مدة التشغيلة، في مجلد إعدادات Docker مؤقت بيتمسح بعد كده (VERIFIED IN CODE). لو الـ Token ناقصه صلاحية، السحب بيتّرفض.
- الـ Digest مش موجود (البناء ماعملش Push، أو الـ Image اتمسحت).
- Images الجهات الخارجية (Third-party) مثبّتة بالـ Digest وبتتسحب من الـ Registries بتاعتها؛ وحد معدّل الطلبات (Rate Limit) أو انقطاع في Registry ممكن يمنع السحب (DOCUMENTED ONLY: حدود معدّل الـ Registries؛ والوضع الحي NOT VERIFIED).
- السيرفر مش قادر يوصل للـ Registry (شبكة أو DNS أو Firewall؛ القواعد الصادرة NOT VERIFIED LIVE).
- القرص مليان (السيناريو 18).
- ملف .env فيه مرجع Image مش مثبّت بالـ Digest؛ و`deploy.sh up` بيرفض يبدأ في الحالة دي (VERIFIED IN CODE).

**أوامر التشخيص الآمنة.** D13 (أدمن مصرّح له) للمراجع؛ وD18؛ وD23 لـ Job البناء؛ و`getent hosts ghcr.io` من الـ VPS.

**دليل التشخيص المتوقع.** نص الخطأ بيسمّي مرجع الـ Image والسبب. `compose pull --policy missing` بيسحب الـ Images اللي مش موجودة بس، فـ Image موجودة محليًا مش هي السبب (VERIFIED IN CODE).

**الإجراء التصحيحي.** صحّح المرجع أو أعد تشغيل البناء لو الـ Digest ناقص (F8). لو الـ Token هو المشكلة، مالك الـ Repository بيصلّح صلاحية الـ Package أو صلاحيات الـ Workflow؛ وماتلصقش Token شخصي على السيرفر. لانقطاع Registry، استنى. لمشكلة القرص، السيناريو 18. ماترجعش تستخدم Tags غير مثبّتة زي `latest`؛ ده بيكسر قاعدة الـ Immutable Release.

**مستوى الخطورة.** متوسط؛ سحب فاشل بيسيب الحاويات السابقة شغّالة لأن `up` بيسحب قبل ما يبدأ (VERIFIED IN CODE).

**التحقق من التعافي.** السحب بيكتمل، و`./deploy.sh status` بيعرض الـ Images المتوقعة، والخدمات سليمة وD5 بينجح.

## 14. فشل الـ Deployment في GitHub Actions

**الأعراض.** الـ Workflow اسمه Deploy Matbakhy preview to Contabo بينتهي بفشل واحد أو أكتر من الفحوص.

**الأسباب المحتملة.** الـ Workflow فيه Jobs اتنين، build وdeploy (VERIFIED IN CODE، `.github/workflows/deploy-preview.yml`). حدّد الخطوة الفاشلة:

| الخطوة الفاشلة | معناها |
|---|---|
| Wait for passing Product CI at this exact commit | الـ Product CI للـ Commit ده فشل، أو اتلغى، أو ماخلّصش في خلال 30 دقيقة (60 فحص كل 30 ثانية). صلّح الـ CI الأول |
| Build API jar, أو Build and publish immutable images | خطأ Compile أو بناء Docker، أو فشل تسجيل دخول Registry، أو فشل Push |
| Configure verified SSH host | Secret مطلوب فاضي: الخطوة بتسمّي المتغيّر الناقص من بين HOST وUSER وSSH_KEY وHOST_PUBKEY وAPI_IMAGE وFRONT_IMAGE |
| Transfer versioned deployment files | الفحص عن بُعد `test -f .env && test -w /srv/matbakhy-staging` فشل (مفيش ملف .env، أو الحساب المتصل مش قادر يكتب في المجلد)، أو فشل التحقق من مفتاح مضيف SSH (الـ Workflow بيستخدم StrictHostKeyChecking=yes مع المفتاح العام للمضيف المخزّن) |
| Deploy digest-pinned images, wait for health, HTTPS smoke | `./deploy.sh up` أو `./deploy.sh smoke` فشل على السيرفر (السيناريوهات 8 و 13 و 16 و 22) |
| Publish and verify the Keycloak admin console | `./deploy.sh admin-console enable` فشل. الفشل قبل ما الـ Switch يتفتح بيقفل الـ Console تاني (Fail closed، DOCUMENTED ONLY في [كونسول أدمن Keycloak](../../operations/14-keycloak-admin-console.md))؛ لكن `admin-console.sh` بيفتح الـ Switch قبل `create-admin` الاختياري، فلو إنشاء الأدمن فشل بعد كده الـ Exit Trap بيسيب الـ Console مفتوح (VERIFIED IN CODE: `deploy/web-preview/admin-console.sh`). بعد أي فشل في الخطوة دي شغّل `./deploy.sh admin-console status`، ولو الـ Console مفتوح ومش عايزه شغّل `./deploy.sh admin-console disable` |

الـ Workflow بيعمل Deploy على الـ Push لـ develop وعلى التشغيل اليدوي بس، تشغيلة واحدة في المرة (Concurrency Group اسمه matbakhy-preview-deploy، وcancel-in-progress قيمته false)، فالتشغيلة اللي في الطابور بتستنى اللي قبلها. قيم الـ Secrets (STAGING_SSH_HOST وSTAGING_SSH_USER وSTAGING_SSH_KEY وSTAGING_SSH_HOST_PUBKEY) مش في الـ Repository والحساب المتصل NOT VERIFIED.

**أوامر التشخيص الآمنة.** D23؛ وصفحة تشغيلة Actions؛ وD7 وD15 على السيرفر لمعرفة اللي اتعمله Deploy فعلًا (أدمن مصرّح له).

**دليل التشخيص المتوقع.** اسم الخطوة الفاشلة من D23، وسطر الخطأ بتاعها. التشغيلة الناجحة بتنتهي بـ `DEPLOYED <sha>` في خطوة الـ Deploy. آخر تشغيلة ناجحة مش دليل على اللي شغّال دلوقتي: ممكن تشغيلة أحدث تكون نشرت Images جديدة وبعدين فشلت في خطوة الـ Admin Console، وده بيسيب الـ Images دي شغّالة. قارن D7 بقائمة التشغيلات (D23) قبل ما تسمّي الإصدار الحالي.

**الإجراء التصحيحي.** صلّح السبب المحدد (CI أو بناء أو Secret أو مفتاح مضيف أو حالة السيرفر). أعد التشغيل بعد ما السبب يتصلّح بس (F8). ماتعيدش تشغيل Job سليم شغّال عشان هو بطيء. ماتطبعش أسرار في Log أثناء الـ Debug أبدًا. استعادة ملف .env السابق بتحصل في مكان واحد بس: جوّه سكريبت الـ Deploy عن بُعد، لما `./deploy.sh up` أو `./deploy.sh smoke` يفشل (VERIFIED IN CODE). أي فشل قبل كده (ملف .env ناقص، تسجيل الدخول للـ Registry، تعديل .env نفسه) بيخرج من غير أي استعادة، وفشل خطوة "Publish and verify the Keycloak admin console" اللي بعدها مابيستعيدش أي حاجة: الـ Images الجديدة و.env الجديد بيفضلوا زي ما هم، والـ Console ممكن يفضل مفتوح (اتأكد بـ `./deploy.sh admin-console status` وقفله بـ `./deploy.sh admin-console disable` لو مش عايزه). الاستعادة بتخص ملف .env بس؛ الحاويات مابتتعملهاش إعادة إنشاء وقاعدة البيانات مابتتعملهاش Rollback (السيناريوهات 15 و 23).

**مستوى الخطورة.** متوسط؛ Deployment نصف مكتمل ممكن يسيب السيرفر على إعدادات مختلطة: بعد فشل `up` أو `smoke` ملف .env بيبقى القديم وفي نفس الوقت ممكن بعض الحاويات تكون شغّالة بالـ Images الجديدة.

**التحقق من التعافي.** تشغيلة بتوصل لـ `DEPLOYED <sha>` وبتخلّص وكل فحوصها ناجحة، وD7 بيظهر الـ sha ده كـ RELEASE_COMMIT.

## 15. نسخة Image غلط

**الأعراض.** الـ Preview بيظهر سلوك أقدم من آخر Merge؛ والـ Frontend والـ API مش متفقين؛ والنسخة في الـ Logs مش الـ Commit المتوقع؛ وقاعدة Caddy قديمة لسه شغّالة.

**الأسباب المحتملة.**
- الـ Deployment ماخلّصش أو كان في الطابور ورا تشغيلة تانية.
- Deployment فاشل استعاد ملف .env القديم لكن الحاويات كانت اتعاد إنشاؤها فعلًا بالـ Images الجديدة، أو العكس. السكريبت عن بُعد بيستعيد الملف بس؛ مابيشغّلش compose تاني (VERIFIED IN CODE)، فملف .env والحاويات الشغّالة ممكن يختلفوا بعد فشل.
- RELEASE_COMMIT وAPI_IMAGE وFRONT_IMAGE اتعدّلوا يدويًا وبقوا غير متسقين مع بعض.
- Image الـ Front بتتبني لدومين Preview واحد عن طريق Build Argument (VERIFIED IN CODE)، فـ Image متبنية لدومين تاني هتنادي على عنوان API غلط.

**أوامر التشخيص الآمنة.** D7 (القيم المضبوطة والـ Images الشغّالة وآخر الإصدارات)، وD13، وD14 للـ Docker Images الاتنين، وD15، وحقل Log الـ API اسمه service.version (D9).

**دليل التشخيص المتوقع.** RELEASE_COMMIT في ملف .env، وLabel الـ revision بتاع الـ Docker Images الاتنين، وservice.version في Logs الـ API المفروض يبقوا نفس الـ Commit من 40 حرف. Commit أقصر معروض من status هو عرض مقتطع بس (`substr` لـ 12 حرف، VERIFIED IN CODE).

**الإجراء التصحيحي.** عشان ترجع قيم الإصدار والـ Images متوافقة مع بعض، شغّل الـ Pipeline للـ Commit المقصود (F8) أو، بواسطة أدمن مصرّح له، اضبط القيم التلاتة من إصدار مسجّل (D16) وشغّل F1. ماتخلطش Image API من Commit مع Image Front من Commit تاني. أي حاجة بترجّع الـ Schema بتاعة قاعدة البيانات لورا مش إصلاح Image (السيناريو 23).

**مستوى الخطورة.** متوسط.

**التحقق من التعافي.** D7 وD14 ونسخة الـ Log كلهم بيظهروا الـ Commit المقصود؛ وD5 بينجح.

## 16. فشل Migration لقاعدة البيانات

**الأعراض.** الـ API مابيبقاش سليم أبدًا بعد Deployment؛ والـ Log بتاعه بيظهر خطأ Flyway؛ وD12 بيظهر صف فيه قيمة success فيها f؛ والـ Deployment بينتهي بـ "API did not become healthy".

**الأسباب المحتملة.**
- Migration جديد فيه خطأ أو بيتعارض مع بيانات موجودة.
- Flyway بيتصل بدور المالك matbakhy (SPRING_FLYWAY_USER) بينما الـ API بيشتغل بدور matbakhy_app الأقل صلاحية؛ وأي Grant ناقص بيتطبّق بواسطة الـ Callback اسمه afterMigrate (VERIFIED IN CODE، تعليق compose.yaml). فخطأ صلاحيات غالبًا بيشاور على كلمة سر المالك.
- Migration اتغيّر بعد ما اتطبّق (عدم تطابق Checksum، DOCUMENTED ONLY: سلوك Flyway القياسي).
- نقص في مساحة القرص أو الذاكرة أثناء الـ Migration (السيناريوهات 18 و 19).

**أوامر التشخيص الآمنة.** D9 لـ api، وD12، وD16، وD15، وD11.

**دليل التشخيص المتوقع.** الـ Log بيسمّي نسخة الـ Migration اللي فشلت. قبل كل `up` بيلاقي حاوية PostgreSQL شغّالة، `deploy.sh` بياخد نسخة احتياطية وبيستعيدها في قاعدة بيانات مؤقتة عشان يتحقق منها (VERIFIED IN CODE)؛ والمخرجات بتظهر سطرين "backup ... written" و"backup verified: ... restored and reconciled" ومعاهم مسار الـ Dump. أما سجل الإصدارات فالوضع فيه مختلف: `record_release` بيشتغل بعد ما الـ Stack يبدأ ويبقى سليم بس (VERIFIED IN CODE)، فنسخة Deployment فشل أثناء الـ Migration مش بتظهر في D15. دوّر عليها في مخرجات الـ Deployment ده (Log الـ Actions أو Console اللي شغّل `up`) أو في أحدث مجلد `db-<UTC stamp>` تحت BACKUP_DIR (جوه حاوية postgres المسار هو /backups/db-<UTC stamp>)، وراجع ملفات `.sha256` و`.manifest` اللي `scripts/db-backup.sh` بيكتبها جنب الـ Dump. D15 وD16 بيعدّوا الإصدارات الناجحة بس، فترقيم `rollback-plan [N]` مابيحسبش محاولة فشلت قبل ما الـ Stack يبقى سليم (والمحاولة اللي فشلت بعد كده، زي في `smoke`، بتتسجّل). أول Deployment على سيرفر فاضي مابياخدش نسخة احتياطية لأن مفيش حاوية قاعدة بيانات لسه (VERIFIED IN CODE).

**الإجراء التصحيحي.** الـ Migrations بتتحرك للأمام بس (DOCUMENTED ONLY، [الـ Deployment والـ Rollback للـ Staging](../../operations/11-staging-deployment-and-rollback.md)، القسم 4). اختار بالجدول في السيناريو 23: Migration جديد لإصلاح المشكلة للأمام (Fix-forward) عن طريق Pull Request، أو Rollback للـ Images بس لما كل Migration من آخر إصدار سليم هو Expand-only (`scripts/check_migration_compat.py` بيفرض الـ Annotation في الـ CI)، أو استعادة من النسخة الاحتياطية قبل الـ Deployment للـ Migrations الكاسرة (F11). ماتعدّلش جدول flyway_schema_history ولا تمسح صفوف بنفسك، وماتشغّلش أوامر إصلاح ارتجالية على بيانات حيّة من غير المالك.

**مستوى الخطورة.** عالي؛ ده بيلمس الـ Schema بتاعة المصدر الوحيد لبيانات البيزنس.

**التحقق من التعافي.** D12 بيظهر أعلى version وقيمة success فيها t، والـ API سليم، واستعلام المطابقة بيبلّغ OK بعد الاستعادة، وD5 بينجح.

## 17. أخطاء صلاحيات الـ Persistent Volume

**الأعراض.** حاوية بتخرج عند البدء بـ "permission denied" أو "read-only file system"؛ وKeycloak مش قادر يستورد الـ Realm؛ وSeaweedFS مش قادر يقرا ملف بيانات الاعتماد بتاعه؛ والنسخ الاحتياطية مش قادرة تتكتب؛ و`deploy.sh up` بيفشل في إنشاء القفل المشترك.

**الأسباب المحتملة.**
- الـ Stack بيستخدم Docker Volumes مسمّاة للحالة (postgres-data وstorage-data وcaddy-data وcaddy-config وloki-data وalloy-data وprometheus-data وalertmanager-data وalert-sink-data وgrafana-data) ومسارات على الجهاز المضيف للإعدادات المولّدة والـ Flags والنسخ الاحتياطية (VERIFIED IN CODE، compose.yaml). مسار الـ Docker data root ومواقع الـ Volumes المسمّاة على الجهاز ومحتويات الـ Volumes NOT VERIFIED LIVE (محتاجين الـ Docker socket)؛ اتأكد منها بفحص الـ Volume.
- compose.yaml بيعمل Bind-mount لـ BACKUP_DIR/metrics جوّه حاوية node-exporter (VERIFIED IN CODE). لو مجلد الجهاز ده مش موجود لما الـ Stack يبدأ أول مرة، Docker بينشئه بنفسه (DOCUMENTED ONLY: سلوك Docker مع مصدر Bind ناقص)، وغالبًا بيبقى مملوك لـ root، وساعتها ملف مقياس النسخ الاحتياطي اللي بيكتبه حساب الـ Deployment ممكن يفشل. لازم المجلد يتعمل قبل كده بمالك يسمح لحساب الـ Deployment يكتب فيه؛ وتسلسل إعادة البناء لسه مابيقولش كده (فجوة توثيق، NOT VERIFIED LIVE).
- مسارات المضيف: المجلد المولّد المفروض يكون Mode 700 ومجلداته الفرعية identity وstorage بـ 755 وملفاته 644، عشان الحاويات تقدر تقراهم بينما باقي مستخدمي السيرفر مايقدروش يوصلولهم بالمسار (VERIFIED IN CODE، `render` في `deploy/web-preview/deploy.sh`). على السيرفر الحي /srv/matbakhy-staging/generated هو drwx------ ومملوك لـ devops (VERIFIED LIVE).
- GENERATED_DIR وBACKUP_DIR في ملف .env ممكن يشاوروا على مجلد الحساب مش قادر يكتب فيه. ملف المثال اللي في الـ Repository والافتراضي في compose لسه بيستخدموا /srv/matbakhy-preview/generated، بينما نسخة قديمة بالاسم ده موجودة على السيرفر الحي (VERIFIED LIVE) والقيمة الحية لـ GENERATED_DIR NOT VERIFIED. أكّدها بـ D22 قبل ما تغيّر أي حاجة.
- حد شغّل أمر بحساب غلط وعمل ملفات مملوكة لـ root في مجلد حساب الـ Deployment لازم يكتب فيه.

**أوامر التشخيص الآمنة.** D21 على المجلد المولّد ومسارات الـ Flags وidentity وstorage والنسخ الاحتياطية؛ وD22؛ وD9 للحاوية الفاشلة؛ و`ls -ld /srv /srv/matbakhy-staging /srv/matbakhy-backups`.

**دليل التشخيص المتوقع.** قارن بالـ Modes أعلاه. /srv هو root:root 755، و/srv/matbakhy-staging هو devops:devops 755، و/srv/matbakhy-backups هو devops:devops 750 (VERIFIED LIVE). ملف بمالك أو Mode غلط هو الدليل؛ و"Permission denied" على قائمة مجلد من حساب غير مميّز متوقع للمجلدات المحمية وهو في حد ذاته مش عطل.

**الإجراء التصحيحي.** فضّل السكريبت اللي بيضبط الـ Modes: F10 (`./deploy.sh render`)، وبعدها F2 (صيغة إعادة الإنشاء) للخدمات اللي بتقرا الملفات. لأي مسار تاني، أدمن مصرّح له بيصلّح المالك أو الـ Mode للمسار المسمّى بس، بعد تسجيل القيم الحالية بـ D21. ماتطبّقش صلاحيات واسعة بشكل Recursive (مثلًا Mode 777)، وماتخلّيش المجلد المولّد مقروء للكل، وماتضيفش حساب aiagent لمجموعة docker أو sudo عشان يقرا المسارات دي.

**مستوى الخطورة.** متوسط؛ أخطاء الصلاحيات ممكن تكشف أسرار أو تكسر خدمات.

**التحقق من التعافي.** D21 بيظهر المالك والـ Mode المتوقعين، والحاوية الفاشلة بتبدأ وبتفضل سليمة، وD5 بينجح.

## 18. القرص مليان

**الأعراض.** خدمات بتنهار أو بتفشل تكتب؛ وPostgreSQL بيوقف قبول الكتابة؛ و"no space left on device" بيظهر في الـ Logs أو السحب؛ وتنبيه DiskPressure بيشتغل.

**الأسباب المحتملة.** في 2026-10-21 القرص الجذر /dev/sda1 كان حجمه 290 GB والمستخدم منه 18 GB (VERIFIED LIVE)، فده مش الوضع الحالي. مصادر النمو المحتملة، حسب الإعدادات (VERIFIED IN CODE إلا لو اتعلّم غير كده):
- Logs الحاويات: 10 MB × 3 ملفات لكل حاوية.
- مخزن Logs بتاع Loki بفترة احتفاظ مقترحة 720 ساعة، وmetrics بتاعة Prometheus لمدة 15 يوم وحد حجم 2 GB. الاتنين قيم افتراضية مقترحة، مش سياسات معتمدة.
- نسخ احتياطية مكتوبة في BACKUP_DIR، كل واحدة فيها Dump لقاعدة البيانات وأرشيف لتخزين الأوبجكتس، وبتفضل لحد ما حد يشيلها (مفيش مسح تلقائي للنسخ الاحتياطية ظاهر في الـ Repository؛ وهل في Cron حي بيعمله NOT VERIFIED، لأن الـ Crontabs بتاعة المستخدمين التانيين مش مقروءة).
- Images قديمة (كل إصدار بيسحب اتنين Docker Images جداد بالـ Digest).
- ملفات الـ QA (السكريبت `deploy.sh prune-qa` موجود ليها).
- أدوات مالهاش علاقة على نفس الجهاز (مجلدات AI Supervisor موجودة تحت /srv، VERIFIED LIVE؛ وهي مش جزء من بيئة تشغيل Matbakhy).

**أوامر التشخيص الآمنة.** D18؛ و`du -xh --max-depth=1 /srv` (المجلدات اللي الحساب مش قادر يقراها هتبلّغ أخطاء؛ والأدمن بيشغّله للنتايج الكاملة)؛ و`docker system df` (أدمن مصرّح له).

**دليل التشخيص المتوقع.** استخدام فوق حوالي 85 في المية على نظام الملفات الجذر، أو `docker system df` بيظهر مساحة Images كبيرة قابلة للاسترداد، أو BACKUP_DIR كبير.

**الإجراء التصحيحي.** بالترتيب ده، وقف لما تبقى المساحة كفاية: F7 صيغة العرض، وبعدها F7 مع --apply لملفات الـ QA القديمة؛ وF6 للـ Dangling Images؛ انقل النسخ الاحتياطية الأقدم لتخزين برّه الجهاز وامسح النسخ الأقدم بتصريح المالك بس وبعد التأكد إن أحدث نسخة احتياطية اتستعادت بنجاح (مقياس BackupStale). ماتمسحش أو تعمل Prune لـ Volumes أبدًا (قاعدة البيانات، وتخزين الأوبجكتس، والـ Logs، والـ metrics)، وماتمسحش أحدث نسخة احتياطية أبدًا. ماتشغّلش F4 والقرص قرب يتملي؛ هو محتاج مساحة.

**مستوى الخطورة.** عالي لو Volume قاعدة البيانات على القرص المليان؛ مسح العنصر الغلط مفيش رجوع منه.

**التحقق من التعافي.** D18 بيظهر مساحة فاضية، وpostgres بيقبل اتصالات (D11)، والـ Stack سليم (D8)، وF4 الجاي بيكتمل (ملف نسخة احتياطية جديد والمقياس اتحدّث).

## 19. ضغط الذاكرة

**الأعراض.** حاوية بتعيد التشغيل بـ Exit code 137؛ وردود بطيئة؛ والجهاز بيعمل Swap؛ وD20 بيظهر OOMKilled true؛ والـ Preview فيه ردود 502 عشوائية.

**الأسباب المحتملة.**
- خدمة وصلت لحدّها. ملف compose بيضبط حدود ذاكرة لكل خدمة: proxy بـ 256 MiB، وpostgres بـ 1536 MiB، وidentity بـ 1 GiB، وstorage بـ 512 MiB، وapi بـ 2 GiB، وloki بـ 384 MiB، وalloy بـ 256 MiB، وprometheus بـ 512 MiB، وalertmanager بـ 128 MiB، وalert-sink بـ 64 MiB، وnode-exporter بـ 64 MiB، وgrafana بـ 256 MiB؛ والمجموع المحسوب من compose.yaml هو 7040 MiB، حوالي 6.9 GiB (VERIFIED IN CODE).
- الجهاز عنده حوالي 23 GiB والمستخدم حوالي 3.4 GiB وقت الملاحظة (VERIFIED LIVE)، فاستنفاد الذاكرة على مستوى الجهاز بسبب الـ Stack ده لوحده مستبعد؛ والأدوات التانية على نفس الجهاز (AI Supervisor وعملية Java مالكها مجهول، الاتنين ظاهرين في الـ Listening Sockets اللي اتلاحظت على السيرفر، VERIFIED LIVE) مش مثبّت إنها أسباب.
- خدمات Java بتستخدم نسبة من حد الحاوية (MaxRAMPercentage 70 للـ API وKeycloak)، فحد مضبوط واطي أوي بيسيب Heap قليل (VERIFIED IN CODE).
- ملف الـ Override لحجم 4 GB اسمه compose.4gb.yaml اتكتب لسيرفر Hetzner مقترح (OUTDATED / INCORRECT لجهاز Contabo ذو الـ 23 GiB). وهل ملف .env الحي بيضبط COMPOSE_OVERRIDE NOT VERIFIED؛ اتأكد بـ D22، لأن الـ Override بيخفض الحدود.

**أوامر التشخيص الآمنة.** D19 وD20 وD22 (COMPOSE_OVERRIDE)، وعلى الجهاز المضيف، Log الـ Kernel لأحداث نفاد الذاكرة بواسطة أدمن مصرّح له (`journalctl -k --since "1 hour ago"`، بيتطلب عضوية في مجموعة تقدر تقرا الـ Journal؛ وأنهي حسابات تقدر NOT VERIFIED).

**دليل التشخيص المتوقع.** OOMKilled true لحاوية واحدة مسمّاة، وRestartCount بتاعها بيزيد؛ والذاكرة الفاضية طبيعية على مستوى الجهاز. OOMKilled لوحدها مابتبيّنش إذا كان السبب حدّ الحاوية ولا ذاكرة الجهاز؛ أكّد من Log الـ Kernel (القتل بسبب memory cgroup بيسمّي الـ cgroup بتاع الحاوية، والقتل على مستوى الجهاز كله لأ) قبل ما تحدد السبب.

**الإجراء التصحيحي.** ارفع حد الخدمة دي بس، عن طريق Pull Request بيغيّر compose.yaml ويتعمل له Deploy بالـ Pipeline (F9)؛ وماترفعش حدود مالهاش علاقة. أعد تشغيل الخدمة المتأثرة (F2، صيغة `restart` اللي مابتطبّقش أي تغيير في الإعدادات) عشان ترجّع الخدمة أثناء تجهيز التغيير. دوّر على السبب وصلّحه (مثلًا استعلام طويل أو Memory Leak) بدل ما ترفع الحدود بس. ماتشغّلش الـ Stack من غير حدود.

**مستوى الخطورة.** متوسط؛ تكرار قتل postgres ممكن يطوّل وقت التعافي بعد الانهيار (Crash Recovery)، وقتل الـ API ممكن يسبب فشل الطلبات.

**التحقق من التعافي.** D20 بيظهر OOMKilled false وRestartCount ثابت، وأوقات الرد طبيعية، وD5 بينجح.

## 20. فشل تجديد الشهادة

**الأعراض.** تاريخ الشهادة من D4 قرب ينتهي أو انتهى؛ والمتصفحات بدأت تظهر تحذيرات انتهاء؛ وLog الـ Proxy بيظهر أخطاء ACME.

**الأسباب المحتملة.**
- Caddy بيحصل على الشهادات من Let's Encrypt وبيجدّدها تلقائيًا لكل اسم موقع في الـ Caddyfile (DOCUMENTED ONLY: سلوك Caddy؛ الـ Issuer وصلاحية الـ 90 يوم اتلاحظوا، VERIFIED LIVE، لكن التجديد نفسه لسه ماتأكدش منه). بيانات التواصل الخاصة بـ ACME متظبّطة في ACME_EMAIL.
- بورت 80 أو 443 مبقاش قابل للوصول من الإنترنت (حالة Firewall الجهاز NOT VERIFIED LIVE؛ وقواعد شبكة المزوّد NOT VERIFIED).
- أسماء sslip.io مش بتتحلّ للسيرفر (السيناريو 2).
- Volume اسمه caddy-data، اللي Caddy بيخزّن فيه الشهادات ومفاتيح الحساب، ضاع أو مش قابل للكتابة (السيناريو 17).
- حدود معدّل Let's Encrypt بسبب تكرار الطلبات الفاشلة أو تكرار الطلبات الجديدة (DOCUMENTED ONLY: سياسة المورّد؛ الحدود بالظبط لأسماء sslip.io مش مسجّلة هنا).
- ACME_EMAIL لسه فيه Placeholder المثال؛ و`deploy.sh up` بيرفض الـ Placeholders (VERIFIED IN CODE).

**أوامر التشخيص الآمنة.** D4 لكل مضيف، و`openssl s_client -connect app.<domain>:443 -servername app.<domain> </dev/null 2>/dev/null | openssl x509 -noout -checkend 1209600` (Exit status 0 معناه لسه صالحة بعد 14 يوم)؛ وD3؛ وD9 للـ Proxy؛ وD22 لقيمة دومين الـ Preview (D22 مابيطبعش ACME_EMAIL عن قصد).

**دليل التشخيص المتوقع.** لمضيف app، تاريخ not-after أبعد من 30 يوم طبيعي لشهادة Let's Encrypt بصلاحية 90 يوم اللي اتلاحظت؛ أما Issuer وتواريخ المضيفين التانيين فهي NOT VERIFIED LIVE، فاقرا مخرجات D4 لكل مضيف لوحده. Log الـ Proxy بيظهر خطوة ACME الفاشلة (نوع التحدي، خطأ DNS أو اتصال).

**الإجراء التصحيحي.** رجّع إمكانية الوصول للبورتات 80 و 443 وحلّ الأسماء الأول؛ Caddy بيعيد المحاولة لوحده (DOCUMENTED ONLY). لو لازم تعيد تشغيل الـ Proxy عشان يعيد المحاولة أسرع، استخدم F2 للـ Proxy بس. ماتمسحش الـ Volume بتاع caddy-data، وماتنسخش شهادات بنفسك، وماتعطّلش الـ HTTPS. لو في حدود معدّل، استنى؛ وماتطلبش شهادات جديدة كتير بتغيير الدومينات.

**مستوى الخطورة.** متوسط لعالي؛ شهادة منتهية بتخلي الـ Preview مش صالح للاستخدام في المتصفحات.

**التحقق من التعافي.** فحص D4 بيظهر تاريخ not-after جديد وsubjectAltName بيغطي المضيف، وأمر التحقق في D4 بيخرج بـ 0 مع verify=0، وD5 بينجح.

## 21. روابط الميديا مكسورة

**الأعراض.** صور الوجبات مش بتظهر في تطبيقات الويب؛ وطلبات الصور لمضيف الميديا بتفشل؛ والـ Uploads بتفشل.

**الأسباب المحتملة.**
- جذر مضيف الميديا بيرجّع 403 بالتصميم (السيناريو 5)؛ واستخدامه كاختبار مضلّل.
- الأوبجكت ناقص من الـ Bucket، أو مفتاح الأوبجكت في قاعدة البيانات مش مطابق للملف.
- الـ Bucket العام matbakhy-public مش موجود. `deploy.sh up` بيعمل الـ Buckets بأمر Best-effort فشله بيتتجاهل (`|| true`، VERIFIED IN CODE)، فالفشل صامت.
- الـ Base URL العام اللي الـ API بيولّده غلط: OBJECT_STORAGE_PUBLIC_BASE_URL لازم يكون https://media.<domain>/matbakhy-public والـ Endpoint بتاع الـ S3 Client هو https://media.<domain> (VERIFIED IN CODE).
- الـ Caller برّه فئة العميل (مضيف الميديا بيتبع قائمة وصول العميل، السيناريو 5).
- حاوية الـ Storage واقعة أو مش سليمة (السيناريو 7)، أو Volume storage-data تالف أو مليان.
- سياسة أمان المحتوى (Content Security Policy) لتطبيقات الويب بتسرد الصور من مضيف app نفسه وData URIs ومضيف الميديا بس (VERIFIED IN CODE)، لكن الـ Caddyfile بيبعتها كـ Content-Security-Policy-Report-Only. السياسة دي ممكن تبلّغ عن مخالفة، لكنها هي نفسها مابتقدرش تمنع صورة، فمابتفسّرش صورة مكسورة طول ما هي Report-Only.

**أوامر التشخيص الآمنة.** D1 على رابط صورة حقيقي مأخوذ من مصدر الصفحة أو الـ API (`https://media.<domain>/matbakhy-public/<object key>`)؛ وD2 لقراءة نوع المحتوى؛ وD8 وD9 للـ storage (أدمن مصرّح له)؛ وتبويب الشبكة في أدوات المطوّر بالمتصفح.

**دليل التشخيص المتوقع.** الصورة الشغّالة بترد 200 بنوع محتوى صورة. 403 مع XML وx-amz-request-id معناه إن بوابة S3 رفضت الطلب ده. 404 معناه مفيش أوبجكت كده. 403 "Not available" من Caddy معناه إن الـ Caller برّه فئة العميل.

**الإجراء التصحيحي.** لـ Bucket أو أوبجكت ناقص، أدمن مصرّح له يعيد تشغيل F1 (اللي بيعيد إنشاء الـ Buckets بشكل Idempotent) أو يعيد الرفع عن طريق التطبيق؛ وماتكتبش في Volume الـ Storage مباشرة. لرابط غلط، صلّح قيمة متغيّر البيئة عن طريق المالك وF2 بصيغة إعادة الإنشاء للـ api. لـ Storage واقع، السيناريو 7. استعادة تخزين الصور بتستخدم الأرشيف من F4 وتحتاج المالك.

**مستوى الخطورة.** منخفض لمتوسط؛ الصور بيانات تسويقية عامة في الـ Preview، لكن المستندات الخاصة موجودة في الـ Bucket الخاص وماينفعش تتكشف أبدًا.

**التحقق من التعافي.** رابط الصورة بيرجّع 200 بنوع محتوى صورة، والصفحة بتظهر الصورة، ورفع جديد على حساب اختبار بيظهر.

## 22. فشل الـ Smoke Tests

**الأعراض.** `deploy.sh smoke` أو D5 بيطبع `FAIL: ...` وبيخرج بـ 1؛ وJob الـ Deployment بيفشل في خطوة الـ Smoke.

**الأسباب المحتملة.** كل فحص Smoke له معنى (VERIFIED IN CODE، `deploy/web-preview/smoke.sh`):

| رسالة الفشل بتحتوي على | المعنى | روح لـ |
|---|---|---|
| does not serve the web app, أو lacks the header | مضيف app أو ops مش بيقدّم الـ Build أو هيدرز الـ Proxy ناقصة | 1، 4، 5 |
| build lacks the demo environment notice | Image الـ Front مش Build الـ Preview | 15 |
| API-001 not reachable, أو does not report the PREVIEW demo environment | الـ API واقع أو بيبلّغ عن بيئة تانية | 7، 8 |
| discovery not reachable | Endpoint الوجبات بيفشل | 8، 9 |
| answered X without a session (expected 401 or 403) | Endpoint محمي مفتوح أو بيدّي خطأ: Regression أمني يتعامل معاه على إنه عاجل | 4، 5 |
| actuator must not be published, أو metrics must not be published | الـ Proxy بينشر بورت الإدارة | 4 |
| identity issuer is ... | عدم تطابق Issuer | 11 |
| id.<domain>/... must not be published | الـ Realm الـ master أو Console الأدمن قابل للوصول على مضيف هوية العميل | 4 |
| kc-admin ... must serve the console or answer 404 | مضيف الأدمن بيرد بكود غير متوقع؛ الكود 000 بيحذّر بس | 6، 12 |
| unexpected sign-in redirect | مشكلة Redirect تسجيل الدخول | 10 |

فيه سببين بيئيين: الجهاز اللي بيشغّل الـ Smoke Test مش في الـ Allow-list (استخدم SMOKE_OPS=skip لمضيف ops؛ وللمضيفين التانيين شغّل من جهاز مسموح له)، أو الـ Stack عمره ثواني بس. إعادة المحاولة بتختلف من فحص للتاني (VERIFIED IN CODE): الطلبات اللي بتمر عبر الـ Helper اسمه `get` في السكريبت (طلبات الصفحة والـ Script والإعدادات والوجبات والـ Discovery) بتتعاد لحد 6 مرات كل 5 ثواني؛ أما فحوص كود الحالة (`code`) وطلب هيدرز الأمان وطلب الـ Redirect الأخير لتسجيل الدخول فبتتبعت مرة واحدة من غير إعادة محاولة؛ وفحص kc-admin بيستخدم حلقته الخاصة لحد 18 محاولة كل 5 ثواني، يعني حوالي 90 ثانية. فلو فحص من غير إعادة محاولة فشل مرة واحدة ممكن يبقى فشل مؤقت، فشغّل D5 مرة كمان قبل ما تعتبره عطل مؤكد؛ والفشل المتكرر بنفس الشكل حقيقي.

**أوامر التشخيص الآمنة.** D5 تاني، مرة واحدة، لاستبعاد Race عند البدء في الفحوص اللي مابتتعادش؛ وD1 على الرابط بالظبط في الرسالة؛ وبعدها أوامر السيناريو اللي في الجدول.

**دليل التشخيص المتوقع.** الرسالة بتسمّي أول فحص فشل؛ والسكريبت بيقف عند أول فشل.

**الإجراء التصحيحي.** اتبع السيناريو اللي في الجدول. ماتعدّلش `smoke.sh` عشان ينجح، وماتتخطاش فحص أبدًا؛ الاختبارات ماينفعش تتضعف عشان نجيب نتيجة ناجحة (قاعدة المشروع). على السيرفر، Smoke Test فاشل بعد `up` بيخلي سكريبت الـ Deploy عن بُعد يستعيد ملف .env السابق ويخرج بفشل؛ الحاويات مابتتعملهاش إعادة إنشاء وقاعدة البيانات مابتتعملهاش Rollback (السيناريوهات 14 و 15).

**مستوى الخطورة.** متوسط؛ بعض حالات الفشل بتدل على كشف Endpoints محمية.

**التحقق من التعافي.** D5 بيطبع سطر PASS الأخير.

## 23. Deployment فاشل محتاج Rollback

**الأعراض.** Deployment سيّب الـ Preview مش سليم، بأخطاء مابتتصلّحش بإعادة المحاولة، أو إصدار جديد فيه عيب؛ والمالك قرر يرجع.

**الأسباب المحتملة.** تغيير تطبيق وحش، أو Migration فاشل أو كاسر (السيناريو 16)، أو مشكلة Image (السيناريوهات 13 و 15)، أو حالة مختلطة بعد تشغيلة فاشلة.

**أوامر التشخيص الآمنة.** D7 وD15 وD16 (اقرا الفقرة اللي بعدها قبل ما تختار N)، وD12 وD9 وD23.

**اختيار هدف الـ Rollback.** `rollback-plan [N]` بيعدّ الإصدارات الناجحة المسجّلة بس، لأن `record_release` بيشتغل بعد ما الـ Stack يبقى سليم (VERIFIED IN CODE). حدّد الأول إذا كان الـ Deployment الفاشل اتسجّل ولا لأ، بمقارنة الـ Commit في آخر صف في D15 بالـ Commit الفاشل من D23. (أ) لو الـ Deployment فشل قبل ما الـ Stack يبقى سليم، مابيتسجّلش وآخر صف هو آخر إصدار ناجح؛ وN=1 هيتخطاه، ولو فيه صف واحد بس `rollback-plan` بيرفض. (ب) لو فشل بعد ما بقى سليم (مثلًا في `smoke`)، يبقى هو آخر صف وN=1 بيختار الإصدار اللي قبله. في الحالة (أ) مفيش خيار في `rollback-plan` يختار آخر صف؛ السكريبت موجود بصيغة N>=1 بس. الموجود: آخر صف في D15 فيه الـ Commit وImage الـ API وImage الـ Front اللي تحطها في .env بإيدك (الحقول من 2 لـ 4)، وحقل الـ Schema بعد (الحقل 6) تقارنه بـ D12. الناقص: حكم من السكريبت لصف زي ده. الطريق ده مقروء من الكود ولسه ماتجرّبش عمليًا (NOT VERIFIED)؛ وإضافة خيار للسكريبت تغيير في الكود عن طريق Pull Request وقرار للمالك.

**دليل التشخيص المتوقع.** D16 بيطبع الـ Commit الهدف وقيم .env التلاتة وحكم. الحكم "schema has not moved": Rollback للـ Images بس بيحافظ على البيانات. الحكم "schema moved": Rollback للـ Images بس آمن فقط لو كل Migration من وقتها Expand-only؛ وإلا فالنسخة الاحتياطية المسجّلة قبل أول Deployment طبّقهم هي الطريق (VERIFIED IN CODE، `rollback_plan` في `deploy/web-preview/deploy.sh`).

**جدول القرار** (DOCUMENTED ONLY: [الـ Deployment والـ Rollback للـ Staging](../../operations/11-staging-deployment-and-rollback.md)، القسم 4.2؛ والـ Helper اللي بيطبع الحكم VERIFIED IN CODE):

| الموقف | الإجراء |
|---|---|
| الإصدار الجديد وحش؛ ومفيش Migration من آخر إصدار سليم | Rollback للـ Images بس: اضبط القيم التلاتة من D16 وشغّل F3. البيانات بتفضل |
| الـ Migrations من وقتها كلها Expand-only | Rollback للـ Images بس؛ الـ API الأقدم بيتجاهل الـ Migrations اللي ميعرفهاش. شغّل الـ Smoke Test بعدها |
| Migration فيه تشديد (Tightening) | جرّب الـ Image الأقدم على نسخة الأول؛ لو الكتابات فشلت، استخدم مسار الاستعادة |
| Migration كاسر، أو البيانات اتضرّت | استعد النسخة الاحتياطية قبل الـ Deployment (F11). البيانات المكتوبة بعد النسخة دي بتضيع؛ بلّغ الـ Testers |
| الـ Deployment فشل في نصه | D7، اقرا Logs الـ api وidentity، وبعدين صلّح للأمام أو ارجع بالصفوف اللي فوق. `up` ممكن يتكرر |

**الإجراء التصحيحي.** الـ Rollback قرار للمالك. سجّل الـ Commit الفاشل والـ Commit الهدف والسبب. خد نسخة احتياطية جديدة الأول لو قاعدة البيانات لسه مقروءة (F4)، إلا لو القرص أو حالة قاعدة البيانات بتخلي ده مش آمن. وبعدها نفّذ F3، أو F11 لمسار الاستعادة (وقّف الـ API الأول؛ بدّل قواعد البيانات وانت متأكد إن مفيش حد متصل؛ واحتفظ بقاعدة البيانات الفاشلة لحد ما السبب يتفهم). لاحظ إن معالجة الفشل الخاصة بالـ Pipeline بتستعيد ملف .env لما `up` أو `smoke` يفشل في السكريبت عن بُعد بس (مش لما خطوة الـ Admin Console اللي بعدها تفشل)، ومابتعملش إعادة إنشاء لأي حاوية ولا Rollback تلقائي لقاعدة البيانات (VERIFIED IN CODE، `.github/workflows/deploy-preview.yml`). جرّب الاستعادة على سيرفر جديد معزول؛ ماتجرّبهاش على الـ Preview الحي الأول أبدًا ([النسخ الاحتياطي والاستعادة والتعافي من الكوارث](backup-restore-disaster-recovery-ar.md)، [دليل إعادة البناء](server-rebuild-guide-ar.md)).

**مستوى الخطورة.** عالي لأي Rollback بيلمس قاعدة البيانات؛ متوسط لـ Rollback الـ Images بس.

**التحقق من التعافي.** D7 بيظهر الـ Commit الهدف والـ Schema؛ والـ api سليم؛ و`./deploy.sh smoke` بينجح؛ وتدفق أوردر ببيانات تجريبية غير حقيقية (Synthetic Data) في ويب العمليات أو حالات قبول المالك لسه بيشتغل؛ والحادثة (السبب، والـ Commit، والنسخة الاحتياطية المستخدمة، وفقدان البيانات لو حصل) متسجّلة.

## قيود معروفة وفجوات في التوثيق

- حالة Docker أثناء التشغيل (الحاويات والـ Images والـ Volumes والحدود) NOT VERIFIED LIVE لأن حساب aiagent مش قادر يقرا الـ Docker socket. كل تشخيص قائم على Docker في الصفحة دي NOT VERIFIED LIVE لحد ما أدمن مصرّح له يشغّله ([التحقق للقراءة فقط من الأدمن](admin-readonly-verification-ar.md)).
- القيم الحية لـ GENERATED_DIR وBACKUP_DIR وCOMPOSE_OVERRIDE NOT VERIFIED LIVE. الافتراضي في compose و`.env.example` لسه بيسمّوا /srv/matbakhy-preview/generated، وده بيطابق نسخة قديمة موجودة على الجهاز، بينما الـ Deployment في /srv/matbakhy-staging (فجوة توثيق: أكّد بـ D22).
- وصف المطلوب للصفحة دي بيقول 22 سيناريو بس بيسرد 23؛ كل الـ 23 موجودين.
- `compose.4gb.yaml`، ومستند تجهيز Hetzner ([تجهيز الـ Staging على Hetzner](../../operations/09-staging-hetzner-preparation.md)) وأجزاء من مستندات التشغيل التانية بتوصف جهاز Hetzner مقترح وهي OUTDATED / INCORRECT لجهاز Contabo (8 vCPU، حوالي 23 GiB RAM). لما تتعارض مع الصفحة دي، الصفحة دي و[جرد السيرفر](contabo-server-inventory-ar.md) هما المرجع.
- دليل حل المشاكل في التشغيل بيقول إن النسخ الاحتياطية في مجلد backups جوّه مجلد الـ Deployment بينما `deploy.sh` وcompose.yaml الافتراضي عندهم BACKUP_DIR هو /srv/matbakhy-backups (VERIFIED IN CODE)؛ وده تعارض في المستند الأقدم. والـ Runbook التشغيلي نفسه بيقول إنه ماتجرّبش على بيئة منشورة.
- قواعد الـ Firewall (ufw)، وإعدادات sshd الفعّالة، وسياسة sudo، وجداول Cron بتاعة المستخدمين التانيين، والـ Docker data root، وتجديد الشهادة نفسه NOT VERIFIED LIVE؛ ومحتاجين root أو وصول Docker.
- حساب SSH اللي بيعمل الـ Deploy (STAGING_SSH_USER) هو Secret في الـ Repository وهو NOT VERIFIED.
- التنبيهات بتروح لـ Sink اختبار محلي بس لحد ما المالك يوافق على مستلم حقيقي (DOCUMENTED ONLY، الـ Runbook التشغيلي)، فتنبيه بيشتغل مابيظهرش إلا لشخص بيقرا Grafana أو Log الـ Sink.
- ولا أمر تصحيحي منهم اتشغّل لأجل الصفحة دي؛ أعمدة "النتيجة المتوقعة" بتاعتهم جاية من السكريبتات، مش من تجربة.
