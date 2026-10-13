import { priceChanges } from "@matbakhy/api-client";
import { useFocusEffect } from "@react-navigation/native";
import { useCallback, useRef, useState } from "react";
import { View } from "react-native";
import { useApp } from "../AppContext";
import { estimateMinor, lineLimit, loadBasket, loadPending, saveBasket, savePending, type Basket, type BasketLine, type PendingPlacement } from "../basket";
import { errorText } from "../i18n";
import { errorCode, formatMoney, formatTime, newKey, type Schemas } from "../mobileApi";
import { place, recover, type PlacementOutcome } from "../placement";
import { Body, Button, Card, Choice, ErrorLine, Field, Notice, Screen, StatusView, Title } from "../ui";
import { useServer } from "../useServer";
import type { RootProps } from "./navigation";

type Quote = Schemas["QuoteResponse"];

/**
 * Cart and checkout. Prices come only from the server quote. Before an order is sent, its client request id and
 * idempotency key are stored; if the app is killed or the response is lost, reopening the cart asks the server and
 * either shows the existing order or re-sends with the same keys, so one tap can never create two orders (TC-049).
 * While the outcome is unknown the screen says so plainly and never shows the order as placed or failed.
 */
export function CartScreen({ navigation }: RootProps<"Cart">) {
  const { api, t, locale, auth, startSignIn, reloadProfile, reloadLimits, session } = useApp();
  const [basket, setBasket] = useState<Basket | null | undefined>(undefined);
  const [pending, setPending] = useState<PendingPlacement | null>(null);
  const [outcome, setOutcome] = useState<PlacementOutcome | null>(null);
  const [checking, setChecking] = useState(false);

  const settleOutcome = useCallback(
    (o: PlacementOutcome | null) => {
      setOutcome(o);
      if (o?.kind === "placed") {
        setPending(null);
        setBasket(null);
        navigation.replace("Order", { orderId: o.orderId });
      } else if (o?.kind === "rejected") {
        setPending(null);
      }
    },
    [navigation],
  );

  // One recovery check at a time: a second press (or the check on focus) while one is running waits for it instead of
  // sending another lookup and possibly a second re-send (MB-033 retry review; the server's idempotency stays the backstop).
  const inFlight = useRef<Promise<void> | null>(null);
  const recheck = useCallback(() => {
    if (!inFlight.current) {
      setChecking(true);
      inFlight.current = recover(api)
        .then(settleOutcome)
        .finally(() => {
          inFlight.current = null;
          setChecking(false);
        });
    }
    return inFlight.current;
  }, [api, settleOutcome]);

  useFocusEffect(
    useCallback(() => {
      reloadLimits();
      let active = true;
      void Promise.all([loadBasket(), loadPending()]).then(([b, p]) => {
        if (!active) {
          return;
        }
        setBasket(b);
        setPending(p);
        if (p && auth.kind === "signedIn") {
          void recheck();
        }
      });
      return () => {
        active = false;
      };
    }, [auth.kind, recheck, reloadLimits]),
  );

  if (basket === undefined) {
    return <Screen locale={locale}>{null}</Screen>;
  }
  if (!basket || basket.lines.length === 0) {
    return (
      <Screen locale={locale} testID="cart">
        <Body locale={locale} muted>
          {t.cartEmpty}
        </Body>
      </Screen>
    );
  }
  return (
    <Screen locale={locale} testID="cart">
      <Title locale={locale}>{t.cart}</Title>
      <Card testID="cart-lines">
        {basket.kitchenName ? (
          <Body locale={locale} strong>
            {basket.kitchenName}
          </Body>
        ) : null}
        <Body locale={locale} muted>
          {formatTime(basket.slotStartsAt, locale)}
        </Body>
        {basket.lines.map((l) => (
          <BasketLineRow
            key={l.mealId}
            line={l}
            locked={pending !== null}
            onChange={(next) => {
              const lines = basket.lines.flatMap((x) => (x.mealId !== l.mealId ? [x] : next ? [next] : []));
              const updated = lines.length > 0 ? { ...basket, lines } : null;
              setBasket(updated);
              void saveBasket(updated);
            }}
          />
        ))}
        <Body locale={locale} strong>
          {t.estimatedSubtotal}
        </Body>
        <Body locale={locale} strong testID="estimate">
          {estimateText(basket, locale, t.estimateUnavailable)}
        </Body>
        <Body locale={locale} muted>
          {t.estimateNote}
        </Body>
        <Button
          label={t.clearCart}
          variant="secondary"
          disabled={pending !== null}
          onPress={() => {
            void saveBasket(null).then(() => setBasket(null));
          }}
        />
      </Card>
      {pending ? (
        <View accessibilityRole="alert" testID="pending-order" style={{ gap: 8 }}>
          <Notice locale={locale}>{outcome?.kind === "uncertain" && outcome.offline ? t.pendingOffline : t.pendingUncertain}</Notice>
          <Button testID="retry-order" label={t.retryOrder} disabled={checking || auth.kind !== "signedIn"} onPress={() => void recheck()} />
        </View>
      ) : null}
      {/* A refused stale price is explained by the order form's price-change notice instead (D13). */}
      {outcome?.kind === "rejected" && outcome.code !== "PRICE_CHANGED" ? <ErrorLine t={t} code={outcome.code} /> : null}
      {auth.kind !== "signedIn" ? (
        <Card>
          <Body locale={locale}>{t.signInRequired}</Body>
          <Button label={t.signIn} onPress={() => void startSignIn()} />
        </Card>
      ) : auth.profile && !auth.profile.phoneVerified ? (
        <PhoneVerification
          onVerified={() => {
            if (auth.profile) {
              session.cache("me", { ...auth.profile, phoneVerified: true });
            }
            void reloadProfile();
          }}
        />
      ) : (
        // Any edit changes the key, so a quote for the previous basket can never be accepted for the new one. While an
        // order is pending the form stays mounted but hidden, so a refused stale price can be shown afterwards (D13).
        <OrderForm
          key={basket.lines.map((l) => `${l.mealId}:${l.quantity}`).join("|")}
          hidden={pending !== null}
          basket={basket}
          onPending={(p) => setPending(p)}
          onOutcome={settleOutcome}
        />
      )}
    </Screen>
  );
}

function estimateText(basket: Basket, locale: "ar" | "en", unavailable: string): string {
  const amount = estimateMinor(basket);
  return amount === null ? unavailable : formatMoney(amount, locale);
}

/** One basket line: "quantity × title", quantity within 1 and the per-line limit, removal, and a notice when above it. */
function BasketLineRow({ line, locked, onChange }: { line: BasketLine; locked: boolean; onChange: (next: BasketLine | null) => void }) {
  const { t, locale, maxQuantityPerLine } = useApp();
  const limit = lineLimit(maxQuantityPerLine);
  const max = Math.min(limit, line.maxQuantity ?? limit);
  return (
    <View style={{ gap: 6 }}>
      <Body locale={locale}>
        {line.quantity} × {line.title}
        {line.unitPriceMinor !== undefined ? ` · ${formatMoney(line.unitPriceMinor * line.quantity, locale)}` : ""}
      </Body>
      <View style={{ flexDirection: "row", gap: 8, flexWrap: "wrap" }}>
        <Button testID={`qty-dec-${line.mealId}`} label="−" accessibilityLabel={t.decreaseOf(line.title)} variant="secondary" disabled={locked || line.quantity <= 1} onPress={() => onChange({ ...line, quantity: line.quantity - 1 })} />
        <Button testID={`qty-inc-${line.mealId}`} label="+" accessibilityLabel={t.increaseOf(line.title)} variant="secondary" disabled={locked || line.quantity >= max} onPress={() => onChange({ ...line, quantity: line.quantity + 1 })} />
        <Button testID={`remove-${line.mealId}`} label={t.remove} accessibilityLabel={t.removeLine(line.title)} variant="secondary" disabled={locked} onPress={() => onChange(null)} />
      </View>
      {line.quantity > limit ? <Notice locale={locale}>{t.overLineLimit(limit)}</Notice> : null}
    </View>
  );
}

function PhoneVerification({ onVerified }: { onVerified: () => void }) {
  const { api, t } = useApp();
  const [phone, setPhone] = useState("+20");
  const [challenge, setChallenge] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  async function run<T>(fn: () => Promise<{ data?: T; error?: unknown }>): Promise<T | null> {
    setBusy(true);
    setError(null);
    try {
      const r = await fn();
      if (r.data !== undefined) {
        return r.data;
      }
      setError(errorCode(r.error));
      return null;
    } catch {
      setError("NETWORK");
      return null;
    } finally {
      setBusy(false);
    }
  }
  return (
    <Card testID="phone-verification">
      <Field testID="phone" label={t.phone} value={phone} onChangeText={(v) => setPhone(v.trim())} keyboardType="phone-pad" ltr />
      <Button
        label={t.sendCode}
        disabled={busy}
        onPress={() => void run(() => api.POST("/api/v1/me/phone/challenges", { body: { phoneE164: phone } })).then((c) => c && setChallenge(c.id))}
      />
      {challenge ? (
        <>
          <Field testID="otp" label={t.code} value={code} onChangeText={(v) => setCode(v.replace(/\D/g, ""))} keyboardType="number-pad" ltr />
          <Button
            label={t.verify}
            disabled={busy || code.length < 4}
            onPress={() =>
              void run(() => api.POST("/api/v1/me/phone/challenges/{challengeId}/verify", { params: { path: { challengeId: challenge } }, body: { code } })).then(
                (ok) => ok && onVerified(),
              )
            }
          />
        </>
      ) : null}
      <ErrorLine t={t} code={error} />
    </Card>
  );
}

function OrderForm({
  basket,
  hidden,
  onPending,
  onOutcome,
}: {
  basket: Basket;
  hidden: boolean;
  onPending: (p: PendingPlacement) => void;
  onOutcome: (o: PlacementOutcome) => void;
}) {
  const { api, t, locale, maxQuantityPerLine } = useApp();
  // A line above the server's current per-line limit gets no quote until the customer reduces it (Q1).
  const overLimit = basket.lines.some((l) => l.quantity > lineLimit(maxQuantityPerLine));
  const [mode, setMode] = useState<"DELIVERY" | "PICKUP">(basket.modes.includes("DELIVERY") ? "DELIVERY" : "PICKUP");
  const [addressId, setAddressId] = useState<string | null>(null);
  const [quote, setQuote] = useState<Quote | null>(null);
  const [accepted, setAccepted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // D13: a price that changed before placement is shown and must be accepted explicitly before the new quote is placed.
  const [change, setChange] = useState<{ previous: Quote | null; next: Quote } | null>(null);
  const [changeAccepted, setChangeAccepted] = useState(false);
  const [addresses, reloadAddresses] = useServer(() => api.GET("/api/v1/me/addresses"), "addresses");
  // The first saved address is preselected until the customer picks another.
  const selectedAddress = addressId ?? (addresses.status === "ok" ? (addresses.data.items[0]?.id ?? null) : null);

  function quoteRequest() {
    return {
      items: basket.lines.map((l) => ({ mealId: l.mealId, quantity: l.quantity })),
      slotId: basket.slotId,
      fulfillmentMode: mode,
      ...(mode === "DELIVERY" && selectedAddress ? { addressId: selectedAddress } : {}),
    };
  }

  /** After a refused stale quote: a fresh server quote for the same basket, shown against the one the customer saw. */
  async function requote(previous: Quote) {
    setChangeAccepted(false);
    setAccepted(false);
    try {
      const r = await api.POST("/api/v1/quotes", { body: quoteRequest() });
      if (r.data) {
        setQuote(r.data);
        setChange({ previous, next: r.data });
        return true;
      }
    } catch {
      // Unreachable: the basket stays and "Calculate total" can be pressed again.
    }
    setQuote(null);
    setChange(null);
    setError("REQUOTE_FAILED");
    return false;
  }

  async function getQuote() {
    setBusy(true);
    setError(null);
    setChange(null);
    try {
      const r = await api.POST("/api/v1/quotes", { body: quoteRequest() });
      setQuote(r.data ?? null);
      setError(r.data ? null : errorCode(r.error));
    } catch {
      setError("NETWORK");
    } finally {
      setBusy(false);
    }
  }

  async function submit(q: Quote) {
    // Keys are fixed and stored before the request so a retry can never create a second order.
    const p: PendingPlacement = {
      clientRequestId: newKey(""),
      idempotencyKey: newKey("app"),
      quoteId: q.quoteId,
      terms: q.termsVersion ?? "",
      cancellation: q.cancellationPolicyVersion,
    };
    await savePending(p);
    onPending(p);
    setBusy(true);
    try {
      const o = await place(api, p);
      onOutcome(o);
      if (o.kind === "rejected" && o.code === "PRICE_CHANGED") {
        // Nothing was ordered: re-quote and let the customer review and accept the new prices (never resubmit silently).
        await requote(q);
      } else if (o.kind === "rejected") {
        setQuote(null);
        setChange(null);
      }
    } finally {
      setBusy(false);
    }
  }

  if (hidden) {
    return null;
  }
  return (
    <Card testID="order-form">
      <View style={{ flexDirection: "row", gap: 8 }}>
        {(["DELIVERY", "PICKUP"] as const)
          .filter((m) => basket.modes.includes(m))
          .map((m) => (
            <Choice
              key={m}
              testID={`mode-${m}`}
              label={t.mode[m] ?? m}
              selected={mode === m}
              onPress={() => {
                setMode(m);
                setQuote(null);
                setChange(null);
              }}
            />
          ))}
      </View>
      {mode === "DELIVERY" ? (
        <StatusView t={t} locale={locale} state={addresses} onRetry={reloadAddresses}>
          {(page) => (
            <View style={{ gap: 8 }}>
              <Body locale={locale} strong>
                {t.address}
              </Body>
              {page.items.map((a) => (
                <Choice
                  key={a.id}
                  label={`${a.label} · ${a.street} ${a.building}`}
                  selected={selectedAddress === a.id}
                  onPress={() => {
                    setAddressId(a.id);
                    setQuote(null);
                    setChange(null);
                  }}
                />
              ))}
              <NewAddress onSaved={reloadAddresses} />
            </View>
          )}
        </StatusView>
      ) : null}
      <Button
        testID="get-quote"
        label={t.getQuote}
        disabled={overLimit || busy || (mode === "DELIVERY" && !selectedAddress)}
        onPress={() => void getQuote()}
      />
      {change ? <PriceChangeNotice change={change} accepted={changeAccepted} onAccept={() => setChangeAccepted(true)} /> : null}
      {quote ? (
        <View testID="quote" style={{ gap: 6 }}>
          <Body locale={locale}>
            {t.subtotal}: {formatMoney(quote.subtotal.amountMinor, locale)}
          </Body>
          <Body locale={locale}>
            {t.deliveryFee}: {formatMoney(quote.deliveryFee.amountMinor, locale)}
          </Body>
          <Body locale={locale}>
            {t.tax}: {formatMoney(quote.tax.amountMinor, locale)}
          </Body>
          <Body locale={locale} strong>
            {t.total}: {formatMoney(quote.total.amountMinor, locale)}
          </Body>
          <Body locale={locale} muted>
            {t.cashOnDelivery}
          </Body>
          {quote.warnings.includes("SYNTHETIC_POLICY") ? <Notice locale={locale}>{t.syntheticPolicy}</Notice> : null}
          <Choice testID="accept-terms" label={t.terms} selected={accepted} onPress={() => setAccepted(!accepted)} />
          <Button
            testID="place-order"
            label={busy ? t.placing : t.placeOrder}
            disabled={!accepted || busy || (change !== null && !changeAccepted)}
            onPress={() => void submit(quote)}
          />
        </View>
      ) : null}
      {error ? <Body locale={locale}>{error === "REQUOTE_FAILED" ? t.requoteFailed : errorText(t, error)}</Body> : null}
    </Card>
  );
}

/**
 * D13: what changed since the customer's last total, attributed accurately: the kitchen for meal prices, otherwise the
 * delivery fee or tax; the previous and new total; and an explicit acceptance before the new quote can be placed.
 */
function PriceChangeNotice({ change, accepted, onAccept }: { change: { previous: Quote | null; next: Quote }; accepted: boolean; onAccept: () => void }) {
  const { t, locale } = useApp();
  const money = (minor: number) => formatMoney(minor, locale);
  const d = change.previous ? priceChanges(change.previous, change.next) : null;
  return (
    <View accessibilityRole="alert" testID="price-change" style={{ gap: 6 }}>
      <Notice locale={locale}>{t.priceChangeTitle}</Notice>
      {d?.meals.map((m) => (
        <Body key={m.mealId} locale={locale}>
          {t.kitchenChangedPrice(m.title, money(m.previousMinor), money(m.nextMinor))}
        </Body>
      ))}
      {d?.deliveryFee ? <Body locale={locale}>{t.deliveryFeeChanged(money(d.deliveryFee.previousMinor), money(d.deliveryFee.nextMinor))}</Body> : null}
      {d?.tax ? <Body locale={locale}>{t.taxChanged(money(d.tax.previousMinor), money(d.tax.nextMinor))}</Body> : null}
      {d?.unexplained ? <Body locale={locale}>{t.orderDetailsChanged}</Body> : null}
      <Body locale={locale} strong testID="price-change-total">
        {d ? t.newTotalReview(money(d.total.previousMinor), money(d.total.nextMinor)) : t.newTotalOnly(money(change.next.total.amountMinor))}
      </Body>
      <Body locale={locale} muted>
        {t.editOrLeave}
      </Body>
      {accepted ? (
        <Body locale={locale}>{t.newTotalAccepted}</Body>
      ) : (
        <Button testID="accept-new-total" label={t.acceptNewTotal} onPress={onAccept} />
      )}
    </View>
  );
}

function NewAddress({ onSaved }: { onSaved: () => void }) {
  const { api, t } = useApp();
  const [open, setOpen] = useState(false);
  const [zones] = useServer(() => api.GET("/api/v1/zones"), "zones");
  const [form, setForm] = useState({ label: "", recipientName: "", governorate: "", city: "", street: "", building: "", zoneId: "", phone: "+20" });
  const [error, setError] = useState<string | null>(null);
  if (!open) {
    return <Button label={t.newAddress} variant="secondary" onPress={() => setOpen(true)} />;
  }
  return (
    <View style={{ gap: 8 }}>
      {(["label", "recipientName", "governorate", "city", "street", "building"] as const).map((f) => (
        <Field key={f} label={t.addressFields[f]} value={form[f]} onChangeText={(v) => setForm({ ...form, [f]: v })} />
      ))}
      <Field label={t.phone} value={form.phone} onChangeText={(v) => setForm({ ...form, phone: v.trim() })} keyboardType="phone-pad" ltr />
      {zones.status === "ok"
        ? zones.data.items.map((z) => <Choice key={z.id} label={`${t.zone}: ${z.name}`} selected={form.zoneId === z.id} onPress={() => setForm({ ...form, zoneId: z.id })} />)
        : null}
      <Button
        label={t.saveAddress}
        onPress={() =>
          void api
            .POST("/api/v1/me/addresses", { body: { ...form } })
            .then((r) => {
              if (r.data) {
                setOpen(false);
                onSaved();
              } else {
                setError(errorCode(r.error));
              }
            })
            .catch(() => setError("NETWORK"))
        }
      />
      <ErrorLine t={t} code={error} />
    </View>
  );
}
