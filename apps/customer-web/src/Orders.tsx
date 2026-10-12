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
          <ul className="order-list transaction">
            {page.items.map((o) => (
              <li key={o.id} className="card row between">
                <a href={`#/orders/${o.id}`}>{o.publicNumber}</a>
                <span className="pill state-pill">{t.state[o.state]}</span>
                <span className="price">
                  <bdi>{formatMoney(o.total.amountMinor, locale)}</bdi>
                </span>
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
        <article className="card stack transaction" aria-labelledby="order-number">
          <h2 id="order-number">
            {o.publicNumber} · <span aria-live="polite">{t.state[o.state]}</span>
          </h2>
          <ul className="line-items">
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
            <p className="notice handover">
              {t.handoverCode}: <strong dir="ltr">{o.handoverCode}</strong>
            </p>
          ) : null}
          {o.contact ? (
            <p>
              {t.kitchenContact}: {o.contact.kitchenName} · <bdi dir="ltr">{o.contact.phone}</bdi>
              {o.contact.pickupLocation ? ` · ${t.pickupLocation}: ${o.contact.pickupLocation}` : ""}
            </p>
          ) : null}
          <ol className="timeline" aria-label={t.timeline}>
            {o.events.map((e) => (
              <li key={e.sequence}>
                {formatTime(e.occurredAt, locale)} · {t.state[e.toState] ?? e.toState}
              </li>
            ))}
          </ol>
          {o.supportCaseIds.length > 0 ? (
            <nav aria-label={t.supportCases}>
              <ul className="rows">
                {o.supportCaseIds.map((caseId) => (
                  <li key={caseId}>
                    <a href={`#/support/${caseId}`}>{t.supportCases}</a>
                  </li>
                ))}
              </ul>
            </nav>
          ) : null}
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
          {o.state === "COMPLETED" ? <ReviewForm ctx={ctx} orderId={o.id} /> : null}
          {o.items[0] ? <a href={`#/meal/${o.items[0].mealId}`}>{t.orderAgain}</a> : null}
        </article>
      )}
    </Status>
  );
}

/** One review per completed own order; the server enforces ownership, state and uniqueness (409 when already reviewed). */
function ReviewForm({ ctx, orderId }: { ctx: Ctx; orderId: string }) {
  const { t } = ctx;
  const [rating, setRating] = useState(5);
  const [text, setText] = useState("");
  const [done, setDone] = useState(false);
  const { run, error, code, busy } = useMutation(ctx, () => undefined);
  if (done || code === "STATE_CONFLICT") {
    return <p role="status">{t.reviewed}</p>;
  }
  return (
    <section className="form" aria-labelledby="review-heading">
      <h3 id="review-heading">{t.rateOrder}</h3>
      <label>
        {t.rating}{" "}
        <select value={rating} onChange={(e) => setRating(Number(e.target.value))}>
          {[5, 4, 3, 2, 1].map((n) => (
            <option key={n} value={n}>
              {n}
            </option>
          ))}
        </select>
      </label>
      <label>
        {t.comment} <textarea maxLength={1000} value={text} onChange={(e) => setText(e.target.value)} />
      </label>
      <button
        type="button"
        disabled={busy}
        onClick={() =>
          void run(() =>
            ctx.api.POST("/api/v1/orders/{orderId}/reviews", { params: { path: { orderId } }, body: text.trim() ? { rating, text: text.trim() } : { rating } }),
          ).then((ok) => ok && setDone(true))
        }
      >
        {t.submitReview}
      </button>
      <ErrorLine message={error} />
    </section>
  );
}
