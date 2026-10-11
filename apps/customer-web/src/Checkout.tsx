import { useEffect, useState } from "react";
import { errorCode, formatMoney, formatTime, loginUrl, newIdempotencyKey, type Schemas } from "./api";
import { loadCart, loadPending, saveCart, savePending, type PendingPlacement } from "./cart";
import { errorText } from "./i18n";
import { ErrorLine, Status, useMutation, useServer, type Ctx } from "./shared";

type Me = Schemas["CustomerProfile"];
type Quote = Schemas["QuoteResponse"];

export function Checkout({ ctx, me, onMeChanged }: { ctx: Ctx; me: Me | null; onMeChanged: () => void }) {
  const { t, locale } = ctx;
  const [cart, setCart] = useState(loadCart);
  if (!cart || cart.lines.length === 0) {
    return <p className="card muted">{t.cartEmpty}</p>;
  }
  return (
    <div className="stack">
      <section className="card" aria-labelledby="cart-heading">
        <h2 id="cart-heading">{t.nav.cart}</h2>
        <ul className="rows">
          {cart.lines.map((l) => (
            <li key={l.mealId}>
              {l.quantity} × {l.title}
            </li>
          ))}
        </ul>
        <p className="muted">{formatTime(cart.slotStartsAt, locale)}</p>
        <button
          type="button"
          className="secondary"
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
        <OrderForm ctx={ctx} cart={cart} />
      )}
    </div>
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

function OrderForm({ ctx, cart }: { ctx: Ctx; cart: NonNullable<ReturnType<typeof loadCart>> }) {
  const { t, locale } = ctx;
  const [mode, setMode] = useState<"DELIVERY" | "PICKUP">(cart.modes.includes("DELIVERY") ? "DELIVERY" : "PICKUP");
  const [addressId, setAddressId] = useState<string | null>(null);
  const [quote, setQuote] = useState<Quote | null>(null);
  const [accepted, setAccepted] = useState(false);
  const [pending, setPending] = useState<PendingPlacement | null>(loadPending);
  const [placing, setPlacing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [addresses, reloadAddresses] = useServer(ctx, () => ctx.api.GET("/api/v1/me/addresses"));
  const quoting = useMutation(ctx, () => undefined);

  // After a reload or a lost response, find out whether the pending order was created.
  useEffect(() => {
    const p = loadPending();
    if (!p) {
      return;
    }
    let active = true;
    ctx.api.GET("/api/v1/orders/by-request/{clientRequestId}", { params: { path: { clientRequestId: p.clientRequestId } } }).then((r) => {
      if (active && r.data) {
        savePending(null);
        saveCart(null);
        ctx.go(`/orders/${r.data.id}`);
      }
    });
    return () => {
      active = false;
    };
  }, [ctx]);

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
      setQuote(null);
      setError(errorText(t, errorCode(r.error)));
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
      <div role="group" aria-label={t.checkout} className="segmented">
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
                <label key={a.id} className="check">
                  <input
                    type="radio"
                    name="address"
                    checked={addressId === a.id}
                    onChange={() => {
                      setAddressId(a.id);
                      setQuote(null);
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
        disabled={quoting.busy || (mode === "DELIVERY" && !addressId)}
        onClick={() =>
          void quoting.run(async () => {
            const r = await ctx.api.POST("/api/v1/quotes", {
              body: {
                items: cart.lines.map((l) => ({ mealId: l.mealId, quantity: l.quantity })),
                slotId: cart.slotId,
                fulfillmentMode: mode,
                ...(mode === "DELIVERY" && addressId ? { addressId } : {}),
              },
            });
            setQuote(r.data ?? null);
            return r;
          })
        }
      >
        {t.getQuote}
      </button>
      <ErrorLine message={quoting.error} />
      {quote ? (
        <div className="stack">
          <dl className="summary">
            <dt>{t.subtotal}</dt>
            <dd>{formatMoney(quote.subtotal.amountMinor, locale)}</dd>
            <dt>{t.deliveryFee}</dt>
            <dd>{formatMoney(quote.deliveryFee.amountMinor, locale)}</dd>
            <dt>{t.tax}</dt>
            <dd>{formatMoney(quote.tax.amountMinor, locale)}</dd>
            <dt>{t.total}</dt>
            <dd>
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
            disabled={!accepted || placing || pending !== null}
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
