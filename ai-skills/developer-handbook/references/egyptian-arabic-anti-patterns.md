# Egyptian Arabic anti-patterns (sanitized regression examples)

Recurring mistakes found while mirroring a real engineering handbook into Egyptian Arabic, with project facts removed. Use them as a review checklist and as regression cases. Every category gives a **Bad** (negative) and a **Good** (positive) example. They are *examples*, not search-and-replace rules: always judge the sentence in context and keep the technical distinction. `./scripts/check_arabic_style.py` flags a few of the phrases as *warnings only*; it never decides quality.

## A1 Literal translation of an English expression
- Bad: "الدليل مخرَج حيّ" · "الفلوس الحقيقية متقفّلة" · "الـ CI أخضر" · "ملفات مقروء بالآلة" (word for word from "living deliverable", "live money is gated", "CI is green", "machine-readable").
- Good: "الدليل بيتحدّث باستمرار مع تطور المشروع" · "التعاملات المالية الحقيقية مش مفعّلة حاليًا" · "كل فحوصات الـ CI نجحت" · "ملفات قابلة للقراءة آليًا".
- Why: the Arabic words exist but the sentence means something else or sounds foreign. Translate the idea.

## A2 Compressed technical phrase that loses its contrast
- Bad: "الـ Rollback للصور بس" · "الـ Schema ماتحركتش".
- Good: "الرجوع لإصدار Docker Images سابق من غير استرجاع قاعدة البيانات" · "مفيش تغيير في بنية أو إصدار الـ Database Schema".
- Why: the English phrase implies a contrast (images but not data) or a precise subject (structure and version). Say it fully.

## A3 Polysemous English word rendered with the wrong sense
- Bad: revision → "مراجعة" (a stored version); audit → "مراجعة" (a recorded trail); dismissal → "رفض" (withdrawing an approval); stale → "منتهي" (outdated, not timed out); typed → "متكتّب"; commit → "التزام"; seeded → "مزروعة"; candidate build → "مرشّح" as a person; hashed → "مُجزّأ"; replay → "إعادة تشغيل".
- Good: revision → "نسخة (Revision)"; audit → "سجل الـ Audit"; dismissal of an approval → "سحب الموافقة"; stale → "مبقاش مواكب للأسعار أو الشروط الحالية"; typed client → "Client بأنواع محددة (Typed)"; commit → "Commit"; seeded → "اتضافت بالـ Seed"; candidate build → "نسخة مرشّحة للإصدار"; hashed → "متخزّن كـ Hash"; replay → "إعادة إرسال نفس الطلب (Replay)".
- Why: pick the sense from the surrounding code and documentation.

## A4 Concept confusion
- Bad: "بيئة Staging" used for a Preview environment; "الاسترجاع" for both backup and rollback; "التصريح" for both authentication and authorization; "تجريبية" for both synthetic data and experimental features.
- Good: say which environment the project defines; "نسخة احتياطية (Backup)" vs "استرجاع (Restore)" vs "رجوع لإصدار سابق (Rollback)"; "التحقق من الهوية (Authentication)" vs "الصلاحيات (Authorization)"; "بيانات تجريبية غير حقيقية (Synthetic Data)".
- Why: readers act on these distinctions (what to run, what data is at risk).

## A5 Direction or attribution reversed
- Bad: "A ← B ← C" for "A → B → C"; "المطبخ بيستردّه" for "the kitchen refunds it"; "أثر الأوردرات على الإجراء" for "the effect of the action on active orders"; "موافقة الطبّاخ" for "approval of the cook".
- Good: keep arrows exactly; "المطبخ بيرجّعه للعميل"; "تأثير الإجراء على الأوردرات الفعّالة"; "اعتماد الطبّاخ".
- Why: it changes who does what or the order of states.

## A6 Register problems
- Bad (too formal): "يتعين على المستخدم التأكد من تثبيت البرمجية المذكورة آنفًا". Bad (too casual or dropped negation): "شغّله وخلاص يا باشا" · "مابينادي" (negation without final ش).
- Good: "اتأكد إن Docker متسطّب قبل ما تكمّل." · "مابيناديش".
- Why: professional conversational tone, correct Egyptian grammar.

## A7 Unnecessary or missing English
- Bad (decoration): "الـ Step الأول هو الـ Check بتاع الـ Version". Bad (over-translation): "الصور" for Docker images, "حاوية" in every place, "الفرع" when the page is about a named Git Branch workflow.
- Good: "الخطوة الأولى: اتأكد من رقم الإصدار." · "صور الـ Container (Docker Images)".
- Why: English is for precision where engineers know the English term; ordinary language stays Arabic.

## A8 Dropped qualifiers and identifiers
- Bad: "(API-108)" removed from a transition; "only" dropped ("بس"); "at least 10 characters" → "10 حروف"; "takes no address" → "مابيطلبش عنوان".
- Good: keep every identifier and qualifier: "...اتبعت (API-108)"؛ "10 محارف على الأقل"؛ "مابياخدش عنوان".
- Why: completeness and exact thresholds are part of the instruction.

## A9 Typography and right-to-left presentation
- Bad: "وومن غير" (doubled prefix) · "اتر فض" (split verb) · a number glued to an Arabic prefix ("و3") · arrows or code scrambled inside a right-to-left line · Arabic-Indic digits.
- Good: "ومن غير" · "اترفض" · "و 3" or "من 1 إلى 3" · long Latin identifiers in code spans · Western digits.
- Why: breaks readability and tool-based checks.

## A10 A statement of fact turned into an instruction, or an order word read wrongly
- Bad: "not tested here" → "ماتجرّبش هنا" (reads as "do not test here") · "No permission was changed to produce this page" → "ماتغيّرش أي صلاحية عشان تتكتب الصفحة" (reads as an order to the reader) · "a later drop-in file overrides it" → "ملف أحدث بيلغيه" ("later" is processing order, not newer) · "client" in an SSH context → "عميل" meaning a customer.
- Good: "لسه مااتجرّبش هنا" or "محدّش جرّب ده هنا" · "ماحصلش أي تغيير في الصلاحيات عشان نكتب الصفحة دي" · "ملف Drop-in بييجي بعده في ترتيب قراءة الإعدادات بيعمل له Override" · "جهاز الـ SSH Client المتصل".
- Why: the reader may act on the sentence. Decide whether the English reports a fact, gives a recommendation or gives an order, and keep that mood; resolve order words ("later", "last", "client", "head") from the technical context.

## How these examples are used
1. During translation: consult the style guide and these categories.
2. During the native-style editorial review: look for each category by reading, not by search.
3. In tests: the phrase checker's warnings and the before/after fixture (`examples/arabic-editorial-fixture/`) show a literal translation corrected without changing a single command, path, identifier or number.
4. When a new recurring mistake is confirmed, add a sanitized Bad/Good pair here (maintenance fix; remove project-specific names).
