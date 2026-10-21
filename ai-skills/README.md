# AI skills library (owner-controlled)

Reusable AI capabilities for any repository. This directory is curated by the repository owner.

## Rules
- Only the owner authorizes a new skill. Agents may recommend one (record it in `registry.yaml` under `recommendations_not_created` only after the owner asks for recommendations, or in a report); they must not create it.
- Significant scope or behaviour changes to an existing skill need owner approval; maintenance fixes do not.
- Skills are reusable across repositories and stacks: no private infrastructure details, host names, accounts, secrets, customer data or project credentials inside a skill.
- Never publish a skill to an external registry without owner approval.
- Skills are documentation/process capabilities; they never grant permission to deploy, spend money or change live systems.

## Current skills
| Skill | Purpose | Entry |
|---|---|---|
| `developer-handbook` | Create, audit, maintain and validate a bilingual software engineering and infrastructure handover handbook | [developer-handbook/SKILL.md](developer-handbook/SKILL.md) |

## Using a skill
In Claude Code, say: "Use the developer-handbook skill for this repository."

## Installing a skill in another repository
Pick one (do not keep two diverging copies):
1. **Project skill (recommended):** copy the directory `ai-skills/developer-handbook/` into the other repository as `.claude/skills/developer-handbook/` (or keep it under `ai-skills/` and add a relative symlink `.claude/skills/developer-handbook -> ../../ai-skills/developer-handbook`).
2. **Personal skill:** copy it to `~/.claude/skills/developer-handbook/`.
Then install the parser used by the validator: `python3 -m pip install 'markdown-it-py==3.0.0'`, and run `python3 .claude/skills/developer-handbook/scripts/test_skill_package.py` to check the copy.

## Tests
`python3 ai-skills/developer-handbook/scripts/test_skill_package.py` (files, metadata, internal references, no project-specific dependencies, safety and bilingual rules), `test_bilingual_parity.py` (validator positive and negative tests), `test_fixture_dry_run.py` (isolated end-to-end dry run on a small fixture project), `test_editorial_fixture.py` (a literal Egyptian Arabic translation corrected without changing any technical content, phrase-checker positive/negative samples). They prove packaging and the validator's behaviour; they do not prove that an AI session will follow the skill perfectly.

## Provenance
The method was extracted from a real bilingual handbook delivery (English/Egyptian Arabic mirror with a structural parity validator, five independent semantic review rounds, and an infrastructure handover). Only the reusable method is included; no project facts.
