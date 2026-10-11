# Brand, Arabic content and UX direction

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
