import { useState } from "react";
import { formatMoney, type Schemas } from "../api";
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
      <ErrorLine message={error} />
    </article>
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
