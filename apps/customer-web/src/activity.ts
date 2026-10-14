import { useEffect } from "react";
import type { Api, Schemas } from "./api";

type Kind = Schemas["ActivityEvent"]["kind"];

/**
 * MB-054 (D17): organic browsing activity for the cooks' free report. The visitor id is random and lives only for this
 * browser tab's session (sessionStorage), so a refresh counts once; the server keeps a per-day keyed hash of it and
 * counts each kind, subject and day once. Fire and forget: a failure never affects browsing, and nothing here ranks,
 * targets or bills anyone.
 */
const KEY = "matbakhy.visitor";
let queue: Schemas["ActivityEvent"][] = [];
let timer: number | null = null;
let target: Api | null = null;

function visitorId(): string | null {
  try {
    let id = window.sessionStorage.getItem(KEY);
    if (!id) {
      id = crypto.randomUUID();
      window.sessionStorage.setItem(KEY, id);
    }
    return id;
  } catch {
    return null; // storage blocked: record nothing rather than invent a stable id
  }
}

function flush() {
  timer = null;
  const events = queue.slice(0, 50);
  queue = queue.slice(50);
  const id = visitorId();
  if (target && id && events.length > 0) {
    void target.POST("/api/v1/activity", { body: { visitorId: id, events } }).catch(() => undefined);
  }
  if (queue.length > 0) {
    timer = window.setTimeout(flush, 1000);
  }
}

export function track(api: Api, kind: Kind, subjectId: string) {
  target = api;
  if (!queue.some((e) => e.kind === kind && e.subjectId === subjectId)) {
    queue.push({ kind, subjectId });
  }
  if (timer === null) {
    timer = window.setTimeout(flush, 1000);
  }
}

/** Records the given subjects once they are shown (empty ids record nothing). */
export function useTrack(api: Api, kind: Kind, subjectIds: string[]) {
  const key = subjectIds.join(",");
  useEffect(() => {
    for (const id of key ? key.split(",") : []) {
      track(api, kind, id);
    }
  }, [api, kind, key]);
}
