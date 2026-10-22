import { useState } from "react";
import { formatMoney, formatTime, type Schemas } from "../api";
import { egpToMinor, ErrorLine, Status, useMutation, useServer, type Ctx } from "../cook/shared";
import { StepUp } from "./Reviews";

type Config = Schemas["ConfigurationVersions"];

/** API-089/090: view and change zones, flags and see the policy; every change needs a reason and MFA step-up. */
export function Configuration({ ctx }: { ctx: Ctx }) {
  const { t, locale } = ctx;
  const [state, reload] = useServer<Config>(ctx, () => ctx.api.GET("/api/v1/ops/configuration"));
  return (
    <Status ctx={ctx} state={state} onRetry={reload}>
      {(c) => (
        <div className="stack">
          <p className="notice">{t.ops.newQuotesOnly}</p>
          {c.commercePolicy ? (
            <section className="card" aria-labelledby="cfg-policy">
              <h2 id="cfg-policy">
                {t.ops.policy} v{c.commercePolicy.version}
              </h2>
              <p>
                {c.commercePolicy.label}
                {c.commercePolicy.synthetic ? ` · ${t.ops.syntheticPolicy}` : ""}
              </p>
              <p className="muted">
                tax {c.commercePolicy.taxRateBps / 100}% ·{" "}
                {c.commercePolicy.commissionRateBps === 0 ? t.ops.noCommission : `commission ${c.commercePolicy.commissionRateBps / 100}%`} · {c.commercePolicy.termsVersion} ·{" "}
                {c.commercePolicy.cancellationPolicyVersion}
              </p>
            </section>
          ) : null}
          {c.flags.map((f) => (
            <FlagRow key={f.key} ctx={ctx} flag={f} refresh={reload} />
          ))}
          <section className="card" aria-labelledby="cfg-zones">
            <h2 id="cfg-zones">{t.ops.zonesTitle}</h2>
            <ul className="cards">
              {c.zones.map((z) => (
                <li key={z.id}>
                  <ZoneRow ctx={ctx} zone={z} refresh={reload} />
                </li>
              ))}
            </ul>
          </section>
          <section className="card" aria-labelledby="cfg-changes">
            <h2 id="cfg-changes">{t.ops.changes}</h2>
            <ul className="rows">
              {c.changes.map((ch) => (
                <li key={ch.id}>
                  {formatTime(ch.createdAt, locale)} · {ch.key} v{ch.version} · {ch.reason}
                  {ch.approvalReference ? ` · ${ch.approvalReference}` : ""}
                </li>
              ))}
            </ul>
          </section>
        </div>
      )}
    </Status>
  );
}

function FlagRow({ ctx, flag, refresh }: { ctx: Ctx; flag: Config["flags"][number]; refresh: () => void }) {
  const { t } = ctx;
  const [reason, setReason] = useState("");
  const { run, error, code, busy } = useMutation(ctx, refresh);
  return (
    <section className="card" aria-label={flag.key}>
      <h2>
        {flag.key === "ordering-enabled" ? t.ops.orderingEnabled : flag.key}: {flag.enabled ? "✓" : "✗"}
      </h2>
      <label>
        {t.ops.changeReason} <input maxLength={1000} value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <button
        type="button"
        className={flag.enabled ? "danger" : undefined}
        disabled={busy || reason.trim().length < 3}
        onClick={() =>
          void run(() =>
            ctx.api.POST("/api/v1/ops/configuration/{key}/versions", {
              params: { path: { key: `flag:${flag.key}` } },
              body: { value: { enabled: !flag.enabled }, reason: reason.trim(), expectedVersion: flag.version },
            }),
          )
        }
      >
        {flag.enabled ? t.pause : t.open}
      </button>
      <StepUp ctx={ctx} code={code} />
      <ErrorLine message={code === "STEP_UP_REQUIRED" ? null : error} />
    </section>
  );
}

function ZoneRow({ ctx, zone, refresh }: { ctx: Ctx; zone: Config["zones"][number]; refresh: () => void }) {
  const { t, locale } = ctx;
  const [fee, setFee] = useState((zone.deliveryFeeMinor / 100).toFixed(2));
  const [active, setActive] = useState(zone.active);
  const [reason, setReason] = useState("");
  const { run, error, code, busy } = useMutation(ctx, refresh);
  const minor = egpToMinor(fee);
  return (
    <article className="card" aria-label={zone.code}>
      <h3>
        {locale === "ar" ? zone.nameAr : zone.nameEn} · {formatMoney(zone.deliveryFeeMinor, locale)}
      </h3>
      <label>
        {t.ops.feeEgp} <input inputMode="decimal" value={fee} onChange={(e) => setFee(e.target.value)} aria-invalid={minor === null} />
      </label>
      <label className="check">
        <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} /> {t.ops.zoneActive}
      </label>
      <label>
        {t.ops.changeReason} <input maxLength={1000} value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <button
        type="button"
        disabled={busy || minor === null || reason.trim().length < 3}
        onClick={() =>
          void run(() =>
            ctx.api.POST("/api/v1/ops/configuration/{key}/versions", {
              params: { path: { key: `zone:${zone.id}` } },
              body: { value: { active, deliveryFeeMinor: minor ?? 0 }, reason: reason.trim(), expectedVersion: zone.version },
            }),
          )
        }
      >
        {t.save}
      </button>
      <StepUp ctx={ctx} code={code} />
      <ErrorLine message={code === "STEP_UP_REQUIRED" ? null : error} />
    </article>
  );
}
