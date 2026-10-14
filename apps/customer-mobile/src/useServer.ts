import { useCallback, useEffect, useRef, useState } from "react";
import { errorCode } from "./mobileApi";

export type Loaded<T> = { status: "loading" } | { status: "error"; code: string } | { status: "ok"; data: T };

/**
 * Loads from the server when {@code key} changes or {@code reload} is called; the server response is the only source
 * of state. A transport failure is reported as "NETWORK" so screens can say plainly that the app is offline instead of
 * showing stale data as current.
 */
export function useServer<T>(load: () => Promise<{ data?: T; error?: unknown; response: Response }>, key: string = "") {
  const [nonce, setNonce] = useState(0);
  const token = `${key}#${nonce}`;
  const [result, setResult] = useState<{ token: string; state: Loaded<T> } | null>(null);
  const loadRef = useRef(load);
  useEffect(() => {
    loadRef.current = load;
  });
  useEffect(() => {
    let active = true;
    loadRef
      .current()
      .then((r) => {
        if (active) {
          setResult({ token, state: r.data !== undefined ? { status: "ok", data: r.data } : { status: "error", code: errorCode(r.error) } });
        }
      })
      .catch(() => {
        if (active) {
          setResult({ token, state: { status: "error", code: "NETWORK" } });
        }
      });
    return () => {
      active = false;
    };
  }, [token]);
  const reload = useCallback(() => setNonce((n) => n + 1), []);
  // Until the response for the current key arrives, the state is loading (never the previous key's data).
  const state: Loaded<T> = result && result.token === token ? result.state : { status: "loading" };
  return [state, reload] as const;
}
