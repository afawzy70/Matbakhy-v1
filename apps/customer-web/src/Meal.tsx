import { useState } from "react";
import { formatMoney, formatTime } from "./api";
import { loadCart, saveCart } from "./cart";
import { ErrorLine, Status, useMutation, useServer, type Ctx } from "./shared";

export function Meal({ ctx, id, signedIn }: { ctx: Ctx; id: string; signedIn: boolean }) {
  const { t, locale } = ctx;
  const [meal, reload] = useServer(ctx, () => ctx.api.GET("/api/v1/meals/{mealId}", { params: { path: { mealId: id } } }), `${id}|${locale}`);
  const [slotId, setSlotId] = useState<string | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [message, setMessage] = useState<string | null>(null);
  return (
    <Status ctx={ctx} state={meal} onRetry={reload}>
      {(m) => {
        const slot = m.nextSlots.find((s) => s.id === slotId) ?? null;
        return (
          <article className="card stack" aria-labelledby="meal-title">
            <a href="#/">{t.back}</a>
            {m.photo ? <img src={m.photo.url} alt={m.photo.alt} /> : null}
            <h2 id="meal-title">{m.title}</h2>
            <p className="muted">
              {m.kitchen.name} · {m.portionText}
            </p>
            <p>
              <strong>{formatMoney(m.price.amountMinor, locale)}</strong>
            </p>
            <p>{m.description}</p>
            <p className="muted">{m.ingredients}</p>
            <p className="notice">
              {m.allergenCodes.join(" · ")}
              {m.allergenStatement ? ` — ${m.allergenStatement}` : ""}
            </p>
            <fieldset>
              <legend>{t.slots}</legend>
              {m.nextSlots.length === 0 ? <p className="muted">{t.noSlots}</p> : null}
              {m.nextSlots.map((s) => (
                <label key={s.id} className="check">
                  <input type="radio" name="slot" checked={slotId === s.id} onChange={() => setSlotId(s.id)} /> {formatTime(s.startsAt, locale)} ·{" "}
                  {t.portionsLeft(s.availablePortions)} · {s.fulfillmentModes.map((f) => t.mode[f]).join(" / ")}
                </label>
              ))}
            </fieldset>
            <label>
              {t.quantity}{" "}
              <input type="number" min={1} max={20} value={quantity} onChange={(e) => setQuantity(Math.max(1, Math.min(20, Number(e.target.value) || 1)))} />
            </label>
            <button
              type="button"
              disabled={!slot || m.availability !== "AVAILABLE"}
              onClick={() => {
                if (!slot) {
                  return;
                }
                const cart = loadCart();
                if (cart && (cart.kitchenId !== m.kitchen.id || cart.slotId !== slot.id)) {
                  setMessage(t.otherKitchen);
                  return;
                }
                const lines = (cart?.lines ?? []).filter((l) => l.mealId !== m.id);
                lines.push({ mealId: m.id, title: m.title, quantity });
                saveCart({ kitchenId: m.kitchen.id, slotId: slot.id, slotStartsAt: slot.startsAt, modes: slot.fulfillmentModes, lines });
                setMessage(t.added);
              }}
            >
              {t.addToCart}
            </button>
            {message ? <p role="status">{message}</p> : null}
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
