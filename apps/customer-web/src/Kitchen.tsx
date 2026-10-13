import { useTrack } from "./activity";
import { brand } from "@matbakhy/design-tokens";
import { FoodCard } from "./Browse";
import { Status, useServer, type Ctx } from "./shared";

/** Public kitchen profile (no private location or contact) and its meals from the server. */
export function Kitchen({ ctx, slug }: { ctx: Ctx; slug: string }) {
  const { t, locale } = ctx;
  const [kitchen, reload] = useServer(ctx, () => ctx.api.GET("/api/v1/kitchens/{slug}", { params: { path: { slug } } }), `${slug}|${locale}`);
  useTrack(ctx.api, "KITCHEN_VIEW", kitchen.status === "ok" ? [kitchen.data.kitchenId] : []);
  return (
    <Status ctx={ctx} state={kitchen} onRetry={reload}>
      {(k) => (
        <div className="stack">
          <a href="#/">{t.back}</a>
          <section className="card pad stack-sm" aria-labelledby="kitchen-title">
            <div className="row">
              <span className="avatar" aria-hidden="true">
                {k.displayName.trim().charAt(0)}
              </span>
              <h2 id="kitchen-title">{k.displayName}</h2>
            </div>
            <p>{k.description}</p>
            <p className="muted small">{t.home.verificationNote(brand.name[locale])}</p>
            {!k.available ? <p className="notice">{t.home.kitchenUnavailable}</p> : null}
          </section>
          <KitchenMeals ctx={ctx} kitchenId={k.kitchenId} />
        </div>
      )}
    </Status>
  );
}

function KitchenMeals({ ctx, kitchenId }: { ctx: Ctx; kitchenId: string }) {
  const { t, locale } = ctx;
  const [meals, reload] = useServer(ctx, () => ctx.api.GET("/api/v1/meals", { params: { query: { kitchenId, limit: 50 } } }), `${kitchenId}|${locale}`);
  return (
    <section aria-labelledby="kitchen-meals" className="stack-sm">
      <h2 id="kitchen-meals">{t.home.kitchenMeals}</h2>
      <Status ctx={ctx} state={meals} onRetry={reload}>
        {(page) =>
          page.items.length === 0 ? (
            <p className="muted">{t.noMeals}</p>
          ) : (
            <ul className="food-list">
              {page.items.map((m) => (
                <li key={m.id}>
                  <FoodCard ctx={ctx} meal={m} />
                </li>
              ))}
            </ul>
          )
        }
      </Status>
    </section>
  );
}
