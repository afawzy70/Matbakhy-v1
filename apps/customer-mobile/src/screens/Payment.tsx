import { useState } from "react";
import { View } from "react-native";
import { useApp } from "../AppContext";
import { errorCode, formatMoney, formatTime, type Schemas } from "../mobileApi";
import { Body, Button, Card, ErrorLine, Field, Notice } from "../ui";

type Terms = Schemas["QuotePaymentTerms"];
type Payment = Schemas["PaymentView"];

/**
 * MB-053 (D16): deposit terms in a quote, as on the web. The customer pays the kitchen's approved account directly; the
 * platform never receives or holds the money, and nothing here claims a bank confirmation.
 */
export function PaymentTerms({ terms, deadline }: { terms: Terms; deadline: string }) {
  const { t, locale } = useApp();
  if (terms.method === "COD" || !terms.recipient) {
    return (
      <Body locale={locale} muted>
        {t.cashOnDelivery}
      </Body>
    );
  }
  return (
    <View testID="deposit-terms" style={{ gap: 6 }}>
      <Body locale={locale} strong>
        {t.pay.depositNow(formatMoney(terms.deposit.amountMinor, locale))}
      </Body>
      <Body locale={locale}>{t.pay.restOnReceipt(formatMoney(terms.remainder.amountMinor, locale))}</Body>
      <Body locale={locale}>
        {t.pay.methods[terms.recipient.method] ?? terms.recipient.method}: {"⁦"}
        {terms.recipient.identifier}
        {"⁩"} · {terms.recipient.name}
      </Body>
      <Body locale={locale}>{t.pay.sendBefore(formatTime(deadline, locale))}</Body>
      <Body locale={locale} muted>
        {t.pay.directNote}
      </Body>
    </View>
  );
}

/** The order's payment state, honestly worded, and the customer's one action: saying the deposit was sent. */
export function PaymentStatus({ orderId, payment, onChanged }: { orderId: string; payment: Payment | null | undefined; onChanged: () => void }) {
  const { api, t, locale } = useApp();
  const [reference, setReference] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  if (!payment || payment.method !== "DEPOSIT") {
    return null;
  }
  async function report() {
    setBusy(true);
    setError(null);
    try {
      const r = await api.POST("/api/v1/orders/{orderId}/payment/transfer-report", {
        params: { path: { orderId } },
        body: reference.trim() ? { reference: reference.trim() } : {},
      });
      setError(r.data ? null : errorCode(r.error));
    } catch {
      setError("NETWORK");
    } finally {
      setBusy(false);
      onChanged();
    }
  }
  return (
    <Card testID="payment-status">
      <Body locale={locale} strong>
        {t.pay.title}: {t.pay.states[payment.state] ?? payment.state}
      </Body>
      <Body locale={locale}>
        {t.pay.depositNow(formatMoney(payment.deposit.amountMinor, locale))} · {t.pay.restOnReceipt(formatMoney(payment.remainder.amountMinor, locale))}
      </Body>
      {payment.recipient ? (
        <Body locale={locale}>
          {t.pay.methods[payment.recipient.method] ?? payment.recipient.method}: {"⁦"}
          {payment.recipient.identifier}
          {"⁩"} · {payment.recipient.name}
        </Body>
      ) : null}
      {payment.state === "WAITING_FOR_DEPOSIT" ? (
        <>
          {payment.depositExpiresAt ? <Body locale={locale}>{t.pay.sendBefore(formatTime(payment.depositExpiresAt, locale))}</Body> : null}
          <Field testID="transfer-reference" label={t.pay.reference} value={reference} onChangeText={setReference} ltr />
          <Button testID="report-transfer" label={t.pay.iSent} disabled={busy} onPress={() => void report()} />
        </>
      ) : null}
      {t.pay.explain[payment.state] ? <Notice locale={locale}>{t.pay.explain[payment.state]}</Notice> : null}
      <ErrorLine t={t} code={error} />
    </Card>
  );
}
