package com.matbakhy.identity.api;

import java.util.Arrays;
import java.util.Optional;

/** Granular staff permissions (docs/product/06-permissions.md). Admin is not an implicit superuser. */
public enum Permission {
    STAFF_GRANT("staff.grant"),
    AUDIT_READ("audit.read"),
    COOK_REVIEW("cook.review"),
    CONTENT_MODERATE("content.moderate"),
    ORDER_OPS("order.ops"),
    ORDER_EXCEPTION_CANCEL("order.exception_cancel"),
    ORDER_HANDOVER_OVERRIDE("order.handover_override"),
    SUPPORT_CASE("support.case"),
    FINANCE_READ("finance.read"),
    FINANCE_VERIFY("finance.verify"),
    FINANCE_REFUND_APPROVE("finance.refund_approve"),
    FINANCE_SETTLEMENT("finance.settlement"),
    CONFIG_READ("config.read"),
    CONFIG_WRITE("config.write"),
    INTEGRATION_OPS("integration.ops");

    private final String code;

    Permission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Spring Security authority name. */
    public String authority() {
        return "PERM_" + code;
    }

    public static Optional<Permission> fromCode(String code) {
        return Arrays.stream(values()).filter(p -> p.code.equals(code)).findFirst();
    }
}
