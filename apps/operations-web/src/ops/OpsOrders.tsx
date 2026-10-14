import { useState } from "react";
import { formatMoney, formatTime, newIdempotencyKey, type Schemas } from "../api";
import { ErrorLine, Status, useMutation, useServer, type Ctx } from "../cook/shared";
import { StepUp } from "./Reviews";

type Flag = Schemas["OrderExceptionFlag"];
const FLAGS: Flag[] = ["OVERDUE_ACCEPTANCE", "DELIVERY_FAILED", "CASH_DISPUTED", "OPEN_CASE"];

export function OpsOrders({ ctx }: { ctx: Ctx }) {
  const { t, locale } = ctx;
  const [flag, setFlag] = useState<Flag | "ALL">("ALL");
  const [selected, setSelected] = useState<string | null>(null);
  const [list, reload] = useServer<Schemas["OpsOrderPage"]>(
    ctx,
    () => ctx.api.GET("/api/v1/ops/orders", { params: { query: { limit: 50, ...(flag === "ALL" ? {} : { exception: flag }) } } }),
    flag,
  );
  return (
    <div className="stack">
      <div role="group" aria-label={t.ops.orders} className="segmented">
        {(["ALL", ...FLAGS] as const).map((f) => (
          <button key={f} type="button" aria-pressed={flag === f} onClick={() => setFlag(f)}>
            {f === "ALL" ? t.ops.filterAll : t.ops.exception[f]}
          </button>
        ))}
      </div>
      <Status ctx={ctx} state={list} onRetry={reload}>
        {(page) =>
          page.items.length === 0 ? (
            <p className="muted">{t.ops.noItems}</p>
          ) : (
            <ul className="rows">
              {page.items.map((o) => (
                <li key={o.id}>
                  <button type="button" className="link" onClick={() => setSelected(o.id)}>
                    {o.publicNumber}
                  </button>{" "}
                  · {t.state[o.state]} · {t.mode[o.fulfillmentMode]} · {formatMoney(o.total.amountMinor, locale)} · {formatTime(o.createdAt, locale)}
                  {o.exceptions.map((x) => (
                    <span key={x} className="badge badge-suspended">
                      {" "}
                      {t.ops.exception[x]}
                    </span>
                  ))}
                </li>
              ))}
            </ul>
          )
        }
      </Status>
      {selected ? <OrderDetail key={selected} ctx={ctx} id={selected} onChanged={reload} /> : null}
    </div>
  );
}

function OrderDetail({ ctx, id, onChanged }: { ctx: Ctx; id: string; onChanged: () => void }) {
  const { t, locale } = ctx;
  const [view, reload] = useServer<Schemas["OpsOrderView"]>(ctx, () => ctx.api.GET("/api/v1/ops/orders/{orderId}", { params: { path: { orderId: id } } }), id);
  const refresh = () => {
    reload();
    onChanged();
  };
  return (
    <Status ctx={ctx} state={view} onRetry={reload}>
      {(v) => (
        <section className="card" aria-labelledby="ops-order-heading">
          <h2 id="ops-order-heading">
            {v.order.publicNumber} · {t.state[v.order.state]}
          </h2>
          <p>
            {t.total}: {formatMoney(v.order.total.amountMinor, locale)} · {t.cash[v.order.cashState] ?? v.order.cashState}
          </p>
          {v.exceptions.length > 0 ? <p className="notice">{v.exceptions.map((x) => t.ops.exception[x]).join(" · ")}</p> : null}
          {v.customerContact ? (
            <p>
              {t.customer}: {v.customerContact.name} · <bdi dir="ltr">{v.customerContact.phone}</bdi>
            </p>
          ) : (
            <p className="muted">{t.ops.noContact}</p>
          )}
          <ol className="rows" aria-label="timeline">
            {v.order.events.map((e) => (
              <li key={e.sequence}>
                {formatTime(e.occurredAt, locale)} · {e.fromState ? `${t.state[e.fromState] ?? e.fromState} → ` : ""}
                {t.state[e.toState] ?? e.toState}
                {e.reasonCode ? ` (${e.reasonCode})` : ""}
              </li>
            ))}
          </ol>
          {["PREPARING", "READY"].includes(v.order.state) ? <ExceptionCancel ctx={ctx} view={v} refresh={refresh} /> : null}
          {["READY", "OUT_FOR_DELIVERY"].includes(v.order.state) ? <HandoverOverride ctx={ctx} view={v} refresh={refresh} /> : null}
        </section>
      )}
    </Status>
  );
}

function ExceptionCancel({ ctx, view, refresh }: { ctx: Ctx; view: Schemas["OpsOrderView"]; refresh: () => void }) {
  const { t } = ctx;
  const [reasonCode, setReasonCode] = useState<"SAFETY" | "OPERATIONAL" | "OTHER">("OPERATIONAL");
  const [caseRef, setCaseRef] = useState("");
  const [note, setNote] = useState("");
  const { run, error, code, busy } = useMutation(ctx, refresh);
  return (
    <form
      className="form card"
      aria-label={t.ops.exceptionCancel}
      onSubmit={(e) => {
        e.preventDefault();
        void run(() =>
          ctx.api.POST("/api/v1/ops/orders/{orderId}/exception-cancel", {
            params: { path: { orderId: view.order.id }, header: { "Idempotency-Key": newIdempotencyKey() } },
            body: { expectedVersion: view.order.version, reasonCode, caseId: caseRef.trim(), ...(note.trim() ? { note: note.trim() } : {}) },
          }),
        );
      }}
    >
      <h3>{t.ops.exceptionCancel}</h3>
      <label>
        {t.reason}{" "}
        <select value={reasonCode} onChange={(e) => setReasonCode(e.target.value as "SAFETY" | "OPERATIONAL" | "OTHER")}>
          <option value="OPERATIONAL">OPERATIONAL</option>
          <option value="SAFETY">SAFETY</option>
          <option value="OTHER">OTHER</option>
        </select>
      </label>
      <label>
        {t.ops.caseReference} <input required maxLength={100} value={caseRef} onChange={(e) => setCaseRef(e.target.value)} />
      </label>
      <label>
        {t.ops.note} <input maxLength={1000} value={note} onChange={(e) => setNote(e.target.value)} />
      </label>
      <button type="submit" className="danger" disabled={busy || !caseRef.trim()}>
        {t.ops.exceptionCancel}
      </button>
      <StepUp ctx={ctx} code={code} />
      <ErrorLine message={code === "STEP_UP_REQUIRED" ? null : error} />
    </form>
  );
}

function HandoverOverride({ ctx, view, refresh }: { ctx: Ctx; view: Schemas["OpsOrderView"]; refresh: () => void }) {
  const { t } = ctx;
  const [reason, setReason] = useState("");
  const [evidence, setEvidence] = useState("");
  const { run, error, code, busy } = useMutation(ctx, refresh);
  return (
    <form
      className="form card"
      aria-label={t.ops.handoverOverride}
      onSubmit={(e) => {
        e.preventDefault();
        void run(() =>
          ctx.api.POST("/api/v1/ops/orders/{orderId}/handover-override", {
            params: { path: { orderId: view.order.id }, header: { "Idempotency-Key": newIdempotencyKey() } },
            body: { expectedVersion: view.order.version, reason: reason.trim(), evidenceRef: evidence.trim() },
          }),
        );
      }}
    >
      <h3>{t.ops.handoverOverride}</h3>
      <label>
        {t.reason} <input minLength={10} maxLength={1000} value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <label>
        {t.ops.evidenceRef} <input maxLength={200} value={evidence} onChange={(e) => setEvidence(e.target.value)} />
      </label>
      <button type="submit" className="danger" disabled={busy || reason.trim().length < 10 || !evidence.trim()}>
        {t.ops.handoverOverride}
      </button>
      <StepUp ctx={ctx} code={code} />
      <ErrorLine message={code === "STEP_UP_REQUIRED" ? null : error} />
    </form>
  );
}
