# Run guide

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — how to run the fixture service locally
> **Language / اللغة:** English · [العربية](run-guide-ar.md)

Install the dependencies and start the service. Make sure Python 3.12 is installed first.

| Step | Command | Read-only |
|---|---|---|
| Install | `pip install -r requirements.txt` | no |
| Start | `flask --app app.main run` | no |

```bash
curl -s http://127.0.0.1:5000/health
```

- [ ] The response contains `UP`.
