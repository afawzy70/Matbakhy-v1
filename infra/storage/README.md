# Object storage layout (MB-013)

Local development and tests use the S3 emulator in `infra/compose.yaml` (buckets created at start). It does not enforce access policies or signature expiry, so the rules below must be enforced by the production provider (open decision O07) and verified before any live upload (P09).

| Bucket | Contents | Required production policy |
|---|---|---|
| `matbakhy-private` | Client uploads under `uploads/<media-id>` (quarantine and originals), verification documents | No public access, no listing. Reads and writes only through signed URLs issued by the API (upload 10 minutes, read 5 minutes) or by the API's own credentials. Server-side encryption. Lifecycle rule removing objects of abandoned uploads. |
| `matbakhy-public` | Approved, re-encoded public variants under `media/<media-id>.jpg` | Public read through the CDN/public base URL (`OBJECT_STORAGE_PUBLIC_BASE_URL`); writes only by the API. Never holds originals, documents or unapproved media. |

API settings: `OBJECT_STORAGE_ENDPOINT` (API to storage), `OBJECT_STORAGE_CLIENT_ENDPOINT` (host embedded in signed URLs), `OBJECT_STORAGE_REGION`, `OBJECT_STORAGE_ACCESS_KEY`, `OBJECT_STORAGE_SECRET_KEY`, `OBJECT_STORAGE_PRIVATE_BUCKET`, `OBJECT_STORAGE_PUBLIC_BUCKET`, `OBJECT_STORAGE_PUBLIC_BASE_URL`. Staging and production refuse to start with the local emulator credentials or without a public base URL, and refuse to start without a real malware scanner.
