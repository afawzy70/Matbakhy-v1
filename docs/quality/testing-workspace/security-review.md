# Secret-scan findings and proposed resolution

Status: BLOCKED — no scanner exclusions have been applied.

Gitleaks 8.30.1 (official download with verified SHA256 checksums) scanned all fetched branches: 561 commits, four findings. Independent security reviewer audit_identity classified the exact source usages below as non-credentials. No matched credential-like fixture value is reproduced here. The owner prototype remains byte-for-byte unchanged.

| Exact fingerprint | Independent source classification |
|---|---|
| ab514a810cb07b3d3a40648af172a3e68d611197:docs/quality/testing-workspace/reference/approved-prototype.html:generic-api-key:24 | Owner HTML constant identifies localStorage progress, not authentication. |
| 2edfa2dae0555f48564e162af5cdff4ea11baed3:.gitleaksignore:generic-api-key:41 | Historical explanatory comment describes synthetic test markers, not an active credential. |
| 433d6925243b47473a35dc8816019e9d0571b1e7:ai-skills/developer-handbook/scripts/test_bilingual_parity.py:generic-api-key:351 | Synthetic marker in a temporary exception fixture tests diagnostic redaction. |
| 5e15ddd6e293d4e79499564d3aaefd51af089ad3:ai-skills/developer-handbook/scripts/test_bilingual_parity.py:generic-api-key:284 | Fabricated credential text in a temporary test document tests masking. |

## Concrete proposal requiring owner approval

Add only the following four lines to the existing ignore file. This is a review proposal, not active configuration. No whole-file, path or rule exclusion is proposed; unchanged commit fingerprints would exempt only these four historical matches. Any other match would still fail.

```text
ab514a810cb07b3d3a40648af172a3e68d611197:docs/quality/testing-workspace/reference/approved-prototype.html:generic-api-key:24
2edfa2dae0555f48564e162af5cdff4ea11baed3:.gitleaksignore:generic-api-key:41
433d6925243b47473a35dc8816019e9d0571b1e7:ai-skills/developer-handbook/scripts/test_bilingual_parity.py:generic-api-key:351
5e15ddd6e293d4e79499564d3aaefd51af089ad3:ai-skills/developer-handbook/scripts/test_bilingual_parity.py:generic-api-key:284
```

Automatic permission review rejected applying these exclusions because the owner had not explicitly authorized suppressing these findings or changing scanner enforcement scope. The stated risk is persistent reduction of scanner coverage. Implementation of the proposal remains blocked pending explicit owner approval. The secret scan continues to fail; no protection has been bypassed and no history has been rewritten.
