import { useFocusEffect } from "@react-navigation/native";
import { useCallback, useState } from "react";
import { Pressable, Text, View } from "react-native";
import { useApp } from "../AppContext";
import { loadBasket } from "../basket";
import { formatMoney } from "../mobileApi";
import { Body, Button, Card, Field, Screen, StatusView, colors } from "../ui";
import { useServer } from "../useServer";
import type { TabProps } from "./navigation";

/** Native home: search and published, orderable meals from the server (the client never filters, prices or ranks). */
export function DiscoverScreen({ navigation }: TabProps<"Discover">) {
  const { api, t, locale } = useApp();
  const [q, setQ] = useState("");
  const [query, setQuery] = useState("");
  const [basketCount, setBasketCount] = useState(0);
  const [meals, reload] = useServer(() => api.GET("/api/v1/meals", { params: { query: { limit: 50, sort: "NEWEST", ...(query ? { q: query } : {}) } } }), `${query}|${locale}`);
  useFocusEffect(
    useCallback(() => {
      void loadBasket().then((b) => setBasketCount(b?.lines.reduce((n, l) => n + l.quantity, 0) ?? 0));
    }, []),
  );
  return (
    <Screen locale={locale} testID="discover">
      {basketCount > 0 ? <Button testID="open-cart" label={t.openCart(basketCount)} onPress={() => navigation.navigate("Cart")} /> : null}
      <Field testID="search" label={t.search} value={q} onChangeText={setQ} />
      <Button label={t.searchAction} variant="secondary" onPress={() => setQuery(q.trim())} />
      <StatusView t={t} locale={locale} state={meals} onRetry={reload}>
        {(page) =>
          page.items.length === 0 ? (
            <Body locale={locale} muted>
              {t.noMeals}
            </Body>
          ) : (
            <View style={{ gap: 12 }}>
              {page.items.map((m) => (
                <Pressable
                  key={m.id}
                  testID={`meal-${m.id}`}
                  accessibilityRole="button"
                  accessibilityLabel={`${m.title}, ${m.kitchen.name}, ${formatMoney(m.price.amountMinor, locale)}`}
                  onPress={() => navigation.navigate("Meal", { mealId: m.id })}
                >
                  <Card>
                    <Body locale={locale} strong>
                      {m.title}
                    </Body>
                    <Body locale={locale} muted>
                      {m.kitchen.name}
                    </Body>
                    {m.description ? <Body locale={locale}>{m.description}</Body> : null}
                    <Text style={{ color: colors["primary"], fontWeight: "700" }}>{formatMoney(m.price.amountMinor, locale)}</Text>
                    {m.availability !== "AVAILABLE" ? (
                      <Body locale={locale} muted>
                        {m.availability === "SOLD_OUT" ? t.soldOut : t.unavailable}
                      </Body>
                    ) : null}
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
