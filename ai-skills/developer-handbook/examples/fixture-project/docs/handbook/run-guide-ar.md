# دليل التشغيل

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — إزاي تشغّل خدمة المثال محليًا
> **Language / اللغة:** [English](run-guide.md) · العربية

ثبّت الاعتماديات وشغّل الخدمة. اتأكد الأول إن Python 3.12 متسطّب.

| الخطوة | الأمر | قراءة فقط |
|---|---|---|
| التثبيت | `pip install -r requirements.txt` | لأ |
| التشغيل | `flask --app app.main run` | لأ |

```bash
curl -s http://127.0.0.1:5000/health
```

- [ ] الرد فيه `UP`.
