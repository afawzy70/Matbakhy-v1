"""Synthetic QA catalogue; only touches the dedicated IQA compose database."""
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import urllib.request
import uuid
import zlib

root = Path(__file__).resolve().parents[2]
state = Path('/workspace/qa-tools/cross-role/stack')
creds=json.loads((state/'credentials.json').read_text())
def uid(n): return str(uuid.UUID(int=n))
def q(v): return "'" + str(v).replace("'", "''") + "'"
def chunk(k, b):
    return struct.pack('!I', len(b)) + k + b + struct.pack('!I', zlib.crc32(k+b))
image = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('!IIBBBBB',800,600,8,2,0,0,0)) + chunk(b'IDAT', zlib.compress((b'\x00'+bytes([180,90,50])*800)*600)) + chunk(b'IEND',b'')
(state.parent/'solid.png').write_bytes(image)
urllib.request.urlopen(urllib.request.Request('http://localhost:39090/matbakhy-public/iqa.png', data=image, method='PUT', headers={'Content-Type':'image/png'})).read()
sql=['BEGIN;']
actors={}
for i,(role,subject) in enumerate(creds['subjects'].items(),1):
    ident=uid(700+i);actors[role]=ident
    sql.append(f"INSERT INTO app_user(id,issuer,subject,display_name,email_verified) VALUES ({q(ident)},'http://localhost:38180/realms/matbakhy',{q(subject)},{q('QA '+role)},true);")
permissions={'ops-a':['staff.grant','audit.read','config.read','config.write'],'ops-review':['cook.review','content.moderate','order.ops','order.exception_cancel','order.handover_override'],'support-a':['support.case'],'finance-a':['finance.read','finance.verify','finance.refund_approve','finance.settlement'],'finance-b':['finance.read','finance.verify','finance.refund_approve','finance.settlement'],'config-a':['config.read','config.write','integration.ops']}
for role,grants in permissions.items():
    for perm in grants:
        sql.append(f"INSERT INTO user_grant(id,user_id,permission,granted_by,grant_reason) VALUES (gen_random_uuid(),{q(actors[role])},{q(perm)},{q(actors['ops-a'])},'Independent QA initial synthetic capability');")
for role in ['customer-a','customer-b']:
    sql.append(f"UPDATE app_user SET phone_e164={q('+'+str(20)+str(1000000000 + (1 if role=='customer-a' else 2)))},phone_verified_at=now() WHERE id={q(actors[role])};")

fixtures={'kitchens':{},'meals':{},'slots':{},'actors':actors,'subjects':creds['subjects'],'initialPermissions':permissions}
# Local fake contact fields are generated, never included in saved API evidence.
phone = '+' + str(20) + str(1000000000)
for n,label in [(1,'A'),(2,'B'),(3,'PAUSED')]:
    u,a,k=actors['cook-a' if n==1 else 'cook-b' if n==2 else 'cook-new'],uid(200+n),uid(300+n)
    
    sql.append(f"INSERT INTO cook_application(id,user_id,state) VALUES ({q(a)},{q(u)},'APPROVED');")
    sql.append(f"INSERT INTO cook_application_revision(application_id,revision,public_name,bio,contact_phone,private_location,pickup_enabled,zone_ids,submitted_at) VALUES ({q(a)},1,{q('QA Kitchen '+label)},'Synthetic QA kitchen',{q(phone)},'LOCAL_FIXTURE_ONLY',true,'{{}}',now());")
    sql.append(f"INSERT INTO kitchen(id,owner_id,application_id,approved_revision,public_slug,display_name,description,pickup_enabled,contact_phone,private_location,operating_state) VALUES ({q(k)},{q(u)},{q(a)},1,{q('qa-kitchen-'+label.lower())},{q('مطبخ تجريبي '+label)},'Synthetic independent QA kitchen',true,{q(phone)},'LOCAL_FIXTURE_ONLY',{q('PAUSED' if n==3 else 'ACTIVE')});")
    sql.append(f"INSERT INTO kitchen_zone(kitchen_id,zone_id) SELECT {q(k)},id FROM service_zone WHERE code LIKE 'test-zone-%';")
    fixtures['kitchens'][label]={'id':k,'slug':'qa-kitchen-'+label.lower(),'name':'مطبخ تجريبي '+label}
    for s,days,status in [(1,3,'OPEN'),(2,4,'OPEN'),(3,5,'CLOSED'),(4,-3,'OPEN'),(5,6,'OPEN')]:
        sid=uid(400+n*10+s)
        sql.append(f"INSERT INTO cook_slot(id,kitchen_id,local_date,starts_at,ends_at,lead_minutes,cutoff_at,max_portions,state) SELECT {q(sid)},{q(k)},(now()+interval '{days} days')::date,now()+interval '{days} days',now()+interval '{days} days 2 hours',120,now()+interval '{days} days -2 hours',50,{q(status)};")
        fixtures['slots'][label+str(s)]={'id':sid,'state':status,'daysFromSeed':days}
    media=uid(500+n)
    sql.append(f"INSERT INTO media_asset(id,owner_id,purpose,state,original_filename,declared_content_type,declared_bytes,declared_sha256,private_key,content_type,bytes,width,height,public_key,scan_verdict,ticket_expires_at) VALUES ({q(media)},{q(u)},'MEAL_PHOTO','APPROVED','iqa.png','image/png',{len(image)},{q(hashlib.sha256(image).hexdigest())},{q('iqa-private-'+label)},'image/png',{len(image)},800,600,'iqa.png'||{q(label)},'CLEAN',now()+interval '1 hour');")
    urllib.request.urlopen(urllib.request.Request('http://localhost:39090/matbakhy-public/iqa.png'+label,data=image,method='PUT',headers={'Content-Type':'image/png'})).read()
    for m in range(1,4 if n==1 else 2):
        mid=uid(600+n*10+m); key=label+str(m)
        price=12025 if key=='A1' else 9550 if key=='A2' else 8025
        ar='وجبة تجريبية '+key+(' عنوان عربي طويل لاختبار العرض في الهاتف والأرقام المختلطة' if key=='A2' else '')
        en='QA Meal '+key
        desc='وصف مستقل للوجبة '+key+' بمكونات تجريبية فقط. '+('وصف طويل للاختبار. '*70 if key=='A2' else '')
        de='Independent synthetic description for meal '+key+'. '+('Long description for QA. '*50 if key=='A2' else '')
        sql.append(f"INSERT INTO meal(id,kitchen_id,status,title_ar,title_en,description_ar,description_en,portion_ar,portion_en,ingredients_ar,ingredients_en,allergen_declaration,price_minor,published_at,category) VALUES ({q(mid)},{q(k)},'PUBLISHED',{q(ar)},{q(en)},{q(desc)},{q(de)},'حصة تجريبية','Synthetic portion','أرز تجريبي','Synthetic rice','UNKNOWN',{price},now(),'MAIN_DISHES');")
        snapshot=json.dumps({'titleAr':ar,'titleEn':en,'descriptionAr':desc,'descriptionEn':de,'portionAr':'حصة تجريبية','portionEn':'Synthetic portion','ingredientsAr':'أرز تجريبي','ingredientsEn':'Synthetic rice','allergenDeclaration':'UNKNOWN','allergenCodes':[],'priceMinor':price,'currency':'EGP'})
        sql.append(f"INSERT INTO meal_revision(meal_id,revision,snapshot,price_minor) VALUES ({q(mid)},1,{q(snapshot)},{price});")
        sql.append(f"INSERT INTO meal_media(meal_id,media_id,position,alt_ar,alt_en) VALUES ({q(mid)},{q(media)},0,'صورة تجريبية','Synthetic solid colour');")
        for s in range(1,6):
            capacity=0 if key=='A3' else (25 if s==5 else 3)
            sql.append(f"INSERT INTO meal_slot(id,cook_slot_id,meal_id,capacity) VALUES ({q(uid(1000+n*100+m*10+s))},{q(uid(400+n*10+s))},{q(mid)},{capacity});")
        fixtures['meals'][key]={'id':mid,'kitchenId':k,'titleAr':ar,'titleEn':en,'descriptionAr':desc,'descriptionEn':de,'priceMinor':price,'capacity':capacity}
sql.append('COMMIT;')
cmd=['docker','compose','--env-file',str(state/'.env'),'-f',str(root/'infra/compose.yaml'),'-f',str(state/'override.yaml'),'exec','-T','postgres','psql','-U','matbakhy','-d','matbakhy','-v','ON_ERROR_STOP=1']
subprocess.run(cmd,input='\n'.join(sql),text=True,check=True,stdout=subprocess.DEVNULL)
(root/'qa/independent-cross-role-4eaee3a/evidence').mkdir(exist_ok=True)
(root/'qa/independent-cross-role-4eaee3a/evidence/fixtures.json').write_text(json.dumps(fixtures,ensure_ascii=False,indent=2)+'\n')
print('Seeded synthetic A/B/paused kitchens, distinct meals and future/closed/past/sold-out slots.')
