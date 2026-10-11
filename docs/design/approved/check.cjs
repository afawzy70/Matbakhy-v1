// Scoped design-reference checks, not product acceptance scenarios.
let chromium;
try { ({chromium}=require('playwright')); }
catch { ({chromium}=require(require.resolve('playwright-core',{paths:[require('node:path').resolve(__dirname,'../../../packages/design-tokens')]}))); }
const {pathToFileURL}=require('node:url');
const {resolve}=require('node:path');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH||undefined});
 try {
  const page=await browser.newPage({viewport:{width:390,height:844}});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  const url=pathToFileURL(resolve(__dirname,'index.html')).href;
  await page.goto(url,{waitUntil:'domcontentloaded'});
  await page.getByRole('button',{name:'وجبة ليا أو لينا'}).click();
  await page.locator('#contextual').waitFor({state:'visible'});
  await page.getByRole('button',{name:'فرد واحد',exact:true}).click();
  assert.match(await page.locator('#results').innerText(),/مفيش اختيارات/);
  await page.getByRole('button',{name:'مسح الفلاتر',exact:true}).click();
  assert.equal(await page.locator('#results article').count(),3);
  await page.locator('#search').fill('نادية');
  assert.equal(await page.locator('#results article').count(),1);
  await page.locator('#search').fill('');
  await page.locator('.tabs [data-mode=kitchens]').click();
  assert.equal(await page.locator('#results article').count(),2);
  await page.locator('.tabs [data-mode=dishes]').click();
  await page.getByRole('button',{name:'تصفية النتائج'}).click();
  await page.locator('#max-price').fill('190');
  await page.getByRole('button',{name:'عرض النتائج',exact:true}).click();
  assert.equal(await page.locator('#results article').count(),1);
  await page.locator('#results a[href="#/dish/mahshi"]').click();
  assert.equal(await page.locator('#minus').isDisabled(),true);
  await page.locator('#plus').click();
  assert.match(await page.locator('#total').innerText(),/٣٦٠/);
  await page.locator('#plus').click();await page.locator('#plus').click();
  assert.equal(await page.locator('#plus').isDisabled(),true);
  await page.locator('#add').click();
  await page.getByRole('button',{name:'سلة المشتريات',exact:true}).click();
  assert.match(await page.locator('#dialog-content').innerText(),/٧٢٠/);
  await page.locator('#close-dialog').click();
  await page.goto(url+'#/dish/pasta',{waitUntil:'domcontentloaded'});
  // Direct reload resets the memory-only basket; set up conflict via in-page navigation instead.
  await page.evaluate(()=>{location.hash='#/dish/mahshi';});
  await page.locator('#add[data-add=mahshi]').click();
  await page.evaluate(()=>{location.hash='#/dish/pasta';});
  await page.locator('#add[data-add=pasta]').click();
  assert.match(await page.locator('#dialog-content').innerText(),/استبدالها/);
  await page.locator('#close-dialog').click();
  await page.getByRole('button',{name:'سلة المشتريات',exact:true}).click();
  assert.match(await page.locator('#dialog-content').innerText(),/مطبخ أمينة/);
  await page.locator('#close-dialog').click();
  await page.locator('#add').click();
  await page.getByRole('button',{name:'استبدال السلة',exact:true}).click();
  await page.getByRole('button',{name:'سلة المشتريات',exact:true}).click();
  assert.match(await page.locator('#dialog-content').innerText(),/مطبخ نادية/);
  await page.locator('#close-dialog').click();
  for(const width of [320,390,420]){
   await page.setViewportSize({width,height:844});
   for(const route of ['#/','#/kitchen/amina','#/dish/mahshi']){
    await page.goto(url+route,{waitUntil:'domcontentloaded'});
    assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),`overflow ${width} ${route}`);
    const undersized=await page.locator('button,a,input').evaluateAll(els=>els.filter(e=>{const r=e.getBoundingClientRect();return r.width>0&&r.height>0&&(r.width<43.9||r.height<43.9)}).map(e=>e.textContent));
    assert.deepEqual(undersized,[],`touch sizes ${width} ${route}`);
   }
  }
  await page.goto(url+'?customer=returning',{waitUntil:'domcontentloaded'});
  assert.equal(await page.locator('.history').count(),1);
  await page.goto(url,{waitUntil:'domcontentloaded'});
  assert.equal(await page.locator('.history').count(),0);
  await page.setViewportSize({width:390,height:844});
  await page.screenshot({path:resolve(__dirname,'homepage-review.png'),fullPage:true});
  assert.deepEqual(errors,[]);
  console.log('PASS: contextual filters, reset, search, modes, price filter, routes, quantity/capacity, basket replacement/cancel, returning state, 320/390/420px overflow and 44px targets; no runtime page errors.');
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
