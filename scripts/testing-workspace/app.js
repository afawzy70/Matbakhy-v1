/* global document, window, location, history */
import {matches, persisted, report, statuses, executionIssues} from './core.mjs';

const main = document.querySelector('main');
const secretHistory = new Set();
const secrets = new Map(); // Passwords have no storage or export path.
const notes = {};
const metadata = {environment:'', release:''};
const storageKey = 'matbakhy-testing-v1';
let records = {}, data, pendingExport = null;
try { records = persisted(JSON.parse(localStorage.getItem(storageKey) || '{}')); } catch { records = {}; }
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const arDomains = {'discovery-basket':'العميل والسلة','orders-finance':'الطلبات والمالية','cook-ops':'المطبخ والعمليات','config-security':'الإعدادات والأمان'};
const sourceURL = path => `https://github.com/afawzy70/Matbakhy-v1/blob/${data.source_sha}/${path}`;
const sourceLink = path => `<a href="${esc(sourceURL(path))}" target="_blank" rel="noopener noreferrer">${esc(path)}</a>`;
const technical = value => `<div class="technical" dir="ltr">${esc(typeof value === 'string' ? value : JSON.stringify(value,null,2))}</div>`;
const list = values => `<ul class="list">${(values || []).map(v=>`<li>${technical(v)}</li>`).join('')}</ul>`;
const badge = value => `<span class="badge">${esc(value)}</span>`;
const caseHref = id => `#/case/${encodeURIComponent(id)}`;
const status = id => {const r=records[id];const c=data?.cases.find(c=>c.id===id);return r?.status==='PASS'&&c&&executionIssues(c,r,notes[id],notes[id]?.run).length?'BLOCKED':r?.status||'NOT_RUN';};
const record = id => records[id] ||= {status:'NOT_RUN',checks:[]};
function save() { try { localStorage.setItem(storageKey, JSON.stringify(persisted(records))); } catch { toast('المتصفح مش بيسمح بالحفظ. صدّر التقرير قبل ما تقفل.'); } }
function toast(message) { document.querySelector('#toast').textContent=message; setTimeout(()=>document.querySelector('#toast').textContent='',4500); }
function copy(value) { return `<button class="copy" data-copy="${esc(value)}" type="button">نسخ القيمة غير السرية</button>`; }
function card(c) { return `<article class="card">${badge(c.id)}<h3><a href="${caseHref(c.id)}">${esc(c.guidance.title || c.title)}</a></h3><small>${esc(arDomains[c.domain])} · ${esc(c.priority)}</small><div class="inline-values">${badge(status(c.id))}${badge(c.guidance_complete?'الدليل مكتمل':'الدليل محتاج مراجعة')}</div><a href="${caseHref(c.id)}">افتح خطوات الحالة ←</a></article>`; }
function home() {
  const done=data.cases.filter(c=>status(c.id)!=='NOT_RUN').length;
  const last=Object.entries(records).filter(([,r])=>r.updated).sort((a,b)=>b[1].updated.localeCompare(a[1].updated))[0];
  main.innerHTML=`<section class="hero"><div><div class="eyebrow">اختبر مطبخي، خطوة بخطوة</div><h1>كل الأدوار.<br>في مساحة واحدة.</h1><p>من أول تسجيل الدخول لحد استلام الطلب ومراجعة الحسابات. اختار الحالة، جهّز المطلوب، ونفّذ وسجّل اللي حصل.</p><div class="actions"><a class="button" href="${caseHref('CFG-001')}">ابدأ الاختبار · J0</a>${last?`<a class="button secondary" href="${caseHref(last[0])}">كمّل آخر حالة</a>`:'<a class="button secondary" href="#/journeys">شوف الرحلات الأساسية</a>'}</div></div><aside><span class="number">200</span><b>حالة اختبار محفوظة بمعرّفها الأصلي</b><p>${done} اتسجّل لهم نتيجة · ${200-done} لسه NOT_RUN</p><progress value="${done}" max="200" aria-label="الحالات المسجّل لها نتيجة"></progress></aside></section><div class="notice">دي مساحة تنفيذ وتسجيل، مش إثبات إن الاختبارات نجحت. ${data.cases.filter(c=>c.guidance_complete).length} دليل مكتمل المراجعة. الحالات اللي محتاجة توضيح عليها علامة واضحة، وتقدر تسجّلها BLOCKED وتكمل غيرها.</div><section><h2>هتختبر إيه النهارده؟</h2><div class="grid"><article class="card"><h3>الرحلات الأساسية J0–J8</h3><p>ابدأ بالأساسيات، وبعدها جرّب رحلة الطلب كاملة بين الأدوار.</p><a href="#/journeys">افتح الرحلات ←</a></article>${Object.entries(arDomains).map(([key,title])=>`<article class="card"><h3>${title}</h3><p>${data.cases.filter(c=>c.domain===key).length} حالة بإعداداتها وخطواتها ونتايجها المتوقعة.</p><a href="#/cases?domain=${key}">شوف الحالات ←</a></article>`).join('')}<article class="card"><h3>التقدّم والنتايج</h3><p>راجع اللي خلص واللي واقف، وصدّر تقرير يربط النتيجة بالحالة والنسخة.</p><a href="#/results">افتح تقريرك ←</a></article></div><a class="button secondary" href="#/cases">بحث في كل الـ 200 حالة</a></section>`;
}
const journeyNames=['البداية والوصول وتسجيل الدخول','استلام من المطبخ وكاش','توصيل وكاش','استلام وعربون 50%','توصيل وعربون 50%','الأسعار والتكرار وعزل الحسابات','المطبخ وتحكّم العمليات','التحقق بخطوتين','أجهزة iPhone وAndroid'];
function journeys() { main.innerHTML=`<div class="eyebrow">ابدأ بـ J0 وسجّل النسخة</div><h1>الرحلات الأساسية</h1><p>كل رحلة بتجمع حالات مترابطة. نتيجة الرحلة بتتحدد من نتايج الحالات اللي نفّذتها فعلًا.</p>${data.journeys.map((j,i)=>`<section class="panel"><h2><bdi>${j.id}</bdi> · ${journeyNames[i]}</h2>${technical(j.purpose)}<ol>${j.cases.map(id=>{const c=data.cases.find(c=>c.id===id);return `<li><a href="${caseHref(id)}">${esc(c.guidance.title||c.title)}</a> ${badge(id)} ${badge(status(id))}</li>`;}).join('')}</ol></section>`).join('')}`; }
function cases(params) {
  main.innerHTML=`<div class="eyebrow">الكتالوج الكامل</div><h1>اختار حالتك وابدأ</h1><div class="filters"><label>ابحث بالرقم، الموضوع، أو الحساب<input id="search" type="search" placeholder="مثال: عربون، CFG-001، FX-CUST-A" value="${esc(params.get('q')||'')}"></label><label>المجموعة<select id="domain"><option value="">كل المجموعات</option>${Object.entries(arDomains).map(([v,t])=>`<option value="${v}" ${params.get('domain')===v?'selected':''}>${t}</option>`).join('')}</select></label><label>اكتمال الدليل<select id="readiness"><option value="">الكل</option><option value="complete" ${params.get('readiness')==='complete'?'selected':''}>مكتمل المراجعة</option><option value="draft" ${params.get('readiness')==='draft'?'selected':''}>محتاج مراجعة</option></select></label><label>نتيجة التنفيذ<select id="result"><option value="">كل النتايج</option>${statuses.map(s=>`<option ${params.get('result')===s?'selected':''}>${s}</option>`).join('')}</select></label></div><p id="count" role="status"></p><div class="grid" id="cards"></div>`;
  function filter(){const q=main.querySelector('#search').value,d=main.querySelector('#domain').value,r=main.querySelector('#readiness').value,s=main.querySelector('#result').value;const found=data.cases.filter(c=>matches(c,q,d,r,s,records));main.querySelector('#count').textContent=`${found.length} حالة من 200`;main.querySelector('#cards').innerHTML=found.length?found.map(card).join(''):'<p class="empty">مفيش حالات مطابقة. جرّب رقم الحالة أو امسح الفلاتر.</p>';const p=new URLSearchParams();if(q)p.set('q',q);if(d)p.set('domain',d);if(r)p.set('readiness',r);if(s)p.set('result',s);history.replaceState(null,'',`#/cases${p.size?'?'+p:''}`);}
  main.querySelectorAll('input,select').forEach(el=>el.addEventListener('input',filter));filter();
}
function accountName(key) {
  const actor=data.actors.find(a=>a.key===key);
  return actor?.username || key;
}
function actorLabel(key) {
  if(key.includes('COOK'))return '👨‍🍳 الطباخ';
  if(key.includes('CUST'))return '👤 العميل';
  if(key.includes('FINANCE'))return 'الموظف المالي';
  if(key.includes('STAFF'))return 'موظف العمليات / Back-office';
  if(key==='FX-GUEST')return 'زائر من غير تسجيل دخول';
  return key;
}
function passwordSource(key) {
  const actor=data.actors.find(a=>a.key===key);
  if(!actor?.username)return '';
  return actor.username.startsWith('preview-')
    ? 'الباسورد: قيمة PREVIEW_USER_PASSWORD في ملف إعداد السيرفر الخاص؛ اطلبها من المشغّل المصرّح له عبر مدير كلمات مرور. مش موجودة في GitHub.'
    : 'الباسورد: credentials.private.json الخاص بالتجهيز في BACKUP_DIR/owner-e2e-private/<run>/، عن طريق المشغّل وقناة خاصة. الحساب الموجود قبل كده بيحتفظ بباسورده القديم.';
}
function accountCards(c) {
  const keys=c.guidance.actors||c.actors;
  return `<div class="account-grid">${keys.map(key=>{
    const actor=data.actors.find(a=>a.key===key);
    return `<div class="who"><b>${esc(actorLabel(key))}</b><code>${esc(accountName(key))}</code><p class="account-note">${esc(passwordSource(key)||actor?.note||'راجع دور المشغّل في الخطوة.')}</p>${actor?.grants?`<p class="account-note">منح التطبيق: <bdi>${esc(actor.grants.join(', '))}</bdi>. المطلوبة فعلًا في كل خطوة مكتوبة جنبها.</p>`:''}${actor?.username?`<label class="field account-credentials"><span>إدخال خاص اختياري · ذاكرة التبويب فقط</span><input class="private-input" type="password" autocomplete="off" data-secret="${esc(key)}" aria-label="باسورد ${esc(key)}"></label>`:''}</div>`;
  }).join('')}</div>`;
}
function field(title,text,className='') {
  return text?`<div class="step-field ${className}"><b>${title}</b><p>${esc(text)}</p></div>`:'';
}
function executionStep(c,step,index) {
  const account=data.actors.find(a=>a.key===step.actor);
  const original=c.steps.find(s=>s.n===step.canonical_n);
  const kind=step.kind==='canonical'?`خطوة أصلية ${step.canonical_n} · ${c.id}`:step.kind==='verification'?'تحقق إضافي · مش خطوة أصلية':'تجهيز إضافي · مش خطوة أصلية';
  return `<article class="visible-step ${step.kind==='canonical'?'step':''}" data-flow-key="${esc(step.key)}" ${original?`data-canonical="${original.n}"`:''}>
    <div class="vs-top"><span class="circle">${index+1}</span><div><strong>${esc(step.title)}</strong><div class="mutedline">${esc(actorLabel(step.actor))}</div><div class="kind">${esc(kind)}</div></div><label class="vs-check"><input class="stepdone" type="checkbox" data-check="${esc(step.key)}" data-case="${esc(c.id)}" ${record(c.id).checks.includes(step.key)?'checked':''}> خلصت</label></div>
    <div class="vs-body">
      ${field('الهدف',step.goal)}
      <div class="step-field"><b>الدور / الحساب</b><p><bdi>${esc(accountName(step.actor))}</bdi>${account?.username?` — ${esc(passwordSource(step.actor))}`:''}</p></div>
      <div class="step-field"><b>افتح هنا</b><a class="btn" href="${esc(step.url)}" rel="noopener noreferrer" target="_blank">افتح التطبيق ↗</a><a class="url" href="${esc(step.url)}" rel="noopener noreferrer" target="_blank">${esc(step.url)}</a></div>
      ${field('الإعداد المطلوب · ومكانه',step.configuration)}
      ${field('بيانات الاختبار',step.data,'compact-data')}
      ${field('اعمل إيه بالضبط؟',step.instruction)}
      ${step.precode?`<div class="step-field"><b>أمر فتح قناة القراءة · عند المشغّل</b>${technical(step.precode)}${copy(step.precode)}</div>`:''}
      ${step.code?`<div class="step-field"><b>القيم / الأوامر المطلوبة</b>${step.code.startsWith('https://')?`<a class="url" href="${esc(step.code)}" target="_blank" rel="noopener noreferrer">${esc(step.code)}</a>`:technical(step.code)}${copy(step.code)}</div>`:''}
      <div class="success"><b>النتيجة المتوقعة</b><p>${esc(step.expected)}</p></div>
      ${field('إزاي تتأكد؟',step.verification)}
      ${field('لو حاجة وقفت',step.troubleshooting,'warning')}
      ${step.original?`<div class="canonical-source"><b>التفاصيل الأصلية المطلوبة للتنفيذ · ${esc(c.id)} / ${original.n}</b>${technical(step.original)}</div>`:''}
    </div>
  </article>`;
}
function scenario(id) {
  const c=data.cases.find(x=>x.id===id);
  if(!c){main.innerHTML='<h1>الحالة مش موجودة</h1><a href="#/cases">ارجع لكل الحالات</a>';return;}
  const flow=c.flow,r=record(id),g=c.guidance;
  notes[id]||={};const run=notes[id].run||={environment:'',release:''};
  const total=flow.length+1;
  main.innerHTML=`
    <header class="header">
      <span class="pillwhite">تجربة خطوة بخطوة · <bdi>${esc(id)}</bdi></span>
      <h1>${esc(g.title||c.title)}</h1>
      <p>${esc(g.purpose||c.title)}</p>
      <span id="progressLabel"></span><div class="progressline" role="progressbar" aria-label="خطوات السيناريو المكتملة" aria-valuemin="0" aria-valuemax="${total}"><div id="bar"></div></div>
      <p class="canonical-count">${c.steps.length} خطوات أصلية محفوظة بمعرّفاتها · التجهيز والتحقق الإضافي مميزين بوضوح · <bdi id="scenario-status">${esc(status(id))}</bdi></p>
    </header>
    <section class="cardx"><h2>🧭 السيناريو اللي هنجربه</h2><p>${esc(g.purpose||c.title)}</p>
      <div class="warning"><b>قبل أي خطوة:</b> البيانات كلها تجريبية، لكن السيرفر فعلي. افحص الأول؛ أي تغيير مستخدم أو منحة أو Realm أو قاعدة بيانات في Preview المشتركة يحتاج موافقة المالك وتسجيل الحالة السابقة. الموقع مش بيشغّل الأوامر أو بيفحص السيرفر تلقائيًا.</div>
      <div class="columns"><div class="who"><b>الحالة الأصلية</b><code>${esc(id)}</code><small>اختبار يدوي · NOT_RUN لحد التنفيذ الفعلي بدليل</small></div><div class="who"><b>${g.summary?'الإجمالي المرجعي':'مراجعة الدليل'}</b><code>${esc(g.summary||(c.guidance_complete?'دليل تنفيذي متراجع من المصدر':'NOT VERIFIED · لسه محتاج مراجعة'))}</code><small>اكتمال الدليل مش نتيجة نجاح الاختبار</small></div></div>
      ${c.guidance_gaps.length?`<div class="warning"><b>الدليل غير مكتمل:</b> ${esc(c.guidance_gaps.join(' · '))}</div>`:''}
      ${c.readiness.needs_isolated_stack||c.destructive?'<div class="warning"><b>بيئة معزولة فقط:</b> الحالة تغيّر بيانات مشتركة أو تختبر عطل/سباق. لو البيئة المطلوبة مش متاحة سجّل BLOCKED؛ ماتطبقهاش على Preview المشتركة.</div>':''}
      ${c.readiness.needs_device?'<div class="warning"><b>جهاز فعلي مطلوب:</b> نسخة iPhone/Android موقعة وجهاز حقيقي. معاينة المتصفح مش بديل، ولو مش متاحين الحالة BLOCKED.</div>':''}
    </section>
    <section class="cardx"><h2>1. هتدخل بمين؟</h2>${accountCards(c)}<p class="muted">استخدم Browser Profile مستقل لكل حساب. ما تفتحش حسابين في نفس مخزن الكوكيز؛ نافذتان Incognito ممكن يشاركوا الجلسة. دخول الأدمن في Keycloak مختلف عن موظف التطبيق.</p></section>
    <section id="execute" class="cardx"><h2>2. نفّذ السيناريو خطوة بخطوة</h2><p class="muted">كل الخطوات ظاهرة قدامك. نفّذ من فوق لتحت، قارن النتيجة، وبعدها علّم «خلصت». العلامات بتتحفظ على نفس الجهاز.</p>
      <div id="steps">${flow.map((s,i)=>executionStep(c,s,i)).join('')}
        <article class="visible-step" data-flow-key="verify:outcomes"><div class="vs-top"><span class="circle">${total}</span><div><strong>قارن كل النتايج الأصلية وسجّل الدليل</strong><div class="mutedline">المالك / المختبِر · تحقق إضافي</div></div><label class="vs-check"><input type="checkbox" class="stepdone" data-check="verify:outcomes" ${r.checks.includes('verify:outcomes')?'checked':''}> خلصت</label></div><div class="vs-body" id="expected">
        <p>النتايج دي جزء من معيار الحالة الأصلية، ومش دليل إن أي حاجة حصلت فعلًا. تحقق منها بالقنوات المسموحة؛ أي تحقق إلزامي ناقص يمنع PASS.</p>
        ${Object.entries(c.expected).map(([key,values])=>`<h3>${({ui:'الواجهة',api:'استجابات API',database:'قاعدة البيانات · SELECT فقط عبر المشغّل'})[key]||esc(key)}</h3>${list(values)}`).join('')}
        ${c.unverified?.length?`<div class="warning"><b>نقاط مصدرية لسه مش متحققة حيًا</b>${list(c.unverified)}</div>`:''}
        ${c.guidance.blockers?.length?`<div class="warning"><b>نواقص محددة</b>${list(c.guidance.blockers)}</div>`:''}
        <p class="source-note">المبالغ والأكواد والنتايج الأصلية محفوظة؛ التفاصيل الإنجليزية التقنية في الحالات غير المتراجعة لسه محتاجة إكمال عربي.</p>
        <details><summary>مراجع المصدر والاختبارات الآلية · تشخيص اختياري</summary><p>${sourceLink(c.source)}</p><ul class="source-list">${(g.source_refs||[]).map(p=>`<li>${sourceLink(p)}</li>`).join('')}</ul>${list(c.automated_coverage)}</details>
        </div></article>
      </div>
    </section>
    <section id="record" class="cardx"><h2>3. هل التجربة نجحت؟</h2><div class="success">علّم PASS بس بعد تنفيذ كل المطلوب ومقارنة النتايج وحفظ دليل منقّح. اكتمال العلامات مش إثبات إن المنتج نجح.</div>
      <label class="field"><span>رقم النسخة المنشورة اللي اختبرتها · RELEASE_COMMIT</span><input id="run-release" dir="ltr" value="${esc(run.release)}" placeholder="SHA من deploy.sh status"></label>
      <label class="field"><span>اسم البيئة · من غير أسرار</span><input id="run-env" value="${esc(run.environment)}" placeholder="Preview أو بيئة معزولة محددة"></label>
      <label class="field"><span>رقم الطلب / معرّف بيانات الاختبار التجريبية</span><input data-note="order" value="${esc(notes[id]?.order||'')}" placeholder="MB-… لو الحالة بتعمل طلب"></label>
      <label class="field"><span>سبب التعطيل أو مشكلة التنفيذ</span><textarea data-note="reason">${esc(notes[id]?.reason||'')}</textarea></label>
      <label class="field"><span>الدليل المنقّح · رابط آمن أو وصف النتيجة الفعلية</span><textarea data-note="evidence">${esc(notes[id]?.evidence||'')}</textarea></label>
      <label class="field"><span>العيب: المتوقع، الفعلي، خطوات التكرار وrequest ID منقّح</span><textarea data-note="defect">${esc(notes[id]?.defect||'')}</textarea></label>
      <p class="mutedline">العلامات والحالة بتتحفظ محليًا. كلمات السر والملاحظات والأدلة ورقم النسخة في ذاكرة التبويب فقط؛ Reload بيمسحها. النتيجة المستكملة من غير دليل بتظهر غير موثّقة، والتقرير لا يصدرها PASS.</p>
      <div class="result-controls">${statuses.map(s=>`<button class="btn secondary" data-status="${s}" aria-pressed="${s===r.status}">${s}</button>`).join('')}</div>
      <div class="actions"><button class="btn" data-export>راجع ونزّل نتيجة التجربة</button><a class="smallbtn" href="#/cases">ارجع لكل الحالات</a></div>
      <p class="record-state" id="record-message" role="status">${esc(status(id))}${status(id)!=='NOT_RUN'&&!notes[id]?.evidence?' · نتيجة محفوظة، الدليل محتاج يتربط من جديد':''}</p>
    </section><p class="mutedline">الموقع لا يتصل تلقائيًا بتطبيق مطبخي أو Keycloak ولا يطبق أي إعدادات. كل نتائج التشغيل مدخلة يدويًا بواسطة المختبِر.</p>`;
  function refreshStatus(){main.querySelector('#scenario-status').textContent=status(id);main.querySelectorAll('[data-status]').forEach(b=>b.setAttribute('aria-pressed',b.dataset.status===status(id)));}
  function progress(){const keys=new Set([...flow.map(s=>s.key),'verify:outcomes']);const n=r.checks.filter(k=>keys.has(k)).length;main.querySelector('#progressLabel').textContent=`${n} من ${total} خطوة خلصت`;main.querySelector('#bar').style.width=`${n/total*100}%`;main.querySelector('[role=progressbar]').setAttribute('aria-valuenow',n);}
  progress();
  main.querySelectorAll('[data-check]').forEach(el=>el.addEventListener('change',()=>{r.checks=el.checked?[...new Set([...r.checks,el.dataset.check])]:r.checks.filter(x=>x!==el.dataset.check);r.updated=new Date().toISOString();if(r.status==='PASS'&&executionIssues(c,r,notes[id],run).length){r.status='BLOCKED';main.querySelector('#record-message').textContent='اتغيّرت الخطوات المطلوبة؛ النتيجة بقت BLOCKED لحد إعادة التحقق.';}save();progress();refreshStatus();}));
  main.querySelectorAll('[data-secret]').forEach(el=>el.addEventListener('input',()=>{if(el.value){secrets.set(el.dataset.secret,el.value);secretHistory.add(el.value);}else secrets.delete(el.dataset.secret);}));
  main.querySelectorAll('[data-note]').forEach(el=>el.addEventListener('input',()=>{notes[id]||={};notes[id][el.dataset.note]=el.value;}));
  main.querySelector('#run-release').oninput=e=>run.release=e.target.value;
  main.querySelector('#run-env').oninput=e=>run.environment=e.target.value;
  main.querySelectorAll('[data-status]').forEach(el=>el.addEventListener('click',()=>{
    const next=el.dataset.status,note=notes[id]||{};
    if(next==='PASS'&&(executionIssues(c,r,note,run).length)){toast('PASS محتاج دليل مكتمل المراجعة، تنفيذ كل الخطوات، RELEASE_COMMIT ودليل فعلي.');return;}
    if((next==='FAIL'||next==='BLOCKED')&&!note.reason?.trim()){toast('اكتب سبب الفشل أو التعطيل الأول.');return;}
    r.status=next;r.updated=new Date().toISOString();save();refreshStatus();main.querySelector('#record-message').textContent=`اتسجّلت ${next} — نتيجة مدخلة من المختبِر، مش تحقق تلقائي.`;main.querySelectorAll('[data-status]').forEach(b=>b.setAttribute('aria-pressed',b===el));
  }));
}

function results(){main.innerHTML=`<div class="eyebrow">سجلّك على الجهاز ده</div><h1>التقدّم والنتايج</h1><div class="grid">${statuses.map(s=>`<div class="card"><b>${s}</b><h2>${data.cases.filter(c=>status(c.id)===s).length}</h2></div>`).join('')}</div><div class="panel"><label>اسم بيئة التنفيذ · من غير أسرار<input id="run-env" value="${esc(metadata.environment)}"></label><label>ملحوظة عامة للدفعة · مش بديل رقم نسخة كل حالة<input id="run-release" dir="ltr" value="${esc(metadata.release)}"></label><p>بيانات النسخة والأدلة مش بتتحفظ تلقائيًا. التقرير بيضم كل الحالات بما فيها NOT_RUN. التصدير ملوش كلمات سر الحسابات الخاصة.</p><button data-export>راجع وصدّر التقرير</button><details><summary>معاينة التقرير المنقّح</summary><pre id="report-preview" class="technical"></pre></details></div><div class="table-scroll"><table><thead><tr><th>الحالة</th><th>النتيجة</th><th>اكتمال الدليل</th></tr></thead><tbody>${data.cases.map(c=>`<tr><td><a href="${caseHref(c.id)}"><bdi>${c.id}</bdi></a></td><td>${status(c.id)}</td><td>${c.guidance_complete?'مكتمل':'NOT VERIFIED'}</td></tr>`).join('')}</tbody></table></div>`;main.querySelector('#run-env').oninput=e=>metadata.environment=e.target.value;main.querySelector('#run-release').oninput=e=>metadata.release=e.target.value;}
function exportReport(){
  pendingExport=JSON.stringify(report(data,records,notes,[...secretHistory],metadata),null,2);
  let review=document.querySelector('#export-review');if(!review){review=document.createElement('section');review.id='export-review';review.className='panel';main.append(review);}
  review.innerHTML='<h2>راجع التقرير قبل التنزيل</h2><p>لو فيه أي بيانات حساسة، عدّل الملاحظات وراجع التقرير من جديد. التقرير بيضم نتايج مدخلة يدويًا؛ النتايج المستكملة بعد إعادة التحميل ممكن دليلها يكون ناقص.</p><pre class="technical"></pre><button id="confirm-export">التقرير منقّح · نزّل JSON</button>';
  review.querySelector('pre').textContent=pendingExport;
  review.querySelector('button').onclick=()=>{const blob=new Blob([pendingExport],{type:'application/json'}),a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='matbakhy-test-results.json';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000);toast('اتنزّل التقرير. احتفظ بالأدلة المنقّحة في مكان خاص.');};review.scrollIntoView();
}

function safeDecode(value){try{return decodeURIComponent(value);}catch{return '';}}
function route(){const [path,query='']=(location.hash.slice(1)||'/').split('?');if(path==='/')home();else if(path==='/journeys')journeys();else if(path==='/cases')cases(new URLSearchParams(query));else if(path==='/results')results();else if(path.startsWith('/case/'))scenario(safeDecode(path.slice(6)));else main.innerHTML='<h1>الصفحة مش موجودة</h1><a href="#/">ارجع للبداية</a>';window.scrollTo(0,0);main.focus({preventScroll:true});}
document.addEventListener('click',async e=>{if(e.target.closest('.skip')){e.preventDefault();main.focus();return;}const copyButton=e.target.closest('[data-copy]');if(copyButton){try{await navigator.clipboard.writeText(copyButton.dataset.copy);toast('اتنسخت القيمة.');}catch{toast('النسخ مش متاح. حدّد القيمة وانسخها يدويًا.');}}if(e.target.closest('[data-export]'))exportReport();const scroll=e.target.closest('[data-scroll]');if(scroll){e.preventDefault();document.getElementById(scroll.dataset.scroll)?.scrollIntoView();}});
window.addEventListener('hashchange',route);
try {const response=await fetch('./catalog.json');if(!response.ok)throw new Error('catalog unavailable');data=await response.json();route();}catch{main.innerHTML='<h1>ملف الحالات مش متاح</h1><p>شغّل python3 scripts/testing_workspace.py وبعدين افتح الموقع من خادم HTTP زي تعليمات المعاينة.</p>';}
