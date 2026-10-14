import type { Schemas } from "../api";
import { Status, useServer, type Ctx } from "./shared";

/** No approved kitchen: show the application state. Editing the application is a later customer-web/mobile flow. */
export function Onboarding({ ctx }: { ctx: Ctx }) {
  const [state, reload] = useServer<Schemas["CookApplication"] | null>(
    ctx,
    async () => {
      const r = await ctx.api.GET("/api/v1/cook/application");
      return r.response.status === 404 ? { data: null, response: r.response } : r;
    },
  );
  const { t } = ctx;
  return (
    <section className="card" aria-labelledby="onboarding-heading">
      <h2 id="onboarding-heading">{t.noKitchen}</h2>
      <Status ctx={ctx} state={state} onRetry={reload}>
        {(app) =>
          app ? (
            <p>
              {t.applicationState}: <strong>{app.state}</strong>
            </p>
          ) : (
            <p>{t.noApplication}</p>
          )
        }
      </Status>
    </section>
  );
}
