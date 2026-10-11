package com.matbakhy.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * For web sessions: replaces the stored OIDC login with an {@link AccountAuthentication} for the
 * current request only (not persisted), so every request re-reads account status from the database.
 */
class WebAccountFilter extends OncePerRequestFilter {

    private final AccountMapper mapper;

    WebAccountFilter(AccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current instanceof OAuth2AuthenticationToken token && token.getPrincipal() instanceof OidcUser user) {
            AccountAuthentication account = mapper.map(user.getIssuer().toString(), user.getSubject(), user.getClaims());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(account);
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
