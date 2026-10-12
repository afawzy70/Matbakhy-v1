import { useState } from "react";
import { Alert, View } from "react-native";
import { useApp } from "../AppContext";
import { decideAdd, loadBasket, loadPending, saveBasket, type Basket } from "../basket";
import { formatMoney, formatTime } from "../mobileApi";
import { Body, Button, Card, Choice, Notice, Screen, StatusView, Title } from "../ui";
import { useServer } from "../useServer";
import type { RootProps } from "./navigation";

function maxQuantity(slot: { availablePortions: number } | null): number {
  return slot ? Math.max(1, Math.min(20, slot.availablePortions)) : 20;
}

/**
 * Meal detail with slots from the server. Adding from another kitchen or slot asks before replacing the basket
 * (BR-018, TC-148): "Keep cart" (or dismissing) leaves it unchanged; "Replace cart" replaces it.
 */
export function MealScreen({ route, navigation }: RootProps<"Meal">) {
  const { api, t, locale, auth } = useApp();
  const { mealId } = route.params;
  const [meal, reload] = useServer(() => api.GET("/api/v1/meals/{mealId}", { params: { path: { mealId } } }), `${mealId}|${locale}`);
  const [slotId, setSlotId] = useState<string | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [message, setMessage] = useState<string | null>(null);
  const [favoriteBusy, setFavoriteBusy] = useState(false);
  const [favorite, setFavorite] = useState<boolean | null>(null);

  async function add(target: Omit<Basket, "lines">, line: Basket["lines"][number]) {
    const outcome = decideAdd(await loadBasket(), await loadPending(), target, line);
    if (outcome.kind === "added") {
      await saveBasket(outcome.basket);
      setMessage(t.added);
      return;
    }
    if (outcome.kind === "placementPending") {
      setMessage(t.placementPending);
      return;
    }
    const keep = () => setMessage(t.basketKept);
    Alert.alert(
      t.replaceTitle,
      t.otherKitchen,
      [
        { text: t.keepBasket, style: "cancel", onPress: keep },
        {
          text: t.replaceBasket,
          style: "destructive",
          onPress: () => {
            void saveBasket(outcome.replacement).then(() => setMessage(t.added));
          },
        },
      ],
      { cancelable: true, onDismiss: keep },
    );
  }

  return (
    <Screen locale={locale} testID="meal">
      <StatusView t={t} locale={locale} state={meal} onRetry={reload}>
        {(m) => {
          const slot = m.nextSlots.find((s) => s.id === slotId) ?? null;
          return (
            <View style={{ gap: 12 }}>
              <Title locale={locale}>{m.title}</Title>
              <Body locale={locale} muted>
                {m.kitchen.name}
              </Body>
              <Body locale={locale} strong>
                {formatMoney(m.price.amountMinor, locale)} · {m.portionText}
              </Body>
              {m.description ? <Body locale={locale}>{m.description}</Body> : null}
              <Card>
                <Body locale={locale} strong>
                  {t.ingredients}
                </Body>
                <Body locale={locale}>{m.ingredients}</Body>
                <Body locale={locale}>
                  {m.allergenCodes.includes("UNKNOWN")
                    ? t.allergenUnknown
                    : m.allergenCodes.includes("NONE_DECLARED")
                      ? t.allergenNone
                      : `${t.allergenContains}: ${m.allergenCodes.join("، ")}`}
                </Body>
              </Card>
              <Body locale={locale} strong>
                {t.slots}
              </Body>
              {m.nextSlots.length === 0 ? (
                <Body locale={locale} muted>
                  {t.noSlots}
                </Body>
              ) : null}
              {m.nextSlots.map((s, i) => (
                <Choice
                  key={s.id}
                  testID={`slot-${i}`}
                  selected={slotId === s.id}
                  label={`${formatTime(s.startsAt, locale)} · ${t.portionsLeft(s.availablePortions)} · ${s.fulfillmentModes.map((f) => t.mode[f] ?? f).join(" / ")}`}
                  onPress={() => {
                    setSlotId(s.id);
                    setQuantity((q) => Math.min(q, maxQuantity(s)));
                  }}
                />
              ))}
              <View style={{ flexDirection: "row", gap: 8, alignItems: "center" }} accessibilityLabel={t.quantity}>
                <Button label="−" accessibilityLabel={t.decrease} variant="secondary" disabled={quantity <= 1} onPress={() => setQuantity(quantity - 1)} />
                <Body locale={locale} strong>
                  {quantity}
                </Body>
                <Button label="+" accessibilityLabel={t.increase} variant="secondary" disabled={quantity >= maxQuantity(slot)} onPress={() => setQuantity(quantity + 1)} />
              </View>
              <Button
                testID="add-to-cart"
                label={`${t.addToCart} · ${formatMoney(m.price.amountMinor * quantity, locale)}`}
                disabled={!slot || m.availability !== "AVAILABLE"}
                onPress={() => {
                  if (slot) {
                    void add(
                      { kitchenId: m.kitchen.id, slotId: slot.id, slotStartsAt: slot.startsAt, modes: slot.fulfillmentModes },
                      { mealId: m.id, title: m.title, quantity },
                    );
                  }
                }}
              />
              <Body locale={locale} muted>
                {t.totalNote}
              </Body>
              {message ? (
                <Notice locale={locale} testID="meal-message">
                  {message}
                </Notice>
              ) : null}
              <Button testID="go-to-cart" label={t.cart} variant="secondary" onPress={() => navigation.navigate("Cart")} />
              {auth.kind === "signedIn" ? (
                <Button
                  label={favorite ? t.unfavorite : t.favorite}
                  variant="secondary"
                  disabled={favoriteBusy}
                  onPress={() => {
                    setFavoriteBusy(true);
                    const req = favorite
                      ? api.DELETE("/api/v1/me/favorites/{mealId}", { params: { path: { mealId: m.id } } })
                      : api.PUT("/api/v1/me/favorites/{mealId}", { params: { path: { mealId: m.id } } });
                    void req
                      .then((r) => r.response.ok && setFavorite(!favorite))
                      .catch(() => undefined)
                      .finally(() => setFavoriteBusy(false));
                  }}
                />
              ) : null}
            </View>
          );
        }}
      </StatusView>
    </Screen>
  );
}
