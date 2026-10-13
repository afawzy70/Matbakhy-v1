# Independent FULL REGRESSION — 2026-10-13

Application pinned: d877a1576cd942200fe0a53b5a19fee76b996025. QA branch qa/full-regression-d877a15-20261013. Definitions: docs/quality/test-cases/. Results pending execution, never imported from the previous campaign. Fresh Compose project matbakhy-regression-d877a15, DB36432/API39080/IdP39180/S339990/mail39025+32025; web36173/staff36174. Other projects untouched.

Startup: frozen pnpm lockfile; JDK21/Maven package; jar made readable to documented nonroot runtime; generated isolated real password+OTP realm; dedicated Compose override for local proxy/healthcheck; fresh database and one-shot seed. Initial API container failed because the built jar mode0600 was not readable by nonroot; QA setup correction chmod644/rebuild, not a product finding. No developer tests or CI inspection treated as independent journey execution. Fake SMS/notifications/media scanner, S3Mock/Mailpit; fixture verified phone via SQL, never counted as verification journey PASS.

Exact commands and final results will be recorded here after execution. Native workflow checks out the same literal application SHA and separate QA controls, uses fresh runner services, and does not cancel implementation workflows or upload store builds. Existing native harness is invoked independently; each assertion and skipped prerequisite is reviewed.
