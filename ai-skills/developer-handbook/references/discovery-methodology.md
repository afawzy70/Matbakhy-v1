# Discovery methodology (Stage A)

Goal: build an evidence-classified inventory before writing any page. Nothing in the handbook may rest on memory or on a guess.

## Order of work
1. Run `./scripts/scan_handbook_facts.py --root <repo>` for a read-only starting inventory (technologies, CI/CD files, container and IaC files, test locations, documentation trees, git conventions). Its output is `VERIFIED IN CODE`.
2. Read, in this order: top-level README and contribution/agent instruction files, architecture and decision records, the dependency manifests, the CI/CD definitions, deployment and infrastructure files, database migrations and schema, API contracts (OpenAPI, GraphQL, protobuf), configuration templates (names only, never secret values), test directories.
3. Inventory existing documentation: for each file record purpose, audience, accuracy, implementation status, last relevant change (`git log -1 --format=%cs -- <file>`), whether it describes a superseded platform or provider, whether a translation exists, whether its links resolve, whether it suffices for independent handover.
4. Read the code that implements the documented behaviour before describing it. Where documents and code disagree, trust the code, record the conflict, and do not silently fix the source text.
5. Decide the page set from what was detected. A project without mobile apps gets no mobile chapter; one without containers gets no container chapter.

## Evidence classes (use exactly these labels)
| Label | Meaning | Typical source |
|---|---|---|
| `VERIFIED LIVE` | observed on the running environment by an authorized, read-only check | a non-privileged shell on the host, a public HTTPS request |
| `VERIFIED IN CODE` | confirmed by reading the repository | source, configs, workflows, migrations, tests |
| `DOCUMENTED ONLY` | a document says it; not independently confirmed | prose docs, comments |
| `OUTDATED / INCORRECT` | conflicts with newer or stronger evidence | superseded provider instructions |
| `NOT VERIFIED` | no evidence either way | anything unobserved |

Write the label next to the claim (column or parentheses). Never upgrade a class without new evidence. Simulations, rehearsals on other machines and dry-runs are not `VERIFIED LIVE`.

## Documentation coverage matrix
Columns: document, purpose, audience, evidence class, mentions a superseded platform, reflects current platform, translation exists, links resolve, sufficient for handover. Add a drift list: each discrepancy between documentation and code or live observation gets an ID, evidence, impact and a recommended action (documentation fix versus infrastructure change; the latter is never executed by this skill).

## Live observation (only when authorized)
Allowed: system metadata, users and groups (`id`, `getent`), directory ownership (`ls -ld`), listening sockets, container metadata through an authorized account, public DNS/HTTPS responses. Forbidden: sudo or user switching to read protected data, reading secret files, restarts, package updates, firewall, DNS, SSH or permission changes, config changes, deployments, destructive restore tests. When something needs privileges, add it to a numbered read-only command sheet for an administrator (who runs it, why, expected evidence, what must not be pasted into tickets) and mark the fact `NOT VERIFIED`.

## Output
A discovery note (kept outside the delivered pages or in the verification report) with the inventory, the evidence classes, the conflict list and the list of NOT VERIFIED items. It feeds every later stage.
