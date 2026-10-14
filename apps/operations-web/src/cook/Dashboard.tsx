import { formatMoney, formatTime, type Schemas } from "../api";
import { minutesLeft, Status, useServer, type Ctx } from "./shared";

export function Dashboard({ ctx, onOpenOrders }: { ctx: Ctx; onOpenOrders: () => void }) {
  const [state, reload] = useServer<Schemas["CookDashboard"]>(ctx, () => ctx.api.GET("/api/v1/cook/dashboard"), ctx.locale);
  const { t, locale } = ctx;
  return (
    <Status ctx={ctx} state={state} onRetry={reload}>
      {(d) => {
        const offset = ctx.serverOffsetMs;
        const k = d.kitchen;
        return (
          <div className="stack">
            <section aria-labelledby="kitchen-heading" className="card">
              <h2 id="kitchen-heading">{k.displayName}</h2>
              <p>
                <span className={`badge badge-${k.operatingState.toLowerCase()}`}>{t.kitchenState[k.operatingState]}</span>
              </p>
              {k.pausedBy === "STAFF" ? <p className="muted">{t.pausedByStaff}</p> : null}
            </section>
            <section aria-labelledby="counts-heading" className="card">
              <h2 id="counts-heading">{t.counts}</h2>
              <ul className="counts">
                {Object.entries(d.activeCounts).map(([s, n]) => (
                  <li key={s}>
                    <span className="count">{n}</span> {t.state[s]}
                  </li>
                ))}
              </ul>
            </section>
            <section aria-labelledby="pending-heading" className="card">
              <h2 id="pending-heading">{t.pending}</h2>
              {d.pendingAcceptance.length === 0 ? (
                <p className="muted">{t.noOrders}</p>
              ) : (
                <ul className="rows">
                  {d.pendingAcceptance.map((o) => {
                    const left = minutesLeft(o.acceptBy, offset);
                    return (
                      <li key={o.orderId}>
                        <button type="button" className="link" onClick={onOpenOrders}>
                          {o.publicNumber}
                        </button>{" "}
                        · {t.mode[o.fulfillmentMode]} · {formatMoney(o.total.amountMinor, locale)} ·{" "}
                        <span className={left < 10 ? "deadline urgent" : "deadline"}>
                          {t.acceptBy} {formatTime(o.acceptBy, locale)} ({left < 0 ? t.expired : t.minutesLeft(left)})
                        </span>
                      </li>
                    );
                  })}
                </ul>
              )}
            </section>
            <section aria-labelledby="slots-heading" className="card">
              <h2 id="slots-heading">{t.upcomingSlots}</h2>
              <ul className="rows">
                {d.upcomingSlots.map((s) => (
                  <li key={s.slotId}>
                    {formatTime(s.startsAt, locale)} · {t.slotState[s.state]} ·{" "}
                    {t.portions(s.reserved + s.allocated + s.consumed, s.maxPortions)}
                  </li>
                ))}
              </ul>
            </section>
          </div>
        );
      }}
    </Status>
  );
}
