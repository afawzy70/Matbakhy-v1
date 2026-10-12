package com.matbakhy.app.web;

import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps every failure to the standard {@link ApiError} body without stack traces or internal messages. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final com.matbakhy.app.security.ApiSecurityErrors securityErrors;

    ApiExceptionHandler(com.matbakhy.app.security.ApiSecurityErrors securityErrors) {
        this.securityErrors = securityErrors;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e, HttpServletRequest request) {
        log.info("API failure {}: {}", e.code(), e.getMessage());
        return respond(e.code(), e.fieldErrors(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldError(f.getField(), f.getCode() == null ? "INVALID" : f.getCode()))
                .sorted(java.util.Comparator.comparing(FieldError::field).thenComparing(FieldError::code))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, fields, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> invalidParameters(HandlerMethodValidationException e, HttpServletRequest request) {
        return respond(ErrorCode.VALIDATION_FAILED, List.of(), request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> malformed(Exception e, HttpServletRequest request) {
        return respond(ErrorCode.MALFORMED_REQUEST, List.of(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(NoResourceFoundException e, HttpServletRequest request) {
        return respond(ErrorCode.NOT_FOUND, List.of(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, List.of(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> mediaType(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, List.of(), request);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<ApiError> denied(org.springframework.security.access.AccessDeniedException e, HttpServletRequest request) {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        ErrorCode code = com.matbakhy.app.security.ApiSecurityErrors.denialCode(authentication, e);
        securityErrors.auditDenial(request, authentication, code);
        return respond(code, List.of(), request);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    ResponseEntity<ApiError> unauthenticated(org.springframework.security.core.AuthenticationException e, HttpServletRequest request) {
        return respond(ErrorCode.UNAUTHENTICATED, List.of(), request);
    }

    /**
     * The database (or another data resource) is unreachable: a retryable 503 without internal detail, so clients back
     * off and retry and operators see a dependency outage rather than a code defect (MB-037).
     */
    @ExceptionHandler(org.springframework.dao.DataAccessResourceFailureException.class)
    ResponseEntity<ApiError> dependencyUnavailable(org.springframework.dao.DataAccessResourceFailureException e, HttpServletRequest request) {
        log.warn("Data resource unavailable ({})", e.getClass().getSimpleName());
        return respond(ErrorCode.DEPENDENCY_UNAVAILABLE, List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled failure", e);
        return respond(ErrorCode.INTERNAL_ERROR, List.of(), request);
    }

    private static ResponseEntity<ApiError> respond(ErrorCode code, List<FieldError> fields, HttpServletRequest request) {
        return ResponseEntity.status(code.httpStatus()).body(ApiError.of(code, fields, RequestIdFilter.current(request)));
    }
}
