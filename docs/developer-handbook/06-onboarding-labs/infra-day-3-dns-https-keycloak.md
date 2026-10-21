# Infrastructure Day 3 - DNS, sslip.io, Caddy, HTTPS and Keycloak routing

> **Owner role:** Infrastructure and deployment
> **Status:** REFERENCE — lab for checking DNS, TLS and routing of the public Preview with read-only client commands and for reading the Caddy configuration.
> **Language / اللغة:** English · [العربية](infra-day-3-dns-https-keycloak-ar.md)

## Evidence legend

| Label | Meaning |
|---|---|
| VERIFIED LIVE | Observed on 2026-10-21 by the unprivileged user aiagent on the Contabo VPS, using curl against the public hosts from the server itself. |
| VERIFIED IN CODE | Read in this repository (file named). It shows intent, not necessarily what runs. |
| DOCUMENTED ONLY | A document or a tool's usual behaviour states it; nothing confirmed it here. |
| OUTDATED / INCORRECT | Written for the proposed Hetzner host or superseded. |
| NOT VERIFIED | Not observed. |

## Objectives

- Explain what sslip.io does and why the Preview needs no DNS account.
- Check DNS, the TLS certificate and the HTTP behaviour of each Preview host with dig, curl and openssl.
- Explain why `media.*` answers 403 at its root and why `id.*` answers 302, and show both are expected.
- Read the Caddyfile and find which host forwards to which container.
- Know that Caddy obtains and renews Let's Encrypt certificates, and what is and is not verified about renewal.

## Prerequisites

- Days 1 and 2 completed: [Day 1](infra-day-1-topology-accounts.md) and [Day 2](infra-day-2-docker-services.md).
- Read [DNS, HTTPS and sslip.io](../08-infrastructure/dns-https-sslip-io.md) and [live Preview access](../05-developer-workflow/live-preview-access.md).
- A laptop with dig, curl and openssl installed. Nothing is installed or changed on the server.

## Rules for this lab

- Run the commands below only from your laptop against the public hosts. They send ordinary read requests (a DNS query, an HTTPS request, a TLS handshake). They create no account, order or upload and never use credentials.
- The Preview applies access classes by caller address (CUSTOMER_ALLOWED_IPS and STAFF_ALLOWED_IPS in `deploy/web-preview/Caddyfile`). From a network outside the class you can legitimately get 403 with the plain body "Not available". That is an access-policy answer, not an outage. The live results in this lab were taken from the server's own address, so yours can differ.
- Do not request certificates, change DNS, or run load. Do not hammer the hosts; one request per command is enough.
- Replace `<preview-domain>` with the documented Preview domain 167-86-90-174.sslip.io (public in the repository).

## Guided tasks

### Task 1. Understand sslip.io by resolving a name

| Item | Detail |
|---|---|
| Command | `dig +short A app.<preview-domain>` |
| Machine | your laptop |
| Linux user | your own |
| Kind | read-only (a public DNS query) |
| Prerequisite | dig installed |
| Expected result | Prints 167.86.90.174 (VERIFIED LIVE: the names resolve to that address) |
| Verification | Run `dig +short A app.<preview-domain> @1.1.1.1` and compare; both answers agree |
| Likely failure | Empty output. Check spelling (dots and dashes), the resolver, or try the second command. If both are empty the public service may be down; report it, do not work around it |

Explain in your notes why this works: the sslip.io service answers any hostname that contains an IPv4 address with that address (DOCUMENTED ONLY, described in [DNS, HTTPS and sslip.io](../08-infrastructure/dns-https-sslip-io.md)). Nobody on the Matbakhy side creates DNS records for the Preview. Whether an AAAA record is returned is NOT VERIFIED; the server does have a public IPv6 address (VERIFIED LIVE) but that does not prove a DNS record for it.

### Task 2. Inspect the TLS certificate

| Item | Detail |
|---|---|
| Command | `openssl s_client -connect app.<preview-domain>:443 -servername app.<preview-domain> </dev/null 2>/dev/null \| openssl x509 -noout -issuer -subject -dates` |
| Machine | your laptop |
| Linux user | your own |
| Kind | read-only (a TLS handshake) |
| Prerequisite | openssl installed |
| Expected result | Issuer Let's Encrypt; a notBefore and notAfter about 90 days apart. At the time of observation the app host certificate was valid 2026-10-18 to 2027-01-16 (VERIFIED LIVE for the app host only); a newer renewal will show later dates. The issuer and dates of the other hosts (ops, api, id, kc-admin, media) were not recorded: repeat the command for each and treat any difference, including a different certificate authority, as an observation, not an error |
| Verification | Compute the number of days between the dates. Compare the notAfter date with today's date and write down how many days remain |
| Likely failure | Empty output means no handshake. The `-servername` flag is required because the proxy selects the certificate by the requested name |

Questions: who obtains this certificate? Caddy, automatically, using the email in ACME_EMAIL (VERIFIED IN CODE: the global block of `deploy/web-preview/Caddyfile` and `deploy/web-preview/compose.yaml`). Where do certificates persist? In the named volume caddy-data mounted at /data in the proxy container (VERIFIED IN CODE; the contents on the live host are NOT VERIFIED LIVE). Has renewal actually happened on the live host? NOT VERIFIED: Caddy renews automatically (DOCUMENTED ONLY), but no renewal event has been observed. Repeat this command after the expiry window approaches (the page on DNS and HTTPS describes the renewal window) and record what you see.

### Task 3. Check the HTTP behaviour of every host

Run each command once and record the status.

| Command | Machine | Linux user | Kind | Expected result (VERIFIED LIVE from the server's address) | If it differs |
|---|---|---|---|---|---|
| `curl -sS -o /dev/null -w '%{http_code}\n' https://app.<preview-domain>/` | your laptop | any | read-only | 200 | 403 with "Not available": you are outside the customer class. Timeout: port 443 is not reachable from your network |
| `curl -sS -o /dev/null -w '%{http_code}\n' https://ops.<preview-domain>/` | your laptop | any | read-only | 200 | 403: you are outside the staff class (normal for most networks) |
| `curl -sS https://api.<preview-domain>/api/v1/configuration/public` | your laptop | any | read-only | 200 with JSON containing orderingAvailable true and environment name PREVIEW, demo true | 403: outside the customer class |
| `curl -sS -o /dev/null -w '%{http_code}\n' https://api.<preview-domain>/actuator/health` | your laptop | any | read-only | 404, because the proxy forwards only /api/*, /oauth2/*, /login/oauth2/* and /logout (VERIFIED LIVE and VERIFIED IN CODE) | 200 would be a defect in the routing; report it |
| `curl -sS -I https://id.<preview-domain>/` | your laptop | any | read-only | 302 with a Location header pointing at the kc-admin host's /admin/ | 403: outside the customer class |
| `curl -sS -I https://kc-admin.<preview-domain>/` | your laptop | any | read-only | 302 with Location /admin/master/console/ while the admin-console flag exists | 404: the flag is absent and the host is closed (fail closed by design) |
| `curl -sS -D - -o /dev/null https://media.<preview-domain>/` | your laptop | any | read-only | 403 with Content-Type application/xml and an x-amz-request-id header | 403 with the body "Not available" and no x-amz-request-id means the proxy refused you (access class) |

Never open the kc-admin console and never try to sign in as part of this lab. It requires a password and a one-time password and is not a training target.

### Task 4. Explain the media 403 (expected)

1. Open `deploy/web-preview/Caddyfile` and find the block for `media.{$PREVIEW_DOMAIN}`. It applies the customer access check and then `reverse_proxy storage:8333` (VERIFIED IN CODE).
2. The live response had Content-Type application/xml and an x-amz-request-id header (VERIFIED LIVE). Caddy's own refusal is plain text with no such header, so the answer came from the S3 gateway (SeaweedFS) behind Caddy. The root path is an anonymous request to list buckets, which S3 refuses without credentials.
3. Public objects live under the prefix /matbakhy-public/ (VERIFIED IN CODE: OBJECT_STORAGE_PUBLIC_BASE_URL in `deploy/web-preview/compose.yaml`). So a 403 at the root is the expected result for the root path. The exact error code in the body was not stored and is not claimed here.
4. Write one sentence on how you would tell "expected 403" from "media is down": a down service shows 502 or a connection error, not an S3 style 403 (NOT VERIFIED; derived from how a reverse proxy behaves).

### Task 5. Explain the id 302 (expected)

1. In `deploy/web-preview/compose.yaml`, service identity, find KC_HOSTNAME (https://id.<domain>) and KC_HOSTNAME_ADMIN (https://kc-admin.<domain>) (VERIFIED IN CODE).
2. Observe the Location header from Task 3: it points at the admin host. The most likely cause is Keycloak redirecting its root to the admin URL because an admin hostname is set. This is an inference from the configuration and the header; it was not confirmed inside Keycloak.
3. Find in the Caddyfile that `/admin` and `/realms/master` paths answer 404 on the id host, so the admin console is never served there (VERIFIED IN CODE).
4. Customer sign-in uses `/realms/matbakhy/...`, not the root. Run `curl -sS https://id.<preview-domain>/realms/matbakhy/.well-known/openid-configuration` (read-only; 200 with an issuer ending in /realms/matbakhy from the customer class, DOCUMENTED ONLY here since the live facts did not record this call; the compose health check requests the same path inside the container, VERIFIED IN CODE).
5. State why this is not an error and what you must not do: do not add a redirect or rewrite on the id host to "fix" it.

### Task 6. Read the Caddyfile

| Item | Detail |
|---|---|
| Command | `sed -n 1,66p deploy/web-preview/Caddyfile` |
| Machine | your laptop |
| Linux user | your own |
| Kind | read-only |
| Expected result | The email global option, the shared header snippet, the two access snippets and the app and ops site blocks |
| Verification | Complete the table below from the file |

| Host | Serves or forwards to | Access check |
|---|---|---|
| app | static customer web from /srv/customer-web | customer class |
| ops | static operations web from /srv/operations-web | staff class |
| api | api:8080 for /api/*, /oauth2/*, /login/oauth2/*, /logout; everything else 404 | customer class for every request; for /api/v1/ops and /api/v1/cook the staff class is checked first and then the customer class as well, so a staff caller must be in both lists |
| id | identity:8080, except admin and master realm paths (404) | customer class |
| kc-admin | identity:8080 for the console paths only, and only while the flag exists | none by address; Keycloak password plus one-time password |
| media | storage:8333 | customer class |

Then run `python3 scripts/test_proxy_access.py` only if you have the prerequisites it states: it starts the real Caddyfile in a Caddy container, so Docker and a Caddy image must already be present on your laptop, and without them the test is skipped. A skipped test is NOT RUN, not PASS. Read the head of the script first (it exercises the Caddyfile in the repository, VERIFIED IN CODE). Do not report PASS for a test you did not run.

### Task 7. Plan (do not run) a domain replacement

Read section 6 of [DNS, HTTPS and sslip.io](../08-infrastructure/dns-https-sslip-io.md), "Domain replacement runbook". Write a half-page plan answering: which variables change, in which files, what must be reachable from the Internet before the certificate can be issued (ports 80 and 443), what happens to existing application accounts (the API identifies an account by issuer and subject, so a new OIDC_ISSUER does not match existing accounts unless you migrate them or decide on a reset), in which order the server configuration and the merge to develop must happen (a push to develop deploys automatically with the domain the server .env holds at that moment), what happens to the old host names (the Caddyfile builds every host from the single PREVIEW_DOMAIN, so recreating the proxy with a new value removes the old host names: a cutover has downtime unless dual-domain routing is separately designed and rehearsed, which the handbook has not done; the decision belongs to the owner), and how to roll back, including the Keycloak settings that are stored in Keycloak and are not undone by restoring .env. Do not execute any step; domain replacement is a state-changing operation for an authorized engineer.

## Verification checkpoints

| Checkpoint | Pass when |
|---|---|
| DNS | You resolved at least two Preview hosts and explained why no DNS account exists |
| TLS | You recorded issuer, validity dates and days remaining, and stated what is NOT VERIFIED about renewal |
| HTTP table | All seven rows are filled with your own observed status, and any difference from the live facts is explained by the access class |
| media and id | You can explain both responses in your own words with evidence labels |
| Caddy | The routing table is complete and matches the file |

## Completion criteria

- [ ] I ran only read-only client commands against the public hosts and one request per command.
- [ ] I can explain sslip.io, Let's Encrypt issuance by Caddy and where certificates persist.
- [ ] I can explain why media returns 403 at its root and why id returns 302, and that neither is an error.
- [ ] I know renewal is documented but not observed, and what an administrator can check.
- [ ] I did not open or attempt to sign in to the Keycloak admin console.

## Where to read next

[DNS, HTTPS and sslip.io](../08-infrastructure/dns-https-sslip-io.md), [runtime architecture](../08-infrastructure/runtime-architecture.md), [live Preview access](../05-developer-workflow/live-preview-access.md), [identity and security](../03-architecture/identity-and-security.md).

Next: [Infrastructure Day 4](infra-day-4-cicd-operations.md).

## Known limitations and documentation gaps

- The statuses above were observed from the server's own address. Results from other networks depend on the access classes and can be 403 "Not available".
- The renewal of the certificate and the AAAA record state are NOT VERIFIED.
- The OIDC discovery call in Task 5 was not recorded in the live facts.
