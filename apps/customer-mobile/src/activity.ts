import { useEffect } from "react";
import { newKey, type MobileApi, type Schemas } from "./mobileApi";

type Kind = Schemas["ActivityEvent"]["kind"];

/**
 * MB-054 (D17) on native, as on the web: organic browsing activity for the cooks' free report. The visitor id is random
 * and lives only for this app process (never stored, never an account, device or advertising id); the server keeps a
 * per-day keyed hash of it and counts each kind, subject and day once. Fire and forget: a failure never affects the app.
 */
let visitor: string | null = null;
let queue: Schemas["ActivityEvent"][] = [];
let timer: ReturnType<typeof setTimeout> | null = null;
let target: MobileApi | null = null;

function flush() {
  timer = null;
  const events = queue.slice(0, 50);
  queue = queue.slice(50);
  visitor ??= newKey("app");
  if (target && events.length > 0) {
    try {
      void target.POST("/api/v1/activity", { body: { visitorId: visitor, events } }).catch(() => undefined);
    } catch {
      // never affects browsing
    }
  }
  if (queue.length > 0) {
    timer = setTimeout(flush, 1000);
  }
}

export function track(api: MobileApi, kind: Kind, subjectId: string) {
  target = api;
  if (!queue.some((e) => e.kind === kind && e.subjectId === subjectId)) {
    queue.push({ kind, subjectId });
  }
  timer ??= setTimeout(flush, 1000);
}

export function useTrack(api: MobileApi, kind: Kind, subjectIds: string[]) {
  const key = subjectIds.join(",");
  useEffect(() => {
    for (const id of key ? key.split(",") : []) {
      track(api, kind, id);
    }
  }, [api, kind, key]);
}
