import { useState } from "react";
import { formatMoney, formatTime, type Schemas } from "../api";
import { egpToMinor, ErrorLine, Status, useMutation, useServer, type Ctx } from "./shared";

type Meal = Schemas["CookMeal"];

async function sha256Hex(file: File): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", await file.arrayBuffer());
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}

export function Meals({ ctx }: { ctx: Ctx }) {
  const [state, reload] = useServer<Schemas["CookMealPage"]>(ctx, () => ctx.api.GET("/api/v1/cook/meals", { params: { query: { limit: 100 } } }), ctx.locale);
  const { t } = ctx;
  return (
    <div className="stack">
      <NewMeal ctx={ctx} refresh={reload} />
      <section aria-labelledby="meals-heading">
        <h2 id="meals-heading">{t.meals}</h2>
        <Status ctx={ctx} state={state} onRetry={reload}>
          {(page) => (
            <ul className="cards">
              {page.items.map((m) => (
                <li key={m.id}>
                  <MealCard ctx={ctx} meal={m} refresh={reload} />
                </li>
              ))}
            </ul>
          )}
        </Status>
      </section>
    </div>
  );
}

function MealCard({ ctx, meal, refresh }: { ctx: Ctx; meal: Meal; refresh: () => unknown }) {
  const { t, locale } = ctx;
  const { run, error, busy } = useMutation(ctx, refresh);
  const actions: ("PUBLISH" | "PAUSE" | "ARCHIVE")[] =
    meal.status === "DRAFT" || meal.status === "PAUSED" ? ["PUBLISH", "ARCHIVE"] : meal.status === "PUBLISHED" ? ["PAUSE", "ARCHIVE"] : [];
  const label = { PUBLISH: t.publish, PAUSE: t.pause, ARCHIVE: t.archive };
  return (
    <article className="card" aria-label={meal.content.titleAr ?? meal.id}>
      <header className="card-head">
        <h3>{(locale === "en" ? meal.content.titleEn : null) ?? meal.content.titleAr ?? "—"}</h3>
        <span className="badge">{t.mealStatus[meal.status]}</span>
      </header>
      <p>{meal.content.priceMinor != null ? formatMoney(meal.content.priceMinor, locale) : "—"}</p>
      <div className="actions">
        {actions.map((a) => (
          <button
            key={a}
            type="button"
            className={a === "ARCHIVE" ? "danger" : undefined}
            disabled={busy}
            onClick={() =>
              void run(() =>
                ctx.api.POST("/api/v1/cook/meals/{mealId}/publication", {
                  params: { path: { mealId: meal.id } },
                  body: { action: a, expectedVersion: meal.version },
                }),
              )
            }
          >
            {label[a]}
          </button>
        ))}
      </div>
      <PriceHistory ctx={ctx} meal={meal} refresh={refresh} />
      <ErrorLine message={error} />
    </article>
  );
}

/**
 * D14 (MB-048): the active price (the meal's current price) and its previous actual changes with Africa/Cairo times,
 * read from API-099; a simple price change through the existing meal edit (new prices apply to orders not yet placed).
 */
function PriceHistory({ ctx, meal, refresh }: { ctx: Ctx; meal: Meal; refresh: () => unknown }) {
  const { t, locale } = ctx;
  const [open, setOpen] = useState(false);
  const [price, setPrice] = useState("");
  const priceMinor = egpToMinor(price);
  const [history, reload] = useServer<Schemas["MealPriceHistory"]>(
    ctx,
    () => (open ? ctx.api.GET("/api/v1/cook/meals/{mealId}/price-history", { params: { path: { mealId: meal.id } } }) : Promise.resolve({ response: new Response(null, { status: 204 }) })),
    `${open}|${meal.version}`,
  );
  const { run, error, busy } = useMutation(ctx, () => {
    refresh();
    reload();
  });
  const sectionId = `price-history-${meal.id}`;
  return (
    <div className="stack">
      <button type="button" className="secondary" aria-expanded={open} aria-controls={sectionId} onClick={() => setOpen(!open)}>
        {t.priceHistory}
      </button>
      {open ? (
        <section id={sectionId} aria-label={t.priceHistory} className="stack">
          <Status ctx={ctx} state={history} onRetry={reload}>
            {(h) => {
              const [current, ...previous] = h.changes;
              return (
                <>
                  <p>
                    <strong>{t.activePrice}:</strong> {h.activePrice ? formatMoney(h.activePrice.amountMinor, locale) : "—"}
                    {current ? ` · ${t.since} ${formatTime(current.changedAt, locale)}` : ""}
                  </p>
                  <h4>{t.previousPrices}</h4>
                  {previous.length === 0 ? (
                    <p>{t.noPreviousPrices}</p>
                  ) : (
                    <ol className="rows" aria-label={t.previousPrices}>
                      {previous.map((c) => (
                        <li key={c.revision}>
                          {formatMoney(c.price.amountMinor, locale)} · <time dateTime={c.changedAt}>{formatTime(c.changedAt, locale)}</time>
                        </li>
                      ))}
                    </ol>
                  )}
                </>
              );
            }}
          </Status>
          <p className="hint">{t.priceChangeNote}</p>
          {meal.status !== "ARCHIVED" ? (
            <div className="form">
              <label>
                {t.newPrice}{" "}
                <input inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} aria-invalid={price !== "" && priceMinor === null} />
              </label>
              <button
                type="button"
                disabled={busy || priceMinor === null || priceMinor <= 0 || priceMinor === meal.content.priceMinor}
                onClick={() =>
                  void run(() =>
                    ctx.api.PATCH("/api/v1/cook/meals/{mealId}", {
                      params: { path: { mealId: meal.id } },
                      body: { expectedVersion: meal.version, priceMinor: priceMinor ?? 0 },
                    }),
                  ).then((ok) => ok && setPrice(""))
                }
              >
                {t.changePrice}
              </button>
              <ErrorLine message={error} />
            </div>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}

function NewMeal({ ctx, refresh }: { ctx: Ctx; refresh: () => unknown }) {
  const { t } = ctx;
  const [titleAr, setTitleAr] = useState("");
  const [titleEn, setTitleEn] = useState("");
  const [descriptionAr, setDescriptionAr] = useState("");
  const [portionAr, setPortionAr] = useState("");
  const [ingredientsAr, setIngredientsAr] = useState("");
  const [price, setPrice] = useState("");
  const [declaration, setDeclaration] = useState<"NONE_DECLARED" | "UNKNOWN">("NONE_DECLARED");
  const [photo, setPhoto] = useState<File | null>(null);
  const { run, error, busy } = useMutation(ctx, refresh);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const priceMinor = egpToMinor(price);

  /** Signed direct upload; the server verifies type, size, checksum and dimensions before approving. */
  async function upload(file: File): Promise<string | null> {
    const ticket = await ctx.api.POST("/api/v1/uploads", {
      body: { purpose: "MEAL_PHOTO", filename: file.name, contentType: file.type as "image/jpeg", bytes: file.size, sha256: await sha256Hex(file) },
    });
    if (!ticket.data) {
      return null;
    }
    const put = await fetch(ticket.data.uploadUrl, { method: "PUT", headers: ticket.data.requiredHeaders as Record<string, string>, body: file });
    if (!put.ok) {
      return null;
    }
    const done = await ctx.api.POST("/api/v1/uploads/{uploadId}/complete", { params: { path: { uploadId: ticket.data.uploadId } }, body: {} });
    return done.data?.state === "APPROVED" ? done.data.id : null;
  }

  return (
    <form
      className="card form"
      aria-labelledby="new-meal-heading"
      onSubmit={(e) => {
        e.preventDefault();
        void (async () => {
          setUploadError(null);
          let mediaId: string | null = null;
          if (photo) {
            mediaId = await upload(photo).catch(() => null);
            if (!mediaId) {
              setUploadError(t.genericError);
              return;
            }
          }
          const ok = await run(() =>
            ctx.api.POST("/api/v1/cook/meals", {
              body: {
                titleAr,
                ...(titleEn ? { titleEn } : {}),
                ...(descriptionAr ? { descriptionAr } : {}),
                ...(portionAr ? { portionAr } : {}),
                ...(ingredientsAr ? { ingredientsAr } : {}),
                ...(priceMinor !== null ? { priceMinor } : {}),
                allergenDeclaration: declaration,
                ...(mediaId ? { photos: [{ mediaId }] } : {}),
              },
            }),
          );
          if (ok) {
            setTitleAr("");
            setTitleEn("");
            setDescriptionAr("");
            setPortionAr("");
            setIngredientsAr("");
            setPrice("");
            setPhoto(null);
          }
        })();
      }}
    >
      <h2 id="new-meal-heading">{t.newMeal}</h2>
      <label>
        {t.titleAr} <input required lang="ar" dir="rtl" value={titleAr} onChange={(e) => setTitleAr(e.target.value)} />
      </label>
      <label>
        {t.titleEn} <input lang="en" dir="ltr" value={titleEn} onChange={(e) => setTitleEn(e.target.value)} />
      </label>
      <label>
        {t.description} <textarea lang="ar" dir="rtl" value={descriptionAr} onChange={(e) => setDescriptionAr(e.target.value)} />
      </label>
      <label>
        {t.portion} <input lang="ar" dir="rtl" value={portionAr} onChange={(e) => setPortionAr(e.target.value)} />
      </label>
      <label>
        {t.ingredients} <textarea lang="ar" dir="rtl" value={ingredientsAr} onChange={(e) => setIngredientsAr(e.target.value)} />
      </label>
      <label>
        {t.price} <input inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} aria-invalid={price !== "" && priceMinor === null} />
      </label>
      <label>
        {t.allergens}{" "}
        <select value={declaration} onChange={(e) => setDeclaration(e.target.value as "NONE_DECLARED" | "UNKNOWN")}>
          <option value="NONE_DECLARED">{t.allergenNone}</option>
          <option value="UNKNOWN">{t.allergenUnknown}</option>
        </select>
      </label>
      <label>
        {t.photo} <input type="file" accept="image/jpeg,image/png" onChange={(e) => setPhoto(e.target.files?.[0] ?? null)} />
      </label>
      <button type="submit" disabled={busy || !titleAr.trim()}>
        {t.create}
      </button>
      <ErrorLine message={uploadError ?? error} />
    </form>
  );
}
