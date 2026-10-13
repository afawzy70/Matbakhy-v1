"""Local CI-only fixture control; loopback, synthetic DB only. No application auth/rule bypass.
Independent native tests call this to lower an explicitly synthetic policy in the same
running session and to observe committed-but-undelivered order responses. No secrets logged.
"""
import http.server,json,os,subprocess,sys
from pathlib import Path
if sys.platform=='darwin':
 pg=subprocess.check_output(['brew','--prefix','postgresql@16'],text=True).strip();cmd=[pg+'/bin/psql','-h','localhost','-U','matbakhy','-d','matbakhy']
else:cmd=['docker','compose','--env-file','infra/.env','-f','infra/compose.yaml','exec','-T','postgres','psql','-U','matbakhy','-d','matbakhy']
def query(sql):return subprocess.check_output(cmd+['-tA','-v','ON_ERROR_STOP=1','-c',sql],text=True).strip()
class Handler(http.server.BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_GET(self):
  data={};status=200
  if self.path=='/lower':
   assert query("SELECT count(*) FROM commerce_policy WHERE status='ACTIVE' AND synthetic")=='1'
   query("UPDATE commerce_policy SET max_item_quantity=5 WHERE status='ACTIVE' AND synthetic");data={'limit':5}
  elif self.path=='/restore':query("UPDATE commerce_policy SET max_item_quantity=10 WHERE status='ACTIVE' AND synthetic");data={'limit':10}
  elif self.path=='/seed-case':
   query("INSERT INTO support_case(id,reference,order_id,customer_id,category,severity,state,description,sla_due_at,created_at,updated_at) SELECT '00000000-0000-4000-8000-00000000a077','CS-DA877001',o.id,o.customer_id,'QUALITY','NORMAL','OPEN','Synthetic native support reply fixture',now()+interval '1 day',now(),now() FROM customer_order o JOIN app_user u ON u.id=o.customer_id WHERE u.display_name LIKE '%SyntheticA%' ORDER BY o.created_at LIMIT 1 ON CONFLICT DO NOTHING")
   query("INSERT INTO case_message(id,case_id,author_id,author_role,visibility,body,created_at) SELECT '00000000-0000-4000-8000-00000000a078',c.id,k.owner_id,'STAFF','PUBLIC','QA PUBLIC NATIVE REPLY',now() FROM support_case c JOIN customer_order o ON o.id=c.order_id JOIN kitchen k ON k.id=o.kitchen_id WHERE c.reference='CS-DA877001' ON CONFLICT DO NOTHING")
   query("INSERT INTO case_message(id,case_id,author_id,author_role,visibility,body,created_at) SELECT '00000000-0000-4000-8000-00000000a079',c.id,k.owner_id,'STAFF','STAFF_ONLY','QA PRIVATE NATIVE NOTE',now() FROM support_case c JOIN customer_order o ON o.id=c.order_id JOIN kitchen k ON k.id=o.kitchen_id WHERE c.reference='CS-DA877001' ON CONFLICT DO NOTHING")
   data={'reference':'CS-DA877001'}
  elif self.path=='/pending':
   flag=Path(os.environ['RUNNER_TEMP'])/'iqa-held-order.json';data=json.loads(flag.read_text()) if flag.exists() else {'held':False}
   data['orders']=int(query("SELECT count(*) FROM customer_order o JOIN app_user u ON u.id=o.customer_id WHERE u.display_name LIKE '%SyntheticA%'"))
  else:status=404
  self.send_response(status);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(json.dumps(data).encode())
http.server.HTTPServer(('127.0.0.1',9099),Handler).serve_forever()
