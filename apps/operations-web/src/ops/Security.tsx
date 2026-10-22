import { useState } from "react";
import { stepUpUrl, type Schemas } from "../api";
import { ErrorLine, Status, useMutation, useServer, type Ctx } from "../cook/shared";

const CATEGORIES = ["CUSTOMER", "KITCHEN", "STAFF"] as const;
const MODES = ["DISABLED", "OPTIONAL", "REQUIRED"] as const;

function StepUpLine({ ctx }: { ctx: Ctx }) {
  return (
    <p className="notice">
      {ctx.t.ops.stepUp}{" "}
      <a className="button" href={stepUpUrl(window.location.href)}>
        {ctx.t.ops.stepUpLink}
      </a>
    </p>
  );
}

/** Own security state (D18) plus, for those holding the permissions, the policy per category and the audited reset. */
export function SecurityTab({ ctx, policyAdmin, resetAllowed }: { ctx: Ctx; policyAdmin: boolean; resetAllowed: boolean }) {
  return (
    <div className="stack">
      <Own ctx={ctx} />
      {policyAdmin ? <PolicyAdmin ctx={ctx} /> : null}
      {resetAllowed ? <Reset ctx={ctx} /> : null}
    </div>
  );
}

function Own({ ctx }: { ctx: Ctx }) {
  const { t } = ctx;
  const [state, reload] = useServer<Schemas["SecuritySettings"]>(ctx, () => ctx.api.GET("/api/v1/me/security"), ctx.locale);
  return (
    <section className="card" aria-labelledby="own-security-heading">
      <h2 id="own-security-heading">{t.sec.title}</h2>
      <Status ctx={ctx} state={state} onRetry={reload}>
        {(s) => <OwnBody ctx={ctx} settings={s} reload={reload} />}
      </Status>
    </section>
  );
}

function OwnBody({ ctx, settings, reload }: { ctx: Ctx; settings: Schemas["SecuritySettings"]; reload: () => void }) {
  const { t } = ctx;
  const { run, error, code, busy } = useMutation(ctx, reload);
  const step = stepUpUrl(window.location.href);
  const set = (enabled: boolean) => void run(() => ctx.api.PUT("/api/v1/me/security/mfa", { body: { enabled } }));
  if (!settings.available) {
    return <p role="status">{t.sec.unavailable}</p>;
  }
  return (
    <div className="stack">
      <p>
        {t.sec.policy}: <strong>{t.sec.mode[settings.effectiveMode] ?? settings.effectiveMode}</strong>
      </p>
      <p>
        {t.sec.status}: <strong>{settings.required ? t.sec.on : t.sec.off}</strong> · {settings.verifiedInSession ? t.sec.verified : t.sec.notVerified}
      </p>
      {settings.effectiveMode === "REQUIRED" ? <p role="status">{t.sec.requiredNotice}</p> : null}
      {settings.required && !settings.verifiedInSession ? (
        <p className="notice">
          {t.sec.verifyHint}{" "}
          <a className="button" href={step}>
            {t.sec.verifyNow}
          </a>
        </p>
      ) : null}
      {settings.effectiveMode === "OPTIONAL" && !settings.enabled ? (
        settings.verifiedInSession ? (
          <button type="button" disabled={busy} onClick={() => set(true)}>
            {t.sec.turnOn}
          </button>
        ) : (
          <p>
            {t.sec.verifyHint}{" "}
            <a className="button" href={step}>
              {t.sec.turnOn}
            </a>
          </p>
        )
      ) : null}
      {settings.effectiveMode === "OPTIONAL" && settings.enabled ? (
        <p>
          <button type="button" className="secondary" disabled={busy} onClick={() => set(false)}>
            {t.sec.turnOff}
          </button>{" "}
          <span className="muted">{t.sec.turnOffHint}</span>
        </p>
      ) : null}
      {code === "STEP_UP_REQUIRED" ? <StepUpLine ctx={ctx} /> : <ErrorLine message={error} />}
      <p>
        <a href={settings.accountManagementUrl} target="_blank" rel="noopener noreferrer">
          {t.sec.manage}
        </a>
      </p>
    </div>
  );
}

function PolicyAdmin({ ctx }: { ctx: Ctx }) {
  const { t } = ctx;
  const [state, reload] = useServer<Schemas["MfaPolicyList"]>(ctx, () => ctx.api.GET("/api/v1/ops/security/mfa-policy"));
  return (
    <section className="card" aria-labelledby="mfa-policy-heading">
      <h2 id="mfa-policy-heading">{t.sec.policyTitle}</h2>
      <Status ctx={ctx} state={state} onRetry={reload}>
        {(list) => (
          <ul className="rows">
            {CATEGORIES.map((c) => {
              const p = list.items.find((i) => i.category === c);
              return p ? (
                <li key={c}>
                  <PolicyRow ctx={ctx} policy={p} refresh={reload} />
                </li>
              ) : null;
            })}
          </ul>
        )}
      </Status>
    </section>
  );
}

function PolicyRow({ ctx, policy, refresh }: { ctx: Ctx; policy: Schemas["MfaPolicy"]; refresh: () => void }) {
  const { t } = ctx;
  const [mode, setMode] = useState<(typeof MODES)[number]>(policy.mode);
  const [reason, setReason] = useState("");
  const [saved, setSaved] = useState(false);
  const { run, error, code, busy } = useMutation(ctx, refresh);
  return (
    <div role="group" aria-label={t.sec.category[policy.category] ?? policy.category}>
      <strong>{t.sec.category[policy.category] ?? policy.category}</strong>
      <label>
        {t.sec.mode_label}{" "}
        <select value={mode} onChange={(e) => setMode(e.target.value as (typeof MODES)[number])}>
          {MODES.map((m) => (
            <option key={m} value={m}>
              {t.sec.mode[m]}
            </option>
          ))}
        </select>
      </label>
      <label>
        {t.sec.reason} <input maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      {mode === "REQUIRED" && policy.mode !== "REQUIRED" ? <p className="notice">{t.sec.requiredWarning}</p> : null}
      <button
        type="button"
        disabled={busy || mode === policy.mode || reason.trim().length < 3}
        onClick={() =>
          void run(() =>
            ctx.api.PUT("/api/v1/ops/security/mfa-policy/{category}", {
              params: { path: { category: policy.category } },
              body: { mode, expectedVersion: policy.version, reason: reason.trim() },
            }),
          ).then((ok) => {
            setSaved(ok);
            if (ok) {
              setReason("");
            }
          })
        }
      >
        {t.sec.apply}
      </button>
      {saved ? <p role="status">{t.sec.policySaved}</p> : null}
      {code === "STEP_UP_REQUIRED" ? <StepUpLine ctx={ctx} /> : <ErrorLine message={error} />}
    </div>
  );
}

function Reset({ ctx }: { ctx: Ctx }) {
  const { t } = ctx;
  const [accountId, setAccountId] = useState("");
  const [reason, setReason] = useState("");
  const [done, setDone] = useState<string | null>(null);
  const { run, error, code, busy } = useMutation(ctx, () => undefined);
  return (
    <section className="card form" aria-labelledby="mfa-reset-heading">
      <h2 id="mfa-reset-heading">{t.sec.resetTitle}</h2>
      <p className="muted">{t.sec.resetNote}</p>
      <label>
        {t.sec.accountId} <input value={accountId} onChange={(e) => setAccountId(e.target.value.trim())} />
      </label>
      <label>
        {t.sec.resetReason} <input maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <button
        type="button"
        className="danger"
        disabled={busy || !/^[0-9a-fA-F-]{36}$/.test(accountId) || reason.trim().length < 10}
        onClick={() => {
          setDone(null);
          void run(async () => {
            const r = await ctx.api.POST("/api/v1/ops/accounts/{accountId}/mfa-reset", {
              params: { path: { accountId } },
              body: { reason: reason.trim() },
            });
            if (r.data) {
              setDone(t.sec.resetDone(r.data.removedFactors));
            }
            return r;
          });
        }}
      >
        {t.sec.reset}
      </button>
      {done ? <p role="status">{done}</p> : null}
      {code === "STEP_UP_REQUIRED" ? <StepUpLine ctx={ctx} /> : <ErrorLine message={error} />}
    </section>
  );
}
