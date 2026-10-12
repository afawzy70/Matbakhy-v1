import { useRef, useState } from "react";
import { formatMoney, formatTime } from "./api";
import { loadCart, loadPending, saveCart, type Cart } from "./cart";
import { ErrorLine, Status, useMutation, useServer, type Ctx } from "./shared";

/** Quantity is bounded by the chosen slot's remaining portions (server value) and the order line maximum of 20. */
function maxQuantity(slot: { availablePortions: number } | null): number {
  return slot ? Math.max(1, Math.min(20, slot.availablePortions)) : 20;
}

export function Meal({ ctx, id, signedIn }: { ctx: Ctx; id: string; signedIn: boolean }) {
  const { t, locale } = ctx;
  const [meal, reload] = useServer(ctx, () => ctx.api.GET("/api/v1/meals/{mealId}", { params: { path: { mealId: id } } }), `${id}|${locale}`);
  const [slotId, setSlotId] = useState<string | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [message, setMessage] = useState<string | null>(null);
  // BR-018: a basket holds one kitchen and one slot; replacing it needs the customer's explicit confirmation.
  const [replacement, setReplacement] = useState<Cart | null>(null);
  // When the replacement prompt closes, focus returns to the button that opened it (keyboard and screen readers).
  const addButton = useRef<HTMLButtonElement>(null);
  const closePrompt = () => {
    setReplacement(null);
    window.setTimeout(() => addButton.current?.focus(), 0);
  };
  return (
    <Status ctx={ctx} state={meal} onRetry={reload}>
      {(m) => {
        const slot = m.nextSlots.find((s) => s.id === slotId) ?? null;
        // CW03 / BR-013: the selected slot itself must have the portions; the meal being AVAILABLE through another slot is not enough.
        const slotExhausted = slot !== null && slot.availablePortions < 1;
        const purchasable = slot !== null && m.availability === "AVAILABLE" && !slotExhausted && quantity <= slot.availablePortions;
        return (
          <article className="meal-detail stack" aria-labelledby="meal-title">
            <a href="#/">{t.back}</a>
            {m.photo ? <img className="hero" src={m.photo.url} alt={m.photo.alt} /> : <div className="hero" aria-hidden="true" />}
            <div className="row between">
              <h2 id="meal-title">{m.title}</h2>
              <span className="price">
                <bdi>{formatMoney(m.price.amountMinor, locale)}</bdi>
              </span>
            </div>
            <a className="kitchen-link" href={`#/kitchen/${m.kitchen.slug}`}>
              {m.kitchen.name}
            </a>
            <section className="card pad stack-sm" aria-label={t.portion}>
              <strong>{m.portionText}</strong>
            </section>
            <section className="stack-sm" aria-labelledby="about-heading">
              <h3 id="about-heading">{t.aboutDish}</h3>
              <p>{m.description}</p>
            </section>
            <section className="card pad stack-sm" aria-labelledby="ingredients-heading">
              <h3 id="ingredients-heading">{t.ingredientsAllergens}</h3>
              <p>{m.ingredients}</p>
              <p className={m.allergenCodes.includes("UNKNOWN") ? "notice" : undefined}>
                {m.allergenCodes.includes("UNKNOWN")
                  ? t.allergenUnknown
                  : m.allergenCodes.includes("NONE_DECLARED")
                    ? t.allergenNone
                    : `${t.allergenContains}: ${m.allergenCodes.map((a) => t.allergenNames[a] ?? a).join("، ")}`}
                {m.allergenStatement ? ` — ${m.allergenStatement}` : ""}
              </p>
            </section>
            <fieldset className="slots">
              <legend>{t.slots}</legend>
              {m.nextSlots.length === 0 ? <p className="muted">{t.noSlots}</p> : null}
              {m.nextSlots.map((s) => (
                <label key={s.id} className="slot-option">
                  <input
                    type="radio"
                    name="slot"
                    checked={slotId === s.id}
                    onChange={() => {
                      setSlotId(s.id);
                      setQuantity((q) => Math.min(q, Math.max(1, Math.min(20, s.availablePortions))));
                    }}
                  />
                  <span>
                    {formatTime(s.startsAt, locale)} · {t.portionsLeft(s.availablePortions)} · {s.fulfillmentModes.map((f) => t.mode[f]).join(" / ")}
                  </span>
                </label>
              ))}
            </fieldset>
            <div className="buy-bar">
              <div className="qty" role="group" aria-label={t.quantityGroup}>
                <button type="button" className="secondary" aria-label={t.decrease} disabled={quantity <= 1} onClick={() => setQuantity(quantity - 1)}>
                  −
                </button>
                <input
                  type="number"
                  aria-label={t.quantity}
                  min={1}
                  max={maxQuantity(slot)}
                  value={quantity}
                  onChange={(e) => setQuantity(Math.max(1, Math.min(maxQuantity(slot), Number(e.target.value) || 1)))}
                />
                <button type="button" className="secondary" aria-label={t.increase} disabled={quantity >= maxQuantity(slot)} onClick={() => setQuantity(quantity + 1)}>
                  +
                </button>
              </div>
            <button
              ref={addButton}
              type="button"
              disabled={!purchasable}
              onClick={() => {
                if (!slot || !purchasable) {
                  return;
                }
                const cart = loadCart();
                const next: Cart = { kitchenId: m.kitchen.id, slotId: slot.id, slotStartsAt: slot.startsAt, modes: slot.fulfillmentModes, lines: [] };
                const line = { mealId: m.id, title: m.title, quantity };
                if (cart && cart.lines.length > 0 && (cart.kitchenId !== m.kitchen.id || cart.slotId !== slot.id)) {
                  if (loadPending()) {
                    // An order may already exist for the current basket; it is resolved at checkout before any change.
                    setMessage(t.placementPending);
                    return;
                  }
                  setMessage(null);
                  setReplacement({ ...next, lines: [line] });
                  return;
                }
                const lines = (cart?.lines ?? []).filter((l) => l.mealId !== m.id);
                lines.push(line);
                saveCart({ ...next, lines });
                setReplacement(null);
                setMessage(t.added);
              }}
            >
              {t.addToCart} · <bdi>{formatMoney(m.price.amountMinor * quantity, locale)}</bdi>
            </button>
            </div>
            {slotExhausted ? <p className="notice">{t.slotSoldOut}</p> : null}
            <p className="muted small">{t.totalNote}</p>
            {replacement ? (
              <div
                role="alertdialog"
                aria-labelledby="replace-title"
                aria-describedby="replace-text"
                className="notice stack"
                onKeyDown={(e) => {
                  if (e.key === "Escape") {
                    closePrompt();
                    setMessage(t.basketKept);
                  }
                }}
              >
                <h3 id="replace-title">{t.replaceTitle}</h3>
                <p id="replace-text">{t.otherKitchen}</p>
                <div className="row">
                  <button
                    type="button"
                    className="danger"
                    onClick={() => {
                      saveCart(replacement);
                      closePrompt();
                      setMessage(t.added);
                    }}
                  >
                    {t.replaceBasket}
                  </button>
                  <button
                    type="button"
                    className="secondary"
                    autoFocus
                    onClick={() => {
                      closePrompt();
                      setMessage(t.basketKept);
                    }}
                  >
                    {t.keepBasket}
                  </button>
                </div>
              </div>
            ) : null}
            {message ? <p role="status">{message}</p> : null}
            <ShareLink ctx={ctx} mealId={m.id} />
            {signedIn ? <FavoriteToggle ctx={ctx} mealId={m.id} /> : null}
            <KitchenReviews ctx={ctx} kitchenId={m.kitchen.id} signedIn={signedIn} />
          </article>
        );
      }}
    </Status>
  );
}

/** Favorite state comes from the customer's own list; adding and removing are idempotent on the server. */
function FavoriteToggle({ ctx, mealId }: { ctx: Ctx; mealId: string }) {
  const { t } = ctx;
  const [favorites, reload] = useServer(ctx, () => ctx.api.GET("/api/v1/me/favorites", { params: { query: { limit: 100 } } }), mealId);
  const { run, error, busy } = useMutation(ctx, reload);
  if (favorites.status !== "ok") {
    return null;
  }
  const isFavorite = favorites.data.items.some((f) => f.mealId === mealId);
  return (
    <div>
      <button
        type="button"
        className="secondary"
        aria-pressed={isFavorite}
        disabled={busy}
        onClick={() =>
          void run(() =>
            isFavorite
              ? ctx.api.DELETE("/api/v1/me/favorites/{mealId}", { params: { path: { mealId } } })
              : ctx.api.PUT("/api/v1/me/favorites/{mealId}", { params: { path: { mealId } } }),
          )
        }
      >
        {isFavorite ? t.unfavorite : t.favorite}
      </button>
      <ErrorLine message={error} />
    </div>
  );
}

const REPORT_REASONS = ["OFFENSIVE", "PRIVATE_INFO", "SPAM", "NOT_ABOUT_ORDER", "OTHER"] as const;

/** Visible reviews only, with the server's visible-only rating summary. No customer or order data is shown. */
function KitchenReviews({ ctx, kitchenId, signedIn }: { ctx: Ctx; kitchenId: string; signedIn: boolean }) {
  const { t, locale } = ctx;
  const [reviews, reload] = useServer(ctx, () => ctx.api.GET("/api/v1/kitchens/{kitchenId}/reviews", { params: { path: { kitchenId }, query: { limit: 20 } } }), `${kitchenId}|${locale}`);
  const { run, error } = useMutation(ctx, reload);
  const [reported, setReported] = useState<string[]>([]);
  return (
    <section aria-labelledby="reviews-heading" className="stack">
      <h3 id="reviews-heading">{t.reviews}</h3>
      <Status ctx={ctx} state={reviews} onRetry={reload}>
        {(page) => (
          <>
            {page.summary.averageTenths != null ? (
              <p>{t.ratingOf((page.summary.averageTenths / 10).toLocaleString(locale === "ar" ? "ar-EG" : "en-EG", { minimumFractionDigits: 1 }), page.summary.count)}</p>
            ) : (
              <p className="muted">{t.noReviews}</p>
            )}
            <ul className="rows">
              {page.items.map((r) => (
                <li key={r.id}>
                  <span aria-label={`${r.rating}/5`}>{"★".repeat(r.rating)}</span> {r.text ?? ""} <span className="muted">{formatTime(r.createdAt, locale)}</span>
                  {signedIn && !reported.includes(r.id) ? (
                    <select
                      aria-label={t.report}
                      value=""
                      onChange={(e) => {
                        const reasonCode = e.target.value as (typeof REPORT_REASONS)[number];
                        void run(() => ctx.api.POST("/api/v1/reviews/{reviewId}/reports", { params: { path: { reviewId: r.id } }, body: { reasonCode } })).then(
                          (ok) => ok && setReported((list) => [...list, r.id]),
                        );
                      }}
                    >
                      <option value="">{t.report}</option>
                      {REPORT_REASONS.map((code) => (
                        <option key={code} value={code}>
                          {t.reportReasons[code]}
                        </option>
                      ))}
                    </select>
                  ) : null}
                  {reported.includes(r.id) ? <span role="status"> {t.reported}</span> : null}
                </li>
              ))}
            </ul>
          </>
        )}
      </Status>
      <ErrorLine message={error} />
    </section>
  );
}

/** The public meal address; private order links are never shared from here. Clipboard failure leaves the link selectable. */
function ShareLink({ ctx, mealId }: { ctx: Ctx; mealId: string }) {
  const { t } = ctx;
  const [copied, setCopied] = useState(false);
  const link = `${window.location.origin}${window.location.pathname}#/meal/${mealId}`;
  return (
    <p className="row">
      <bdi dir="ltr" className="muted">
        {link}
      </bdi>
      <button
        type="button"
        className="secondary"
        onClick={() => {
          void navigator.clipboard?.writeText(link).then(
            () => setCopied(true),
            () => setCopied(false),
          );
        }}
      >
        {t.shareLink}
      </button>
      {copied ? <span role="status">{t.linkCopied}</span> : null}
    </p>
  );
}
