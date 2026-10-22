# البنية التحتية اليوم 4 - GitHub Actions والـ Deployment والمراقبة وحل المشاكل

> **Owner role:** Infrastructure and deployment
> **Status:** REFERENCE — Lab لقراءة Workflows الـ CI والـ Deployment، ومتابعة Deployment على GitHub، وقراءة تصميم المراقبة، والتمرّن على حل المشاكل (Troubleshooting) من الـ Repository والـ API العام.
> **Language / اللغة:** [English](infra-day-4-cicd-operations.md) · العربية

## مفتاح الأدلة

| الوسم | المعنى |
|---|---|
| VERIFIED LIVE | اتلاحظ يوم 2026-10-21 على الـ Contabo VPS بواسطة المستخدم aiagent من غير صلاحيات مرتفعة. |
| VERIFIED IN CODE | اتقرا في الـ Repository ده (الملف مذكور). بيبيّن النية، مش بالضرورة اللي شغّال فعلًا. |
| DOCUMENTED ONLY | مستند بيقول كده؛ ومفيش حاجة أكّدته هنا. |
| OUTDATED / INCORRECT | مكتوب لسيرفر Hetzner المقترح أو اتلغى بحاجة أحدث. |
| NOT VERIFIED | مااتلاحظش. |

## الأهداف

- تشرح المسار من الـ Pull Request لحد الـ Preview الشغّال وتسمّي كل Gate.
- تقرا `.github/workflows/deploy-preview.yml` وتقول كل خطوة بتعمل إيه، وبتستخدم أنهي Secrets (الأسماء بس)، وإيه اللي مابيتعملوش له Rollback.
- تتابع Deployment على GitHub بأوامر `gh` للقراءة فقط من غير ما تلغي أو تعيد تشغيل Jobs شغّالة كويس.
- توصف تصميم المراقبة (الـ Logs والـ Metrics والتنبيهات) وحدوده.
- تشخّص عَرَض (Symptom) اتبلّغ عنه باستخدام إعدادات الـ API العام وسلوك الـ HTTP للـ Hosts وصفحة حل المشاكل.

## المتطلبات المسبقة

- الأيام من 1 إلى 3 خلصت: [اليوم 1](infra-day-1-topology-accounts-ar.md) و[اليوم 2](infra-day-2-docker-services-ar.md) و[اليوم 3](infra-day-3-dns-https-keycloak-ar.md).
- اقرا [مسار الـ Deployment](../08-infrastructure/deployment-pipeline-ar.md) و[CI/CD](../05-developer-workflow/ci-cd-ar.md).
- أداة سطر أوامر GitHub اللي اسمها `gh` متسطّبة وعامل بيها تسجيل دخول على اللابتوب بتاعك بحسابك انت (`gh auth status`). صلاحية القراءة على الـ Repository كفاية لكل الأوامر اللي تحت.

## قواعد الـ Lab ده

- للقراءة فقط. ماتشغّلش Deployment، وماتستخدمش `gh workflow run`، وماتعيدش تشغيل Run ولا تلغيه، وماتعملش Push على develop، وماتعدّلش إعدادات الـ Repository.
- ماتعملش Polling Loop. شغّل كل أمر مرة واحدة واقرا النتيجة. الـ Job اللي واخد وقت طويل مش Job فاشل؛ الخطوة الفاشلة بس هي اللي تبرّر أي إجراء، وساعتها دوّر على سبب الفشل الحقيقي الأول.
- مستخدم الـ SSH بتاع الـ Deployment ومفتاحه هما Secrets في الـ Repository. ينفع تذكر أسماء الـ Secrets؛ لكن ماتحاولش أبدًا تقرا قيمها (GitHub مش بيعرضها) ولا تخمّنها.
- مفيش أي أمر بيشتغل على الـ Contabo VPS. ملف .env بتاع السيرفر مابيتقراش أبدًا.

## المهام الموجّهة

### المهمة 1. اقرا سلسلة الـ Workflows

| البند | التفاصيل |
|---|---|
| الأمر | `ls .github/workflows` وبعدها `sed -n 1,16p .github/workflows/deploy-preview.yml` |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | حسابك انت |
| النوع | للقراءة فقط |
| النتيجة المتوقعة | تسعة ملفات Workflow، منها ci.yml وdoc001.yml وhandbook-parity.yml وnative-build.yml وpreview-images.yml وdeploy-preview.yml (العدد بحسب نسخة الـ Checkout اللي معاك؛ وممكن يتغيّر). الـ Deploy Workflow اسمه "Deploy Matbakhy preview to Contabo"، بيشتغل على Push على develop وعلى التشغيل اليدوي (Manual Dispatch)، وبيستخدم مجموعة التزامن (Concurrency Group) اللي اسمها matbakhy-preview-deploy من غير ما يلغي Run سابق |
| التحقق | اكتب في سطر واحد معنى إعداد التزامن (Concurrency) لما يحصل Merge-ين قريبين من بعض: الـ Deployment التاني بيستنى الأول (VERIFIED IN CODE: cancel-in-progress قيمته false) |

### المهمة 2. امشي على الـ Deploy Workflow خطوة بخطوة

افتح `.github/workflows/deploy-preview.yml` وكمّل الجدول. كل الصفوف VERIFIED IN CODE إلا لو اتذكر غير كده.

| الـ Job والخطوة | بتعمل إيه | الـ Secrets المستخدمة (الأسماء بس) | أثر الفشل |
|---|---|---|---|
| build: wait for passing Product CI | بتسأل GitHub بشكل متكرر (Polling) لحد ما تلاقي Run ناجح لـ ci.yml على نفس الـ Commit بالظبط قبل ما تبني أي حاجة | الـ Workflow Token المدمج | الـ Build بيفشل أو بيخلص وقته (Timeout)؛ مفيش حاجة بتتنشر |
| build: images | بتبني الـ Image-ين وتنشرهم على الـ Container Registry وبتطلّع المراجع بتاعتهم (Digests) | الـ Token المدمج (صلاحية كتابة الـ Packages) | مفيش Deployment بيبدأ |
| deploy: configure verified SSH host | بتكتب مفتاح الـ SSH ومفتاح الـ Host المثبّت (Pinned) وبترفض تكمّل لو أي قيمة مطلوبة فاضية | STAGING_SSH_HOST وSTAGING_SSH_USER وSTAGING_SSH_KEY وSTAGING_SSH_HOST_PUBKEY | بيقف قبل الاتصال |
| deploy: transfer versioned deployment files | بتتأكد إن .env بتاع السيرفر موجود وإن المجلد قابل للكتابة، وبعدها بتبعت مجلدات deploy وinfra وscripts وملف fixtures واحد على /srv/matbakhy-staging بـ tar عبر SSH | الـ Secrets بتاعة الـ SSH | بيقف؛ ملف .env بتاع السيرفر مابيتكتبش فوقه لأن الـ Workflow بيعمل Checkout للملفات المتتبَّعة في Git بس ويأرشف المسارات المذكورة بس من الـ Checkout النضيف ده (أمر tar نفسه مابيحترمش .gitignore، فأي أرشيف يدوي من نسخة شغل فيها .env محلي لازم يستثنيه صراحةً) |
| deploy: deploy digest-pinned images | بتحدّث تلات سطور في .env بتاع السيرفر، وبعدين بتشغّل `./deploy.sh up` و `./deploy.sh smoke`؛ لو واحد منهم فشل بترجّع الـ .env القديم وبتخرج بكود غير صفري (أما لو الفشل حصل قبل كده، زي تسجيل الدخول على الـ Registry، فبتخرج من غير ما ترجّع حاجة) | الـ Secrets بتاعة الـ SSH والـ Token المدمج لتسجيل الدخول على الـ Registry (اسم مستخدم الـ Registry بيتكتب جوّه السكريبت البعيد اللي الـ Workflow بيولّده) | ملف .env بس هو اللي بيرجع: الـ Containers اللي اتعمل لها Recreate قبل كده بتفضل شغّالة بالـ Images الجديدة لحد ما حد يشغّل `up` تاني بالـ Pins المسترجعة، ومفيش Rollback أوتوماتيكي لقاعدة البيانات. قبل ما تشغّل بـ Pins أقدم، اتأكد إن التطبيق الأقدم يقدر يشتغل على الـ Schema اللي الإصدار الفاشل ممكن يكون عمل لها Migration بالفعل (استخدم `./deploy.sh rollback-plan`). الرقم N فيه بيعدّ بس الإصدارات الناجحة اللي `up` سجّلها (VERIFIED IN CODE): أي `up` فاشل مابيتسجّلش، فبعد Deployment فاشل ممكن N=1 يعدّي على آخر إصدار ناجح، والخطة عمرها ما بتستهدف آخر سطر متسجّل نفسه. لازم أدمن يقرا آخر سطر في سجل الإصدارات (فيه الـ Commit والـ Digests بتاعة الـ Images والـ Schema) ويقارنه بالخطة؛ ومفيش في الدليل تسلسل متجرَّب لده (لسه ماتجرّبش عمليًا: NOT VERIFIED) |
| deploy: publish and verify the Keycloak admin console | بتشغّل `./deploy.sh admin-console enable` على السيرفر، بعد ما الخطوة اللي قبلها تنجح | الـ Secrets بتاعة الـ SSH | مفيش رجوع للـ .env في الخطوة دي: لو فشلت، الـ Images الجديدة بتفضل منشورة والـ Run بيتسجّل كفاشل. اسم الخطوة بيقول إن الـ Console بيقفل نفسه تاني لو التحقق فشل؛ ده ادعاء مكتوب في الـ Workflow، والسلوك نفسه NOT VERIFIED. التقوية (Hardening) بتغيّر إعدادات متخزّنة جوّه Keycloak، ورجوع الـ .env مابيلغيهاش. شوف مستند Keycloak admin console |

أسئلة:

1. أنهي Job عليه `environment: preview`؟ deploy.
2. أنهي Branch مسموح له يعمل Deploy؟ develop بس (الشرط على الـ Job بتاع deploy).
3. أنهي قيمة الـ Pipeline مابيعرضهاش ليك أبدًا؟ مستخدم Linux اللي قيمته متخزّنة في STAGING_SSH_USER. ده NOT VERIFIED؛ ملكية devops لـ /srv/matbakhy-staging بتتماشى مع ده لكن مش دليل عليه.
4. إيه اللي الـ Pipeline مابيعملوش له Rollback؟ بنية قاعدة البيانات (Schema) والبيانات (VERIFIED IN CODE: رسالة الفشل). اقرا جداول قرار الـ Rollback في [النسخ الاحتياطي والاسترجاع والتعافي من الكوارث](../08-infrastructure/backup-restore-disaster-recovery-ar.md).

### المهمة 3. تابع الـ Runs على GitHub (للقراءة فقط)

| الأمر | الجهاز والمستخدم | النوع | المتطلب المسبق | النتيجة المتوقعة | التحقق | فشل محتمل والإجراء |
|---|---|---|---|---|---|---|
| `gh run list --workflow deploy-preview.yml --branch develop --limit 5` | اللابتوب بتاعك، وحساب GitHub بتاعك | للقراءة فقط | gh عامل Authentication | آخر الـ Runs مع الحالة والنتيجة والـ Head Commit بتاع كل واحد | سجّل الـ Commit والنتيجة لأحدث Run. آخر Run ناجح مش دليل على الـ Commit المنشور: الـ Workflow بيطبع DEPLOYED قبل خطوة الـ admin-console، فممكن Run أحدث ينشر وبعدين تفشل الخطوة دي، ويفضل آخر Run ناجح شاور على إصدار أقدم. افتح الـ Runs الأحدث الفاشلة وشوف أنهي خطوات نجحت؛ وتأكيد مراجع الـ Images الشغّالة محتاج أدمن مصرّح له (NOT VERIFIED هنا) | خطأ Authentication: شغّل `gh auth status`. مفيش صفوف: الـ Repository غلط؛ شغّل الأمر جوّه الـ Checkout |
| `gh run view <run-id>` | اللابتوب بتاعك | للقراءة فقط | رقم Run من الصف اللي قبله | الـ Jobs والخطوات وحالاتها | حدّد أنهي خطوة شغّالة أو فاشلة | ماتعيدش تشغيل Job شغّال أو سليم |
| `gh run view <run-id> --log-failed` | اللابتوب بتاعك | للقراءة فقط | الـ Run فشل | الـ Log بتاع الخطوات الفاشلة بس | دوّر على أول سطر فاشل | لو الناتج فاضي يبقى الـ Run ماكانش فاشل؛ ماتستنتجش أي حاجة تانية |
| `gh pr checks <pr-number>` | اللابتوب بتاعك | للقراءة فقط | رقم Pull Request | الـ Checks المطلوبة للـ PR وحالتها | عُدّ كام واحد منهم Pending وكام Passing وكام Failing | الـ Pending طبيعي طول ما الـ Jobs شغّالة |
| `gh api repos/<owner>/<repo>/rulesets/25447480` | اللابتوب بتاعك | للقراءة فقط | Token يقدر يقرا القواعد | JSON فيه enforcement بقيمة active والـ Status Checks المطلوبة؛ الـ Ruleset هو DOCUMENTED ONLY في [نظام صيانة التوثيق](../05-developer-workflow/documentation-maintenance-ar.md) | قارن الـ Contexts المطلوبة بالقائمة في [مسار الـ Deployment](../08-infrastructure/deployment-pipeline-ar.md) | 403 أو 404 معناها إن الـ Token بتاعك مش بيقرا القواعد، مش إن القواعد اختفت |

صيغة أوامر gh هي الصيغة المعتادة للأداة (DOCUMENTED ONLY هنا). اللي الأوامر دي بترجّعه النهارده مش مسجّل في الدليل ده؛ سجّل ملاحظتك انت مع التاريخ.

تمرين لاتخاذ قرار: Run لـ Deploy بيعرض إن الخطوة "Deploy digest-pinned images" شغّالة بقالها 20 دقيقة. هل هي واقفة؟ مهلة الـ Job هي 30 دقيقة (VERIFIED IN CODE) والخطوة بتستنى الـ Containers تبقى سليمة (Health)، فالتشغيل الطويل لوحده مش فشل. راجع قايمة الخطوات بـ `gh run view` وآخر الـ Log؛ وتصرّف بس لو فيه خطوة فاشلة فعلًا.

### المهمة 4. اقرا الإعدادات العامة وحالة الـ Preview

| البند | التفاصيل |
|---|---|
| الأمر | `curl -sS https://api.<preview-domain>/api/v1/configuration/public` |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | حسابك انت |
| النوع | للقراءة فقط |
| النتيجة المتوقعة | JSON فيه orderingAvailable وorderLimits واسم البيئة PREVIEW مع demo true (VERIFIED LIVE يوم 2026-10-21 من عنوان السيرفر نفسه) |
| التحقق | اتأكد إن environment.name هو PREVIEW وإن demo هو true. الـ Endpoint مابيرجّعش Commit ولا إصدار، فمش هيثبت أنهي Release شغّال |
| فشل محتمل | 403 مع النص "Not available": عنوانك برّه فئة العملاء (رد من سياسة الوصول، مش عطل). خطأ اتصال: الـ DNS أو البورت 443؛ كرّر فحوصات اليوم 3 |

شغّل كمان `curl -sS -o /dev/null -w '%{http_code}\n' https://api.<preview-domain>/actuator/health` واتأكد إنه 404، بالتصميم.

اختياري، وللقراءة فقط: `smoke.sh` مابيعملش حساب ولا أوردر (VERIFIED IN CODE: الـ Header بتاع السكريبت). استخدم الأمر اللي يناسب شبكتك. من جهاز في فئة الموظفين: `sh deploy/web-preview/smoke.sh <preview-domain>`. ومن جهاز برّه فئة الموظفين: `SMOKE_OPS=skip sh deploy/web-preview/smoke.sh <preview-domain>`؛ ومن غير SMOKE_OPS=skip السكريبت بيطلب الـ Operations Web ويستلم 403 ويفشل، وده رد من سياسة الوصول مش عيب. ونجاح التشغيل من فئة العملاء بس مش دليل إن فئة الموظفين شغّالة. لو ماشغّلتهوش، سجّل NOT RUN.

### المهمة 5. اقرا تصميم المراقبة

| البند | التفاصيل |
|---|---|
| الأمر | `sed -n 1,40p deploy/web-preview/observability/alerts.yml` و `sed -n '/^## 9. Observability/,/^## 10. Deployment path/p' docs/developer-handbook/08-infrastructure/runtime-architecture.md` |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | حسابك انت |
| النوع | للقراءة فقط |
| النتيجة المتوقعة | قواعد التنبيه ومسار بيانات المراقبة: الـ Logs بتاعة Docker → Alloy → Loki؛ والـ Metrics → Prometheus؛ وAlertmanager → alert-sink محلي؛ وGrafana على بورت Loopback |
| التحقق | جاوب: مين بيتبلّغ لما تنبيه يطلع؟ ولا حد: التنبيهات بتروح لـ alert-sink، وهو مستقبِل (Receiver) تجريبي محلي (VERIFIED IN CODE). إزاي المهندس بيوصل لـ Grafana؟ عن طريق SSH Tunnel على بورت الـ Loopback بواسطة مهندس مصرّح له؛ Grafana مربوط على 127.0.0.1:3000 (VERIFIED IN CODE). فيه Listener على 127.0.0.1:3000 على الـ Host (VERIFIED LIVE كـ Socket بس)، لكن إنه Grafana فعلًا NOT VERIFIED لأن قايمة الـ Containers مش مقروءة. هل Containers المراقبة سليمة دلوقتي؟ NOT VERIFIED LIVE |

ملاحظة لوقت لاحق: فيه تنبيه لو النسخ الاحتياطي اتأخر (BackupStale: مفيش نسخة احتياطية متحقق منها في آخر 24 ساعة، VERIFIED IN CODE). هل فيه أي Job للنسخ الاحتياطي على السيرفر الحي ولا لأ؟ ده NOT VERIFIED.

### المهمة 6. تمارين حل المشاكل (للقراءة فقط)

افتح [حل مشاكل البنية التحتية](../08-infrastructure/infrastructure-troubleshooting-ar.md). لكل عَرَض (Symptom) من اللي تحت، دوّر على السيناريو المطابق، وبعدين اكتب أول فحص للقراءة فقط والسبب المحتمل ومين لازم يتصرّف (انت، ولا أدمن مصرّح له).

1. الموقع بيفتح لكن تسجيل الدخول بيفشل بخطأ Redirect.
2. الـ Run بتاع الـ Deployment فشل عند خطوة الـ Smoke.
3. الـ Preview بيرد عليك 403 "Not available" لكن شغّال مع زميلك.
4. الـ Host بتاع media بيرد 403 على الجذر.
5. الـ Host بتاع Keycloak admin بيرد 404.

التفكير المتوقع، من الدليل ده: البند 3 رد من فئة الوصول (اليوم 3)؛ والبند 4 هو رفض S3 المتوقع على المسار الجذر؛ والبند 5 معناه إن Flag الـ Admin Console مش موجود والـ Host بيرفض في حالة الشك (Fail Closed). البندين 1 و 2 لازم يتجاوبوا من صفحة حل المشاكل والـ Logs بتاعة الـ Workflow؛ ماتخمّنش سبب من غير دليل.

## نقاط التحقق

| نقطة التحقق | بتنجح لما |
|---|---|
| جدول الـ Workflow | الصفوف الستة كلها اتكمّلت واتراجعت على الملف |
| أوامر gh | شغّلت على الأقل الأمر الأول والتاني والرابع مرة لكل واحد وسجّلت نتايجك انت مع التاريخ، أو كتبت NOT RUN |
| تمرين القرار | بتشرح ليه مش هتلغي أو تعيد تشغيل Job طويل وسليم |
| المراقبة | تقدر تقول إن التنبيهات مابتوصلش لحد وإن Grafana على Loopback بس |
| حل المشاكل | كل تمرين له أول فحص للقراءة فقط وجهة مسؤولة عن التنفيذ |

## معايير الإكمال

- [ ] أقدر أتتبّع Merge على develop من خلال الـ CI وبناء الـ Images ونقل الملفات بالـ SSH والـ Deploy والـ Smoke وخطوة الـ Admin Console.
- [ ] أقدر أقول إيه اللي بيتعمل له Rollback وإيه اللي مابيتعملوش له عند الفشل.
- [ ] استخدمت أوامر القراءة فقط بس وماعملتش Polling Loop.
- [ ] سجّلت NOT RUN لأي حاجة ماشغّلتهاش.
- [ ] تعاملت مع مستخدم الـ SSH وملف .env بتاع السيرفر وحالة الـ Containers الحية على إنها NOT VERIFIED.

## اقرا إيه بعد كده

[مسار الـ Deployment](../08-infrastructure/deployment-pipeline-ar.md)، [حل مشاكل البنية التحتية](../08-infrastructure/infrastructure-troubleshooting-ar.md)، [معمارية التشغيل الخاصة بالـ Preview](../08-infrastructure/runtime-architecture-ar.md)، [CI/CD](../05-developer-workflow/ci-cd-ar.md).

التالي: [البنية التحتية اليوم 5](infra-day-5-rebuild-recovery-ar.md).

## القيود المعروفة وفجوات التوثيق

- حالة الـ Deployment الحالية للـ Preview (الـ Commit والـ Images ورقم إصدار الـ Schema) مش ظاهرة للعامة؛ تأكيدها محتاج أدمن مصرّح له. وآخر Run ناجح للـ Workflow ممكن يكون أقدم من Run أحدث نشر وبعدين فشلت فيه خطوة الـ admin-console.
- تفاصيل الـ Ruleset وقايمة الـ Checks المطلوبة هي DOCUMENTED ONLY لحد ما مستخدم عنده Token بصلاحية قراءة القواعد يقراها تاني بأمر gh.
- كلام الـ Deployment بتاع فترة Hetzner في المستندات التانية تاريخي؛ الـ Lab ده وصفحة مسار الـ Deployment بيوصفوا الـ Workflow بتاع Contabo.
