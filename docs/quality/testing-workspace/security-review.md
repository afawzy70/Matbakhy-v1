# Secret-scan findings and proposed resolution

Status: IMPLEMENTED — the owner explicitly approved the four documented fingerprints; exact exclusions applied, scan results recorded separately.

Gitleaks 8.30.1 (official download with verified SHA256 checksums) scanned all fetched branches: 561 commits, four findings. Independent security reviewer audit_identity classified the exact source usages below as non-credentials. No matched credential-like fixture value is reproduced here. The owner prototype remains byte-for-byte unchanged.

| Exact fingerprint | Independent source classification |
|---|---|
| ab514a810cb07b3d3a40648af172a3e68d611197:docs/quality/testing-workspace/reference/approved-prototype.html:generic-api-key:24 | Owner HTML constant identifies localStorage progress, not authentication. |
| 2edfa2dae0555f48564e162af5cdff4ea11baed3:.gitleaksignore:generic-api-key:41 | Historical explanatory comment describes synthetic test markers, not an active credential. |
| 433d6925243b47473a35dc8816019e9d0571b1e7:ai-skills/developer-handbook/scripts/test_bilingual_parity.py:generic-api-key:351 | Synthetic marker in a temporary exception fixture tests diagnostic redaction. |
| 5e15ddd6e293d4e79499564d3aaefd51af089ad3:ai-skills/developer-handbook/scripts/test_bilingual_parity.py:generic-api-key:284 | Fabricated credential text in a temporary test document tests masking. |

## Owner-approved exact scope

The owner approved adding only the following four lines to the existing ignore file. These are now active exact-fingerprint exclusions. No whole-file, path or rule exclusion is proposed; unchanged commit fingerprints would exempt only these four historical matches. Any other match would still fail.

```text
ab514a810cb07b3d3a40648af172a3e68d611197:docs/quality/testing-workspace/reference/approved-prototype.html:generic-api-key:24
2edfa2dae0555f48564e162af5cdff4ea11baed3:.gitleaksignore:generic-api-key:41
433d6925243b47473a35dc8816019e9d0571b1e7:ai-skills/developer-handbook/scripts/test_bilingual_parity.py:generic-api-key:351
5e15ddd6e293d4e79499564d3aaefd51af089ad3:ai-skills/developer-handbook/scripts/test_bilingual_parity.py:generic-api-key:284
```

Automatic permission review previously rejected applying the proposal without explicit owner authorization because it persistently reduces scanner coverage. The owner subsequently explicitly approved this exact four-fingerprint scope. That approval has now been applied without broad exclusions, scanner changes, history rewriting, merge or deployment. Full-history and committable-tree scan results are recorded in the follow-up evidence; no success is inferred merely from applying the exclusions.

## Verification after applying the approved four fingerprints

The verified binary scanned 563 history commits and reported no leaks (exit 0). A committable-tree snapshot reported one match: the same non-credential progress namespace in the archived prototype at line 24. Directory scans use a path-only fingerprint rather than the approved commit-qualified fingerprint. No fifth exclusion has been added; the tree gate remains FAIL. The original prototype bytes remain unchanged.

CI uses the Docker mount path, so the unresolved directory-scan fingerprint is:

```text
/repo/docs/quality/testing-workspace/reference/approved-prototype.html:generic-api-key:24
```

This additional fingerprint is not covered by the four-line approval and has not been applied. Full Secret scan success is not claimed. The change also removes two redundant browser-global declarations identified by CI lint; this is a comment-only correction with no runtime change.
