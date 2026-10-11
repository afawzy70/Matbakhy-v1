import { useState } from "react";
import { formatMoney, formatTime } from "./api";
import { loadCart, saveCart } from "./cart";
import { Status, useServer, type Ctx } from "./shared";

export function Meal({ ctx, id }: { ctx: Ctx; id: string }) {
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
          </article>
        );
      }}
    </Status>
  );
}
