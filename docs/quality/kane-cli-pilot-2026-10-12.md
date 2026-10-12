# Kane CLI QA pilot — 2026-10-12

This was a bounded tool evaluation, not a release approval. Kane CLI **did not execute any test**. No test result below comes from Kane or from any other tool.

## Overall result: BLOCKED

The environment's egress policy denies every TestMu AI / LambdaTest host. Kane's natural-language agent runs on those servers, so it cannot authenticate or run from this cloud session, even with valid credentials.

## What was tested

| Item | Value |
|---|---|
| Product branch | `implementation/mvp`, isolated read-only worktree (scratchpad, not the implementation session's checkout) |
| Tested commit | `64161d5b8da1004ec6d5a96e60a4edba0ca1f1d7` (MB-031: fresh login on every interactive sign-in) |
| Kane CLI | `@testmuai/kane-cli@0.8.22` from registry.npmjs.org, installed locally in the scratchpad (not global, not in the repo) |
| Node.js (session) | v22.22.0 |
| Chromium | Playwright Chromium 1194 at `/opt/pw-browsers` (headless possible via `kane-cli run --headless`, or `--cdp-endpoint` / `KANE_CLI_CHROME_PATH`) |

## Compatibility findings

| Check | Result | Evidence |
|---|---|---|
| Official instructions `https://www.testmuai.com/kane-cli/agents.md` | NOT READ | Proxy: `CONNECT www.testmuai.com:443 → 403 connect_rejected`. WebFetch: `ENOTFOUND`. Package README and `--help` were used instead. |
| Package install | PASS | `npm install @testmuai/kane-cli@0.8.22`, `kane-cli --version` → `0.8.22` |
| Node.js | OK | The CLI runs on Node 22. |
| Headless Chrome | Available, but not exercised by Kane | `run --headless` exists. Chromium is present. |
| Authentication | NOT DONE | `kane-cli balance` → `Not authenticated [prod]. Run "kane-cli login" first.` Options: `login --oauth` (needs a browser), or `login --username <user> --access-key <key>`. |
| Backend network | **BLOCKED** | `403 connect_rejected` (organization egress policy) for kaneai-api, auth, accounts, api, test-manager-api, downloads and evidence `.lambdatest.com`, and for www.testmuai.com |
| Matbakhy local stack (`scripts/dev.sh doctor`) | Not started | Docker daemon not running. The repo requires Node 24.21.0; the session has 22.22.0. Both can be fixed in-session, but there is no point until Kane can run. |

No credentials were requested, entered, printed or committed. Nothing was purchased and no overages were enabled.

## Owner actions needed to unblock

1. **Network:** in this cloud environment's settings (environment menu in the session title bar → Edit → Network access), add these under Allowed domains, keeping "Allow package managers" ticked: `*.lambdatest.com` (at least the kaneai-api, auth, accounts, api, test-manager-api, downloads and evidence hosts) and `www.testmuai.com`. See https://code.claude.com/docs/en/cloud-environments#network-access. Alternatively, run the pilot on your own machine.
2. **Account:** a TestMu AI (LambdaTest) account with KaneAI CLI access on the free tier. Check credits with `kane-cli balance` before running anything.
3. **Credential:** basic auth works headless in a cloud session. Put the username and access key into the environment's secrets (for example `LT_USERNAME` / `LT_ACCESS_KEY`), never into chat or the repo. The agent then passes them as `--username "$LT_USERNAME" --access-key "$LT_ACCESS_KEY"`. OAuth login needs a desktop browser, so it does not suit this session.

Kane runs headless Chromium locally and drives it from TestMu's cloud agent, so it can reach `http://localhost:5173` without a tunnel. Page content (synthetic data only) is sent to TestMu's servers.

## Requirements used

- **D11 (ACCEPTED):** portion-size filtering is deferred. The homepage must not show the one-person / two-people filter, and sorting must not stand in for it.
- **D12 (ACCEPTED):** a meal card shows the meal's own published description when one exists and omits it cleanly when absent. Descriptions are never invented.
- **BR-018 / spec step 4 / CW05:** a basket holds exactly one cook and one slot. Adding from a different cook or slot asks for confirmation before anything is cleared. "Keep" leaves the basket unchanged.
- **BR-019 / BR-020:** amounts are whole piastres shown as EGP. The cart quote is authoritative.

## Test cases (derived and ready, all NOT_RUN)

Precondition for all three: local throwaway stack at the tested commit (`scripts/dev.sh up`), synthetic data per `docs/quality/04-external-qa-handoff.md` §3: kitchen K1 (cook-a) with published meals M1 (has a description) and M2 (no description); a second approved kitchen K2 with meal M3; K1 slots S1 and S2 on different days. Customer web is at `http://localhost:5173`, in Arabic RTL by default, with a fresh browser profile.

### KQ-01: Browse meals (D11, D12): NOT_RUN

1. Open `/`.
2. Look at the discovery list and the filter controls.

Expected:
- Published meals from approved kitchens are listed, with their price in EGP.
- M1's card shows exactly M1's stored description.
- M2's card shows no description, no placeholder and no blank gap.
- No "one person / two people" filter control is present, and no sort control is labelled as a portion filter.
- The layout is right-to-left with no horizontal scroll at 1280 px.

### KQ-02: Add to basket: NOT_RUN

1. Open M1, choose slot S1, quantity 1, and add it to the basket.
2. Open `/cart`.

Expected:
- The cart shows one line: M1 × 1, kitchen K1, slot S1.
- The cart count shows 1.
- The subtotal equals M1's price, in EGP with no fractional-piastre artefacts.
- No confirmation dialog appears.

### KQ-03: Basket replacement (BR-018): NOT_RUN

1. Start with the basket from KQ-02.
2. Open M3 (kitchen K2) and add it. Expected: a confirmation that the basket will be replaced, with nothing cleared yet.
3. Choose Keep. Expected: the cart is still only M1 × 1 from K1, S1.
4. Add M3 again and choose Replace. Expected: the cart is only M3 × 1 from K2. M1 is gone.
5. Add M1 from slot S2 (same kitchen, different slot). Expected: the replace confirmation appears again, because the slot differs.

### Kane invocation planned (not executed)

```
kane-cli run "<KQ-xx steps and expected results>" --url http://localhost:5173 --headless --agent --mode testing --timeout 600 --username "$LT_USERNAME" --access-key "$LT_ACCESS_KEY"
```

Each run's NDJSON output and evidence pack would be kept as evidence. Bug reports would follow the template in `04-external-qa-handoff.md` §4.

## Results

| Case | Result | Reason |
|---|---|---|
| KQ-01 Browse meals | NOT_RUN | Kane backend is blocked by the egress policy; not authenticated |
| KQ-02 Add to basket | NOT_RUN | Same |
| KQ-03 Basket replacement | NOT_RUN | Same |

No bugs were found or reported, because nothing was executed. The implementation agent's own TC-147 (web, self-review) is not independent evidence for these cases.
