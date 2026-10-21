# Deployment notes

> **Language / اللغة:** English · [العربية](deploy-notes-ar.md)

This document is a living document. It is updated as the project evolves.

The configuration lives in machine-readable files under `config/`.

Before you deploy, make sure CI is green and the schema did not change.

Real payments are not enabled yet, and all data here is synthetic data.

If the deployment fails, use an image-only rollback: go back to the previous Docker images without restoring the database.

```bash
./deploy.sh up --release 2.4.1
```

- Check `config/app.yaml`.
- Run `./deploy.sh smoke`.
