# Source provenance and technical references

## Product provenance

Prepared 2026-10-11 from the current owner request, retrieved Matbakhy-specific prior discussions and the supplied repository. Retrieval is partial context, not an exhaustive archive. No private transcripts, personal profile details or unrelated project files are copied into this public repository.

| Source | What it supports | Limit |
|---|---|---|
| Owner request, 2026-10-11 | MVP includes customer web, backoffice, Android/iOS; detailed executable plan/rules/business logic; verify-correct-continue; target repository | Does not select payment/fulfillment/vendor/commission/launch city |
| Owner Matbakhy discussion, 2026-04-28 | Egypt supersedes Kuwait; branding early/editable; Spring Boot backend; Android/iOS; AI-assisted mobile build | FlutterFlow/React options were assistant recommendations |
| Recurring 2025/2026 Matbakhy discussions | Homemade-meal marketplace connecting cooks and customers | Specific roles/features and commission model often appeared in assistant proposals |
| Bolt MVP exercise, 2026-07-26 | Prior prototype direction included social discovery and staged generation | Mock UI and no-payment/no-delivery suggestions do not establish production scope |
| Repository read, 2026-10-11 | Initial README only at `82968ee4fe10192a23e5912bfb6ecc05e33eb757` | No implemented product, existing tests or approved visuals found |

Confirmed facts and proposals are separated in the context/decision register. Brand clearance, local legal/commercial policy and provider availability are not verified by this planning effort. Their current validation is a launch dependency.

## Official technical references checked 2026-10-11

- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html): verify compatibility when pinning the Java/Spring toolchain; this pack does not freeze a transient latest version.
- [Expo local app builds](https://docs.expo.dev/guides/local-app-overview/): local native builds use platform development toolchains.
- [Expo local EAS builds](https://docs.expo.dev/build-reference/local-builds/): check platform-specific prerequisites before selecting build runner.
- [Expo application credentials](https://docs.expo.dev/app-signing/app-credentials/): store-distributed apps need appropriate platform signing credentials.
- [Expo build setup](https://docs.expo.dev/build/setup/): configure native build profiles and developer account requirements.
- [Expo iOS submission](https://docs.expo.dev/submit/ios/): submission is an explicit workflow; upload does not prove public release.
- [Claude Code checkpointing](https://code.claude.com/docs/en/checkpointing): session checkpoints do not replace Git and do not cover every external/shell change. Durable repository state/evidence remains necessary.
- [Claude Code memory](https://code.claude.com/docs/en/memory): reference for maintaining project instructions; keep the entrypoint short and detailed specs separately navigable.
- [Pinned GitHub checkout action reference](https://api.github.com/repos/actions/checkout/git/ref/tags/v4): resolved to `11d5960a326750d5838078e36cf38b85af677262` for the initial blueprint workflow. Reassess supported action versions when extending CI.

Architecture, business defaults, estimates, domain rules, test scenarios and operating procedures are design recommendations in this repository, not statements copied from these references. Recheck current provider/store/framework documentation during implementation and release.
