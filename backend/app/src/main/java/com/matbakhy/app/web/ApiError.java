package com.matbakhy.app.web;

import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.util.List;

/** Wire error body: {code, messageKey, fieldErrors[{field, code}], requestId, retryable}. */
public record ApiError(String code, String messageKey, List<FieldError> fieldErrors, String requestId, boolean retryable) {

    static ApiError of(ErrorCode code, List<FieldError> fieldErrors, String requestId) {
        return new ApiError(code.name(), code.messageKey(), List.copyOf(fieldErrors), requestId, code.retryable());
    }
}
