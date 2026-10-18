# Matbakhy configuration, Keycloak and security test cases

**All cases: NOT_RUN.** This is a test specification, not proof of execution. Tested source SHA, deployment digest, operator, environment and evidence must be recorded for each result.

**Environment safety:** Customer/Operations preview URLs may be public. The Keycloak realm login/discovery endpoints are public, but the Keycloak admin console and administrative API must remain network-restricted. Use an authenticated SSH tunnel for administration; MFA, audit and least privilege remain in force. Conduct destructive, failure injection, concurrency, or policy-mutating tests on an isolated disposable environment unless an explicitly approved maintenance window is documented. Use synthetic identities only.

## Identity and OIDC

### CFG-001 — Public realm, protected administration

**Status:** NOT_RUN · **Actor:** Guest

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Open https://id.167-86-90-174.sslip.io/realms/matbakhy/.well-known/openid-configuration; inspect issuer; request /admin and /realms/master.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Issuer matches HTTPS public realm; admin/master remain blocked.

### CFG-002 — Web login with PKCE and logout

**Status:** NOT_RUN · **Actor:** Customer A

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. From app site select login; authenticate via Keycloak; open /me; log out; revisit protected resource.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Correct subject; session established and removed; protected resource denied afterward.

### CFG-003 — Invalid redirect and state

**Status:** NOT_RUN · **Actor:** Test OIDC client

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Start authorization using an unregistered redirect_uri; then alter callback state and PKCE verifier in separate isolated browser sessions.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Redirect rejected; tampered callback cannot establish session.

### CFG-004 — Password/session expiry

**Status:** NOT_RUN · **Actor:** Customer A

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Login; expire/revoke session in disposable environment; retry protected request and refresh token.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** No continued authorized access with invalid session; recovery requires reauthentication.

### CFG-005 — Privilege boundary

**Status:** NOT_RUN · **Actor:** Customer A/Cook B

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Try ops and other cook routes via browser and API; compare UI/API response.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** No privileges granted based on exposed URL or forged client role.

### CFG-006 — Session sharing

**Status:** NOT_RUN · **Actor:** Customer A/B

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Login A in browser 1 and B in browser 2; switch accounts on shared browser; inspect basket/orders/history.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Identity-scoped caches clear; no cross-account data display.

## MFA policy modes and configuration

### CFG-007 — Customer OPTIONAL disabled preference

**Status:** NOT_RUN · **Actor:** Admin/Customer A

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Set CUSTOMER OPTIONAL with expectedVersion; login A; leave user preference OFF; place order and read security settings.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Normal operation without requested MFA; high-risk actions independently protected.

### CFG-008 — Customer OPTIONAL preference ON

**Status:** NOT_RUN · **Actor:** Customer A

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Enroll TOTP through Keycloak step-up; turn on own MFA; sign out/in; access protected API without step-up then with step-up.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** STEP_UP_REQUIRED until acr/amr proof; then allowed.

### CFG-009 — Kitchen OPTIONAL preference ON

**Status:** NOT_RUN · **Actor:** Cook A

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Set KITCHEN OPTIONAL; enroll cook OTP; enable; attempt meal update without and with step-up.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Policy applies to cook-scoped actions.

### CFG-010 — Staff OPTIONAL

**Status:** NOT_RUN · **Actor:** Staff A

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Set STAFF OPTIONAL; leave preference OFF; attempt routine permitted read and high-risk policy update.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Routine access per policy; high-risk update still requires recent MFA.

### CFG-011 — Category REQUIRED

**Status:** NOT_RUN · **Actor:** Admin/Customer/Cook/Staff

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. In isolated environment set category REQUIRED; attempt protected call password-only; complete step-up; attempt to disable own MFA.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Denied without proof, allowed after proof; disabling rejected.

### CFG-012 — Category DISABLED

**Status:** NOT_RUN · **Actor:** Admin/User A

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Set category DISABLED; inspect own security page and attempt enable API; check previously registered OTP data.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Enrollment unavailable in app; previous factor not silently removed; strong staff actions stay protected.

### CFG-013 — Cross-role precedence

**Status:** NOT_RUN · **Actor:** Multi-role A

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Configure CUSTOMER DISABLED, KITCHEN OPTIONAL, STAFF REQUIRED; sign in multi-role A; exercise protected actions.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Most protective REQUIRED controls all applicable actions.

### CFG-014 — Missing configuration row

**Status:** NOT_RUN · **Actor:** Operator on isolated DB

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Remove one category configuration row in disposable database; perform protected action.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Fails safely as REQUIRED; logs no secrets.

### CFG-015 — Optimistic version conflict

**Status:** NOT_RUN · **Actor:** Admin A/B

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Fetch same policy version; submit conflicting PUT changes; inspect audit/current value.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** One update succeeds; stale update returns conflict.

### CFG-016 — Unauthenticated policy mutation

**Status:** NOT_RUN · **Actor:** Guest/Customer

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Send policy PUT/reset API using no session or customer session.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Denied; no state change.

### CFG-017 — MFA user-disable safety

**Status:** NOT_RUN · **Actor:** Customer A

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Enable factor; attempt disable without recent step-up, then with fresh step-up; refresh existing session.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** First denied; second allowed under OPTIONAL; sessions and elevated proof invalidated.

### CFG-018 — Admin reset permissions

**Status:** NOT_RUN · **Actor:** Admin A/Support

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Attempt reset by support, self-reset by admin, and reset other user with short reason.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** All invalid attempts denied and audited.

### CFG-019 — Authorized admin reset

**Status:** NOT_RUN · **Actor:** Admin A/Customer B

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Admin has reset grant and recent MFA; provide >=10 char reason; reset B; try sensitive call with B pre-reset token.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Credential removed at IdP; old elevated proof rejected; audit retained.

### CFG-020 — Reset with IdP failure

**Status:** NOT_RUN · **Actor:** Isolated operator

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Inject transient Keycloak service account API failure during reset; inspect durable reset marker and retry.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Incomplete audit preserved; no stale assurance; idempotent recovery.

### CFG-021 — Old token after reset

**Status:** NOT_RUN · **Actor:** Customer B

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Retain a still-live pre-reset access token; reset account; invoke sensitive action.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Denied regardless of access-token expiry.

### CFG-022 — TOTP invalid/replay

**Status:** NOT_RUN · **Actor:** Enrolled user

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Enter incorrect OTP, then replay a successful code in same time step.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Bad/reused proof rejected.

### CFG-023 — Missing Keycloak client secret

**Status:** NOT_RUN · **Actor:** Operator

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. In disposable stack remove OIDC_ADMIN_CLIENT_SECRET; attempt protected reset.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** 503 safe failure; no false success.

### CFG-024 — Mobile same-user step-up

**Status:** NOT_RUN · **Actor:** iPhone Customer A

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Trigger protected call; request step-up; authenticate A; return to app; retry non-idempotent order via explicit action.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** No duplicate order; safe recovery; pending idempotency key preserved.

### CFG-025 — Mobile different-user step-up

**Status:** NOT_RUN · **Actor:** iPhone Customer A/B

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Have A order/basket; trigger step-up but authenticate B; reopen account screens.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** A profile, cart, orders and caches absent before B rendered.

### CFG-026 — Mobile background/return

**Status:** NOT_RUN · **Actor:** iPhone Customer A

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Begin step-up; background app; return; cancel or complete auth.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Correct callback and recovery; no stale loading or accidental placement.

## Keycloak migration and release configurations

### CFG-027 — Migration status before apply

**Status:** NOT_RUN · **Actor:** Operator

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. After approved application deploy run ./migrate-mfa.sh status.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Reports actual existing flow, client and secret readiness without disclosure.

### CFG-028 — Migration apply with backup

**Status:** NOT_RUN · **Actor:** Operator

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. In change window run apply; inspect DB backup/realm export names and status; verify.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Idempotent flow and mapper created, verified backup, safe status.

### CFG-029 — Repeated migration apply

**Status:** NOT_RUN · **Actor:** Operator

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Repeat apply on already-migrated disposable realm.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** No duplicate flows/clients or changed active policy.

### CFG-030 — ACR/AMR proof

**Status:** NOT_RUN · **Actor:** Synthetic staff

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Sign in password only and inspect sanitized claims; step-up with TOTP and inspect sanitized claims; call protected API.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Password-only rejected when required; valid step-up claim accepted.

### CFG-031 — Migration rollback unusual names

**Status:** NOT_RUN · **Actor:** Disposable Keycloak

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Use old flow name containing apostrophe; apply/rollback using saved structured state.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** No source/evaluation of state; previous flow restored.

### CFG-032 — Rollback outage recovery

**Status:** NOT_RUN · **Actor:** Operator

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. In disposable environment interrupt migration before final flow binding; retry/rollback.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** No partial active flow or loss of user credentials.

### CFG-033 — MFA default deployment policy

**Status:** NOT_RUN · **Actor:** Operator

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Read policy DB after V31 migration for CUSTOMER/KITCHEN/STAFF.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** All OPTIONAL on preview; never silently REQUIRED.

## Security, operations and delivery

### CFG-034 — Public operations versus protected data

**Status:** NOT_RUN · **Actor:** Guest

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Open ops site publicly; request staff API unauthenticated.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** HTML accessible; sensitive operations return 401/403.

### CFG-035 — Public Keycloak admin boundary

**Status:** NOT_RUN · **Actor:** Guest

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. From external network try /admin, /realms/master and admin REST APIs.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Blocked; no public Keycloak management console.

### CFG-036 — CORS and CSRF

**Status:** NOT_RUN · **Actor:** Guest/test account

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. From unapproved origin attempt credentialed mutation; submit missing/invalid CSRF token on web session.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Requests denied; allowed site origin works.

### CFG-037 — Session cookies

**Status:** NOT_RUN · **Actor:** Browser tester

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Inspect Set-Cookie for session and CSRF cookies over HTTPS.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Correct Secure/HttpOnly/SameSite attributes according to cookie role.

### CFG-038 — CSP rollout

**Status:** NOT_RUN · **Actor:** Browser tester

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Inspect customer/ops headers and console on login/checkout.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Report-Only acknowledged, no untested claim of enforced CSP.

### CFG-039 — Rate-limits

**Status:** NOT_RUN · **Actor:** Security QA

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Use low bounded retry counts against login and OTP endpoints in disposable environment.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Throttling/account policy measurable, no brute force.

### CFG-040 — Secrets and logs

**Status:** NOT_RUN · **Actor:** Security QA

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Run full-history secret scan; inspect sanitized API and deployment logs.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** No secrets or private factors exposed.

### CFG-041 — Develop CD

**Status:** NOT_RUN · **Actor:** Maintainer

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Merge approved PR to develop; follow Build/GHCR/SSH/backup/deploy/smoke in Actions.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Correct SHA and digest deployed; smoke green.

### CFG-042 — Failed deploy

**Status:** NOT_RUN · **Actor:** Operator on isolated clone

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Deploy deliberately failing image; observe health gate and rollback procedure.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Failure visible; backup preserved, no data destruction.

### CFG-043 — Backup verification

**Status:** NOT_RUN · **Actor:** Operator on isolated DB

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Back up and restore into scratch database; run reconciliation.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Restore verifies integrity without touching live DB.

### CFG-044 — Server reboot

**Status:** NOT_RUN · **Actor:** Operator in change window

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Reboot only with approval; inspect API, DB, proxy, agents and timers.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Services recover; interrupted agent tasks resumed only from evidence.

### CFG-045 — Synthetic money guarantee

**Status:** NOT_RUN · **Actor:** Operator

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Place demo deposit/cash order and inspect provider traffic.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** No real transfers or messages occur.

### CFG-046 — Apple iPhone build

**Status:** NOT_RUN · **Actor:** iPhone tester

**Preconditions:** Provision synthetic actor accounts with correct grants; identify expected source commit and environment; snapshot relevant policy/state. Avoid using personal or production data.

**Detailed execution**
1. Record the deployed source SHA, build ID, account role, test run ID and original settings.
2. Install signed TestFlight build; record build ID and versions; run auth/order/MFA/offline journeys.
3. Check both browser UI and authoritative API/Keycloak outcomes; where a state transition occurs, inspect audit logs or isolated DB read-only evidence.
4. Restore modified policy or test-only configuration, if applicable. Confirm that unrelated accounts remain unaffected.
5. Record screenshots/sanitized HTTP response codes, correlation IDs, observed result and PASS/FAIL/BLOCKED with a defect reference.

**Expected:** Physical-device evidence recorded separately from simulator tests.

