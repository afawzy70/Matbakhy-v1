package com.matbakhy.app.security;

import com.matbakhy.app.web.RequestIdFilter;
import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Writes 401/403 in the standard API error body; never redirects API calls to a login page. */
@Component
public class ApiSecurityErrors implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final com.matbakhy.operations.api.AuditLog audit;

    ApiSecurityErrors(com.matbakhy.operations.api.AuditLog audit) {
        this.audit = audit;
    }

    /** TC-007: an authenticated actor denied a privileged operation leaves a redacted audit record. */
    public void auditDenial(HttpServletRequest request, Authentication authentication, ErrorCode code) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentAccount actor) {
            Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
            // Bounded so that a denial on an arbitrarily long path is still recorded: the action column holds 120 characters and
            // the object ID is indexed (MB-039).
            String uri = request.getRequestURI();
            audit.record(new com.matbakhy.operations.api.AuditLog.Entry(actor.id(), bounded("denied " + request.getMethod() + " " + uri, 120),
                    null, "endpoint", bounded(uri, 512), com.matbakhy.operations.api.AuditLog.Outcome.DENIED, code.name(),
                    requestId == null ? null : requestId.toString(), java.util.Map.of()));
        }
    }

    private static String bounded(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e) throws IOException {
        write(request, response, ErrorCode.UNAUTHENTICATED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ErrorCode code = denialCode(authentication, e);
        auditDenial(request, authentication, code);
        write(request, response, code);
    }

    public static ErrorCode denialCode(Authentication authentication, AccessDeniedException e) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentAccount account && !account.active()) {
            // Suspended, deletion-requested or deleted accounts: scoped denial without profile data.
            return ErrorCode.ACCOUNT_DISABLED;
        }
        if (e instanceof StepUpRequiredException) {
            return ErrorCode.STEP_UP_REQUIRED;
        }
        return ErrorCode.FORBIDDEN;
    }

    private static void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        Object id = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        String requestId = id instanceof String s ? "\"" + s + "\"" : "null";
        response.setStatus(code.httpStatus());
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code.name() + "\",\"messageKey\":\"" + code.messageKey()
                + "\",\"fieldErrors\":[],\"requestId\":" + requestId + ",\"retryable\":" + code.retryable() + "}");
    }
}
