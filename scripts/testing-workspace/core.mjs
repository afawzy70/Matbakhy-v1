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
  const privateValues = [...new Set(secrets.filter(Boolean))].sort((a,b)=>b.length-a.length);
  if (privateValues.length) {
    const alternatives = privateValues.map(s=>s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
    value = value.replace(new RegExp(alternatives.join('|'), 'g'), '[REDACTED]');
  }
  return value.replace(/Bearer\s+[^\s"']+/gi, 'Bearer [REDACTED]')
    .replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, '[REDACTED TOKEN]')
    .replace(/((?:password|passwd|secret|token|otp|authorization|cookie)\s*[:=]\s*)[^\n,;]+/gi, '$1[REDACTED]');
}
export function executionIssues(c, record = {}, note = {}, run = {}) {
  const issues = [];
  if (!c.guidance_complete) issues.push('GUIDANCE_NOT_COMPLETE');
  if (!note.evidence?.trim() || !/^[a-f0-9]{40}$/i.test(run.release || '')) issues.push('MISSING_EVIDENCE_OR_RELEASE');
  const required = [...(c.flow || []).map(step=>step.key), 'verify:outcomes'];
  if (required.some(key=>!record.checks?.includes(key))) issues.push('INCOMPLETE_EXECUTION');
  return issues;
}
export function report(data, records, notes, secrets, metadata) {
  return {
    schema: 1, exported_at: new Date().toISOString(), catalog_source_sha: data.source_sha,
    catalog_digest: data.catalog_digest, source_inputs: data.inputs,
    live_verification_by_workspace: false,
    environment: sanitize(metadata.environment, secrets), release: sanitize(metadata.release, secrets),
    results: data.cases.map(c => ({
      id: c.id, recorded_status: records[c.id]?.status || 'NOT_RUN',
      status: records[c.id]?.status === 'PASS' && executionIssues(c, records[c.id], notes[c.id], notes[c.id]?.run).length ? 'BLOCKED' : records[c.id]?.status || 'NOT_RUN',
      updated: records[c.id]?.updated || null, checked_steps: records[c.id]?.checks || [],
      guidance_complete: c.guidance_complete,
      evidence_state: notes[c.id]?.evidence?.trim() && /^[a-f0-9]{40}$/i.test(notes[c.id]?.run?.release || '') ? 'TESTER_SUPPLIED' : 'MISSING_EVIDENCE_OR_RELEASE',
      evidence: sanitize(notes[c.id]?.evidence, secrets),
      defect: sanitize(notes[c.id]?.defect, secrets),
      test_record: sanitize(notes[c.id]?.order, secrets),
      environment: sanitize(notes[c.id]?.run?.environment, secrets),
      release: sanitize(notes[c.id]?.run?.release, secrets),
      execution_issues: executionIssues(c, records[c.id], notes[c.id], notes[c.id]?.run),
      reason: sanitize(notes[c.id]?.reason, secrets),
    })),
  };
}
