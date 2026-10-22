import { useState } from "react";
import { formatMoney, formatTime, type Schemas } from "./api";
import { ErrorLine, useMutation, type Ctx } from "./shared";

type Terms = Schemas["QuotePaymentTerms"];
type Payment = Schemas["PaymentView"];

/**
 * MB-053 (D16): deposit terms in a quote. The customer pays the kitchen's approved account directly; the platform never
 * receives or holds the money, and nothing here claims a bank confirmation.
 */
export function PaymentTerms({ ctx, terms, deadline }: { ctx: Ctx; terms: Terms; deadline: string }) {
  const { t, locale } = ctx;
  if (terms.method === "COD" || !terms.recipient) {
    return <p className="muted">{t.cashOnDelivery}</p>;
  }
  return (
    <div className="notice" data-testid="deposit-terms">
      <p>
        <strong>{t.pay.depositNow(formatMoney(terms.deposit.amountMinor, locale))}</strong> · {t.pay.restOnReceipt(formatMoney(terms.remainder.amountMinor, locale))}
      </p>
      <p>
        {t.pay.methods[terms.recipient.method]}: <bdi dir="ltr">{terms.recipient.identifier}</bdi> · {terms.recipient.name}
      </p>
      <p>{t.pay.sendBefore(formatTime(deadline, locale))}</p>
      <p className="muted">{t.pay.directNote}</p>
    </div>
  );
}

/** The order's payment state, honestly worded, and the one action the customer has: saying the deposit was sent. */
export function PaymentStatus({ ctx, orderId, payment, refresh }: { ctx: Ctx; orderId: string; payment: Payment | null | undefined; refresh: () => unknown }) {
  const { t, locale } = ctx;
  const [reference, setReference] = useState("");
  const { run, error, busy } = useMutation(ctx, refresh);
  if (!payment || payment.method !== "DEPOSIT") {
    return null;
  }
  return (
    <section className="notice" aria-label={t.pay.title} data-testid="payment-status">
      <p>
        <strong>{t.pay.title}:</strong> <span aria-live="polite">{t.pay.states[payment.state] ?? payment.state}</span>
      </p>
      <p>
        {t.pay.depositNow(formatMoney(payment.deposit.amountMinor, locale))} · {t.pay.restOnReceipt(formatMoney(payment.remainder.amountMinor, locale))}
      </p>
      {payment.recipient ? (
        <p>
          {t.pay.methods[payment.recipient.method]}: <bdi dir="ltr">{payment.recipient.identifier}</bdi> · {payment.recipient.name}
        </p>
      ) : null}
      {payment.state === "WAITING_FOR_DEPOSIT" ? (
        <form
          className="inline-form"
          onSubmit={(e) => {
            e.preventDefault();
            void run(() =>
              ctx.api.POST("/api/v1/orders/{orderId}/payment/transfer-report", {
                params: { path: { orderId } },
                body: reference.trim() ? { reference: reference.trim() } : {},
              }),
            );
          }}
        >
          {payment.depositExpiresAt ? <p>{t.pay.sendBefore(formatTime(payment.depositExpiresAt, locale))}</p> : null}
          <label>
            {t.pay.reference} <input dir="ltr" maxLength={64} value={reference} onChange={(e) => setReference(e.target.value)} />
          </label>
          <button type="submit" disabled={busy}>
            {t.pay.iSent}
          </button>
        </form>
      ) : null}
      <p className="muted">{t.pay.explain[payment.state] ?? ""}</p>
      <ErrorLine message={error} />
    </section>
  );
}
