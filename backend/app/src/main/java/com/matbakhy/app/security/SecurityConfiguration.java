package com.matbakhy.app.security;

import com.matbakhy.app.config.MatbakhyProperties;
import com.matbakhy.app.config.MatbakhyProperties.DeploymentEnvironment;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Two chains (ADR-003):
 * <ol>
 *   <li>Native/API clients with {@code Authorization: Bearer}: stateless JWT validation (issuer, audience,
 *       signature, expiry). No cookies are read, so no CSRF token is needed.</li>
 *   <li>Web: OIDC authorization-code login with a server-side session (PostgreSQL), HttpOnly cookie,
 *       CSRF token on every mutation and a strict CORS allowlist.</li>
 * </ol>
 * Authorities come only from server-side state; token role claims are ignored.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(IdentityProperties.class)
class SecurityConfiguration {

    static final String REGISTRATION_ID = "keycloak";
    static final String DELETION_REQUEST = "/api/v1/me/deletion-requests";
    static final String[] PUBLIC_GET = {"/api/v1/configuration/public", "/api/v1/zones", "/api/v1/meals", "/api/v1/meals/*",
            "/api/v1/kitchens/*", "/api/v1/kitchens/*/slots", "/api/v1/kitchens/*/reviews", "/api/v1/policies/*"};

    @Bean
    JwtDecoder jwtDecoder(IdentityProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.backchannel("certs")).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,
                new JwtIssuerValidator(properties.issuer()), audience(properties.apiAudience())));
        return decoder;
    }

    private static org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt> audience(String expected) {
        OAuth2Error error = new OAuth2Error("invalid_token", "Token audience is not this API", null);
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(expected)
                ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(error);
    }

    @Bean
    ClientRegistrationRepository clientRegistrations(IdentityProperties properties, MatbakhyProperties platform) {
        boolean deployed = platform.environment() == DeploymentEnvironment.STAGING
                || platform.environment() == DeploymentEnvironment.PRODUCTION;
        if (deployed && (properties.webClientSecret() == null || properties.webClientSecret().isBlank())) {
            throw new IllegalStateException("OIDC_WEB_CLIENT_SECRET is required in " + platform.environment());
        }
        ClientRegistration web = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId(properties.webClientId())
                .clientSecret(properties.webClientSecret() == null ? "" : properties.webClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri(properties.endpoint("auth"))
                .tokenUri(properties.backchannel("token"))
                .jwkSetUri(properties.backchannel("certs"))
                .userInfoUri(properties.backchannel("userinfo"))
                .userNameAttributeName("sub")
                .issuerUri(properties.issuer())
                .providerConfigurationMetadata(Map.of("end_session_endpoint", properties.endpoint("logout")))
                .build();
        return new InMemoryClientRegistrationRepository(web);
    }

    @Bean
    OidcIdTokenDecoderFactory idTokenDecoderFactory(Clock clock) {
        OidcIdTokenDecoderFactory factory = new OidcIdTokenDecoderFactory();
        factory.setJwtValidatorFactory(registration -> {
            JwtTimestampValidator timestamps = new JwtTimestampValidator();
            timestamps.setClock(clock);
            return new DelegatingOAuth2TokenValidator<>(timestamps,
                    new org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator(registration));
        });
        return factory;
    }

    @Bean
    CookieSerializer sessionCookie(MatbakhyProperties platform) {
        DefaultCookieSerializer cookie = new DefaultCookieSerializer();
        cookie.setCookieName("MATBAKHY_SESSION");
        cookie.setUseHttpOnlyCookie(true);
        cookie.setSameSite("Lax");
        cookie.setCookiePath("/");
        DeploymentEnvironment env = platform.environment();
        cookie.setUseSecureCookie(env == DeploymentEnvironment.STAGING || env == DeploymentEnvironment.PRODUCTION);
        return cookie;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(IdentityProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Idempotency-Key", "If-Match", "X-Request-Id", "Authorization"));
        cors.setExposedHeaders(List.of("X-Request-Id", "Location"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @Bean
    @Order(1)
    SecurityFilterChain bearerChain(HttpSecurity http, AccountMapper mapper, ApiSecurityErrors errors) throws Exception {
        http.securityMatcher(request -> {
                    String header = request.getHeader("Authorization");
                    return header != null && header.regionMatches(true, 0, "Bearer ", 0, 7);
                })
                .cors(cors -> { })
                .csrf(csrf -> csrf.disable()) // bearer credentials are never sent ambiently by a browser
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers(HttpMethod.POST, DELETION_REQUEST).hasAnyAuthority(AccountMapper.ACTIVE, AccountMapper.DELETION_REQUESTED)
                        .requestMatchers("/api/**").hasAuthority(AccountMapper.ACTIVE)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> mapper.map(
                                token.getIssuer().toString(), token.getSubject(), token.getClaims())))
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain webChain(HttpSecurity http, AccountMapper mapper, ApiSecurityErrors errors,
                                 IdentityProperties properties, ClientRegistrationRepository registrations) throws Exception {
        http.cors(cors -> { })
                .csrf(csrf -> csrf.spa())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
                        .requestMatchers("/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers(HttpMethod.POST, DELETION_REQUEST).hasAnyAuthority(AccountMapper.ACTIVE, AccountMapper.DELETION_REQUESTED)
                        .requestMatchers("/api/**").hasAuthority(AccountMapper.ACTIVE)
                        .anyRequest().denyAll())
                .oauth2Login(login -> login.defaultSuccessUrl(properties.postLoginRedirect(), true))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler(idpLogout(properties, registrations))
                        .invalidateHttpSession(true)
                        .deleteCookies("MATBAKHY_SESSION"))
                .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .addFilterBefore(new WebAccountFilter(mapper), AuthorizationFilter.class);
        return http.build();
    }

    /** Responds 200 with the provider end-session URL so the web app can also end the IdP session. */
    private static LogoutSuccessHandler idpLogout(IdentityProperties properties, ClientRegistrationRepository registrations) {
        return (request, response, authentication) -> {
            String url = null;
            if (authentication instanceof OAuth2AuthenticationToken token && token.getPrincipal() instanceof OidcUser user) {
                ClientRegistration registration = ((InMemoryClientRegistrationRepository) registrations).findByRegistrationId(REGISTRATION_ID);
                url = UriComponentsBuilder.fromUriString(properties.endpoint("logout"))
                        .queryParam("client_id", registration.getClientId())
                        .queryParam("id_token_hint", user.getIdToken().getTokenValue())
                        .queryParam("post_logout_redirect_uri", properties.postLoginRedirect())
                        .encode().build().toUriString();
            }
            response.setStatus(200);
            response.setContentType("application/json");
            response.getOutputStream().write(("{\"idpLogoutUrl\":" + (url == null ? "null" : "\"" + url + "\"") + "}")
                    .getBytes(StandardCharsets.UTF_8));
        };
    }
}
