# البنية التحتية اليوم 1 - الـ Topology، والـ Host بتاع Contabo، ومستخدمو Linux والصلاحيات

> **Owner role:** Infrastructure and deployment
> **Status:** REFERENCE — Lab لقراءة جرد سيرفر Contabo وطوبولوجي الـ Preview من غير ما تشغّل أي أمر بصلاحيات مرتفعة.
> **Language / اللغة:** [English](infra-day-1-topology-accounts.md) · العربية

## مفتاح الأدلة

| الوسم | المعنى |
|---|---|
| VERIFIED LIVE | اتلاحظ يوم 2026-10-21 على Contabo VPS بواسطة المستخدم aiagent من غير صلاحيات مرتفعة، للقراءة فقط، زي ما هو مسجّل في [جرد السيرفر](../08-infrastructure/contabo-server-inventory-ar.md). |
| VERIFIED IN CODE | اتقرا في الـ Repository ده (الملف مذكور). بيبيّن النية، مش بالضرورة اللي شغّال فعلًا. |
| DOCUMENTED ONLY | مستند بيقول كده؛ ومفيش حاجة بتأكده. |
| OUTDATED / INCORRECT | مكتوب للـ Host المقترح على Hetzner أو اتلغى بحاجة أحدث. |
| NOT VERIFIED | مااتلاحظش. أوامر الأدمن للقراءة فقط موجودة في [التحقق للقراءة فقط بواسطة الأدمن](../08-infrastructure/admin-readonly-verification-ar.md). |

## الأهداف

بعد الـ Lab ده تقدر:

- ترسم الـ Topology بتاع الـ Preview من الذاكرة: Contabo VPS واحد، ومشروع Docker Compose واحد، وReverse Proxy عام واحد.
- تذكر حقائق الـ Host المتحقق منها (Ubuntu 24.04.5 LTS، 8 vCPU، حوالي 23 GiB RAM، مجلد الـ Deployment هو /srv/matbakhy-staging) وتقول كل واحدة منهم تبع أنهي فئة دليل.
- تقرا جدول الحسابات والمجموعات وتشرح ليه الانضمام لمجموعة docker بيدّي صلاحيات تعادل root، وليه aiagent مينفعش أبدًا ينضم ليها أو لمجموعة sudo.
- تشرح أنهي أجزاء السيرفر NOT VERIFIED ومين مسموح له يتحقق منها.
- تفرّق بين نص Hetzner التاريخي وتعليمات Contabo الحالية.

## المتطلبات المسبقة

- الـ Repository اتعمل له Checkout (شوف [البيئة المحلية](../05-developer-workflow/local-environment-ar.md)). مفيش حاجة هنا محتاجة وصول للسيرفر.
- اقرا [البنية التحتية والـ Deployment](../03-architecture/infrastructure-and-deployment-ar.md) ونظرة عامة على [معمارية التشغيل الخاصة بالـ Preview](../08-infrastructure/runtime-architecture-ar.md).
- تكون قريت [جرد سيرفر Contabo](../08-infrastructure/contabo-server-inventory-ar.md) مرة واحدة، بسرعة.

## قواعد الـ Lab ده

- الـ Lab ده للقراءة فقط وعلى الـ Repository بس. المتدرّب مابيشغّلش أي أمر على السيرفر، ومابيستخدمش sudo، ومابيبدّلش مستخدمي Linux، ومابيقراش أي ملف .env، ومابيحاولش يتخطى أي صلاحية في Linux.
- أي حاجة محتاجة root أو الـ Docker socket بيتنفّذ بواسطة أدمن بنية تحتية مصرّح له بس، من القائمة الموجودة في [صفحة الأدمن](../08-infrastructure/admin-readonly-verification-ar.md).
- الـ Lab ده مابينشئش ولا بيغيّر أي حساب أو مجموعة أو صلاحية. إعادة بناء الحسابات هي اليوم 5، وعلى سيرفر جديد معزول بس.

## المهام الموجّهة

### المهمة 1. ارسم الـ Topology من الـ Repository

| البند | التفاصيل |
|---|---|
| الأمر | `sed -n 1,60p docs/developer-handbook/08-infrastructure/runtime-architecture.md` (أو افتح الصفحة في Markdown viewer عشان الرسومات تظهر) |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | حسابك انت |
| النوع | للقراءة فقط |
| المتطلب المسبق | الـ Repository اتعمل له Checkout |
| النتيجة المتوقعة | هتشوف رسم الـ Network Topology: الإنترنت، وContainer الـ Proxy اللي بينشر البورتين 80 و 443، والخدمات الداخلية وراه |
| التحقق | على ورقة، اكتب شبكات Compose التلاتة (edge وops وinternal) وأنهي واحدة مالهاش مسار للإنترنت. اتأكد بـ `grep -n "internal: true" deploy/web-preview/compose.yaml` (VERIFIED IN CODE) |
| فشل محتمل | Mermaid مابيظهرش في Viewer عادي. استخدم صفحة GitHub أو أي Markdown viewer بيدعم Mermaid |

حدّد أنهي خدمات بتنشر بورت على الـ Host. الإجابة في `deploy/web-preview/compose.yaml`: شغّل `grep -n -A3 "ports:" deploy/web-preview/compose.yaml` واقرا كل نتيجة. قارن بالـ Sockets اللي بتسمع فعلًا في الجرد (البورتات العامة 22 و 80 و 443 هي VERIFIED LIVE). لاحظ إن فيه بورتات على الـ Loopback بس على السيرفر مش ظاهرة في ملف الـ Compose: البورت 3080 هو عملية Node تبع أدوات الـ AI Supervisor، وفيه عملية Java بتسمع على 18080 و 38189 ومالكها مش معروف (VERIFIED LIVE كـ Listeners بس). ماتفترضش إن أي واحدة منهم تبع Matbakhy.

### المهمة 2. اقرا حقائق الـ Host المتحقق منها

| البند | التفاصيل |
|---|---|
| الأمر | `sed -n '/^## 1. Host/,/^## 2. Accounts/p' docs/developer-handbook/08-infrastructure/contabo-server-inventory.md` |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | حسابك انت |
| النوع | للقراءة فقط |
| النتيجة المتوقعة | جدول بحقائق الـ Host |
| التحقق | املا الجدول ده من الصفحة، وضيف وسم الدليل لكل صف |

| الحقيقة | إجابتك | الوسم |
|---|---|---|
| نظام التشغيل | Ubuntu 24.04.5 LTS (Noble) | VERIFIED LIVE |
| الـ CPU والـ RAM | 8 vCPU، حوالي 23 GiB | VERIFIED LIVE |
| مجلد الـ Deployment | /srv/matbakhy-staging | VERIFIED LIVE (المجلد) وVERIFIED IN CODE (`.github/workflows/deploy-preview.yml` بيعمل Deploy هناك) |
| هل ufw نشط وبأنهي قواعد؟ | مش معروف | NOT VERIFIED (محتاج root) |
| أنهي Containers شغّالة دلوقتي؟ | مش معروف | NOT VERIFIED (محتاج الـ Docker socket) |

فشل محتمل: تكتب "verified" على حاجة موجودة في ملف الـ Compose بس. الـ Compose بيوصف النية؛ والحقائق اللي اتلاحظت على السيرفر نفسه هي بس اللي بتوصفه.

### المهمة 3. اقرا الحسابات والمجموعات

| البند | التفاصيل |
|---|---|
| الأمر | `sed -n '/^## 2. Accounts and groups/,/^## 3. SSH policy/p' docs/developer-handbook/08-infrastructure/contabo-server-inventory.md` |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | حسابك انت |
| النوع | للقراءة فقط |
| النتيجة المتوقعة | تلات حسابات: ubuntu (uid 1000)، وdevops (uid 1001)، وaiagent (uid 1002)، ومعاهم مجموعاتهم |
| التحقق | جاوب على الأسئلة اللي تحت من غير ما ترجع للصفحة |

أسئلة تجاوب عليها في ملاحظاتك:

1. أنهي حسابات في مجموعة sudo؟ (ubuntu وdevops، VERIFIED LIVE كعضوية في المجموعة؛ إنما الصلاحيات الفعلية اللي بتديها قواعد sudo NOT VERIFIED لأن /etc/sudoers.d مش مقروء)
2. أنهي حسابات في مجموعة docker؟ (devops بس، VERIFIED LIVE)
3. ليه aiagent مينفعش أبدًا يتضاف لـ docker؟ عضوية مجموعة docker بتخلي المستخدم يشغّل Container بيعمل Mount لنظام ملفات الـ Host، وده بيدّي صلاحيات تعادل root. ده سلوك عام في Linux وDocker (DOCUMENTED ONLY هنا) والجرد بيذكر القاعدة إن aiagent يفضل برّه sudo وdocker.
4. كل حساب بيعمل إيه تشغيليًا؟ الإجابة الصح: ده مش مثبت. ماتستنتجش دور من اسم حساب. اسم مستخدم الـ SSH بتاع الـ Deployment متخزّن في Secret الـ Repository اللي اسمه STAGING_SSH_USER، وقيمته NOT VERIFIED.
5. أنهي مجموعة مالهاش معنى موثّق في الـ Repository؟ users (gid 100).

### المهمة 4. اقرا ملكية المجلدات

| البند | التفاصيل |
|---|---|
| الأمر | `sed -n '/^## 4. Directories/,/^## 5. Network/p' docs/developer-handbook/08-infrastructure/contabo-server-inventory.md` |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | حسابك انت |
| النوع | للقراءة فقط |
| النتيجة المتوقعة | جدول بعناصر /srv مع المالك والمجموعة والـ Mode |
| التحقق | بالنسبة لـ /srv/matbakhy-staging جاوب: المالك، والـ Mode، ومين يقدر يكتب هناك، وإيه Mode ملف .env. المتوقع: devops:devops، 755، وبحسب الـ Mode بس المالك (وroot) هو اللي يقدر يكتب، وملف .env هو 600 |
| فشل محتمل | الخلط بين /srv/matbakhy-preview (نسخة قديمة) و /srv/matbakhy-staging الحالي. قسم الجرد "نسخة /srv/matbakhy-preview القديمة" بيشرح الفرق |

ملاحظات تحتفظ بيها: محتوى .env و /srv/matbakhy-staging/generated مااتقراش عمدًا (VERIFIED LIVE كـ Modes بس). مجلدات النسخ الاحتياطي /srv/matbakhy-backups و /srv/backups هي 750 devops، فـ aiagent مش قادر يعرض محتواها؛ وجود نسخة احتياطية ومدى حداثتها NOT VERIFIED.

### المهمة 5. دوّر على المكان اللي الـ Repository بيوصف فيه سياسة SSH

| البند | التفاصيل |
|---|---|
| الأمر | `sed -n '/^## 3. SSH policy/,/^## 4. Directories/p' docs/developer-handbook/08-infrastructure/contabo-server-inventory.md` |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | حسابك انت |
| النوع | للقراءة فقط |
| النتيجة المتوقعة | تعليمات SSH اللي اتقرت (PasswordAuthentication no وPubkeyAuthentication yes). دي تعليمات مقروءة بس؛ الإعداد الفعلي NOT VERIFIED |
| التحقق | اشرح ليه قيمة PermitRootLogin الفعلية NOT VERIFIED مع إن فيه سطر مقروء بيقول no. الإجابة: ملف Drop-in أحدث ممكن يلغي سطر أسبق، وملف Drop-in واحد مش مقروء للمستخدم من غير صلاحيات مرتفعة، وأمر root اللي بيطبع الإعداد الفعلي هو بس اللي يقدر يحسمها (وهو موجود في صفحة الأدمن) |

### المهمة 6. فرّق بين Hetzner وContabo

| البند | التفاصيل |
|---|---|
| الأمر | `sed -n '/^## 9. Historical Hetzner/,/^## Known limitations/p' docs/developer-handbook/08-infrastructure/contabo-server-inventory.md` وبعدين `sed -n 1,12p docs/operations/09-staging-hetzner-preparation.md` |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | حسابك انت |
| النوع | للقراءة فقط |
| النتيجة المتوقعة | الجرد بيذكر كلام فترة Hetzner اللي مبقاش بينطبق؛ ومستند العمليات مكتوب لـ Host مقترح على Hetzner |
| التحقق | كمّل الجملة دي: "The running Preview is on ___, so statements about ___ are historical (OUTDATED / INCORRECT)." المتوقع: Contabo؛ Hetzner |

### المهمة 7. خطّط لطلب للأدمن

من غير ما تشغّل أي حاجة، اختار تلات عناصر من ملخص NOT VERIFIED في الجرد (القسم 8) واكتب طلب من سطر واحد لأدمن بنية تحتية بيذكر رقم الأمر في [صفحة الأدمن](../08-infrastructure/admin-readonly-verification-ar.md). مثال على الصيغة: "Please run command <n> from the admin page and return sanitised output; do not include secrets." ماتبعتش الطلب، وماتشغّلش الأمر.

## معايير الإكمال

- [ ] رسمت الطوبولوجي وسمّيت شبكات Compose التلاتة والشبكة اللي هي internal.
- [ ] جدول الـ Host بتاعي عليه وسم الدليل الصح في كل صف وبيعلّم ufw وحالة الـ Containers كـ NOT VERIFIED.
- [ ] أقدر أذكر عضويات المجموعات لـ ubuntu وdevops وaiagent وأشرح خطر docker وsudo.
- [ ] ماادّعيتش مسؤولية أي حساب من اسمه.
- [ ] أقدر أقول أنهي مجلد هو الحالي (/srv/matbakhy-staging) وأنهي القديم.
- [ ] ماشغّلتش أي أمر بصلاحيات مرتفعة، وماقريتش أي ملف .env، ولا لمست السيرفر.

## اقرا إيه بعد كده

[جرد سيرفر Contabo](../08-infrastructure/contabo-server-inventory-ar.md)، [التحقق للقراءة فقط بواسطة الأدمن](../08-infrastructure/admin-readonly-verification-ar.md)، [معمارية التشغيل الخاصة بالـ Preview](../08-infrastructure/runtime-architecture-ar.md)، [الوصول للـ Live Preview](../05-developer-workflow/live-preview-access-ar.md).

التالي: [البنية التحتية اليوم 2](infra-day-2-docker-services-ar.md).

## القيود المعروفة وفجوات التوثيق

- الجرد لقطة (Snapshot) بتاريخ 2026-10-21. مابيتحدّثش لوحده؛ أعد التحقق قبل ما تعتمد عليه بعد Deployment أو تغيير من أدمن.
- دور كل حساب Linux وقواعد sudo وإعداد SSH الفعلي وحالة الـ Firewall وحالة الـ Containers هي NOT VERIFIED وبتحتاج أدمن مصرّح له.
- مستندات فترة Hetzner لسه موجودة في الـ Repository للتاريخ؛ والـ Lab ده بيشاور عليها بس عشان يقارنها بـ Host بتاع Contabo.
