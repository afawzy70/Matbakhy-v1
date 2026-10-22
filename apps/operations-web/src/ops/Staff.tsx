import { useState } from "react";
import type { Schemas } from "../api";
import { ErrorLine, Status, useMutation, useServer, type Ctx } from "../cook/shared";
import { StepUp } from "./Reviews";

type Member = Schemas["StaffMember"];
const PERMISSIONS: Member["grants"][number]["permission"][] = [
  "cook.review",
  "content.moderate",
  "order.ops",
  "order.exception_cancel",
  "order.handover_override",
  "support.case",
  "finance.read",
  "finance.verify",
  "finance.refund_approve",
  "finance.settlement",
  "config.read",
  "config.write",
  "integration.ops",
  "security.mfa_policy",
  "security.mfa_reset",
  "audit.read",
  "staff.grant",
];

/** API-091..093: grants with reasons and MFA step-up; the server refuses self-escalation and removing the last administrator. */
export function Staff({ ctx }: { ctx: Ctx }) {
  const [state, reload] = useServer<Schemas["StaffPage"]>(ctx, () => ctx.api.GET("/api/v1/ops/staff"));
  return (
    <Status ctx={ctx} state={state} onRetry={reload}>
      {(page) => (
        <ul className="cards">
          {page.items.map((m) => (
            <li key={m.userId}>
              <MemberCard ctx={ctx} member={m} refresh={reload} />
            </li>
          ))}
        </ul>
      )}
    </Status>
  );
}

function MemberCard({ ctx, member, refresh }: { ctx: Ctx; member: Member; refresh: () => void }) {
  const { t } = ctx;
  const [permission, setPermission] = useState<string>(PERMISSIONS[0] ?? "cook.review");
  const [reason, setReason] = useState("");
  const { run, error, code, busy } = useMutation(ctx, refresh);
  return (
    <article className="card" aria-label={member.displayName ?? member.userId}>
      <h3>
        {member.displayName ?? member.userId} · {member.status}
      </h3>
      <ul className="rows">
        {member.grants.map((g) => (
          <li key={g.grantId}>
            {g.permission}{" "}
            <button
              type="button"
              className="danger"
              disabled={busy || reason.trim().length < 3}
              onClick={() =>
                void run(() =>
                  ctx.api.POST("/api/v1/ops/staff/{userId}/grant-revocations", {
                    params: { path: { userId: member.userId } },
                    body: { grantId: g.grantId, reason: reason.trim() },
                  }),
                )
              }
            >
              {t.ops.revoke}
            </button>
          </li>
        ))}
      </ul>
      <label>
        {t.ops.permission}{" "}
        <select value={permission} onChange={(e) => setPermission(e.target.value)}>
          {PERMISSIONS.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
      </label>
      <label>
        {t.reason} <input maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <button
        type="button"
        disabled={busy || reason.trim().length < 3}
        onClick={() =>
          void run(() =>
            ctx.api.POST("/api/v1/ops/staff/{userId}/grants", { params: { path: { userId: member.userId } }, body: { permission, reason: reason.trim() } }),
          )
        }
      >
        {t.ops.grant}
      </button>
      <StepUp ctx={ctx} code={code} />
      <ErrorLine message={code === "STEP_UP_REQUIRED" ? null : error} />
    </article>
  );
}
