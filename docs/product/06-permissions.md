# Roles and permission matrix

Backend permissions are authoritative; frontend routes only improve usability. Every permission requires active account, tenant/object scope and applicable state. Use deny-by-default. A customer can also be a cook without gaining platform privileges.

| Action | Anonymous | Customer | Cook | Support | Ops | Finance | Admin |
|---|---|---|---|---|---|---|---|
| Browse public published meals/cook public profiles | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| Manage customer address/cart/order/review | No | Own | Own customer identity | No | No | No | No |
| Read customer order contact/address | No | Own | Own order; fulfillment need only | Assigned/authorized case | Operational need | Masked financial view | Requires explicit operational permission |
| Apply as cook | No | Own | Own | No | No | No | No |
| Manage meals/slots | No | No | Own kitchen | No | Moderate, not silently impersonate | No | Only if granted ops permission |
| Accept/prepare/dispatch/complete | No | No | Own kitchen | No | Exception actions with reason | No | Only if granted ops permission |
| Approve/suspend cook | No | No | No | No | Yes | No | Only if granted ops permission |
| Review private onboarding documents | No | No | Own uploaded documents | No | Assigned verification scope | No | Explicit verification permission |
| Read/respond to support case | No | Own | Related limited view | Yes | Yes | Financial portion | Explicit support permission |
| Approve refund obligation | No | Request | Request | Recommend | Recommend | Yes within limit | Explicit finance permission |
| Report collected cash | No | Dispute | Own fulfilled orders | No | With documented exception | Record correction event | Explicit finance permission |
| Verify cash/refund/settlement | No | No | No | No | No | Yes | Explicit finance permission |
| Manage staff grants/configuration | No | No | No | No | Operational config only | Finance config within policy | Yes; step-up MFA |
| Set the MFA policy per account category (`security.mfa_policy`) | No | No | No | No | No | No | Explicit permission; step-up MFA; reason; audited |
| Reset a user's MFA at the identity provider (`security.mfa_reset`) | No | No | No | No | No | No | Explicit permission; step-up MFA; reason; audited; never one's own; never reads secrets |
| Turn one's own MFA on/off | Yes (if the category policy is OPTIONAL) | Yes | Yes | Yes | Yes | Yes | Yes |
| View audit | No | Limited own events | Limited own events | Own case scope | Operational scope | Finance scope | Security/admin scope |

Admin is not an implicit superuser over every data domain. Assign granular permissions such as `cook.review`, `order.exception_cancel`, `order.handover_override`, `finance.refund_approve`, `finance.settlement_verify`, `staff.grant` and `audit.read`. Protect role changes against self-escalation and removing the last recoverable administrator. No public registration request can assign COOK approval, STAFF or ADMIN.

All ID-based requests must load the object and enforce owner/kitchen/case scope. Opaque UUIDs do not replace authorization. Search/count/export endpoints apply the same scope as detail endpoints. Return 404 for another customer's resource to reduce existence leakage. Never serialize private cook location, documents or customer phones into public DTOs.

MFA follows the administrator's policy per account category (D18, `docs/security/mfa-policy.md`): the identity provider performs it, and the API enforces the effective policy server-side. When the policy for staff is REQUIRED, staff sessions must have passed MFA before any protected function. High-risk changes always require recent step-up authentication, reason and audit. Revoke sessions when grants are removed; short token lifetime alone does not immediately revoke active staff permissions, so check current grants for sensitive commands.

A solo pilot may have one person holding multiple roles. Record when separation of duties could not be achieved; do not fabricate another approver or claim independent review. Live finance policy must explicitly accept this operating limitation.
