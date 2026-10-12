/** Test-only helpers (never imported by app code). */
export function fakeJwt(sub: string): string {
  const b64 = (o: object) => globalThis.btoa(JSON.stringify(o)).replace(/=+$/, "").replace(/\+/g, "-").replace(/\//g, "_");
  return `${b64({ alg: "none" })}.${b64({ sub })}.sig`;
}
