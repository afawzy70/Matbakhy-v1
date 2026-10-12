import { useFocusEffect } from "@react-navigation/native";
import { useCallback, useEffect, useState } from "react";
import { Pressable, View } from "react-native";
import { useApp } from "../AppContext";
import { errorCode, formatMoney, formatTime, newKey } from "../mobileApi";
import { Body, Button, Card, Choice, ErrorLine, Field, Notice, Screen, StatusView, Title } from "../ui";
import { useServer } from "../useServer";
import type { RootProps, TabProps } from "./navigation";

/** The customer's own orders, from the server; states and totals are the server's (same as on the web). */
export function OrdersScreen({ navigation }: TabProps<"Orders">) {
  const { api, t, locale, auth, startSignIn } = useApp();
  const signedIn = auth.kind === "signedIn";
  const [orders, reload] = useServer(() => api.GET("/api/v1/orders", { params: { query: { limit: 50 } } }), `${signedIn}|${locale}`);
  useFocusEffect(useCallback(() => reload(), [reload]));
  if (!signedIn) {
    return (
      <Screen locale={locale}>
        <Body locale={locale}>{t.signInRequired}</Body>
        <Button label={t.signIn} onPress={() => void startSignIn()} />
      </Screen>
    );
  }
  return (
    <Screen locale={locale} testID="orders">
      <StatusView t={t} locale={locale} state={orders} onRetry={reload}>
        {(page) =>
          page.items.length === 0 ? (
            <Body locale={locale} muted>
              {t.noOrders}
            </Body>
          ) : (
            <View style={{ gap: 12 }}>
              {page.items.map((o) => (
                <Pressable
                  key={o.id}
                  testID={`order-${o.publicNumber}`}
                  accessibilityRole="button"
                  accessibilityLabel={`${o.publicNumber}, ${t.state[o.state] ?? o.state}, ${formatMoney(o.total.amountMinor, locale)}`}
                  onPress={() => navigation.navigate("Order", { orderId: o.id })}
                >
                  <Card>
                    <Body locale={locale} strong>
                      {o.publicNumber}
                    </Body>
                    <Body locale={locale}>{t.state[o.state] ?? o.state}</Body>
                    <Body locale={locale}>{formatMoney(o.total.amountMinor, locale)}</Body>
                  </Card>
                </Pressable>
              ))}
            </View>
          )
        }
      </StatusView>
    </Screen>
  );
}

/** Order status from the server, refreshed every 10 seconds; actions are only those the server allows. */
export function OrderScreen({ route, navigation }: RootProps<"Order">) {
  const { api, t, locale } = useApp();
  const { orderId } = route.params;
  const [order, reload] = useServer(() => api.GET("/api/v1/orders/{orderId}", { params: { path: { orderId } } }), `${orderId}|${locale}`);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [requested, setRequested] = useState(false);
  useEffect(() => {
    const timer = setInterval(reload, 10_000);
    return () => clearInterval(timer);
  }, [reload]);

  async function command(fn: () => Promise<{ data?: unknown; error?: unknown }>, after?: () => void) {
    setBusy(true);
    setError(null);
    try {
      const r = await fn();
      if (r.data !== undefined) {
        after?.();
      } else {
        setError(errorCode(r.error));
      }
    } catch {
      setError("NETWORK");
    } finally {
      setBusy(false);
      reload();
    }
  }

  return (
    <Screen locale={locale} testID="order">
      <StatusView t={t} locale={locale} state={order} onRetry={reload}>
        {(o) => (
          <View style={{ gap: 12 }}>
            <Title locale={locale}>{o.publicNumber}</Title>
            <Body locale={locale} strong>
              {t.state[o.state] ?? o.state}
            </Body>
            <Card testID="order-items">
              {o.items.map((i) => (
                <Body key={i.mealId} locale={locale}>
                  {i.quantity} × {i.title} — {formatMoney(i.lineTotal.amountMinor, locale)}
                </Body>
              ))}
              <Body locale={locale}>
                {t.subtotal}: {formatMoney(o.subtotal.amountMinor, locale)}
              </Body>
              <Body locale={locale}>
                {t.deliveryFee}: {formatMoney(o.deliveryFee.amountMinor, locale)}
              </Body>
              <Body locale={locale}>
                {t.tax}: {formatMoney(o.tax.amountMinor, locale)}
              </Body>
              <Body locale={locale} strong>
                {t.total}: {formatMoney(o.total.amountMinor, locale)}
              </Body>
              <Body locale={locale} muted>
                {formatTime(o.slot.startsAt, locale)} · {t.mode[o.fulfillmentMode] ?? o.fulfillmentMode}
              </Body>
            </Card>
            {o.handoverCode ? (
              <Notice locale={locale} testID="handover-code">
                {t.handoverCode}: {o.handoverCode}
              </Notice>
            ) : null}
            {o.contact ? (
              <Body locale={locale}>
                {t.kitchenContact}: {o.contact.kitchenName} · {o.contact.phone}
              </Body>
            ) : null}
            <Card>
              <Body locale={locale} strong>
                {t.timeline}
              </Body>
              {o.events.map((e) => (
                <Body key={e.sequence} locale={locale} muted>
                  {formatTime(e.occurredAt, locale)} · {t.state[e.toState] ?? e.toState}
                </Body>
              ))}
            </Card>
            {o.allowedActions.includes("CANCEL") ? (
              <Button
                label={t.cancelOrder}
                variant="danger"
                disabled={busy}
                onPress={() =>
                  void command(() =>
                    api.POST("/api/v1/orders/{orderId}/cancel", {
                      params: { path: { orderId: o.id }, header: { "Idempotency-Key": newKey("app") } },
                      body: { expectedVersion: o.version, reasonCode: "CHANGED_MIND" },
                    }),
                  )
                }
              />
            ) : null}
            {o.allowedActions.includes("REQUEST_CANCELLATION") && !requested ? (
              <Button
                label={t.requestCancellation}
                variant="secondary"
                disabled={busy}
                onPress={() =>
                  void command(
                    () =>
                      api.POST("/api/v1/orders/{orderId}/cancellation-requests", {
                        params: { path: { orderId: o.id }, header: { "Idempotency-Key": newKey("app") } },
                        body: { expectedVersion: o.version, reasonCode: "CHANGED_MIND" },
                      }),
                    () => setRequested(true),
                  )
                }
              />
            ) : null}
            {requested ? <Notice locale={locale}>{t.cancellationRequested}</Notice> : null}
            <ErrorLine t={t} code={error} />
            {o.state === "COMPLETED" ? <ReviewForm orderId={o.id} /> : null}
            <Button label={t.newCase} variant="secondary" onPress={() => navigation.navigate("Support", { orderId: o.id })} />
            {o.items[0] ? <OrderAgain mealId={o.items[0].mealId} label={t.orderAgain} onOpen={(mealId) => navigation.navigate("Meal", { mealId })} /> : null}
          </View>
        )}
      </StatusView>
    </Screen>
  );
}

function ReviewForm({ orderId }: { orderId: string }) {
  const { api, t, locale } = useApp();
  const [rating, setRating] = useState(5);
  const [text, setText] = useState("");
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (done || error === "STATE_CONFLICT") {
    return <Notice locale={locale}>{t.reviewed}</Notice>;
  }
  return (
    <Card>
      <Body locale={locale} strong>
        {t.rateOrder}
      </Body>
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6 }}>
        {[1, 2, 3, 4, 5].map((n) => (
          <Choice key={n} label={t.rating(n)} selected={rating === n} onPress={() => setRating(n)} />
        ))}
      </View>
      <Field label={t.comment} value={text} onChangeText={setText} multiline />
      <Button
        label={t.submitReview}
        onPress={() =>
          void api
            .POST("/api/v1/orders/{orderId}/reviews", { params: { path: { orderId } }, body: text.trim() ? { rating, text: text.trim() } : { rating } })
            .then((r) => (r.data ? setDone(true) : setError(errorCode(r.error))))
            .catch(() => setError("NETWORK"))
        }
      />
      <ErrorLine t={t} code={error} />
    </Card>
  );
}

function OrderAgain({ mealId, label, onOpen }: { mealId: string; label: string; onOpen: (mealId: string) => void }) {
  return <Button label={label} variant="secondary" onPress={() => onOpen(mealId)} />;
}
