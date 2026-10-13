const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../..');
const {chromium,expect}=require(path.join(root,'apps/operations-web/node_modules/@playwright/test'));
const secret=JSON.parse(fs.readFileSync('/workspace/qa-tools/regression-d877a15/stack/credentials.json'));
function totp(key){let bits='';for(const ch of key)bits+='ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'.indexOf(ch).toString(2).padStart(5,'0');const b=Buffer.from(key); /* Keycloak stores raw UTF-8 when secretEncoding is absent. */const counter=Buffer.alloc(8);counter.writeBigUInt64BE(BigInt(Math.floor(Date.now()/30000)));const h=crypto.createHmac('sha1',b).update(counter).digest();return String((h.readUInt32BE(h[h.length-1]&15)&0x7fffffff)%1000000).padStart(6,'0');}
async function login(browser,role,origin='http://localhost:36173'){
 const context=await browser.newContext({viewport:{width:1440,height:1000}}),page=await context.newPage();page.setDefaultTimeout(12000);
 await page.goto(origin);await page.getByRole('link',{name:/^(Sign in|تسجيل الدخول)$/}).first().click();
 await expect(page.locator('#username')).toBeVisible();await page.locator('#username').fill(role);await page.locator('#password').fill(secret.password);await page.locator('#kc-login').click();
 if(secret.otp[role]){await expect(page.locator('#otp')).toBeVisible();const counterPath='/workspace/qa-tools/regression-d877a15/otp-counters.json';const used=fs.existsSync(counterPath)?JSON.parse(fs.readFileSync(counterPath)):{};let tick=Math.floor(Date.now()/30000);let remaining=30000-Date.now()%30000;if(used[role]===tick||remaining<6000){await new Promise(r=>setTimeout(r,remaining+150));tick=Math.floor(Date.now()/30000);}await page.locator('#otp').fill(totp(secret.otp[role]));used[role]=tick;fs.writeFileSync(counterPath,JSON.stringify(used));await page.locator('#kc-login').click();}
 await page.waitForURL(u=>u.origin===origin,{timeout:20000,waitUntil:'domcontentloaded'});
 const response=await context.request.get('http://localhost:39080/api/v1/me');const profile=await response.json();expect(response.status()).toBe(200);
 await context.storageState({path:'/workspace/qa-tools/regression-d877a15/'+role+'-state.json'});
 return {context,page,profile,mfaPerformed:!!secret.otp[role]};
}
module.exports={login,totp,root,secret};
if(require.main===module)(async()=>{const b=await chromium.launch({executablePath:'/usr/bin/chromium',headless:true,args:['--no-sandbox']});for(const role of ['customer-a','cook-a','ops-a','ops-review','support-a','finance-a','finance-b','config-a']){try{const x=await login(b,role,role==='customer-a'?'http://localhost:36173':'http://localhost:36174');console.log({role,profileId:x.profile.id,mfaPerformed:x.mfaPerformed});await x.context.close();}catch(e){console.log({role,status:'BLOCKED',error:String(e.message).slice(0,350)});}}await b.close();})().catch(e=>{console.error(e);process.exit(1)});
