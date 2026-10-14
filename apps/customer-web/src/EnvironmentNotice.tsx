/**
 * Persistent notice on a demo or test environment (owner decision 2026-10-14). It shows when the API reports demo=true
 * (every environment except production) or when this build was made for a demo environment (VITE_DEMO_NOTICE=true, set by
 * the preview front image), so the notice is still there if the API cannot be reached.
 */
export function builtForDemo(): boolean {
  return (import.meta.env.VITE_DEMO_NOTICE as string | undefined) === "true";
}

export function EnvironmentNotice({ show, title, text }: { show: boolean; title: string; text: string }) {
  if (!show) {
    return null;
  }
  return (
    <div className="env-notice" role="note" aria-label={title}>
      <strong>{title}</strong> {text}
    </div>
  );
}
