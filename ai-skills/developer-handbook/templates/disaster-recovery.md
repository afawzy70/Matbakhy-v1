# <System> backup, restore and disaster recovery

> **Owner role:** Infrastructure and deployment
> **Status:** <label> — <meaning; recoverability is NOT proven by the existence of backup scripts>
> **Language / اللغة:** English · العربية (`<name>-ar.md`; make this a Markdown link to the mirror page when you fill the template)

## Backups
| Data | Location | Schedule | Contents | Verification | Retention | Off-machine copy |
|---|---|---|---|---|---|---|

## Credentials and keys that must be preserved (names only, never values)
<list>

## Scenario A: fresh installation
<ordered steps with the command table>

## Scenario B: disaster recovery
<provision replacement, restore infrastructure, restore data stores, restore identity state, restore object storage, restore secrets securely, reconfigure domain, reconnect CI/CD, verify>

## Rollback decision table
| Situation | Action | Data loss risk | Verification |
|---|---|---|---|

## Verification after restore
<integrity, authentication, functionality, consistency>
