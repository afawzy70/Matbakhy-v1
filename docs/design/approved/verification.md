# Scoped reference verification

Date: 2026-10-11. Tested reference source: `6e6a6d49c444e3f0051ba1d1f7b9e48f7640ce33`. Review: self-review by Codex, not independent review. Product gates and the active implementation checkpoint are unchanged.

| Check | Result |
|---|---|
| `node --check docs/design/approved/app.js` | PASS (Node 24.19.0) |
| `NODE_PATH=/workspace/scratch/matbakhy-dom-check/node_modules node docs/design/approved/check-dom.cjs` | PASS, jsdom 26.1.0 |
| `python3 scripts/validate_blueprint.py` | PASS: blueprint structure only |
| `git diff --check` | PASS |
| Chromium layout, screenshots, 320/390/420 px touch-target checks in `check.cjs` | BLOCKED: browser executable absent; matching Playwright browser download repeatedly returned an invalid/truncated ZIP |
| Real external photo/font loading, desktop/English/native rendering, accessibility audit | NOT_RUN |
| Backend/product/CI acceptance gates | NOT_RUN; no success inferred |

DOM checks exercised neutral initial selection, contextual-filter reveal/reset, one-person empty results, search, kitchen mode, price filtering, kitchen category/unavailable state, quantity/capacity bounds, integer totals, over-capacity basket rejection, cancellation and confirmation of cross-kitchen replacement, and clearing the basket. Dialog APIs are stubbed for DOM testing; this does not establish browser focus behavior or rendering.

To reproduce without changing project dependencies, install `jsdom@26.1.0` into a temporary external directory and set `NODE_PATH` to that directory's `node_modules`. For browser checks use Playwright with its matching Chromium installed (or `CHROMIUM_PATH`), then run `node docs/design/approved/check.cjs`. The repository's existing `playwright-core` dependency is also supported after workspace install. A passing browser run produces `homepage-review.png`; no screenshot is currently supplied or claimed.

Integration follow-up: run the browser check and inspect all three screens before porting. Replace remote assets with approved local assets, integrate shared product tokens/fonts, and verify existing checkout/order journeys in the selected visual system. This package does not close MB-004 or O05.
 
Publication used the GitHub connector because shell Git had no credentials. Checks ran locally against the identical published source tree `57bc7666a524ab4728b300953e1c2426c41930ff`; only this evidence file changes afterwards.

## Integration into `implementation/mvp` (Claude Code, 2026-10-11, self-review)

Merged `design/approved-terracotta` (b83630f) into `implementation/mvp` without conflicts (merge 912d447). Checks run in the implementation environment (Node 24.21.0, Chromium 1194 preinstalled at `/opt/pw-browsers`):

| Check | Result |
|---|---|
| `node --check docs/design/approved/app.js` | PASS |
| `check-dom.cjs` with jsdom 26.1.0 in an external temporary directory (`NODE_PATH`) | PASS |
| `check.cjs` with Playwright 1.56.1 and `CHROMIUM_PATH` set to the preinstalled Chromium | PASS: filters, reset, search, modes, price filter, routes, quantity/capacity, basket replacement/cancel, returning state, no horizontal overflow and all targets at least 44 px at 320/390/420 px, no page errors |
| Visual inspection of home, kitchen and dish screenshots at 320/390/420 px (RTL) | Inspected. One defect fixed: at 320 px the portion label and "details" action wrapped inside their buttons; the card action row now wraps instead (`style.css`). `check.cjs` re-run PASS after the fix |
| Cairo loading in Chromium (Google Fonts from the reference page) | Loaded (`document.fonts.check` true) |
| Remote food photographs | Did not load in this environment (requests failed); the image fallback was shown. Licensing/availability not established |
| Workspace lint (`eslint .`) | Initially FAILED on the reference files (browser globals in a classic script, `require()` in CommonJS checks). Fixed by declaring the correct environments in `eslint.config.js` (classic browser script; CommonJS for `.cjs`; browser globals for in-page callbacks of the check scripts); no rule weakened for product code |
| Desktop, English, native rendering; full accessibility audit | NOT_RUN for this reference |

The screenshot written by `check.cjs` is not committed (generated output). Product integration of the tokens and Cairo font is recorded in `docs/status/latest.md`.
