import { useEffect, useState } from "react";
import { formatMoney, formatTime, newIdempotencyKey } from "./api";
import { ErrorLine, Status, useMutation, useServer, type Ctx } from "./shared";

export function OrderList({ ctx }: { ctx: Ctx }) {
  const { t, locale } = ctx;
  const [orders, reload] = useServer(ctx, () => ctx.api.GET("/api/v1/orders", { params: { query: { limit: 50 } } }), locale);
  return (
    <Status ctx={ctx} state={orders} onRetry={reload}>
      {(page) =>
        page.items.length === 0 ? (
          <p className="card muted">{t.noOrders}</p>
        ) : (
          <ul className="rows card">
            {page.items.map((o) => (
              <li key={o.id}>
                <a href={`#/orders/${o.id}`}>{o.publicNumber}</a> · {t.state[o.state]} · {formatMoney(o.total.amountMinor, locale)}
              </li>
            ))}
          </ul>
        )
      }
    </Status>
  );
}

/** Order status from the server; refreshed every 10 seconds so the cook's actions appear without a reload. */
export function OrderDetail({ ctx, id }: { ctx: Ctx; id: string }) {
  const { t, locale } = ctx;
  const [order, reload] = useServer(ctx, () => ctx.api.GET("/api/v1/orders/{orderId}", { params: { path: { orderId: id } } }), `${id}|${locale}`);
  const { run, error, busy } = useMutation(ctx, reload);
  const [requested, setRequested] = useState(false);
  useEffect(() => {
    const timer = window.setInterval(reload, 10_000);
    return () => window.clearInterval(timer);
  }, [reload]);
  return (
    <Status ctx={ctx} state={order} onRetry={reload}>
      {(o) => (
        <article className="card stack" aria-labelledby="order-number">
          <h2 id="order-number">
            {o.publicNumber} · <span aria-live="polite">{t.state[o.state]}</span>
          </h2>
          <ul className="rows">
            {o.items.map((i) => (
              <li key={i.mealId}>
                {i.quantity} × {i.title} — {formatMoney(i.lineTotal.amountMinor, locale)}
              </li>
            ))}
          </ul>
          <p>
            {t.total}: <strong>{formatMoney(o.total.amountMinor, locale)}</strong> · {formatTime(o.slot.startsAt, locale)}
          </p>
          {o.handoverCode ? (
            <p className="notice">
              {t.handoverCode}: <strong dir="ltr">{o.handoverCode}</strong>
            </p>
          ) : null}
          {o.contact ? (
            <p>
              {t.kitchenContact}: {o.contact.kitchenName} · <bdi dir="ltr">{o.contact.phone}</bdi>
              {o.contact.pickupLocation ? ` · ${t.pickupLocation}: ${o.contact.pickupLocation}` : ""}
            </p>
          ) : null}
          <ol className="rows" aria-label={t.timeline}>
            {o.events.map((e) => (
              <li key={e.sequence}>
                {formatTime(e.occurredAt, locale)} · {t.state[e.toState] ?? e.toState}
              </li>
            ))}
          </ol>
          {o.allowedActions.includes("CANCEL") ? (
            <button
              type="button"
              className="danger"
              disabled={busy}
              onClick={() =>
                void run(() =>
                  ctx.api.POST("/api/v1/orders/{orderId}/cancel", {
                    params: { path: { orderId: o.id }, header: { "Idempotency-Key": newIdempotencyKey() } },
                    body: { expectedVersion: o.version, reasonCode: "CHANGED_MIND" },
                  }),
                )
              }
            >
              {t.cancelOrder}
            </button>
          ) : null}
          {o.allowedActions.includes("REQUEST_CANCELLATION") && !requested ? (
            <button
              type="button"
              className="secondary"
              disabled={busy}
              onClick={() =>
                void run(() =>
                  ctx.api.POST("/api/v1/orders/{orderId}/cancellation-requests", {
                    params: { path: { orderId: o.id }, header: { "Idempotency-Key": newIdempotencyKey() } },
                    body: { expectedVersion: o.version, reasonCode: "CHANGED_MIND" },
                  }),
                ).then((ok) => ok && setRequested(true))
              }
            >
              {t.requestCancellation}
            </button>
          ) : null}
          {requested ? <p role="status">{t.cancellationRequested}</p> : null}
          <ErrorLine message={error} />
        </article>
      )}
    </Status>
  );
}
