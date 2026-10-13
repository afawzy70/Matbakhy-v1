const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{execFileSync}=require('node:child_process');
const {root,login}=require('./auth.cjs');
const {chromium,expect}=require(path.join(root,'apps/operations-web/node_modules/@playwright/test'));
const out=path.join(root,'verification/runs/20261013-IQA-d877a15/evidence');fs.mkdirSync(out,{recursive:true});
const f=JSON.parse(fs.readFileSync(path.join(out,'fixtures.json')));
const API='http://localhost:39080',CUSTOMER='http://localhost:36173',OPS='http://localhost:36174';
const SHA='d877a1576cd942200fe0a53b5a19fee76b996025';
function clean(v,key=''){
 if(v==null)return v;
 if(['key','idempotencyKey'].includes(key)&&typeof v==='string'&&/[0-9a-f]{8}-[0-9a-f]{4}-/i.test(v))return '[REDACTED IDEMPOTENCY KEY]';
 if(/password|secret|token|phone|address|private.*url|uploadUrl|idpLogoutUrl|handoverCode|contact|pickupLocation|privateLocation|recipientName|^street$|^building$|^floor$|^unit$|^landmark$/i.test(key))return '[REDACTED]';
 if(key==='code'&&typeof v==='string'&&/^\d{6}$/.test(v))return '[REDACTED]';
 if(typeof v==='string')return v.replace(/\+20\d{10}/g,'[REDACTED PHONE]').replace(/LOCAL_FIXTURE_ONLY/g,'[REDACTED LOCATION]').replace(/(?<!MB-)\b\d{6}\b/g,'[REDACTED SIX-DIGIT VALUE]');
 if(Array.isArray(v))return v.map(x=>clean(x,key));
 if(typeof v==='object')return Object.fromEntries(Object.entries(v).map(([k,x])=>[k,clean(x,k)]));return v;
}
class Harness{
 constructor(){this.results=[];this.network=[];this.console=[];this.actors={};this.current=null;this.shared={};}
 async start(){this.browser=await chromium.launch({executablePath:'/usr/bin/chromium',headless:true,args:['--no-sandbox']});}
 async open(role,staff=false,fresh=false){
  if(this.actors[role]&&!fresh){await this.english(this.actors[role].page);return this.actors[role];}
  const origin=staff?OPS:CUSTOMER,state='/workspace/qa-tools/regression-d877a15/'+role+'-state.json';let x;
  if(fs.existsSync(state)&&!fresh){const context=await this.browser.newContext({storageState:state,viewport:{width:1440,height:1000}});const response=await context.request.get(API+'/api/v1/me');if(response.status()===200){x={context,page:await context.newPage(),profile:await response.json()};await x.page.goto(origin);}else await context.close();}
  if(!x)x=await login(this.browser,role,origin);
  x.page.setDefaultTimeout(9000);x.role=role;x.staff=staff;
  x.page.on('pageerror',e=>this.console.push({case:this.current?.id,role,type:'pageerror',text:clean(e.message)}));
  x.page.on('console',m=>{if(['error','warning'].includes(m.type()))this.console.push({case:this.current?.id,role,type:m.type(),text:clean(m.text())});});
  x.page.on('response',async r=>{if(r.url().startsWith(API+'/api/v1/')){const n={case:this.current?.id,role,method:r.request().method(),path:r.url().slice(API.length),status:r.status(),language:r.request().headers()['accept-language']};try{n.body=clean(await r.json())}catch{}this.network.push(n);}});
  this.actors[role]=x;await this.english(x.page);return x;
 }
 async english(p){await p.getByRole('button',{name:/^(English|العربية)$/}).first().waitFor({state:'visible'});if(await p.getByRole('button',{name:'English',exact:true}).count())await p.getByRole('button',{name:'English',exact:true}).click();await expect(p.locator('html')).toHaveAttribute('lang','en');}
 check(name,pass,actual,expected){this.current.checks.push({name,status:pass?'PASS':'FAIL',expected:clean(expected),actual:clean(actual)});return pass;}
 blocked(name,reason){this.current.checks.push({name,status:'BLOCKED',reason});}
 async case(id,actor,requirement,fn){this.current={id,actor,requirement,applicationSha:SHA,testSuiteSha:execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim(),environment:'matbakhy-regression-d877a15',runtime:'Chromium real local API/PostgreSQL/Keycloak',variation:'en/1440x1000 unless explicit parameter checks',startedAt:new Date().toISOString(),checks:[],evidence:[]};try{await fn();}catch(e){this.current.exception=clean(String(e.message).replace(/https?:\/\/[^\s"]+/g,url=>{try{const u=new URL(url);return u.origin+u.pathname}catch{return '[URL]'}}));this.blocked('Execution stopped; reproduce before defect classification',this.current.exception.slice(0,1600));}
  this.current.status=this.current.checks.some(x=>x.status==='FAIL')?'FAIL':this.current.checks.some(x=>x.status==='BLOCKED')?'BLOCKED':this.current.checks.length?'PASS':'NOT_RUN';this.current.finishedAt=new Date().toISOString();this.current.variations=Object.values(this.actors).map(x=>({role:x.role,viewport:x.page.viewportSize()}));this.results.push(this.current);this.persist();console.log(id,this.current.status,this.current.checks.filter(c=>c.status!=='PASS').map(c=>c.name));}
 persist(){fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({applicationSha:SHA,results:this.results},null,2)+'\n');fs.writeFileSync(path.join(out,'network.json'),JSON.stringify(this.network,null,2)+'\n');fs.writeFileSync(path.join(out,'console.json'),JSON.stringify(this.console,null,2)+'\n');}
 async shot(p,name){await p.screenshot({path:path.join(out,name+'.png'),fullPage:true,mask:[p.locator('.handover'),p.locator('p').filter({hasText:/\+20\d{10}|LOCAL_FIXTURE_ONLY/}),p.locator('input:not([type=number]):not([type=radio]):not([type=checkbox])')]});const data=await p.evaluate(()=>({path:location.pathname+location.hash,lang:document.documentElement.lang,dir:document.documentElement.dir,width:innerWidth,scrollWidth:document.documentElement.scrollWidth,text:document.body.innerText,cart:JSON.parse(localStorage.getItem('matbakhy.cart')||'null'),pending:!!localStorage.getItem('matbakhy.pendingPlacement')}));fs.writeFileSync(path.join(out,name+'.json'),JSON.stringify(clean(data),null,2)+'\n');this.current.evidence.push(name+'.png',name+'.json');return data;}
 async api(x,method,route,body,key){const cookie=(await x.context.cookies(API)).find(c=>c.name==='XSRF-TOKEN');const headers={'Accept-Language':'en',...(cookie?{'X-XSRF-TOKEN':decodeURIComponent(cookie.value)}:{}),...(key?{'Idempotency-Key':key}:{})};const r=await x.context.request.fetch(API+route,{method,headers,...(body!==undefined?{data:body}:{})});let data;try{data=await r.json()}catch{data=null}this.network.push({case:this.current?.id,role:x.role,method,path:route,status:r.status(),body:clean(data)});return {status:r.status(),data};}
 db(statement){return execFileSync('docker',['compose','--env-file','/workspace/qa-tools/regression-d877a15/stack/.env','-f',path.join(root,'infra/compose.yaml'),'-f','/workspace/qa-tools/regression-d877a15/stack/override.yaml','exec','-T','postgres','psql','-U','matbakhy','-d','matbakhy','-tA','-v','ON_ERROR_STOP=1','-c',statement],{encoding:'utf8'}).trim();}
 key(){return 'iqa-'+crypto.randomUUID();}
 async nav(p,route){await p.evaluate(r=>{location.hash=r},route);}
 async reset(x){const pending=await x.page.evaluate(()=>!!localStorage.getItem('matbakhy.pendingPlacement'));if(pending)throw new Error('Refusing to clear unresolved pending order');await x.page.evaluate(()=>localStorage.removeItem('matbakhy.cart'));await this.nav(x.page,'/');}
 async meal(x,key){await this.nav(x.page,'/meal/'+f.meals[key].id);await expect(x.page.locator('#meal-title')).toHaveText(f.meals[key].titleEn);}
 async add(x,key,slotKey,quantity=1){await this.meal(x,key);const m=(await this.api(x,'GET','/api/v1/meals/'+f.meals[key].id)).data;const i=m.nextSlots.findIndex(s=>s.id===f.slots[slotKey].id);if(i<0)throw new Error('Fixture slot missing: '+slotKey);await x.page.getByRole('radio').nth(i).check();await x.page.getByRole('spinbutton',{name:'Quantity',exact:true}).fill(String(quantity));await x.page.getByRole('button',{name:/^Add to cart/}).click();}
 async cart(x){return x.page.evaluate(()=>JSON.parse(localStorage.getItem('matbakhy.cart')||'null'));}
 async estimate(x,minor){const text=await x.page.getByText('Estimated subtotal',{exact:true}).locator('xpath=following-sibling::dd[1]').innerText();this.check('Visible estimated subtotal matches integer arithmetic',text.includes((minor/100).toFixed(2)),text,{amountMinor:minor,currency:'EGP'});}
 async quote(x,mode='PICKUP'){await this.nav(x.page,'/checkout');await x.page.getByRole('button',{name:mode==='PICKUP'?'Pickup from the kitchen':'Delivery',exact:true}).click();const wait=x.page.waitForResponse(r=>r.url()===API+'/api/v1/quotes'&&r.request().method()==='POST');await x.page.getByRole('button',{name:'Calculate total',exact:true}).click();const r=await wait;const data=await r.json();return {status:r.status(),data};}
 async place(x,q){await x.page.getByRole('checkbox',{name:'I accept the terms and the cancellation policy',exact:true}).check();const wait=x.page.waitForResponse(r=>r.url()===API+'/api/v1/orders'&&r.request().method()==='POST');await x.page.getByRole('button',{name:'Place order',exact:true}).click();const r=await wait;return {status:r.status(),data:await r.json()};}
 async createOrderApi(x,key,slot,quantity=1,mode='PICKUP',addressId){let q=await this.api(x,'POST','/api/v1/quotes',{items:[{mealId:f.meals[key].id,quantity}],slotId:f.slots[slot].id,fulfillmentMode:mode,...(addressId?{addressId}:{})});if(q.status!==201)throw new Error('Order fixture quote failed: '+q.status+' '+q.data?.code);const requestId=crypto.randomUUID(),idkey=this.key();const versions={terms:q.data.termsVersion,cancellation:q.data.cancellationPolicyVersion};const o=await this.api(x,'POST','/api/v1/orders',{quoteId:q.data.quoteId,clientRequestId:requestId,acceptedPolicyVersions:versions},idkey);if(o.status!==201)throw new Error('Order fixture placement failed: '+o.status+' '+o.data?.code);return {q:q.data,o:o.data,requestId,idkey};}
 async finish(){if(!this.results.length)throw Error('Zero cases executed');this.persist();for(const x of Object.values(this.actors))await x.context.close();fs.writeFileSync(path.join(out,'runtime.json'),JSON.stringify({applicationSha:SHA,browser:this.browser.version(),mode:'REPORT_ONLY',web:CUSTOMER,staffWeb:OPS,api:API,finishedAt:new Date().toISOString()},null,2)+'\n');await this.browser.close();}
}
module.exports={Harness,expect,f,API,CUSTOMER,OPS,SHA,root,out,clean};
