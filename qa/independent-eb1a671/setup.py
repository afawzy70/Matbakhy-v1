"""Create local-only QA settings, never product settings. Secrets stay outside git."""
import json
import os
from pathlib import Path
import secrets

root = Path(__file__).resolve().parents[2]
state = Path(os.environ.get('IQA_STATE', '/workspace/qa-tools/stack'))
state.mkdir(parents=True, exist_ok=True)
if (state / '.env').exists():
    raise SystemExit('Existing QA settings found; refusing to overwrite local credentials. Use the existing stack or remove only this owned stack first.')
values = dict(line.split('=', 1) for line in (root / 'infra/.env.example').read_text().splitlines()
              if line and not line.startswith('#'))
values.update(COMPOSE_PROJECT_NAME='matbakhy-iqa-eb1a671', POSTGRES_PORT='25432',
              IDENTITY_PORT='28180', OBJECT_STORAGE_PORT='29090', MAIL_UI_PORT='28025',
              MAIL_SMTP_PORT='21025', API_PORT='28080')
for key in ['POSTGRES_PASSWORD', 'KEYCLOAK_ADMIN_PASSWORD', 'OIDC_WEB_CLIENT_SECRET', 'DEV_USER_PASSWORD']:
    values[key] = secrets.token_hex(24)
env = state / '.env'
env.write_text(''.join(f'{k}={v}\n' for k, v in values.items()))
env.chmod(0o600)
realm = (root / 'infra/identity/matbakhy-realm.json').read_text()
for key in ['OIDC_WEB_CLIENT_SECRET', 'DEV_USER_PASSWORD', 'API_PORT']:
    realm = realm.replace('${' + key + '}', values[key])
(state / 'generated/identity').mkdir(parents=True, exist_ok=True)
(state / 'generated/identity/matbakhy-realm.json').write_text(realm)
(state / 'override.yaml').write_text('''services:
  api:
    environment:
      WEB_ALLOWED_ORIGINS: http://localhost:25173
      WEB_POST_LOGIN_REDIRECT: http://localhost:25173/
''')
print('Generated isolated settings; project matbakhy-iqa-eb1a671; web 25173; API 28080; DB 25432.')
