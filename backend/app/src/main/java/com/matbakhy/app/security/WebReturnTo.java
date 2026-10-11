package com.matbakhy.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lets each first-party web app (customer, operations) get the user back after OIDC login. The login start
 * ({@code /oauth2/authorization/...?returnTo=}) remembers the target in the session only when its origin is exactly
 * one of the configured web origins and it has an absolute path; anything else falls back to the default landing
 * page, so the API is never an open redirect.
 */
final class WebReturnTo {

    static final String ATTRIBUTE = "matbakhy.returnTo";

    private WebReturnTo() {
    }

    static Optional<String> validate(String candidate, List<String> allowedOrigins) {
        if (candidate == null || candidate.length() > 2000) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(candidate);
            if (uri.getScheme() == null || uri.getRawAuthority() == null || uri.getRawUserInfo() != null || uri.getRawPath() == null
                    || !uri.getRawPath().startsWith("/") || uri.getRawPath().startsWith("//")) {
                return Optional.empty();
            }
            String origin = uri.getScheme().toLowerCase() + "://" + uri.getRawAuthority().toLowerCase();
            return allowedOrigins.stream().anyMatch(o -> o.equalsIgnoreCase(origin)) ? Optional.of(uri.normalize().toString()) : Optional.empty();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    static OncePerRequestFilter capture(List<String> allowedOrigins) {
        return new OncePerRequestFilter() {
            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                return !request.getRequestURI().startsWith("/oauth2/authorization/");
            }

            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                Optional<String> target = validate(request.getParameter("returnTo"), allowedOrigins);
                if (target.isPresent()) {
                    request.getSession(true).setAttribute(ATTRIBUTE, target.get());
                } else if (request.getSession(false) != null) {
                    request.getSession(false).removeAttribute(ATTRIBUTE);
                }
                chain.doFilter(request, response);
            }
        };
    }

    static AuthenticationSuccessHandler successHandler(String defaultTarget) {
        return (request, response, authentication) -> {
            String target = defaultTarget;
            if (request.getSession(false) != null && request.getSession(false).getAttribute(ATTRIBUTE) instanceof String saved) {
                target = saved;
                request.getSession(false).removeAttribute(ATTRIBUTE);
            }
            response.sendRedirect(target);
        };
    }
}
