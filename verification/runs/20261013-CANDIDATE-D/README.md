# Candidate D attempt at cc52fa4: FAIL (workspace check), not published as a candidate

- Five of six local checks PASS at `cc52fa4224e3163677a1215dc8f63ede2a9dbe4c`: backend verify, local stack, browser
  journeys, secret scan (229 commits, no findings) and guardrail tests.
- **WORKSPACE-CLEAN-CHECKOUT FAIL:** the TC-103 brand guard (`packages/design-tokens/test/tokens.test.ts`, "apps do not
  hard-code the brand name") found the brand name written into the new MB-033 refusal text in
  `apps/customer-mobile/src/i18n.ts`. A real defect in the new code (the name must come from the brand tokens so it
  stays editable, O05). Missed before the push because only the mobile package's own tests had been run.
- Fix: the text takes the name from `brand.name[locale]`. All workspace package tests, typecheck and lint pass
  locally afterwards. The six checks are re-run at the next SHA.
