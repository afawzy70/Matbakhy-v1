# Dependency and supply-chain audit

Baseline: `b7ec0e9052a612f7a8aab38f00519107b1dce213`. Trivy 0.76.0, pnpm 10.34.6. [Trivy tool provenance](evidence/trivy-tool.json), [pnpm JSON](evidence/pnpm-audit.json), [web/infra JSON](evidence/trivy-web-infra.json), [Java offline JSON](evidence/trivy-java-offline.json). Exact versions, advisories, paths, recommended fixed ranges and status are in findings-register.json under `scanner_warnings`.

## Execution and coverage limits

Initial combined scan downloaded its vulnerability/check databases but failed resolving a Maven BOM: Maven Central returned HTTP 429 and Retry-After 1799 seconds. We did not retry the rate-limited service immediately or call a failed scan clean. The separate web/infra scan completed and analyzed 577 lockfile package records. Native pnpm audit completed and returned four advisories, including one not present in the Trivy web result.

An explicit `--offline-scan --skip-db-update` backend scan then completed using the partially populated Trivy Maven cache: 12 POM results, including 149 packages reported for app/pom.xml and 669 aggregated records for parent pom.xml (duplicates across modules). It produced ten unique Java advisory/package/version tuples. This is **POM-derived, cache-dependent SCA**, not a Maven-resolved runtime tree or built-image inspection. Unresolved/missing transitives, BOM scope expansion and build/test-only dependencies must be checked against a real runtime SBOM before release. No compiled JAR, GHCR image OS packages, Keycloak/SeaweedFS/Caddy runtime image or Android/iOS packaged artifact was scanned. Docker access was denied; no privileged workaround attempted.

## Results and applicability

| Scanner package | Version | Advisory matches | Assessment |
| --- | --- | --- | --- |
| tomcat-embed-core | 11.0.24 | CVE-2026-65182, -65905, -68525 (3 Critical scanner labels) | Version match confirmed in POM analysis; reviewed auth uses Spring filter chains, not Tomcat FORM/DIGEST or equivalent container security constraints. Exploitation not demonstrated. Upgrade compatible Boot/Tomcat; do not call this three verified application auth bypasses |
| jackson-core | 3.1.5 | CVE-2026-89407, -89425 (2 High) | String-to-number coercion is explicitly disabled by StrictJsonConfiguration; no DataInput parser use found. Runtime mitigation regression still needed |
| jackson-databind | 3.1.5 | CVE-2026-68497, -91776, -91777 (3 High), -19032, -83557 (2 Medium) | Reviewed request models do not use the identified XML datatypes, polymorphic fallback, identity-reference or Path-binding features. Version matches remain warnings pending runtime artifact/configuration review |
| node-forge | 1.4.0 | GHSA-86w9-cpqp-85rv (High) | Expo CLI transitive tooling; forged-signature path reachability in Matbakhy not established |
| braces | 3.0.3 | GHSA-vfj7-8cjw-p6xm (High) | Mobile Jest/babel-jest/micromatch path; untrusted nested-pattern exposure not established; no DoS payload executed |
| uuid | 7.0.3 | GHSA-w5hq-g745-h8pq (Moderate) | Expo config-plugins/xcode tooling; vulnerable caller-buffer API use not demonstrated |
| sprintf-js | 1.0.3 | GHSA-hp3w-g68c-fv3c (Moderate) | Mobile test tooling through Istanbul/js-yaml/argparse; pnpm-only detection; no runtime format-string exposure established |

Aggregate unique dependency warnings: **3 Critical, 7 High, 4 Medium**, with application exploitation **NOT VERIFIED**. These are kept separate from confirmed application findings and are not silently accepted for the final gate. Resolve them with compatible updates or documented applicability evidence plus independent review. Availability exploits were not sent to preview.

The [Apache Tomcat security page](https://tomcat.apache.org/security-11.html) is an authoritative cross-check; it lists additional issues beyond these scanner matches, reinforcing that scanner database coverage is not exhaustive. Use the current supported patched line, not only the oldest fixed version named by a scanner. Do not confuse the earlier forge GHSA-ppp5-5v6c-4jwp (fixed in 1.4.0) with the later GHSA-86w9-cpqp-85rv detected here. Advisory links from machine output are preserved for revalidation.

Trivy also flags root user (DS-0002, High scanner label) and missing Dockerfile HEALTHCHECK (DS-0026, Low) in front.Dockerfile. These are hardening warnings: root is not proof of host escape; Compose probes and reverse-proxy operation must be considered. Front build/runtime FROM tags are unpinned, while several backend/service images are digest-pinned. Recommend digest pinning, least privilege, dropped capabilities and an appropriate healthcheck through Claude; these are not claimed as independently exploited High defects.
