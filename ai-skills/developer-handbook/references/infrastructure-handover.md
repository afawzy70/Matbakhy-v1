# Infrastructure handover content (Stage C)

Goal: an infrastructure engineer unfamiliar with the project can understand the hosting reality, rebuild it on a clean server, operate it and diagnose failures. Document the technology actually detected (virtual machines, containers, orchestrators, serverless, cloud services, bare metal). Do not assume a provider, a proxy or a container runtime.

## Page set (adapt to what exists)
1. **Environment inventory**: operating system, resources, storage layout, time zone, installed infrastructure packages, scheduled jobs, listening ports and interfaces.
2. **Accounts and permissions**: every relevant user and group with verified memberships, home and shell, ownership of application directories, which account performs which task *only where evidence exists*. Never infer a role from an account name. Record which account the CI/CD system connects as (often a secret: then `NOT VERIFIED` plus how to confirm).
3. **Access and security**: SSH policy as readable, authentication methods, root and password policy, host-key verification, firewall layers (host and provider), key rotation and recovery, privilege boundaries. Never reveal key material.
4. **Containers / services**: images (pinned by digest?), networks, published versus internal ports, health checks, restart policies, resource limits, volumes and bind mounts, logging, data persistence boundaries. If the runtime cannot be inspected without privileges, document it from the definition files as `VERIFIED IN CODE` and mark live state `NOT VERIFIED`.
5. **Runtime directories**: purpose, owner, group, mode, how created, how restored, safe to delete or not. No file contents of secrets.
6. **Networking and edge**: public entry points, reverse proxy routing per host, TLS/ACME, DNS, access classes, security headers, internal-only services, administration surfaces.
7. **Architecture diagrams**: topology, container topology, authentication flow, request routing, persistence, secrets, observability, deployment, failure and recovery paths. Distinguish public traffic, private traffic, administrative access, CI/CD connections and data-store traffic. Add an end-to-end request walkthrough.
8. **Rebuild guide**: see `deployment-and-recovery.md`.
9. **Troubleshooting**: scenarios in a fixed format (symptoms, likely causes, safe diagnostic commands, expected evidence, corrective action, risk level, recovery verification).
10. **Administrator read-only command sheet**: the minimal privileged read-only commands that close the `NOT VERIFIED` gaps; stating who runs them and what not to paste.
11. **Drift report**: documentation versus code versus live observation; superseded-provider instructions labelled historical with a pointer to the current page.

## Command table (mandatory for every executable step)
exact command | machine | required user | read-only or state-changing | prerequisite | expected result | verification command | likely failure and what to do.

## Rules
- Separate "clean installation" from "restore from backup".
- Secrets: describe generation and storage, not values. Name the secrets that must be preserved to recover.
- Mark every state-changing or destructive step with a warning and restrict it to a new isolated server.
- Never weaken an existing access restriction as part of documenting it.
