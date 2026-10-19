import { stepUpUrl, type Schemas } from "./api";
import { ErrorLine, Status, useMutation, useServer, type Ctx } from "./shared";

/**
 * Account security (D18). The factor itself is enrolled and removed in Keycloak (its sign-in page and account console);
 * this section shows the policy and state, and records the user's own preference. The server decides everything: a REQUIRED
 * policy cannot be turned off here, and turning MFA off needs a recent second factor.
 */
export function Security({ ctx }: { ctx: Ctx }) {
  const { t } = ctx;
  const [state, reload] = useServer<Schemas["SecuritySettings"]>(ctx, () => ctx.api.GET("/api/v1/me/security"), ctx.locale);
  return (
    <section className="card" aria-labelledby="security-heading">
      <h2 id="security-heading">{t.security.title}</h2>
      <Status ctx={ctx} state={state} onRetry={reload}>
        {(s) => <SecurityBody ctx={ctx} settings={s} reload={reload} />}
      </Status>
    </section>
  );
}

function SecurityBody({ ctx, settings, reload }: { ctx: Ctx; settings: Schemas["SecuritySettings"]; reload: () => void }) {
  const { t } = ctx;
  const { run, error, code, busy } = useMutation(ctx, reload);
  const step = stepUpUrl(window.location.href);
  const setPreference = (enabled: boolean) => void run(() => ctx.api.PUT("/api/v1/me/security/mfa", { body: { enabled } }));
  return (
    <div className="stack">
      <p>
        {t.security.policy}: <strong>{t.security.mode[settings.effectiveMode] ?? settings.effectiveMode}</strong>
      </p>
      {!settings.available ? (
        <p role="status">{t.security.unavailable}</p>
      ) : (
        <>
          <p>
            {t.security.status}: <strong>{settings.required ? t.security.on : t.security.off}</strong> ·{" "}
            {settings.verifiedInSession ? t.security.verified : t.security.notVerified}
          </p>
          {settings.effectiveMode === "REQUIRED" ? <p role="status">{t.security.requiredNotice}</p> : null}
          {settings.required && !settings.verifiedInSession ? (
            <p className="notice">
              {t.security.verifyHint}{" "}
              <a className="button" href={step}>
                {t.security.verifyNow}
              </a>
            </p>
          ) : null}
          {settings.effectiveMode === "OPTIONAL" && !settings.enabled ? (
            settings.verifiedInSession ? (
              <button type="button" disabled={busy} onClick={() => setPreference(true)}>
                {t.security.turnOn}
              </button>
            ) : (
              <p>
                {t.security.verifyHint}{" "}
                <a className="button" href={step}>
                  {t.security.turnOn}
                </a>
              </p>
            )
          ) : null}
          {settings.effectiveMode === "OPTIONAL" && settings.enabled ? (
            <p>
              <button type="button" className="secondary" disabled={busy} onClick={() => setPreference(false)}>
                {t.security.turnOff}
              </button>{" "}
              <span className="muted">{t.security.turnOffHint}</span>
            </p>
          ) : null}
          {code === "STEP_UP_REQUIRED" ? (
            <p className="notice">
              {t.security.stepUpNeeded}{" "}
              <a className="button" href={step}>
                {t.security.stepUpLink}
              </a>
            </p>
          ) : (
            <ErrorLine message={error} />
          )}
          <p>
            <a href={settings.accountManagementUrl} target="_blank" rel="noopener noreferrer">
              {t.security.manage}
            </a>
          </p>
        </>
      )}
    </div>
  );
}
