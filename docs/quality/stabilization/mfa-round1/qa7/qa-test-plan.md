# D18 independent QA plan and new-test traceability

Source SHA: `c139bdf3c03705f042388ad5d8bd0c1ccf945a1d`. Owner requirements 1–12 are authoritative. Recommendations below do not change acceptance criteria. No application source modified.

Only tests introduced by D18 count in this matrix. R = MfaPolicyResolveTest, P = MfaPolicyIntegrationTest, I = MfaIdpTest, X = MfaResetIntegrationTest, C/O = the new D18 describe blocks in customer/operations App.test.tsx. Existing StaffAssuranceTest and other regression tests are execution evidence only.

| Requirement | New meaningful automated coverage (file test method line) | Gaps / disposition |
|---|---|---|
| 1 Three modes | R:30,39,48,57; P:130,158,183; C:1481–1538 | Meaningful resolver/API/UI scenarios for all modes. |
| 2 Separate category administration | P:111 permission/reason/version/update; P:202 separate STAFF/KITCHEN changes; O:792 save STAFF and :817 CUSTOMER refusal | Meaningful; not an exhaustive category × mode API matrix. |
| 3 OPTIONAL own enable/disable | P:158 successful toggles, idempotency, proof required; C:1494 toggles own preference | Meaningful; no attempted other-user preference write or provider removal E2E. |
| 4 REQUIRED enrol/authenticate and no disable | P:130,148; X:86 bearer enforcement; I:45,52 unenrolled flow; C:1511; O:842 workspace step-up | Meaningful component scenarios, no complete browser-to-API session lifecycle test. |
| 5 DISABLED unavailable, preserve factors, other controls intact | R:48 retains preference; P:183 rejects enable, keeps preference, restores enforcement; C:1521 hides enable | PARTIAL: preference is not a provider factor. No meaningful new test proves existing Keycloak credential preservation, direct enrol/use refusal, or unchanged high-risk controls under DISABLED. |
| 6 Keycloak OIDC, no custom Spring OTP | I:31,38,45,52,61,69 real provider challenge/wrong code/enrol/claims/SSO; X:86 real bearer chain | Meaningful provider tests; no architecture assertion excluding custom production OTP. Helper OTP calculation is test-only. |
| 7 Security UI enrol/status/removal via Keycloak | C:1481 checks management href, C:1494,1515,1526 status/toggle; O policy/reset UI | PARTIAL: no new automated provider account-console removal or actual enrolled-factor status test; operations own settings not directly exercised. |
| 8 Recent reauth before disable; session invalidation | P:158 rejects missing/hour-old proof and accepts fresh; C:1530 handles refusal; X:105 asserts sessionsEnded response | PARTIAL: no meaningful session/refresh-token invalidation assertion for disable or reset. No stale real browser-session test. |
| 9 Safe audited reset, no OTP secret access | P:227 guards; X:105 real deletion, re-enrol prompt, allowed/denied audit and response omissions; O:824 reason/count | Meaningful reset coverage. Public reset response lacks secrets; this does not prove provider credential listing or all admin-accessible surfaces lack secrets. |
| 10 Server enforcement; preference cannot override REQUIRED | R:39,57,67; P:130,148,202; X:86 same bearer denied after policy change | Meaningful. Missing another-client token, DB-derived kitchen+staff mix, concurrent change/preference write. |
| 11 PREVIEW OPTIONAL all; later REQUIRED | R:30 hardcoded defaults; P:98 defaults after fixture reset; P:111 update; X:86 initial default then REQUIRED | PARTIAL: P default assertion is fixture-controlled (QA7-002); X observes fresh-context default but does not assert all three persisted category defaults. |
| 12 Docs, permissions, config, tests, migration/rollout | P:111,227 permissions; I provider config execution; X real DB/provider | PARTIAL: no meaningful new test for deployed realm parity, running-realm upgrade, rollout, rollback compatibility or documentation consistency. Generic validators are outside this NEW-tests matrix. |

Backend test paths: `backend/app/src/test/java/com/matbakhy/app/{mfa,identity}/`. UI paths: `apps/{customer-web,operations-web}/src/App.test.tsx`. Lines refer to the source SHA above. Container-dependent tests are statically mapped, NOT_RUN locally; a mapping is not a PASS.

## Test-quality review

* Confirmed ordering defect: X:108 and X:143 insert the same active permission for the same persistent otp-admin account; V4:22 has a unique active-grant index. After either test, the other fails at fixture insertion, before its intended assertion (QA7-001). No cleanup removes the grant.
* TOTP: no demonstrated successful same-account code reuse across distinct new I/X cases. I:38 wrong-code and I:61 valid-code share otp-stepup, but the wrong code does not consume the valid one. Most successful cases use distinct accounts. Repeating a successful case within a 30-second step can reuse its code; retries/parallel repeats need fresh accounts or next-step handling. Codes are generated before password/browser requests (X:70, I:62), so slow CI can age them. These are recommendations, not observed flakes; no arbitrary sleeps proposed.
* Shared DB: P resets all three policy rows before/after every method (P:53,60), with no transactional rollback. This supports sequential execution but races if methods sharing the context run concurrently. X only restores policies afterward. No parallel execution was established; do not claim a reproduced race.
* Tautology: R hardcoded OPTIONAL checks the resolver, not PREVIEW seed configuration. P:98 reads rows that its own setup just rewrote to OPTIONAL (QA7-002). UI mock echoes are useful request/render contract assertions, not proof of server policy enforcement.
* Response-only assertions: X:123 sessionsEnded cannot detect a provider logout no-op; X then creates a new browser instead of exercising the old session. P disable works with no configured provider admin. QA7-003 records this concrete false-negative gap.
* Existing stale-factor negative exists (P:173); stale real SSO after reset/disable and stale MFA admin policy/reset negatives do not. Another-client token rejection is absent from the new suite. R mixes all categories synthetically; P never creates one DB account with both kitchen ownership and staff grant, nor revoked/expired-grant or non-approved-kitchen cases. X:90–92 verifies sequential live policy change; P:122 stale-version is not a concurrent-writer test. Add coordinated concurrency tests for policy updates and preference disable racing REQUIRED, plus both HTTP security chains.

## Execution scope

Run planning validators, Python discovery, each package test/typecheck, root ESLint and the owner-selected offline backend suite using JDK 21. Docker/Testcontainers/Keycloak-container tests are NOT_RUN by explicit owner/environment constraint. Their CI result must be associated with this exact source SHA; workflow configuration alone is not run evidence. No CI run identifier/result for this SHA was supplied or retrievable offline. No network calls, deployment, native builds or broad backend suite were attempted.
