import { useState } from "react";
import { formatMoney, formatTime, newIdempotencyKey, type Schemas } from "./api";
import { ErrorLine, Status, useMutation, useServer, type Ctx } from "./shared";

const CASE_CATEGORIES = ["LATE", "MISSING_ITEM", "QUALITY", "FOOD_SAFETY", "CASH", "HANDOVER", "OTHER"] as const;

/** The customer's own data: profile, addresses, favorites, inbox, support cases and the deletion request. */
export function Account({ ctx, me, onMeChanged }: { ctx: Ctx; me: Schemas["CustomerProfile"]; onMeChanged: () => void }) {
  return (
    <div className="transaction stack">
      <Profile ctx={ctx} me={me} onMeChanged={onMeChanged} />
      <Favorites ctx={ctx} />
      <Inbox ctx={ctx} />
      <Support ctx={ctx} />
      <Addresses ctx={ctx} />
      <Deletion ctx={ctx} onMeChanged={onMeChanged} />
    </div>
  );
}

function Profile({ ctx, me, onMeChanged }: { ctx: Ctx; me: Schemas["CustomerProfile"]; onMeChanged: () => void }) {
  const { t } = ctx;
  const [name, setName] = useState(me.displayName ?? "");
  const [language, setLanguage] = useState<"ar" | "en">(me.locale === "en" ? "en" : "ar");
  const [done, setDone] = useState(false);
  const { run, error, busy } = useMutation(ctx, onMeChanged);
  return (
    <section className="card form" aria-labelledby="profile-heading">
      <h2 id="profile-heading">{t.profile}</h2>
      <label>
        {t.displayName} <input maxLength={120} value={name} onChange={(e) => setName(e.target.value)} />
      </label>
      <label>
        {t.preferredLanguage}{" "}
        <select value={language} onChange={(e) => setLanguage(e.target.value === "en" ? "en" : "ar")}>
          <option value="ar">العربية</option>
          <option value="en">English</option>
        </select>
      </label>
      <button
        type="button"
        disabled={busy || !name.trim()}
        onClick={() =>
          void run(() => ctx.api.PATCH("/api/v1/me", { body: { expectedVersion: me.version, displayName: name.trim(), locale: language } })).then((ok) => setDone(ok))
        }
      >
        {t.save}
      </button>
      {done ? <p role="status">{t.saved}</p> : null}
      <ErrorLine message={error} />
    </section>
  );
}

function Favorites({ ctx }: { ctx: Ctx }) {
  const { t, locale } = ctx;
  const [state, reload] = useServer(ctx, () => ctx.api.GET("/api/v1/me/favorites"), locale);
  const { run, error } = useMutation(ctx, reload);
  return (
    <section className="card" aria-labelledby="fav-heading">
      <h2 id="fav-heading">{t.favorites}</h2>
      <Status ctx={ctx} state={state} onRetry={reload}>
        {(page) => (
          <ul className="rows">
            {page.items.map((f) => (
              <li key={f.mealId}>
                {f.available ? <a href={`#/meal/${f.mealId}`}>{f.title}</a> : <span>{f.title}</span>} · {f.kitchenName}
                {f.priceMinor != null ? ` · ${formatMoney(f.priceMinor, locale)}` : ""}
                {!f.available ? <span className="badge"> {t.unavailable}</span> : null}{" "}
                <button
                  type="button"
                  className="secondary"
                  onClick={() => void run(() => ctx.api.DELETE("/api/v1/me/favorites/{mealId}", { params: { path: { mealId: f.mealId } } }))}
                >
                  {t.remove}
                </button>
              </li>
            ))}
          </ul>
        )}
      </Status>
      <ErrorLine message={error} />
    </section>
  );
}

function Inbox({ ctx }: { ctx: Ctx }) {
  const { t, locale } = ctx;
  const [state, reload] = useServer(ctx, () => ctx.api.GET("/api/v1/notifications", { params: { query: { limit: 20 } } }));
  const { run } = useMutation(ctx, reload);
  return (
    <section className="card" aria-labelledby="inbox-heading">
      <h2 id="inbox-heading">{t.inbox}</h2>
      <Status ctx={ctx} state={state} onRetry={reload}>
        {(page) => (
          <ul className="rows">
            {page.items.map((n) => (
              <li key={n.id} className={n.readAt ? "muted" : undefined}>
                {formatTime(n.createdAt, locale)} · {t.state[n.params.state ?? ""] ?? n.type}
                {n.orderId ? (
                  <>
                    {" "}
                    <a href={`#/orders/${n.orderId}`}>{n.params.orderNumber}</a>
                  </>
                ) : null}
                {!n.readAt ? (
                  <button
                    type="button"
                    className="link"
                    onClick={() => void run(() => ctx.api.PUT("/api/v1/notifications/{notificationId}/read", { params: { path: { notificationId: n.id } } }))}
                  >
                    {" "}
                    {t.markRead}
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </Status>
    </section>
  );
}

function Support({ ctx }: { ctx: Ctx }) {
  const { t } = ctx;
  const [cases, reload] = useServer(ctx, () => ctx.api.GET("/api/v1/support/cases"));
  const [orders] = useServer(ctx, () => ctx.api.GET("/api/v1/orders", { params: { query: { limit: 20 } } }));
  const [orderId, setOrderId] = useState("");
  const [category, setCategory] = useState<(typeof CASE_CATEGORIES)[number]>("QUALITY");
  const [description, setDescription] = useState("");
  const { run, error, busy } = useMutation(ctx, reload);
  return (
    <section className="card form" aria-labelledby="support-heading">
      <h2 id="support-heading">{t.support}</h2>
      <Status ctx={ctx} state={cases} onRetry={reload}>
        {(page) => (
          <ul className="rows">
            {page.items.map((c) => (
              <li key={c.id}>
                <a href={`#/support/${c.id}`}>
                  {c.reference} · {c.orderNumber} · {t.caseCategories[c.category]} · {t.caseStates[c.state] ?? c.state}
                </a>
              </li>
            ))}
          </ul>
        )}
      </Status>
      <h3>{t.openCase}</h3>
      <label>
        {t.caseOrder}{" "}
        <select value={orderId} onChange={(e) => setOrderId(e.target.value)}>
          <option value="">—</option>
          {orders.status === "ok"
            ? orders.data.items.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.publicNumber}
                </option>
              ))
            : null}
        </select>
      </label>
      <label>
        {t.caseCategory}{" "}
        <select value={category} onChange={(e) => setCategory(e.target.value as (typeof CASE_CATEGORIES)[number])}>
          {CASE_CATEGORIES.map((c) => (
            <option key={c} value={c}>
              {t.caseCategories[c]}
            </option>
          ))}
        </select>
      </label>
      <label>
        {t.caseDescription} <textarea maxLength={4000} value={description} onChange={(e) => setDescription(e.target.value)} />
      </label>
      <button
        type="button"
        disabled={busy || !orderId || description.trim().length < 10}
        onClick={() =>
          void run(() => ctx.api.POST("/api/v1/support/cases", { body: { orderId, category, description: description.trim() } })).then(
            (ok) => ok && setDescription(""),
          )
        }
      >
        {t.openCase}
      </button>
      <ErrorLine message={error} />
    </section>
  );
}

function Addresses({ ctx }: { ctx: Ctx }) {
  const { t } = ctx;
  const [state, reload] = useServer(ctx, () => ctx.api.GET("/api/v1/me/addresses"));
  const { run, error } = useMutation(ctx, reload);
  return (
    <section className="card" aria-labelledby="addr-heading">
      <h2 id="addr-heading">{t.addresses}</h2>
      <Status ctx={ctx} state={state} onRetry={reload}>
        {(page) => (
          <ul className="rows">
            {page.items.map((a) => (
              <li key={a.id}>
                {a.label} · {a.street} {a.building}{" "}
                <button
                  type="button"
                  className="secondary"
                  onClick={() => void run(() => ctx.api.DELETE("/api/v1/me/addresses/{addressId}", { params: { path: { addressId: a.id } } }))}
                >
                  {t.remove}
                </button>
              </li>
            ))}
          </ul>
        )}
      </Status>
      <ErrorLine message={error} />
    </section>
  );
}

function Deletion({ ctx, onMeChanged }: { ctx: Ctx; onMeChanged: () => void }) {
  const { t } = ctx;
  const [confirm, setConfirm] = useState(false);
  const [done, setDone] = useState(false);
  const { run, error, busy } = useMutation(ctx, () => undefined);
  return (
    <section className="card form" aria-labelledby="delete-heading">
      <h2 id="delete-heading">{t.deleteAccount}</h2>
      <label className="check">
        <input type="checkbox" checked={confirm} onChange={(e) => setConfirm(e.target.checked)} /> {t.deleteConfirm}
      </label>
      <button
        type="button"
        className="danger"
        disabled={busy || !confirm || done}
        onClick={() =>
          void run(() => ctx.api.POST("/api/v1/me/deletion-requests", { body: { confirm: true, policyVersion: "retention-policy-draft" } })).then((ok) => {
            if (ok) {
              setDone(true);
              onMeChanged();
            }
          })
        }
      >
        {t.deleteAccount}
      </button>
      {done ? <p role="status">{t.deletionRequested}</p> : null}
      <ErrorLine message={error} />
    </section>
  );
}

/**
 * MBQA-004: one own support case with its conversation (CW16). The server returns PUBLIC messages only (STAFF_ONLY notes
 * never reach the customer) and answers 404 for another customer's case; the page shows them oldest first, attributed
 * to the customer or the support team, and lets the customer reply while the case is not closed.
 */
export function SupportCaseDetail({ ctx, id }: { ctx: Ctx; id: string }) {
  const { t, locale } = ctx;
  const [state, reload] = useServer(ctx, () => ctx.api.GET("/api/v1/support/cases/{caseId}", { params: { path: { caseId: id } } }), `${id}|${locale}`);
  const [reply, setReply] = useState("");
  const [key, setKey] = useState(newIdempotencyKey);
  const { run, error, busy } = useMutation(ctx, reload);
  return (
    <section className="transaction stack" aria-labelledby="case-heading">
      <p>
        <a href="#/account">{t.backToAccount}</a>
      </p>
      <Status ctx={ctx} state={state} onRetry={reload}>
        {(c) => {
          // The server sends PUBLIC messages only; the filter keeps a staff-only note off the screen even if one slipped through.
          const messages = c.messages.filter((m) => m.visibility === "PUBLIC").sort((a, b) => a.createdAt.localeCompare(b.createdAt));
          const closed = c.state === "CLOSED";
          return (
            <div className="stack">
              <div className="card">
                <h2 id="case-heading">
                  {c.reference} · {t.caseCategories[c.category] ?? c.category}
                </h2>
                <p>
                  <span className="badge">{t.caseStates[c.state] ?? c.state}</span>{" "}
                  <a href={`#/orders/${c.orderId}`}>{c.orderNumber ?? t.caseOrder}</a>
                </p>
                <p>{c.description}</p>
              </div>
              <section className="card" aria-labelledby="conversation-heading">
                <div className="card-head">
                  <h3 id="conversation-heading">{t.conversation}</h3>
                  <button type="button" className="secondary" onClick={reload}>
                    {t.refresh}
                  </button>
                </div>
                {messages.length === 0 ? (
                  <p>{t.noReplies}</p>
                ) : (
                  <ol className="rows" aria-label={t.conversation}>
                    {messages.map((m) => (
                      <li key={m.id}>
                        <p>
                          <strong>{m.authorRole === "CUSTOMER" ? t.fromYou : t.fromSupport}</strong> ·{" "}
                          <time dateTime={m.createdAt}>{formatTime(m.createdAt, locale)}</time>
                        </p>
                        <p>{m.body}</p>
                      </li>
                    ))}
                  </ol>
                )}
              </section>
              {closed ? (
                <p className="notice">{t.caseClosedNoReply}</p>
              ) : (
                <div className="card form">
                  <label>
                    {t.yourReply} <textarea maxLength={4000} value={reply} onChange={(e) => setReply(e.target.value)} />
                  </label>
                  <button
                    type="button"
                    disabled={busy || reply.trim().length === 0}
                    onClick={() =>
                      void run(() =>
                        ctx.api.POST("/api/v1/support/cases/{caseId}/messages", {
                          params: { path: { caseId: id }, header: { "Idempotency-Key": key } },
                          body: { body: reply.trim() },
                        }),
                      ).then((ok) => {
                        if (ok) {
                          setReply("");
                          setKey(newIdempotencyKey());
                        }
                      })
                    }
                  >
                    {t.sendReply}
                  </button>
                  <ErrorLine message={error} />
                </div>
              )}
            </div>
          );
        }}
      </Status>
    </section>
  );
}
