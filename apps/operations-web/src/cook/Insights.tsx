import { useState } from "react";
import { formatMoney, type Schemas } from "../api";
import { Status, useServer, type Ctx } from "./shared";

/**
 * MB-054 (D17): the free report for the cook's own kitchen. Interest is distinct visitors per day; orders, portions and
 * money come from the server's records. Gross, refunds and the known net stay separate, and the net is never called
 * profit. There is nothing to buy or boost here.
 */
export function Insights({ ctx }: { ctx: Ctx }) {
  const { t, locale } = ctx;
  const i = t.insights;
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [query, setQuery] = useState<{ from?: string; to?: string }>({});
  const [state, reload] = useServer<Schemas["CookInsights"]>(ctx, () => ctx.api.GET("/api/v1/cook/insights", { params: { query } }),
    JSON.stringify(query));
  const money = (m: { amountMinor: number }) => formatMoney(m.amountMinor, locale);
  return (
    <div className="stack">
      <form
        className="card inline-form"
        onSubmit={(e) => {
          e.preventDefault();
          setQuery({ ...(from ? { from } : {}), ...(to ? { to } : {}) });
        }}
      >
        <label>
          {t.from} <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <label>
          {t.to} <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </label>
        <button type="submit">{t.show}</button>
      </form>
      <Status ctx={ctx} state={state} onRetry={reload}>
        {(s) => (
          <section aria-labelledby="insights-heading" className="card stack" data-testid="insights">
            <h2 id="insights-heading">
              {i.title} {s.from} – {s.to}
            </h2>
            <p className="notice">{i.note}</p>
            <h3>{i.interest}</h3>
            <dl className="summary">
              <dt>{i.mealImpressions}</dt>
              <dd>{s.activity.mealImpressions}</dd>
              <dt>{i.mealViews}</dt>
              <dd>{s.activity.mealViews}</dd>
              <dt>{i.kitchenViews}</dt>
              <dd>{s.activity.kitchenViews}</dd>
              <dt>{i.addToCart}</dt>
              <dd>{s.activity.addToCart}</dd>
            </dl>
            <h3>{i.orders}</h3>
            <dl className="summary">
              <dt>{i.quotes}</dt>
              <dd>{s.orders.quotes}</dd>
              <dt>{i.placed}</dt>
              <dd>{s.orders.placed}</dd>
              <dt>{i.completed}</dt>
              <dd>{s.orders.completed}</dd>
              <dt>{i.cancelled}</dt>
              <dd>{s.orders.cancelled}</dd>
              <dt>{i.rejected}</dt>
              <dd>{s.orders.rejected}</dd>
              <dt>{i.expired}</dt>
              <dd>{s.orders.expired}</dd>
              <dt>{i.deliveryFailed}</dt>
              <dd>{s.orders.deliveryFailed}</dd>
              <dt>{i.repeatCustomers}</dt>
              <dd>{s.orders.repeatCustomers}</dd>
            </dl>
            <h3>{i.portions}</h3>
            <dl className="summary">
              <dt>{i.portionsOffered}</dt>
              <dd>{s.capacity.portionsOffered}</dd>
              <dt>{i.portionsTaken}</dt>
              <dd>{s.capacity.portionsTaken}</dd>
            </dl>
            <h3>{i.money}</h3>
            <dl className="summary">
              <dt>{i.grossSales}</dt>
              <dd>{money(s.money.grossSales)}</dd>
              <dt>{i.refunds}</dt>
              <dd>{money(s.money.refunds)}</dd>
              <dt>{i.netKnown}</dt>
              <dd>
                <strong>{money(s.money.netKnown)}</strong>
              </dd>
            </dl>
            <p className="muted">{i.notProfit}</p>
          </section>
        )}
      </Status>
    </div>
  );
}
