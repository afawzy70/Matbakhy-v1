import datetime, hashlib, json, pathlib, subprocess, time, urllib.request, urllib.error, urllib.parse
ROOT=pathlib.Path('/home/aiagent/workspaces/agents/security')
OUT=ROOT/'docs/security/audits/current-stabilization/evidence'
D='167-86-90-174.sslip.io'
class NoRedirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,*args,**kwargs): return None
opener=urllib.request.build_opener(NoRedirect())
auth=f'https://id.{D}/realms/matbakhy/protocol/openid-connect/auth'
base={'client_id':'matbakhy-web','response_type':'code','scope':'openid','state':'security-audit-inert-state','redirect_uri':f'https://api.{D}/login/oauth2/code/keycloak'}
tests=[('oidc-invalid-redirect',auth+'?'+urllib.parse.urlencode({**base,'redirect_uri':'https://security-audit.invalid/callback','code_challenge_method':'S256','code_challenge':'a'*43})),('oidc-no-pkce',auth+'?'+urllib.parse.urlencode(base)),('login-initiation',f'https://api.{D}/oauth2/authorization/keycloak?returnTo=https%3A%2F%2Fsecurity-audit.invalid%2F'),('private-bucket',f'https://media.{D}/matbakhy-private'),('private-guessed-object',f'https://media.{D}/matbakhy-private/documents/00000000-0000-0000-0000-000000000000')]
results=[]
for name,url in tests:
 row={'name':name,'url':url,'timestamp':datetime.datetime.now(datetime.timezone.utc).isoformat()}
 try:
  try:r=opener.open(urllib.request.Request(url,headers={'User-Agent':'Matbakhy-Security-Audit'}),timeout=15)
  except urllib.error.HTTPError as e:r=e
  with r:
   body=r.read(65536); row.update(status=r.code,body_sha256=hashlib.sha256(body).hexdigest(),body_bytes=len(body))
   row['cookies']=[{'name':c.split('=',1)[0],'secure':'; secure' in c.lower(),'httponly':'; httponly' in c.lower(),'samesite':next((x.strip() for x in c.split(';') if x.strip().lower().startswith('samesite=')),None)} for c in r.headers.get_all('Set-Cookie',[])]
   if r.headers.get('Location'):
    loc=urllib.parse.urlparse(r.headers['Location']); params=urllib.parse.parse_qs(loc.query)
    row['redirect']={'origin':loc.scheme+'://'+loc.netloc,'path':loc.path,'client_id':params.get('client_id'),'redirect_uri':params.get('redirect_uri'),'has_state':'state' in params,'has_nonce':'nonce' in params,'code_challenge_method':params.get('code_challenge_method'),'has_code_challenge':'code_challenge' in params}
 except Exception as e:row['error']=type(e).__name__+': '+str(e)
 results.append(row); print(name,row.get('status',row.get('error')),flush=True);time.sleep(.5)
(OUT/'dast-followup.json').write_text(json.dumps(results,indent=2)+'\n')
meta={'hostname':subprocess.check_output(['hostname'],text=True).strip(),'audited_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'integration_head_observed':subprocess.check_output(['git','rev-parse','origin/stabilize/integration-2026-10-18'],cwd=ROOT,text=True).strip(),'timestamp':datetime.datetime.now(datetime.timezone.utc).isoformat(),'docker_socket_access':'DENIED; no sudo or service changes attempted','environment_status_tool':'NOT AVAILABLE','network_policy_snapshot':'/etc/codex/network-policy.json absent; inherited networking retained'}
(OUT/'provenance.json').write_text(json.dumps(meta,indent=2)+'\n')
ss=subprocess.check_output(['ss','-lnt'],text=True)
# Do not persist VPN or private addresses; capture public/loopback listening port numbers only.
listeners=[]
for line in ss.splitlines()[1:]:
 fields=line.split(); endpoint=fields[3]
 if endpoint.startswith(('0.0.0.0:','127.0.0.1:','[::]:')):listeners.append({'scope':'loopback' if endpoint.startswith('127.') else 'all_interfaces','port':int(endpoint.rsplit(':',1)[1])})
(OUT/'host-listening-ports.json').write_text(json.dumps(listeners,indent=2)+'\n')
