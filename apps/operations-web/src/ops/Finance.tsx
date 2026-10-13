import { useState } from "react";
import { formatMoney, hasCommission, newIdempotencyKey, type Schemas } from "../api";
import { ErrorLine, Status, useMutation, useServer, type Ctx } from "../cook/shared";

type Statement = Schemas["StatementPage"];
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Finance operations for one kitchen: the statement (API-086), full refunds (API-084/085) and settlements (API-087/088). */
export function Finance({ ctx }: { ctx: Ctx }) {
  const { t } = ctx;
  const [input, setInput] = useState("");
  const [kitchenId, setKitchenId] = useState<string | null>(null);
  return (
    <div className="stack">
      <form
        className="card inline-form"
        onSubmit={(e) => {
          e.preventDefault();
          if (UUID_RE.test(input.trim())) {
            setKitchenId(input.trim());
          }
        }}
      >
        <label>
          {t.ops.kitchenId} <input dir="ltr" value={input} onChange={(e) => setInput(e.target.value)} aria-invalid={input !== "" && !UUID_RE.test(input.trim())} />
        </label>
        <button type="submit">{t.show}</button>
      </form>
      {kitchenId ? <KitchenFinance key={kitchenId} ctx={ctx} kitchenId={kitchenId} /> : null}
    </div>
  );
}

function KitchenFinance({ ctx, kitchenId }: { ctx: Ctx; kitchenId: string }) {
  const { t, locale } = ctx;
  const [state, reload] = useServer<Statement>(
    ctx,
    () => ctx.api.GET("/api/v1/ops/finance/statements", { params: { query: { kitchenId, limit: 100 } } }),
    kitchenId,
  );
  return (
    <Status ctx={ctx} state={state} onRetry={reload}>
      {(s) => (
        <div className="stack">
          <section className="card" aria-labelledby="fin-summary">
            <h2 id="fin-summary">
              {t.statement} {s.from} – {s.to}
            </h2>
            <p className="notice">{hasCommission(s) ? t.statementNote : t.statementNoteNoCommission}</p>
            {hasCommission(s) ? null : <p className="notice">{t.noCommission}</p>}
            <dl className="summary">
              <dt>{t.cashVerified}</dt>
              <dd>{formatMoney(s.summary.cashVerified.amountMinor, locale)}</dd>
              {hasCommission(s) ? (
                <>
                  <dt>{t.commissionPosted}</dt>
                  <dd>{formatMoney(s.summary.commissionPosted.amountMinor, locale)}</dd>
                  <dt>{t.commissionReversed}</dt>
                  <dd>{formatMoney(s.summary.commissionReversed.amountMinor, locale)}</dd>
                  <dt>{t.commissionSettled}</dt>
                  <dd>{formatMoney(s.summary.commissionSettled.amountMinor, locale)}</dd>
                  <dt>{t.commissionOutstanding}</dt>
                  <dd>{formatMoney(s.summary.commissionOutstanding.amountMinor, locale)}</dd>
                </>
              ) : null}
            </dl>
            <ul className="rows">
              {s.items.map((l) => (
                <li key={l.orderId}>
                  {l.publicNumber} · {formatMoney(l.orderTotal.amountMinor, locale)} · {t.cash[l.cashState] ?? l.cashState}
                  {l.cashState === "VERIFIED" ? <RequestRefund ctx={ctx} orderId={l.orderId} amountMinor={l.orderTotal.amountMinor} refresh={reload} /> : null}
                </li>
              ))}
            </ul>
          </section>
          <section className="card" aria-labelledby="fin-refunds">
            <h2 id="fin-refunds">{t.ops.refunds}</h2>
            {s.refunds.length === 0 ? <p className="muted">{t.ops.noItems}</p> : null}
            <ul className="rows">
              {s.refunds.map((r) => (
                <li key={r.id}>
                  <RefundRow ctx={ctx} refund={r} refresh={reload} />
                </li>
              ))}
            </ul>
          </section>
          <Settlements ctx={ctx} statement={s} refresh={reload} />
        </div>
      )}
    </Status>
  );
}

function RequestRefund({ ctx, orderId, amountMinor, refresh }: { ctx: Ctx; orderId: string; amountMinor: number; refresh: () => void }) {
  const { t } = ctx;
  const [caseRef, setCaseRef] = useState("");
  const [reason, setReason] = useState("");
  const { run, error, busy } = useMutation(ctx, refresh);
  return (
    <form
      className="inline-form"
      aria-label={t.ops.requestRefund}
      onSubmit={(e) => {
        e.preventDefault();
        void run(() =>
          ctx.api.POST("/api/v1/ops/finance/refunds", {
            params: { header: { "Idempotency-Key": newIdempotencyKey() } },
            body: { orderId, caseId: caseRef.trim(), fullAmountMinor: amountMinor, reason: reason.trim() },
          }),
        );
      }}
    >
      <label>
        {t.ops.caseReference} <input maxLength={100} value={caseRef} onChange={(e) => setCaseRef(e.target.value)} />
      </label>
      <label>
        {t.reason} <input maxLength={1000} value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <button type="submit" className="danger" disabled={busy || caseRef.trim().length < 3 || reason.trim().length < 3}>
        {t.ops.requestRefund}
      </button>
      <ErrorLine message={error} />
    </form>
  );
}

function RefundRow({ ctx, refund, refresh }: { ctx: Ctx; refund: Statement["refunds"][number]; refresh: () => void }) {
  const { t, locale } = ctx;
  const [evidence, setEvidence] = useState("");
  const { run, error, busy } = useMutation(ctx, refresh);
  const actions: ("APPROVE" | "REJECT" | "PAYMENT_RECORDED" | "VERIFY")[] =
    refund.state === "REQUESTED" ? ["APPROVE", "REJECT"] : refund.state === "APPROVED" ? ["PAYMENT_RECORDED", "REJECT"] : refund.state === "PAID" ? ["VERIFY"] : [];
  const act = (action: (typeof actions)[number]) =>
    run(() =>
      ctx.api.POST("/api/v1/ops/finance/refunds/{refundId}/action", {
        params: { path: { refundId: refund.id }, header: { "Idempotency-Key": newIdempotencyKey() } },
        body: { action, expectedVersion: refund.version, ...(action === "PAYMENT_RECORDED" ? { evidenceRef: evidence.trim() } : {}) },
      }),
    );
  return (
    <div className="action">
      {formatMoney(refund.amount.amountMinor, locale)} · {t.ops.refundState[refund.state]}
      {refund.paymentReference ? ` · ${refund.paymentReference}` : ""}
      {refund.state === "APPROVED" ? (
        <label>
          {t.ops.evidence} <input maxLength={200} value={evidence} onChange={(e) => setEvidence(e.target.value)} />
        </label>
      ) : null}
      {actions.map((a) => (
        <button key={a} type="button" disabled={busy || (a === "PAYMENT_RECORDED" && evidence.trim().length < 3)} onClick={() => void act(a)}>
          {t.ops.refundAction[a]}
        </button>
      ))}
      <ErrorLine message={error} />
    </div>
  );
}

function Settlements({ ctx, statement, refresh }: { ctx: Ctx; statement: Statement; refresh: () => void }) {
  const { t, locale } = ctx;
  const [picked, setPicked] = useState<Record<string, boolean>>({});
  const { run, error, busy } = useMutation(ctx, refresh);
  const chosen = statement.unsettledEntries.filter((e) => picked[e.ledgerGroupId]).map((e) => e.ledgerGroupId);
  return (
    <section className="card" aria-labelledby="fin-settlements">
      <h2 id="fin-settlements">{t.ops.settlements}</h2>
      <fieldset>
        <legend>{t.ops.unsettled}</legend>
        {statement.unsettledEntries.map((e) => (
          <label key={e.ledgerGroupId} className="check">
            <input type="checkbox" checked={picked[e.ledgerGroupId] ?? false} onChange={(ev) => setPicked({ ...picked, [e.ledgerGroupId]: ev.target.checked })} />{" "}
            {e.kind} · {formatMoney(e.amount.amountMinor, locale)}
          </label>
        ))}
      </fieldset>
      <button
        type="button"
        disabled={busy || chosen.length === 0}
        onClick={() =>
          void run(() =>
            ctx.api.POST("/api/v1/ops/finance/settlements", {
              params: { header: { "Idempotency-Key": newIdempotencyKey() } },
              body: { kitchenId: statement.kitchenId, period: { from: statement.from, to: statement.to }, entryIds: chosen },
            }),
          ).then(() => setPicked({}))
        }
      >
        {t.ops.createSettlement}
      </button>
      <ErrorLine message={error} />
      <ul className="rows">
        {statement.settlements.map((b) => (
          <li key={b.id}>
            <SettlementRow ctx={ctx} batch={b} refresh={refresh} />
          </li>
        ))}
      </ul>
    </section>
  );
}

function SettlementRow({ ctx, batch, refresh }: { ctx: Ctx; batch: Statement["settlements"][number]; refresh: () => void }) {
  const { t, locale } = ctx;
  const [evidence, setEvidence] = useState("");
  const { run, error, busy } = useMutation(ctx, refresh);
  const actions: ("REVIEW" | "PAYMENT_RECORDED" | "RECONCILE" | "CANCEL")[] =
    batch.state === "DRAFT" ? ["REVIEW", "CANCEL"] : batch.state === "REVIEWED" ? ["PAYMENT_RECORDED"] : batch.state === "PAYMENT_RECORDED" ? ["RECONCILE"] : [];
  return (
    <div className="action">
      {batch.periodFrom} – {batch.periodTo} · {formatMoney(batch.total.amountMinor, locale)} · {t.ops.settlementState[batch.state]}
      {batch.paymentReference ? ` · ${batch.paymentReference}` : ""}
      {batch.state === "REVIEWED" ? (
        <label>
          {t.ops.evidence} <input maxLength={200} value={evidence} onChange={(e) => setEvidence(e.target.value)} />
        </label>
      ) : null}
      {actions.map((a) => (
        <button
          key={a}
          type="button"
          className={a === "CANCEL" ? "danger" : undefined}
          disabled={busy || (a === "PAYMENT_RECORDED" && evidence.trim().length < 3)}
          onClick={() =>
            void run(() =>
              ctx.api.POST("/api/v1/ops/finance/settlements/{batchId}/action", {
                params: { path: { batchId: batch.id }, header: { "Idempotency-Key": newIdempotencyKey() } },
                body: { action: a, expectedVersion: batch.version, ...(a === "PAYMENT_RECORDED" ? { evidenceRef: evidence.trim() } : {}) },
              }),
            )
          }
        >
          {t.ops.settlementAction[a]}
        </button>
      ))}
      <ErrorLine message={error} />
    </div>
  );
}
