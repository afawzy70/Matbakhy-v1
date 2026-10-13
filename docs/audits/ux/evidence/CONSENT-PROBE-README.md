# Audit-only consent reproducer

Source inspected: `cc52fa4224e3163677a1215dc8f63ede2a9dbe4c`, implementation/mvp. Executed with Node24.21.0, pnpm10.34.6, Vitest5.0.3, jsdom30.1.2. Synthetic fetch responses only; no real API/order/payment/account. This test imports the actual React customer App and follows the existing D13 test-fixture approach. It verifies that the undesired behavior exists. A passing result is **not** a product acceptance PASS.

From repository root after installing existing pinned dependencies:

```bash
./apps/customer-web/node_modules/.bin/vitest run --config docs/audits/ux/evidence/consent-vitest.config.mjs
```

The old quote q0 is refused with PRICE_CHANGED; q1's old/new notice appears. Without clicking Accept new total, Calculate total clears the notice; ordinary terms acceptance enables placement and POST q1 succeeds. The probe asserts that path. Browser/backend/native verification is a separate check. After a fix this diagnostic probe should fail; implement the inverse acceptance regression in the relevant normal test suites, then archive this original evidence unchanged.

Files: [probe](consent.probe.test.tsx), [portable audit config](consent-vitest.config.mjs), [actual command output](consent-component-reproducer.txt). Earlier scratch attempts failed on audit-directory module resolution; the config uses relative existing dependency paths and changes no production configuration.
