import { useState } from "react";
import { formatMoney, formatTime, type Schemas } from "../api";
import { Status, useServer, type Ctx } from "./shared";

export function Statement({ ctx }: { ctx: Ctx }) {
  const { t, locale } = ctx;
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [query, setQuery] = useState<{ from?: string; to?: string }>({});
  const [state, reload] = useServer<Schemas["StatementPage"]>(
    ctx,
    () => ctx.api.GET("/api/v1/cook/statements", { params: { query: { ...query, limit: 100 } } }),
    JSON.stringify(query),
  );
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
          <section aria-labelledby="statement-heading" className="card">
            <h2 id="statement-heading">
              {t.statement} {s.from} – {s.to}
            </h2>
            <p className="notice">{t.statementNote}</p>
            <dl className="summary">
              <dt>{t.completedOrders}</dt>
              <dd>{s.summary.completedOrders}</dd>
              <dt>{t.ordersTotal}</dt>
              <dd>{formatMoney(s.summary.ordersTotal.amountMinor, locale)}</dd>
              <dt>{t.cashOutstanding}</dt>
              <dd>{formatMoney(s.summary.cashOutstanding.amountMinor, locale)}</dd>
              <dt>{t.cashReported}</dt>
              <dd>{formatMoney(s.summary.cashReported.amountMinor, locale)}</dd>
              <dt>{t.cashVerified}</dt>
              <dd>{formatMoney(s.summary.cashVerified.amountMinor, locale)}</dd>
              <dt>{t.commissionPosted}</dt>
              <dd>{formatMoney(s.summary.commissionPosted.amountMinor, locale)}</dd>
              <dt>{t.commissionPending}</dt>
              <dd>{s.summary.commissionPending}</dd>
            </dl>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th scope="col">#</th>
                    <th scope="col">{t.date}</th>
                    <th scope="col">{t.total}</th>
                    <th scope="col">{t.cashReport}</th>
                    <th scope="col">{t.commission}</th>
                  </tr>
                </thead>
                <tbody>
                  {s.items.map((l) => (
                    <tr key={l.orderId}>
                      <td>{l.publicNumber}</td>
                      <td>{formatTime(l.completedAt, locale)}</td>
                      <td>{formatMoney(l.orderTotal.amountMinor, locale)}</td>
                      <td>{t.cash[l.cashState] ?? l.cashState}</td>
                      <td>{l.commission ? formatMoney(l.commission.amountMinor, locale) : t.commissionPending}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}
      </Status>
    </div>
  );
}
