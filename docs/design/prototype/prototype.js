/* Draft clickable UX prototype for owner review (MB-004). Static, no backend: every number, name and
   state is synthetic. Server-side rules (quotes, stock, transitions) are only illustrated here. */
(function () {
  "use strict";
  const D = window.PROTOTYPE_DATA;
  const T = {
    ar: {
      brand: "مطبخي", banner: "نموذج تصميم مبدئي ببيانات تجريبية — ليس منتجًا فعليًا ولم تتم الموافقة على التصميم بعد",
      lang: "English", areaCustomer: "العميل", areaCook: "صاحب المطبخ", areaOps: "التشغيل",
      discoverTitle: "وجبات بيتي قريبة منك", zone: "المنطقة", noMeals: "لسه مفيش وجبات متاحة في منطقتك. جرّب منطقة أو ميعاد تاني.",
      portions: (n) => (n === 0 ? "نفدت الكمية" : `متاح ${new Intl.NumberFormat("ar-EG").format(n)}`), view: "التفاصيل", reviews: (n) => (n ? `${n} تقييم` : "لا توجد تقييمات بعد"),
      ingredients: "المكونات", allergens: "معلومات الحساسية", allergyNote: "لو عندك حساسية، راجع المكونات ومعلومات الحساسية قبل الطلب.",
      allergen: { GLUTEN: "جلوتين", NONE_DECLARED: "لم يعلن الطاهي عن مسببات حساسية", UNKNOWN: "غير معروف — اسأل قبل الطلب" },
      chooseSlot: "اختار الميعاد", quantity: "الكمية", addToBasket: "أضف للسلة", soldOut: "نفدت الكمية",
      checkoutTitle: "مراجعة الطلب", fulfillment: "طريقة الاستلام", delivery: "توصيل من المطبخ", pickup: "استلام من المطبخ",
      address: "عنوان التوصيل", subtotal: "إجمالي الأصناف", deliveryFee: "رسوم التوصيل", tax: "الضريبة", total: "الإجمالي",
      cash: "الدفع كاش عند الاستلام.", deadline: "صاحب المطبخ لازم يوافق خلال ١٥ دقيقة وإلا يُلغى الطلب تلقائيًا.",
      policy: "أوافق على شروط الإلغاء (نص تجريبي غير معتمد)", placeOrder: "أكّد الطلب", sampleValues: "قيم عينة فقط — السعر النهائي يحدده الخادم.",
      checking: "بنتأكد من حالة طلبك. متعملش طلب جديد دلوقتي.", stockChanged: "الكمية المتاحة اتغيّرت. راجع السلة قبل تأكيد الطلب.",
      orderTitle: "طلبك", waiting: "طلبك مستني موافقة صاحب المطبخ.", handover: "كود الاستلام (يظهر لك فقط)",
      states: { PENDING_ACCEPTANCE: "في انتظار الموافقة", ACCEPTED: "تمت الموافقة", PREPARING: "جاري التحضير", READY: "جاهز", OUT_FOR_DELIVERY: "في الطريق", COMPLETED: "تم الاستلام" },
      advance: "محاكاة الخطوة التالية (أداة النموذج)", support: "مساعدة في الطلب",
      cookTitle: "طلبات جديدة", acceptBy: (m) => `وافق خلال ${m} دقيقة`, accept: "قبول", reject: "رفض", accepted: "تم القبول",
      rejected: "تم الرفض — تم إرجاع الكمية", mode: { DELIVERY: "توصيل", PICKUP: "استلام" }, order: "الطلب", items: "الأصناف", action: "الإجراء",
      opsTitle: "طلبات انضمام المطابخ", approve: "اعتماد", requestChanges: "طلب تعديل", approved: "تم الاعتماد (بيانات تجريبية)",
      docs: (n) => `${n} مستندات خاصة`, zonesN: (n) => `${n} منطقة`, financeTitle: "فروقات الكاش", expected: "المتوقع", reported: "المُبلَّغ",
      difference: "الفرق", openCase: "فتح حالة مراجعة", caseOpened: "تم فتح حالة مراجعة — لا تعديل على المبالغ الأصلية",
    },
    en: {
      brand: "Matbakhy", banner: "Draft design prototype with synthetic data — not the real product; design not yet approved",
      lang: "العربية", areaCustomer: "Customer", areaCook: "Cook", areaOps: "Operations",
      discoverTitle: "Home-cooked meals near you", zone: "Area", noMeals: "No meals are available here yet. Try another area or time.",
      portions: (n) => (n === 0 ? "Sold out" : `${n} available`), view: "Details", reviews: (n) => (n ? `${n} reviews` : "No reviews yet"),
      ingredients: "Ingredients", allergens: "Allergen information", allergyNote: "If you have an allergy, review the ingredients and allergen information before ordering.",
      allergen: { GLUTEN: "Gluten", NONE_DECLARED: "Cook declared no allergens", UNKNOWN: "Unknown — ask before ordering" },
      chooseSlot: "Choose a time", quantity: "Quantity", addToBasket: "Add to basket", soldOut: "Sold out",
      checkoutTitle: "Review your order", fulfillment: "Fulfillment", delivery: "Delivery by the kitchen", pickup: "Pickup from the kitchen",
      address: "Delivery address", subtotal: "Items subtotal", deliveryFee: "Delivery fee", tax: "Tax", total: "Total",
      cash: "Pay cash on delivery or pickup.", deadline: "The cook must accept within 15 minutes or the order is cancelled automatically.",
      policy: "I accept the cancellation terms (draft text, not approved)", placeOrder: "Confirm order", sampleValues: "Sample values only — the server sets the final price.",
      checking: "We are checking your order. Please do not place another yet.", stockChanged: "Availability changed. Review your basket before confirming.",
      orderTitle: "Your order", waiting: "Your order is waiting for the cook to accept.", handover: "Handover code (shown only to you)",
      states: { PENDING_ACCEPTANCE: "Waiting for acceptance", ACCEPTED: "Accepted", PREPARING: "Preparing", READY: "Ready", OUT_FOR_DELIVERY: "On the way", COMPLETED: "Received" },
      advance: "Simulate next step (prototype control)", support: "Get help with this order",
      cookTitle: "New orders", acceptBy: (m) => `Accept within ${m} min`, accept: "Accept", reject: "Reject", accepted: "Accepted",
      rejected: "Rejected — portions released", mode: { DELIVERY: "Delivery", PICKUP: "Pickup" }, order: "Order", items: "Items", action: "Action",
      opsTitle: "Kitchen applications", approve: "Approve", requestChanges: "Request changes", approved: "Approved (synthetic data)",
      docs: (n) => `${n} private documents`, zonesN: (n) => `${n} zone(s)`, financeTitle: "Cash discrepancies", expected: "Expected", reported: "Reported",
      difference: "Difference", openCase: "Open review case", caseOpened: "Review case opened — original amounts are never edited",
    },
  };
  const ORDER_FLOW = { DELIVERY: ["PENDING_ACCEPTANCE", "ACCEPTED", "PREPARING", "READY", "OUT_FOR_DELIVERY", "COMPLETED"],
    PICKUP: ["PENDING_ACCEPTANCE", "ACCEPTED", "PREPARING", "READY", "COMPLETED"] };
  const state = { lang: new URLSearchParams(location.search).get("lang") === "en" ? "en" : "ar", zone: "zone-a",
    basket: null, mode: "DELIVERY", slot: "s1", quantity: 1, order: null, cook: {}, ops: {}, finance: {} };

  const t = () => T[state.lang];
  const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  const money = (minor) => `<bdi>${esc(new Intl.NumberFormat(state.lang === "ar" ? "ar-EG" : "en-EG",
    { style: "currency", currency: "EGP" }).format(minor / 100))}</bdi>`;
  const num = (n) => `<bdi>${new Intl.NumberFormat(state.lang === "ar" ? "ar-EG" : "en-EG").format(n)}</bdi>`;
  const meal = (id) => D.meals.find((m) => m.id === id);
  const screen = document.getElementById("screen");
  // Navigate and render in the same tick so the visible screen never lags the URL.
  const go = (hash) => {
    if (location.hash !== hash) history.pushState(null, "", hash);
    render();
    screen.focus();
  };

  function quote() {
    const m = meal(state.basket.mealId);
    const subtotal = m.priceMinor * state.basket.quantity;
    const fee = state.mode === "DELIVERY" ? D.deliveryFeeMinor : 0;
    return { subtotal, fee, tax: D.taxMinor, total: subtotal + fee + D.taxMinor };
  }

  const views = {
    discover() {
      const meals = D.meals.filter((m) => m.zone === state.zone);
      return `<h1>${t().discoverTitle}</h1>
        <label class="field" style="max-width:280px">${t().zone}
          <select data-testid="zone-select">${D.zones.map((z) => `<option value="${z.id}" ${z.id === state.zone ? "selected" : ""}>${esc(z.name[state.lang])}</option>`).join("")}</select></label>
        ${meals.length === 0 ? `<p class="notice info" data-testid="empty-state">${t().noMeals}</p>` : `<div class="grid" style="margin-top:16px">${meals.map((m) => {
          const k = D.kitchens[m.kitchen];
          return `<article class="card" data-testid="meal-card-${m.id}"><div class="photo">${esc(m.title[state.lang])}</div>
            <h2 style="margin:0">${esc(m.title[state.lang])}</h2>
            <span class="muted">${esc(k.name[state.lang])} · ${t().reviews(k.reviews)}</span>
            <div class="row"><span class="price">${money(m.priceMinor)}</span><span class="chip">${t().portions(m.available)}</span></div>
            <a class="btn btn-quiet" href="#/meal/${m.id}" data-testid="open-${m.id}">${t().view}</a></article>`;
        }).join("")}</div>`}`;
    },
    meal(id) {
      const m = meal(id); const k = D.kitchens[m.kitchen];
      const soldOut = m.available === 0;
      return `<div class="layout"><section class="card">
          <div class="photo">${esc(m.title[state.lang])}</div>
          <h1 style="margin:0">${esc(m.title[state.lang])}</h1>
          <span class="muted">${esc(k.name[state.lang])} · ${esc(k.approval[state.lang])}</span>
          <span class="price">${money(m.priceMinor)}</span><span>${esc(m.portion[state.lang])}</span>
          <h2>${t().ingredients}</h2><p style="margin:0">${esc(m.ingredients[state.lang])}</p>
          <h2>${t().allergens}</h2><p style="margin:0" data-testid="allergens">${m.allergens.map((a) => esc(t().allergen[a])).join("، ")}</p>
          <p class="notice warning">${t().allergyNote}</p></section>
        <aside class="card"><h2 style="margin-top:0">${t().chooseSlot}</h2>
          <div class="row">${D.slots.map((s) => `<button type="button" class="btn slot" aria-pressed="${s.id === state.slot}" data-slot="${s.id}" data-testid="slot-${s.id}">${esc(s.label[state.lang])}</button>`).join("")}</div>
          <label class="field">${t().quantity}<input type="number" min="1" max="${Math.max(m.available, 1)}" value="${state.quantity}" data-testid="quantity" inputmode="numeric"></label>
          <span class="chip">${t().portions(m.available)}</span>
          <button type="button" class="btn btn-primary" data-testid="add-to-basket" data-meal="${m.id}" ${soldOut ? "disabled" : ""}>${soldOut ? t().soldOut : t().addToBasket}</button>
        </aside></div>`;
    },
    checkout() {
      if (!state.basket) { return views.discover(); }
      const m = meal(state.basket.mealId); const q = quote();
      const stockChanged = state.basket.quantity > m.available;
      return `<h1>${t().checkoutTitle}</h1><div class="layout"><section class="card">
          ${stockChanged ? `<p class="notice warning" role="alert" data-testid="stock-changed">${t().stockChanged}</p>` : ""}
          <p style="margin:0">${num(state.basket.quantity)} × ${esc(m.title[state.lang])} — ${esc(D.slots.find((s) => s.id === state.slot).label[state.lang])}</p>
          <fieldset style="border:0;padding:0;margin:0"><legend class="muted">${t().fulfillment}</legend>
            <div class="row">${["DELIVERY", "PICKUP"].map((mode) => `<button type="button" class="btn slot" aria-pressed="${state.mode === mode}" data-mode="${mode}" data-testid="mode-${mode.toLowerCase()}">${mode === "DELIVERY" ? t().delivery : t().pickup}</button>`).join("")}</div></fieldset>
          ${state.mode === "DELIVERY" ? `<p style="margin:0"><span class="muted">${t().address}:</span> ${esc(D.address[state.lang])}</p>` : ""}
          <p class="notice info" style="margin:0">${t().cash}<br>${t().deadline}</p>
          <label class="row"><input type="checkbox" data-testid="accept-policy" style="min-height:auto;inline-size:24px;block-size:24px"> ${t().policy}</label>
        </section><aside class="card"><table class="totals" data-testid="totals">
          <tr><td>${t().subtotal}</td><td>${money(q.subtotal)}</td></tr>
          <tr><td>${t().deliveryFee}</td><td>${money(q.fee)}</td></tr>
          <tr><td>${t().tax}</td><td>${money(q.tax)}</td></tr>
          <tr class="total"><td>${t().total}</td><td data-testid="total">${money(q.total)}</td></tr></table>
          <span class="muted">${t().sampleValues}</span>
          <button type="button" class="btn btn-primary" data-testid="place-order" ${stockChanged ? "disabled" : ""}>${t().placeOrder}</button>
          <div data-testid="checking" hidden class="row"><span class="spinner" aria-hidden="true"></span><span role="status">${t().checking}</span></div>
        </aside></div>`;
    },
    order() {
      if (!state.order) { return views.discover(); }
      const flow = ORDER_FLOW[state.order.mode]; const idx = flow.indexOf(state.order.state);
      const showCode = ["READY", "OUT_FOR_DELIVERY"].includes(state.order.state);
      return `<h1>${t().orderTitle} <bdi>${state.order.number}</bdi></h1><div class="layout"><section class="card">
          <p class="status" data-testid="order-state">${t().states[state.order.state]}</p>
          ${state.order.state === "PENDING_ACCEPTANCE" ? `<p class="notice info" style="margin:0">${t().waiting}</p>` : ""}
          ${state.order.state === "COMPLETED" ? `<p class="notice success" style="margin:0">${t().states.COMPLETED}</p>` : ""}
          <ol class="timeline">${flow.map((s, i) => `<li class="${i < idx ? "done" : i === idx ? "current" : ""}"><span class="dot"></span>${t().states[s]}</li>`).join("")}</ol>
          ${showCode ? `<div><span class="muted">${t().handover}</span><div class="code" data-testid="handover-code">482 915</div></div>` : ""}
        </section><aside class="card"><table class="totals"><tr class="total"><td>${t().total}</td><td>${money(state.order.total)}</td></tr></table>
          <span class="muted">${t().cash}</span>
          ${state.order.state !== "COMPLETED" ? `<button type="button" class="btn btn-quiet" data-testid="advance">${t().advance}</button>` : ""}
          <a class="btn btn-quiet" href="#/order">${t().support}</a></aside></div>`;
    },
    cook() {
      return `<h1>${t().cookTitle}</h1><div class="table-scroll"><table class="queue"><thead><tr><th>${t().order}</th><th>${t().items}</th><th>${t().fulfillment}</th><th>${t().action}</th></tr></thead><tbody>
        ${D.cookQueue.map((o) => { const done = state.cook[o.number];
          return `<tr data-testid="queue-${o.number}"><td><bdi>${o.number}</bdi><div class="chip">${t().acceptBy(o.acceptByMin)}</div></td><td>${esc(o.items[state.lang])}</td><td>${t().mode[o.mode]}</td>
          <td>${done ? `<span class="status" data-testid="queue-result-${o.number}">${done === "accepted" ? t().accepted : t().rejected}</span>` :
            `<div class="row"><button type="button" class="btn btn-accent" data-cook="accept" data-order="${o.number}" data-testid="accept-${o.number}">${t().accept}</button>
             <button type="button" class="btn btn-quiet" data-cook="reject" data-order="${o.number}" data-testid="reject-${o.number}">${t().reject}</button></div>`}</td></tr>`; }).join("")}
        </tbody></table></div>`;
    },
    ops() {
      return `<h1>${t().opsTitle}</h1>${D.applications.map((a) => `<article class="card" data-testid="application-${a.id}"><h2 style="margin:0">${esc(a.name[state.lang])}</h2>
          <span class="muted">${esc(a.submitted[state.lang])} · ${t().docs(a.docs)} · ${t().zonesN(a.zones)}</span>
          ${state.ops[a.id] ? `<p class="notice success" data-testid="application-result">${t().approved}</p>` :
            `<div class="row"><button type="button" class="btn btn-accent" data-ops="approve" data-app="${a.id}" data-testid="approve-${a.id}">${t().approve}</button>
             <button type="button" class="btn btn-quiet">${t().requestChanges}</button></div>`}</article>`).join("")}
        <h1 style="margin-top:32px">${t().financeTitle}</h1><div class="table-scroll"><table class="queue"><thead><tr><th>${t().order}</th><th>${t().expected}</th><th>${t().reported}</th><th>${t().difference}</th><th>${t().action}</th></tr></thead><tbody>
        ${D.cashExceptions.map((c) => `<tr><td><bdi>${c.number}</bdi></td><td>${money(c.expectedMinor)}</td><td>${money(c.reportedMinor)}</td><td>${money(c.reportedMinor - c.expectedMinor)}</td>
          <td>${state.finance[c.number] ? `<span class="status" data-testid="case-opened">${t().caseOpened}</span>` : `<button type="button" class="btn btn-quiet" data-finance="${c.number}" data-testid="open-case-${c.number}">${t().openCase}</button>`}</td></tr>`).join("")}
        </tbody></table></div>`;
    },
  };

  function render() {
    document.documentElement.lang = state.lang;
    document.documentElement.dir = state.lang === "ar" ? "rtl" : "ltr";
    document.title = `${t().brand} — UX prototype (draft)`;
    document.querySelector("[data-testid=prototype-banner]").textContent = t().banner;
    document.querySelector("[data-testid=brand]").textContent = t().brand;
    document.querySelector("[data-testid=lang-toggle]").textContent = t().lang;
    const [, route, arg] = (location.hash || "#/discover").split("/");
    const area = route === "cook" ? "cook" : route === "ops" ? "ops" : "discover";
    document.querySelector(".areas").innerHTML = [["discover", t().areaCustomer], ["cook", t().areaCook], ["ops", t().areaOps]]
      .map(([r, label]) => `<a href="#/${r}" data-testid="area-${r}" ${r === area ? 'aria-current="page"' : ""}>${label}</a>`).join("");
    screen.innerHTML = (views[route] || views.discover)(arg);
  }

  document.addEventListener("click", (e) => {
    const link = e.target.closest("a[href^='#/']");
    if (link) { e.preventDefault(); go(link.getAttribute("href")); return; }
    const el = e.target.closest("button");
    if (!el) return;
    if (el.dataset.testid === "lang-toggle") { state.lang = state.lang === "ar" ? "en" : "ar"; render(); return; }
    if (el.dataset.slot) { state.slot = el.dataset.slot; render(); return; }
    if (el.dataset.mode) { state.mode = el.dataset.mode; render(); return; }
    if (el.dataset.testid === "add-to-basket") {
      const qty = Number(screen.querySelector("[data-testid=quantity]").value) || 1;
      state.basket = { mealId: el.dataset.meal, quantity: qty }; go("#/checkout"); return;
    }
    if (el.dataset.testid === "place-order") {
      const policy = screen.querySelector("[data-testid=accept-policy]");
      if (!policy.checked) { policy.focus(); policy.setAttribute("aria-invalid", "true"); return; }
      el.disabled = true; screen.querySelector("[data-testid=checking]").hidden = false;
      setTimeout(() => {
        state.order = { number: "MB-T-2001", mode: state.mode, state: "PENDING_ACCEPTANCE", total: quote().total };
        go("#/order");
      }, 600);
      return;
    }
    if (el.dataset.testid === "advance") {
      const flow = ORDER_FLOW[state.order.mode]; state.order.state = flow[flow.indexOf(state.order.state) + 1]; render(); return;
    }
    if (el.dataset.cook) { state.cook[el.dataset.order] = el.dataset.cook === "accept" ? "accepted" : "rejected"; render(); return; }
    if (el.dataset.ops) { state.ops[el.dataset.app] = true; render(); return; }
    if (el.dataset.finance) { state.finance[el.dataset.finance] = true; render(); }
  });
  document.addEventListener("change", (e) => {
    if (e.target.dataset.testid === "zone-select") { state.zone = e.target.value; render(); }
  });
  window.addEventListener("hashchange", () => { render(); screen.focus(); });
  window.addEventListener("popstate", () => { render(); screen.focus(); });
  render();
})();
