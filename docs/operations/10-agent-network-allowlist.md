# Network allowlist needed to review native screenshots (request, 2026-10-14)

Implementation-agent request. Nothing here widens access by itself: the owner changes the environment's network settings, or does not.

## Decision and status (2026-10-14, later)

- **Owner decision:** conditionally approved for `productionresultssa11.blob.core.windows.net` only, after the endpoint's purpose is verified. No wildcard.
- **Purpose verified** (implementation-agent check, below): it is GitHub's storage for Actions artifacts. The condition is met for that host.
- **Not applied yet in this session:** a download from the approved host was still refused (`CONNECT tunnel failed, response 403`, proxy log: `productionresultssa11.blob.core.windows.net:443` rejected, checked again after the decision). The network setting is changed by the owner in the environment's settings, and may only apply to a new session.
- **The host rotates.** GitHub issued a link on `productionresultssa11` for run 48's `ios-screenshots` (artifact 11830230070, the original request) and on `productionresultssa19` for run 51's `android-screenshots` (artifact 11872003401, 07:43 UTC). So the approval covers only artifacts that GitHub happens to store on `sa11`. The agent does not try any other host. Run 51's `android-screenshots` is on `sa19`, **which is not approved** (its `ios-screenshots`, 11872203856, was not asked for; its host is unknown). Options for the owner: approve `sa19` as one more exact host, or download the two artifacts himself.
- **Screenshots reviewed so far: none.** The log-based edge report (32 screens, none past the screen edge) stays the only check; it is not a visual review.

## How the purpose was verified

1. GitHub's documentation lists `*.blob.core.windows.net` among the endpoints Actions needs for artifacts and results (docs.github.com, "Communicating with self-hosted runners"; github.blog changelog of 2023-08-29). It publishes only the wildcard, which this request deliberately does not ask for.
2. The signed link comes from GitHub's own artifact API for an artifact of this repository (`actions_get download_workflow_run_artifact`), and the path starts with `/actions-results/`.
3. **Every download is checked before it is opened:** its SHA-256 must equal the `digest` GitHub reports for that artifact in the artifact listing. A mismatch means the file is discarded unopened.

## Download procedure (once a host is allowed)

```sh
d=$(mktemp -d)                                   # a new, empty directory per artifact
curl -fsS -o "$d/artifact.zip" "<signed url from the GitHub tool>"
echo "<digest from the artifact listing>  $d/artifact.zip" | sed 's/^sha256://' | sha256sum -c -
mkdir "$d/files" && unzip -q "$d/artifact.zip" -d "$d/files"   # only the screenshots; images are viewed, never executed
```

The signed link is valid for about 10 minutes and carries its own authorization; no token is sent or stored. Scripts that read the files run with `python3 -I`.

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
