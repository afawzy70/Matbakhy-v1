/* Independent assertions: does not import developer tests or product logic. */
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../..');
const {chromium, expect} = require(path.join(root, 'apps/operations-web/node_modules/@playwright/test'));
const out = path.join(__dirname, 'evidence');
const f = JSON.parse(fs.readFileSync(path.join(out, 'fixtures.json')));
const base = process.env.IQA_WEB_URL || 'http://localhost:25173';
const api = 'http://localhost:28080';
const results = [], network = [], consoleEvents = [];
let p, context, current;
function check(name, pass, actual, expected) {
  current.checks.push({name, status: pass ? 'PASS' : 'FAIL', expected, actual});
}
async function shot(name) {
  await p.screenshot({path:path.join(out,name+'.png'),fullPage:true});
  const state = await p.evaluate(() => ({url:location.href,lang:document.documentElement.lang,dir:document.documentElement.dir,
    width:innerWidth,scrollWidth:document.documentElement.scrollWidth,text:document.body.innerText,
    controls:[...document.querySelectorAll('button,input,select')].map(e=>({tag:e.tagName,type:e.type,name:e.getAttribute('aria-label')||e.textContent,value:e.value,disabled:e.disabled})),
    cart:JSON.parse(localStorage.getItem('matbakhy.cart')||'null')}));
  fs.writeFileSync(path.join(out,name+'.json'),JSON.stringify(state,null,2)+'\n');
  current.evidence.push(name+'.png',name+'.json');
  return state;
}
async function scenario(id, fn) {
  current={id,startedAt:new Date().toISOString(),checks:[],evidence:[],status:'NOT_RUN'};
  try {await fn();current.status=current.checks.some(x=>x.status==='FAIL')?'FAIL':'PASS';}
  catch(e){current.status='BLOCKED';current.harnessError=String(e.message).slice(0,1600);await shot(id+'-blocked').catch(()=>{});}
  results.push(current);
  fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({applicationSha:'eb1a6717b3304d3a02e6a46d06c77ed5d43ac144',browser:'Chromium',results},null,2)+'\n');
  console.log(id,current.status,current.checks.filter(x=>x.status==='FAIL').map(x=>x.name));
}
async function english() {
  if(await p.locator('html').getAttribute('lang')==='ar')await p.getByRole('button',{name:'English',exact:true}).click();
  await expect(p.locator('html')).toHaveAttribute('lang','en');
  await p.waitForLoadState('networkidle');
}
async function nav(route) {await p.evaluate(r=>{location.hash=r},route);}
async function meal(key) {
  await nav('/meal/'+f.meals[key].id);await english();
  await expect(p.locator('#meal-title')).toHaveText(f.meals[key].titleEn);
}
async function slot(key) {
  const resp=await p.request.get(api+'/api/v1/meals/'+f.meals[key[0]+'1'].id,{headers:{'Accept-Language':'en'}});
  const data=await resp.json();
  const position=data.nextSlots.findIndex(s=>s.id===f.slots[key].id);
  if(position<0)throw new Error('Fixture slot not offered: '+key);
  await p.getByRole('radio').nth(position).check();
}
async function add(key, skey, qty=1) {
  await meal(key);await slot(skey);
  await p.getByRole('spinbutton',{name:'Quantity',exact:true}).fill(String(qty));
  await p.getByRole('button',{name:/^Add to cart/}).click();
}
async function cart() {return p.evaluate(()=>JSON.parse(localStorage.getItem('matbakhy.cart')||'null'));}
async function reset() {await p.evaluate(()=>{localStorage.removeItem('matbakhy.cart');localStorage.removeItem('matbakhy.pendingPlacement')});await nav('/');}
async function expectCart(k,m,s,qty) {
  const c=await cart();check('Exact resulting kitchen/meal/slot/quantity',c?.kitchenId===f.kitchens[k].id&&c?.slotId===f.slots[s].id&&c?.lines.length===1&&c.lines[0].mealId===f.meals[m].id&&c.lines[0].quantity===qty,c,{kitchenId:f.kitchens[k].id,mealId:f.meals[m].id,slotId:f.slots[s].id,quantity:qty});
  await nav('/checkout');await expect(p.locator('.line-items')).toContainText(f.meals[m].titleEn);
  check('Visible resulting meal and quantity',(await p.locator('.line-items').innerText()).includes(qty+' × '+f.meals[m].titleEn),await p.locator('.line-items').innerText(),qty+' × '+f.meals[m].titleEn);
}
(async()=>{
  const b=await chromium.launch({executablePath:process.env.E2E_CHROMIUM||'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
  context=await b.newContext({viewport:{width:1440,height:1000}});p=await context.newPage();p.setDefaultTimeout(7000);
  p.on('console',m=>{if(m.type()==='error'||m.type()==='warning')consoleEvents.push({case:current?.id,type:m.type(),text:m.text()});});
  p.on('pageerror',e=>consoleEvents.push({case:current?.id,type:'pageerror',text:e.message}));
  p.on('requestfailed',r=>network.push({case:current?.id,url:r.url(),failure:r.failure()?.errorText}));
  p.on('response',async r=>{
    if(r.url().startsWith(api+'/api/v1/')){
      const x={case:current?.id,url:r.url(),status:r.status(),acceptLanguage:r.request().headers()['accept-language']};
      if(/\/meals|\/zones|\/kitchens/.test(r.url())){try{x.body=await r.json()}catch{}}
      network.push(x);
    }
  });
  await scenario('IQA-01',async()=>{
    await p.goto(base);await expect(p.locator('.food-card').first()).toBeVisible();
    check('Arabic default direction',await p.locator('html').getAttribute('dir')==='rtl',await p.locator('html').getAttribute('dir'),'rtl');
    await english();
    check('English switch localizes API content',await p.getByRole('article',{name:f.meals.A1.titleEn,exact:true}).count()===1,await p.locator('.food-card').allTextContents(),'English titles/descriptions/portions after selecting English');
    await shot('IQA-01-language-switch');
    // Continue remaining checks via a new route request, retaining the failed switch evidence.
    await meal('A1');await nav('/');
    await expect(p.getByRole('article',{name:f.meals.A1.titleEn,exact:true})).toBeVisible();
    for(const key of ['A1','A2','B1']) {
      const card=p.getByRole('article',{name:f.meals[key].titleEn,exact:true});
      check('Published card '+key,await card.count()===1,await card.count(),1);
      if(await card.count()){check('EGP price '+key,(await card.innerText()).includes((f.meals[key].priceMinor/100).toFixed(2)),await card.innerText(),(f.meals[key].priceMinor/100).toFixed(2));check('Kitchen '+key,(await card.innerText()).includes(f.kitchens[key[0]].name),await card.innerText(),f.kitchens[key[0]].name);}
    }
    check('Paused kitchen excluded',await p.getByRole('article',{name:f.meals.PAUSED1.titleEn,exact:true}).count()===0,await p.locator('.food-card').allTextContents(),'PAUSED1 absent');
    await p.getByRole('button',{name:'Filter results',exact:true}).click();
    const zones=p.locator('#filters select').first();const options=await zones.locator('option').evaluateAll(xs=>xs.map(e=>({value:e.value,text:e.textContent})));
    await zones.selectOption(options[1].value);await p.getByRole('button',{name:'Show results',exact:true}).click();
    await p.getByRole('searchbox').fill('QA Meal B1');await p.getByRole('button',{name:'Search for a meal or kitchen',exact:true}).click();
    await expect(p.locator('.food-card')).toHaveCount(1);
    check('Unique server search result',await p.locator('.food-card').innerText().then(t=>t.includes('QA Meal B1')),await p.locator('.food-card').innerText(),'only B1');
    await shot('IQA-01-search');
    await p.getByRole('searchbox').fill('');await p.getByRole('button',{name:'Search for a meal or kitchen',exact:true}).click();await expect(p.locator('.food-card')).toHaveCount(4);
    check('Fresh zone/search/reset API queries',network.some(n=>new URL(n.url).searchParams.get('q')==='QA Meal B1')&&network.some(n=>n.url.includes('zoneId=')),network.filter(n=>n.url.includes('/meals?')).map(n=>n.url),'server query contains zoneId and q; reset returns all four active meals');
    await shot('IQA-01-browse');
  });
  await scenario('IQA-02',async()=>{
    await meal('A1');const txt=await p.locator('main').innerText();
    for(const [name,value] of Object.entries({description:f.meals.A1.descriptionEn.trim(),ingredients:'Synthetic rice',portion:'Synthetic portion',price:'120.25',allergens:'Detailed allergy information is not available'}))check(name,txt.includes(value),txt,value);
    const data=await (await p.request.get(api+'/api/v1/meals/'+f.meals.A1.id,{headers:{'Accept-Language':'en'}})).json();
    check('Only open future offered slots',data.nextSlots.length===2&&data.nextSlots.every(s=>[f.slots.A1.id,f.slots.A2.id].includes(s.id)),data.nextSlots,'A1 and A2 future OPEN slots, each capacity 3');
    await shot('IQA-02-meal');await p.locator('.meal-detail .kitchen-link').click();await expect(p.locator('#kitchen-title')).toHaveText(f.kitchens.A.name);
    const kt=await p.locator('main').innerText();check('Operational approval wording',/operational/i.test(kt)&&!/guarantee(s)? food safety/i.test(kt),kt,'operational verification, no food safety guarantee');
    const kr=await (await p.request.get(api+'/api/v1/kitchens/'+f.kitchens.A.slug)).json();
    check('Private kitchen fields absent',!JSON.stringify(kr).includes('LOCAL_FIXTURE_ONLY')&&!('contactPhone' in kr),kr,'no private location/contact');await shot('IQA-02-kitchen');
  });
  await scenario('IQA-03',async()=>{
    await nav('/');await expect(p.locator('.food-card')).toHaveCount(4);
    for(const key of ['A1','A2','B1'])check('Own description '+key,(await p.getByRole('article',{name:f.meals[key].titleEn,exact:true}).locator('.dish-description').innerText())===f.meals[key].descriptionEn.trim(),await p.getByRole('article',{name:f.meals[key].titleEn,exact:true}).locator('.dish-description').innerText(),f.meals[key].descriptionEn);
    await p.getByRole('button',{name:/A meal for me or us/}).click();
    await expect(p.getByRole('form',{name:'Helpful choices'})).toBeVisible();
    check('D11 portion filter absent',await p.getByRole('button',{name:/^(One person|Two people)$/i}).count()===0,await p.getByRole('button').allTextContents(),'no one-person/two-people filters');
    await p.route(api+'/api/v1/meals?*',async route=>{const resp=await route.fetch();const body=await resp.json();body.items=body.items.map(m=>({...m,description:null}));await route.fulfill({response:resp,json:body});});
    await p.reload();await english();await expect(p.locator('.food-card')).toHaveCount(4);
    check('Absent description omitted (contract injection)',await p.locator('.dish-description').count()===0,await p.locator('.dish-description').count(),0);await shot('IQA-03-null-contract');await p.unrouteAll({behavior:'wait'});
  });
  await scenario('IQA-04',async()=>{
    await reset();await add('A1','A1',2);
    check('Meal quantity preview arithmetic',(await p.getByRole('button',{name:/^Add to cart/}).innerText()).includes('240.50'),await p.getByRole('button',{name:/^Add to cart/}).innerText(),'240.50 EGP = 2 × 120.25');
    await expectCart('A','A1','A1',2);await p.reload();await english();
    check('Draft persisted after reload',(await cart()).lines[0].quantity===2,await cart(),'A1/S1 quantity 2');
    const txt=await p.locator('main').innerText();
    check('Basket quantity change controls',await p.locator('main input[type=number], main button[aria-label*=quantity]').count()>0,await p.locator('main').getByRole('button').allTextContents(),'increase/decrease or quantity input');
    check('Per-line removal control',await p.locator('.line-items button').count()>0,await p.locator('.line-items').innerText(),'remove individual line');
    check('Estimated basket subtotal',/240[.,]50/.test(txt),txt,'estimated subtotal 240.50 EGP');await shot('IQA-04-cart');
    await p.getByRole('button',{name:'Empty cart',exact:true}).click();check('Empty state',await p.locator('main').innerText().then(t=>t.includes('Your cart is empty')),await p.locator('main').innerText(),'clear empty state');
  });
  for(const [id,target,sk,confirm] of [['IQA-05','B1','B1',false],['IQA-06','B1','B1',true],['IQA-07','A2','A2',false],['IQA-08','A2','A2',true]])await scenario(id,async()=>{
    await reset();await add('A1','A1',2);const before=await cart();await add(target,sk,1);
    await expect(p.getByRole('alertdialog')).toBeVisible();
    check('No mutation before confirmation',JSON.stringify(await cart())===JSON.stringify(before),await cart(),before);await shot(id+'-prompt');
    await p.getByRole('button',{name:confirm?'Replace cart':'Keep my current cart',exact:true}).click();
    await expect(p.getByRole('alertdialog')).toHaveCount(0);
    if(!confirm)check('Cancel preserves complete basket',JSON.stringify(await cart())===JSON.stringify(before),await cart(),before);
    await expectCart(confirm?target[0]:'A',confirm?target:'A1',confirm?sk:'A1',confirm?1:2);await shot(id+'-result');
  });
  await scenario('IQA-09',async()=>{
    await reset();await meal('A3');check('Sold-out has no selectable slot',await p.getByRole('radio').count()===0,await p.getByRole('radio').count(),0);check('Sold-out add disabled',await p.getByRole('button',{name:/^Add to cart/}).isDisabled(),await p.getByRole('button',{name:/^Add to cart/}).isDisabled(),true);await shot('IQA-09-soldout');
    await meal('A1');await slot('A1');await p.getByRole('spinbutton',{name:'Quantity',exact:true}).fill('99');
    check('Quantity capped by remaining stock',await p.getByRole('spinbutton',{name:'Quantity',exact:true}).inputValue()==='3',await p.getByRole('spinbutton',{name:'Quantity',exact:true}).inputValue(),'3');check('Increase disabled at cap',await p.getByRole('button',{name:'Increase quantity',exact:true}).isDisabled(),await p.getByRole('button',{name:'Increase quantity',exact:true}).isDisabled(),true);await shot('IQA-09-cap');
    const pausedResponse=await p.request.get(api+'/api/v1/meals/'+f.meals.PAUSED1.id);
    check('Paused meal not publicly purchasable',pausedResponse.status()===404,pausedResponse.status(),404);
    await nav('/kitchen/'+f.kitchens.PAUSED.slug);await expect(p.locator('#kitchen-title')).toBeVisible();
    check('Paused kitchen clear unavailable state',(await p.locator('main').innerText()).includes('not taking orders'),await p.locator('main').innerText(),'not taking orders');await shot('IQA-09-paused');
    await nav('/');await expect(p.getByRole('searchbox')).toBeVisible();await p.getByRole('searchbox').fill('NO_MATCH_IQA');await p.getByRole('button',{name:'Search for a meal or kitchen',exact:true}).click();await expect(p.locator('.food-card')).toHaveCount(0);await shot('IQA-09-empty');check('Empty state visible',(await p.locator('main').innerText()).includes('No meals'),await p.locator('main').innerText(),'no meals, clear/recovery');
    await p.route(api+'/api/v1/meals?*',route=>route.fulfill({status:503,json:{code:'INTERNAL_ERROR',messageKey:'error.internal',fieldErrors:[],requestId:'iqa-injected-error',retryable:true}}));await p.reload();await english();await expect(p.getByRole('button',{name:'Retry',exact:true})).toBeVisible();await shot('IQA-09-error');check('Error offers retry',await p.getByRole('button',{name:'Retry',exact:true}).isVisible(),await p.locator('main').innerText(),'visible Retry');await p.unrouteAll({behavior:'wait'});await p.getByRole('button',{name:'Retry',exact:true}).click();await expect(p.locator('.food-card')).toHaveCount(4);check('Retry recovers real API',await p.locator('.food-card').count()===4,await p.locator('.food-card').count(),4);
  });
  await scenario('IQA-10',async()=>{
    await reset();await add('A1','A1',2);
    for(const lang of ['ar','en'])for(const width of [1440,390,320]) {
      await p.setViewportSize({width,height:900});await p.reload();
      if(lang==='en')await english();
      if(lang==='ar'&&await p.locator('html').getAttribute('lang')==='en')await p.getByRole('button',{name:'العربية',exact:true}).click();
      await nav('/');await expect(p.locator('.food-card')).toHaveCount(4);
      check(`Direction ${lang}/${width}`,await p.locator('html').getAttribute('dir')===(lang==='ar'?'rtl':'ltr'),await p.locator('html').getAttribute('dir'),lang==='ar'?'rtl':'ltr');
      for(const [pageName,route,ready] of [['browse','/','.food-card'],['meal','/meal/'+f.meals.A2.id,'#meal-title'],['kitchen','/kitchen/'+f.kitchens.A.slug,'#kitchen-title'],['basket','/checkout','.line-items']]) {
        await nav(route);await expect(p.locator(ready).first()).toBeVisible();
        const state=await shot(`IQA-10-${lang}-${width}-${pageName}`);check(`No page overflow ${lang}/${width}/${pageName}`,state.scrollWidth<=width,state.scrollWidth,width);
      }
    }
    await p.setViewportSize({width:390,height:900});await english();await reset();await add('A1','A1',2);await add('B1','B1');
    const dialog=p.getByRole('alertdialog');await expect(dialog).toBeVisible();
    check('Prompt defaults to safe cancel',await p.getByRole('button',{name:'Keep my current cart',exact:true}).evaluate(e=>e===document.activeElement),await p.evaluate(()=>document.activeElement.textContent),'Keep my current cart focused');
    await p.keyboard.press('Tab');const inside=await dialog.evaluate(e=>e.contains(document.activeElement));check('Prompt keyboard focus contained',inside,await p.evaluate(()=>document.activeElement.outerHTML),'focus stays in active alertdialog');await shot('IQA-10-prompt-focus');
    await p.getByRole('button',{name:'Keep my current cart',exact:true}).focus();await p.keyboard.press('Escape');await expect(dialog).toHaveCount(0);
    check('Escape restores add focus',await p.getByRole('button',{name:/^Add to cart/}).evaluate(e=>e===document.activeElement),await p.evaluate(()=>document.activeElement.textContent),'Add to cart focused');
    await p.setViewportSize({width:320,height:900});await p.evaluate(()=>{document.documentElement.style.fontSize='200%'});await shot('IQA-10-reflow-200');check('200% text no sideways scroll',await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),await p.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth})),{width:320,scroll:320});
  });
  await new Promise(r=>setTimeout(r,300));
  fs.writeFileSync(path.join(out,'network.json'),JSON.stringify(network,null,2)+'\n');fs.writeFileSync(path.join(out,'console.json'),JSON.stringify(consoleEvents,null,2)+'\n');
  fs.writeFileSync(path.join(out,'environment.json'),JSON.stringify({applicationSha:'eb1a6717b3304d3a02e6a46d06c77ed5d43ac144',browserVersion:b.version(),web:base,api,completedAt:new Date().toISOString(),mode:'REPORT_ONLY',synthetic:true},null,2)+'\n');
  await b.close();
  process.exitCode=results.some(r=>r.status==='FAIL'||r.status==='BLOCKED')?1:0;
})().catch(e=>{console.error(e);process.exit(1)});
