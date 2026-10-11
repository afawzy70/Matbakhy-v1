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

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e) throws IOException {
        write(request, response, ErrorCode.UNAUTHENTICATED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) throws IOException {
        write(request, response, denialCode(SecurityContextHolder.getContext().getAuthentication(), e));
    }

    public static ErrorCode denialCode(Authentication authentication, AccessDeniedException e) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentAccount account && !account.active()) {
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
