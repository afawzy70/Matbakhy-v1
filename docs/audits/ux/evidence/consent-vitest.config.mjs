// Audit-only component reproducer: uses existing pinned dependencies; no production configuration change.
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { defineConfig } from '../../../../apps/customer-web/node_modules/vitest/dist/config.js';
import react from '../../../../apps/customer-web/node_modules/@vitejs/plugin-react/dist/index.js';
const root = fileURLToPath(new URL('../../../../', import.meta.url));
export default defineConfig({
  root,
  plugins: [react()],
  resolve: { alias: { react: resolve(root, 'apps/customer-web/node_modules/react') } },
  test: { environment: 'jsdom', include: ['docs/audits/ux/evidence/consent.probe.test.tsx'] },
});
