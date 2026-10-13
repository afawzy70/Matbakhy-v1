import { priceChanges } from "@matbakhy/api-client";
import { useEffect, useState } from "react";
import { errorCode, formatMoney, formatTime, loginUrl, newIdempotencyKey, type Schemas } from "./api";
import { estimateMinor, lineLimit, loadCart, loadPending, saveCart, savePending, type CartLine, type PendingPlacement } from "./cart";
import { errorText } from "./i18n";
import { ErrorLine, Status, useMutation, useServer, type Ctx } from "./shared";

type Me = Schemas["CustomerProfile"];
type Quote = Schemas["QuoteResponse"];

export function Checkout({ ctx, me, onMeChanged }: { ctx: Ctx; me: Me | null; onMeChanged: () => void }) {
  const { t, locale } = ctx;
  const [cart, setCart] = useState(loadCart);
  // While an order from this basket may exist, the basket cannot change (CW05: no surprise changes to a sent order).
  const [locked, setLocked] = useState(() => loadPending() !== null);
  const limit = lineLimit(ctx.maxQuantityPerLine);
  if (!cart || cart.lines.length === 0) {
    return (
      <div className="transaction">
        <p className="card muted">{t.cartEmpty}</p>
        <a className="button" href="#/">
          {t.nav.browse}
        </a>
      </div>
    );
  }
  return (
    <div className="transaction stack">
      <section className="card" aria-labelledby="cart-heading">
        <h2 id="cart-heading">{t.nav.cart}</h2>
        {cart.kitchenName ? (
          <p>
            <span className="muted">{t.kitchenLabel}: </span>
            <strong>{cart.kitchenName}</strong>
          </p>
        ) : null}
        <p>
          <span className="pill slot-pill">{formatTime(cart.slotStartsAt, locale)}</span>
        </p>
        <ul className="line-items">
          {cart.lines.map((l) => (
            <CartLineRow
              key={l.mealId}
              ctx={ctx}
              line={l}
              locked={locked}
              limit={limit}
              onChange={(next) => {
                const lines = cart.lines.flatMap((x) => (x.mealId !== l.mealId ? [x] : next ? [next] : []));
                const updated = lines.length > 0 ? { ...cart, lines } : null;
                saveCart(updated);
                setCart(updated);
              }}
            />
          ))}
        </ul>
        <dl className="summary">
          <dt>{t.estimatedSubtotal}</dt>
          <dd>
            <bdi>{estimateText(cart, locale, t.estimateUnavailable)}</bdi>
          </dd>
        </dl>
        <p className="muted small">{t.estimateNote}</p>
        <button
          type="button"
          className="secondary"
          disabled={locked}
          onClick={() => {
            saveCart(null);
            setCart(null);
          }}
        >
          {t.clearCart}
        </button>
      </section>
      {!me ? (
        <section className="card">
          <p>{t.signInToOrder}</p>
          <a className="button" href={loginUrl(window.location.href)}>
            {t.signIn}
          </a>
        </section>
      ) : !me.phoneVerified ? (
        <PhoneVerification ctx={ctx} onVerified={onMeChanged} />
      ) : (
        // Any edit changes the key, so a quote for the previous basket can never be accepted for the new one.
        <OrderForm
          key={cart.lines.map((l) => `${l.mealId}:${l.quantity}`).join("|")}
          ctx={ctx}
          cart={cart}
          onPending={setLocked}
          overLimit={cart.lines.some((l) => l.quantity > limit)}
        />
      )}
    </div>
  );
}

function estimateText(cart: NonNullable<ReturnType<typeof loadCart>>, locale: string, unavailable: string): string {
  const amount = estimateMinor(cart);
  return amount === null ? unavailable : formatMoney(amount, locale);
}

/** One basket line: quantity within 1..maxQuantity (at most 20), removal, and the line estimate when known. */
function CartLineRow({ ctx, line, locked, limit, onChange }: { ctx: Ctx; line: CartLine; locked: boolean; limit: number; onChange: (next: CartLine | null) => void }) {
  const { t, locale } = ctx;
  const max = Math.min(limit, line.maxQuantity ?? limit);
  return (
    <li className="cart-line">
      <span className="cart-line-title">{line.title}</span>
      <div className="qty" role="group" aria-label={t.quantityOf(line.title)}>
        <button type="button" className="secondary" aria-label={t.decreaseOf(line.title)} disabled={locked || line.quantity <= 1} onClick={() => onChange({ ...line, quantity: line.quantity - 1 })}>
          −
        </button>
        <output aria-live="polite">{line.quantity}</output>
        <button type="button" className="secondary" aria-label={t.increaseOf(line.title)} disabled={locked || line.quantity >= max} onClick={() => onChange({ ...line, quantity: line.quantity + 1 })}>
          +
        </button>
      </div>
      {line.unitPriceMinor !== undefined ? <bdi className="muted">{formatMoney(line.unitPriceMinor * line.quantity, locale)}</bdi> : null}
      <button type="button" className="secondary" aria-label={t.removeLine(line.title)} disabled={locked} onClick={() => onChange(null)}>
        {t.remove}
      </button>
      {line.quantity > limit ? (
        <p role="alert" className="notice cart-line-notice">
          {t.overLineLimit(limit)}
        </p>
      ) : null}
    </li>
  );
}

function PhoneVerification({ ctx, onVerified }: { ctx: Ctx; onVerified: () => void }) {
  const { t } = ctx;
  const [phone, setPhone] = useState("+20");
  const [challenge, setChallenge] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const { run, error, busy } = useMutation(ctx, () => undefined);
  return (
    <section className="card form" aria-labelledby="contact-heading">
      <h2 id="contact-heading">{t.contact}</h2>
      <label>
        {t.phone} <input dir="ltr" inputMode="tel" value={phone} onChange={(e) => setPhone(e.target.value.trim())} />
      </label>
      <button
        type="button"
        disabled={busy}
        onClick={() =>
          void run(async () => {
            const r = await ctx.api.POST("/api/v1/me/phone/challenges", { body: { phoneE164: phone } });
            if (r.data) {
              setChallenge(r.data.id);
            }
            return r;
          })
        }
      >
        {t.sendCode}
      </button>
      {challenge ? (
        <>
          <label>
            {t.code} <input inputMode="numeric" autoComplete="one-time-code" value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))} />
          </label>
          <button
            type="button"
            disabled={busy || code.length < 4}
            onClick={() =>
              void run(() => ctx.api.POST("/api/v1/me/phone/challenges/{challengeId}/verify", { params: { path: { challengeId: challenge } }, body: { code } })).then(
                (ok) => ok && onVerified(),
              )
            }
          >
            {t.verify}
          </button>
        </>
      ) : null}
      <ErrorLine message={error} />
    </section>
  );
}

function OrderForm({
  ctx,
  cart,
  onPending,
  overLimit,
}: {
  ctx: Ctx;
  cart: NonNullable<ReturnType<typeof loadCart>>;
  onPending: (pending: boolean) => void;
  /** A line is above the server's current per-line limit: no quote until the customer reduces it (Q1). */
  overLimit: boolean;
}) {
  const { t, locale } = ctx;
  const [mode, setMode] = useState<"DELIVERY" | "PICKUP">(cart.modes.includes("DELIVERY") ? "DELIVERY" : "PICKUP");
  const [addressId, setAddressId] = useState<string | null>(null);
  const [quote, setQuote] = useState<Quote | null>(null);
  const [accepted, setAccepted] = useState(false);
  const [pending, setPending] = useState<PendingPlacement | null>(loadPending);
  const [placing, setPlacing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // D13: a price that changed before placement is shown and must be accepted explicitly before the new quote is placed.
  const [change, setChange] = useState<{ previous: Quote | null; next: Quote } | null>(null);
  const [changeAccepted, setChangeAccepted] = useState(false);
  const [addresses, reloadAddresses] = useServer(ctx, () => ctx.api.GET("/api/v1/me/addresses"));
  const quoting = useMutation(ctx, () => undefined);

  // After a reload or a lost response, find out whether the pending order was created.
  useEffect(() => {
    const p = loadPending();
    if (!p) {
      return;
    }
    let active = true;
    ctx.api
      .GET("/api/v1/orders/by-request/{clientRequestId}", { params: { path: { clientRequestId: p.clientRequestId } } })
      .then((r) => {
        if (active && r.data) {
          savePending(null);
          saveCart(null);
          ctx.go(`/orders/${r.data.id}`);
        }
      })
      // OBS-001: unreachable lookup: keep the pending order and its keys; the screen stays "unconfirmed" with the retry.
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [ctx]);

  function quoteRequest() {
    return {
      items: cart.lines.map((l) => ({ mealId: l.mealId, quantity: l.quantity })),
      slotId: cart.slotId,
      fulfillmentMode: mode,
      ...(mode === "DELIVERY" && addressId ? { addressId } : {}),
    };
  }

  /** After a refused stale quote: a fresh server quote for the same basket, shown against the one the customer saw. */
  async function requote(previous: Quote | null) {
    setChangeAccepted(false);
    setAccepted(false);
    try {
      const r = await ctx.api.POST("/api/v1/quotes", { body: quoteRequest() });
      if (r.data) {
        setQuote(r.data);
        setChange({ previous, next: r.data });
        return;
      }
    } catch {
      // Unreachable: fall through; the basket stays and "Calculate total" can be pressed again.
    }
    setQuote(null);
    setChange(null);
    setError(t.requoteFailed);
  }

  async function place(p: PendingPlacement) {
    setPlacing(true);
    setError(null);
    try {
      const r = await ctx.api.POST("/api/v1/orders", {
        params: { header: { "Idempotency-Key": p.idempotencyKey } },
        body: { quoteId: p.quoteId, clientRequestId: p.clientRequestId, acceptedPolicyVersions: { terms: p.terms, cancellation: p.cancellation } },
      });
      if (r.data) {
        savePending(null);
        saveCart(null);
        ctx.go(`/orders/${r.data.id}`);
        return;
      }
      if (r.response.status >= 500) {
        setError(t.pendingRetry);
        return;
      }
      // The server answered definitively; this request can never succeed with these keys.
      savePending(null);
      setPending(null);
      onPending(false);
      const code = errorCode(r.error);
      if (code === "PRICE_CHANGED") {
        // Nothing was ordered: re-quote and let the customer review and accept the new prices (never resubmit silently).
        await requote(quote);
        return;
      }
      setQuote(null);
      setChange(null);
      setError(errorText(t, code));
    } catch {
      setError(t.pendingRetry);
    } finally {
      setPlacing(false);
    }
  }

  return (
    <section className="card form" aria-labelledby="order-heading">
      <h2 id="order-heading">{t.checkout}</h2>
      {pending ? (
        <div className="notice">
          <p>{t.pendingRetry}</p>
          <button type="button" disabled={placing} onClick={() => void place(pending)}>
            {t.retryOrder}
          </button>
        </div>
      ) : null}
      <div role="group" aria-label={t.checkout} className="mode-switch">
        {(["DELIVERY", "PICKUP"] as const)
          .filter((m) => cart.modes.includes(m))
          .map((m) => (
            <button
              key={m}
              type="button"
              aria-pressed={mode === m}
              onClick={() => {
                setMode(m);
                setQuote(null);
                setChange(null);
              }}
            >
              {t.mode[m]}
            </button>
          ))}
      </div>
      {mode === "DELIVERY" ? (
        <Status ctx={ctx} state={addresses} onRetry={reloadAddresses}>
          {(page) => (
            <fieldset>
              <legend>{t.address}</legend>
              {page.items.map((a) => (
                <label key={a.id} className="option-card">
                  <input
                    type="radio"
                    name="address"
                    checked={addressId === a.id}
                    onChange={() => {
                      setAddressId(a.id);
                      setQuote(null);
                      setChange(null);
                    }}
                  />{" "}
                  {a.label} · {a.street} {a.building}
                </label>
              ))}
              <NewAddress ctx={ctx} onSaved={reloadAddresses} />
            </fieldset>
          )}
        </Status>
      ) : null}
      <button
        type="button"
        className="cta"
        disabled={overLimit || quoting.busy || (mode === "DELIVERY" && !addressId)}
        onClick={() =>
          void quoting.run(async () => {
            const r = await ctx.api.POST("/api/v1/quotes", { body: quoteRequest() });
            setQuote(r.data ?? null);
            setChange(null);
            setError(null);
            return r;
          })
        }
      >
        {t.getQuote}
      </button>
      <ErrorLine message={quoting.error} />
      {change ? <PriceChangeNotice ctx={ctx} change={change} accepted={changeAccepted} onAccept={() => setChangeAccepted(true)} /> : null}
      {quote ? (
        <div className="stack">
          <dl className="summary">
            <dt>{t.subtotal}</dt>
            <dd>{formatMoney(quote.subtotal.amountMinor, locale)}</dd>
            <dt>{t.deliveryFee}</dt>
            <dd>{formatMoney(quote.deliveryFee.amountMinor, locale)}</dd>
            <dt>{t.tax}</dt>
            <dd>{formatMoney(quote.tax.amountMinor, locale)}</dd>
            <dt className="summary-total">{t.total}</dt>
            <dd className="summary-total">
              <strong>{formatMoney(quote.total.amountMinor, locale)}</strong>
            </dd>
          </dl>
          <p className="muted">{t.cashOnDelivery}</p>
          {quote.warnings.includes("SYNTHETIC_POLICY") ? <p className="notice">{t.syntheticPolicy}</p> : null}
          <label className="check">
            <input type="checkbox" checked={accepted} onChange={(e) => setAccepted(e.target.checked)} /> {t.terms}
          </label>
          <button
            type="button"
            className="cta"
            disabled={!accepted || placing || pending !== null || (change !== null && !changeAccepted)}
            onClick={() => {
              // Keys are fixed and stored before the request so a retry can never create a second order.
              const p: PendingPlacement = {
                clientRequestId: crypto.randomUUID(),
                idempotencyKey: newIdempotencyKey(),
                quoteId: quote.quoteId,
                terms: quote.termsVersion ?? "",
                cancellation: quote.cancellationPolicyVersion,
              };
              savePending(p);
              setPending(p);
              onPending(true);
              void place(p);
            }}
          >
            {placing ? t.placing : t.placeOrder}
          </button>
        </div>
      ) : null}
      <ErrorLine message={error} />
    </section>
  );
}

/**
 * D13: what changed since the customer's last total, attributed accurately: the kitchen for meal prices, otherwise the
 * delivery fee or tax; the previous and new total; and an explicit acceptance before the new quote can be placed.
 */
function PriceChangeNotice({ ctx, change, accepted, onAccept }: { ctx: Ctx; change: { previous: Quote | null; next: Quote }; accepted: boolean; onAccept: () => void }) {
  const { t, locale } = ctx;
  const money = (minor: number) => formatMoney(minor, locale);
  const d = change.previous ? priceChanges(change.previous, change.next) : null;
  return (
    <div role="alert" className="notice" aria-labelledby="price-change-title" data-testid="price-change">
      <h3 id="price-change-title">{t.priceChangeTitle}</h3>
      {d?.meals.map((m) => (
        <p key={m.mealId}>{t.kitchenChangedPrice(m.title, money(m.previousMinor), money(m.nextMinor))}</p>
      ))}
      {d?.deliveryFee ? <p>{t.deliveryFeeChanged(money(d.deliveryFee.previousMinor), money(d.deliveryFee.nextMinor))}</p> : null}
      {d?.tax ? <p>{t.taxChanged(money(d.tax.previousMinor), money(d.tax.nextMinor))}</p> : null}
      {d?.unexplained ? <p>{t.orderDetailsChanged}</p> : null}
      <p>
        <strong>{d ? t.newTotalReview(money(d.total.previousMinor), money(d.total.nextMinor)) : t.newTotalOnly(money(change.next.total.amountMinor))}</strong>
      </p>
      <p>
        {t.editOrLeave} <a href="#/">{t.backToBrowsing}</a>
      </p>
      {accepted ? (
        <p role="status">{t.newTotalAccepted}</p>
      ) : (
        <button type="button" onClick={onAccept}>
          {t.acceptNewTotal}
        </button>
      )}
    </div>
  );
}

function NewAddress({ ctx, onSaved }: { ctx: Ctx; onSaved: () => void }) {
  const { t } = ctx;
  const [open, setOpen] = useState(false);
  const [zones] = useServer(ctx, () => ctx.api.GET("/api/v1/zones"));
  const [form, setForm] = useState({ label: "", recipientName: "", governorate: "", city: "", street: "", building: "", zoneId: "", phone: "+20" });
  const { run, error, busy } = useMutation(ctx, onSaved);
  if (!open) {
    return (
      <button type="button" className="secondary" onClick={() => setOpen(true)}>
        {t.newAddress}
      </button>
    );
  }
  return (
    <div className="form">
      {(["label", "recipientName", "governorate", "city", "street", "building"] as const).map((f) => (
        <label key={f}>
          {t.addressFields[f]} <input value={form[f]} maxLength={120} onChange={(e) => setForm({ ...form, [f]: e.target.value })} />
        </label>
      ))}
      <label>
        {t.phone} <input dir="ltr" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value.trim() })} />
      </label>
      <label>
        {t.zone}{" "}
        <select value={form.zoneId} onChange={(e) => setForm({ ...form, zoneId: e.target.value })}>
          <option value="">{t.chooseZone}</option>
          {zones.status === "ok"
            ? zones.data.items.map((z) => (
                <option key={z.id} value={z.id}>
                  {z.name}
                </option>
              ))
            : null}
        </select>
      </label>
      <button
        type="button"
        disabled={busy}
        onClick={() =>
          void run(() =>
            ctx.api.POST("/api/v1/me/addresses", {
              body: {
                label: form.label,
                recipientName: form.recipientName,
                governorate: form.governorate,
                city: form.city,
                street: form.street,
                building: form.building,
                zoneId: form.zoneId,
                phone: form.phone,
              },
            }),
          ).then((ok) => ok && setOpen(false))
        }
      >
        {t.saveAddress}
      </button>
      <ErrorLine message={error} />
    </div>
  );
}
