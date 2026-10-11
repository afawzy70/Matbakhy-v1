import { useState } from "react";
import { formatTime, newIdempotencyKey, type Schemas } from "../api";
import { ErrorLine, Status, useMutation, useServer, type Ctx } from "../cook/shared";

type StaffCase = Schemas["StaffSupportCase"];
const RESOLUTIONS = ["INFORMATION_PROVIDED", "ORDER_CANCELLED", "CANCELLATION_DECLINED", "REFUND_REQUESTED", "KITCHEN_FOLLOW_UP", "DUPLICATE", "NO_ACTION", "OTHER"] as const;

export function Cases({ ctx }: { ctx: Ctx }) {
  const { t, locale } = ctx;
  const [filter, setFilter] = useState<"ACTIVE" | "OVERDUE" | "MINE" | "RESOLVED">("ACTIVE");
  const [selected, setSelected] = useState<string | null>(null);
  const [queue, reload] = useServer<Schemas["StaffSupportCasePage"]>(
    ctx,
    () =>
      ctx.api.GET("/api/v1/ops/cases", {
        params: {
          query: {
            limit: 50,
            ...(filter === "RESOLVED" ? { state: "RESOLVED" as const } : { state: "ACTIVE" as const }),
            ...(filter === "OVERDUE" ? { overdue: true } : {}),
            ...(filter === "MINE" ? { mine: true } : {}),
          },
        },
      }),
    filter,
  );
  return (
    <div className="stack">
      <div role="group" aria-label={t.ops.cases} className="segmented">
        {(["ACTIVE", "OVERDUE", "MINE", "RESOLVED"] as const).map((f) => (
          <button key={f} type="button" aria-pressed={filter === f} onClick={() => setFilter(f)}>
            {f === "ACTIVE" ? t.ops.active : f === "OVERDUE" ? t.ops.overdue : f === "MINE" ? t.ops.mine : t.ops.caseState.RESOLVED}
          </button>
        ))}
      </div>
      <Status ctx={ctx} state={queue} onRetry={reload}>
        {(page) =>
          page.items.length === 0 ? (
            <p className="muted">{t.ops.noItems}</p>
          ) : (
            <ul className="rows">
              {page.items.map((c) => (
                <li key={c.id}>
                  <span className={c.severity === "CRITICAL" ? "badge badge-suspended" : "badge"}>{t.ops.severity[c.severity]}</span>{" "}
                  <button type="button" className="link" onClick={() => setSelected(c.id)}>
                    {c.reference}
                  </button>{" "}
                  · {t.ops.category[c.category]} · {c.orderNumber} · {t.ops.caseState[c.state]} ·{" "}
                  <span className={c.overdue ? "deadline urgent" : "deadline"}>
                    {t.ops.slaDue} {formatTime(c.slaDueAt, locale)}
                    {c.overdue ? ` (${t.ops.overdue})` : ""}
                  </span>
                </li>
              ))}
            </ul>
          )
        }
      </Status>
      {selected ? <CaseDetail key={selected} ctx={ctx} id={selected} onChanged={reload} /> : null}
    </div>
  );
}

function CaseDetail({ ctx, id, onChanged }: { ctx: Ctx; id: string; onChanged: () => void }) {
  const { t, locale } = ctx;
  const [detail, reload] = useServer<StaffCase>(ctx, () => ctx.api.GET("/api/v1/ops/cases/{caseId}", { params: { path: { caseId: id } } }), id);
  const refresh = () => {
    reload();
    onChanged();
  };
  return (
    <Status ctx={ctx} state={detail} onRetry={reload}>
      {(c) => (
        <section className="card" aria-labelledby="case-heading">
          <h2 id="case-heading">
            {c.reference} · {t.ops.category[c.category]} · {t.ops.caseState[c.state]}
          </h2>
          <p>
            {t.ops.severity[c.severity]} · {c.orderNumber} · {t.ops.slaDue} {formatTime(c.slaDueAt, locale)}
          </p>
          <p>{c.description}</p>
          <ol className="rows" aria-label="messages">
            {c.messages.map((m) => (
              <li key={m.id} className={m.visibility === "STAFF_ONLY" ? "notice" : undefined}>
                <small className="muted">
                  {m.authorRole} · {m.visibility === "STAFF_ONLY" ? t.ops.staffOnly : t.ops.public} · {formatTime(m.createdAt, locale)}
                </small>
                <br />
                {m.body}
              </li>
            ))}
          </ol>
          {c.state !== "CLOSED" ? <Reply ctx={ctx} id={c.id} refresh={refresh} /> : null}
          {c.state !== "CLOSED" ? <CaseUpdate ctx={ctx} c={c} refresh={refresh} /> : null}
        </section>
      )}
    </Status>
  );
}

function Reply({ ctx, id, refresh }: { ctx: Ctx; id: string; refresh: () => void }) {
  const { t } = ctx;
  const [body, setBody] = useState("");
  const [visibility, setVisibility] = useState<"PUBLIC" | "STAFF_ONLY">("STAFF_ONLY");
  const { run, error, busy } = useMutation(ctx, refresh);
  return (
    <form
      className="form"
      onSubmit={(e) => {
        e.preventDefault();
        void run(() =>
          ctx.api.POST("/api/v1/ops/cases/{caseId}/messages", {
            params: { path: { caseId: id }, header: { "Idempotency-Key": newIdempotencyKey() } },
            body: { body: body.trim(), visibility },
          }),
        ).then((ok) => ok && setBody(""));
      }}
    >
      <label>
        {visibility === "STAFF_ONLY" ? t.ops.staffOnly : t.ops.public} <textarea maxLength={4000} value={body} onChange={(e) => setBody(e.target.value)} />
      </label>
      <div role="group" className="segmented" aria-label={t.ops.staffOnly}>
        {(["STAFF_ONLY", "PUBLIC"] as const).map((v) => (
          <button key={v} type="button" aria-pressed={visibility === v} onClick={() => setVisibility(v)}>
            {v === "STAFF_ONLY" ? t.ops.staffOnly : t.ops.public}
          </button>
        ))}
      </div>
      <button type="submit" disabled={busy || !body.trim()}>
        {t.ops.send}
      </button>
      <ErrorLine message={error} />
    </form>
  );
}

function CaseUpdate({ ctx, c, refresh }: { ctx: Ctx; c: StaffCase; refresh: () => void }) {
  const { t } = ctx;
  const [state, setState] = useState<StaffCase["state"]>(c.state === "RESOLVED" ? "CLOSED" : "RESOLVED");
  const [resolution, setResolution] = useState<(typeof RESOLUTIONS)[number]>("INFORMATION_PROVIDED");
  const [reason, setReason] = useState("");
  const { run, error, busy } = useMutation(ctx, refresh);
  const send = (body: Partial<Schemas["PatchOpsCasesByIdRequest"]>) =>
    run(() => ctx.api.PATCH("/api/v1/ops/cases/{caseId}", { params: { path: { caseId: c.id } }, body: { ...body, expectedVersion: c.version } }));
  return (
    <div className="form">
      <button type="button" className="secondary" disabled={busy} onClick={() => void send({ assignToMe: true })}>
        {t.ops.assignToMe}
      </button>
      <label>
        {t.ops.setState}{" "}
        <select value={state} onChange={(e) => setState(e.target.value as StaffCase["state"])}>
          {(["IN_PROGRESS", "WAITING_ON_CUSTOMER", "RESOLVED", "CLOSED", "OPEN"] as const).map((s) => (
            <option key={s} value={s}>
              {t.ops.caseState[s]}
            </option>
          ))}
        </select>
      </label>
      {state === "RESOLVED" ? (
        <label>
          {t.ops.resolution}{" "}
          <select value={resolution} onChange={(e) => setResolution(e.target.value as (typeof RESOLUTIONS)[number])}>
            {RESOLUTIONS.map((r) => (
              <option key={r} value={r}>
                {t.ops.resolutionCode[r]}
              </option>
            ))}
          </select>
        </label>
      ) : null}
      <label>
        {t.reason} <input maxLength={1000} value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <button
        type="button"
        disabled={busy}
        onClick={() => void send({ state, ...(state === "RESOLVED" ? { resolutionCode: resolution } : {}), ...(reason.trim() ? { reason: reason.trim() } : {}) })}
      >
        {t.save}
      </button>
      <ErrorLine message={error} />
    </div>
  );
}
