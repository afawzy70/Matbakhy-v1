import { useState } from "react";
import { formatMoney, formatTime } from "./api";
import { Status, useServer, type Ctx } from "./shared";

export function Browse({ ctx }: { ctx: Ctx }) {
  const { t, locale } = ctx;
  const [zoneId, setZoneId] = useState("");
  const [q, setQ] = useState("");
  const [query, setQuery] = useState("");
  const [zones] = useServer(ctx, () => ctx.api.GET("/api/v1/zones"), locale);
  const [meals, reload] = useServer(
    ctx,
    () => ctx.api.GET("/api/v1/meals", { params: { query: { limit: 50, ...(zoneId ? { zoneId } : {}), ...(query ? { q: query } : {}) } } }),
    `${zoneId}|${query}|${locale}`,
  );
  return (
    <div className="stack">
      <form
        className="card inline-form"
        role="search"
        onSubmit={(e) => {
          e.preventDefault();
          setQuery(q.trim());
        }}
      >
        <label>
          {t.zone}{" "}
          <select value={zoneId} onChange={(e) => setZoneId(e.target.value)}>
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
        <label>
          {t.search} <input type="search" maxLength={80} value={q} onChange={(e) => setQ(e.target.value)} />
        </label>
        <button type="submit">{t.search}</button>
      </form>
      <Status ctx={ctx} state={meals} onRetry={reload}>
        {(page) =>
          page.items.length === 0 ? (
            <p className="muted">{t.noMeals}</p>
          ) : (
            <ul className="cards grid">
              {page.items.map((m) => (
                <li key={m.id}>
                  <article className="card" aria-label={m.title}>
                    {m.photo ? <img src={m.photo.url} alt={m.photo.alt} loading="lazy" /> : null}
                    <h3>
                      <a href={`#/meal/${m.id}`}>{m.title}</a>
                    </h3>
                    <p className="muted">
                      {m.kitchen.name} · {m.portionText}
                    </p>
                    <p>
                      <strong>{formatMoney(m.price.amountMinor, locale)}</strong> · {t.availability[m.availability]}
                    </p>
                    {m.nextSlots[0] ? <p className="muted">{formatTime(m.nextSlots[0].startsAt, locale)}</p> : null}
                  </article>
                </li>
              ))}
            </ul>
          )
        }
      </Status>
    </div>
  );
}
