# Agent provenance, registration and reuse

Upstream: [Agency Agents](https://github.com/msitarzewski/agency-agents), pinned commit `f99f6aa910a442b0197b768ce0ea7751e35e2060`. The original researcher and architect Markdown bodies, identities and descriptions are retained verbatim. Project responsibilities are appended to `developer_instructions`. The upstream license is retained in [UPSTREAM-LICENSE](UPSTREAM-LICENSE).

Reviewed upstream files: `design/design-ux-researcher.md`, `design/design-ux-architect.md`, `integrations/codex/README.md`, Codex conversion and selection/installation paths in `scripts/convert.sh`, `scripts/install.sh` and shared helpers. Official source-only frontmatter metadata is intentionally omitted by its converter. Display names remain **UX Researcher** and **UX Architect**. The install identifiers are **ux-researcher** and **ux-architect**, confirmed with `./scripts/install.sh --list agents` before installation.

Commands executed in the pinned upstream checkout:

```sh
./scripts/install.sh --list agents
./scripts/convert.sh --tool codex
CODEX_AGENTS_DIR="$CODEX_HOME/agents" ./scripts/install.sh --tool codex --agent ux-researcher,ux-architect --no-interactive
```

The converter generates the upstream integration output; only the two requested agents were installed. No full catalog was installed. `CODEX_AGENTS_DIR` is the official installer's supported destination override; the active cloud `CODEX_HOME` differs from the user's default home.

Before changes: `/home/agent/.codex/config.toml` was empty, active `/run/codex-environment/codex-home/config.toml` absent, and neither agent directory contained agents. No preexisting agent/configuration was overwritten. Codex CLI is `0.159.2`.

Active original-format definitions:

- `/run/codex-environment/codex-home/agents/ux-researcher.toml`
- `/run/codex-environment/codex-home/agents/ux-architect.toml`

The supported `[agents.design-ux-researcher]` and `[agents.design-ux-architect]` registry entries refer to compatibility config layers under `$CODEX_HOME/roles/`; those layers contain the same complete `developer_instructions`, without source-only `name`/`description` keys being treated as general config keys. The original-format definitions retain those identity fields. See [registration example](codex-registration.example.toml); place the two `.role.toml` files in `roles/` relative to the destination config when reusing it. Merge entries into existing configuration; never replace an existing config blindly.

**Registration VERIFIED for the active local CLI:** the official [configuration schema](https://developers.openai.com/codex/config-schema.json) supports `agents.<role>.description/config_file`. A fresh `codex app-server --strict-config --stdio` successfully initialized and returned both resolved role entries from `config/read(includeLayers=true)`. This establishes effective registry discovery beyond file copying. TOML parsing and exact preserved-body checks succeeded. [Sanitized effective config evidence](../evidence/codex-effective-registration.json), [installation/body hashes](../evidence/agent-install-verification.json), [official installer log](../evidence/agency-install.txt).

**Audit workers ACTIVE through supported explicit instructions:** this hosted session's `collaboration.spawn_agent` has no custom-role selector and cannot prove native role dispatch/hot reload. The coordinator therefore passed each complete preserved definition and project appendix to its own isolated worker. Both acknowledged reading them and produced independent reports. This is not represented as native hosted TOML dispatch. Registered CLI roles are reusable in a fresh compatible Codex session; the current audit used the explicit-instructions fallback. These are independent AI reviews, not separate human approvals.

Reusable invocation: start two isolated worktrees at the chosen implementation SHA; instruct one supported worker to read `ux-researcher.toml` and perform only the research report/journey matrix, and another to read `ux-architect.toml` and perform only architecture/evidence. Assign disjoint file ownership; coordinator deduplicates and validates. Never infer the audit target from main's README: discover the active branch/PR and pin it first. Agent sessions are not persistent background monitoring services after this audit ends.
