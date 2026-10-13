"""Preserve prior raw attempts verbatim in verified archives; leave current results reviewable."""
import hashlib, json, shutil, tarfile
from pathlib import Path

root = Path(__file__).resolve().parents[2]/'verification/runs/20261013-IQA-d877a15'
out = root/'archive'
out.mkdir(exist_ok=True)
records=[]
for number in range(1,8):
    folder=root/f'attempt{number}'
    archive=out/(folder.name+'.tar.gz')
    assert folder.is_dir() and not archive.exists(), 'Refuse missing source or overwrite'
    files=sorted(p for p in folder.rglob('*') if p.is_file())
    assert files and all(not p.is_symlink() for p in files)
    digests={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
    with tarfile.open(archive,'w:gz') as tar:
        for p in files:tar.add(p,arcname=str(p.relative_to(root)),recursive=False)
    with tarfile.open(archive,'r:gz') as tar:
        members=tar.getmembers()
        assert len(members)==len(files)
        for m in members:
            assert m.isfile() and not m.name.startswith('/') and '..' not in Path(m.name).parts
            assert hashlib.sha256(tar.extractfile(m).read()).hexdigest()==digests[m.name]
    records.append({'source':folder.name,'archive':str(archive.relative_to(root)),'sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'files':len(files),'members':[{'file':name,'sha256':digest} for name,digest in digests.items()]})
    # Removal is limited to this own, verified, wholly preserved attempt snapshot.
    shutil.rmtree(folder)
(out/'manifest.json').write_text(json.dumps(records,indent=2)+'\n')
print(json.dumps({'archives':len(records),'preservedFiles':sum(r['files'] for r in records),'verified':True}))
