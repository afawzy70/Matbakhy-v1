import {test} from 'node:test';
import assert from 'node:assert/strict';
import {matches, persisted, report, sanitize} from './core.mjs';

test('search combines Arabic, canonical ID, actor, readiness and result filters',()=>{
  const c={id:'CFG-001',title:'OIDC',guidance:{title:'تسجيل الدخول'},actors:['FX-GUEST'],surface:['identity'],domain:'config-security',guidance_complete:false};
  assert.equal(matches(c,'تسجيل','config-security','draft','BLOCKED',{'CFG-001':{status:'BLOCKED'}}),true);
  assert.equal(matches(c,'cfg-001'),true);
  assert.equal(matches(c,'FX-GUEST'),true);
  assert.equal(matches(c,'','orders-finance'),false);
  assert.equal(matches(c,'','','complete'),false);
});
test('persistent state allowlist strips credentials and narrative fields',()=>{
  const result=persisted({'CFG-001':{status:'PASS',password:'synthetic-private',evidence:'private',checks:['step:1'],updated:'2026-10-22'}});
  assert.deepEqual(Object.keys(result['CFG-001']).sort(),['checks','status','updated']);
  assert.equal(JSON.stringify(result).includes('private'),false);
  assert.equal(persisted({X:{status:'unknown'}}).X.status,'NOT_RUN');
});
test('reports redact all session credential history and disclose missing resumed evidence',()=>{
  const data={cases:[{id:'CFG-001',guidance_complete:false}],inputs:{x:'hash'},catalog_digest:'digest',source_sha:'base'};
  const result=report(data,{'CFG-001':{status:'PASS',checks:['step:1']}},{'CFG-001':{reason:'old-private and new-private',evidence:'Bearer dummy-token'}},['old-private','new-private'],{environment:'demo',release:''});
  assert.equal(result.results[0].evidence_state,'MISSING_EVIDENCE_OR_RELEASE');
  assert.equal(JSON.stringify(result).includes('old-private'),false);
  assert.equal(JSON.stringify(result).includes('dummy-token'),false);
  assert.equal(result.live_verification_by_workspace,false);
  assert.equal(result.catalog_digest,'digest');
  assert.equal(sanitize('password=synthetic-value'),'password=[REDACTED]');
  assert.equal(sanitize('demo-value', ['d','de','dem','demo-value']), '[REDACTED]');
  assert.equal(sanitize('a.*[b]', ['a','a.*[b]']), '[REDACTED]');
});
