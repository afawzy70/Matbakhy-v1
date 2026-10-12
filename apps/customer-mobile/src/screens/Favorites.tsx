import { useFocusEffect } from "@react-navigation/native";
import { useCallback } from "react";
import { View } from "react-native";
import { useApp } from "../AppContext";
import { Body, Button, Card, Screen, StatusView } from "../ui";
import { useServer } from "../useServer";
import type { TabProps } from "./navigation";

export function FavoritesScreen({ navigation }: TabProps<"Favorites">) {
  const { api, t, locale, auth, startSignIn } = useApp();
  const signedIn = auth.kind === "signedIn";
  const [favorites, reload] = useServer(() => api.GET("/api/v1/me/favorites", { params: { query: { limit: 100 } } }), `${signedIn}|${locale}`);
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
    <Screen locale={locale} testID="favorites">
      <StatusView t={t} locale={locale} state={favorites} onRetry={reload}>
        {(page) =>
          page.items.length === 0 ? (
            <Body locale={locale} muted>
              {t.noFavorites}
            </Body>
          ) : (
            <View style={{ gap: 12 }}>
              {page.items.map((f) => (
                <Card key={f.mealId}>
                  <Body locale={locale} strong>
                    {f.title}
                  </Body>
                  {f.available ? null : (
                    <Body locale={locale} muted>
                      {t.unavailable}
                    </Body>
                  )}
                  <Button label={t.tabs.discover} variant="secondary" onPress={() => navigation.navigate("Meal", { mealId: f.mealId })} />
                  <Button
                    label={t.unfavorite}
                    variant="secondary"
                    onPress={() => void api.DELETE("/api/v1/me/favorites/{mealId}", { params: { path: { mealId: f.mealId } } }).then(reload, reload)}
                  />
                </Card>
              ))}
            </View>
          )
        }
      </StatusView>
    </Screen>
  );
}
