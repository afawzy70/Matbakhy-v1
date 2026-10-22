# البنية التحتية اليوم 5 - تمرين إعادة البناء المعزول وتقييم النسخ الاحتياطي والتعافي

> **Owner role:** Infrastructure and deployment
> **Status:** REFERENCE — Lab بيجهّز تمرين على سيرفر نضيف معزول (أو الـ Stack المحلي) وتقييم للنسخ الاحتياطي والتعافي؛ كتّاب التوثيق لسه ماشغّلوش التمرين ده.
> **Language / اللغة:** [English](infra-day-5-rebuild-recovery.md) · العربية

## مفتاح الأدلة

| الوسم | المعنى |
|---|---|
| VERIFIED LIVE | اتلاحظ يوم 2026-10-21 على الـ Contabo VPS بواسطة المستخدم aiagent من غير صلاحيات مرتفعة (شوف [جرد السيرفر](../08-infrastructure/contabo-server-inventory-ar.md)). |
| VERIFIED IN CODE | اتقرا في الـ Repository ده (الملف مذكور). بيبيّن النية، مش بالضرورة اللي شغّال فعلًا. |
| DOCUMENTED ONLY | مستند أو السلوك المعتاد لأداة بيقول كده؛ ومفيش حاجة أكّدته هنا. |
| OUTDATED / INCORRECT | مكتوب لسيرفر Hetzner المقترح أو اتلغى بحاجة أحدث. |
| NOT VERIFIED | مااتلاحظش. |

## بيان مهم: التمرين ده لسه ماتشغّلش

كتّاب التوثيق ماشغّلوش تمرين اليوم 5. مااتبناش سيرفر نضيف، ولا اتاخدت نسخة احتياطية أو اتسترجعت، ولا اتقاس زمن تعافي أثناء كتابة الـ Lab ده. كل نتيجة متوقعة تحت مستنتجة من قراءة سكريبتات الـ Repository (VERIFIED IN CODE) أو من سلوك الأدوات العام (DOCUMENTED ONLY). لما تشغّله، سجّل النتايج اللي انت لاحظتها بنفسك وعلّم أي حاجة بتتخطّاها بـ NOT RUN.

## الأهداف

- تجهّز بيئة معزولة يتبني فيها Stack شبه الـ Preview من الصفر ويتمسح من غير أي خطر على الـ Preview الشغّال.
- تتمرّن على إعادة إنشاء حسابات Linux والمجموعات وملكية المجلدات والصلاحيات بنفس النمط المسجّل لسيرفر Contabo، على سيرفر مؤقت (Throw-away) بس.
- تتمرّن على نسخة احتياطية منطقية (Logical Backup) لقاعدة البيانات واسترجاع متحقق منه في قاعدة بيانات جديدة فاضية باستخدام سكريبتات الـ Repository.
- تقيّم وضع النسخ الاحتياطي والتعافي الحقيقي للـ Preview: إيه الموجود في الكود، وإيه اللي NOT VERIFIED على السيرفر الحي، وإيه اللي لازم الأدمن يتأكد منه.

## المتطلبات المسبقة

- الأيام من 1 إلى 4 خلصت: [اليوم 1](infra-day-1-topology-accounts-ar.md) و[اليوم 2](infra-day-2-docker-services-ar.md) و[اليوم 3](infra-day-3-dns-https-keycloak-ar.md) و[اليوم 4](infra-day-4-cicd-operations-ar.md).
- اقرا [دليل إعادة بناء السيرفر](../08-infrastructure/server-rebuild-guide-ar.md) و[النسخ الاحتياطي والاسترجاع والتعافي من الكوارث](../08-infrastructure/backup-restore-disaster-recovery-ar.md).
- اختار خيار عزل واحد (تحت) وخلّي معاك تصريح مكتوب بيه من مالك البنية التحتية. الـ VPS المؤقت المدفوع بيصرف فلوس ومحتاج التصريح ده؛ الخيار المحلي لأ.

## قواعد العزل (إلزامية)

1. ماتشغّلش أي أمر من التمرين على الـ Preview الشغّال، ولا الدومين بتاعه 167-86-90-174.sslip.io، ولا الـ IP بتاعه، ولا الـ Contabo VPS.
2. سيرفر أو Stack التمرين لازم يكون له دومينه الخاص أو اسم محلي بس، وSecrets خاصة بيه متولّدة للتمرين، وبيانات تجريبية غير حقيقية (Synthetic Data) بس.
3. ماتعيدش استخدام أي Secret أو مفتاح SSH أو ملف .env أو نسخة احتياطية من الـ Preview الشغّال. ماتنسخش نسخة احتياطية لبيانات حقيقية جوّه التمرين (النسخ الاحتياطية فيها بيانات شخصية، شوف الـ Header بتاع `scripts/db-backup.sh`).
4. ماتوجّهش التمرين على Secrets الـ Repository دي: STAGING_SSH_HOST أو STAGING_SSH_USER أو STAGING_SSH_KEY أو STAGING_SSH_HOST_PUBKEY. ماتشغّلش Workflow الـ Deployment للتمرين.
5. دمّر السيرفر المؤقت أو الـ Stack في الآخر وسجّل إنك دمّرته.
6. لازم التمرين مايخلّيش الـ Automation بتاع الـ Preview الحي يتوجّه لسيرفر تاني. الـ Workflow بتاع الـ Deployment والـ Workflow بتاع تجهيز الـ Owner E2E في الـ Repository ده بيستخدموا Environment اسمها `preview` وSecrets بتاعتها. ماتضبطش Secrets في الـ Environment دي وماتعدّلش الـ Workflows دي للتمرين، حتى لو خطوة في إعادة البناء بتتكلم عن Secrets أو Workflows في الـ Repository. التمرين اللي محتاج Workflow الـ Deployment بيستخدم Repository (أو Fork) منفصل بـ Environment وSecrets وConcurrency Group بتوعه هو. تحويل الـ Preview الحقيقي لسيرفر معاد بناؤه (Cutover) إجراء منفصل ومحتاج تصريح صريح: لسه ماتجرّبش عمليًا ومش موصوف في الـ Lab ده (NOT VERIFIED؛ ومحتاج قرار من الـ Owner).
7. المتدرّب مابيستخدمش sudo ولا Docker ولا مستخدم تاني على السيرفر الحي أبدًا. أما على سيرفر التمرين فالمتدرّب هو أدمن الجهاز المؤقت ده بس.

## خيارات العزل

| الخيار | فين | محتاج | بيغطي | مابيغطيش |
|---|---|---|---|---|
| A. Stack محلي | اللابتوب بتاعك، Docker | Docker، والـ Repository | تشغيل الخدمات، وسكريبتات النسخ الاحتياطي والاسترجاع لقاعدة البيانات، والـ Smoke | حسابات Linux، وSSH، والـ DNS العام، والشهادات، وخصوصيات Contabo |
| B. VPS مؤقت | سيرفر جديد مصرّح لك تنشئه وتمسحه، ومعاه دومين خاص بيه | سيرفر Ubuntu 24.04 نضيف، ودومين للاختبار، وتصريح المالك | إعادة البناء الكاملة من [دليل إعادة بناء السيرفر](../08-infrastructure/server-rebuild-guide-ar.md) بما فيها الحسابات والـ Firewall والـ DNS والشهادة والـ Deployment | ولا حاجة عن الحالة غير المعروفة للسيرفر الحي |

الخيار A: الـ Repository فيه `scripts/dev.sh` بأوامر فرعية doctor وup وsmoke وdown وreset وverify (VERIFIED IN CODE). الأمر `scripts/dev.sh verify` بيشغّل doctor وup وsmoke في مشروع compose مؤقت معزول وبينضّف بعد كده. ده Stack تطوير محلي، مش Stack الـ Preview: مابيستخدمش `deploy/web-preview/compose.yaml`.

## المهام الموجّهة

### المهمة 1. افحص المتطلبات المحلية (الخيار A)

| البند | التفاصيل |
|---|---|
| الأمر | `scripts/dev.sh doctor` |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | حسابك انت |
| النوع | فحوصات للقراءة فقط (بتتأكد إن الأدوات موجودة بس) |
| المتطلب المسبق | الـ Repository اتعمل له Checkout |
| النتيجة المتوقعة | سطر "ok: ..." لكل متطلب، أو "MISSING: ..." مع تلميح (VERIFIED IN CODE: الدالة doctor في `scripts/dev.sh`) |
| التحقق | كود الخروج 0 معناه إن كل المتطلبات موجودة |
| فشل محتمل | MISSING: docker daemon. شغّل Docker وأعد تشغيل الأمر. ماتسطّبش أي حاجة على سيرفر |

### المهمة 2. شغّل Stack مؤقت وجرّبه بالـ Smoke (الخيار A)

| البند | التفاصيل |
|---|---|
| الأمر | `scripts/dev.sh verify` |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | بتاعك انت (لازم يقدر يستخدم Docker محليًا) |
| النوع | بيغيّر الحالة على اللابتوب بتاعك بس: بيبني الـ API، وبيشغّل Containers في مشروع compose مؤقت، وبعدين بيشيلها. تحذير: بيستهلك موارد Docker المحلية ووقت؛ ماتشغّلوش أبدًا على الـ Contabo VPS |
| المتطلب المسبق | المهمة 1 نجحت |
| النتيجة المتوقعة | doctor وup وsmoke بينجحوا، وبعدين التنظيف (VERIFIED IN CODE: التعليقات في بداية `scripts/dev.sh`) |
| التحقق | الأمر بيخرج بكود 0 و `docker ps` على اللابتوب بتاعك مابيعرضش Containers متبقية من المشروع ده |
| فشل محتمل | بورت مستخدم بالفعل أو فشل Build: اقرا أول خطأ، وصلّحه محليًا، وأعد التشغيل. لو ماشغّلتهوش، سجّل NOT RUN |

### المهمة 3. تمرين إعادة البناء على سيرفر مؤقت (الخيار B): الحسابات والصلاحيات

اتبع [دليل إعادة بناء السيرفر](../08-infrastructure/server-rebuild-guide-ar.md) للإجراء الكامل بالترتيب؛ هو المرجع للخطوات بالظبط. المهمة دي جزء الحسابات والصلاحيات بس، كتمرين تقارن بيه الحالة المسجّلة على السيرفر الحي. الـ Code Block ده مخطط تقريبي لسيرفر مؤقت وبيستخدم Placeholders؛ مش للسيرفر الحي. الـ Code Block افتراضي ومش مشغّل من الكتّاب.

```bash
# Throw-away server only. Run as the administrator of THAT server. Not for the live Preview.
# 1. Accounts recorded on the live host (names are reproduced from the inventory; their roles are not established):
sudo adduser --disabled-password --gecos "" --uid 1000 ubuntu    # may already exist on a cloud image
sudo adduser --disabled-password --gecos "" --uid 1001 devops
sudo adduser --disabled-password --gecos "" --uid 1002 aiagent
# 2. Group memberships recorded on the live host:
sudo usermod -aG sudo ubuntu
sudo usermod -aG sudo,docker,users devops
sudo usermod -aG users aiagent          # NO sudo and NO docker for aiagent, ever
# 3. SSH: key-only login for each account that needs it, using public keys you generate for the exercise
sudo install -d -m 700 -o <user> -g <user> /home/<user>/.ssh
# append the exercise public key to /home/<user>/.ssh/authorized_keys, then: chmod 600 and chown <user>:<user>
#    (copy the public key from your laptop to the exercise server first, for example by pasting it; the private key never leaves your laptop)
#    Keep a working administrator key for yourself on the exercise server until every later step is done; do not delete the only key you use to log in
# 4. Deployment directory ownership and modes recorded on the live host:
sudo install -d -m 755 -o devops -g devops /srv/matbakhy-staging
# on the exercise server, the .env file inside deploy/web-preview is created by deploy.sh init-secrets with mode 600 (see the rebuild guide).
# That is the supported creation method; how the live .env was created is NOT VERIFIED.
```

قبل ما تستخدم المخطط، راجع كل سطر فيه على [جرد سيرفر Contabo](../08-infrastructure/contabo-server-inventory-ar.md) ودليل إعادة البناء. السطور اللي بتعكس الحسابات المسجّلة هي إعادة إنتاج لحالة اتلاحظت، مش توصية إنك تدّي devops صلاحية sudo أو docker؛ وقرار استخدام نفس النمط ده على سيرفر جديد هو قرار للمالك والأدمن. الـ uids الرقمية هي المسجّلة (VERIFIED LIVE)، ودي مهمة بس لو ملكية الملفات لازم تطابق نسخة مسترجعة.

نقط لازم تراجعها على دليل إعادة البناء قبل ما تمشي بيه (VERIFIED IN CODE إلا اللي متعلّم غير كده؛ ولا واحدة فيهم اتجرّبت عمليًا):

- أرشيف الـ Bootstrap لازم يشمل الملفات المتتبَّعة (Tracked) بس، من Commit محدد بأمر `git archive`. أمر `tar` عادي على Working Copy بيشمل `.env` والملفات المتولّدة حتى لو `git status` نضيف، لأنها في الـ gitignore.
- اعمل `BACKUP_DIR/metrics` بإيدك، بملكية تناسب حساب الـ Deploy، قبل أول تشغيل لـ Compose (شوف اليوم 2)، وتأكد من الملكية.
- `docker compose --env-file` بيدّي قيم للـ Interpolation جوّه ملف الـ Compose بس. مابيضبطش متغيّرات الـ Shell، فأي تسلسل أوامر يدوي بيستخدم `$COMPOSE_OVERRIDE` لازم يسمّي ملف الـ Override صراحةً بـ `-f` أو يضبط المتغيّر في الـ Shell الأول.
- `PUSH_DELIVERY_ENABLED=false` يفضل هو القيمة التشغيلية المطلوبة. بيئتا STAGING وPRODUCTION بيرفضوا `true`؛ أما بيئة Preview فمابترفضهاش، لأن Preview بتتحسب Synthetic وبتختار مزوّد الـ Push المحاكى.

التحقق على سيرفر التمرين، للقراءة فقط:

| الأمر | النتيجة المتوقعة |
|---|---|
| `id aiagent` | مجموعتا aiagent وusers بس؛ مفيش sudo ولا docker |
| `getent group docker` | الأعضاء: devops بس |
| `getent group sudo` | الأعضاء: ubuntu وdevops |
| `ls -ld /srv/matbakhy-staging` | 755 devops:devops |
| `ssh -o PasswordAuthentication=no <user>@<exercise-host> true` | بيشتغل بمفتاح التمرين (ده بيثبت بس إن الدخول بالمفتاح شغّال؛ مابيثبتش إن الـ Passwords مرفوضة) |
| `ssh -o PubkeyAuthentication=no -o PreferredAuthentications=password <user>@<exercise-host> true` | السيرفر بيرد Permission denied من غير ما يعرض طلب Password؛ ده الفحص اللي بيثبت إن الـ Passwords مرفوضة على سيرفر التمرين. بالنسبة للسيرفر الحي، التعليمات المقروءة بتقول PasswordAuthentication no، لكن الإعداد الفعلي وقيمة تسجيل دخول root هما NOT VERIFIED |

فشل محتمل: الـ SSH Daemon بيقبل الـ Passwords. صلّح إعدادات SSH بتاعة سيرفر التمرين وافحص القيمة الفعلية بناتج اختبار الـ Daemon نفسه على السيرفر ده.

### المهمة 4. نسخة احتياطية منطقية لقاعدة البيانات واسترجاع متحقق منه (أي خيار)

اقرا `scripts/db-backup.sh` و`scripts/db-restore.sh` الأول (VERIFIED IN CODE). استخدم قاعدة بيانات مؤقتة فيها بيانات تجريبية غير حقيقية (Synthetic Data) بس.

| الخطوة | الأمر (Placeholders) | الجهاز والمستخدم | النوع | النتيجة المتوقعة | التحقق | فشل محتمل |
|---|---|---|---|---|---|---|
| 1 | اضبط `PGHOST` و`PGPORT` و`PGUSER` و`PGDATABASE` لقاعدة البيانات المؤقتة؛ واضبط الـ Password بآلية libpq القياسية | اللابتوب بتاعك أو سيرفر التمرين | متغيرات بيئة بس | المتغيرات بتشاور على قاعدة البيانات المؤقتة ومفيش غيرها | `echo $PGHOST` بيعرض الـ Host بتاع القاعدة المؤقتة | التوجيه على قاعدة بيانات حقيقية بالغلط. وقّف وراجع قبل كل تشغيل |
| 2 | `scripts/db-backup.sh <backup-dir-outside-git>` | نفسه | بيغيّر الحالة: بيقرا القاعدة وبيكتب ملفات | Dump وملف Checksum وManifest فيه أعداد الصفوف، وسطر "backup ... written in Ns" | `ls <backup-dir>` بيعرض تلات ملفات؛ ملف الـ Checksum فيه اسم الـ Dump | "refusing: ... inside a Git working tree": اختار مجلد برّه الـ Repository |
| 3 | `RESTORE_CONFIRM_TARGET=<new-db> scripts/db-restore.sh <dump> <new-db>` | نفسه | بيغيّر الحالة: بينشئ قاعدة جديدة وبيسترجع فيها | الخروج بكود 0 معناه إن المطابقة (Reconciliation) نجحت؛ 1 المطابقة فشلت؛ 2 السكريبت رفض يشتغل (VERIFIED IN CODE) | قارن ناتج الاسترجاع بأعداد الصفوف في الـ Manifest | "refusing: set RESTORE_CONFIRM_TARGET": اضبط المتغير على نفس اسم الهدف. ماتسترجعش أبدًا فوق قاعدة فيها جداول بالفعل؛ السكريبت بيرفض |
| 4 | امسح قواعد البيانات المؤقتة واحذف ملفات النسخ الاحتياطي | نفسه | بيغيّر الحالة: بيمسح بيانات، وللمؤقت بس | مفيش حاجة بتفضل | القايمة مابتعرضش حاجة | مفيش فشل متوقّع |

تعليقات السكريبت بتنص على قاعدة إن النسخة الاحتياطية مش مقبولة إلا بعد ما تتسترجع وتتطابق (Reconciliation) (VERIFIED IN CODE: الـ Header بتاع `scripts/db-restore.sh`). الغلاف (Wrapper) بتاع ناحية السيرفر هو `./deploy.sh backup` (بيأرشف كمان تخزين الصور) و `./deploy.sh restore-db <dump>`؛ دول بيشتغلوا على سيرفر شبه الـ Preview بواسطة مشغّل مصرّح له، وبيتجرّبوا على سيرفر التمرين بس في الخيار B. فيه تلات حدود لازم تعرفها (VERIFIED IN CODE: `deploy/web-preview/deploy.sh`): الأمر `restore-db` بياخد الـ Dump بس وبيسترجع دايمًا في قاعدة اسمها matbakhy_restore، يعني ده تمرين استرجاع مش تحويل للتطبيق (Cutover) على القاعدة المسترجعة؛ والأمر `backup` بيوقّف خدمة الـ Storage ويأرشف بياناتها وبعدين يشغّلها تاني، ولو خطوة الأرشفة فشلت السكريبت بيخرج قبل أمر التشغيل ويسيب الـ Storage واقف لحد ما مشغّل يفحص حالة الـ Container ويشغّله؛ والـ Dump بتاع قاعدة بيانات Keycloak بيتعمل من دالة الـ Backup الداخلية اللي `admin-console.sh enable` بينده عليها (`admin-console.sh` مفيهوش أمر فرعي اسمه `backup`)، أما `migrate-mfa.sh` فعنده فعلًا الأمرين `backup` و`apply` (VERIFIED IN CODE)؛ ومفيش حاجة في الـ Lab ده بتسترجع بيانات Keycloak ولا تخزين الصور ولا الـ Secrets ولا الـ Cutover للتطبيق، ومفيش لها إجراء متجرَّب في الدليل ده.

### المهمة 5. تقييم النسخ الاحتياطي والتعافي (قراءة واستنتاج)

كمّل الجدول ده من الـ Repository والحقائق الحية. مفيش أمر بيشتغل على السيرفر الحي.

| السؤال | الإجابة بتيجي منين | إجابتك | وسم الدليل |
|---|---|---|---|
| هل فيه إجراء للنسخ الاحتياطي؟ | الأوامر الفرعية backup وrestore-db في `deploy/web-preview/deploy.sh`؛ و`scripts/db-backup.sh`؛ و`scripts/db-restore.sh` | أيوه، في الكود | VERIFIED IN CODE |
| هل `./deploy.sh up` بياخد نسخة احتياطية من قاعدة البيانات قبل ما يغيّر أي حاجة؟ | قسم تعليمات الاستخدام في بداية السكريبت `deploy/web-preview/deploy.sh` ("back up the database") | موثّق في القسم ده | VERIFIED IN CODE |
| هل فيه Job نسخ احتياطي مجدول على السيرفر الحي؟ | قايمة cron على السيرفر الحي | مفيش Job نسخ احتياطي لـ Matbakhy كان ظاهر للحساب من غير صلاحيات مرتفعة؛ وcrontabs المستخدمين التانيين ماكانتش مقروءة | NOT VERIFIED |
| هل فيه نسخ احتياطية، وإيه مدى حداثتها؟ | /srv/matbakhy-backups بصلاحيات 750 devops:devops ومش ممكن aiagent يعرض محتواه | غير معروف | NOT VERIFIED |
| هل الاسترجاع اتثبت على البيانات الحية؟ | مفيش دليل في الدليل ده | غير معروف | NOT VERIFIED |
| النسخ الاحتياطية متخزّنة فين بالنسبة للسيرفر؟ | المجلدات على نفس قرص الـ VPS (/srv/...)؛ النسخ برّه السيرفر مش موثّقة في الدليل ده | غير معروف لو فيه نسخ برّه السيرفر | NOT VERIFIED |
| هل تخزين الصور بيتاخد منه نسخة احتياطية؟ | `./deploy.sh backup` بيأرشفه (بيوقّف خدمة الـ Storage أثناء الأرشفة؛ ولو الأرشفة فشلت بتفضل واقفة) | الإجراء موجود | VERIFIED IN CODE |
| هل ممكن نسترجع بيانات Keycloak وتخزين الصور ونعمل الـ Cutover للتطبيق بإجراء متجرَّب؟ | [دليل إعادة بناء السيرفر](../08-infrastructure/server-rebuild-guide-ar.md) والأمر `restore-db` اللي بيسترجع في matbakhy_restore بس | مفيش إجراء كامل متجرَّب موثّق | NOT VERIFIED |
| هل ممكن نسترجع الشهادات والمفاتيح؟ | Caddy بيعيد إصدار الشهادات لما الـ Data Volume بتاعه يبقى فاضي؛ في حدود معدّل الطلبات المسموح بيه عند Let's Encrypt (Rate Limits) | إعادة الإصدار ممكنة | DOCUMENTED ONLY |
| هل ممكن نسترجع الـ Secrets؟ | ملف .env بتاع السيرفر فيه الـ Secrets؛ مش مذكور إن كان فيه نسخة منها برّه السيرفر ولا لأ | غير معروف | NOT VERIFIED |
| إيه زمن التعافي وفترة فقد البيانات اللي بينطبقوا هنا؟ | مفيش قيمة اتقاست | مفيش ادعاء | NOT VERIFIED |

بعد كده اكتب تقييم في صفحة واحدة من تلات أجزاء: إيه اللي اتثبت (اللي VERIFIED IN CODE كقدرة بس)، وإيه اللي لسه ماتثبتش (كل حاجة NOT VERIFIED)، والحد الأدنى من طلبات القراءة فقط للأدمن (اختار أوامر من [صفحة الأدمن](../08-infrastructure/admin-readonly-verification-ar.md) وجداول القرار في [النسخ الاحتياطي والاسترجاع والتعافي من الكوارث](../08-infrastructure/backup-restore-disaster-recovery-ar.md)). ماتقولش إن الـ Preview ممكن يتعافى؛ قول إيه اللي لازم يتثبت.

### المهمة 6. اقفل التمرين

دمّر السيرفر المؤقت أو شغّل `scripts/dev.sh down` (أو `scripts/dev.sh reset` لمسح الـ Volume بتاع البيانات المحلية؛ الأمر reset بيرفض ملفات الـ env اللي مش معلّمة كمحلية، VERIFIED IN CODE). اكتب ملاحظة ختامية: إيه اللي شغّلته، وإيه اللي تخطّيته (NOT RUN)، وإيه اللي اختلف عن الدليل، وأنهي صفحات محتاجة تصحيح. قدّم التصحيحات من خلال مسار الـ Pull Request العادي مع إقرار Documentation Impact (الأثر على التوثيق)، شوف [سياسة التوثيق](../05-developer-workflow/documentation-policy-ar.md).

## نقاط التحقق

| نقطة التحقق | بتنجح لما |
|---|---|
| العزل | تقدر تثبت إن ولا أمر اتوجّه للسيرفر الحي ولا الدومين ولا الـ Secrets |
| الحسابات | الخيار B بس: جدول التحقق بيطابق المجموعات المسجّلة، وaiagent مالوش sudo ولا docker |
| النسخ الاحتياطي والاسترجاع | الـ Dump والـ Checksum والـ Manifest موجودين، والاسترجاع انتهى بكود خروج 0، أو سجّلت NOT RUN |
| التقييم | كل حاجة غير معروفة معلّمة NOT VERIFIED وملحق بيها طلب للأدمن |
| التنظيف | بيئة التمرين اتدمّرت والملاحظة الختامية بتقول كده |

## معايير الإكمال

- [ ] فهمت إن التمرين متجهّز لكن الكتّاب ماشغّلوهوش، وسجّلت تشغيلي أنا بأمانة.
- [ ] استخدمت بيئة معزولة بـ Secrets خاصة بيها وبيانات تجريبية غير حقيقية (Synthetic Data) بس.
- [ ] أقدر أشرح ليه النسخة الاحتياطية مابتتحسبش صالحة إلا بعد ما تتسترجع وتتطابق.
- [ ] عملت تقييم النسخ الاحتياطي والتعافي من غير ما أدّعي إمكانية تعافي محدش أثبتها.
- [ ] مادّيتش aiagent صلاحية sudo أو Docker في أي مكان، وماغيّرتش حاجة في السيرفر الحي.

## اقرا إيه بعد كده

[دليل إعادة بناء السيرفر](../08-infrastructure/server-rebuild-guide-ar.md)، [النسخ الاحتياطي والاسترجاع والتعافي من الكوارث](../08-infrastructure/backup-restore-disaster-recovery-ar.md)، [التحقق للقراءة فقط بواسطة الأدمن](../08-infrastructure/admin-readonly-verification-ar.md)، [تغطية توثيق البنية التحتية وتقرير الانحراف](../07-reference/infrastructure-documentation-coverage-ar.md).

ارجع لـ [البنية التحتية اليوم 1](infra-day-1-topology-accounts-ar.md) لبداية سلسلة الـ Labs دي.

## القيود المعروفة وفجوات التوثيق

- التمرين وكل أمر في المخطط الكتّاب ماشغّلوهمش؛ مخطط الحسابات بيعكس الحالة المسجّلة ولازم يتراجع على دليل إعادة البناء.
- موعد النسخ الاحتياطي على السيرفر الحي ومحتوى النسخ والنسخ برّه السيرفر واسترجاع الـ Secrets وأي زمن تعافي كلها NOT VERIFIED. ولسه مفيش في الدليل تسلسل تعافي كامل متجرَّب (كل مخازن البيانات، والـ Secrets، والملكية، والـ Cutover للتطبيق، والتحقق، والـ Rollback)؛ فماتعتبرش الـ Lab ده دليل إن التعافي بإعادة البناء كافي.
- الـ Stack المحلي في الخيار A مش Stack الـ Preview، فمش بيثبت إن ملف compose بتاع الـ Preview أو إصدار شهادات Caddy بيشتغلوا على سيرفر نضيف.
- مستندات الـ Staging بتاعة فترة Hetzner بتوصف سيرفر تاني؛ ماتتبعهاش في إعادة بناء Contabo.
