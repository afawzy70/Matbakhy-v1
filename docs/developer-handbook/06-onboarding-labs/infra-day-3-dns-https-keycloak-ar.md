# البنية التحتية اليوم 3 - الـ DNS وsslip.io وCaddy وHTTPS وتوجيه Keycloak

> **Owner role:** Infrastructure and deployment
> **Status:** REFERENCE — Lab لفحص الـ DNS والـ TLS والتوجيه (Routing) الخاص بالـ Preview العام بأوامر عميل للقراءة فقط، ولقراءة إعدادات Caddy.
> **Language / اللغة:** [English](infra-day-3-dns-https-keycloak.md) · العربية

## مفتاح الأدلة

| الوسم | المعنى |
|---|---|
| VERIFIED LIVE | اتلاحظ يوم 2026-10-21 بواسطة المستخدم aiagent من غير صلاحيات مرتفعة على الـ Contabo VPS، باستخدام curl على الـ Hosts العامة من السيرفر نفسه. |
| VERIFIED IN CODE | اتقرا في الـ Repository ده (الملف مذكور). بيبيّن النية، مش بالضرورة اللي شغّال فعلًا. |
| DOCUMENTED ONLY | مستند أو السلوك المعتاد لأداة بيقول كده؛ ومفيش حاجة أكّدته هنا. |
| OUTDATED / INCORRECT | مكتوب لسيرفر Hetzner المقترح أو اتلغى بحاجة أحدث. |
| NOT VERIFIED | مااتلاحظش. |

## الأهداف

- تشرح sslip.io بيعمل إيه وليه الـ Preview مش محتاج حساب DNS.
- تفحص الـ DNS وشهادة الـ TLS وسلوك الـ HTTP لكل Host في الـ Preview باستخدام dig وcurl وopenssl.
- تشرح ليه `media.*` بيرد 403 على الجذر وليه `id.*` بيرد 302، وتوضّح إن الاتنين متوقّعين.
- تقرا الـ Caddyfile وتعرف أنهي Host بيوجّه لأنهي Container.
- تعرف إن Caddy بيجيب شهادات Let's Encrypt ويجدّدها، وإيه اللي متحقق منه وإيه اللي مش متحقق منه بخصوص التجديد.

## المتطلبات المسبقة

- اليوم 1 واليوم 2 خلصوا: [اليوم 1](infra-day-1-topology-accounts-ar.md) و[اليوم 2](infra-day-2-docker-services-ar.md).
- اقرا [الـ DNS والـ HTTPS وsslip.io](../08-infrastructure/dns-https-sslip-io-ar.md) و[الدخول على الـ Live Preview ورحلات الاستخدام التفاعلية](../05-developer-workflow/live-preview-access-ar.md).
- لابتوب متسطّب عليه dig وcurl وopenssl. مفيش أي حاجة بتتسطّب أو بتتغيّر على السيرفر.

## قواعد الـ Lab ده

- شغّل الأوامر اللي تحت من اللابتوب بتاعك بس وعلى الـ Hosts العامة. دي بتبعت طلبات قراءة عادية (استعلام DNS، طلب HTTPS، TLS Handshake). مابتعملش حساب ولا أوردر ولا رفع ملفات، ومابتستخدمش Credentials خالص.
- الـ Preview بيطبّق فئات الوصول (Access Classes) حسب عنوان المتصل (CUSTOMER_ALLOWED_IPS وSTAFF_ALLOWED_IPS في `deploy/web-preview/Caddyfile`). لو جيت من شبكة برّه الفئة ممكن تاخد 403 بشكل طبيعي ومعاها نص بسيط "Not available". ده رد من سياسة الوصول، مش عطل. النتايج اللي اتلاحظت في الـ Lab ده اتاخدت من عنوان السيرفر نفسه، فنتايجك ممكن تختلف.
- ماتطلبش شهادات، وماتغيّرش الـ DNS، وماتشغّلش اختبار ضغط (Load). ماتبعتش طلبات كتير على الـ Hosts؛ طلب واحد لكل أمر كفاية.
- بدّل `<preview-domain>` بدومين الـ Preview الموثّق 167-86-90-174.sslip.io (عام في الـ Repository).

## المهام الموجّهة

### المهمة 1. افهم sslip.io عن طريق حل اسم (DNS Resolution)

| البند | التفاصيل |
|---|---|
| الأمر | `dig +short A app.<preview-domain>` |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | حسابك انت |
| النوع | للقراءة فقط (استعلام DNS عام) |
| المتطلب المسبق | dig متسطّب |
| النتيجة المتوقعة | بيطبع 167.86.90.174 (VERIFIED LIVE: الأسماء بتتحل على العنوان ده) |
| التحقق | شغّل `dig +short A app.<preview-domain> @1.1.1.1` وقارن؛ الإجابتين لازم يتطابقوا |
| فشل محتمل | الناتج فاضي. راجع الإملاء (النقط والشرطات)، والـ Resolver، أو جرّب الأمر التاني. لو الاتنين فاضيين ممكن الخدمة العامة واقعة؛ بلّغ عن ده، ماتحاولش تلف حواليه |

اشرح في ملاحظاتك ليه ده بيشتغل: خدمة sslip.io بترد على أي Hostname فيه عنوان IPv4 مكتوب جواه بنفس العنوان ده (DOCUMENTED ONLY، موصوف في [الـ DNS والـ HTTPS وsslip.io](../08-infrastructure/dns-https-sslip-io-ar.md)). محدش من ناحية Matbakhy بيعمل سجلات DNS للـ Preview. هل بيرجع سجل AAAA ولا لأ؟ ده NOT VERIFIED؛ السيرفر عنده فعلًا عنوان IPv6 عام (VERIFIED LIVE) لكن ده مش دليل على وجود سجل DNS ليه.

### المهمة 2. افحص شهادة الـ TLS

| البند | التفاصيل |
|---|---|
| الأمر | `openssl s_client -connect app.<preview-domain>:443 -servername app.<preview-domain> </dev/null 2>/dev/null \| openssl x509 -noout -issuer -subject -dates` |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | حسابك انت |
| النوع | للقراءة فقط (TLS Handshake) |
| المتطلب المسبق | openssl متسطّب |
| النتيجة المتوقعة | الجهة المصدِّرة (Issuer) هي Let's Encrypt؛ وبين notBefore وnotAfter حوالي 90 يوم. وقت الملاحظة كانت شهادة الـ Host بتاع app صالحة من 2026-10-18 لحد 2027-01-16 (VERIFIED LIVE للـ Host بتاع app بس)؛ أي تجديد أحدث هيظهر بتواريخ أبعد. الـ Issuer وتواريخ الـ Hosts التانية (ops وapi وid وkc-admin وmedia) ماتسجّلتش: كرّر الأمر لكل واحد فيهم، واعتبر أي اختلاف، حتى لو جهة إصدار تانية، ملاحظة مش خطأ |
| التحقق | احسب عدد الأيام بين التاريخين. قارن تاريخ notAfter بتاريخ النهارده واكتب كام يوم فاضل |
| فشل محتمل | الناتج الفاضي معناه إن مفيش Handshake حصل. الخيار `-servername` مطلوب لأن الـ Proxy بيختار الشهادة حسب الاسم المطلوب |

أسئلة: مين بيجيب الشهادة دي؟ Caddy، أوتوماتيكي، باستخدام الإيميل في ACME_EMAIL (VERIFIED IN CODE: الـ Global Block في `deploy/web-preview/Caddyfile` و`deploy/web-preview/compose.yaml`). الشهادات بتتخزّن فين؟ في الـ Named Volume اللي اسمه caddy-data ومتعمل له Mount على /data في Container الـ Proxy (VERIFIED IN CODE؛ محتوياته على السيرفر الحي NOT VERIFIED LIVE). هل التجديد حصل فعلًا على السيرفر الحي؟ NOT VERIFIED: Caddy بيجدّد أوتوماتيكي (DOCUMENTED ONLY)، لكن محدش لاحظ حدث تجديد. كرّر الأمر ده لما تاريخ الانتهاء يقرّب (صفحة الـ DNS والـ HTTPS بتوصف فترة التجديد) وسجّل اللي شفته.

### المهمة 3. افحص سلوك الـ HTTP لكل Host

شغّل كل أمر مرة واحدة وسجّل الحالة.

| الأمر | الجهاز | مستخدم Linux | النوع | النتيجة المتوقعة (VERIFIED LIVE من عنوان السيرفر) | لو اختلفت |
|---|---|---|---|---|---|
| `curl -sS -o /dev/null -w '%{http_code}\n' https://app.<preview-domain>/` | اللابتوب بتاعك | أي مستخدم | للقراءة فقط | 200 | 403 مع "Not available": انت برّه فئة العملاء. Timeout: مش ممكن توصل للبورت 443 من شبكتك |
| `curl -sS -o /dev/null -w '%{http_code}\n' https://ops.<preview-domain>/` | اللابتوب بتاعك | أي مستخدم | للقراءة فقط | 200 | 403: انت برّه فئة الموظفين (طبيعي لمعظم الشبكات) |
| `curl -sS https://api.<preview-domain>/api/v1/configuration/public` | اللابتوب بتاعك | أي مستخدم | للقراءة فقط | 200 مع JSON فيه orderingAvailable true واسم البيئة PREVIEW، demo true | 403: برّه فئة العملاء |
| `curl -sS -o /dev/null -w '%{http_code}\n' https://api.<preview-domain>/actuator/health` | اللابتوب بتاعك | أي مستخدم | للقراءة فقط | 404، لأن الـ Proxy بيوجّه /api/* و/oauth2/* و/login/oauth2/* و/logout بس (VERIFIED LIVE وVERIFIED IN CODE) | لو رجع 200 يبقى فيه خلل في التوجيه؛ بلّغ عنه |
| `curl -sS -I https://id.<preview-domain>/` | اللابتوب بتاعك | أي مستخدم | للقراءة فقط | 302 مع Header اسمه Location بيشاور على /admin/ في الـ Host بتاع kc-admin | 403: برّه فئة العملاء |
| `curl -sS -I https://kc-admin.<preview-domain>/` | اللابتوب بتاعك | أي مستخدم | للقراءة فقط | 302 مع Location بقيمة /admin/master/console/ طول ما الـ Flag بتاع الـ Admin Console موجود | 404: الـ Flag مش موجود والـ Host مقفول (بيرفض في حالة الشك، Fail Closed، بالتصميم) |
| `curl -sS -D - -o /dev/null https://media.<preview-domain>/` | اللابتوب بتاعك | أي مستخدم | للقراءة فقط | 403 مع Content-Type بقيمة application/xml وHeader اسمه x-amz-request-id | 403 مع النص "Not available" ومن غير x-amz-request-id معناه إن الـ Proxy رفضك (فئة الوصول) |

ماتفتحش أبدًا الـ Console بتاع kc-admin وماتحاولش تسجّل دخول فيه كجزء من الـ Lab ده. هو محتاج Password وOne-Time Password، وهو مش هدف للتدريب.

### المهمة 4. اشرح الـ 403 بتاع media (متوقّع)

1. افتح `deploy/web-preview/Caddyfile` ودوّر على الـ Block بتاع `media.{$PREVIEW_DOMAIN}`. هو بيطبّق فحص وصول العملاء وبعدها `reverse_proxy storage:8333` (VERIFIED IN CODE).
2. الرد الحي كان فيه Content-Type بقيمة application/xml وHeader اسمه x-amz-request-id (VERIFIED LIVE). رفض Caddy نفسه بيكون نص بسيط من غير Header زي ده، يعني الرد جه من بوابة S3 (SeaweedFS) اللي ورا Caddy. المسار الجذر عبارة عن طلب سرد الـ Buckets من غير تسجيل دخول، وS3 بترفضه من غير Credentials.
3. الـ Objects العامة موجودة تحت البادئة /matbakhy-public/ (VERIFIED IN CODE: OBJECT_STORAGE_PUBLIC_BASE_URL في `deploy/web-preview/compose.yaml`). فالـ 403 على المسار الجذر هو النتيجة المتوقعة. كود الخطأ بالظبط في الـ Body مااتخزّنش ومش بندّعيه هنا.
4. اكتب جملة واحدة عن إزاي هتفرّق بين "403 متوقّع" و"الـ media واقعة": الخدمة الواقعة بتدّي 502 أو خطأ اتصال، مش 403 بشكل S3 (NOT VERIFIED؛ مستنتج من طريقة شغل الـ Reverse Proxy).

### المهمة 5. اشرح الـ 302 بتاع id (متوقّع)

1. في `deploy/web-preview/compose.yaml`، الخدمة identity، دوّر على KC_HOSTNAME (https://id.<domain>) وKC_HOSTNAME_ADMIN (https://kc-admin.<domain>) (VERIFIED IN CODE).
2. لاحظ الـ Header اسمه Location من المهمة 3: هو بيشاور على Host الأدمن. السبب الأرجح إن Keycloak بيحوّل الجذر بتاعه لـ URL الأدمن لأن Hostname الأدمن متظبّط. ده استنتاج من الإعدادات والـ Header؛ ومااتأكدناش منه من جوّه Keycloak.
3. دوّر في الـ Caddyfile وهتلاقي إن المسارين `/admin` و `/realms/master` بيردوا 404 على الـ Host بتاع id، فالـ Admin Console مابيتقدّمش هناك أبدًا (VERIFIED IN CODE).
4. تسجيل دخول العميل بيستخدم `/realms/matbakhy/...`، مش الجذر. شغّل `curl -sS https://id.<preview-domain>/realms/matbakhy/.well-known/openid-configuration` (للقراءة فقط؛ 200 مع issuer بينتهي بـ /realms/matbakhy من فئة العملاء، DOCUMENTED ONLY هنا لأن الحقائق اللي اتلاحظت على السيرفر مااتسجّلش فيها الطلب ده؛ والـ Health Check في compose بيطلب نفس المسار جوّه الـ Container، VERIFIED IN CODE).
5. وضّح ليه ده مش خطأ وإيه اللي ماينفعش تعمله: ماتضيفش Redirect ولا Rewrite على الـ Host بتاع id عشان "تصلّحه".

### المهمة 6. اقرا الـ Caddyfile

| البند | التفاصيل |
|---|---|
| الأمر | `sed -n 1,66p deploy/web-preview/Caddyfile` |
| الجهاز | اللابتوب بتاعك |
| مستخدم Linux | حسابك انت |
| النوع | للقراءة فقط |
| النتيجة المتوقعة | الإعداد العام (Global Option) بتاع الإيميل، والـ Snippet المشترك للـ Headers، والـ Snippets بتاعة الوصول الاتنين، والـ Site Blocks بتاعة app وops |
| التحقق | كمّل الجدول اللي تحت من الملف |

| الـ Host | بيقدّم أو بيوجّه إلى | فحص الوصول |
|---|---|---|
| app | الويب الثابت للعميل من /srv/customer-web | فئة العملاء |
| ops | ويب العمليات (Operations Web) الثابت من /srv/operations-web | فئة الموظفين |
| api | api:8080 للمسارات /api/* و /oauth2/* و /login/oauth2/* و /logout؛ وأي حاجة تانية 404 | فئة العملاء لكل الطلبات؛ وللمسارين /api/v1/ops و /api/v1/cook بيتفحص فئة الموظفين الأول وبعدها فئة العملاء كمان، يعني المتصل من الموظفين لازم يكون في القائمتين |
| id | identity:8080، ما عدا مسارات الأدمن والـ master realm (404) | فئة العملاء |
| kc-admin | identity:8080 لمسارات الـ Console بس، وطول ما الـ Flag موجود بس | مفيش تقييد حسب العنوان؛ Password بتاع Keycloak زائد One-Time Password |
| media | storage:8333 | فئة العملاء |

بعد كده شغّل `python3 scripts/test_proxy_access.py` لو عندك المتطلبات اللي بيذكرها بس: هو بيشغّل الـ Caddyfile الحقيقي في Container بتاع Caddy، فلازم Docker وImage بتاع Caddy يكونوا موجودين فعلًا على اللابتوب بتاعك، ومن غيرهم الاختبار بيتخطّى (Skipped). الاختبار المتخطّى يعتبر NOT RUN، مش PASS. اقرا بداية السكريبت الأول (هو بيختبر الـ Caddyfile الموجود في الـ Repository، VERIFIED IN CODE). ماتبلّغش PASS عن اختبار ماشغّلتهوش.

### المهمة 7. خطّط (ماتشغّلش) لاستبدال الدومين

اقرا القسم 6 من [الـ DNS والـ HTTPS وsslip.io](../08-infrastructure/dns-https-sslip-io-ar.md)، "Domain replacement runbook". اكتب خطة بنص صفحة بتجاوب على: أنهي متغيّرات بتتغيّر، وفي أنهي ملفات، وإيه اللي لازم يكون متاح الوصول ليه من الإنترنت قبل ما الشهادة تتصدر (البورتات 80 و 443)، وإيه اللي هيحصل للحسابات الموجودة في التطبيق (الـ API بيعرّف الحساب بالـ issuer والـ subject، فقيمة OIDC_ISSUER الجديدة مش هتطابق الحسابات الموجودة إلا لو نقلتها أو قررت تعمل Reset)، وترتيب تغيير إعدادات السيرفر والـ Merge على develop (أي Push على develop بيعمل Deployment أوتوماتيكي بالدومين اللي في .env على السيرفر في اللحظة دي)، وإزاي ترجع في الاستبدال (Rollback)، بما في ذلك إعدادات Keycloak المتخزّنة جوّه Keycloak نفسه ورجوع .env مابيلغيهاش. ماتنفّذش أي خطوة؛ استبدال الدومين عملية بتغيّر الحالة ومخصوصة لمهندس مصرّح له.

## نقاط التحقق

| نقطة التحقق | بتنجح لما |
|---|---|
| DNS | حلّيت اسمين على الأقل من Hosts الـ Preview وشرحت ليه مفيش حساب DNS |
| TLS | سجّلت الجهة المُصدرة وتواريخ الصلاحية والأيام المتبقية، وذكرت إيه اللي NOT VERIFIED بخصوص التجديد |
| جدول HTTP | الصفوف السبعة كلها متملية بالحالة اللي انت لاحظتها بنفسك، وأي اختلاف عن الحقائق الحية متفسّر بفئة الوصول |
| media وid | تقدر تشرح الرد (Response) بتاع كل واحد بكلامك انت ومعاه وسوم الأدلة |
| Caddy | جدول التوجيه كامل ومطابق للملف |

## معايير الإكمال

- [ ] شغّلت أوامر عميل للقراءة فقط على الـ Hosts العامة، وطلب واحد لكل أمر.
- [ ] أقدر أشرح sslip.io وإصدار Let's Encrypt بواسطة Caddy ومكان تخزين الشهادات.
- [ ] أقدر أشرح ليه media بترد 403 على الجذر وليه id بترد 302، وإن ولا واحد منهم خطأ.
- [ ] عارف إن التجديد موثّق بس مااتلاحظش، وإيه اللي يقدر الأدمن يتحقق منه.
- [ ] مافتحتش الـ Admin Console بتاع Keycloak وماحاولتش أسجّل دخول فيه.

## اقرا إيه بعد كده

[الـ DNS والـ HTTPS وsslip.io](../08-infrastructure/dns-https-sslip-io-ar.md)، [معمارية التشغيل الخاصة بالـ Preview](../08-infrastructure/runtime-architecture-ar.md)، [الوصول لبيئة المعاينة الحية](../05-developer-workflow/live-preview-access-ar.md)، [الهوية والأمان](../03-architecture/identity-and-security-ar.md).

التالي: [البنية التحتية اليوم 4](infra-day-4-cicd-operations-ar.md).

## القيود المعروفة وفجوات التوثيق

- الحالات اللي فوق اتلاحظت من عنوان السيرفر نفسه. النتايج من شبكات تانية بتعتمد على فئات الوصول وممكن تبقى 403 "Not available".
- تجديد الشهادة وحالة سجل AAAA هما NOT VERIFIED.
- طلب اكتشاف الـ OIDC في المهمة 5 مااتسجّلش في الحقائق اللي اتلاحظت على السيرفر.
