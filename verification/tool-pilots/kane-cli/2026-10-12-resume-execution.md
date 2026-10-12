# Kane CLI QA pilot — execution report (resume), 2026-10-12

Bounded tool evaluation. Not release approval, not story evidence, not an independent review. Supersedes the
"NOT_RUN" table in `2026-10-12-pilot.md` (kept unchanged as history).

## Result summary

| Case | Status | Kane run (project `01M4QQF5GQVYT89T8BA5QYHYTA`, test case) | Credits |
|---|---|---|---|
| TC-K1 Browse meals | **PASS** for assertions 1, 2, 3, 4 and the D12 *present* branch. D12 *absent* branch: **NOT_RUN (unreachable by data, see Finding F1)** | `01M4QSA6918P35PJ6X6TKVWJCM` (attempt 2); attempt 1 `01M4QS562QK38SB24Y38BP6C6M` = FAILED, tool/objective fault, no product signal | 19.4 (+36.1 attempt 1) |
| TC-K2 Add to basket | **PASS** | `01M4QSC4N96SK8A3WWVVCVXRTH` | 37.7 |
| TC-K3 a+b Replace basket (different kitchen: keep, then replace) | **PASS** | `01M4QSGMDW122SA616CGW1H64Z` | 67.1 |
| TC-K3 variant (same kitchen, different slot) | **PASS** | `01M4QSQG0X6NYSNZDTNBM4WSHJ` | 38.2 |

Credits: 1200.0 at start, 978.85 at end (≈221 used, free allowance; nothing purchased, no overage enabled).
Product defects found: **none**. One fixture/schema observation (F1, not a defect by itself).
Screenshots per step and Kane's `result.yaml` are in `evidence/<case>-screenshots/`; raw `--agent` NDJSON in `evidence/*.ndjson`
(contain account-scoped share links; run links above are the plain dashboard paths, sign-in required).

## Authentication and access

- Kane CLI `0.8.22`, Node v22 (global tool). Explicit login `kane-cli login --username "$LT_USERNAME" --access-key "$LT_ACCESS_KEY" --profile qa-pilot-resume` succeeded
  (basic auth, prod). `whoami`: Authenticated, token valid. `balance`: 1200 credits. Existing profiles untouched; credentials never printed
  or written (tracing off, repository and evidence scanned for both values: no match).
- OAuth is not used (headless container). Chrome: preinstalled Chromium via `KANE_CLI_CHROME_PATH`.

## Tested application

- Repository `afawzy70/Matbakhy-v1`, branch `implementation/mvp`, commit **`1ee2eaef8e31e8e821b5a050eba071b32a96feca`** (checked out in a detached worktree outside this repo; no product code changed).
  The earlier pilot named `64161d5`; the branch had advanced.
- Stack: `scripts/dev.sh up` (via `scripts/stack-up.sh`), isolated compose project `matbakhy-kane`, own PostgreSQL volume, alternate ports (API 18080), random local secrets.
  Customer web: `vite` on `http://localhost:5173` against that API. Node **24.21.0** (from `.node-version`), JDK 21.
- Environment workarounds (not product issues): container `wget` cannot use the egress proxy, so a curl-backed `wget` shim was on PATH for `mvnw`; Docker Hub anonymous pulls returned `429` and were retried until the images were cached.

## Fixture / direct database setup (separate from tested behaviour)

`scripts/seed.sql`, loaded with `psql` into the throwaway database. This is **setup, not tested behaviour**: users `qa-seed`, two approved ACTIVE kitchens in `test-zone-a`
(A, B), meals A1 120 EGP, A2 85 EGP, B1 95 EGP (all PUBLISHED, with descriptions), X1 PAUSED, OPEN slots tomorrow Cairo 13:00 and 18:00 (Kitchen A) and 13:00 (Kitchen B).
No browser or API step created this data. Only synthetic data was uploaded to TestMu; no credentials or personal data appear in objectives or evidence.
Each Kane run starts in a fresh browser session with an empty basket (TC-K3 runs assert the empty basket first).

## Expected results (derived from requirements, not from implementation text)

- D11 (ACCEPTED): no one-person/two-people portion filter control; no sort mislabelled as a filter.
- D12 (ACCEPTED): description shown when the meal has one, omitted cleanly when absent, never invented.
- BR-016/BR-017: only purchasable meals of approved active kitchens are discoverable (X1 paused ⇒ absent).
- BR-018 / TC-147: one kitchen and one slot per basket; changing either asks first; cancel keeps the basket; confirm replaces it entirely.
- Integer money (BR price rules): totals shown in whole EGP with no hidden fractions beyond the locale display (120.00 ج.م.).

## Results in detail

**TC-K1 (attempt 2, PASS).** Kane assertions all returned true: A1/A2 listed under QA Kitchen A, B1 under QA Kitchen B, each card with a description paragraph and an EGP price;
paused meal not listed; no portion-size filter visible. Confirmed independently of Kane against the API before the run: `GET /api/v1/meals?zoneId=…` returned exactly A1/A2/B1 (X1 absent).
Attempt 1 (FAILED, 36.1 credits) is preserved in `evidence/tc-k1-attempt1-agent-misread.ndjson`: my objective said "choose the zone if needed"; the page already listed meals and the agent looped on a
non-existent zone control. Kane's own assessment: agent fault, no failed UI action, no product error. Classified as **tool/objective failure, not a product defect**; the objective was rewritten and re-run.
Note: the "description present" assertion checks that a `<p>` exists in the card (Kane generated that check), plus visual confirmation; it does not compare exact text.

**TC-K2 (PASS).** Meal page price ١٢٠٫٠٠ ج.م.; earlier slot, quantity 1, "added" confirmation shown; cart has exactly A1 ×1 / QA Kitchen A; cart total ١٢٠٫٠٠ ج.م. = meal price.
(The checkout page then showed the cart; the slot match is asserted via the selected earlier slot only, not read back on the cart line by name.)

**TC-K3 a+b (PASS, 7 of 7 Kane checks: empty-basket precondition, dialog "تستبدل السلة؟" shown, basket unchanged before the choice, "unchanged" message after keep, basket still A1 only, then after replace basket = B1 ×1 from Kitchen B and A1 absent).**
`cart_excludes_A1 = false` is the *expected* value (A1 must be absent); status PASS.

**TC-K3 variant (PASS, 4 of 4 checks).** Same kitchen, other slot: A1 at 13:00 in basket, add A2 at 18:00 ⇒ dialog appears before any change; keep ⇒ basket still A1 ×1 at 13:00, A2 absent.

## Findings

- **F1 (fixture limitation / possible D12 gap, severity Low, not confirmed as a defect).** The schema constraint `meal_publishable_ck` (`V7__meals.sql`) requires `description_ar IS NOT NULL` for every PUBLISHED or PAUSED meal, so a visible meal
  with no description cannot exist. The D12 "omit cleanly when absent" branch therefore could not be exercised with real data and is reported NOT_RUN, not PASS. (`Browse.tsx` skips an empty description, but that was not run.)
  Needs the product owner: either D12's "absent" case only concerns a locale fallback, or the cook flow should allow publishing without a description. Tested commit `1ee2eae`.
- No other discrepancies observed. Nothing here approves any release gate.

## Limits of this pilot

Single run per case (no repeat to measure stability); Arabic RTL default locale only; desktop Chrome headless only; Kane assertions are model-generated, so screenshots are kept for human review.
Slot identity on the cart line was not read back by label in TC-K2. English UI, mobile viewports and the same-kitchen-variant "replace" path were not exercised.

## Reproduce

1. `scripts/stack-up.sh <app-worktree> <state-dir>` (Node from `.node-version` on PATH; JDK 21; Docker).
2. `psql` the throwaway database with `scripts/seed.sql`.
3. `pnpm exec vite --port 5173` in `apps/customer-web` with `VITE_API_BASE_URL=http://localhost:18080`.
4. `kane-cli login --username "$LT_USERNAME" --access-key "$LT_ACCESS_KEY" --profile <new>`; then `kane-cli run "<objective>" --url http://localhost:5173/ --headless --agent` with the objectives recorded in `evidence/*.ndjson` (`bifurcation` event).
