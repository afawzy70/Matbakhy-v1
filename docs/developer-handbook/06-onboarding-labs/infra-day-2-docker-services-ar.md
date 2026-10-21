# البنية التحتية اليوم 2 - خدمات Docker Compose والشبكات والـ Volumes الدائمة

> **Owner role:** Infrastructure and deployment
> **Status:** REFERENCE — Lab لقراءة تعريف الـ Compose بتاع الـ Preview؛ وفحص Docker على السيرفر الحي بيعمله أدمن مصرّح له بس.
> **Language / اللغة:** [English](infra-day-2-docker-services.md) · العربية

## مفتاح الأدلة

| الوسم | المعنى |
|---|---|
| VERIFIED LIVE | اتلاحظ يوم 2026-10-21 على Contabo VPS بواسطة المستخدم aiagent من غير صلاحيات مرتفعة (شوف [جرد السيرفر](../08-infrastructure/contabo-server-inventory-ar.md)). |
| VERIFIED IN CODE | اتقرا في الـ Repository ده (الملف مذكور). بيبيّن النية، مش بالضرورة اللي شغّال فعلًا. |
| DOCUMENTED ONLY | مستند بيقول كده؛ ومفيش حاجة بتأكده. |
| OUTDATED / INCORRECT | مكتوب للـ Host المقترح على Hetzner أو اتلغى بحاجة أحدث. |
| NOT VERIFIED | مااتلاحظش. أوامر الأدمن موجودة في [التحقق للقراءة فقط بواسطة الأدمن](../08-infrastructure/admin-readonly-verification-ar.md). |

إعدادات تشغيل Docker للـ Preview الحي (الـ Containers الشغّالة، والـ Images المستخدمة، والـ Volumes على القرص، وحدود الموارد المطبّقة، ومحتوى .env) هي NOT VERIFIED LIVE. الحساب من غير صلاحيات مرتفعة مش قادر يقرا الـ Docker socket. عشان كده الـ Lab ده بيشتغل من ملف الـ Compose في الـ Repository.

## الأهداف

- اقرا `deploy/web-preview/compose.yaml` وسجّل لكل خدمة مصدر الـ Image بتاعها، وشبكتها، والـ Volumes بتاعتها، والبورتات المنشورة.
- اشرح الشبكات التلاتة (edge وops وinternal) وليه الـ Database ومزوّد الهوية مالهمش مسار للإنترنت.
- اربط كل Volume مسمّى بالبيانات اللي بيحملها وقول إيه اللي هيضيع لو الـ Volume ده اتفقد.
- اشرح ليه الـ Images متثبّتة (Pinned) وليه مفيش حاجة بتتبني على السيرفر.
- اعرف أنهي فحوصات على السيرفر الحي محتاجة أدمن، وأنهي أوامر آمنة للقراءة في الـ Repository.

## المتطلبات المسبقة

- اليوم 1 خلص: [البنية التحتية اليوم 1](infra-day-1-topology-accounts-ar.md).
- اقرا أقسام [معمارية التشغيل الخاصة بالـ Preview](../08-infrastructure/runtime-architecture-ar.md) الخاصة بالـ Topology بتاع الـ Containers وحفظ بيانات الـ Database وتخزين الميديا.
- Docker مش مطلوب للمهام اللي على الـ Repository بس. ماتسطّبش ولا تشغّل أي حاجة على الـ Contabo VPS.

## قواعد الـ Lab ده

- كل المهام بتقرا ملفات في الـ Repository على اللابتوب بتاعك. مفيش حاجة بتتشغّل.
- ماتشغّلش `docker` ولا `docker compose config` (بيطبع قيم الأسرار من .env بعد ما يعمل لها Expand) ولا أي أمر على السيرفر الحي. الفحص على السيرفر الحي مذكور في [صفحة الأدمن](../08-infrastructure/admin-readonly-verification-ar.md) وبيشغّله أدمن مصرّح له بس.
- ماتقراش أي ملف .env. الملف `deploy/web-preview/.env.example` هو القالب العام من غير قيم سرية، وقراءته مسموحة.

## المهام الموجّهة

### المهمة 1. اعمل قائمة بالخدمات

| البند | التفاصيل |
|---|---|
| الأمر | `grep -nE '^  [a-z-]+:$' deploy/web-preview/compose.yaml` |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | حسابك انت |
| النوع | للقراءة فقط |
| المتطلب المسبق | الـ Repository اتعمل له Checkout |
| النتيجة المتوقعة | أسماء الخدمات (وممكن تظهر معاها مفاتيح المستوى الأعلى زي networks وvolumes): proxy, postgres, identity, storage, api, loki, alloy, prometheus, alertmanager, alert-sink, node-exporter, grafana |
| التحقق | عدّ 12 خدمة. اسم المشروع في السطر `name: matbakhy-preview` قرب أول الملف |
| فشل محتمل | الناتج فيه مفاتيح تانية بمسافتين؛ تجاهل المفاتيح اللي مش خدمات |

املا الجدول ده من الملف (VERIFIED IN CODE: `deploy/web-preview/compose.yaml`). الصفوف الأولى معطاة كدليل؛ كمّل الباقي بنفسك.

| الخدمة | مصدر الـ Image | الشبكات | الـ Volume الدائم | البورت المنشور |
|---|---|---|---|---|
| proxy | FRONT_IMAGE من .env (Caddy وجوّاه نسختَي الويب المبنيّتين بالـ CI) | edge, internal | caddy-data, caddy-config | 80 و 443 على كل العناوين |
| postgres | postgres:16.15-alpine متثبّت بالـ Digest | internal | postgres-data | مفيش |
| identity | keycloak 26.8.0 متثبّت بالـ Digest | internal | كمّل من الملف | مفيش |
| storage | SeaweedFS متثبّت بالـ Digest | internal | storage-data | مفيش |
| api | API_IMAGE من .env (بيتبني بواسطة CI) | internal | كمّل من الملف | مفيش |
| grafana | grafana 12.1.1 متثبّت بالـ Digest | internal, ops | grafana-data | 127.0.0.1:3000 بس |
| الستة التانيين | مكونات المراقبة (Observability) | كمّل من الملف | كمّل من الملف | مفيش |

### المهمة 2. افهم الشبكات التلاتة

| البند | التفاصيل |
|---|---|
| الأمر | `sed -n '/^networks:/,/^volumes:/p' deploy/web-preview/compose.yaml` |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | حسابك انت |
| النوع | للقراءة فقط |
| النتيجة المتوقعة | edge وops وinternal؛ وinternal بس هي اللي فيها الإعداد `internal: true` |
| التحقق | جاوب: أنهي خدمة على edge؟ وأنهي على ops؟ وإيه اللي بيعمله `internal: true`؟ |

الإجابات المتوقعة: الـ proxy على edge وinternal؛ وgrafana على ops وinternal؛ وشبكة Compose معلّمة internal مالهاش مسار خارج للإنترنت ومش ممكن توصلها من برّه Docker host (سلوك Docker القياسي، DOCUMENTED ONLY). التعليق في الملف بيذكر النية: الـ proxy بس هو اللي منشور على الشبكة (وGrafana بيربط كمان على عنوان الـ Loopback بتاع الـ Host 127.0.0.1:3000، وده مش متاح من الإنترنت). هل شبكات Docker على السيرفر الحي بتطابق ده؟ NOT VERIFIED LIVE؛ والحقائق اللي اتلاحظت بتبيّن بس إن تلات شبكات bridge موجودة على الـ Host (VERIFIED LIVE: واجهات br-* بالنطاقات 172.18 و 172.19 و 172.20).

### المهمة 3. اربط الـ Volumes بالبيانات وأثر فقدانها

| البند | التفاصيل |
|---|---|
| الأمر | `sed -n '/^volumes:/,$p' deploy/web-preview/compose.yaml` و`grep -nE "postgres-data\|storage-data\|caddy-data" deploy/web-preview/compose.yaml` |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | حسابك انت |
| النوع | للقراءة فقط |
| النتيجة المتوقعة | عشرة Volumes مسمّاة |
| التحقق | كمّل الجدول اللي تحت؛ وقارن بأقسام حفظ البيانات بشكل دائم (Persistence) في [معمارية التشغيل الخاصة بالـ Preview](../08-infrastructure/runtime-architecture-ar.md) |

| الـ Volume | المخزّن فيه إيه (من مسار الـ Mount) | اللي بيضيع لو اتفقد |
|---|---|---|
| postgres-data | مجلد بيانات PostgreSQL | كل بيانات التطبيق: الأوردرات والحسابات والكتالوج. ممكن تتسترجع من نسخة احتياطية متحقق منها بس |
| storage-data | مجلد بيانات SeaweedFS | الصور المرفوعة. ممكن تتسترجع من أرشيف الـ Storage اللي بيعمله أمر النسخ الاحتياطي بس |
| caddy-data | مجلد بيانات Caddy، اللي بتتحفظ فيه الشهادات والمفاتيح (DOCUMENTED ONLY: الوضع المعتاد في Caddy) | الشهادات؛ وCaddy بيطلب شهادات جديدة عند البدء، في حدود معدّل الطلبات المسموح بيه عند Let's Encrypt (Rate Limits)، شوف [الـ DNS والـ HTTPS](../08-infrastructure/dns-https-sslip-io-ar.md) |
| loki-data, prometheus-data, alertmanager-data, alloy-data, alert-sink-data, grafana-data | حالة الـ Logs والـ Metrics والـ Dashboards | التاريخ بس؛ والتطبيق بيفضل شغّال |

مكان الـ Volumes على قرص الـ VPS وأحجامها NOT VERIFIED LIVE؛ والأدمن يقدر يعرفهم بأوامر موجودة في صفحة الأدمن.

### المهمة 4. أنهي ملفات بتتعمل لها Mount من مجلد الـ Host

| البند | التفاصيل |
|---|---|
| الأمر | `grep -nE '^\s+- (\./\|\.\./\|/\|\$\{)' deploy/web-preview/compose.yaml` |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | حسابك انت |
| النوع | للقراءة فقط |
| النتيجة المتوقعة | Bind mounts: ملفات إعدادات المراقبة، و`infra/db/reconcile.sql`، ومجلد الـ Flags اللي اتولّد بأمر render ومتعمل له Mount للقراءة بس في الـ proxy، ومسارات الـ Host اللي بيستخدمها alloy وnode-exporter، وكام حاجة تانية |
| التحقق | لكل Mount، قول هل هو للقراءة بس (اللاحقة :ro). واشرح ليه الـ proxy بياخد مجلد الـ Flags للقراءة بس |

ليه ده مهم: ملف الـ Flag في مجلد الـ Flags هو اللي بيتحكم هل الـ Host بتاع كونسول أدمن Keycloak هيرد ولا لأ (شوف التعليقات في `deploy/web-preview/Caddyfile`؛ والملف بيتعمل ويتشال بأمر `deploy.sh admin-console`). اليوم 3 بيغطي التوجيه. القيمة الافتراضية لمتغير المجلد في الملف بتشاور على /srv/matbakhy-preview/generated، النسخة القديمة، فالقيمة الحقيقية جاية من .env بتاع السيرفر، وده NOT VERIFIED.

### المهمة 5. حدود الموارد وملف الـ 4 GB

| البند | التفاصيل |
|---|---|
| الأمر | `grep -nE 'mem_limit\|cpus' deploy/web-preview/compose.yaml` وبعدين `sed -n 1,8p deploy/web-preview/compose.4gb.yaml` |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | حسابك انت |
| النوع | للقراءة فقط |
| النتيجة المتوقعة | حدود ذاكرة وCPU لكل خدمة في الملف الرئيسي، وملف تاني الـ Header بتاعه بيقول إنه مكتوب لـ Host بتاع Hetzner بـ 2 vCPU و 4 GB |
| التحقق | جاوب: هل compose.4gb.yaml مستخدم على Host بتاع Contabo؟ الإجابة الصح: NOT VERIFIED. هو بيتستخدم بس لو .env بتاع السيرفر بيظبط COMPOSE_OVERRIDE (VERIFIED IN CODE: تعليق في الملف و`deploy/web-preview/deploy.sh`). الـ Host الحي فيه 8 vCPU وحوالي 23 GiB RAM (VERIFIED LIVE)، فملف الـ 4 GB تاريخي (OUTDATED / INCORRECT بالنسبة لحجم الـ Host) إلا لو أدمن أكد إنه متظبط |

### المهمة 6. الـ Images المتثبّتة (Pinned) ومفيش Build على السيرفر

| البند | التفاصيل |
|---|---|
| الأمر | `grep -n "image:" deploy/web-preview/compose.yaml` |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | حسابك انت |
| النوع | للقراءة فقط |
| النتيجة المتوقعة | الـ Images بتاعة الأطراف التانية متثبّتة بـ sha256 Digest (ومعظمها عليه رقم إصدار كمان، وImage الـ storage متثبّتة بالـ Digest بس)؛ والـ Image-ين بتوعنا جايين من متغيرات |
| التحقق | اشرح إزاي API_IMAGE وFRONT_IMAGE بيجيلهم قيمهم. الإجابة: الـ Workflow بتاع الـ Deployment بيكتبهم في .env بتاع السيرفر بعد ما الـ CI يبني الـ Images وينشرها، شوف [مسار الـ Deployment](../08-infrastructure/deployment-pipeline-ar.md) (VERIFIED IN CODE: `.github/workflows/deploy-preview.yml`) |

### المهمة 7. نظرة المشغّل (للقراءة فقط)

| البند | التفاصيل |
|---|---|
| الأمر | `sed -n 1,20p deploy/web-preview/deploy.sh` |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | حسابك انت |
| النوع | للقراءة فقط (قراءة الملف) |
| النتيجة المتوقعة | قائمة الأوامر الفرعية: init-secrets, render, up, seed, smoke, backup, restore-db, prune-qa, status, rollback-plan, admin-console, e2e-reset |
| التحقق | علّم أنهي أوامر فرعية بتقرا بس (status وrollback-plan بيقولوا كده في الـ Header؛ وprune-qa من غير --apply بيعرض بس) وأنهي بتغيّر الحالة (init-secrets بيعمل .env، وrender بيكتب الإعدادات والأسرار في GENERATED_DIR، وup وseed وbackup وrestore-db وe2e-reset وprune-qa مع --apply وadmin-console). أما smoke فبيبعت طلبات HTTPS على الـ Hosts العامة ومابيعملش حاجة جديدة، لكنه معمول يشتغل على السيرفر وممكن يفشل بسبب سياسة الوصول. تشغيل أي واحد منهم شغل مشغّل مصرّح له على السيرفر، ومش جزء من الـ Lab ده أبدًا |

التسليم لأدمن: عشان تشوف قائمة الـ Containers والـ Images والـ Volumes الحقيقية، اطلب من أدمن مصرّح له يشغّل أوامر Docker الموجودة في [صفحة الأدمن](../08-infrastructure/admin-readonly-verification-ar.md) ويرجّع ناتج اتشالت منه البيانات الحساسة (Sanitised). ماتطلبش أبدًا ناتج `docker inspect` أو `docker compose config` الخام، لأنهم بيطبعوا الأسرار.

## نقاط التحقق

| نقطة التحقق | بتنجح لما |
|---|---|
| جدول الخدمات | كل الـ 12 خدمة فيها مصدر الـ Image والشبكة والـ Volume والبورت متملّيين، وكل مدخل موسوم VERIFIED IN CODE |
| الشبكات | تقدر تشرح edge وops وinternal في جملتين لكل واحدة |
| الـ Volumes | كل Volume من العشرة ليه جملة بتوصف أثر فقدانه |
| الحالة الحية | كل جملة عن الـ Containers الشغّالة معلّمة NOT VERIFIED LIVE |

## معايير الإكمال

- [ ] كتبت الخدمات والشبكات والـ Volumes من ملف الـ Compose من غير ما أشغّل Docker.
- [ ] أقدر أقول أنهي خدمات بتنشر بورت وليه الـ proxy بس هو المتاح للإنترنت.
- [ ] أقدر أسمّي الـ Volumes الاتنين اللي فقدانهم معناه فقدان بيانات، وإزاي بيتاخد منهم نسخ احتياطي.
- [ ] اتعاملت مع أي معلومة عن حالة Docker على السيرفر الحي على إنها NOT VERIFIED LIVE.
- [ ] ماقريتش أي ملف .env ولا لمست السيرفر.

## اقرا إيه بعد كده

[معمارية التشغيل الخاصة بالـ Preview](../08-infrastructure/runtime-architecture-ar.md)، [النسخ الاحتياطي والاسترجاع والتعافي من الكوارث](../08-infrastructure/backup-restore-disaster-recovery-ar.md)، [التحقق للقراءة فقط بواسطة الأدمن](../08-infrastructure/admin-readonly-verification-ar.md).

التالي: [البنية التحتية اليوم 3](infra-day-3-dns-https-keycloak-ar.md).

## القيود المعروفة وفجوات التوثيق

- مفيش حالة Docker على الـ Host الحي اتلاحظت؛ وكل حاجة عن الـ Containers جاية من الـ Repository.
- هل الـ Host الحي بيستخدم ملف الـ 4 GB، وإيه GENERATED_DIR الحقيقي، وأنهي Image Digests شغّالة: كل ده NOT VERIFIED.
- موعد النسخ الاحتياطي ومدى حداثته على الـ Host الحي NOT VERIFIED؛ ومفيش مهمة مجدولة للنسخ الاحتياطي بتاعة Matbakhy كانت ظاهرة للحساب من غير صلاحيات مرتفعة.
