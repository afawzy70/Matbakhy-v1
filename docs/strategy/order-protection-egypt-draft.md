# Matbakhy Order Protection — Egypt policy discovery v0.1
Date: 2026-10-13
Status: DRAFT / FUTURE INITIATIVE / NOT ACTIVE. Owner and qualified Egyptian legal/food-compliance/payment review required before launch. This is a design hypothesis, not legal advice, provider approval, or permission to retain consumer money.

## Core promise
Protect approved home cooks from preparing costly non-collectible orders. Avoid making unsupported promises of guaranteed payment. Give customers clear, proportionate rules and genuine recourse. Never ask a cook to start high-exposure preparation before the platform can show VERIFIED_REQUIRED_PAYMENT (if applicable) and ACCEPTED.

## Proposed customer-facing policy
1. Show exact product price, taxes/fees, delivery/pickup, cancellation deadline, required upfront amount, responsible seller/collector, refund and contact terms **before** final confirmation. Preserve immutable versioned policy and order-price snapshot.
2. For standard low-exposure orders, permit cash on delivery/pickup only if the cook/zone/operations policy allows it. Verify contact phone before order. Define cook acceptance deadline; pending is not confirmed.
3. For high-exposure orders (large quantity, advance ingredients, personalized menu, events), require a prepayment/deposit through a licensed/contracted payment provider before acceptance or prep. No manual screenshot proof. Deposit size is based on exposure & agreement, not a universal 30%/50% rule.
4. Customers can cancel before a clearly disclosed preparation cutoff without a discretionary penalty, subject to reviewed legal policy. After cutoff, any eligible fee/retention is capped by the published policy and legally recoverable/actual documented exposure; never retain amounts automatically if supplier is at fault.
5. If cook rejects, cannot fulfill, changes essential terms, arrives late in breach, or food is unsafe/wrong: preserve customer statutory remedies, investigate and refund as legally required; never use 'no refunds for food' blanket text.
6. If customer misses agreed handover: record timestamps, customer-visible contact attempts, arrival/ready evidence, reasonable waiting window, handover code; staff review before closing a dispute. Never mark as delivered just because driver/cook says so.
7. No public blacklist, discriminatory proxy scoring or permanent punishment after one disputed no-show. A documented repeated pattern can trigger payment-first eligibility with notice and appeal. Limit data and retention.
8. No off-platform transfer to cook personal wallet as 'platform protected'; show verified provider payment receipt and use controlled ledger/reconciliation.
9. Cook sees a clear badge: 'Payment verified / Preparation approved / COD allowed / Payment pending'; money collected, due, refundable and owed to cook remain separate financial states.
10. Support an auditable neutral dispute process: customer and cook upload relevant proof, human reviews, records reason, appeal, final outcome; restrict private phone/address visibility.

## Proposed risk tiers — operational tests, NOT current thresholds
Tier A: low-risk ready-to-prepare menu & small value: COD may be allowed with verified phone + acceptance confirmation and service-zone limits.
Tier B: future dated order / material ingredients commitment: deposit covering a reviewed portion of exposure; deposit verification before purchase/prep.
Tier C: bespoke catering / large events: signed digital quote & staged prepayments or full payment, with explicit milestones and cancellation terms.
Payment-first rules may also apply after substantiated repeated collection failures; no arbitrary or secret fees. Thresholds/amounts and timing to be tuned in an Egyptian field pilot, approved by owner and counsel.

## Exact lifecycle & separation of responsibility
QUOTE_CREATED -> CUSTOMER_REVIEWED_TERMS -> (PAYMENT_REQUIRED -> PAYMENT_VERIFIED) -> COOK_ACCEPTED -> PREPARING -> READY -> HANDED_OVER_VERIFIED -> COMPLETED -> RECONCILED.
Offer decline/expiry/cancel/refund/dispute branches. The system must not infer payment from checkout return redirect or chat. Verify provider callback signature, amount, currency, idempotency, order ID; query provider for ambiguous states; never double-charge on retry. Capacity reservation and expiry must be concurrency safe. Preserve original amount after order acceptance. Changes require explicit re-quote before placement.
Separate order-state, payment-state, cooking-state, handover-state and settlement-state. Refunds/retentions are recorded with reason and approval; cook compensation is never implied by merely receiving a customer deposit.

## Egypt operational/legal gates — verify before production
- Consumer Protection Law 181/2018, especially distance contracting disclosures (Arts 37-41) and execution regulations. Article 41 has exceptions for goods made to consumer specification and circumstances where withdrawal contradicts nature of product; the applicability to each prepared-food scenario is a question for Egyptian counsel, not a blanket exemption. Never waive remedies for defect, misdescription or supplier breach.
- National Food Safety Authority registration/licensing and whether a residential home kitchen is eligible for a relevant category in each municipality/zone. NFSA's portal is for food establishments; do not assume home cooks are automatically lawful/registered.
- Merchant of record, tax/invoice obligations, commercial status, platform agency agreements; who legally collects/holds money; provider's actual available partial-payment/refund/payout terms; consumer data and proof retention under Egypt privacy rules.
- Payment product evidence: Paymob documents Egyptian payment links, refunds, transaction callbacks and Payouts, but merchant-specific capabilities, limits, chargebacks and permitted funds flow require written confirmation.
- No custom escrow, wallets, pooled funds or guarantee of payout without appropriate legal/provider authorizations.

## Policy examples for review
A. 250 EGP standard order: customer verifies phone; COD may proceed if no deposit policy required; prep starts only after cook accepts. First missed pickup -> evidence & service workflow, not permanent ban.
B. 3,000 EGP bespoke family gathering; example ingredients at risk 1,200 EGP: propose 1,200 EGP prepayment *only when lawful, feasible and disclosed*; no prep until provider confirms. Any subsequent cancellation assessed by agreed and lawful milestone policy; unused balance refunded appropriately.
C. Kitchen cancels after deposit: no kitchen compensation; refund customer promptly, investigate incident and service recovery.
D. Customer claims allergy contamination or spoiled food: stop any automatic retention, initiate safety escalation and complaint review; preserve statutory remedies.
E. Electronic payment succeeds but callback late: payment inquiry/reconciliation; avoid duplicate charges and avoid giving kitchen falsely confirmed prep status.

## Pilot operations
Start with 1–2 zones, limited cooks and manually reviewed high-exposure orders. Train each cook on 'when am I safe to buy ingredients?'. Test disputed no-show, unreachable phone, acceptance timeout, duplicate callback, partial refund, chargeback, failed delivery, last-portion race, cook cancellation, food safety complaint, customer refund and reconciliation. Record median support resolution, cook loss per 100 orders, no-show rate, deposit conversion drop, dispute reversal rate, refund timeliness and net contribution margin. Do not launch guarantee fund until enough historical evidence.

## Proposed microcopy, subject to legal signoff
Customer: 'طلبك يحتاج مقدمًا قدره [amount] جنيه قبل بدء التحضير. آخر موعد للإلغاء [timestamp]. شوف شروط الإلغاء والاسترداد قبل التأكيد.'
Cook: 'الطلب مؤكد والدفع المطلوب تم التحقق منه. يمكنك بدء التحضير. تفاصيل التسوية: [link].'
Pending: 'لسه ما تبدأش شراء خامات للطلب ده. الدفع المطلوب لم يتأكد بعد.'

## Open decisions
What constitutes high exposure? COD cap? cook opt-in? minimum notice for cancellations? late handover window? deposit basis and commission? who collects customer funds and transfers to cook? who bears card charges/chargebacks? safety/licensing model for home kitchens? refund SLA? employee authority? legal text in Arabic? limit on preserved evidence? provider fees and payout calendar?

## Source starting points (not final legal findings)
- Egyptian consumer protection law 181/2018: https://egyptlaw.ai/ar/laws/eg-egyptian-consumer-protection-law-law-181-2018
- NFSA official registration portal: https://reg.nfsa.gov.eg/
- Egyptian state information re NFSA local market oversight 2026: https://mediadr.sis.gov.eg/handle/123456789/117748
- Paymob Egyptian payment link: https://www.paymob.com/ar/payment-link
- Official Paymob API collections: https://github.com/PaymobAccept/API-Postman-Collections

## Product governance
No automatic implementation, production rollout, financial policy activation, or new MVP commitments. Owners may later approve scoped stories and an external legal/financial provider integration design. Reference issues #3 and #4 (AI concierge must also comply with payment gating and explicit consent).

## Owner direction — 2026-10-13 (pilot payments)
- Prioritize customer acquisition and familiar existing Egyptian payment instruments: InstaPay, regulated mobile wallets, transfers, and eligible COD. Do NOT invent a proprietary wallet, escrow, payment method or require heavy provider integration for initial pilot.
- Exploratory MVP option: customer transfers deposit directly to the cook's permitted business/merchant account or wallet. Platform displays approved recipient details, amount and terms and logs a manual cook-reported receipt acknowledgement, explicitly NOT independent bank verification.
- Financial flow is customer -> cook, not through Matbakhy. Cook confirmation and order acceptance are separate actions. Orders requiring deposits cannot start preparation before cook verifies receipt in actual account. Pending and disputed transfers route to human support and no duplicate payment demand.
- Do NOT claim that lack of custody makes Matbakhy legally exempt. Platform roles (marketplace intermediary/contracting party/agent), advertising, consumer rights, data duties, provider terms, kitchen licensing, merchant-account permissible use and dispute practices require Egyptian counsel review before live launch.
- No blanket 'platform has no legal responsibility' promise. Customer-facing language must truthfully distinguish who sells, who is paid, who refunds, what platform support does, and cannot waive mandatory statutory rights.
- Revisit automated payment verification, payment gateway, settlement and guarantees after pilot conversion, cook loss, disputes, and provider/legal feasibility measurements. This paragraph records owner's commercial preference, not legal approval or changed MVP story acceptance.
