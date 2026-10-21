# Deployment, rebuild and recovery content (Stage C, continued)

## Delivery lifecycle (document what the workflow definitions prove)
Developer change, pull request, reviews, CI tests, merge, build, artifact/registry publish, deployment, health verification, live application. For each step say whether it is **automated** (a workflow proves it), **manual** (a person does it), **required policy** (branch protection or settings, only if verifiable) or **proposed** (not implemented). Do not assume a review or security agent exists unless a workflow or setting proves it.

Cover: triggers; build per component; image/artifact tagging and digest pinning; registry authentication; how the CI system authenticates to the target (secret names only); what is transferred; which directories are updated; how server-only configuration is preserved; migrations; health and smoke checks; failure reporting; whether rollback is automatic or manual and what it does and does not restore. Include a sequence diagram.

## Operational verification (read-only)
How to find the deployed revision, current artifact versions, whether the latest main line was deployed, workflow success, container health, migration completion, TLS validity, reachability and smoke results. Give commands and expected outputs; say which need privileges.

## Rebuild from a clean machine (documentation-verified is not execution-verified)
Order: provision, base OS and updates policy, users and groups, SSH hardening, firewall, dependencies, runtime installation, permissions groups, directories and ownership, secrets generation, source/CD connection, registry access, first deployment, data stores, identity provider, object storage, reverse proxy, DNS, TLS, CD hookup, seed/synthetic data, smoke tests, backups, logging and monitoring, rollback readiness. Mark the guide `DOCUMENTATION VERIFIED, NOT EXECUTION VERIFIED` unless an isolated rebuild was actually performed and recorded; say what any rehearsal script actually proves.

## Backup, restore, rollback, disaster recovery
- Backups: locations, schedule, contents, verification, retention, off-machine copies. A backup script is not proof of recoverability.
- Scenario A fresh installation (empty data) and Scenario B disaster recovery (replacement machine, restored data). Distinguish creating new identities from restoring existing identity state, and list the credentials and encryption keys that must be preserved for stored data to remain readable (names only).
- Rollback: artifact-only rollback, migration compatibility (expand/contract, forward-only), breaking migrations, backup-based restoration, potential data loss, a decision table.
- Verification after restore: data integrity, authentication, application functionality, consistency.
- Destructive tests only on an isolated copy, never on the live environment.

## Domain and TLS
Explain DNS records, TLS and certificate authority issuance/renewal, certificate persistence, expiry, common failures and rate limits; list hardcoded host names and the exact places to change when the domain changes, as a separate "required implementation changes" list (not applied).
