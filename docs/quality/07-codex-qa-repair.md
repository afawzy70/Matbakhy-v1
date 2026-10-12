# Repairs for the independent Codex QA assessment (2026-10-12)

The implementation agent wrote this repair record; it is **self-review**, not independent verification. The findings below are **FIXED_PENDING_INDEPENDENT_RETEST**: Codex has not retested them. Nothing is closed by independent QA, and no release gate is approved.

## Retest revisions (updated 2026-10-12, 20:50 Asia/Kuwait)

Two immutable revisions are offered. Neither has been retested by Codex; nothing here is independent verification.

| | **A. Original defects fixed: `cdc592a2492a691de714a3d0bf23b31f25599480`** | **B. Newer candidate: `4555f599411da9683453bee209559c8d1f11cded`** (also the cross-role repair candidate, [08](08-codex-qa-repair-cross-role.md)) |
|---|---|---|
| Purpose | Retest MBQA-001, MBQA-002, FG-001 and Q1 exactly as repaired | Retest the same, plus everything changed since A |
| Evidence | Six local checks PASS (`verification/runs/20261012-MB-032/`); Product CI run 243 success; native run 31 success on Android and iOS | Six local checks PASS: `verification/runs/20261012-QA-REPAIR-2-CHECKS/`; native run 35 at this SHA PASS on Android and iOS; native run 34 at `f5d2ba0` (same mobile behaviour except the Q1 refresh) PASS on Android and iOS |
| Customer web and operations web code | As repaired | A plus MBQA-003/004/005, OBS-001 and the Q1 limit refresh ([08](08-codex-qa-repair-cross-role.md)) |
| Native app | Basket editing, recovery, Q1 limits | A plus: one recovery check at a time ("Check and retry"), order deep links, sign-in prompt on a linked order, refresh on return to the foreground (MB-033) |
| Native checks | MB-031/MB-032 | A plus quantity limit, lowered limit, delivery checkout and deep links on Android and iOS |
| Backend | As repaired | A plus: `APP_ENV=PREVIEW` (inactive locally), denial audit on long paths fixed, staff view of order contact audited (MB-039) |
| Native fixtures | — | Kitchen one delivers to Test zone A; its slot has 15 portions |

**Recommendation:** use **B** for a combined retest. Use **A** only to confirm the original findings in isolation.

**Fixes included in `cdc592a`:**

| Finding | Original reproduction (QA report on `eb1a671`) | Expected | Fix commits |
|---|---|---|---|
| MBQA-002 | Meal with two open slots (3 portions each); exhaust S1 (0 left) while S2 keeps 3; select S1; Add to cart | No basket write for the exhausted slot; S2 adds at most 3; a fully sold-out meal is unpurchasable | `e2c6ebc` (web and native) |
| MBQA-001 | Fresh browser, Arabic browse with bilingual fixtures; click English; inspect cards and the meal request | Requests carry the chosen language, and titles, descriptions and portions switch, in both directions; a stale answer is never shown | `e2c6ebc` (customer web); `a817164` (operations web: kitchen and staff screens had the same root cause) |
| FG-001 | Add A1/S1 × 2 and open the cart | Per-line quantity and removal, an estimated subtotal separate from the quote, kitchen shown; the four replacement paths keep or replace with exact estimates (240.50 / 80.25 / 240.50 / 95.50) | `f600577` (web); `b68eaaa` (native) |
| Q1 (related) | Quantity 11–20 selectable while the policy allows 10 | Quantity capped at min(server-published limit, slot portions); a basket line above a lowered limit is explained and gets no quote until reduced | `4eaee3a` (API-001), `cdc592a` (web and native clients) |

**What changed for the retest:**
- IQA-09's quantity-cap expectation now follows the server's published limit. With the synthetic policy (10), quantity 99 clamps to min(10, slot portions), no longer to min(20, portions).
- The fixtures need the active commerce policy (the dev profile provides version 1, limit 10).

**Environment and data:**
- Unchanged from [06-codex-qa-handoff.md](06-codex-qa-handoff.md): isolated compose project, synthetic users from the realm, dev profile policy and zones, your own synthetic catalogue SQL.
- New endpoint the clients call: `GET /api/v1/configuration/public`.

**Correction to flag in the external QA report (not edited by the implementation agent):**
- The QA report's "Comparison after independent execution" used the obsolete, authentication-blocked Kane report.
- The successful Kane execution report is `verification/tool-pilots/kane-cli/2026-10-12-resume-execution.md` at `d0ba642`; see "Reporting correction" below.
- The QA branch is Codex's to update.

## Source

| Item | Value |
|---|---|
| QA branch | `qa/independent-browse-basket-eb1a671-20261012` (read only; not merged or cherry-picked) |
| QA evidence commit | `a7ed1f3061ffef8d729076405ddef9bd1f5933a9` |
| Report | `qa/independent-eb1a671/report.md` on that branch |
| Application tested by QA | `eb1a6717b3304d3a02e6a46d06c77ed5d43ac144` |
| Repairs made on | `implementation/mvp` from `5bd7681`; newer work on the branch kept |
| First repair candidate (superseded) | `c35ad2d0be06f045059e19fbcc1c6e25209cb645`: six-check evidence PASS; Product CI runs 220 and 221 success |
| **Current retest revisions** | **A `cdc592a…`** and **B `4555f599411da9683453bee209559c8d1f11cded`**: see "Retest revisions" above |

The QA failures and evidence on the QA branch are unchanged. The red-phase logs below were produced on the implementation branch.

## Findings and status

| Finding | QA severity | Reproduced here | Fix | Status |
|---|---|---|---|---|
| **MBQA-002**: an exhausted selected slot can be added while another slot of the same meal has stock | Major | Yes: web test (fake API at the fetch boundary, QA fixture shape) and native test, both red against unchanged code | `e2c6ebc`: Add requires the selected slot's own portions (`quantity ≤ availablePortions`, at least 1); a sold-out slot shows "This time is sold out. Choose another time."; web and native | FIXED_PENDING_INDEPENDENT_RETEST |
| **MBQA-001**: a language switch sends the previous language to catalogue requests | Moderate | Yes: three web tests red (browse, meal detail, kitchen page; both directions; stale slow answer) | `e2c6ebc`: the request language is set on the client in the same click that changes the language, not read from the document language (which an effect updates after children have loaded) | FIXED_PENDING_INDEPENDENT_RETEST |
| **FG-001**: CW05 basket lacks quantity editing, line removal, estimated subtotal and the kitchen | Major (acceptance gap) | Yes: four web tests red (missing controls) | `f600577`: per-line quantity and removal, estimated subtotal, kitchen name; edits discard any quote shown; a pending order locks the basket | FIXED_PENDING_INDEPENDENT_RETEST (web). Native cart: `b68eaaa` under MB-032 (unit/UI tests; emulator/simulator run pending) |
| Meal page kept the previous meal's slot and quantity on direct meal-to-meal navigation (found by the FG-001 test, not by QA) | Low | Yes: red log kept | `f600577`: the meal page is keyed by meal id | FIXED (self-review) |

### MBQA-002 details

- **Tests:** web `QA repair MBQA-002` (two tests) and native `QA repair MBQA-002 on native`.
- **Coverage:**
  - mixed-stock slots (0 and 3 portions): selecting the exhausted slot leaves Add disabled, and clicking it writes nothing;
  - the slot with stock caps the quantity at 3 and adds exactly `[meal, 3]` in that slot;
  - switching back to the exhausted slot cannot rewrite the basket;
  - an entirely sold-out meal stays unpurchasable.
- **Server enforcement is unchanged:** quote and placement still refuse stock the slot lacks (`QuoteIntegrationTest`, `OrderPlacementIntegrationTest`).

### MBQA-001 details

- **Tests:** web `QA repair MBQA-001` (three tests).
- **Bilingual fixture:** the fake API answers in the request's `Accept-Language`, as the real API does.
- **Assertions:** both the request header and the translated content (title, description, portion text, kitchen name), Arabic → English → Arabic, on browse, meal detail and kitchen page.
- **Stale answers:** an English answer held back until after the customer switched back to Arabic is never shown, and the page stays Arabic.
- **Native:** not affected. The native client is created per language and its loads are keyed by language.
- **Related, fixed after the candidate:** the operations web (`apps/operations-web/src/api.ts`) used the same document-language pattern.
  - A new test reproduced it: after switching back to Arabic, the open Orders section reloaded in the same render and sent `en` (`RED-ops-web-language.log`).
  - Fixed after `c35ad2d` with the same approach: the language is set on the client in the click.
  - This fix is not part of the Codex candidate.

### FG-001 details

- **Tests:** web `QA repair FG-001` (four tests), plus the keyboard browser journey, which now edits a quantity with the keyboard and checks the estimate is shown.
- **Arithmetic (integer piastres):**

  | Step | Lines | Estimate |
  |---|---|---|
  | Start | 2 × A1 + 1 × A2 | 336.00 |
  | A2 to 2 | 2 × A1 + 2 × A2 | 431.50 |
  | A1 to 1 | 1 × A1 + 2 × A2 | 311.25 |
  | Remove A1 | 2 × A2 | 191.00 |
  | Remove A2 | none | empty state; storage cleared |

- **Limits:** quantity stays between 1 and min(20, the portions available when added). The slot in the test had 3 portions, so the estimate stops at 360.75.
- **All four replacement paths** (exact saved kitchen, slot and lines, and the estimate):

  | Path | Choice | Result | Estimate |
  |---|---|---|---|
  | Another kitchen | cancel | A1 × 2, slot S1 | 240.50 |
  | Another kitchen | confirm | B1 × 1, slot BS1 | 80.25 |
  | Same kitchen, another slot | cancel | A1 × 2, slot S1 | 240.50 |
  | Same kitchen, another slot | confirm | A2 × 1, slot S2 | 95.50 |

- **Estimate versus quote:**
  - The basket shows "Estimated subtotal" and a note that the final total at the current price comes from "Calculate total". The server quote stays a separate figure.
  - An edit after a quote removes that quote and its Place order button, and the next quote is sent with the new quantities.
  - Unit price and portion limit are stored with the line only for the estimate and the controls. They are never sent; the quote request carries meal ids and quantities only.
- **Older baskets:** a basket saved before this change has no price snapshot, so it shows "Not available; calculate the total" instead of a wrong figure.
- **Native (MB-032, `b68eaaa`):** the same basket editing on the native cart: quantity, removal, kitchen name, estimate and note; an edit discards the quote; an unconfirmed order locks the basket. Covered by three native UI tests, which were red first. Discarding the quote after an edit is tested on the web only; the native screen uses the same keyed remount, but no native test covers it.
- **MB-027:** marked DONE on 2026-10-11 with "cart" among its steps. CW05 editing was incomplete until `f600577`. Its acceptance (real browser orders) was and is met. The new basket scenario is recorded as TC-149.

## Commands and results (local, at the candidate SHA)

| Command | Result |
|---|---|
| Web tests: `pnpm exec vitest run` (apps/customer-web) | 24 passed (9 new) |
| Browser journeys `scripts/dev.sh e2e` at `f600577` (throwaway stack, Chromium) | 5 passed, including the keyboard journey editing a basket quantity |
| Native tests: `pnpm exec jest` (apps/customer-mobile) | 28 passed (4 new: MBQA-002 on native, three basket-editing tests) |
| `tsc --noEmit` (web, mobile, operations web) and `eslint` (all three apps) | clean |
| Red-phase logs | `verification/runs/20261012-QA-REPAIR/RED-*.log` |
| Six-check evidence run (backend verify, clean-clone workspace, local stack, browser journeys, secret scan, guardrails) at `c35ad2d` | all six PASS: `verification/runs/20261012-QA-REPAIR/evidence.json`; scenarios TC-149, TC-150 and TC-151 PASS (self-review) |

## Requirement questions (proposals only; not resolved)

These are proposals for the owner. Behaviour was not changed for them.

- **RQ-001: D12 optional discovery description vs BR-011 required description at publication.**
  - *Proposal:* read the two as applying at different boundaries.
    - BR-011 keeps the Arabic description mandatory for publication; the database constraint stays.
    - D12's "omit cleanly when absent" covers a null in the discovery response, for example a future optional English text or a moderation-redacted field.
  - No change to publication rules. Acceptance would stay as tested today: contract-level null omission.
- **RQ-002: the replacement prompt — modal or inline?**
  - *Proposal:* make it a modal `alertdialog`.
    - Tab and Shift+Tab stay inside Keep/Replace while it is open.
    - Escape keeps the basket; focus returns to Add.
    - The rest of the page is inert while it is open.
  - This matches the WAI-ARIA alertdialog pattern and the native `Alert`.
  - Acceptance: browser keyboard test for containment, plus a screen-reader pass (part of BLK-003).
- **RQ-003: unavailable cards in browse.**
  - *Proposal:* keep showing SOLD_OUT/UNAVAILABLE meals honestly, labelled and not purchasable, after the available ones. Excluding them would make shared links and favorites appear broken.
  - The alternative is to exclude them from browse, as the journey text says, and keep them only in favorites and history.
  - Either way is a small change. Owner choice.
- **Q1: per-line quantity limit.**
  - The mechanism is implemented at A (option A): clients follow the server's published limit (API-001).
  - The value is still open (O04). The synthetic policy uses 10, which is a fixture, not a commercial decision. See `docs/product/q1-quantity-limit-decision.md`.

## Reporting correction: the Kane comparison

QA section "Comparison after independent execution" compared against an obsolete Kane report. That was `verification/tool-pilots/kane-cli/2026-10-12-pilot.md` on `claude/kane-cli-qa-pilot-resume-y7scfp`, where every case was BLOCKED by authentication.

The successful Kane execution report is `verification/tool-pilots/kane-cli/2026-10-12-resume-execution.md` at `d0ba642` (branch `claude/kane-cli-qa-pilot-vx8evl`). It tested application `1ee2eae`, and its results are:
- TC-K1 browse: PASS, except the D12 "absent" branch, which was NOT_RUN because the data could not reach it (F1, the same point as RQ-001);
- TC-K2 add to basket: PASS;
- TC-K3 replacement, different kitchen keep/replace: PASS;
- TC-K3 variant, same kitchen keep: PASS;
- no product defects reported.

**One discrepancy for the reviewer.** Kane TC-K2 reports a "cart total ١٢٠٫٠٠ ج.م." at `1ee2eae`, but that revision had no basket subtotal (FG-001). The figure most likely came from another element, such as the meal page's "Add to cart · price" button. This was not verified. It does not change any finding.

This is a reporting correction only. It is separate from the product repairs, and no Kane credits were used.

## For the Codex retest

- **Revision:** check out **B `4555f599411da9683453bee209559c8d1f11cded`** (or A `cdc592a…` for the original findings alone), not the branch head.
- **Startup:** as in `docs/quality/06-codex-qa-handoff.md`.
  - Isolated compose project and the synthetic users from the realm.
  - Dev-profile policy (version 1, limit 10) and zones.
  - Your own synthetic catalogue SQL.
  - The clients call `GET /api/v1/configuration/public` for limits.
- **Retest:**
  1. IQA-09 mixed-stock selection. The quantity cap is now min(published limit 10, slot portions).
  2. IQA-01 language switch, both directions, on browse, meal and kitchen.
  3. IQA-04 to IQA-08, including the basket totals.
  4. The adjacent add and checkout flow.
- **Remaining platform gaps (not covered by either revision):**
  - physical Android and iOS devices (only emulator and simulator runs exist);
  - screen-reader testing (BLK-003; MB-029/TC-047 stay blocked);
  - push notifications (no provider; O07);
  - RQ-001/002/003 and the O04 limit value.
