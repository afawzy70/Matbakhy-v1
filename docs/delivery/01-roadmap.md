# Delivery roadmap and checkpoints

The [47-story backlog](../../planning/backlog.json) is the executable queue. Each story includes concrete implementation steps, outputs, acceptance, dependencies, rule IDs and scenario IDs. Estimates below are planning ranges in focused engineer-days for one experienced engineer assisted by AI; they are not commitments or predicted autonomous-agent runtime. External reviews, credentials, recruitment and store review can extend elapsed time. Re-estimate after P01 and P04 using actual throughput.

| Phase | Stories | Deliverable / exit evidence | Planning range |
|---|---|---|---|
| P00 Scope and UX | MB-001–004 | Reconciled assumptions, traceability, editable brand direction and reviewable prototypes | 3–5 days |
| P01 Foundation | MB-005–008, MB-047 | Pinned workspace, backend/module/database baseline, contract tooling, local stack and real CI commands | 4–7 days |
| P02 Identity and supply onboarding | MB-009–012 | Sessions/PKCE contract, profile/phone/address, scoped grants/audit, cook review API | 5–8 days |
| P03 Catalogue and availability | MB-013–016 | Media, publishable meals, correct dated capacity, serviceable server-side discovery | 4–7 days |
| P04 Transaction slice | MB-017–021 | Quote → order → cook acceptance → fulfillment → cash/ledger → notification with failure/race proof | 7–12 days |
| P05 Operations | MB-022–026 | Cook workspace, platform approval/moderation, support, finance and configuration | 6–10 days |
| P06 Complete web | MB-027–030 | Customer purchase/account/trust flows, responsive Arabic/English and browser E2E | 5–9 days |
| P07 Native mobile | MB-031–035 | Native customer feature parity, lifecycle/push, Android and iOS install/test evidence | 8–14 days |
| P08 Hardening | MB-036–039 | Adversarial authorization/security, race/crash recovery, load, privacy/backup evidence | 5–9 days |
| P09 Staging/UAT | MB-040–043 | Integrated staging, operating rehearsal, owner UAT fixes and release candidate | 4–7 days |
| P10 Authorized release | MB-044–046 | Pilot deployment, separate store outcomes and observed operational closure | 3–5 days plus external lead time |

Indicative effort sum: 54–103 focused days, before conditional payment/courier/social tranches. AI may shorten some coding tasks; integration, native validation and business readiness still require evidence. Prefer a smaller truthful pilot to removing required checks to meet a date.

## Sequencing and review points

1. P00 establishes what is confirmed, proposed and open. Produce UX for review; pending visual approval blocks final affected visual implementation, not database/security work.
2. P01 must compile each target's minimal shell, including mobile, to reveal toolchain blockers early. No sprawling UI before a reliable foundation.
3. P04 is the main correctness checkpoint: one real order flow plus failure/race cases. It may be driven with API/integration tests before polished clients.
4. P05/P06 consume tested contracts. Do not build an operations mock while claiming real fulfillment exists.
5. P07 applies the same backend to Android/iOS. Native QA and signing remain separate evidence streams.
6. P08/P09 prove recoverability, permission boundaries and operator readiness. Re-run affected checks after UAT repairs.
7. P10 begins only for an exact authorized release candidate. Build completion, store submission and public availability are different states.

A phase may contain independent stories, but default execution is one story at a time. Do not use a numerical phase label to ignore explicit dependencies. The protocol allows independent progress while an external/design item is blocked; the phase cannot be called passed until all its required checks are satisfied.

## Gate failure recovery

Freeze dependent work; record failing scenario and reproduction; classify product defect/environment/decision; repair root cause; rerun the failed check and affected regression/invariant tests; update evidence; re-evaluate gate. Never fix a failing gate by excluding tests or marking a missing environment as success. After three materially different unsuccessful repairs, checkpoint the blocker and work on independent eligible stories.

## Initial launch decisions

Before live pilot resolve O01–O09 and determine O10's scope. Keep implementation moving with synthetic zones/rates/providers, but label them in UI/config and reject them in production. No city, real commission, tax treatment or delivery contract is supplied by this plan.
