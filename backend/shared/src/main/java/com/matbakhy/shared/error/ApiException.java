package com.matbakhy.shared.error;

import java.util.List;
import java.util.Objects;

/**
 * A domain or application failure with a stable API code. Modules throw this (or a subclass); the
 * web layer maps it to the standard error body. The message is for logs only and is never returned
 * to clients, so it must not contain personal data.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<FieldError> fieldErrors;

    public ApiException(ErrorCode code, String logMessage) {
        this(code, logMessage, List.of());
    }

    public ApiException(ErrorCode code, String logMessage, List<FieldError> fieldErrors) {
        super(logMessage);
        this.code = Objects.requireNonNull(code, "code");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }

    /** A field-level validation problem: the JSON field path and a stable machine code. */
    public record FieldError(String field, String code) {
        public FieldError {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(code, "code");
        }
    }
}
