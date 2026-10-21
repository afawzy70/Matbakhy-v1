# جرد سيرفر Contabo

> **Owner role:** Infrastructure and deployment
> **Status:** PARTIALLY IMPLEMENTED — الـ Host اللي تحت شغّال واتلاحظ بأوامر للقراءة فقط بمستخدم عادي من غير صلاحيات إدارية؛ وأي حاجة محتاجة root أو الـ Docker socket تبقى NOT VERIFIED
> **Language / اللغة:** [English](contabo-server-inventory.md) · العربية

الصفحة دي بتسجّل اللي معروف عن السيرفر الوحيد اللي بيشغّل بيئة الـ Preview بتاعة Matbakhy. وبتفصل بين اللي اتلاحظ على الجهاز نفسه واللي بيقوله الـ Repository. ومفيهاش أي أسرار أبدًا.

## مفتاح الأدلة

| الوسم | المعنى |
|---|---|
| VERIFIED LIVE | اتلاحظ يوم 2026-10-21 على السيرفر نفسه بواسطة المستخدم aiagent اللي من غير صلاحيات إدارية: أوامر للقراءة فقط، من غير sudo، ومن غير Docker socket، ومن غير ملفات محمية. |
| VERIFIED IN CODE | اتقرا في الـ Repository ده (الملف مذكور في الصف). بيوصف السلوك المقصود، مش بالضرورة اللي شغّال فعلًا. |
| DOCUMENTED ONLY | مستند بيقول كده؛ ومفيش حاجة بتأكده. |
| OUTDATED / INCORRECT | مكتوب للـ Host المقترح على Hetzner أو اتلغى بحاجة أحدث، وبيتعارض مع دليل أحدث. |
| NOT VERIFIED | ماتلاحظش ومش موجود في الـ Repository. الأمر اللي الأدمن هيشغّله عشان يتحقق منه موجود في [التحقق بأوامر للقراءة فقط بواسطة الأدمن](admin-readonly-verification-ar.md). |

الـ Preview بيئة بتشتغل ببيانات تجريبية غير حقيقية (Synthetic Data)، مش Production ومش بيئة إصدار ([البنية التحتية والـ Deployment](../03-architecture/infrastructure-and-deployment-ar.md)).

## 1. الـ Host

| البند | القيمة | الدليل |
|---|---|---|
| المزوّد | Contabo VPS (الـ Host الشغّال؛ الخطة الأقدم كانت اسمها Hetzner، شوف [القسم 9](#9-نص-hetzner-التاريخي-مقابل-الـ-host-الحالي)) | VERIFIED LIVE (اسم الـ Workflow "Deploy Matbakhy preview to Contabo" هو VERIFIED IN CODE في `.github/workflows/deploy-preview.yml`) |
| نظام التشغيل | Ubuntu 24.04.5 LTS (Noble) | VERIFIED LIVE |
| الـ Kernel | 6.8.0-147-generic | VERIFIED LIVE |
| الـ CPU | 8 vCPU | VERIFIED LIVE |
| الذاكرة | الإجمالي 23 GiB؛ والمستخدم منها حوالي 3.4 GiB وقت الملاحظة | VERIFIED LIVE |
| القرص | /dev/sda1، حجمه 290 GB، المستخدم منه 18 GB (6%) | VERIFIED LIVE |
| المنطقة الزمنية | Europe/Berlin (CEST) هي المنطقة الزمنية للـ Host. المنطقة الزمنية اللي بيستخدمها كل Scheduler وكل Logger مش مثبتة: `deploy.sh` بيكتب أوقات سجل الإصدارات (Release Log) بتوقيت UTC (`deploy/web-preview/deploy.sh`, function record_release)، فماتفترضش توقيت برلين في مدخلات الـ Cron ولا في Logs التطبيق ولا الـ Containers | VERIFIED LIVE (المنطقة الزمنية للـ Host بس)؛ VERIFIED IN CODE (أوقات الـ Release Log بتوقيت UTC)؛ اصطلاحات الـ Cron والـ Logs NOT VERIFIED |
| Docker Engine | 29.9.1 | VERIFIED LIVE (`docker --version`) |
| Docker Compose plugin | v5.6.0 | VERIFIED LIVE (`docker compose version`) |
| الحزم المثبّتة اللي اتشافت | docker-ce, docker-ce-cli, containerd.io, docker-ce-rootless-extras, ufw, unattended-upgrades | VERIFIED LIVE |
| مش مثبّتين | fail2ban وCaddy على الـ Host. Caddy شغّال جوه الـ Container بتاع الـ Proxy | VERIFIED LIVE لحزم الـ Host؛ VERIFIED IN CODE للـ Container (`deploy/web-preview/compose.yaml`, service proxy) |
| الخدمات النشطة اللي اتشافت | ssh, cron, docker, containerd, tailscaled, unattended-upgrades, systemd-resolved, systemd-networkd, systemd-timesyncd, rsyslog | VERIFIED LIVE |
| دومين الـ Preview المنشور | 167-86-90-174.sslip.io، وعنوان IPv4 العام 167.86.90.174 (معلن بالفعل في الـ Repository) | VERIFIED LIVE لتحليل الـ DNS؛ شوف [الـ DNS والـ HTTPS](dns-https-sslip-io-ar.md) |

ملف الـ Override بتاع حجم 4 GB واسمه `compose.4gb.yaml` وأرقام "2 vCPU and 4 GB" في المستندات الأقدم بتوصف خطة Hetzner، ومش بتوصف الـ Host ده (OUTDATED / INCORRECT بالنسبة لحجم الـ Host). أما أنهي Override للحجم بيختاره ملف `.env` الشغّال (`COMPOSE_OVERRIDE`) فده NOT VERIFIED.

## 2. الحسابات والمجموعات

الحسابات اللي الـ uid بتاعها 1000 أو أكتر هي بس المذكورة. البيانات جاية من /etc/passwd وid وgetent.

| الحساب | uid وgid | الـ Home | المجموعات المتحقق منها | الدليل |
|---|---|---|---|---|
| ubuntu | 1000, 1000 | /home/ubuntu (750 ubuntu:ubuntu), bash | ubuntu, adm, cdrom, sudo, dip, lxd | VERIFIED LIVE |
| devops | 1001, 1001 | /home/devops (750 devops:devops), bash | devops, sudo, users, docker | VERIFIED LIVE |
| aiagent | 1002, 1002 | غير مسجّل، bash | aiagent, users | VERIFIED LIVE |

عضويات المجموعات المهمة:

| المجموعة | الأعضاء (المتحقق منهم) | ليه مهمة |
|---|---|---|
| sudo (gid 27) | ubuntu, devops | العضوية فيها بتدّي صلاحية sudo عادةً، لكن الصلاحيات الفعلية NOT VERIFIED: ملفات سياسة sudo مش مقروءة للـ aiagent، فالقواعد بالظبط (محتاج كلمة سر ولا لأ، وحدود الأوامر) مجهولة. |
| docker (gid 988) | devops | العضوية فيها بتساوي root على الـ Host. devops بس هو العضو. |
| users (gid 100) | devops, aiagent | مفيش ملف في الـ Repository بيدّي المجموعة دي معنى. |

قواعد لازم تفضل صحيحة:

- aiagent مش في مجموعة sudo ولا docker، ومينفعش ياخد صلاحيات sudo أو Docker أبدًا. ومش قادر يقرا /var/run/docker.sock، وده سبب إن حالة الـ Containers NOT VERIFIED هنا.
- ماحصلش أي تغيير في الصلاحيات أو عضويات المجموعات أو الحسابات عشان نكتب الصفحة دي.

### المسؤوليات

المسؤولية التشغيلية لكل حساب مش بتتحدّد من اسم الحساب. ماتستنتجش دور الحساب من اسمه.

| الحساب | المسؤولية | الدليل الموجود |
|---|---|---|
| ubuntu | مش متحدّدة من اسم الحساب | عضو في sudo وadm وcdrom وdip وlxd (VERIFIED LIVE). مفيش حاجة بتبيّن الحساب ده بيتستخدم في إيه. |
| devops | مش متحدّدة من اسم الحساب | عضو في sudo وdocker. بيملك /srv/matbakhy-staging وملف .env بتاعه ومجلدات النسخ الاحتياطي و/srv/deployments (VERIFIED LIVE). ده دليل غير مباشر بس: مستخدم SSH بتاع الـ Deployment محفوظ كـ Secret في الـ Repository على GitHub (شوف تحت). |
| aiagent | مش متحدّدة من اسم الحساب | بيملك /srv/ai-supervisor و/srv/ai-workspaces، اللي فيهم أدوات الذكاء الاصطناعي ومش جزء من تشغيل Matbakhy (VERIFIED LIVE). ملوش sudo ومفيش وصول لـ Docker. |

مستخدم Linux اللي الـ Workflow بتاع الـ Deployment بيتصل بيه هو الـ Secret `STAGING_SSH_USER` في الـ Repository (VERIFIED IN CODE: `.github/workflows/deploy-preview.yml`, step "Transfer versioned deployment files"). قيمته NOT VERIFIED. ملكية devops لـ /srv/matbakhy-staging بتتماشى مع إن devops هو المستخدم ده، لكنها مش بتثبت كده. إزاي تتأكد: أدمن يربط تسجيلات دخول SSH المقبولة (الأمر 13 في [صفحة الأدمن](admin-readonly-verification-ar.md)) بوقت تشغيل Workflow معروف، ويبلّغ عن أي لبس فاضل، لأن سجلات الدخول المقبولة لوحدها مابتبيّنش أنهي جلسة كانت بتاعة GitHub Actions. أما مراجعة أسماء الـ Secrets الموجودة في إعدادات الـ Repository فبتبيّن بس إن `STAGING_SSH_USER` متظبّط؛ الاسم مابيكشفش اسم المستخدم، والقيم مينفعش تتقرا.

## 3. سياسة SSH

الأجزاء المقروءة من /etc/ssh/sshd_config و/etc/ssh/sshd_config.d هي اللي aiagent قدر يقراها.

| الإعداد | القيمة المقروءة | الدليل والتحفظ |
|---|---|---|
| PasswordAuthentication | no | VERIFIED LIVE (نص الملف) |
| KbdInteractiveAuthentication | no | VERIFIED LIVE (نص الملف) |
| PubkeyAuthentication | yes | VERIFIED LIVE (نص الملف) |
| UsePAM | yes | VERIFIED LIVE (نص الملف) |
| X11Forwarding | yes | VERIFIED LIVE (نص الملف). مش مطلوب لـ Matbakhy؛ ومراجعة تقوية الأمان (Hardening) ممكن تقفله. |
| PermitRootLogin | النص المقروء فيه القيمتين (no و yes) | القيمة الفعّالة منهم NOT VERIFIED. كلام دليل إعادة البناء إن أول قيمة بتتقرا هي اللي بتتطبّق DOCUMENTED ONLY. الملف /etc/ssh/sshd_config.d/50-cloud-init.conf مش مقروء للـ aiagent وممكن يكون بيظبطها تاني. لازم أدمن يأكّدها بـ `sshd -T` بصلاحية root (الأمر 1 في صفحة الأدمن). |

الحاجات دي مش مقروءة للـ aiagent وبالتالي NOT VERIFIED: 50-cloud-init.conf، وكل ملفات authorized_keys، والمفاتيح الخاصة بالـ Host، و/etc/sudoers.d. عدد المفاتيح المصرّح بيها لكل حساب، وأنهي مفتاح بتاع أنهي شخص أو بتاع GitHub Actions، مجهول، ومصدر المفاتيح الفعلي (AuthorizedKeysFile أو AuthorizedKeysCommand) كمان مجهول.

الـ Workflow بتاع الـ Deployment بيثبّت مفتاح الـ Host بتاع السيرفر من الـ Secret `STAGING_SSH_HOST_PUBKEY` وبيتصل بـ `StrictHostKeyChecking=yes` (VERIFIED IN CODE: `.github/workflows/deploy-preview.yml`, step "Configure verified SSH host").

## 4. المجلدات

القائمة اتعملت بـ `ls -ld` بواسطة aiagent. عمود "الغرض" جاي من الكود أو المستندات وعليه وسم الدليل؛ وهو مش دليل على طريقة استخدام الملفات الشغّالة فعلًا.

| المسار | المالك والـ Mode (VERIFIED LIVE) | الغرض ودليله |
|---|---|---|
| /srv | root:root 755 | المجلد الأب. |
| /srv/matbakhy-staging | devops:devops 755؛ اتعدّل في Oct 18 و Oct 20 | وجهة الـ Deployment المستمر: الـ Workflow بيفك فيه deploy وinfra وscripts وملف Fixture واحد خاص بقبول المالك (Owner Acceptance) (VERIFIED IN CODE: `.github/workflows/deploy-preview.yml`, step "Transfer versioned deployment files"). وفيه نسخة شبه الـ Repository (.github وapps وbackend وcontracts وdeploy وdocs وinfra وscripts وغيرهم)، VERIFIED LIVE. |
| /srv/matbakhy-staging/deploy/web-preview | devops:devops 755 | مجلد التشغيل لكل أوامر `deploy.sh` (VERIFIED IN CODE: الملف `deploy/web-preview/deploy.sh` بيعمل `cd` لمجلده هو). فيه compose.yaml وcompose.4gb.yaml وCaddyfile وdeploy.sh وadmin-console.sh وsmoke.sh وملفات SQL بتاعة الـ Seed والمجلدات identity وobservability وpostgres وstorage وe2e-fixtures (VERIFIED LIVE). |
| /srv/matbakhy-staging/deploy/web-preview/.env | devops:devops 600 | ملف الإعدادات الخاص بالسيرفر بس وفيه كل الأسرار. محتواه ماتقراش ومينفعش يتنسخ أبدًا (VERIFIED LIVE للوجود والمالك والـ Mode). الأمر `deploy.sh init-secrets` هو الطريقة المدعومة لإنشائه (VERIFIED IN CODE: `deploy/web-preview/deploy.sh`)؛ أما إزاي الملف ده بالذات اتعمل فده NOT VERIFIED. |
| /srv/matbakhy-staging/generated | المالك devops، الـ Mode 700 (drwx------)؛ المجموعة غير مسجّلة | غالبًا فيه إعدادات الـ Realm والـ Storage بعد الـ Render، لأن `deploy.sh render` بيعمل GENERATED_DIR بـ Mode 700 وجواه identity وstorage وflags (VERIFIED IN CODE: `deploy/web-preview/deploy.sh`, function render). إن ملف .env الشغّال بيظبط GENERATED_DIR على المسار ده، ده NOT VERIFIED. المحتوى ماتقراش. |
| /srv/matbakhy-backups | devops:devops 750؛ 28 عنصر، ومش ممكن aiagent يعرض محتواها | قيمة BACKUP_DIR الافتراضية: Dumps الـ Database وأرشيفات تخزين الصور وسجل الإصدارات (Release Log) والـ Lock المشترك (VERIFIED IN CODE: `deploy/web-preview/compose.yaml`, `deploy/web-preview/deploy.sh`). إن BACKUP_DIR الشغّال هو المسار ده، وإيه هي الـ 28 عنصر دول، ده NOT VERIFIED. وcompose.yaml كمان بيعمل Bind Mount لـ `BACKUP_DIR/metrics` للقراءة بس جوه node-exporter (VERIFIED IN CODE: `deploy/web-preview/compose.yaml`)، فالمجلد الفرعي ده لازم يكون موجود بملكية مناسبة قبل أول تشغيل؛ وجوده هنا NOT VERIFIED. |
| /srv/backups | devops 750 (المجموعة غير مسجّلة) | الغرض مش متحدّد. مفيش ملف في الـ Repository بيشير للمسار ده. |
| /srv/deployments | devops 750 (المجموعة غير مسجّلة)؛ ومش ممكن عرض محتواه | الغرض مش متحدّد. مفيش ملف في الـ Repository بيشير للمسار ده. |
| /srv/matbakhy-preview | devops:devops 775 | نسخة قديمة من ملفات الـ Deploy من Oct 17 إلى 18؛ شوف [القسم 7](#7-نسخة-srvmatbakhy-preview-القديمة). |
| /srv/ai-supervisor | aiagent، 750 (المجموعة غير مسجّلة) | أدوات الذكاء الاصطناعي، مش تشغيل Matbakhy (VERIFIED LIVE). |
| /srv/ai-workspaces | aiagent، 750 (المجموعة غير مسجّلة) | أدوات الذكاء الاصطناعي، مش تشغيل Matbakhy (VERIFIED LIVE). |
| /srv/flags | مش موجود على نظام ملفات الـ Host | الاسم ظاهر جوه الـ Container بتاع الـ Proxy بس: الـ Caddyfile بيقرا /srv/flags/admin-console-public وcompose.yaml بيعمل Mount لـ GENERATED_DIR/flags هناك للقراءة بس (VERIFIED IN CODE: `deploy/web-preview/Caddyfile`, `deploy/web-preview/compose.yaml`). مكانه على الـ Host تحت مجلد generated وهو NOT VERIFIED. |
| /srv/matbakhy-qa-artifacts | مش في القائمة اللي اتلاحظت | قيمة QA_ARTIFACT_DIR الافتراضية (VERIFIED IN CODE: `deploy/web-preview/.env.example`). إن المجلد موجود على الـ Host ده، ده NOT VERIFIED. |

مواقع الـ Docker Volumes على الـ Host NOT VERIFIED LIVE؛ اتأكد منها بفحص الـ Volumes (الأمرين 8 و 9 في صفحة الأدمن). أسماؤها وأحجامها كمان NOT VERIFIED LIVE. الـ Volumes اللي ملف الـ Compose بيعلنها مذكورة في القسم الجاي.

### الـ Volumes اللي ملف الـ Compose بيعلنها

VERIFIED IN CODE (`deploy/web-preview/compose.yaml`, project name matbakhy-preview): postgres-data, storage-data, caddy-data, caddy-config, loki-data, alloy-data, prometheus-data, alertmanager-data, alert-sink-data, grafana-data. إن كل واحد منهم موجود، وبأنهي اسم بالـ Prefix، ده NOT VERIFIED LIVE (الأوامر 8 و 9 في صفحة الأدمن).

## 5. واجهات الشبكة والمنافذ المفتوحة

| البند | الملاحظة | الدليل |
|---|---|---|
| eth0 | IPv4 عام 167.86.90.174/24 وIPv6 عام 2a02:c207:2367:3442::1/64 | VERIFIED LIVE |
| tailscale0 | موجودة؛ والـ Tailscale daemon نشط؛ وعنوان tailnet خاص. مين الناس أو الأجهزة على الـ tailnet واستخدامها في إيه، ده NOT VERIFIED | VERIFIED LIVE (الواجهة والـ Daemon) |
| docker0 | 172.17.0.1/16، الحالة DOWN | VERIFIED LIVE |
| Docker bridges | تلات شبكات br-* على 172.18.0.1/16 و 172.19.0.1/16 و 172.20.0.1/16، الحالة UP، ومعاها تلات واجهات veth. ملف الـ Compose بيعلن تلات شبكات (edge وops وinternal)، وده متسق لكن مش مثبت إنها نفس الشبكات | VERIFIED LIVE (الواجهات)؛ VERIFIED IN CODE (الشبكات المعلنة) |

الـ Sockets اللي بتسمع (Listening) والمتشافة بـ `ss -tlnp` بواسطة aiagent:

| الـ Listener | الملاحظة | القراءة |
|---|---|---|
| 0.0.0.0 و[::] المنفذ 22 | عام | SSH. VERIFIED LIVE. |
| 0.0.0.0 و[::] المنفذين 80 و 443 | عام | الـ Container بتاع الـ Proxy بينشر "80:80" و "443:443" (VERIFIED IN CODE: `deploy/web-preview/compose.yaml`). العملية بتاعة الـ Listener مش ظاهرة للـ aiagent، فالربط بالـ Container مستنتج مش مثبت. |
| 127.0.0.1 المنفذ 3000 | Loopback بس | بيطابق ربط Grafana "127.0.0.1:3000:3000" في ملف الـ Compose؛ والعملية المالكة NOT VERIFIED. |
| 127.0.0.1 المنفذ 3080 | Loopback بس | عملية node تابعة لأدوات الـ AI Supervisor، مش Matbakhy (VERIFIED LIVE). |
| 127.0.0.53 و 127.0.0.54 المنفذ 53 | Loopback بس | الـ Resolver المحلي (systemd-resolved). |
| منافذ عالية على tailscale0 | الـ tailnet بس | Tailscale. |
| 127.0.0.1 المنفذين 18080 و 38189 | Loopback بس | عملية Java مالكها غير معروف. ماتفترضش إنها Matbakhy: ملف الـ Compose مش بينشر منافذ زي دي. |

مفيش Database ولا Keycloak ولا Storage ولا API ولا منفذ إدارة مفتوح للعامة. ملف الـ Compose بينشر منافذ الـ Proxy ومنفذ Grafana على الـ Loopback بس (VERIFIED IN CODE)، وده متطابق مع القائمة. الـ Firewall اللي هيفرض ده NOT VERIFIED (ufw مثبّت؛ وحالته محتاجة root).

## 6. الشغل المجدول

| البند | الملاحظة | الدليل |
|---|---|---|
| matbakhy-claude-recover.timer | systemd timer لمهمة استرجاع (Recovery) بتاعة الـ AI Supervisor. مش جزء من تطبيق Matbakhy | VERIFIED LIVE (الوجود)؛ الغرض من ملاحظات الفحص المباشر |
| /etc/cron.d | e2scrub_all, staticroute, sysstat | VERIFIED LIVE |
| جدول النسخ الاحتياطي بتاع Matbakhy | مش ظاهر. الـ Crontabs بتاعة المستخدمين التانيين مش مقروءة للـ aiagent. المستندات الأقدم بتقول للمشغّل يضيف مدخل Cron يومي لـ `deploy.sh backup` للمستخدم اللي بيشغّل السيرفر | NOT VERIFIED على الـ Host ده؛ DOCUMENTED ONLY في `docs/operations/12-owner-server-setup.md`, section 9 |

الـ Repository بياخد نسخة احتياطية قبل كل Deploy في `deploy.sh up` (VERIFIED IN CODE: `deploy/web-preview/deploy.sh`, function up). هل فيه نسخ احتياطي تاني متجدول أو بيتعمل يدوي؟ ده NOT VERIFIED: الـ Crontabs بتاعة المستخدمين التانيين وقائمة الـ systemd timers الكاملة وأي ممارسة يدوية ماتفحصتش (الأمرين 12 و 15 في صفحة الأدمن). غياب مدخل Cron ظاهر مش معناه إن مفيش نسخ احتياطي متجدول أو يدوي. شوف [النسخ الاحتياطي والاستعادة والتعافي من الكوارث](backup-restore-disaster-recovery-ar.md).

## 7. نسخة /srv/matbakhy-preview القديمة

- VERIFIED LIVE: المجلد /srv/matbakhy-preview (devops:devops, 775) فيه نسخة أقدم من ملفات الـ Deploy بتاريخ Oct 17 إلى 18؛ وملف deploy.sh بتاعها أصغر من اللي في /srv/matbakhy-staging.
- VERIFIED IN CODE: القيمة الافتراضية لـ GENERATED_DIR في الاتنين `deploy/web-preview/compose.yaml` و`deploy/web-preview/.env.example` لسه /srv/matbakhy-preview/generated. لكن الـ Workflow بتاع الـ Deployment موجّه لـ /srv/matbakhy-staging.
- النتيجة: القيمة الافتراضية في الـ Repository ومجلد الـ Deployment الشغّال مختلفين. لو ملف .env الشغّال مش بيعمل Override لـ GENERATED_DIR، الإعدادات المتولّدة بالـ Render (بما فيها الأسرار) ومجلد الـ Flags هيبقوا تحت المسار القديم (BACKUP_DIR إعداد مستقل ومابيحدّدش مكان الإعدادات المتولّدة). القيم اللي بيظبطها .env الشغّال NOT VERIFIED؛ والأمر 2 في صفحة الأدمن بيقرا أسماء المفاتيح دي بس.
- ماتمسحش المجلد القديم ولا تعيد استخدامه قبل ما ده يتأكد. اعتبره نسخة قديمة (Legacy) وماتشغّلش `deploy.sh` منه.

## 8. NOT VERIFIED LIVE (ملخص)

كل اللي تحت محتاج أدمن بنية تحتية مصرّح له. أرقام الأوامر بتشير لـ [التحقق بأوامر للقراءة فقط بواسطة الأدمن](admin-readonly-verification-ar.md).

| الموضوع | ليه مجهول | الأمر |
|---|---|---|
| إعدادات SSH الفعلية، منها PermitRootLogin | sshd -T و 50-cloud-init.conf محتاجين root | 1 |
| حالة وقواعد الـ Firewall (ufw) | محتاج root | 3 |
| ملفات وقواعد سياسة sudo | /etc/sudoers.d مش مقروء | 4 |
| الـ Containers الشغّالة والـ Images وحالة الصحة وعدد مرات إعادة التشغيل | الـ Docker socket مش متاح للـ aiagent | 5, 7 |
| حدود الـ Container (الذاكرة، الـ CPU) المطبّقة فعلًا | نفس السبب | 7 |
| الـ Docker volumes وأسماؤها وأحجامها | نفس السبب | 8, 9, 14 |
| شبكات Docker | نفس السبب | 6 |
| الـ systemd timers (القائمة الكاملة، منها أي Timer بتاع Matbakhy) | اتشاف جزء منها بس | 15 |
| ملفات الشهادات وحالة التجديد في الـ Volume بتاع Caddy | مسار الـ Volume مش مقروء | 10 |
| محتوى .env (القيم) | سر؛ وأسماء مفاتيح قليلة غير سرية بس هي اللي ممكن تتشيّك | 2 |
| محتوى /srv/matbakhy-backups و/srv/backups و/srv/deployments | مش ممكن عرضها | 11 |
| مدخلات Cron بتاعة devops والمستخدمين التانيين، وجداول النظام | مش مقروءة | 12 |
| الـ Commit والـ Image Digests وعلامات الـ Revision المنشورة فعلًا | الـ Docker socket وملف .env مش متاحين للـ aiagent | 17, 18 |
| أنهي مستخدم بيدخل بيه مفتاح SSH بتاع الـ Deployment | الـ Secret اسمه STAGING_SSH_USER؛ ودليل الـ Log محتاج root | 13 |
| عدد مفاتيح SSH المصرّح بيها لكل حساب (الأمر 13 بيدّي عدد سطور بس) ومصدر المفاتيح الفعلي | مش مقروء | 1 و 13 |
| مالك العملية على منافذ الـ Loopback رقم 3000 و 18080 و 38189 | مش ظاهر للـ aiagent | 16 |
| عضوية الـ tailnet والغرض منها | مش ظاهرة | مش مغطّى (لوحة أدمن الـ tailnet) |

## 9. نص Hetzner التاريخي مقابل الـ Host الحالي

أول مستندات البنية التحتية اتكتبت لـ Host مقترح على Hetzner Cloud. فاضلة محفوظة كتاريخ بس. ماتمشيش عليها مع سيرفر Contabo الشغّال.

| المصدر | الكلام اللي كان مكتوب في فترة Hetzner | الحالة بالنسبة للـ Host الشغّال |
|---|---|---|
| `docs/operations/09-staging-hetzner-preparation.md` | مشروع Hetzner، Cloud Firewall، خطة 2 vCPU و 4 GB | OUTDATED / INCORRECT بالنسبة للـ Host (Contabo، 8 vCPU، 23 GiB). لسه مفيدة كتبرير للتصميم. |
| `docs/operations/12-owner-server-setup.md` | اسم المستخدم المشغّل matbakhy، والحزمة تحت /srv/matbakhy-preview/src، Firewall من كونسول Hetzner، `sudo ufw status` "not used" | OUTDATED / INCORRECT: مفيش حساب matbakhy ظهر في الجرد المسجَّل للحسابات اللي الـ uid بتاعها 1000 أو أكتر (الحسابات فوق)؛ إن مفيش حساب كده خالص (مثلًا بـ uid أقل) NOT VERIFIED، ومجلد الـ Deployment هو /srv/matbakhy-staging، وترتيب الـ Firewall NOT VERIFIED. |
| `deploy/web-preview/compose.4gb.yaml` وملاحظات الـ 4 GB في `deploy/web-preview/README.md` | حدود ذاكرة لجهاز 4 GB | OUTDATED / INCORRECT بالنسبة للحجم؛ إن الـ Override لسه متختار، ده NOT VERIFIED. |
| `deploy/web-preview/.env.example` | GENERATED_DIR تحت /srv/matbakhy-preview | مختلف عن المجلد الشغّال؛ شوف [القسم 7](#7-نسخة-srvmatbakhy-preview-القديمة). |

التعليمات الحالية هي الصفحة دي، و[التحقق بأوامر للقراءة فقط بواسطة الأدمن](admin-readonly-verification-ar.md)، و[مسار الـ Deployment](deployment-pipeline-ar.md)، و[دليل إعادة بناء السيرفر](server-rebuild-guide-ar.md).

## القيود المعروفة وفجوات التوثيق

- معلومات الـ Containers والـ Volumes والـ Firewall وsudo والـ Cron مقدرناش نقراها من غير صلاحيات؛ والجدول في القسم 8 هو القائمة الكاملة للي لسه مفتوح.
- مجموعة (Group) كذا مجلد تحت /srv ومجلد الـ Home بتاع aiagent ماتسجّلوش.
- المستند `docs/operations/12-owner-server-setup.md` لسه بيوصف حساب matbakhy و/srv/matbakhy-preview كأنهم الترتيب الشغّال؛ وده بيتعارض مع الـ Host اللي اتلاحظ. التصحيح مسؤولية مالك التوثيق، ومتتبَّع في صفحة التغطية ([تغطية توثيق البنية التحتية](../07-reference/infrastructure-documentation-coverage-ar.md)).
- الغرض من /srv/backups و/srv/deployments مش موثّق في أي مكان في الـ Repository.
