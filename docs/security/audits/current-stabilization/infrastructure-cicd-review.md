# Infrastructure and CI/CD security review

Source `b7ec0e9052a612f7a8aab38f00519107b1dce213`; host `vmi3673442`; [host listener metadata](evidence/host-listening-ports.json). Static configuration review plus bounded HTTPS requests. Secret values, Docker configuration, container environments, private SSH material and user logs were not read or printed.

## Compose, storage and proxy

Preview Compose publishes proxy 80/443 and Grafana on 127.0.0.1:3000; database/API/identity/storage are on an internal network with no declared public ports. Resource/log limits exist. API Dockerfile creates and uses a dedicated non-root user; front image inherits Caddy defaults, with no explicit non-root USER/healthcheck and mutable FROM tags. The configuration does not consistently set read_only, cap_drop or no-new-privileges. Treat these as hardening work; no host escape was demonstrated. Several third-party/backend images are digest pinned. Runtime effective users/capabilities/mounts and actual GHCR image OS vulnerabilities are NOT TESTED because Docker socket access was denied.

Caddy sets HTTPS/HSTS/nosniff/referrer policy and denies framing on app/ops; CSP missing (SEC-004). It permits only configured API/OIDC routes, blocks public actuator/Swagger paths, identity admin/master routes, and has separate customer/staff source-IP classes. Source-IP classification is not a substitute for API permissions. Both interfaces were reachable from this server, but the server may be trusted: no assertion that staff access is globally public. Independent external-source allowlist tests remain NOT TESTED. Caddy has access logs off; app/log collection must avoid sensitive data by design.

Media proxies the S3 service; its template grants anonymous Read only to the public bucket. Private downloads are scoped signed attachments; private bucket and guessed private object probes returned 403, root listing 403. The API S3 identity has broad Admin/Read/Write/List/Tagging rights; narrow to necessary buckets/actions before real deployment. Public variants are image re-encodes; private document sealing and PDF checks have SEC-002/SEC-001 findings. No private listing or content retrieval attempted.

PREVIEW deliberately permits synthetic fixtures and fake outbound/malware providers; STAGING/PRODUCTION guards reject missing real providers or development secrets. This supports a demonstration environment and cannot establish production readiness. A real malware scanner and secure document parsing are separate controls. Environment variables/secrets must remain outside Git with restrictive files; this audit did not inspect server .env contents or verify effective filesystem permissions.

## Identity and data

Template Keycloak uses start (not start-dev), database persistence, fixed HTTPS hostname and forwarded headers; exact callback, confidential web client, PKCE S256, direct password grant disabled, registration disabled and brute-force protection enabled. Admin/master proxy boundaries returned 404. Actual realm drift, admin credential policy/MFA enrollment, session cookies after authentication and credential rotation are NOT TESTED. Do not enable a mobile preview client by copying development secrets; preview template currently declares the web client, while native source has a separate public-client model.

Database runtime/migration roles are separated in configuration; actual live grants, superuser ownership, backup encryption/retention and restore isolation are NOT TESTED. No SQL was executed against preview. Backups and logs can contain PII and require operational access review beyond public endpoint testing.

## GitHub Actions / GHCR / SSH

Product/blueprint workflows use contents:read. Checkout actions are pinned by commit. No pull_request_target execution of untrusted PR code with secrets was found in reviewed workflows. Deploy-preview grants contents:read, actions:read and packages:write at workflow scope; reduce packages write to the build job where feasible. Images are published by exact source tags and deployed by resolved digests. Deployment waits for successful Product CI on the same SHA and deployment job requires develop plus preview environment. Manual dispatch on other branches cannot pass the deploy job condition. Repository environment protection/reviewer settings and token/package visibility are NOT TESTED through configuration APIs.

SSH host key is supplied/pinned and StrictHostKeyChecking remains enabled; private key file mode 600 and SSH directory 700. GHCR token is passed by stdin and a temporary remote DOCKER_CONFIG is removed; server .env/data are preserved. The deploy identity can transfer deployment code and invoke Docker; its host authority and forced-command restrictions need a live administrator review. Secret existence/rotation and exact GitHub access policies are NOT TESTED; no credentials read. Failed deployment restores the config file but does not automatically reverse image/schema state; this is an operational recovery limitation requiring documented controlled recovery, not proof of a security exploit.

## Host and Remote Control

Read-only `ss -lnt` observed public/all-interface listeners 22/80/443 and loopback 3000/3080. Database/Keycloak/S3 management ports were not seen bound publicly in that snapshot. Kernel listeners do not prove remote firewall reachability. Private/VPN addresses were omitted from persisted evidence. Port 3080 process identity, Remote Control authentication, Tailscale ACLs, firewall rules, SSH credential restrictions and service unit settings are **NOT TESTED**; socket permissions do not authorize reading their secrets or restarting sessions. No active agent process was interrupted.

The rehearsal helper disables certificate/hostname validation (SEC-003); independent probes used verified TLS and never ran that load helper. Full TLS protocol/cipher analysis, SSH daemon configuration audit and external port reachability tests remain NOT TESTED. Remediation and deployment belong to Claude/owner; this branch contains documentation/evidence only.

Follow-up `1a7d6a9da2af2acfa4accfdcc8133ece276817ca`: afterCommit cleanup now catches RuntimeException and logs its full throwable. This is a new log-redaction review target, not verified sensitive disclosure; run a synthetic provider-exception test with sentinel secrets and assert none reach stored logs. Do not import real provider logs into this public audit.
