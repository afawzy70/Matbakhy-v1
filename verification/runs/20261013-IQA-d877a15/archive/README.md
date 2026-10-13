# Preserved earlier attempts

These seven archives preserve every raw JSON/screenshot from earlier campaign attempts, including original failed/blocked assertions. They are not final PASS evidence. Final fresh FULL attempt8 remains unpacked in ../evidence. TARGETED probes and their correction history remain separately named.

Before packing, Gitleaks scanned248 nonzero history commits and52.89MB of committable source/evidence with no leaks; the original per-file privacy controls mask generated contacts, addresses, codes, tokens and nonce aliases. Archive generation verified every member is a regular relative file with no traversal/symlink, and compared every extracted content SHA256 to its original. [manifest.json](manifest.json) gives archive/member digests and file counts. No exclusions were added to the secret scanner. A later directory scan of gzip bytes cannot replace this prepacking scan of extracted text; this scope limitation is explicit.

To inspect, from verification/runs/20261013-IQA-d877a15:

```bash
python3 - <<'PY'
import hashlib,json,pathlib
for r in json.load(open('archive/manifest.json')):
    p=pathlib.Path(r['archive'])
    assert hashlib.sha256(p.read_bytes()).hexdigest()==r['sha256']
print('Archive checksums verified; prior outcomes unchanged')
PY
mkdir -p /tmp/matbakhy-iqa-prior-attempts
tar -xzf archive/attempt6.tar.gz -C /tmp/matbakhy-iqa-prior-attempts
```

Prior attempt results and suite SHAs are inside attemptN/results.json. Extract another named archive for its original screenshots/geometry/API evidence. Do not add these results to the current campaign denominator or use the mutated data to continue a full campaign.
