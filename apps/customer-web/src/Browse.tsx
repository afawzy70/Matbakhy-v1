import { brand } from "@matbakhy/design-tokens";
import { useState } from "react";
import { formatMoney, formatTime, type Schemas } from "./api";
import { egpToMinor, Status, useServer, type Ctx } from "./shared";

export const CATEGORIES = ["MAIN_DISHES", "SIDES_SALADS", "SOUPS", "BAKED_GOODS", "DESSERTS", "BREAKFAST", "READY_TO_COOK"] as const;
type Category = (typeof CATEGORIES)[number];
const INTENTS = ["SELF", "COMPLEMENT", "GATHERING"] as const;
type Intent = (typeof INTENTS)[number];
type Sort = "NEWEST" | "PRICE_ASC" | "PRICE_DESC";
type Meal = Schemas["MealSummary"];

/**
 * Home in the owner-selected hierarchy (docs/design/approved): brand strip, three intent tiles that reveal contextual
 * filters, dishes/kitchens switch, search and filter action, category chips, stacked food cards. Every filter is a
 * server query parameter; the client never filters, prices or ranks meals itself.
 */
export function Browse({ ctx }: { ctx: Ctx }) {
  const { t, locale } = ctx;
  const [zoneId, setZoneId] = useState("");
  const [q, setQ] = useState("");
  const [query, setQuery] = useState("");
  const [intent, setIntent] = useState<Intent | null>(null);
  const [category, setCategory] = useState<Category | null>(null);
  const [sort, setSort] = useState<Sort>("NEWEST");
  const [maxPrice, setMaxPrice] = useState("");
  const [maxPriceMinor, setMaxPriceMinor] = useState<number | null>(null);
  const [showFilters, setShowFilters] = useState(false);
  const [mode, setMode] = useState<"dishes" | "kitchens">("dishes");
  const [zones] = useServer(ctx, () => ctx.api.GET("/api/v1/zones"), locale);
  const [meals, reload] = useServer(
    ctx,
    () =>
      ctx.api.GET("/api/v1/meals", {
        params: {
          query: {
            limit: 50,
            sort,
            ...(zoneId ? { zoneId } : {}),
            ...(query ? { q: query } : {}),
            ...(category ? { categoryId: category } : {}),
            ...(maxPriceMinor !== null ? { maxPriceMinor } : {}),
          },
        },
      }),
    `${zoneId}|${query}|${category}|${sort}|${maxPriceMinor}|${locale}`,
  );
  const filtered = category !== null || maxPriceMinor !== null || sort !== "NEWEST" || query !== "";
  const clear = () => {
    setCategory(null);
    setSort("NEWEST");
    setMaxPrice("");
    setMaxPriceMinor(null);
    setQ("");
    setQuery("");
  };
  return (
    <div className="home stack">
      <section className="brand-strip" aria-label={brand.name[locale]}>
        <div>
          <strong>{brand.name[locale]}</strong>
          <span>{brand.slogan[locale]}</span>
        </div>
        <span className="pill">{t.home.preorder}</span>
      </section>

      <section aria-labelledby="intent-heading" className="stack-sm">
        <div className="row between">
          <h2 id="intent-heading">{t.home.intentTitle}</h2>
          <span className="muted small">{t.home.intentHint}</span>
        </div>
        <div className="intents">
          {INTENTS.map((i) => (
            <button key={i} type="button" className="intent" aria-pressed={intent === i} onClick={() => setIntent(intent === i ? null : i)}>
              <strong>{t.home.intents[i].title}</strong>
              <span className="small">{t.home.intents[i].hint}</span>
            </button>
          ))}
        </div>
        {intent ? (
          <div className="contextual card" id="contextual" aria-label={t.home.contextual}>
            <span className="small muted">{t.home.sortLabel}</span>
            <div className="chips">
              {(["NEWEST", "PRICE_ASC", "PRICE_DESC"] as const).map((s) => (
                <button key={s} type="button" aria-pressed={sort === s} onClick={() => setSort(s)}>
                  {t.home.sort[s]}
                </button>
              ))}
            </div>
            <button type="button" className="link" onClick={clear} disabled={!filtered}>
              {t.home.clear}
            </button>
          </div>
        ) : null}
      </section>

      <div className="mode-switch" role="group" aria-label={t.home.modeLabel}>
        <button type="button" aria-pressed={mode === "dishes"} onClick={() => setMode("dishes")}>
          {t.home.dishes}
        </button>
        <button type="button" aria-pressed={mode === "kitchens"} onClick={() => setMode("kitchens")}>
          {t.home.kitchens}
        </button>
      </div>

      <form
        className="search-bar"
        role="search"
        onSubmit={(e) => {
          e.preventDefault();
          setQuery(q.trim());
        }}
      >
        <label className="grow">
          <span className="sr-only">{t.search}</span>
          <input type="search" aria-label={t.search} placeholder={t.search} maxLength={80} value={q} onChange={(e) => setQ(e.target.value)} />
        </label>
        <button type="submit">{t.search}</button>
        <button type="button" className="secondary" aria-expanded={showFilters} aria-controls="filters" onClick={() => setShowFilters(!showFilters)}>
          {t.home.filters}
        </button>
      </form>
      {showFilters ? (
        <form
          id="filters"
          className="card form"
          onSubmit={(e) => {
            e.preventDefault();
            setMaxPriceMinor(maxPrice.trim() ? egpToMinor(maxPrice) : null);
            setShowFilters(false);
          }}
        >
          <label>
            {t.zone}{" "}
            <select value={zoneId} onChange={(e) => setZoneId(e.target.value)}>
              <option value="">{t.chooseZone}</option>
              {zones.status === "ok"
                ? zones.data.items.map((z) => (
                    <option key={z.id} value={z.id}>
                      {z.name}
                    </option>
                  ))
                : null}
            </select>
          </label>
          <label>
            {t.home.maxPrice} <input inputMode="decimal" value={maxPrice} onChange={(e) => setMaxPrice(e.target.value)} />
          </label>
          {maxPrice.trim() && egpToMinor(maxPrice) === null ? <p className="field-error">{t.home.invalidPrice}</p> : null}
          <button type="submit" disabled={maxPrice.trim() !== "" && egpToMinor(maxPrice) === null}>
            {t.home.showResults}
          </button>
        </form>
      ) : null}

      <div className="chips" role="group" aria-label={t.home.categoriesLabel}>
        <button type="button" aria-pressed={category === null} onClick={() => setCategory(null)}>
          {t.home.all}
        </button>
        {CATEGORIES.map((c) => (
          <button key={c} type="button" aria-pressed={category === c} onClick={() => setCategory(c)}>
            {t.home.categories[c]}
          </button>
        ))}
      </div>

      <section aria-labelledby="results-heading" className="stack-sm">
        <h2 id="results-heading">{mode === "dishes" ? t.home.resultsTitle : t.home.kitchensTitle}</h2>
        <Status ctx={ctx} state={meals} onRetry={reload}>
          {(page) =>
            page.items.length === 0 ? (
              <div className="card pad stack-sm">
                <p className="muted">{t.noMeals}</p>
                {filtered ? (
                  <button type="button" className="secondary" onClick={clear}>
                    {t.home.clear}
                  </button>
                ) : null}
              </div>
            ) : mode === "dishes" ? (
              <ul className="food-list">
                {page.items.map((m) => (
                  <li key={m.id}>
                    <FoodCard ctx={ctx} meal={m} />
                  </li>
                ))}
              </ul>
            ) : (
              <KitchenList ctx={ctx} meals={page.items} />
            )
          }
        </Status>
      </section>
    </div>
  );
}

export function FoodCard({ ctx, meal: m }: { ctx: Ctx; meal: Meal }) {
  const { t, locale } = ctx;
  const unavailable = m.availability !== "AVAILABLE";
  return (
    <article className="food-card" aria-label={m.title}>
      <div className="food-photo">
        {m.photo ? <img src={m.photo.url} alt={m.photo.alt} loading="lazy" /> : null}
        {m.nextSlots[0] ? <span className="pill on-photo">{formatTime(m.nextSlots[0].startsAt, locale)}</span> : null}
      </div>
      <div className="food-body">
        <div className="row between">
          <h3>
            <a href={`#/meal/${m.id}`}>{m.title}</a>
          </h3>
          <span className="price">
            <bdi>{formatMoney(m.price.amountMinor, locale)}</bdi>
          </span>
        </div>
        <a className="kitchen-link" href={`#/kitchen/${m.kitchen.slug}`}>
          {m.kitchen.name}
        </a>
        <div className="row between card-actions">
          <span className="pill portion">{m.portionText}</span>
          {unavailable ? (
            <span className="pill muted">{t.availability[m.availability]}</span>
          ) : (
            <a className="button" href={`#/meal/${m.id}`} aria-label={`${t.home.details}: ${m.title}`}>
              {t.home.details}
            </a>
          )}
        </div>
      </div>
    </article>
  );
}

/** Kitchens in the current server results, in result order; the meal count is of those results only. */
function KitchenList({ ctx, meals }: { ctx: Ctx; meals: Meal[] }) {
  const { t } = ctx;
  const kitchens = new Map<string, { kitchen: Meal["kitchen"]; count: number }>();
  for (const m of meals) {
    const entry = kitchens.get(m.kitchen.id);
    kitchens.set(m.kitchen.id, { kitchen: m.kitchen, count: (entry?.count ?? 0) + 1 });
  }
  return (
    <ul className="food-list">
      {[...kitchens.values()].map(({ kitchen, count }) => (
        <li key={kitchen.id}>
          <article className="card pad row kitchen-card" aria-label={kitchen.name}>
            <span className="avatar" aria-hidden="true">
              {kitchen.name.trim().charAt(0)}
            </span>
            <div className="grow">
              <h3>{kitchen.name}</h3>
              <p className="muted small">{t.home.dishCount(count)}</p>
            </div>
            <a className="button secondary" href={`#/kitchen/${kitchen.slug}`} aria-label={`${t.home.openKitchen}: ${kitchen.name}`}>
              {t.home.openKitchen}
            </a>
          </article>
        </li>
      ))}
    </ul>
  );
}
