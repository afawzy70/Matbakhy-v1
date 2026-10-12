#!/usr/bin/env bash
# Kane CLI QA pilot: run TC-K1..TC-K3 headlessly against the QA stack (qa-stack.sh up). Synthetic data only.
#   run-cases.sh [TC-K1|TC-K2|TC-K3 ...]     default: all three
# Needs LT_USERNAME / LT_ACCESS_KEY in the environment (never printed, never written). Refuses to run when the credit
# balance cannot be read. Each case uses a fresh Chrome profile, so the browser cart starts empty (known state);
# the DB fixture is re-applied first. NDJSON output goes to $QA_EVIDENCE_DIR (default: ./evidence/<UTC stamp>),
# with the credentials masked.
set -euo pipefail
set +x

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MVP="${MVP_WORKTREE:?set MVP_WORKTREE to the implementation worktree under test}"
URL="${QA_URL:-http://localhost:5173/}"
OUT="${QA_EVIDENCE_DIR:-$HERE/evidence/$(date -u +%Y%m%dT%H%M%SZ)}"
CHROME="${KANE_CLI_CHROME_PATH:-$(ls -d /opt/pw-browsers/chromium-*/chrome-linux/chrome 2>/dev/null | head -1)}"
export KANE_CLI_CHROME_PATH="$CHROME"
: "${LT_USERNAME:?LT_USERNAME missing}" "${LT_ACCESS_KEY:?LT_ACCESS_KEY missing}"
# Stray whitespace from pasting into environment settings is not part of either value.
LT_USERNAME="$(printf %s "$LT_USERNAME" | tr -d '[:space:]')"; LT_ACCESS_KEY="$(printf %s "$LT_ACCESS_KEY" | tr -d '[:space:]')"
mkdir -p "$OUT"

mask() { sed -e "s/${LT_ACCESS_KEY}/***ACCESS_KEY***/g" -e "s/${LT_USERNAME}/***USERNAME***/g"; }
auth=(--username "$LT_USERNAME" --access-key "$LT_ACCESS_KEY")

echo "-- credit balance (gate)"
if ! kane-cli balance "${auth[@]}" </dev/null 2>&1 | mask | tee "$OUT/balance.txt"; then
  echo "BLOCKED: credit balance not verifiable; no case run" | tee -a "$OUT/balance.txt"; exit 3
fi
grep -qi 'error\|fail' "$OUT/balance.txt" && { echo "BLOCKED: balance check reported an error; no case run"; exit 3; }

PREP="Open the page. If the page is in Arabic, click the language button labelled 'English' so the interface is in English. In the 'Your area' selector choose 'Test zone A'."

declare -A OBJ
OBJ[TC-K1]="$PREP Then list the dishes. Assert that dishes 'QA Molokhia', 'QA Fatta' and 'QA Mahshi' are listed, each showing a kitchen name and a price in EGP with no fractional piastres. Assert that the 'QA Molokhia' card shows the text 'Green molokhia with garlic, QA description'. Assert that the 'QA Fatta' card does not show an empty description area, a placeholder such as 'No description' or invented text. Assert that 'QA Paused Meal' is not offered for purchase. Assert that no filter control for 'one person', 'two people' or any portion size is visible anywhere on the page, and that no sort option is labelled as a portion or serving-size filter."
OBJ[TC-K2]="$PREP Open the dish 'QA Molokhia' from 'مطبخ اختبار أ'. Note the displayed price. Choose the earliest delivery/pickup slot (the 1:00 PM slot), set quantity to 1 and click 'Add to cart'. Assert that the message 'Added to cart.' appears. Open 'Cart' from the navigation. Assert that the cart contains exactly one line, 'QA Molokhia' quantity 1 from kitchen 'مطبخ اختبار أ' for the 1:00 PM slot, and that the cart total equals the price noted on the dish page (EGP 85, no fractions)."
OBJ[TC-K3]="$PREP Open the dish 'QA Molokhia', choose the 1:00 PM slot, quantity 1, click 'Add to cart' and assert 'Added to cart.' appears. Part A (different kitchen): go back to the dish list, open 'QA Mahshi' from 'مطبخ اختبار ب', choose its slot, quantity 1, click 'Add to cart'. Assert that a dialog titled 'Replace your cart?' appears explaining one kitchen and one slot per order. Click 'Keep my current cart'. Assert the message 'Your cart is unchanged.' appears. Open 'Cart' and assert it still holds only 'QA Molokhia' x1 for the 1:00 PM slot. Part B: open 'QA Mahshi' again, choose its slot, click 'Add to cart', and in the 'Replace your cart?' dialog click 'Replace cart'. Open 'Cart' and assert it holds only 'QA Mahshi' x1 from 'مطبخ اختبار ب' and no 'QA Molokhia'. Part C (same kitchen, different slot): open 'QA Molokhia', choose the 7:00 PM slot, click 'Add to cart' and click 'Replace cart' if asked so the cart holds 'QA Molokhia' at 7:00 PM; then open 'QA Molokhia' again, choose the 1:00 PM slot and click 'Add to cart'. Assert that the 'Replace your cart?' dialog appears again. Click 'Keep my current cart' and assert the cart still holds 'QA Molokhia' for the 7:00 PM slot only."

cases=("$@"); [ ${#cases[@]} -gt 0 ] || cases=(TC-K1 TC-K2 TC-K3)
for c in "${cases[@]}"; do
  [ -n "${OBJ[$c]:-}" ] || { echo "unknown case $c"; exit 2; }
  "$HERE/qa-stack.sh" seed "$MVP" >/dev/null
  profile="$(mktemp -d)"
  echo "-- $c"
  set +e
  kane-cli run "${OBJ[$c]}" --url "$URL" --headless --timeout 600 --max-steps 80 --agent --mode testing \
    --chrome-profile "$profile" --name "matbakhy-${c}" "${auth[@]}" </dev/null 2>&1 | mask > "$OUT/$c.ndjson"
  rc=${PIPESTATUS[0]}
  set -e
  rm -rf "$profile"
  echo "$c exit=$rc" | tee -a "$OUT/exit-codes.txt"
done
echo "evidence: $OUT"
