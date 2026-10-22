# Branch consolidation (stabilization phase, 2026-10-18)

Inspected at: `origin/develop` = `origin/implementation/mvp` = `0eaf755`. `origin/main` = `047f42e` (untouched).
Original repository `/home/aiagent/workspaces/matbakhy`: branch `ci/develop-pipeline`, clean, no stash, no untracked work (verified 2026-10-18).

## Key finding
`develop` already contains the complete MVP implementation (0 commits between `develop` and `implementation/mvp`; 314 ahead of `main`).
The only unmerged integration deltas are CI/CD workflow files (PR #7, PR #8). PR #6 (UX audit) is documentation targeting `implementation/mvp`; it is NOT mandatory feature work and is not merged here.

## Integration plan
Integration branch `stabilize/integration-2026-10-18` (from `develop`) = develop + PR #7 (`ci/develop-pipeline`) + PR #8 (`chore/contabo-auto-cd`) + stabilization docs/fixes. One PR to `develop`. PR #7/#8 are closed as superseded only after the integration PR merges. PR #1 (`implementation/mvp` -> `main`) is left open and untouched (main stays untouched).

## Branch table
| Branch | Tip | Ahead of develop | Purpose / PR | Classification |
|---|---|---|---|---|
| `main` | 047f42e | - | production line | protected, untouched |
| `develop` | 0eaf755 | - | integration/staging target | protected |
| `implementation/mvp` | 0eaf755 | 0 | MVP; head of PR #1 -> main | RETAIN (active PR #1) |
| `ci/develop-pipeline` | dc3fbb4 | 1 | PR #7 native builds on develop PRs | MERGE (via integration branch) |
| `chore/contabo-auto-cd` | 9f2ad82 | 4 | PR #8 Contabo CD on develop | MERGE (via integration branch) |
| `chore/cd-ssh-smoke` | b092595 | 1 | read-only SSH smoke workflow, no PR | RETAIN FOR LATER (decide after CD verified; superseded by PR #8 host checks) |
| `audit/ux-independent-implementation-2026-10-13` | 5ce34b0 | 7 | PR #6 UX audit docs (111 behind) | RETAIN FOR LATER (docs only; not a feature gate; PR #6 stays open) |
| `qa/full-regression-d877a15-20261013` | fa974ff | 34 | independent QA evidence, 66-case suite, native QA workflow | RETAIN FOR LATER (unique evidence/test cases; REQUIRES INVESTIGATION for harvesting reusable tests by QA agent) |
| `qa/independent-browse-basket-eb1a671-20261012` | a7ed1f3 | 2 | QA evidence | RETAIN FOR LATER (unique evidence) |
| `qa/independent-cross-role-4eaee3a-20261012` | f76664c | 6 | QA scripts + evidence | RETAIN FOR LATER (unique evidence) |
| `claude/kane-cli-qa-pilot-vx8evl` | d0ba642 | 3 | Kane CLI pilot, executed TC-K1..3 | RETAIN FOR LATER (unique evidence) |
| `claude/kane-cli-qa-pilot-resume-j9svbp` | a24bbfc | 2 | Kane pilot (auth blocked) | RETAIN FOR LATER (subset of y7scfp lineage; delete candidate after review) |
| `claude/kane-cli-qa-pilot-resume-y7scfp` | 44db32e | 4 | Kane pilot diag | RETAIN FOR LATER |
| `claude/kane-cli-qa-pilot-oyjyac` | 031a70e | 1 | Kane pilot report (egress blocked) | RETAIN FOR LATER |
| `design/approved-terracotta` | b83630f | 0 | merged PR #2; design reference | ALREADY INTEGRATED -> SAFE TO DELETE (after tag) |
| `docs/owner-mvp-first-quality-sequence-2026-10-13` | 047f42e | 0 | owner docs; ancestor of main & develop | ALREADY INTEGRATED -> SAFE TO DELETE (after tag) |
| `wip/mb-033-native-batch` | 18d0260 | 0 | WIP native batch; ancestor of develop | ALREADY INTEGRATED -> SAFE TO DELETE (after tag) |
| `agent/claude-mvp` (local worktree) | 0eaf755 | 0 | Claude worktree branch | RETAIN (active worktree) |
| `agent/codex-review` (local worktree) | 0eaf755 | 0 | Codex worktree branch | RETAIN (active worktree) |

Deletion is deferred to the final cleanup step; recovery tags `archive/<branch>` are created and pushed first. See `final-quality-gate.md` for any deletions performed.

## Environment constraints recorded
No JDK, no Docker socket access, Node 22 (repo requires >=24.21) on the VPS: backend and Testcontainers/local-stack checks cannot run locally and are authoritative only in GitHub Actions. Any such check not executed locally is reported NOT_RUN locally with the CI run as the evidence source.
