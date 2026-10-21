# التحقق للقراءة فقط بواسطة الأدمن

> **Owner role:** Infrastructure and deployment
> **Status:** REFERENCE — قائمة ثابتة من أوامر القراءة فقط لأدمن بنية تحتية مخوّل؛ ولا واحد منها اتشغّل بواسطة كاتب الصفحة دي
> **Language / اللغة:** [English](admin-readonly-verification.md) · العربية

الحساب العادي (من غير صلاحيات إدارية) اللي عمل [جرد سيرفر Contabo](contabo-server-inventory-ar.md) مقدرش يشوف كل حاجة. الصفحة دي بتسرد أقل مجموعة أوامر بتسدّ الفجوات دي، ومنها الـ Commit والـ Image Digests المنشورة فعلًا (الأمرين 17 و 18). كل أمر بيقرا بس. النتايج بتأكد الجرد أو بتصحّحه؛ ومابتدّيش تصريح بأي تغيير أبدًا.

## مفتاح الأدلة

| الوسم | المعنى |
|---|---|
| VERIFIED LIVE | اتلاحظ على السيرفر يوم 2026-10-21 بواسطة المستخدم aiagent اللي من غير صلاحيات إدارية. |
| VERIFIED IN CODE | اتقرا في الـ Repository (الملف مذكور). |
| DOCUMENTED ONLY | مستند أو اصطلاح خاص بأداة بيقول كده؛ ومش متأكد منه هنا. |
| OUTDATED / INCORRECT | اتلغى بحاجة أحدث أو مكتوب لفترة Hetzner. |
| NOT VERIFIED | ماتلاحظش؛ الأوامر اللي تحت موجودة عشان تتحقق منه. |

## قواعد للكل

- شغّل الأوامر دي بس كأدمن بنية تحتية مخوّل، على الـ VPS بتاع Contabo، بحسابه الشخصي. الـ Agents مابيشغّلوهاش أبدًا.
- aiagent مينفعش ياخد sudo ولا صلاحيات Docker أبدًا، ولا حتى مؤقتًا، عشان يشغّل القائمة دي. الأدمن هو اللي بيشغّل الأوامر ويرجّع مخرجات اتنضّفت من الأسرار.
- كل الأوامر للقراءة فقط. لو أي أمر طلب تغيير حاجة، وقّف.
- متشغّلش `docker inspect` من غير `--format` أبدًا، ولا `docker compose config`، ولا `docker compose exec` جوه أي Service، ولا `cat` على .env، ولا أي حاجة بتطبع متغيّرات البيئة. دول بيكشفوا الأسرار.
- متلصقش مخرجات خام في Issue أو Pull Request عام أبدًا (الـ Repository عام).
- سجّل التاريخ ودور الأدمن (مش اسم شخصي) مع النتيجة بعد التنضيف. النتايج اللي اتسجّلت قبل الـ Deployment الجاي ممكن تبقى مش مواكبة للوضع بعده.

## المتغيّرات البديلة (Placeholders)

| المتغيّر البديل | المعنى |
|---|---|
| `<deploy-dir>` | /srv/matbakhy-staging/deploy/web-preview (VERIFIED LIVE كمسار؛ الـ Workflow بيعمل Deploy هناك، VERIFIED IN CODE في `.github/workflows/deploy-preview.yml`) |
| `<project>` | matbakhy-preview، اسم المشروع المتظبّط في `deploy/web-preview/compose.yaml` (VERIFIED IN CODE) |
| `<caddy-volume>` | الاسم اللي بيطبعه الأمر 8 لـ Volume بيانات Caddy؛ حسب تسمية Compose، المتوقع إنه `<project>_caddy-data` (DOCUMENTED ONLY، اصطلاح Compose) |
| `<mountpoint>` | المجلد اللي بيطبعه الأمر 9 |

## الأوامر

كل الأوامر بتشتغل على الـ VPS، بواسطة أدمن مخوّل، مع `sudo` إلا لو حساب الأدمن ده معاه صلاحيات Docker أصلًا. "RO" معناها للقراءة فقط.

| # | الأمر | بيشغّله | RO | الغرض والدليل المتوقع | متلصقش، والفشل المحتمل |
|---|---|---|---|---|---|
| 1 | `sudo sshd -T` | الأدمن عبر sudo | أيوه | إعدادات SSH الفعّالة. دوّر على السطور permitrootlogin وpasswordauthentication وkbdinteractiveauthentication وpubkeyauthentication وx11forwarding وallowusers وallowgroups وauthorizedkeysfile وauthorizedkeyscommand. المتوقع passwordauthentication no؛ وpermitrootlogin بيبيّن القيمة الفعلية الحقيقية (الملفات المقروءة كانت متناقضة). | الصق السطور دي بس. المخرجات الكاملة بتسرد كمان مسارات ملفات مفاتيح الـ Host؛ ومفيهاش محتوى مفاتيح، بس مش محتاجينها. الفشل: "command not found" معناها إن /usr/sbin مش في الـ PATH؛ استخدم `sudo /usr/sbin/sshd -T`. |
| 2 | `sudo grep -E '^(PREVIEW_DOMAIN\|GENERATED_DIR\|BACKUP_DIR\|QA_ARTIFACT_DIR\|COMPOSE_OVERRIDE\|ALLOWED_CLIENT_IPS\|CUSTOMER_ALLOWED_IPS\|STAFF_ALLOWED_IPS)=' <deploy-dir>/.env` | الأدمن عبر sudo | أيوه | بيأكد المجلدات والـ Override بتاع الحجم اللي الإعداد الفعلي بيستخدمهم، وفئات الوصول (Access Classes) المتظبّطة. المتوقع من الكود: أسماء المفاتيح موجودة (`deploy/web-preview/.env.example`)؛ القيم NOT VERIFIED. | متوسّعش الـ Pattern لمفاتيح تانية أبدًا: كل حاجة تانية في .env سر. قوائم الـ IP التلاتة دي تفاصيل تحكم في الوصول؛ الصقها بس لو التذكرة خاصة. |
| 3 | `sudo ufw status verbose` وكمان `sudo ufw status numbered` لو محتاج أرقام القواعد | الأدمن عبر sudo | أيوه | الـ Firewall بتاع الـ Host مفعّل ولا لأ، والقواعد الواردة (Inbound) الموجودة إيه. المتوقع "Status: active" مع السماح بـ 22 و 80 و 443، أو "inactive" لو بيستخدموا الـ Firewall بتاع المزوّد بدله (NOT VERIFIED أنهي واحد). الأمر `verbose` بيعرض الإعدادات الافتراضية والقواعد من غير أرقام؛ و`numbered` بس هو اللي بيرقّمها. | القواعد ممكن تسرد عناوين مصدر للأدمن. الصق أرقام القواعد والمنافذ، مش العناوين. |
| 4 | `sudo ls -l /etc/sudoers.d` | الأدمن عبر sudo | أيوه | أسماء ملفات الـ Drop-in بتاعة sudoers بس. أسماء الملفات مابتبيّنش إيه المسموح لكل حساب، و`visudo -c` بيفحص الصياغة بس. عشان تثبت الصلاحيات الفعلية شغّل كمان `sudo -l -U ubuntu` و`sudo -l -U devops` و`sudo -l -U aiagent`، وقارنها بمجموعة sudo المتحقق منها (ubuntu, devops). المتوقع إن aiagent يتقاله إنه مش مسموح له يشغّل sudo. | متعملش `cat` للملفات دي في تذكرة أبدًا. استخدم `sudo visudo -c` بس لو عايز فحص الصياغة (Syntax) كمان؛ هو مابيطبعش أي قواعد. ارجّع ملخص منقّح لنتيجة كل `sudo -l -U` (فئات الأوامر المسموحة، زي "كل الأوامر" أو "ولا أمر")، مش القواعد الخام. |
| 5 | `sudo docker ps --all --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'` و `sudo docker compose ls --all` | أدمن عنده صلاحيات Docker | أيوه | إيه الـ Containers بتاعة Matbakhy الموجودة وحالة صحتها. المتوقع الـ Services المعرّفة في `deploy/web-preview/compose.yaml` (proxy, postgres, identity, storage, api, loki, alloy, prometheus, alertmanager, alert-sink, node-exporter, grafana) تحت المشروع matbakhy-preview. الـ Images المفروض تكون متثبّتة بالـ Digest. ضيف `sudo docker images --digests` عشان تشوف الـ Digests. | الـ Images والأسماء مش سر. الفشل: "permission denied" معناها إن الحساب ملوش صلاحيات Docker؛ متضيفش أي حساب لمجموعة docker عشان الأمر ده. |
| 6 | `sudo docker network ls` و `sudo docker network inspect --format '{{.Name}} {{.Internal}}' $(sudo docker network ls -q)` | أدمن عنده صلاحيات Docker | أيوه | بيأكد الشبكات التلاتة للمشروع (edge, ops, internal) وإن الشبكة internal معلّمة internal true، زي ما `deploy/web-preview/compose.yaml` بيعرّف (VERIFIED IN CODE). بتطابق الـ br-* bridges التلاتة اللي اتشافوا Live. | مفيش أسرار بتتطبع. الـ Command Substitution جزء من سطر واحد للقراءة فقط. |
| 7 | `sudo docker inspect --format '{{.Name}} mem={{.HostConfig.Memory}} cpu={{.HostConfig.NanoCpus}} restart={{.HostConfig.RestartPolicy.Name}} health={{if .State.Health}}{{.State.Health.Status}}{{end}} restarts={{.RestartCount}}' $(sudo docker ps -aq)` | أدمن عنده صلاحيات Docker | أيوه | حدود الذاكرة والـ CPU المطبّقة وسياسة إعادة التشغيل (Restart Policy) وحالة الصحة لكل Container. قارنها بـ `mem_limit` و`cpus` في ملف الـ Compose (VERIFIED IN CODE، مثلًا api 2g و 1.5 CPU). | الـ `--format` إلزامي. `docker inspect` العادي بيطبع متغيّرات البيئة بكلمات السر. |
| 8 | `sudo docker volume ls --filter label=com.docker.compose.project=<project>` | أدمن عنده صلاحيات Docker | أيوه | أسماء الـ Volumes الحقيقية. المتوقع عشرة Volumes زي ما ملف الـ Compose بيعرّف، بالـ Prefix بتاع المشروع. | أسماء بس. |
| 9 | `sudo docker volume inspect --format '{{.Name}} {{.Mountpoint}}' <caddy-volume>` | أدمن عنده صلاحيات Docker | أيوه | مجلد الـ Host لـ Volume بيانات Caddy (الأمر 10 محتاجه). المتوقع تحت /var/lib/docker/volumes (DOCUMENTED ONLY). | المسار بس. متقراش Volumes تانية: postgres-data و storage-data فيهم بيانات التطبيق. |
| 10 | `sudo find <mountpoint>/caddy/certificates -name '*.crt'` وبعدين، لكل ملف في القائمة، `sudo openssl x509 -noout -subject -issuer -dates -in <file>` | الأدمن عبر sudo | أيوه | صاحب الشهادة (Subject) والجهة المصدِرة (Issuer) وتواريخ الصلاحية لكل Host. الدليل الحي بيوري إن شهادة الـ app host من Let's Encrypt وصالحة من 2026-10-18 إلى 2027-01-16 (VERIFIED LIVE من برّه)؛ الأمر ده بيأكد الشهادات المخزّنة حاليًا لكل الـ Hosts، ومنها الـ app host، وصلاحيتها. مابيثبتش إن التجديد التلقائي بينجح؛ التجديد يفضل NOT VERIFIED. هيكل المجلدات تحت caddy/ هو اصطلاح Caddy، ومش مقروء في الـ Repository ده (DOCUMENTED ONLY، NOT VERIFIED). | متفتحش ملفات بتنتهي بـ .key ولا أي حاجة تحت مجلد حساب acme أبدًا؛ دي مفاتيح خاصة. التواريخ وأسماء الـ Subject ممكن تتلصق. |
| 11 | `sudo find /srv -maxdepth 3 -printf '%M %u:%g %p\n'` | الأدمن عبر sudo | أيوه | الـ Mode والمالك والمجموعة لمجلدات /srv وكل اللي تحتها، بما فيها حقول المجموعة اللي العرض بالحساب العادي مقدرش يسجّلها، والعناصر جوه /srv/matbakhy-backups و /srv/backups و /srv/deployments (أسماء بس). | أسماء ملفات الـ Backup مش سر، بس متضيفش `-exec cat`. متوسّعش `-maxdepth` جوه مجلد generated؛ أسماء ملفاته كفاية. |
| 12 | `sudo crontab -l -u devops`، ونفس الأمر مع `-u ubuntu` و`-u aiagent`، وكمان `sudo ls -l /var/spool/cron/crontabs /etc/cron.d /etc/cron.daily /etc/cron.weekly` | الأدمن عبر sudo | أيوه | هل فيه مهمة Backup أو Prune بتاعة Matbakhy مجدولة، وعلى أنهي حساب. المتوقع مدخل `deploy.sh backup` لو الممارسة المذكورة في `docs/operations/12-owner-server-setup.md` (DOCUMENTED ONLY) اتّبعت. بلّغ عن كل جدول لوحده: غير موجود ("no crontab for <user>")، أو مش ممكن الوصول له (خطأ صلاحيات)، أو ماتفحصش (الحساب أو المجلد ماتشيّكش عليه). عرض الملفات في الـ Spool بيبيّن الأسماء بس، مش مهام الحسابات التانية، والجداول بتاعة النظام في /etc/cron.* لازم تتقرا أو تتعرض لوحدها؛ والـ Timers بتاعة systemd بيغطيها الأمر 15. | سطور Cron ممكن تضمّن بيانات اعتماد أو Tokens. امسحها (Redact) عندك الأول قبل ما تلصق أي حاجة؛ الصق وقت الجدولة واسم السكريبت بس. الفشل: "no crontab for devops" معناها إن الحساب ده ملوش Crontab؛ ومش بتغطي الحسابات التانية ولا جداول النظام. |
| 13 | `sudo journalctl -u ssh --since 2026-10-17 -g 'Accepted publickey' --no-pager` و `sudo wc -l /home/ubuntu/.ssh/authorized_keys /home/devops/.ssh/authorized_keys /home/aiagent/.ssh/authorized_keys` | الأدمن عبر sudo | أيوه | أنهي حسابات قبلت دخول بمفتاح عام (بيبيّن أنهي حسابات دخلت بالمفتاح؛ الـ Secret STAGING_SSH_USER في الـ Repository مش مقروء) وعدد سطور كل ملف. `wc -l` بيعدّ السطور، بالتعليقات والسطور الفاضية، مش المفاتيح، والمسارات مفترضة إنها الافتراضية: استخدم سطور authorizedkeysfile وauthorizedkeyscommand من الأمر 1 عشان تعرف مصدر المفاتيح الفعلي، وده NOT VERIFIED. السجلات دي بتبيّن عمليات دخول SSH الناجحة بس. عشان تربط واحدة منها بالـ Deployment، قارن وقتها بتشغيل معروف لـ Workflow الـ Deployment على GitHub Actions، وبلّغ عن أي لبس فاضل (مثلًا لو أكتر من حساب دخل في الوقت ده). اسم الـ Unit `ssh` هو اسم Ubuntu 24.04 وبيطابق خدمة ssh النشطة (VERIFIED LIVE). | متلصقش عناوين IP لأجهزة الـ SSH Clients المتصلة ولا بصمات المفاتيح. بلّغ اسم الحساب وعدد السطور (اكتب جنبه "عدد سطور بس") والتاريخ بس. متقراش محتوى authorized_keys أبدًا. الملف المفقود إجابة صالحة. |
| 14 | `df -h` و `sudo docker system df` | أدمن (وصلاحيات Docker للأمر التاني) | أيوه | المساحة الفاضية على القرص، وحجم اللي بتستخدمه Docker Images والـ Containers والـ Volumes والـ Build Cache. الوضع الحالي: 18 GB من 290 GB مستخدمة على /dev/sda1. | أحجام بس. |
| 15 | `systemctl list-timers --all --no-pager` | أي حساب أدمن | أيوه | قائمة الـ Timers الكاملة، ومنها هل فيه أي حاجة غير الـ Timer بتاع استرجاع الـ AI Supervisor ليها علاقة بـ Matbakhy. | أسماء الـ Units بس. |
| 16 | `sudo ss -tlnp`، وبعدين `ps -o user=,pid= -p <pid>` لكل PID بيظهر في ss على المنافذ دي | الأدمن عبر sudo | أيوه | أسماء العمليات اللي ورا الـ Listeners، عشان نأكد الـ Proxy على 80 و 443 وGrafana على 127.0.0.1:3000 ومالك عملية Java على 127.0.0.1 على المنافذ 18080 و 38189. `ss` بيعرض الـ PID مش اسم المستخدم، عشان كده بييجي بعده البحث بـ `ps` (المستخدم والـ PID بس). | الصق أسماء العمليات والمنافذ واسم حساب كل PID؛ الـ Arguments بتاعة العمليات ممكن تحتوي أسرار، فمتستخدمش `ps` بسطور الأوامر الكاملة في التذاكر. |
| 17 | `sudo -u devops <deploy-dir>/deploy.sh status` | الأدمن عبر sudo، بالحساب اللي بيملك مجلد الـ Deploy (devops هو المالك، VERIFIED LIVE) | أيوه | قيمة RELEASE_COMMIT المتظبّطة، و API_IMAGE و FRONT_IMAGE المثبّتين، ورقم إصدار الـ Database Schema، وحالة الـ Containers، وآخر خمس سطور من الـ Release Log (VERIFIED IN CODE: الدالتين status و record_release في `deploy/web-preview/deploy.sh`؛ وتعليق أول السكريبت بيوصف status بإنه للقراءة فقط). ده الإصدار المتظبّط والمسجّل، مش دليل على اللي شغّال فعلًا. | السكريبت بيحمّل .env في العملية بتاعته هو بس، لكنه بيطبع الحقول اللي فوق بس. ماتستخدمش `cat .env` بدله. الفشل: "missing .env" معناها إن الحساب أو المجلد غلط؛ و"none recorded" معناها مفيش ملف Release Log في BACKUP_DIR. |
| 18 | `sudo docker inspect --format '{{.Name}} {{.Config.Image}} {{.Image}} revision={{index .Config.Labels "org.opencontainers.image.revision"}}' $(sudo docker ps -q)` | أدمن عنده صلاحيات Docker | أيوه | مرجع الـ Image والـ Image Id اللي بيستخدمه كل Container شغّال. قارن الـ Digests بنتيجة الأمر 17. البناء بيحط علامة الـ revision على الـ Docker Images الاتنين بتوع التطبيق (VERIFIED IN CODE: `docker build --label org.opencontainers.image.revision` في `.github/workflows/deploy-preview.yml` و`.github/workflows/preview-images.yml`). وجود العلامة على الـ Containers الشغّالة NOT VERIFIED LIVE؛ علامات الـ Container ممكن تغلّب علامات الـ Image، فالقيمة الفاضية مش معناها إن الـ Image مش متبنية بالـ Workflows دي: افحص الـ Image اللي في حقل Image بتاع الـ Container قبل أي استنتاج. ولا قيمة منهم دليل على الـ Commit المصدر لوحدها. | الـ `--format` إلزامي. الأسماء ومراجع الـ Images والـ Ids مش سر. |

علامات الـ Pipe في الأمر 2 مكتوبة بـ Escape عشان الجدول. اكتبها من غير Escape (خط رأسي واحد) في الـ Shell.

## إيه اللي ترجّعه

رجّع رقم الأمر، والتاريخ، والمخرجات بعد التنضيف، وسطر واحد لكل بند بيقول "matches the inventory" أو "differs: <what>". وبعدين مالك التوثيق بيحدّث [جرد سيرفر Contabo](contabo-server-inventory-ar.md)، ويغيّر وسم كل بند اتأكد منه إلى VERIFIED LIVE مع التاريخ ودور الأدمن.

## اللي القائمة دي مش بتعمله عن قصد

- مابتعملش Restart ولا Deploy ولا Prune ولا تجديد ولا Rotate ولا تعديل لأي حاجة. التغييرات مكانها [مسار الـ Deployment](deployment-pipeline-ar.md) و[دليل إعادة بناء السيرفر](server-rebuild-guide-ar.md) و[النسخ الاحتياطي والاستعادة والتعافي من الكوارث](backup-restore-disaster-recovery-ar.md).
- مابتقراش أسرار: قيم .env، وبيانات اعتماد Keycloak أو الـ Database، والمفاتيح الخاصة بـ TLS وSSH.
- مابتفحصش Tailscale؛ الـ tailnet بيتدار من الـ Admin Console بتاعه (NOT VERIFIED).

## القيود المعروفة وفجوات التوثيق

- هيكل مجلد شهادات Caddy (الأمر 10) جاي من اصطلاح التخزين بتاع Caddy، مش من الـ Repository ده؛ لو المسار مختلف، دوّر في `<mountpoint>` على ملفات بتنتهي بـ .crt بدله.
- الأمر 7 بيعتمد على أسماء حقول inspect بتاعة Docker. القيمة الفاضية في `health=` متوقعة لأي Container مفيهوش Healthcheck، مهما كان إصدار Docker، فاقرا كل حقل لوحده. بس لو حقل موجود عند كل الـ Containers (زي سياسة إعادة التشغيل) رجع فاضي ممكن يكون إصدار Docker مختلف عن 29.9.1 (VERIFIED LIVE)؛ ساعتها عدّل الحقل بدل ما تشيل الـ Format.
- الأمرين 5 و 7 مابيبيّنوش الـ Commit المصدر. الأمرين 17 و 18 بيضيفوا الإصدار المتظبّط ومراجع الـ Images الشغّالة، لكن الـ Commit المصدر مابيتثبتش إلا بعلامة revision على الـ Container الشغّال (البناء بيحطها، VERIFIED IN CODE؛ وجودها على الـ Containers الشغّالة NOT VERIFIED LIVE) وتشغيل الـ Workflow المسجّل دليل داعم على اللي اتنشر، مش إثبات للي شغّال دلوقتي، فمراجع الـ Images الشغّالة وعلامات الـ revision لسه محتاجة تحقق من أدمن؛ غير كده يفضل NOT VERIFIED.
- أسماء Compose Volumes بالـ Prefix بتاع المشروع هي السلوك المعتاد لـ Compose (DOCUMENTED ONLY)؛ الأمر 8 هو المرجع.
- وجود نسخة Backup برّه السيرفر مش مغطّى بأي أمر للقراءة فقط هنا؛ ده سؤال للمالك.
