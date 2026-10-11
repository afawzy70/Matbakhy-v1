import { useState } from "react";
import type { Schemas } from "../api";
import { ErrorLine, Status, useMutation, useServer, type Ctx } from "./shared";

type Settings = Schemas["CookSettings"];

export function SettingsPanel({ ctx }: { ctx: Ctx }) {
  const [state, reload] = useServer<Schemas["CookDashboard"]>(ctx, () => ctx.api.GET("/api/v1/cook/dashboard"));
  return (
    <Status ctx={ctx} state={state} onRetry={reload}>
      {(d) => <SettingsForm key={d.kitchen.version} ctx={ctx} settings={d.kitchen} refresh={reload} />}
    </Status>
  );
}

function SettingsForm({ ctx, settings, refresh }: { ctx: Ctx; settings: Settings; refresh: () => unknown }) {
  const { t, locale } = ctx;
  const [description, setDescription] = useState(settings.description);
  const [pickup, setPickup] = useState(settings.pickupEnabled);
  const [zones, setZones] = useState<Record<string, boolean>>(Object.fromEntries(settings.zones.map((z) => [z.zoneId, z.deliveryEnabled])));
  const { run, error, busy } = useMutation(ctx, refresh);
  const save = (body: Partial<Schemas["PatchCookSettingsRequest"]>) =>
    run(() => ctx.api.PATCH("/api/v1/cook/settings", { body: { ...body, expectedVersion: settings.version } }));
  return (
    <div className="stack">
      <section className="card" aria-labelledby="state-heading">
        <h2 id="state-heading">{t.kitchenState[settings.operatingState]}</h2>
        {settings.operatingState === "ACTIVE" ? (
          <button type="button" className="danger" disabled={busy} onClick={() => void save({ paused: true })}>
            {t.pauseKitchen}
          </button>
        ) : null}
        {settings.operatingState === "PAUSED" && settings.pausedBy === "COOK" ? (
          <button type="button" disabled={busy} onClick={() => void save({ paused: false })}>
            {t.resumeKitchen}
          </button>
        ) : null}
        {settings.pausedBy === "STAFF" ? <p className="muted">{t.pausedByStaff}</p> : null}
      </section>
      <form
        className="card form"
        aria-labelledby="settings-heading"
        onSubmit={(e) => {
          e.preventDefault();
          void save({
            description,
            pickupEnabled: pickup,
            deliveryZones: Object.entries(zones).map(([zoneId, deliveryEnabled]) => ({ zoneId, deliveryEnabled })),
          });
        }}
      >
        <h2 id="settings-heading">{t.settings}</h2>
        <label>
          {t.description} <textarea maxLength={2000} value={description} onChange={(e) => setDescription(e.target.value)} />
        </label>
        <label className="check">
          <input type="checkbox" checked={pickup} onChange={(e) => setPickup(e.target.checked)} /> {t.pickup}
        </label>
        <fieldset>
          <legend>{t.deliveryZones}</legend>
          {settings.zones.map((z) => (
            <label key={z.zoneId} className="check">
              <input type="checkbox" checked={zones[z.zoneId] ?? false} onChange={(e) => setZones({ ...zones, [z.zoneId]: e.target.checked })} />{" "}
              {locale === "ar" ? z.nameAr : z.nameEn}
            </label>
          ))}
        </fieldset>
        <button type="submit" disabled={busy}>
          {t.save}
        </button>
        <ErrorLine message={error} />
      </form>
    </div>
  );
}
