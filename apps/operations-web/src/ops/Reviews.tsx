import { useState } from "react";
import { loginUrl, newIdempotencyKey, type Schemas } from "../api";
import { ErrorLine, Status, useMutation, useServer, type Ctx } from "../cook/shared";

type Review = Schemas["ApplicationReview"];
const STATES = ["SUBMITTED", "UNDER_REVIEW", "CHANGES_REQUESTED", "APPROVED", "REJECTED"] as const;

/** Shown when the API asks for a fresh MFA login before a sensitive staff action. */
export function StepUp({ ctx, code }: { ctx: Ctx; code: string | null }) {
  if (code !== "STEP_UP_REQUIRED") {
    return null;
  }
  const href = loginUrl(window.location.href).replace("?returnTo=", "?stepUp=true&returnTo=");
  return (
    <p className="notice">
      {ctx.t.ops.stepUp}{" "}
      <a className="button" href={href}>
        {ctx.t.ops.stepUpLink}
      </a>
    </p>
  );
}

export function Reviews({ ctx }: { ctx: Ctx }) {
  const { t } = ctx;
  const [state, setState] = useState<(typeof STATES)[number]>("SUBMITTED");
  const [selected, setSelected] = useState<string | null>(null);
  const [list, reloadList] = useServer<Schemas["ApplicationPage"]>(
    ctx,
    () => ctx.api.GET("/api/v1/ops/cooks", { params: { query: { state, limit: 50 } } }),
    state,
  );
  return (
    <div className="stack">
      <div role="group" aria-label={t.ops.reviews} className="segmented">
        {STATES.map((s) => (
          <button key={s} type="button" aria-pressed={state === s} onClick={() => setState(s)}>
            {t.ops.appState[s]}
          </button>
        ))}
      </div>
      <Status ctx={ctx} state={list} onRetry={reloadList}>
        {(page) =>
          page.items.length === 0 ? (
            <p className="muted">{t.ops.noItems}</p>
          ) : (
            <ul className="rows">
              {page.items.map((a) => (
                <li key={a.id}>
                  <button type="button" className="link" onClick={() => setSelected(a.id)}>
                    {a.publicName ?? a.id}
                  </button>{" "}
                  · {t.ops.appState[a.state]} · {t.ops.revision(a.currentRevision)}
                </li>
              ))}
            </ul>
          )
        }
      </Status>
      {selected ? <ReviewDetail key={selected} ctx={ctx} id={selected} onChanged={reloadList} /> : null}
    </div>
  );
}

function ReviewDetail({ ctx, id, onChanged }: { ctx: Ctx; id: string; onChanged: () => void }) {
  const { t } = ctx;
  const [detail, reload] = useServer<Review>(ctx, () => ctx.api.GET("/api/v1/ops/cooks/{applicationId}", { params: { path: { applicationId: id } } }), id);
  const refresh = () => {
    reload();
    onChanged();
  };
  return (
    <Status ctx={ctx} state={detail} onRetry={reload}>
      {(r) => {
        const rev = r.revisions.find((x) => x.revision === r.currentRevision) ?? r.revisions[r.revisions.length - 1];
        return (
          <section className="card" aria-labelledby="review-heading">
            <h2 id="review-heading">
              {rev?.publicName ?? r.id} · {t.ops.appState[r.state]}
            </h2>
            {rev ? (
              <dl className="summary">
                <dt>{t.ops.bio}</dt>
                <dd>{rev.bio}</dd>
                <dt>{t.ops.phone}</dt>
                <dd>
                  <bdi dir="ltr">{rev.contactPhone}</bdi>
                </dd>
                <dt>{t.ops.location}</dt>
                <dd>{rev.privateLocation}</dd>
                <dt>{t.pickup}</dt>
                <dd>{rev.pickupEnabled ? "✓" : "—"}</dd>
                <dt>{t.deliveryZones}</dt>
                <dd>{t.ops.zones(rev.zoneIds.length)}</dd>
              </dl>
            ) : null}
            {rev && rev.documentIds.length > 0 ? <Documents ctx={ctx} ids={rev.documentIds} /> : null}
            <Decision ctx={ctx} review={r} refresh={refresh} />
            {r.kitchen ? <KitchenState ctx={ctx} kitchen={r.kitchen} refresh={refresh} /> : null}
          </section>
        );
      }}
    </Status>
  );
}

/** Private documents are fetched one at a time as short-lived signed reads; each read is audited by the server. */
function Documents({ ctx, ids }: { ctx: Ctx; ids: string[] }) {
  const { t } = ctx;
  const [links, setLinks] = useState<Record<string, string>>({});
  return (
    <div>
      <h3>{t.ops.documents}</h3>
      <ul className="rows">
        {ids.map((docId, i) => (
          <li key={docId}>
            {links[docId] ? (
              <a href={links[docId]} target="_blank" rel="noreferrer noopener">
                {t.ops.documentLink} #{i + 1}
              </a>
            ) : (
              <button
                type="button"
                className="secondary"
                onClick={() =>
                  void ctx.api.GET("/api/v1/uploads/{uploadId}", { params: { path: { uploadId: docId } } }).then((r) => {
                    if (r.data?.privateReadUrl) {
                      setLinks((l) => ({ ...l, [docId]: r.data.privateReadUrl as string }));
                    }
                  })
                }
              >
                {t.ops.viewDocument} #{i + 1}
              </button>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}

function Decision({ ctx, review, refresh }: { ctx: Ctx; review: Review; refresh: () => void }) {
  const { t } = ctx;
  const options: Schemas["PostOpsCooksDecisionRequest"]["decision"][] =
    review.state === "SUBMITTED" ? ["START_REVIEW"] : review.state === "UNDER_REVIEW" ? ["APPROVE", "REQUEST_CHANGES", "REJECT"] : [];
  const [reason, setReason] = useState("");
  const { run, error, code, busy } = useMutation(ctx, refresh);
  if (options.length === 0) {
    return null;
  }
  return (
    <div className="form">
      <label>
        {t.ops.decisionReason} <textarea maxLength={2000} value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <div className="actions">
        {options.map((d) => (
          <button
            key={d}
            type="button"
            className={d === "REJECT" ? "danger" : undefined}
            disabled={busy}
            onClick={() =>
              void run(() =>
                ctx.api.POST("/api/v1/ops/cooks/{applicationId}/decision", {
                  params: { path: { applicationId: review.id }, header: { "Idempotency-Key": newIdempotencyKey() } },
                  body: { decision: d, revision: review.currentRevision, expectedVersion: review.version, ...(reason.trim() ? { reason: reason.trim() } : {}) },
                }),
              )
            }
          >
            {t.ops.decision[d]}
          </button>
        ))}
      </div>
      <StepUp ctx={ctx} code={code} />
      <ErrorLine message={code === "STEP_UP_REQUIRED" ? null : error} />
    </div>
  );
}

function KitchenState({ ctx, kitchen, refresh }: { ctx: Ctx; kitchen: Schemas["KitchenAdmin"]; refresh: () => void }) {
  const { t } = ctx;
  const [reason, setReason] = useState("");
  const { run, error, code, busy } = useMutation(ctx, refresh);
  const actions: ("PAUSE" | "SUSPEND" | "RESTORE")[] =
    kitchen.operatingState === "ACTIVE" ? ["PAUSE", "SUSPEND"] : kitchen.operatingState === "PAUSED" ? ["SUSPEND", "RESTORE"] : kitchen.operatingState === "SUSPENDED" ? ["RESTORE"] : [];
  const impact = Object.entries(kitchen.activeOrders).filter(([, n]) => n > 0);
  return (
    <div className="card" aria-label={t.ops.kitchen}>
      <h3>
        {t.ops.kitchen}: {kitchen.displayName} · {t.kitchenState[kitchen.operatingState]}
      </h3>
      <p>
        {t.ops.impact}: {impact.length === 0 ? "0" : impact.map(([s, n]) => `${t.state[s] ?? s} ${n}`).join(" · ")}
      </p>
      <p className="muted">{t.ops.impactNote}</p>
      <label>
        {t.reason} <input maxLength={2000} value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <div className="actions">
        {actions.map((a) => (
          <button
            key={a}
            type="button"
            className={a === "RESTORE" ? undefined : "danger"}
            disabled={busy || reason.trim().length < 3}
            onClick={() =>
              void run(() =>
                ctx.api.POST("/api/v1/ops/kitchens/{kitchenId}/operating-state", {
                  params: { path: { kitchenId: kitchen.kitchenId } },
                  body: { action: a, reason: reason.trim(), expectedVersion: kitchen.version },
                }),
              )
            }
          >
            {t.ops.kitchenAction[a]}
          </button>
        ))}
      </div>
      <StepUp ctx={ctx} code={code} />
      <ErrorLine message={code === "STEP_UP_REQUIRED" ? null : error} />
    </div>
  );
}
