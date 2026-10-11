import { useCallback, useEffect, useRef, useState } from "react";
import { errorCode, type Api } from "./api";
import { errorText, type Strings } from "./i18n";

export interface Ctx {
  api: Api;
  t: Strings;
  locale: "ar" | "en";
  /** Called when the server says the session ended. */
  onSignedOut: () => void;
  /** Navigate within the app (hash routes). */
  go: (route: string) => void;
}

export type Loaded<T> = { status: "loading" } | { status: "error"; code: string } | { status: "ok"; data: T };

/**
 * Loads from the server whenever {@code key} changes or {@code reload} is called; the server response is the only
 * source of state. {@code load} is read from a ref so callers can pass an inline function.
 */
export function useServer<T>(ctx: Ctx, load: () => Promise<{ data?: T; error?: unknown; response: Response }>, key: string = "") {
  const [state, setState] = useState<Loaded<T>>({ status: "loading" });
  const [nonce, setNonce] = useState(0);
  const loadRef = useRef(load);
  const signedOutRef = useRef(ctx.onSignedOut);
  useEffect(() => {
    loadRef.current = load;
    signedOutRef.current = ctx.onSignedOut;
  });
  useEffect(() => {
    let active = true;
    loadRef
      .current()
      .then((r) => {
        if (!active) {
          return;
        }
        if (r.response.status === 401) {
          signedOutRef.current();
          return;
        }
        setState(r.data !== undefined ? { status: "ok", data: r.data } : { status: "error", code: errorCode(r.error) });
      })
      .catch(() => {
        if (active) {
          setState({ status: "error", code: "NETWORK" });
        }
      });
    return () => {
      active = false;
    };
  }, [key, nonce]);
  const reload = useCallback(() => setNonce((n) => n + 1), []);
  return [state, reload] as const;
}

export function Status<T>({ ctx, state, onRetry, children }: { ctx: Ctx; state: Loaded<T>; onRetry: () => void; children: (data: T) => React.ReactNode }) {
  if (state.status === "loading") {
    return <p role="status">{ctx.t.loading}</p>;
  }
  if (state.status === "error") {
    return (
      <div role="alert" className="notice notice-danger">
        <p>{errorText(ctx.t, state.code)}</p>
        <button type="button" onClick={onRetry}>
          {ctx.t.retry}
        </button>
      </div>
    );
  }
  return <>{children(state.data)}</>;
}

/** Runs a mutation, shows the server's error, and always refreshes from the server afterwards. */
export function useMutation(ctx: Ctx, refresh: () => unknown) {
  const [error, setError] = useState<string | null>(null);
  const [code, setCode] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const run = useCallback(
    async (call: () => Promise<{ error?: unknown; response: Response }>): Promise<boolean> => {
      setBusy(true);
      setError(null);
      setCode(null);
      try {
        const r = await call();
        if (r.response.status === 401) {
          ctx.onSignedOut();
          return false;
        }
        if (!r.response.ok) {
          setCode(errorCode(r.error));
          setError(errorText(ctx.t, errorCode(r.error)));
          return false;
        }
        return true;
      } catch {
        setError(errorText(ctx.t, "NETWORK"));
        return false;
      } finally {
        setBusy(false);
        void refresh();
      }
    },
    [ctx, refresh],
  );
  return { run, error, code, busy };
}

export function ErrorLine({ message }: { message: string | null }) {
  return message ? (
    <p role="alert" className="field-error">
      {message}
    </p>
  ) : null;
}

/** Minutes until a server deadline, using the server clock offset rather than the device clock. */
export function minutesLeft(deadline: string, serverOffsetMs: number, nowMs: number = Date.now()): number {
  return Math.floor((Date.parse(deadline) - (nowMs + serverOffsetMs)) / 60000);
}

export function egpToMinor(value: string): number | null {
  if (!/^\d{1,7}(\.\d{1,2})?$/.test(value.trim())) {
    return null;
  }
  const [whole, fraction = ""] = value.trim().split(".");
  return Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
}
