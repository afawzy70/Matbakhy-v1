# Engineering standards and definition of done

## Backend

Java 21; Maven wrapper and dependencyManagement; explicit module APIs; package by domain with controller/application/domain/infrastructure layers. Use validated request DTOs and actor-specific response DTOs, never serialize JPA entities. Constructor injection, deterministic clocks in tests, bounded timeouts and parameterized logging. Domain failures map to stable API codes. Transactions belong at application service boundaries; external sends occur after commit through outbox.

Keep controllers thin but do not build generic frameworks before concrete use. Money and stock rules have focused domain tests; concurrency, constraints and migrations use real PostgreSQL. Architecture tests enforce boundaries. No blanket exception catch returning success, swallowed future failures, unbounded retries or background in-memory jobs as sole persistence.

## Web/native

TypeScript strict mode, generated API clients, accessible components, feature-oriented files, typed forms, centralized translated messages, semantic design tokens. Separate server-state cache from local form/navigation state. Do not duplicate pricing/authorization logic in clients. Avoid `any`, suppressed lint rules and arbitrary timeouts in tests without documented need. Handle stale responses, cancellation and account changes explicitly.

Design reusable primitives after validating real screens. Web tables have coherent header/cell widths, accessible sort states, responsive overflow and server-side filtering/pagination. Native uses native navigation/accessibility and keyboard-safe layouts. No invisible fixture fallback if live API fails.

## Git and CI

Branch `feature/MB-xxx-short-name` or `fix/MB-xxx-short-name`; small commits with requirement ID. Inspect current branch/worktree first. Never discard existing edits or use force-push by default. Pull requests explain problem, behavior, evidence and residual risk. Respect actual repository protections; do not assume they are configured. Do not require GitHub self-approval from a single identity; record honest code/security review evidence separately.

Required CI expands as code appears: blueprint integrity, backend build/unit/integration/architecture/migration, frontend lint/typecheck/build/unit, contract validation/generation drift, web E2E, native tests/builds on appropriate runners, dependency/secret/security scans. A documentation-only check is named accordingly and never called product verification.

## Story done checklist

1. All dependencies complete and required decisions/design inputs available for affected scope.
2. Acceptance behavior implemented with no fake production success path.
3. Rules, DTOs, migrations, UI, fixtures and user-facing messages consistent.
4. Mapped scenarios actually executed against appropriate environment; outputs recorded at tested source SHA.
5. Review findings repaired and affected tests rerun; no unresolved critical/high defect in story scope.
6. Accessibility/RTL/object-authorization covered when applicable.
7. Logs contain no sensitive fields; provider/feature failure is handled.
8. Documentation and operator instructions updated; checkpoint contains exact next action.

Do not chase a coverage percentage instead of checking invariants. Target high-value boundary, failure and race tests. Add regression tests for meaningful defects; avoid tests that only duplicate a constant or mock the implementation into success.

## Change control

New owner requirements receive IDs, impact analysis, acceptance scenarios and dependencies. Update scope/decision register, contracts and affected gates before execution. Do not silently remove difficult stories, downgrade checks or mark a deferred feature complete. Small routine implementation choices are autonomous; material product, financial, privacy or deployment changes require actual direction.
