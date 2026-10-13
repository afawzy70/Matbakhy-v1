"""Isolated local services and real password+TOTP Keycloak authentication for QA."""
from pathlib import Path
import secrets
import base64
import json

ROOT=Path(__file__).resolve().parents[2]
STATE=Path('/workspace/qa-tools/regression-d877a15/stack')
STATE.mkdir(parents=True,exist_ok=True)
if (STATE/'.env').exists():raise SystemExit('Refusing to overwrite existing QA credentials')
values=dict(l.split('=',1) for l in (ROOT/'infra/.env.example').read_text().splitlines() if l and not l.startswith('#'))
values.update(COMPOSE_PROJECT_NAME='matbakhy-regression-d877a15',POSTGRES_PORT='36432',IDENTITY_PORT='39180',OBJECT_STORAGE_PORT='39990',MAIL_UI_PORT='39025',MAIL_SMTP_PORT='32025',API_PORT='39080')
for k in ['POSTGRES_PASSWORD','KEYCLOAK_ADMIN_PASSWORD','OIDC_WEB_CLIENT_SECRET','DEV_USER_PASSWORD']:values[k]=secrets.token_hex(24)
(STATE/'.env').write_text(''.join(f'{k}={v}\n' for k,v in values.items()));(STATE/'.env').chmod(0o600)
realm=json.loads((ROOT/'infra/identity/matbakhy-realm.json').read_text())
roles=['customer-a','customer-b','cook-a','cook-b','cook-new','ops-a','ops-review','support-a','finance-a','finance-b','config-a']
original={u['username']:u for u in realm['users']}
ids={};otp={}
for i,name in enumerate(roles,1):
 uid=original.get(name,{}).get('id',f'00000000-0000-4000-8000-{700+i:012x}');ids[name]=uid
 if name not in original:
  realm['users'].append(dict(id=uid,username=name,email=name+'@matbakhy.test',emailVerified=True,enabled=True,firstName='QA',lastName=name,credentials=[dict(type='password',value='${DEV_USER_PASSWORD}',temporary=False)]))
for u in realm['users']:
 if u['username'].startswith(('ops-','support-','finance-','config-')):
  key=base64.b32encode(secrets.token_bytes(20)).decode().rstrip('=');otp[u['username']]=key
  u['credentials'].append(dict(type='otp',userLabel='Independent QA TOTP',secretData=json.dumps({'value':key}),credentialData=json.dumps({'subType':'totp','digits':6,'counter':0,'period':30,'algorithm':'HmacSHA1'})))
# Real Keycloak password and OTP execution. No fabricated ACR, claims or application security changes.
realm['browserFlow']='qa-browser'
realm['authenticatorConfig']=[{'alias':'qa-pwd-amr','config':{'default.reference.value':'pwd'}},{'alias':'qa-otp-amr','config':{'default.reference.value':'otp','default.reference.maxAge':'600'}}]
def flow(alias,top,executions):return dict(alias=alias,description='Independent local QA real authentication',providerId='basic-flow',topLevel=top,builtIn=False,authenticationExecutions=executions)
realm['authenticationFlows']=[
 flow('qa-browser',True,[dict(authenticator='auth-cookie',requirement='ALTERNATIVE',priority=10,authenticatorFlow=False),dict(flowAlias='qa-forms',authenticatorFlow=True,requirement='ALTERNATIVE',priority=20)]),
 flow('qa-forms',False,[dict(authenticator='auth-username-password-form',authenticatorConfig='qa-pwd-amr',requirement='REQUIRED',priority=10,authenticatorFlow=False),dict(flowAlias='qa-otp',authenticatorFlow=True,requirement='CONDITIONAL',priority=20)]),
 flow('qa-otp',False,[dict(authenticator='conditional-user-configured',requirement='REQUIRED',priority=10,authenticatorFlow=False),dict(authenticator='auth-otp-form',authenticatorConfig='qa-otp-amr',requirement='REQUIRED',priority=20,authenticatorFlow=False)])]
for client in realm['clients']:
 client['protocolMappers'].append(dict(name='qa-real-amr',protocol='openid-connect',protocolMapper='oidc-amr-mapper',config={'id.token.claim':'true','access.token.claim':'true'}))
 if client['clientId']=='matbakhy-web':client['attributes']['post.logout.redirect.uris']='http://localhost:36173/##http://localhost:36174/'
text=json.dumps(realm)
for k in ['DEV_USER_PASSWORD','OIDC_WEB_CLIENT_SECRET','API_PORT']:text=text.replace('${'+k+'}',values[k])
(STATE/'generated/identity').mkdir(parents=True,exist_ok=True)
(STATE/'generated/identity/matbakhy-realm.json').write_text(text)
(STATE/'credentials.json').write_text(json.dumps({'password':values['DEV_USER_PASSWORD'],'otp':otp,'subjects':ids}));(STATE/'credentials.json').chmod(0o600)
(STATE/'override.yaml').write_text('''services:
  api:
    environment:
      WEB_ALLOWED_ORIGINS: http://localhost:36173,http://localhost:36174
      WEB_POST_LOGIN_REDIRECT: http://localhost:36173/
      NO_PROXY: localhost,127.0.0.1,object-storage,identity,postgres,mail
      no_proxy: localhost,127.0.0.1,object-storage,identity,postgres,mail
      JAVA_TOOL_OPTIONS: "-Dhttp.nonProxyHosts=localhost|127.*|object-storage|identity|postgres|mail"
    healthcheck:
      test: ["CMD-SHELL", "wget -Y off -qO- http://127.0.0.1:8080/actuator/health | grep -q UP"]
''')
print('Isolated QA realm/settings generated; secret values remain outside git.')
