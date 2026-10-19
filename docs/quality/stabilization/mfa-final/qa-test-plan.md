# FINAL QA10 plan

Source SHA: `240cf0c08fef1fa92bb65f2886a938cf9401d04a`.

Run the three repository validators, unfiltered Python scripts discovery, tests and typechecks separately in all five JavaScript packages, root lint, and the owner's five-class offline Maven suite. Reuse existing synthetic fixtures; no application changes, network, container execution or hash manifests. Findings use QA10- IDs only when supported by evidence.

Traceability: MFA precedence/defaults/reset-time boundary → MfaPolicyResolveTest; staff recent-factor/reset proof → StaffAssuranceTest; module boundaries → ModuleBoundaryTest; PDF validation → MediaInspectorPdfTest; order identity → OrderFingerprintTest. Client and UI regressions → api-client, design-tokens, customer-web, operations-web, customer-mobile tests. Planning/scope/migration integrity → Python validators and scripts tests. Static migration validation does not establish runtime migration compatibility.

Container-backed policy/API/provider/reset/session integration tests are NOT_RUN locally. This bounded run does not establish complete D18 acceptance or native device behavior. Prior detailed requirement mapping remains in ../mfa-round3/qa-test-plan.md; its execution results and line references belong to its older SHA.
