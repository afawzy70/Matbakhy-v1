import { useState } from "react";
import { formatMoney, formatTime, type Schemas } from "../api";
import { ErrorLine, Status, useMutation, useServer, type Ctx, type Loaded } from "../cook/shared";

type QueueState = "DISPUTED" | "RECONCILE_PENDING" | "REFUND_OWED";
type Item = Schemas["PaymentQueue"]["items"][number];
type Pending = Schemas["PendingRecipients"]["items"][number];

/**
 * MB-052 (D16), finance staff: approve kitchens' payment accounts and decide on ended orders whose deposit is open.
 * There is no action that marks money as received: only the kitchen can report that.
 */
export function PaymentsOps({ ctx }: { ctx: Ctx }) {
  const { t } = ctx;
  const [recipients, reload] = useServer<Schemas["PendingRecipients"]>(ctx, () => ctx.api.GET("/api/v1/ops/payment-recipients"));
  // Both panels need finance.verify; finance staff with read access only see nothing here rather than two error cards.
  if (recipients.status === "error" && recipients.code === "FORBIDDEN") {
    return null;
  }
  return (
    <div className="stack">
      <section className="card" aria-labelledby="recipients-heading">
        <h2 id="recipients-heading">{t.pay.pendingRecipients}</h2>
        <Recipients ctx={ctx} state={recipients} reload={reload} />
      </section>
      <section className="card" aria-labelledby="payments-heading">
        <h2 id="payments-heading">{t.pay.queueTitle}</h2>
        <p className="notice">{t.pay.queueNote}</p>
        <PaymentQueue ctx={ctx} />
      </section>
    </div>
  );
}

function Recipients({ ctx, state, reload }: { ctx: Ctx; state: Loaded<Schemas["PendingRecipients"]>; reload: () => void }) {
  const { t } = ctx;
  return (
    <Status ctx={ctx} state={state} onRetry={reload}>
      {(r) =>
        r.items.length === 0 ? (
          <p className="muted">{t.pay.noPending}</p>
        ) : (
          <ul className="rows">
            {r.items.map((p) => (
              <li key={p.kitchenId}>
                <Approve ctx={ctx} item={p} refresh={reload} />
              </li>
            ))}
          </ul>
        )
      }
    </Status>
  );
}

function Approve({ ctx, item, refresh }: { ctx: Ctx; item: Pending; refresh: () => unknown }) {
  const { t } = ctx;
  const [reason, setReason] = useState("");
  const { run, error, busy } = useMutation(ctx, refresh);
  return (
    <form
      className="inline-form"
      onSubmit={(e) => {
        e.preventDefault();
        void run(() =>
          ctx.api.POST("/api/v1/ops/kitchens/{kitchenId}/payment-recipient/approval", {
            params: { path: { kitchenId: item.kitchenId } },
            body: { expectedVersion: item.version, reason: reason.trim() },
          }),
        );
      }}
    >
      <p>
        <strong>{item.kitchenName}</strong> · {t.pay.methods[item.recipient.method]}: <bdi dir="ltr">{item.recipient.identifier}</bdi> ·{" "}
        {item.recipient.name}
      </p>
      <label>
        {t.reason} <input minLength={10} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <button type="submit" disabled={busy || reason.trim().length < 10}>
        {t.pay.approve}
      </button>
      <ErrorLine message={error} />
    </form>
  );
}

function PaymentQueue({ ctx }: { ctx: Ctx }) {
  const { t } = ctx;
  const [filter, setFilter] = useState<QueueState>("DISPUTED");
  const [state, reload] = useServer<Schemas["PaymentQueue"]>(
    ctx,
    () => ctx.api.GET("/api/v1/ops/payments", { params: { query: { state: filter } } }),
    filter,
  );
  return (
    <>
      <div role="group" aria-label={t.pay.queueTitle} className="segmented">
        {(["DISPUTED", "RECONCILE_PENDING", "REFUND_OWED"] as const).map((f) => (
          <button key={f} type="button" aria-pressed={filter === f} onClick={() => setFilter(f)}>
            {t.pay.states[f]}
          </button>
        ))}
      </div>
      <Status ctx={ctx} state={state} onRetry={reload}>
        {(q) =>
          q.items.length === 0 ? (
            <p className="muted">{t.pay.queueEmpty}</p>
          ) : (
            <ul className="rows">
              {q.items.map((i) => (
                <li key={i.orderId}>
                  <Decide ctx={ctx} item={i} refresh={reload} />
                </li>
              ))}
            </ul>
          )
        }
      </Status>
    </>
  );
}

function Decide({ ctx, item, refresh }: { ctx: Ctx; item: Item; refresh: () => unknown }) {
  const { t, locale } = ctx;
  const [outcome, setOutcome] = useState<"REFUND_OWED" | "CLOSED_NO_FUNDS">("REFUND_OWED");
  const [reason, setReason] = useState("");
  const { run, error, busy } = useMutation(ctx, refresh);
  const ended = ["COMPLETED", "REJECTED", "EXPIRED", "CANCELLED", "DELIVERY_FAILED"].includes(item.orderState);
  return (
    <div>
      <p>
        <strong>{item.publicNumber}</strong> · {t.state[item.orderState]} · {t.pay.depositTitle} {formatMoney(item.deposit.amountMinor, locale)} ·{" "}
        {item.lastReason ? (t.pay.reasons[item.lastReason] ?? item.lastReason) : ""} · {formatTime(item.updatedAt, locale)}
      </p>
      {ended && item.state !== "REFUND_OWED" ? (
        <form
          className="inline-form"
          onSubmit={(e) => {
            e.preventDefault();
            void run(() =>
              ctx.api.POST("/api/v1/ops/orders/{orderId}/payment/decision", {
                params: { path: { orderId: item.orderId } },
                body: { expectedVersion: item.version, outcome, reason: reason.trim() },
              }),
            );
          }}
        >
          <label>
            {t.pay.outcome}{" "}
            <select value={outcome} onChange={(e) => setOutcome(e.target.value as typeof outcome)}>
              <option value="REFUND_OWED">{t.pay.states.REFUND_OWED}</option>
              <option value="CLOSED_NO_FUNDS">{t.pay.states.CLOSED_NO_FUNDS}</option>
            </select>
          </label>
          <label>
            {t.reason} <input minLength={10} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
          </label>
          <button type="submit" disabled={busy || reason.trim().length < 10}>
            {t.pay.decide}
          </button>
          <ErrorLine message={error} />
        </form>
      ) : (
        <p className="muted">{item.state === "REFUND_OWED" ? t.pay.waitingRefund : t.pay.activeOrder}</p>
      )}
    </div>
  );
}
