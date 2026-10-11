import { useState } from "react";
import { formatMoney, type Schemas } from "../api";
import { ErrorLine, Status, useMutation, useServer, type Ctx } from "../cook/shared";

type Item = Schemas["OpsCataloguePage"]["items"][number];

export function Catalogue({ ctx }: { ctx: Ctx }) {
  const { t } = ctx;
  const [moderation, setModeration] = useState<"ALL" | "VISIBLE" | "HIDDEN">("ALL");
  const [q, setQ] = useState("");
  const [query, setQuery] = useState("");
  const [state, reload] = useServer<Schemas["OpsCataloguePage"]>(
    ctx,
    () =>
      ctx.api.GET("/api/v1/ops/catalogue", {
        params: { query: { limit: 50, ...(moderation === "ALL" ? {} : { moderationState: moderation }), ...(query ? { q: query } : {}) } },
      }),
    `${moderation}|${query}`,
  );
  return (
    <div className="stack">
      <form
        className="card inline-form"
        onSubmit={(e) => {
          e.preventDefault();
          setQuery(q.trim());
        }}
      >
        <label>
          {t.ops.search} <input value={q} maxLength={80} onChange={(e) => setQ(e.target.value)} />
        </label>
        <div role="group" aria-label={t.ops.catalogue} className="segmented">
          {(["ALL", "VISIBLE", "HIDDEN"] as const).map((m) => (
            <button key={m} type="button" aria-pressed={moderation === m} onClick={() => setModeration(m)}>
              {m === "ALL" ? t.ops.filterAll : m === "VISIBLE" ? t.ops.visible : t.ops.hidden}
            </button>
          ))}
        </div>
        <button type="submit">{t.show}</button>
      </form>
      <Status ctx={ctx} state={state} onRetry={reload}>
        {(page) =>
          page.items.length === 0 ? (
            <p className="muted">{t.ops.noItems}</p>
          ) : (
            <ul className="cards">
              {page.items.map((m) => (
                <li key={m.id}>
                  <ModerationCard ctx={ctx} item={m} refresh={reload} />
                </li>
              ))}
            </ul>
          )
        }
      </Status>
      <ReviewQueue ctx={ctx} />
    </div>
  );
}

type QueuedReview = Schemas["ReviewQueue"]["items"][number];

/** API-098 + API-073: reported visible reviews (most reported first) and hidden reviews; every change needs a reason. */
function ReviewQueue({ ctx }: { ctx: Ctx }) {
  const { t } = ctx;
  const [visibility, setVisibility] = useState<"VISIBLE" | "HIDDEN">("VISIBLE");
  const [state, reload] = useServer<Schemas["ReviewQueue"]>(ctx, () => ctx.api.GET("/api/v1/ops/reviews", { params: { query: { visibility, limit: 50 } } }), visibility);
  return (
    <section className="stack" aria-labelledby="review-queue-heading">
      <h2 id="review-queue-heading">{t.ops.customerReviews}</h2>
      <div role="group" aria-label={t.ops.customerReviews} className="segmented">
        {(["VISIBLE", "HIDDEN"] as const).map((v) => (
          <button key={v} type="button" aria-pressed={visibility === v} onClick={() => setVisibility(v)}>
            {v === "VISIBLE" ? t.ops.reportedReviews : t.ops.hidden}
          </button>
        ))}
      </div>
      <Status ctx={ctx} state={state} onRetry={reload}>
        {(page) =>
          page.items.length === 0 ? (
            <p className="muted">{t.ops.noQueuedReviews}</p>
          ) : (
            <ul className="cards">
              {page.items.map((r) => (
                <li key={r.id}>
                  <ReviewCard ctx={ctx} review={r} refresh={reload} />
                </li>
              ))}
            </ul>
          )
        }
      </Status>
    </section>
  );
}

function ReviewCard({ ctx, review, refresh }: { ctx: Ctx; review: QueuedReview; refresh: () => void }) {
  const { t } = ctx;
  const [reason, setReason] = useState("");
  const { run, error, busy } = useMutation(ctx, refresh);
  const hidden = review.visibility === "HIDDEN";
  return (
    <article className="card" aria-label={`${review.rating}/5 ${review.text ?? ""}`.trim()}>
      <header className="card-head">
        <h3>{"★".repeat(review.rating)}</h3>
        <span className="badge">{t.ops.reportCount(review.reportCount)}</span>
        <span className={hidden ? "badge badge-suspended" : "badge badge-active"}>{hidden ? t.ops.hidden : t.ops.visible}</span>
      </header>
      <p>{review.text ?? "—"}</p>
      {review.reportReasons.length > 0 ? <p className="muted">{review.reportReasons.join(" · ")}</p> : null}
      {hidden && review.moderationReason ? (
        <p className="muted">
          {t.reason}: {review.moderationReason}
        </p>
      ) : null}
      <label>
        {t.reason} <input maxLength={1000} value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <button
        type="button"
        className={hidden ? undefined : "danger"}
        disabled={busy || reason.trim().length < 3}
        onClick={() =>
          void run(() =>
            ctx.api.POST("/api/v1/ops/reviews/{reviewId}/moderation", {
              params: { path: { reviewId: review.id } },
              body: { visibility: hidden ? "VISIBLE" : "HIDDEN", reason: reason.trim(), expectedVersion: review.version },
            }),
          )
        }
      >
        {hidden ? t.ops.restore : t.ops.hide}
      </button>
      <ErrorLine message={error} />
    </article>
  );
}

function ModerationCard({ ctx, item, refresh }: { ctx: Ctx; item: Item; refresh: () => void }) {
  const { t, locale } = ctx;
  const [reason, setReason] = useState("");
  const { run, error, busy } = useMutation(ctx, refresh);
  const hidden = item.moderationState === "HIDDEN";
  const title = (locale === "en" ? item.titleEn : null) ?? item.titleAr ?? item.id;
  return (
    <article className="card" aria-label={title}>
      <header className="card-head">
        <h3>{title}</h3>
        <span className="badge">{t.mealStatus[item.status]}</span>
        <span className={hidden ? "badge badge-suspended" : "badge badge-active"}>{hidden ? t.ops.hidden : t.ops.visible}</span>
      </header>
      <p>
        {item.kitchenName} · {item.priceMinor != null ? formatMoney(item.priceMinor, locale) : "—"}
      </p>
      {hidden && item.moderationReason ? (
        <p className="muted">
          {t.reason}: {item.moderationReason}
        </p>
      ) : null}
      <label>
        {t.reason} <input maxLength={1000} value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <button
        type="button"
        className={hidden ? undefined : "danger"}
        disabled={busy || reason.trim().length < 3}
        onClick={() =>
          void run(() =>
            ctx.api.POST("/api/v1/ops/meals/{mealId}/moderation", {
              params: { path: { mealId: item.id } },
              body: { action: hidden ? "RESTORE" : "HIDE", reason: reason.trim(), expectedVersion: item.version },
            }),
          )
        }
      >
        {hidden ? t.ops.restore : t.ops.hide}
      </button>
      <ErrorLine message={error} />
    </article>
  );
}
