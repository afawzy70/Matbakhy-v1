# Developer handover content (Stage B)

A new developer must be able to understand the product, navigate the code, run it, test it, change it safely, open a pull request and see the change reach a running environment, without asking anyone. Adapt this checklist to the detected stack; omit what does not exist.

## Page set (typical)
1. **Start**: project overview, learning roadmap with a day-by-day path, repository navigation, glossary of domain and technical terms.
2. **Business**: product vision, users and actors, business model, permissions matrix, core lifecycles and state machines, pricing/payments rules if any, business-rule catalogue that links to the canonical source instead of copying it.
3. **User journeys**: one page per surface (web, mobile, back office) plus an end-to-end journey: role, preconditions, steps, screens, API calls, data touched, errors, tests, known limitations.
4. **Architecture**: system overview, backend, frontend, mobile, database, identity and security, integrations and events, diagrams (Mermaid) that name real components.
5. **Codebase guide**: repository map, module guide (responsibility, public API, dependencies, tests), API contracts, key implementations (the three or four flows that matter most), shared libraries.
6. **Workflow**: prerequisites with exact versions, local environment, run and debug, test execution (every suite and how to prove tests really ran), Git and pull-request workflow, CI/CD overview, preview/staging environment, troubleshooting, documentation maintenance policy.
7. **Onboarding labs**: five short labs with objectives, safe tasks, expected results, checkpoints and completion criteria; a final assessment with evidence for each criterion.
8. **Reference**: architecture decisions, implementation status, requirements traceability (requirement to story to scenario to code to test), FAQ, inventory and gaps, documentation map.

## Rules
- Every page starts with: title, owner role, status label (implemented / partially implemented / planned / disabled / deprecated / not verified / reference) and a one-line meaning. Label planned or partial features honestly; backlog status is a hint, code is proof.
- Prefer linking to canonical documents over copying (copies drift). Summarise only what a developer needs, then link.
- Cite code by path (and line only when stable). Inline-code paths must exist; hypothetical paths go in fenced blocks or angle placeholders.
- Pricing, money, security and authorization rules: document the implemented behaviour from code and tests, and separate it from requirements that are not implemented.
- Record documentation gaps and suspected source errors in a "Known limitations and documentation gaps" section; never fix the source silently while translating.
- Keep a maintenance policy: documentation updates ship in the same pull request as the change; add a CI check for references (links, anchors, source paths, identifiers).

## Done when
A reviewer who knows nothing about the project can answer: what does it do, who uses it, where is X in the code, how do I run and test it, how does my change reach production or preview, and where is the proof.
