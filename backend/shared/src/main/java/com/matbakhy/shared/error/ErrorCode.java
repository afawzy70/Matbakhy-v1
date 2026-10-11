package com.matbakhy.shared.error;

/**
 * Stable API error codes with their HTTP status and retryability (docs/engineering/03-api-contract.md).
 * Clients localize {@link #messageKey()}; codes are part of the public contract and must not be renamed.
 */
public enum ErrorCode {
    MALFORMED_REQUEST(400, false),
    UNAUTHENTICATED(401, false),
    FORBIDDEN(403, false),
    ACCOUNT_DISABLED(403, false),
    STEP_UP_REQUIRED(403, false),
    NOT_FOUND(404, false),
    METHOD_NOT_ALLOWED(405, false),
    UNSUPPORTED_MEDIA_TYPE(415, false),
    VALIDATION_FAILED(422, false),
    STATE_CONFLICT(409, false),
    VERSION_CONFLICT(409, false),
    IDEMPOTENCY_CONFLICT(409, false),
    STOCK_CHANGED(409, false),
    PRICE_CHANGED(409, false),
    QUOTE_EXPIRED(409, false),
    SLOT_CLOSED(409, false),
    KITCHEN_UNAVAILABLE(409, false),
    RATE_LIMITED(429, true),
    DEPENDENCY_UNAVAILABLE(503, true),
    INTERNAL_ERROR(500, true);

    private final int httpStatus;
    private final boolean retryable;

    ErrorCode(int httpStatus, boolean retryable) {
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public boolean retryable() {
        return retryable;
    }

    public String messageKey() {
        return "error." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
