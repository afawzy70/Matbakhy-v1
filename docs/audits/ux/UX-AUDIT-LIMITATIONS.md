# UX audit limitations and execution record

Audit date: 2026-10-13. Application baseline: **cc52fa4224e3163677a1215dc8f63ede2a9dbe4c**, `implementation/mvp`. Audit branch: `audit/ux-independent-implementation-2026-10-13`. The latest owner-policy comparison is main **047f42eebfcfc904622a123bbb8bdf9b2a16cda8**. All audit edits are under `docs/audits/ux/`; no application implementation, production data, commercial feature flag or delivery checkpoint was changed.

## Scope and confidence

Main contains a planning pack and newer commercial decisions; the active implementation exists on a separate branch/draft PR. Initial main-only inspection was corrected before final recommendations. Implementation and owner-policy snapshots are deliberately distinguished. A scope/contract gap is not proof that a production customer was charged incorrectly. No deployed production environment or real customer account was used.

- VERIFIED: reproduced by an executed audit probe, with its fixture/component/runtime boundary stated.
- CODE-SUPPORTED: supported by inspected implementation/contracts. No visual, device or participant result implied.
- HEURISTIC: a specific expert hypothesis requiring validation; secondary suggestions in the specialist reports are not additional counted defects.
- UNVERIFIED: inaccessible or unexecuted behavior. Never converted into a pass.

The 21 master findings contain 7 VERIFIED and 14 CODE-SUPPORTED classifications. For findings spanning several clients, only the explicitly exercised part is VERIFIED. Twenty-one journey rows were evaluated (12 customer, 9 kitchen, splitting receiving and processing orders); this does not mean all 21 were tested end to end. Proposed journey steps are not measured task times, clicks or participant outcomes.

No interviews, surveys, analytics, conversion measurements or usability sessions were conducted. Frequency, user impact and business value used for priority are reasoned judgments, not statistical estimates. No conversion uplift is predicted. Specialist agents are independent AI work sessions, not separate human reviewers.

## Environment and actual checks

Existing documented local procedures were used: `scripts/dev.sh up`, the repository Compose stack, pnpm workspace commands, and operations-web Playwright suite. Runtime was Chromium 141.0.7390.37 through pinned Playwright 1.56.1; Node 24.21.0, pnpm 10.34.6, full Temurin JDK 21 and Docker Compose. Customer web ran on localhost:5173, operations web on :5174 and API on :8080. PostgreSQL, development identity, object storage and mail were local synthetic services. No public deployment occurred.

Cloud-only setup obstacles: initial Node/pnpm mismatch; Maven external-download proxy/trust configuration; missing Java compiler; an ignored build JAR unreadable by the container user; internal object-storage requests sent through the session proxy. These were resolved through scratch tool installations/settings, permissions on ignored build output and a scratch Compose proxy-bypass override, without application changes. The first real browser-suite attempt failed at photo upload and dependent fixture setup. After fixing local internal networking and resetting only this audit's synthetic volumes, the complete suite passed. This is an environment recovery, not an application fix or a suppressed test failure. The Compose API container health probe still reported unhealthy: its internal wget received HTTP 403, while an external local curl to /actuator/health returned HTTP 200 and the real API suite passed. The exact health-probe/proxy cause was not resolved; do not infer that the complete Compose health gate passed. Earlier documentation validation ran before all report files existed and failed on a then-missing report link; final validation is recorded separately.

| Executed check | Result and boundary | Evidence |
|---|---|---|
| `pnpm --filter @matbakhy/customer-web test` | 41 passed; component tests | [log](evidence/customer-components.txt) |
| `pnpm --filter @matbakhy/operations-web test` | 18 passed; component tests | [log](evidence/operations-components.txt) |
| `pnpm exec playwright test` from operations-web, local E2E variables | 5 passed, 1.7 minutes; real local API, synthetic users/orders | [log](evidence/real-api-browser-suite.txt) |
| Dedicated consent component reproducer | Passing diagnostic assertion demonstrates unwanted behavior; not a successful product acceptance test | [instructions](evidence/CONSENT-PROBE-README.md), [output](evidence/consent-component-reproducer.txt) |
| Targeted browser probes against actual React UI and intercepted synthetic API | Discovery, keyboard, numeral, onboarding and unknown-route probes executed | [script](evidence/browser-probes.cjs), [results](evidence/browser-probes.json) |
| Current cook statement monetary reflow | AR320 and EN320/390 digits split; AR390 and both1440 intact for this fixture | [script](evidence/money-reflow-probe.cjs), [results](evidence/money-reflow-current.json) |
| Final blueprint, guardrails and audit integrity | Results recorded after full report generation; documentation checks are not product certification | [validation record](evidence/report-validation.txt) |

The five real-API tests exercised cook pickup/delivery fulfillment, customer pickup/delivery ordering and subsequent cook actions, customer responsive layouts, keyboard navigation/focus restoration, and allowed operations sections in both directions. Customer layout loop covers home/kitchen/meal at 320, 360, 390, 420, 768, 1440 CSS pixels in Arabic and English. Operations loop covers allowed cook/staff sections at 360, 390, 768, 1440. Automated axe scans at 390/1440 reject serious/critical WCAG-tagged results; that threshold is narrower than proving zero violations or WCAG conformance. Additional homepage fixture scans recorded no axe violations at 390/1440. The 320px monetary fixture catches a content-specific problem beyond existing layout assertions.

Browser routes exercised through the real suite include discovery, meal, kitchen, checkout, orders/list/detail, account and support. Cook/staff panels were visited by permission-filtered navigation, not invented URL routes. The targeted no-kitchen onboarding probe used a synthetic no-application response. Visual screenshots below are fixture-backed; real-suite screenshots containing synthetic contact fields are deliberately omitted from this public repository.

## Reproduction and captured evidence

Prepare the documented local stack and development users, install the pinned workspace dependencies and Chromium, start both Vite apps with the local API base URL. Supply the repository's documented `E2E_API_URL`, `E2E_OPS_URL`, `E2E_CUSTOMER_URL`, `E2E_USER_PASSWORD` and `E2E_PSQL` variables from local development configuration; never print or commit their sensitive values. Run the existing suite from `apps/operations-web`. Do not point the suite at production: it creates synthetic records and uses test SQL.

From repository root, with Vite available, run:

```sh
node docs/audits/ux/evidence/browser-probes.cjs
node docs/audits/ux/evidence/money-reflow-probe.cjs
./apps/customer-web/node_modules/.bin/vitest run --config docs/audits/ux/evidence/consent-vitest.config.mjs
python3 scripts/validate_blueprint.py
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

Probe files show all synthetic response boundaries. They overwrite their own evidence files when rerun; review and commit a new dated evidence record deliberately. Exact defect steps and post-fix assertions are in [regression cases](UX-REGRESSION-TEST-CASES.md).

| Capture | What it establishes |
|---|---|
| [home AR390](evidence/home-ar-390.png), [AR1440](evidence/home-ar-1440.png), [meal AR1440](evidence/meal-ar-1440.png) | Current rendered fixture UI at stated widths, not participant usability |
| [Arabic digits rejected](evidence/arabic-digits-rejected.png) | Arabic digits rejected by the price parser in English UI; same parser used in Arabic; not a native OTP reproduction |
| [onboarding](evidence/cook-onboarding-no-action.png) | No-application state lacks an actionable main control |
| [unknown route](evidence/unknown-route.png) | Empty main for unmatched customer hash route |
| [cook EN320](evidence/money-cook-320-en.png), [EN390](evidence/money-cook-390-en.png), [AR320](evidence/money-cook-320-ar.png) | Broken monetary digits for a long synthetic statement fixture |
| [cook AR390](evidence/money-cook-390-ar.png), [AR1440](evidence/money-cook-1440-ar.png), [EN1440](evidence/money-cook-1440-en.png) | Comparison captures for the same fixture |

## Unavailable or incomplete validation

Android/iOS apps were source-inspected, without emulator, physical-device, native keyboard/safe-area or native screen-reader execution. VoiceOver, TalkBack, desktop screen readers, browser/OS matrix, large native text, exhaustive 200%/400% zoom, reduced motion and text-spacing overrides were not tested. Accessibility observations and axe/keyboard passes are bounded evidence, not a WCAG 2.2 AA certification.

No production latency, geolocation-permission matrix, real SMS/email delivery, live payment/deposit, catering flow, growth monetization or real earnings was tested. Some supported error states are inspected rather than triggered. Proposed regression cases remain NOT_RUN after repair; no finding is FIXED or VERIFIED as a repair in this audit. Serving-person filters and bespoke catering are deliberately deferred, not defects to implement implicitly.

Historical QA reports were read, not treated as current reruns. UX-021 preserves the existing MBQA-006 alias from [historical QA](https://github.com/afawzy70/Matbakhy-v1/blob/c17785a4e8a8646fac01726b564e0628d687cb1e/verification/runs/20261013-IQA-d877a15/defects.md). Current cook monetary cases were rerun; additional historical finance variants remain unverified in this session. Prior repairs MBQA-001–005, FG-001 and OBS-001 are not duplicated without new evidence.

## Agent registration boundary

Official Agency Agents source is pinned to `f99f6aa910a442b0197b768ce0ea7751e35e2060`. Only ux-researcher and ux-architect were installed. Original identities/instruction bodies are preserved with appended Matbakhy guidance. **Registration VERIFIED** in the active Codex CLI configuration through strict app-server initialization and effective config read, not file existence alone. Both audit workers were **ACTIVE** via complete explicit instructions, the supported fallback for this hosted collaboration API, which exposes no custom TOML role selector. Native hosted role dispatch/hot reload remains unavailable/unverified. See [full provenance and reusable instructions](agents/README.md). Neither worker is represented as an always-running service.

## Publication-time concurrent changes

A final fetch found implementation/mvp at **d35cc133f7d163801a263c8b40c80edd4823f289**, four commits after this audit's pinned baseline. The inspected diff adds native permission/brand/account placement and iOS test-harness changes plus planning/evidence updates. It does not alter the web checkout, onboarding, discovery or monetary-layout code used by the audit reproductions. These newer changes were not executed by this audit and are not covered by its pass claims. No application changes were merged into the audit branch. Claude must reconcile current source and planning status before implementing; findings describe cc52fa4, not an indefinitely current release certification.

Pinned owner-authority sources: [docs/strategy/mvp-direct-deposit-payment.md](https://github.com/afawzy70/Matbakhy-v1/blob/047f42eebfcfc904622a123bbb8bdf9b2a16cda8/docs/strategy/mvp-direct-deposit-payment.md), [docs/delivery/07-owner-decisions-implementation-sequence.md](https://github.com/afawzy70/Matbakhy-v1/blob/047f42eebfcfc904622a123bbb8bdf9b2a16cda8/docs/delivery/07-owner-decisions-implementation-sequence.md).
