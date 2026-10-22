"""Bounded anonymous preview probes. Stores metadata only; no cookies or response bodies."""
import datetime, hashlib, json, pathlib, time, urllib.request, urllib.error

OUT = pathlib.Path('/home/aiagent/workspaces/agents/security/docs/security/audits/current-stabilization/evidence')
OUT.mkdir(parents=True, exist_ok=True)
DOMAIN = '167-86-90-174.sslip.io'
tests = [(h, '/', 'GET', {}) for h in ['app','ops','api','id','media']]
tests += [('api', p, 'GET', {}) for p in [
 '/api/v1/configuration/public','/api/v1/zones','/api/v1/meals?limit=1',
 '/api/v1/me','/api/v1/orders','/api/v1/orders/00000000-0000-0000-0000-000000000000',
 '/api/v1/cook/orders','/api/v1/ops/orders','/api/v1/ops/staff',
 '/actuator/health','/actuator/env','/actuator/prometheus','/v3/api-docs','/swagger-ui/index.html',
 '/api/v1/meals?limit=-1','/api/v1/meals?limit=101','/api/v1/meals?cursor=%27%22%3Csecurity-audit%3E']]
tests += [('id', p, 'GET', {}) for p in ['/admin/','/realms/master/.well-known/openid-configuration','/realms/matbakhy/.well-known/openid-configuration']]
tests += [('api','/api/v1/orders','GET',{'Authorization':'Bearer security-audit-invalid-token'})]
tests += [('api','/api/v1/orders','OPTIONS',{'Origin':o,'Access-Control-Request-Method':'POST','Access-Control-Request-Headers':'content-type,x-xsrf-token'}) for o in ['https://security-audit.invalid',f'https://app.{DOMAIN}']]
# Invalid empty body, no session, no CSRF; cannot create a valid order.
tests += [('api','/api/v1/orders','POST',{'Content-Type':'application/json','Origin':'https://security-audit.invalid'})]

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs): return None

opener=urllib.request.build_opener(NoRedirect())
results=[]
for host,path,method,headers in tests:
    url=f'https://{host}.{DOMAIN}{path}'
    row={'timestamp':datetime.datetime.now(datetime.timezone.utc).isoformat(),'method':method,'url':url,'request_headers':headers}
    try:
        req=urllib.request.Request(url,headers={'User-Agent':'Matbakhy-Independent-Security-Audit/1.0',**headers},method=method,data=b'{}' if method=='POST' else None)
        try: response=opener.open(req,timeout=15)
        except urllib.error.HTTPError as e: response=e
        with response:
            body=response.read(131072)
            row.update(status=response.code,body_bytes=len(body),body_sha256=hashlib.sha256(body).hexdigest())
            row['headers']={k:v for k,v in response.headers.items() if k.lower() in ['content-type','strict-transport-security','x-frame-options','content-security-policy','x-content-type-options','referrer-policy','access-control-allow-origin','access-control-allow-credentials','cache-control','server','vary','www-authenticate']}
            row['cookies']=[{'name':c.split('=',1)[0],'secure':'; secure' in c.lower(),'httponly':'; httponly' in c.lower(),'samesite':next((x.strip() for x in c.split(';') if x.strip().lower().startswith('samesite=')),None)} for c in response.headers.get_all('Set-Cookie',[])]
            try:
                parsed=json.loads(body)
                if isinstance(parsed,dict):
                    row['json_keys']=sorted(parsed.keys())
                    if isinstance(parsed.get('code'),str): row['error_code']=parsed['code']
                    if host=='id' and 'issuer' in parsed: row['oidc_metadata']={k:parsed.get(k) for k in ['issuer','code_challenge_methods_supported','token_endpoint_auth_methods_supported']}
            except (ValueError,UnicodeError): pass
    except Exception as e: row['error']=type(e).__name__+': '+str(e)
    results.append(row)
    print(method,url,row.get('status',row.get('error')),flush=True)
    time.sleep(0.5)
(OUT/'dast-anonymous.json').write_text(json.dumps(results,indent=2)+'\n')
