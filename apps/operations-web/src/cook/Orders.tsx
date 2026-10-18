import { useState } from "react";
import { formatMoney, formatTime, newIdempotencyKey, type Schemas } from "../api";
import { OrderPayment } from "./Payments";
import { egpToMinor, ErrorLine, minutesLeft, Status, useMutation, useServer, type Ctx } from "./shared";

type Order = Schemas["CookOrderView"];
const ACTIVE = ["PENDING_ACCEPTANCE", "ACCEPTED", "PREPARING", "READY", "OUT_FOR_DELIVERY"] as const;
const PATH: Record<string, string> = {
  ACCEPT: "accept",
  REJECT: "reject",
  PREPARE: "prepare",
  READY: "ready",
  DISPATCH: "dispatch",
  HANDOVER: "handover",
  DELIVERY_FAILURE: "delivery-failure",
  CANCEL: "cancel",
};
const REASONS: Record<string, string[]> = {
  REJECT: ["KITCHEN_BUSY", "INGREDIENT_UNAVAILABLE", "OUTSIDE_CAPACITY", "OTHER"],
  CANCEL: ["INGREDIENT_UNAVAILABLE", "KITCHEN_ISSUE", "OTHER"],
  DELIVERY_FAILURE: ["CUSTOMER_UNREACHABLE", "ADDRESS_NOT_FOUND", "REFUSED", "OTHER"],
};

export function Orders({ ctx }: { ctx: Ctx }) {
  const [filter, setFilter] = useState<"ACTIVE" | "COMPLETED">("ACTIVE");
  const load = async () => {
    const states: readonly string[] = filter === "ACTIVE" ? ACTIVE : ["COMPLETED"];
    const pages = await Promise.all(
      states.map((state) => ctx.api.GET("/api/v1/cook/orders", { params: { query: { state: state as Order["state"], limit: 50 } } })),
    );
    const failed = pages.find((p) => p.data === undefined);
    if (failed) {
      return { error: failed.error, response: failed.response };
    }
    const items = pages.flatMap((p) => p.data?.items ?? []);
    return { data: items, response: (pages[0] as (typeof pages)[number]).response };
  };
  const [state, reload] = useServer<Order[]>(ctx, load, `${filter}:${ctx.locale}`);
  const { t } = ctx;
  return (
    <div className="stack">
      <div role="group" aria-label={t.tabs.orders} className="segmented">
        {(["ACTIVE", "COMPLETED"] as const).map((f) => (
          <button key={f} type="button" aria-pressed={filter === f} onClick={() => setFilter(f)}>
            {t.filter[f]}
          </button>
        ))}
      </div>
      <Status ctx={ctx} state={state} onRetry={reload}>
        {(orders) =>
          orders.length === 0 ? (
            <p className="muted">{t.noOrders}</p>
          ) : (
            <ul className="cards">
              {orders.map((o) => (
                <li key={o.id}>
                  <OrderCard ctx={ctx} order={o} refresh={reload} />
                </li>
              ))}
            </ul>
          )
        }
      </Status>
    </div>
  );
}

function OrderCard({ ctx, order, refresh }: { ctx: Ctx; order: Order; refresh: () => unknown }) {
  const { t, locale } = ctx;
  const { run, error, busy } = useMutation(ctx, refresh);
  const [reason, setReason] = useState<Record<string, string>>({});
  const [code, setCode] = useState("");
  const [reference, setReference] = useState("");
  const left = order.state === "PENDING_ACCEPTANCE" ? minutesLeft(order.acceptBy, ctx.serverOffsetMs) : null;

  const command = (action: string) =>
    run(() =>
      ctx.api.POST(`/api/v1/cook/orders/{orderId}/${PATH[action]}` as "/api/v1/cook/orders/{orderId}/accept", {
        params: { path: { orderId: order.id }, header: { "Idempotency-Key": newIdempotencyKey() } },
        body: {
          expectedVersion: order.version,
          ...(REASONS[action] ? { reasonCode: reason[action] ?? REASONS[action][0] } : {}),
          ...(action === "HANDOVER" ? { code } : {}),
          ...(action === "DISPATCH" ? { deliveryReference: reference } : {}),
        } as never,
      }),
    );

  const address = order.contact?.deliveryAddress as Record<string, unknown> | null | undefined;
  return (
    <article className="card" aria-label={order.publicNumber}>
      <header className="card-head">
        <h3>{order.publicNumber}</h3>
        <span className="badge">{t.state[order.state]}</span>
        <span className="muted">{t.mode[order.fulfillmentMode]}</span>
      </header>
      <ul className="rows">
        {order.items.map((i) => (
          <li key={i.mealId}>
            {i.quantity} × {i.title} — {formatMoney(i.lineTotal.amountMinor, locale)}
            <br />
            <small className="muted">{i.allergenStatement}</small>
          </li>
        ))}
      </ul>
      <p>
        {t.total}: <strong>{formatMoney(order.total.amountMinor, locale)}</strong> · {formatTime(order.slot.startsAt, locale)}
      </p>
      {left !== null ? (
        <p className={left < 10 ? "deadline urgent" : "deadline"}>
          {t.acceptBy} {formatTime(order.acceptBy, locale)} ({left < 0 ? t.expired : t.minutesLeft(left)})
        </p>
      ) : null}
      {order.fulfillmentNote ? (
        <p>
          {t.note}: {order.fulfillmentNote}
        </p>
      ) : null}
      {order.contact ? (
        <p>
          {t.customer}: {order.contact.name} · <bdi dir="ltr">{order.contact.phone}</bdi>
          {address ? (
            <>
              <br />
              {t.address}:{" "}
              {["street", "building", "floor", "unit", "landmark"]
                .map((k) => address[k])
                .filter((v) => typeof v === "string" && v)
                .join("، ")}
            </>
          ) : null}
        </p>
      ) : null}
      {order.state === "COMPLETED" ? <p className="muted">{t.cash[order.cashState] ?? order.cashState}</p> : null}
      <OrderPayment ctx={ctx} order={order} refresh={refresh} />

      <div className="actions">
        {order.allowedActions.map((action) => (
          <div key={action} className="action">
            {REASONS[action] ? (
              <label>
                {t.reason}{" "}
                <select value={reason[action] ?? REASONS[action][0]} onChange={(e) => setReason({ ...reason, [action]: e.target.value })}>
                  {REASONS[action].map((r) => (
                    <option key={r} value={r}>
                      {t.reasons[r]}
                    </option>
                  ))}
                </select>
              </label>
            ) : null}
            {action === "HANDOVER" ? (
              <label>
                {t.handoverCode}{" "}
                <input inputMode="numeric" autoComplete="off" maxLength={6} value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))} />
              </label>
            ) : null}
            {action === "DISPATCH" ? (
              <label>
                {t.deliveryReference} <input maxLength={200} value={reference} onChange={(e) => setReference(e.target.value)} />
              </label>
            ) : null}
            <button
              type="button"
              className={["REJECT", "CANCEL", "DELIVERY_FAILURE"].includes(action) ? "danger" : undefined}
              disabled={busy || (action === "HANDOVER" && code.length !== 6) || (action === "DISPATCH" && !reference.trim())}
              onClick={() => void command(action)}
            >
              {t.action[action]}
            </button>
          </div>
        ))}
      </div>
      {order.state === "COMPLETED" && order.cashState === "DUE" ? <CashReport ctx={ctx} order={order} refresh={refresh} /> : null}
      <ErrorLine message={error} />
    </article>
  );
}

function CashReport({ ctx, order, refresh }: { ctx: Ctx; order: Order; refresh: () => unknown }) {
  const { t } = ctx;
  const [amount, setAmount] = useState((((order.payment?.remainder.amountMinor ?? order.total.amountMinor)) / 100).toFixed(2));
  const [reference, setReference] = useState("");
  const { run, error, busy } = useMutation(ctx, refresh);
  const minor = egpToMinor(amount);
  return (
    <form
      className="inline-form"
      onSubmit={(e) => {
        e.preventDefault();
        if (minor === null) {
          return;
        }
        void run(() =>
          ctx.api.POST("/api/v1/cook/orders/{orderId}/cash-reports", {
            params: { path: { orderId: order.id }, header: { "Idempotency-Key": newIdempotencyKey() } },
            body: {
              amountMinor: minor,
              currency: "EGP",
              collectedAt: new Date().toISOString(),
              method: "CASH",
              ...(reference.trim() ? { externalReference: reference.trim() } : {}),
              expectedVersion: order.version,
            },
          }),
        );
      }}
    >
      <h4>{t.cashReport}</h4>
      <label>
        {t.amountEgp} <input inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} aria-invalid={minor === null} />
      </label>
      <label>
        {t.reference} <input maxLength={128} value={reference} onChange={(e) => setReference(e.target.value)} />
      </label>
      <button type="submit" disabled={busy || minor === null}>
        {t.submit}
      </button>
      <ErrorLine message={error} />
    </form>
  );
}
