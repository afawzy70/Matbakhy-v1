# Brand, Arabic content and UX direction

**2026-10-11 owner update:** The [approved-direction reference](../design/approved/DESIGN.md) governs palette, Cairo typography and homepage hierarchy. The review package below still defines remaining screen coverage; it does not reopen the selected visual direction. Logo/trademark and whole-product approval remain separate.

Branding starts in P00 before final UI implementation. `Matbakhy / مطبخي` is a working name. No trademark clearance or legal registration is claimed. Keep logo, name, slogan, color tokens and typography editable in shared assets/configuration.

## Proposed direction to prototype

Warm, trustworthy and food-focused: natural food photography, generous spacing on discovery, clear prices/availability, human cook profiles and restrained warm accents. Operations screens prioritize readable tables, status queues and clear actions. Do not reuse a developer-console design from another project. Avoid fake ratings, fabricated testimonials, false countdowns and unsupported "certified safe" claims.

Create a compact review package: customer discovery + meal detail + checkout + order status; cook order queue; platform cook review + financial exception; mobile home + checkout in Arabic RTL and English LTR. Include a phone viewport and tablet/desktop. Record screenshot paths and the exact commit; request visual approval only after these can be reviewed.

Use semantic design tokens for spacing, typography, foreground/background, focus, danger, warning, success, border, elevation and radii. Pick fonts with Arabic glyph support and verify licensing. Mirror navigation direction and directional icons where appropriate; do not mirror food images, logos, numbers or all icons indiscriminately. Use CSS logical properties; test mixed Arabic/Latin addresses and EGP totals with bidi isolation.

## Content rules

All user-facing text uses translation keys with explicit fallback. No raw backend stack traces, enum names or internal implementation jargon. Display server error codes through localized messages. Errors identify the field/action and a useful recovery path.

| Situation | Arabic draft | English draft |
|---|---|---|
| No meals in zone | لسه مفيش وجبات متاحة في منطقتك. جرّب منطقة أو ميعاد تاني. | No meals are available here yet. Try another area or time. |
| Stock changed | الكمية المتاحة اتغيّرت. راجع السلة قبل تأكيد الطلب. | Availability changed. Review your basket before confirming. |
| Uncertain order response | بنتأكد من حالة طلبك. متعملش طلب جديد دلوقتي. | We are checking your order. Please do not place another yet. |
| Waiting for cook | طلبك مستني موافقة صاحب المطبخ. | Your order is waiting for the cook to accept. |
| Allergen uncertainty | لو عندك حساسية، راجع المكونات ومعلومات الحساسية قبل الطلب. | If you have an allergy, review the ingredients and allergen information before ordering. |
| Offline | الاتصال بالإنترنت مش متاح. حاول تاني لما الاتصال يرجع. | You are offline. Try again when your connection returns. |

These are draft tone examples, not approved legal text. Required terms, privacy notices, cancellation wording and ingredient/allergen statements need owner-approved content before pilot. Never generate a dietary suitability guarantee from a label.

Minimum accessibility target is WCAG 2.2 AA for web; test native screen readers, dynamic text and touch targets. Color never carries status alone. Respect reduced motion. Every form has visible labels, associated errors and a sensible focus order.

## Editable brand system (MB-003, draft)

Status: **DRAFT, pending owner approval (O05)**. Trademark clearance has not been searched or registered.

- Source of truth: [`packages/design-tokens/tokens.json`](../../packages/design-tokens/tokens.json). It holds the brand name and slogan (Arabic/English), light and dark semantic colors, typography, spacing, radii, elevation, touch-target minimum, usage notes and the required contrast pairs.
- Web apps import the generated `tokens.css` (`--mb-*` custom properties) and the font. The native app imports the same TypeScript tokens. App titles and headings read the brand name from tokens; HTML titles are injected at build time. Tests fail if app source hard-codes the brand name or a hex color, so renaming or recoloring needs only `tokens.json` (then `pnpm --filter @matbakhy/design-tokens generate`).
- Draft wordmark: `wordmarkSvg()` draws a cooking-pot mark with steam plus the brand name from tokens. It is a placeholder for review, not an approved logo.
- Font: IBM Plex Sans Arabic (Arabic and Latin glyphs), SIL Open Font License 1.1, from `@fontsource/ibm-plex-sans-arabic` 5.3.0. The OFL permits bundling and embedding in apps; the license text ships with the package.
- Contrast: every listed pair is tested against WCAG 2.2 thresholds in both themes (4.5:1 for text, 3:1 for focus indicators and `borderStrong` component boundaries). `border` is for decorative dividers only.
- Bidirectional content: wrap embedded opposite-direction runs (amounts with currency, Latin inside Arabic, mixed addresses) in `<bdi>` or `unicode-bidi: isolate`.
- Specimens: `pnpm --filter @matbakhy/design-tokens specimen <dir>` renders light/dark × Arabic/English × phone/desktop with Chromium and fails unless the Arabic font actually loaded.
