// Requires jsdom 26.1.0 via NODE_PATH. DOM behavior only: no layout/visual claims.
const {JSDOM}=require('jsdom');
const {readFileSync}=require('node:fs');
const {join}=require('node:path');
const assert=require('node:assert/strict');
const dom=new JSDOM(readFileSync(join(__dirname,'index.html'),'utf8'),{url:'https://reference.invalid/',runScripts:'outside-only'});
const w=dom.window,d=w.document;
w.scrollTo=()=>{};w.HTMLElement.prototype.scrollIntoView=()=>{};
w.HTMLDialogElement.prototype.showModal=function(){this.open=true;};
w.HTMLDialogElement.prototype.close=function(){this.open=false;};
const click=s=>{const el=d.querySelector(s);assert.ok(el,s);el.click();};
const text=s=>d.querySelector(s).textContent;
function route(hash){w.location.hash=hash;w.dispatchEvent(new w.HashChangeEvent('hashchange'));}
try{
 w.eval(readFileSync(join(__dirname,'app.js'),'utf8'));
 assert.equal(d.querySelectorAll('#results article').length,3);
 assert.equal(d.querySelector('#contextual').hidden,true);
 click('[data-intent=solo]');assert.equal(d.querySelector('#contextual').hidden,false);
 assert.equal(d.querySelectorAll('#contextual [aria-pressed=true]').length,0);
 click('[data-portion="0"]');assert.match(text('#results'),/مفيش اختيارات/);
 click('[data-action=reset]');assert.equal(d.querySelectorAll('#results article').length,3);
 d.querySelector('#search').value='نادية';d.querySelector('#search').dispatchEvent(new w.Event('input'));
 assert.equal(d.querySelectorAll('#results article').length,1);
 click('[data-mode=kitchens]');assert.match(text('#results'),/مطبخ نادية/);
 click('[data-action=filter]');d.querySelector('#max-price').value='190';click('[data-action=apply-filter]');assert.match(text('#results'),/مفيش اختيارات/);
 click('[data-action=reset]');
 route('#/kitchen/amina');assert.equal(d.querySelectorAll('#kitchen-meals article').length,3);
 assert.equal(d.querySelectorAll('#kitchen-meals button:disabled').length,1);
 click('[data-kitchen-category="صواني بالفرن"]');assert.equal(d.querySelectorAll('#kitchen-meals article').length,1);
 route('#/dish/mahshi');assert.equal(d.querySelector('#minus').disabled,true);
 click('#plus');assert.match(text('#total'),/٣٦٠/);
 click('#plus');click('#plus');assert.equal(d.querySelector('#plus').disabled,true);
 click('#add');click('[data-action=basket]');assert.match(text('#dialog-content'),/٧٢٠/);click('#close-dialog');
 click('#add');assert.match(text('#notice'),/أكتر من المتاح/);
 route('#/dish/pasta');click('#add');assert.match(text('#dialog-content'),/استبدالها/);click('#close-dialog');
 click('[data-action=basket]');assert.match(text('#dialog-content'),/مطبخ أمينة/);click('#close-dialog');
 click('#add');click('[data-replace=pasta]');click('[data-action=basket]');assert.match(text('#dialog-content'),/مطبخ نادية/);
 click('[data-action=empty-cart]');click('[data-action=basket]');assert.match(text('#dialog-content'),/فاضية/);
 console.log('PASS DOM: neutral intent, reveal/reset, portion empty state, search, kitchen mode, price filter, kitchen categories/unavailable, quantity/capacity, total, basket bounds, cancel/confirm replacement, clear basket. Layout and browser rendering NOT_RUN.');
}finally{w.close();}
