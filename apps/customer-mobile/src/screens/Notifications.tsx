import { View } from "react-native";
import { useApp } from "../AppContext";
import { formatTime } from "../mobileApi";
import { Body, Button, Card, Screen, StatusView } from "../ui";
import { useServer } from "../useServer";
import type { RootProps } from "./navigation";

export function NotificationsScreen({ navigation }: RootProps<"Notifications">) {
  const { api, t, locale } = useApp();
  const [items, reload] = useServer(() => api.GET("/api/v1/notifications", { params: { query: { limit: 20 } } }), locale);
  return (
    <Screen locale={locale} testID="notifications">
      <StatusView t={t} locale={locale} state={items} onRetry={reload}>
        {(page) =>
          page.items.length === 0 ? (
            <Body locale={locale} muted>
              {t.noNotifications}
            </Body>
          ) : (
            <View style={{ gap: 12 }}>
              {page.items.map((n) => (
                <Card key={n.id}>
                  <Body locale={locale} strong={!n.readAt}>
                    {t.state[String(n.params["state"] ?? "")] ?? n.type} · {String(n.params["orderNumber"] ?? "")}
                  </Body>
                  <Body locale={locale} muted>
                    {formatTime(n.createdAt, locale)}
                  </Body>
                  {n.orderId ? <OrderLink orderId={n.orderId} label={t.tabs.orders} onOpen={(id) => navigation.navigate("Order", { orderId: id })} /> : null}
                  {!n.readAt ? (
                    <Button
                      label={t.markRead}
                      variant="secondary"
                      onPress={() => void api.PUT("/api/v1/notifications/{notificationId}/read", { params: { path: { notificationId: n.id } } }).then(reload, reload)}
                    />
                  ) : null}
                </Card>
              ))}
            </View>
          )
        }
      </StatusView>
    </Screen>
  );
}

function OrderLink({ orderId, label, onOpen }: { orderId: string; label: string; onOpen: (id: string) => void }) {
  return <Button label={label} variant="secondary" onPress={() => onOpen(orderId)} />;
}
