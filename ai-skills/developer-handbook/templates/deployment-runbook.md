# <System> deployment pipeline

> **Owner role:** Infrastructure and deployment
> **Status:** <label> — <meaning>
> **Language / اللغة:** English · العربية (`<name>-ar.md`; make this a Markdown link to the mirror page when you fill the template)

## Lifecycle
<change → pull request → reviews → CI → merge → build → registry → deploy → health → live> with each step classified automated / manual / required policy / proposed, and a sequence diagram.

## Workflows
| Workflow file | Trigger | Jobs | Secrets (names only) | Proves | Does not prove |
|---|---|---|---|---|---|

## Deployment steps
| Step | Command or action | Machine | User | Read-only? | Expected | Verify | Failure and response |
|---|---|---|---|---|---|---|---|

## Failure and rollback
<what is restored automatically, what is not, data/migration risks>

## Operational verification (read-only)
| Question | Command | Expected | Needs privileges? |
|---|---|---|---|
| Which revision is deployed? | <cmd> | <output> | <yes/no> |
