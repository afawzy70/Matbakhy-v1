import { useState } from "react";
import { formatTime, type Schemas } from "../api";
import { ErrorLine, Status, useMutation, useServer, type Ctx } from "./shared";

type Slot = Schemas["CookSlot"];

export function Slots({ ctx }: { ctx: Ctx }) {
  const [state, reload] = useServer<Schemas["CookSlotPage"]>(ctx, () => ctx.api.GET("/api/v1/cook/slots", { params: { query: { limit: 100 } } }));
  const [meals] = useServer<Schemas["CookMealPage"]>(
    ctx,
    () => ctx.api.GET("/api/v1/cook/meals", { params: { query: { status: "PUBLISHED", limit: 100 } } }),
  );
  const { t } = ctx;
  return (
    <div className="stack">
      <NewSlot ctx={ctx} refresh={reload} meals={meals.status === "ok" ? meals.data.items : []} />
      <section aria-labelledby="slots-list-heading">
        <h2 id="slots-list-heading">{t.slots}</h2>
        <Status ctx={ctx} state={state} onRetry={reload}>
          {(page) => (
            <ul className="cards">
              {page.items.map((s) => (
                <li key={s.id}>
                  <SlotCard ctx={ctx} slot={s} refresh={reload} />
                </li>
              ))}
            </ul>
          )}
        </Status>
      </section>
    </div>
  );
}

/**
 * Actions offered per slot state: the transitions the server allows (DRAFT→OPEN, OPEN→PAUSED/CLOSED, PAUSED→OPEN/CLOSED;
 * SlotService stays authoritative and refuses anything else). A draft is never closed directly (MBQA-005).
 */
const SLOT_ACTIONS: Record<string, ("OPEN" | "PAUSED" | "CLOSED")[]> = {
  DRAFT: ["OPEN"],
  OPEN: ["PAUSED", "CLOSED"],
  PAUSED: ["OPEN", "CLOSED"],
};

function SlotCard({ ctx, slot, refresh }: { ctx: Ctx; slot: Slot; refresh: () => unknown }) {
  const { t, locale } = ctx;
  const { run, error, busy } = useMutation(ctx, refresh);
  const next = SLOT_ACTIONS[slot.state] ?? [];
  const label = { OPEN: t.open, PAUSED: t.pause, CLOSED: t.close };
  return (
    <article className="card" aria-label={formatTime(slot.startsAt, locale)}>
      <header className="card-head">
        <h3>
          {formatTime(slot.startsAt, locale)} – {formatTime(slot.endsAt, locale)}
        </h3>
        <span className="badge">{t.slotState[slot.state]}</span>
      </header>
      <p>{t.portions(slot.reserved + slot.allocated + slot.consumed, slot.maxPortions)}</p>
      <div className="actions">
        {next.map((s) => (
          <button
            key={s}
            type="button"
            disabled={busy}
            onClick={() =>
              void run(() =>
                ctx.api.PATCH("/api/v1/cook/slots/{slotId}", { params: { path: { slotId: slot.id } }, body: { state: s, expectedVersion: slot.version } }),
              )
            }
          >
            {label[s]}
          </button>
        ))}
      </div>
      <ErrorLine message={error} />
    </article>
  );
}

function NewSlot({ ctx, refresh, meals }: { ctx: Ctx; refresh: () => unknown; meals: Schemas["CookMeal"][] }) {
  const { t } = ctx;
  const [date, setDate] = useState("");
  const [start, setStart] = useState("13:00");
  const [end, setEnd] = useState("15:00");
  const [max, setMax] = useState("10");
  const [lead, setLead] = useState("120");
  const [capacity, setCapacity] = useState<Record<string, string>>({});
  const { run, error, busy } = useMutation(ctx, refresh);
  return (
    <form
      className="card form"
      aria-labelledby="new-slot-heading"
      onSubmit={(e) => {
        e.preventDefault();
        void run(() =>
          ctx.api.POST("/api/v1/cook/slots", {
            body: {
              localDate: date,
              localStart: start,
              localEnd: end,
              timezone: "Africa/Cairo",
              maxPortions: Number(max),
              leadMinutes: Number(lead),
              mealCapacities: Object.entries(capacity)
                .filter(([, v]) => Number(v) > 0)
                .map(([mealId, v]) => ({ mealId, capacity: Number(v) })),
            },
          }),
        );
      }}
    >
      <h2 id="new-slot-heading">{t.newSlot}</h2>
      <label>
        {t.date} <input type="date" required value={date} onChange={(e) => setDate(e.target.value)} />
      </label>
      <label>
        {t.start} <input type="time" required value={start} onChange={(e) => setStart(e.target.value)} />
      </label>
      <label>
        {t.end} <input type="time" required value={end} onChange={(e) => setEnd(e.target.value)} />
      </label>
      <label>
        {t.maxPortions} <input type="number" min={1} max={500} required value={max} onChange={(e) => setMax(e.target.value)} />
      </label>
      <label>
        {t.leadMinutes} <input type="number" min={30} max={1440} required value={lead} onChange={(e) => setLead(e.target.value)} />
      </label>
      {meals.length > 0 ? (
        <fieldset>
          <legend>{t.mealCapacity}</legend>
          {meals.map((m) => (
            <label key={m.id}>
              {m.content.titleAr}{" "}
              <input type="number" min={0} max={500} value={capacity[m.id] ?? ""} onChange={(e) => setCapacity({ ...capacity, [m.id]: e.target.value })} />
            </label>
          ))}
        </fieldset>
      ) : null}
      <button type="submit" disabled={busy}>
        {t.create}
      </button>
      <ErrorLine message={error} />
    </form>
  );
}
