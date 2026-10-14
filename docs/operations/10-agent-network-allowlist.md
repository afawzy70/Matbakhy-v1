# Network allowlist needed to review native screenshots (request, 2026-10-14)

Implementation-agent request. Nothing here widens access by itself: the owner changes the environment's network settings, or does not.

## What is blocked

The native CI jobs (`native-build.yml`) upload screenshots as GitHub Actions artifacts (`android-screenshots`, about 1.3 MB; `ios-screenshots`, about 5.6 MB; synthetic data only). The agent can read job logs through the GitHub tools, but cannot download the artifacts: the download link points at Azure blob storage, and the session's network proxy refuses it.

Observed on 2026-10-14:

```
curl: (56) CONNECT tunnel failed, response 403
host: productionresultssa11.blob.core.windows.net  (HTTPS, port 443)
path: /actions-results/<id>/workflow-job-run-<id>/artifacts/<id>.zip?<signed query>
```

The signed link is issued by GitHub's artifact API, is valid for about 10 minutes, and carries its own authorization (`sig`, `se`, `sp=r`). The agent sends no credentials to it.

## Why it is needed

Visual review of the largest-text screens (MB-033) on Android and iOS, in English and Arabic. The agent now prints an edge report to the job log (no labelled element runs past the screen edge on 32 screens). That check cannot see text cut off inside an element, overlapping elements or RTL mirroring errors; only looking at the screenshots can. The review would be labelled implementation-agent review, not human approval.

## Minimum allowlisting

- Allow **only** `productionresultssa11.blob.core.windows.net` (HTTPS, GET).
- Do **not** allow `*.blob.core.windows.net`: it covers every Azure storage account of every tenant.
- GitHub can move artifact storage to another account (`productionresultssaNN`). If a later download is refused, the 403 names the host; add that one host only after checking it matches the form above.
- Where to change it: the cloud environment menu in the session title bar, then Edit, then Network access, then Allowed domains (see https://code.claude.com/docs/en/cloud-environments#network-access).
- No tokens, cookies or secrets are required or stored. Files are saved to the session scratchpad, one new directory per download, and are only read as images.

## Alternatives that need no network change

- The owner opens the two artifacts from the run page and looks at the screens (human review; this is the stronger evidence for MB-033 anyway).
- A CI step that stores a contact sheet of the screenshots in the repository or on a QA branch. Not recommended: it puts binary files in a public repository, even if synthetic.
