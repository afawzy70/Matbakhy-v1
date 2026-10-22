# Egyptian Arabic style guide for technical documentation

Use this guide **while translating and while reviewing**. It sets the language standard for every Egyptian Arabic page the skill generates or updates. It contains only reusable engineering vocabulary; keep a project's own business terms in that project's glossary page, not here.

## 1. Target register
Professional conversational Egyptian Arabic, as an experienced Egyptian engineer writes a runbook for a colleague: natural, clear, correct, consistent, faithful and complete. Not Modern Standard Arabic textbook prose, not street slang, no jokes. Address the reader as "انت" (imperative: "شغّل"، "اتأكد"، "راجع"). Prefer short sentences. Keep every detail of the English source; do not summarize, soften, or add.

Check each paragraph by reading it aloud: if an Egyptian engineer would not say it, rewrite it by meaning, not word by word.

## 2. English or Arabic?
Keep **established engineering terms in English** (Latin script) with the Arabic article attached: الـ Backend، الـ Frontend، الـ API، الـ Endpoint، الـ Database، الـ Container، Docker، الـ CI/CD، الـ Deployment، الـ Rollback، GitHub Actions، الـ Pull Request، الـ Branch، الـ Authentication، الـ Authorization، الـ Token، الـ Session، الـ Migration، الـ Reverse Proxy، الـ Health Check، Keycloak and other product names.
Use **Arabic for ordinary language**: explanations, reasons, warnings, conditions, instructions. English is for technical precision, never decoration: do not write "الـ Step الأول" when "الخطوة الأولى" is natural.
When a term could mislead if only transliterated, give the Arabic explanation once and the English term in parentheses: "صور الـ Container (Docker Images)"، not just "صور".
Never translate: commands, paths, env vars, endpoints, class/method/package names, DB identifiers, config keys, versions, branch/workflow names, ids, URLs, evidence/status labels (for example VERIFIED LIVE، NOT VERIFIED، IMPLEMENTED، PLANNED، PASS، FAIL). Keep numbers in Western digits.

## 3. Preferred wording (generic engineering vocabulary)
| English | Preferred Egyptian wording | Notes |
|---|---|---|
| machine-readable files | ملفات قابلة للقراءة آليًا | not "مقروء بالآلة" |
| living document / continuously maintained | بيتحدّث باستمرار مع تطور المشروع | not "مخرَج حيّ" |
| live money is gated / not enabled | التعاملات المالية الحقيقية مش مفعّلة حاليًا | say what is *enabled*, not "متقفّلة" |
| image-only rollback | الرجوع لإصدار Docker Images سابق من غير استرجاع قاعدة البيانات | the contrast with database restore is part of the meaning |
| the schema did not move | مفيش تغيير في بنية أو إصدار الـ Database Schema | |
| CI is green | كل فحوصات الـ CI نجحت | |
| the pipeline is red / failed | واحد أو أكتر من فحوصات الـ CI فشل | |
| deploy to / deployed commit | نشر على بيئة / رقم الـ Commit المنشور | keep Deployment/Deploy when it is the technical name |
| health check | فحص الصحة (Health Check) | |
| read-only | للقراءة فقط (مابيغيّرش حاجة) | |
| state-changing | بيغيّر حالة السيستم | |
| fails closed | بيرفض في حالة الشك (Fail Closed) | explain once |
| single point of failure | نقطة فشل واحدة (Single Point of Failure) | |
| out of the box / by default | افتراضيًا | |
| prerequisite | متطلب مسبق | |
| acceptance criteria | معايير القبول | |
| known limitation / gap | قيد معروف / فجوة معروفة | |
| workaround | حل بديل مؤقت | |
| troubleshooting | حل المشاكل (Troubleshooting) | |
| synthetic data | بيانات تجريبية غير حقيقية (Synthetic Data) | see section 5 |
| real customer data | بيانات العملاء الحقيقية | never mix with synthetic data |

Translate the **meaning in context**. These rows are examples, not search-and-replace rules: check the sentence, the surrounding table and the technical distinction before using one.

## 4. Concepts that must never be blurred
Preview vs Staging vs Production (three different environments; use the project's own definition and never swap them) · Backup vs Restore (creating a copy vs recovering from it) · Rollback vs Database Restore (returning to an earlier release vs recovering data; image rollback does not undo data changes) · Synthetic Data vs actual customer data · Authentication (who you are) vs Authorization (what you may do) · Required vs Optional · Implemented vs Planned · Verified vs Not Verified · Read-only vs state-changing · Revision (a stored version) vs Review (an assessment) vs Audit (a recorded trail) · Stale (outdated against current rules) vs Expired (past its time limit) · Dismiss (withdraw) vs Reject · Commit (a version-control snapshot or a DB transaction commit) vs a commitment · Seeded (loaded from seed data) vs planted.

## 5. Synthetic data wording
Use "بيانات تجريبية غير حقيقية (Synthetic Data)" (and "حسابات/مستخدمين تجريبيين غير حقيقيين") when the point is that the data is generated for testing and is not real. Do not write "تجريبية" alone where the reader could think it means "experimental" or might hold real data; the phrase "غير حقيقية" and the English term carry the meaning. Never use a wording that suggests the data could be customer data.

## 6. Grammar, punctuation and typography
- Egyptian negation: "ماتعملش"، "مابيشتغلش"، "مفيش"; do not drop the final "ش" of the negation ("مابيناديش" not "مابينادي").
- Keep verb and prefix intact: "اترفض" (not "اتر فض"), "ومن غير" (not "وومن غير"), "والـ API". A digit or Latin token directly after an Arabic prefix letter (e.g. "و3") may be tokenized wrongly by tools: separate with a space or reword ("من 1 إلى 3").
- Arabic comma "،" and semicolon "؛" in prose; keep Latin punctuation inside code, commands and identifiers. Do not use diacritics in headings.
- Arrows and symbols keep their direction (a transition "A → B" stays "A → B" inside right-to-left text; never swap to "←").
- Bold and italic markers must wrap whole words; do not put them inside code spans.
- Mixed text: put long Latin identifiers in code spans so right-to-left reordering cannot scramble them.

## 7. Consistency
Build one glossary page per project (Arabic term next to the English term, the definition, and a link to the code or document it refers to). Reuse the same Arabic wording for a concept everywhere; a second wording needs a reason. Before delivery, search the Arabic set for each glossary term's competing renderings.

## 8. Examples of professional Egyptian Arabic
- "قبل ما تشغّل التطبيق، اتأكد إن Docker متسطّب وشغّال عندك."
- "لو الـ Service مشتغلش، راجع الـ Application Logs عشان تعرف سبب المشكلة."
- "الأمر ده للقراءة فقط ومابيغيّرش حاجة على السيرفر؛ شغّله بحساب له صلاحية مناسبة."
- "الرجوع لإصدار Docker Images سابق مابيرجّعش البيانات؛ لو التغيير مس قاعدة البيانات لازم تستعيد من نسخة احتياطية."
- "البيانات هنا بيانات تجريبية غير حقيقية (Synthetic Data) ومفيهاش أي بيانات عملاء."

## 9. Review checklist (one row per document in the language-quality report)
Naturalness · Grammar · Clarity · Technical accuracy · Terminology consistency · Completeness · Readability · Markdown and right-to-left presentation. Record PASS, FAIL or NOT VERIFIED for each; a material mistranslation, a misleading technical instruction, an omitted detail or unresolved unnatural wording prevents an unconditional PASS. Anti-pattern catalogue: [egyptian-arabic-anti-patterns.md](egyptian-arabic-anti-patterns.md).
