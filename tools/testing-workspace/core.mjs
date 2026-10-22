export const statuses = ['NOT_RUN', 'PASS', 'FAIL', 'BLOCKED'];
export function matches(c, query = '', domain = '', readiness = '', result = '', records = {}) {
  const text = JSON.stringify([c.id, c.title, c.guidance, c.actors, c.surface]).toLocaleLowerCase('ar');
  return (!query || text.includes(query.trim().toLocaleLowerCase('ar')))
    && (!domain || c.domain === domain)
    && (!readiness || (readiness === 'complete' ? c.guidance_complete : !c.guidance_complete))
    && (!result || (records[c.id]?.status || 'NOT_RUN') === result);
}
export function persisted(records) {
  return Object.fromEntries(Object.entries(records).map(([id, r]) => [id, {
    status: statuses.includes(r.status) ? r.status : 'NOT_RUN',
    checks: Array.isArray(r.checks) ? r.checks.filter(x => typeof x === 'string') : [],
    updated: typeof r.updated === 'string' ? r.updated : null,
  }]));
}
export function sanitize(text, secrets = []) {
  let value = String(text || '');
  for (const secret of secrets) if (secret) value = value.split(secret).join('[REDACTED]');
  return value.replace(/Bearer\s+[^\s"']+/gi, 'Bearer [REDACTED]')
    .replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, '[REDACTED TOKEN]')
    .replace(/((?:password|passwd|secret|token|otp|authorization|cookie)\s*[:=]\s*)[^\n,;]+/gi, '$1[REDACTED]');
}
export function report(data, records, notes, secrets, metadata) {
  return {
    schema: 1, exported_at: new Date().toISOString(), catalog_source_sha: data.source_sha,
    live_verification_by_workspace: false,
    environment: sanitize(metadata.environment, secrets), release: sanitize(metadata.release, secrets),
    results: data.cases.map(c => ({
      id: c.id, status: records[c.id]?.status || 'NOT_RUN',
      updated: records[c.id]?.updated || null, checked_steps: records[c.id]?.checks || [],
      guidance_complete: c.guidance_complete,
      evidence: sanitize(notes[c.id]?.evidence, secrets),
      defect: sanitize(notes[c.id]?.defect, secrets),
      reason: sanitize(notes[c.id]?.reason, secrets),
    })),
  };
}
