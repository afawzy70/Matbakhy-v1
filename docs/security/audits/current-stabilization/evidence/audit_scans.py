"""Temporary isolated scanner orchestration; never modifies application files."""
import concurrent.futures, datetime, hashlib, json, os, pathlib, subprocess, tarfile, urllib.request
ROOT=pathlib.Path('/home/aiagent/workspaces/agents/security')
OUT=ROOT/'docs/security/audits/current-stabilization/evidence'
TMP=pathlib.Path('/tmp/matbakhy-security-downloads'); TMP.mkdir(exist_ok=True)
OUT.mkdir(parents=True,exist_ok=True)
def run(name,args,timeout=600,env=None):
    try:
        p=subprocess.run(args,cwd=ROOT,capture_output=True,text=True,timeout=timeout,env=env)
        (OUT/(name+'.stdout')).write_text(p.stdout)
        (OUT/(name+'.stderr')).write_text(p.stderr)
        result={'command':args,'exit_code':p.returncode,'timestamp':datetime.datetime.now(datetime.timezone.utc).isoformat()}
    except Exception as e: result={'command':args,'error':str(e)}
    (OUT/(name+'.execution.json')).write_text(json.dumps(result,indent=2)+'\n')
    print(name,result,flush=True)
    return result
def binary(repo,tool,suffix):
    request=urllib.request.Request('https://api.github.com/repos/'+repo+'/releases/latest',headers={'User-Agent':'Matbakhy-Security-Audit'})
    release=json.load(urllib.request.urlopen(request,timeout=30))
    asset=next(a for a in release['assets'] if a['name'].endswith(suffix))
    archive=TMP/asset['name']
    urllib.request.urlretrieve(asset['browser_download_url'],archive)
    sums=next(a for a in release['assets'] if a['name'].endswith('checksums.txt'))
    checks=urllib.request.urlopen(sums['browser_download_url'],timeout=30).read().decode()
    expected=next(line.split()[0] for line in checks.splitlines() if line.split()[-1].lstrip('*')==asset['name'])
    actual=hashlib.sha256(archive.read_bytes()).hexdigest()
    if expected!=actual: raise RuntimeError('Checksum mismatch')
    with tarfile.open(archive) as tf:
        member=next(m for m in tf.getmembers() if m.name==tool)
        (TMP/tool).write_bytes(tf.extractfile(member).read())
    (TMP/tool).chmod(0o700)
    (OUT/(tool+'-tool.json')).write_text(json.dumps({'release':release['tag_name'],'asset':asset['name'],'sha256':actual,'checksum_source':sums['browser_download_url']},indent=2)+'\n')
    return str(TMP/tool)
def semgrep():
    env=dict(os.environ,SEMGREP_SEND_METRICS='off',SEMGREP_ENABLE_VERSION_CHECK='0')
    run('semgrep',['/tmp/matbakhy-security-tools/bin/semgrep','scan','--config','p/security-audit','--config','p/owasp-top-ten','--metrics=off','--disable-version-check','--json','--output',str(OUT/'semgrep.json'),'--exclude','docs','--exclude','verification','--exclude','planning','--exclude','pnpm-lock.yaml','.'],env=env)
def gitleaks():
    try:
        exe=binary('gitleaks/gitleaks','gitleaks','linux_x64.tar.gz')
        run('gitleaks',[exe,'dir','--redact=100','--report-format','json','--report-path',str(OUT/'gitleaks.json'),'--no-banner',str(ROOT)],300)
    except Exception as e: (OUT/'gitleaks-install.json').write_text(json.dumps({'error':str(e)}))
def trivy():
    try:
        exe=binary('aquasecurity/trivy','trivy','Linux-64bit.tar.gz')
        run('trivy-fs',[exe,'fs','--scanners','vuln,misconfig','--cache-dir',str(TMP/'trivy-cache'),'--format','json','--output',str(OUT/'trivy.json'),'--skip-dirs','docs,verification,.git','--timeout','8m',str(ROOT)],540)
    except Exception as e: (OUT/'trivy-install.json').write_text(json.dumps({'error':str(e)}))
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    list(pool.map(lambda f:f(),[semgrep,gitleaks,trivy]))
