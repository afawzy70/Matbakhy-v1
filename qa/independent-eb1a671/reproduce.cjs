/* Targeted reproductions and coverage additions within the original ten cases. */
const fs=require('node:fs'),path=require('node:path'),{execFileSync}=require('node:child_process');
const root=path.resolve(__dirname,'../..'),out=path.join(__dirname,'evidence');
const {chromium,expect}=require(path.join(root,'apps/operations-web/node_modules/@playwright/test'));
const f=JSON.parse(fs.readFileSync(path.join(out,'fixtures.json')));
const api='http://localhost:28080',base='http://localhost:25173';
const evidence=[];
function sql(statement){return execFileSync('docker',['compose','--env-file','/workspace/qa-tools/stack/.env','-f',path.join(root,'infra/compose.yaml'),'-f','/workspace/qa-tools/stack/override.yaml','exec','-T','postgres','psql','-U','matbakhy','-d','matbakhy','-v','ON_ERROR_STOP=1','-c',statement],{encoding:'utf8'});}
(async()=>{
 const b=await chromium.launch({executablePath:'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
 let p;
 async function save(name,details){await p.screenshot({path:path.join(out,name+'.png'),fullPage:true});evidence.push({id:name,...details});}
 for(let i=1;i<=3;i++){
  const c=await b.newContext({viewport:{width:390,height:900}});p=await c.newPage();const requests=[];
  p.on('response',async r=>{if(r.url().includes('/api/v1/meals?'))requests.push({url:r.url(),status:r.status(),language:r.request().headers()['accept-language'],body:await r.json()})});
  await p.goto(base);await expect(p.locator('.food-card')).toHaveCount(4);await p.getByRole('button',{name:'English',exact:true}).click();await p.waitForLoadState('networkidle');
  await save('MBQA-001-repro-'+i,{requirement:'BR-046',expected:'English API content after selecting English',htmlLang:await p.locator('html').getAttribute('lang'),htmlDir:await p.locator('html').getAttribute('dir'),titles:await p.locator('.food-card h3').allTextContents(),requests});
  await c.close();
 }
 const c=await b.newContext({viewport:{width:390,height:900}});p=await c.newPage();
 await p.goto(base);await p.getByRole('button',{name:'English',exact:true}).click();await p.waitForLoadState('networkidle');
 const ms=f.meals.A1.id,s1=f.slots.A1.id;
 sql(`UPDATE meal_slot SET consumed=3 WHERE meal_id='${ms}' AND cook_slot_id='${s1}'; UPDATE cook_slot SET consumed=3 WHERE id='${s1}';`);
 try{
  const data=await (await p.request.get(api+'/api/v1/meals/'+ms,{headers:{'Accept-Language':'en'}})).json();
  await p.evaluate(id=>{location.hash='/meal/'+id},ms);await expect(p.locator('#meal-title')).toHaveText(f.meals.A1.titleEn);
  const ix=data.nextSlots.findIndex(s=>s.id===s1);await p.getByRole('radio').nth(ix).check();
  const add=p.getByRole('button',{name:/^Add to cart/}),disabled=await add.isDisabled();
  if(!disabled)await add.click();
  await save('MBQA-002-exhausted-selection',{requirement:'CW03; BR-013/015',expected:'Cannot add a selected slot with zero remaining portions',api:data,addDisabled:disabled,quantity:await p.getByRole('spinbutton',{name:'Quantity',exact:true}).inputValue(),cart:await p.evaluate(()=>JSON.parse(localStorage.getItem('matbakhy.cart')||'null'))});
 }finally{sql(`UPDATE meal_slot SET consumed=0 WHERE meal_id='${ms}' AND cook_slot_id='${s1}'; UPDATE cook_slot SET consumed=0 WHERE id='${s1}';`);}
 // A real SOLD_OUT response requires exhausted positive capacity, not capacity zero (unoffered).
 const a3=f.meals.A3.id;
 sql(`UPDATE meal_slot SET capacity=3,consumed=3 WHERE meal_id='${a3}'; UPDATE cook_slot SET consumed=3 WHERE kitchen_id='${f.kitchens.A.id}';`);
 try{
  const data=await (await p.request.get(api+'/api/v1/meals/'+a3,{headers:{'Accept-Language':'en'}})).json();
  await p.evaluate(id=>{location.hash='/meal/'+id},a3);await expect(p.locator('#meal-title')).toHaveText(f.meals.A3.titleEn);
  await save('IQA-09-true-soldout',{api:data,expected:'all stock exhausted; Add disabled',addDisabled:await p.getByRole('button',{name:/^Add to cart/}).isDisabled()});
 }finally{sql(`UPDATE meal_slot SET capacity=0,consumed=0 WHERE meal_id='${a3}'; UPDATE cook_slot SET consumed=0 WHERE kitchen_id='${f.kitchens.A.id}';`);}
 // Loading is held by a controlled response gate, rather than inferred from elapsed time.
 let release;const gate=new Promise(r=>{release=r});
 await p.route(api+'/api/v1/meals?*',async r=>{const response=await r.fetch();await gate;await r.fulfill({response});});
 await p.evaluate(()=>{location.hash='/'});await expect(p.getByText('Loading…',{exact:true}).first()).toBeVisible();
 await save('IQA-09-controlled-loading',{expected:'visible loading until response arrives',loadingVisible:true});release();await expect(p.locator('.food-card')).toHaveCount(4);await p.unrouteAll({behavior:'wait'});
 // Validate actual text enlargement: root font alone left px-sized text unchanged in the main run.
 await p.evaluate(id=>{location.hash='/meal/'+id},ms);await expect(p.locator('#meal-title')).toHaveText(f.meals.A1.titleEn);
 await p.setViewportSize({width:320,height:900});
 const before=await p.locator('#meal-title').evaluate(e=>getComputedStyle(e).fontSize);
 await p.evaluate(()=>{const sizes=[...document.querySelectorAll('body *')].map(e=>[e,parseFloat(getComputedStyle(e).fontSize)]);for(const [e,size] of sizes)e.style.fontSize=(size*2)+'px';});
 const after=await p.locator('#meal-title').evaluate(e=>getComputedStyle(e).fontSize);
 await save('IQA-10-text-enlargement',{method:'controlled 200% computed text-size injection; not native browser zoom',before,after,expected:'all actions fit without page horizontal overflow',dimensions:await p.evaluate(()=>({width:innerWidth,scrollWidth:document.documentElement.scrollWidth})),addVisible:await p.getByRole('button',{name:/^Add to cart/}).isVisible()});
 await c.close();await b.close();
 fs.writeFileSync(path.join(out,'supplementary.json'),JSON.stringify({applicationSha:'eb1a6717b3304d3a02e6a46d06c77ed5d43ac144',evidence},null,2)+'\n');
 console.log(evidence.map(e=>e.id));
})().catch(e=>{console.error(e);process.exit(1)});
