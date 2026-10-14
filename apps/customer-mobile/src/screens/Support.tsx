import { useState } from "react";
import { View } from "react-native";
import { useApp } from "../AppContext";
import { errorCode } from "../mobileApi";
import { Body, Button, Card, Choice, ErrorLine, Field, Notice, Screen, StatusView, Title } from "../ui";
import { useServer } from "../useServer";
import type { RootProps } from "./navigation";

const CATEGORIES = ["LATE", "MISSING_ITEM", "QUALITY", "FOOD_SAFETY", "CASH", "HANDOVER", "OTHER"] as const;

/** Order-scoped support cases; staff-only notes never reach the customer (server-enforced). */
export function SupportScreen({ route }: RootProps<"Support">) {
  const { api, t, locale } = useApp();
  const [cases, reload] = useServer(() => api.GET("/api/v1/support/cases"), locale);
  const [orders] = useServer(() => api.GET("/api/v1/orders", { params: { query: { limit: 20 } } }), "orders");
  const [orderId, setOrderId] = useState<string | null>(route.params.orderId ?? null);
  const [category, setCategory] = useState<(typeof CATEGORIES)[number]>("QUALITY");
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState<string | null>(null);
  return (
    <Screen locale={locale} testID="support">
      <Title locale={locale}>{t.support}</Title>
      <StatusView t={t} locale={locale} state={cases} onRetry={reload}>
        {(page) =>
          page.items.length === 0 ? (
            <Body locale={locale} muted>
              {t.noCases}
            </Body>
          ) : (
            <View style={{ gap: 8 }}>
              {page.items.map((c) => (
                <Card key={c.id}>
                  <Body locale={locale}>
                    {c.reference} · {c.orderNumber ?? ""} · {t.caseCategories[c.category] ?? c.category} · {c.state}
                  </Body>
                </Card>
              ))}
            </View>
          )
        }
      </StatusView>
      <Card>
        <Body locale={locale} strong>
          {t.newCase}
        </Body>
        {orders.status === "ok"
          ? orders.data.items.map((o) => <Choice key={o.id} label={`${t.caseOrder}: ${o.publicNumber}`} selected={orderId === o.id} onPress={() => setOrderId(o.id)} />)
          : null}
        <Body locale={locale}>{t.caseCategory}</Body>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6 }}>
          {CATEGORIES.map((c) => (
            <Choice key={c} label={t.caseCategories[c] ?? c} selected={category === c} onPress={() => setCategory(c)} />
          ))}
        </View>
        <Field label={t.caseDescription} value={description} onChangeText={setDescription} multiline />
        <Button
          label={t.submitCase}
          disabled={!orderId || !description.trim()}
          onPress={() =>
            void api
              .POST("/api/v1/support/cases", { body: { orderId: orderId ?? "", category, description: description.trim() } })
              .then((r) => {
                if (r.data) {
                  setSent(r.data.reference);
                  setDescription("");
                  reload();
                } else {
                  setError(errorCode(r.error));
                }
              })
              .catch(() => setError("NETWORK"))
          }
        />
        {sent ? <Notice locale={locale}>{t.caseSent(sent)}</Notice> : null}
        <ErrorLine t={t} code={error} />
      </Card>
    </Screen>
  );
}
