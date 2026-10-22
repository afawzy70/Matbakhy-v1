# Local staging rehearsal (not Hetzner evidence)

- Source commit built and exercised: `ea159c1f7d52d20675e8f9a0d6d23330401fab54`.
- Where: the agent's own container, local https name `mb.localhost`, `compose.yaml` + `compose.4gb.yaml`, locally built images (not digest-pinned, not GHCR), synthetic data. Run by `scripts/rehearse-preview.sh all` (down, build, up, smoke, load, persist, rollback, e2e), clean volumes, fresh realm.
- Review type: self-review only. Not independent. Not a pass of AUD-01, LOG-01, OPS-01..03 or TC-057 on a real preview.
- Not rehearsed: the Hetzner host, SSH forced-command transport, digest-pinned `deploy.sh up`, real registry images, public DNS/TLS issuance, the real network allowlist.
- Files: smoke.txt, browser-smoke.txt (3 passed), load.json (10 clients, 60 s, 17,482 requests, all 200, backup running concurrently), peaks.txt and oom.txt (sum of peaks 1,310 MiB; no OOM kills, no restarts), persist.txt (data kept across down/up; API killed with SIGKILL healthy again after 21 s), rollback.txt (older image on schema 28 serves config, meals, zones), e2e.txt (remote-mode window 6 passed), e2e-reset.txt, status.txt.
