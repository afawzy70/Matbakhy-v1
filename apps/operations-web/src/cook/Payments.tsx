import { useState } from "react";
import { formatMoney, type Schemas } from "../api";
import { egpToMinor, ErrorLine, Status, useMutation, useServer, type Ctx } from "./shared";

type Order = Schemas["CookOrderView"];
type Profile = Schemas["KitchenPaymentProfile"];
type Method = Schemas["PaymentRecipient"]["method"];
const METHODS: Method[] = ["INSTAPAY", "MOBILE_WALLET", "BANK_TRANSFER"];

/**
 * MB-052 (D16): the kitchen's payment terms. Customers pay a deposit directly to the kitchen's own account; the platform
 * never holds the money. A new or changed account is offered to customers only after staff approve it.
 */
export function PaymentSettings({ ctx }: { ctx: Ctx }) {
  const [state, reload] = useServer<Profile>(ctx, () => ctx.api.GET("/api/v1/cook/payment-profile"));
  return (
    <Status ctx={ctx} state={state} onRetry={reload}>
      {(p) => <PaymentForm key={p.version} ctx={ctx} profile={p} refresh={reload} />}
    </Status>
  );
}

function PaymentForm({ ctx, profile, refresh }: { ctx: Ctx; profile: Profile; refresh: () => unknown }) {
  const { t } = ctx;
  const [percent, setPercent] = useState(String(profile.depositBps / 100));
  const [cap, setCap] = useState(profile.codMaxTotalMinor === null ? "" : (profile.codMaxTotalMinor / 100).toFixed(2));
  const [method, setMethod] = useState<Method>(profile.recipient?.method ?? "INSTAPAY");
  const [identifier, setIdentifier] = useState(profile.recipient?.identifier ?? "");
  const [name, setName] = useState(profile.recipient?.name ?? "");
  const { run, error, busy } = useMutation(ctx, refresh);
  const pct = /^\d{1,3}$/.test(percent.trim()) && Number(percent) <= 100 ? Number(percent) : null;
  const capMinor = cap.trim() === "" ? null : egpToMinor(cap);
  const hasRecipient = identifier.trim() !== "" || name.trim() !== "";
  const valid = pct !== null && (cap.trim() === "" || capMinor !== null) && (pct === 0 || (identifier.trim().length >= 3 && name.trim().length >= 2));
  return (
    <form
      className="card form"
      aria-labelledby="payment-heading"
      onSubmit={(e) => {
        e.preventDefault();
        if (!valid || pct === null) {
          return;
        }
        void run(() =>
          ctx.api.PUT("/api/v1/cook/payment-profile", {
            body: {
              expectedVersion: profile.version,
              depositBps: pct * 100,
              codMaxTotalMinor: capMinor,
              recipient: hasRecipient ? { method, identifier: identifier.trim(), name: name.trim() } : null,
            },
          }),
        );
      }}
    >
      <h2 id="payment-heading">{t.pay.settingsTitle}</h2>
      <p className="notice">{t.pay.settingsNote}</p>
      <label>
        {t.pay.depositPercent} <input inputMode="numeric" value={percent} onChange={(e) => setPercent(e.target.value)} aria-invalid={pct === null} />
      </label>
      <label>
        {t.pay.codCap} <input inputMode="decimal" value={cap} onChange={(e) => setCap(e.target.value)} aria-invalid={cap.trim() !== "" && capMinor === null} />
      </label>
      <fieldset>
        <legend>{t.pay.recipient}</legend>
        <label>
          {t.pay.method}{" "}
          <select value={method} onChange={(e) => setMethod(e.target.value as Method)}>
            {METHODS.map((m) => (
              <option key={m} value={m}>
                {t.pay.methods[m]}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t.pay.identifier} <input dir="ltr" maxLength={64} value={identifier} onChange={(e) => setIdentifier(e.target.value)} />
        </label>
        <label>
          {t.pay.accountName} <input maxLength={80} value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <p className={profile.recipientStatus === "APPROVED" ? "badge" : "muted"} data-testid="recipient-status">
          {profile.recipient === null ? t.pay.noRecipient : profile.recipientStatus === "APPROVED" ? t.pay.approved : t.pay.waitingApproval}
        </p>
      </fieldset>
      <button type="submit" disabled={busy || !valid}>
        {t.save}
      </button>
      <ErrorLine message={error} />
    </form>
  );
}

/** The deposit on one order, and what the kitchen can report about it. Never a bank confirmation. */
export function OrderPayment({ ctx, order, refresh }: { ctx: Ctx; order: Order; refresh: () => unknown }) {
  const { t, locale } = ctx;
  const p = order.payment;
  const { run, error, busy } = useMutation(ctx, refresh);
  const [seen, setSeen] = useState("");
  const [reference, setReference] = useState("");
  if (!p || p.method !== "DEPOSIT") {
    return null;
  }
  const ended = ["COMPLETED", "REJECTED", "EXPIRED", "CANCELLED", "DELIVERY_FAILED"].includes(order.state);
  const canReport = !ended && ["WAITING_FOR_DEPOSIT", "CUSTOMER_TRANSFER_REPORTED", "DISPUTED"].includes(p.state);
  const canDispute = ["WAITING_FOR_DEPOSIT", "CUSTOMER_TRANSFER_REPORTED", "RECONCILE_PENDING"].includes(p.state);
  const canRefund = ["REFUND_OWED", "RECONCILE_PENDING"].includes(p.state);
  const seenMinor = egpToMinor(seen);
  const post = (path: "receipt" | "problem" | "refund", body: Record<string, unknown>) =>
    run(() =>
      ctx.api.POST(`/api/v1/cook/orders/{orderId}/payment/${path}` as "/api/v1/cook/orders/{orderId}/payment/receipt", {
        params: { path: { orderId: order.id } },
        body: { expectedVersion: p.version, ...body } as never,
      }),
    );
  return (
    <section className="payment" aria-label={t.pay.depositTitle}>
      <p>
        <strong>{t.pay.depositTitle}:</strong> {formatMoney(p.deposit.amountMinor, locale)} · {t.pay.remainder}{" "}
        {formatMoney(p.remainder.amountMinor, locale)} · <span className="badge">{t.pay.states[p.state]}</span>
      </p>
      {p.recipient ? (
        <p className="muted">
          {t.pay.methods[p.recipient.method]}: <bdi dir="ltr">{p.recipient.identifier}</bdi>
        </p>
      ) : null}
      {canReport || canDispute || canRefund ? <p className="notice">{t.pay.checkOwnAccount}</p> : null}
      <div className="actions">
        {canReport ? (
          <button type="button" disabled={busy} onClick={() => void post("receipt", { amountMinor: p.deposit.amountMinor })}>
            {t.pay.received(formatMoney(p.deposit.amountMinor, locale))}
          </button>
        ) : null}
        {canDispute ? (
          <>
            <button type="button" className="danger" disabled={busy} onClick={() => void post("problem", { kind: "NOT_RECEIVED" })}>
              {t.pay.notReceived}
            </button>
            <label>
              {t.pay.amountSeen} <input inputMode="decimal" value={seen} onChange={(e) => setSeen(e.target.value)} />
            </label>
            <button type="button" disabled={busy || seenMinor === null} onClick={() => void post("problem", { kind: "AMOUNT_MISMATCH", amountMinor: seenMinor })}>
              {t.pay.differentAmount}
            </button>
          </>
        ) : null}
        {canRefund ? (
          <>
            <label>
              {t.pay.refundReference} <input dir="ltr" maxLength={64} value={reference} onChange={(e) => setReference(e.target.value)} />
            </label>
            <button type="button" disabled={busy || !reference.trim()} onClick={() => void post("refund", { reference: reference.trim() })}>
              {t.pay.refundSent}
            </button>
          </>
        ) : null}
      </div>
      <ErrorLine message={error} />
    </section>
  );
}
