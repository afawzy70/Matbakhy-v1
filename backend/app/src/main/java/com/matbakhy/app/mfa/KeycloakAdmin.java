package com.matbakhy.app.mfa;

import com.matbakhy.app.security.IdentityProperties;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ErrorCode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Minimal Keycloak admin REST client (service account, client-credentials). It only lists credential ids/types, deletes
 * OTP credentials and ends a user's sessions. It never reads or returns credential secrets: Keycloak does not expose
 * them and this class never asks for them. Error bodies are not logged (they can echo identifiers).
 */
@Component
public class KeycloakAdmin {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdmin.class);

    private final MfaProperties props;
    private final IdentityProperties identity;
    private final JsonMapper json;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile String token;
    private volatile Instant tokenExpiresAt = Instant.MIN;

    KeycloakAdmin(MfaProperties props, IdentityProperties identity, JsonMapper json, Clock clock) {
        this.props = props;
        this.identity = identity;
        this.json = json;
        this.clock = clock;
    }

    public boolean configured() {
        return props.adminConfigured();
    }

    /** {@code http://host/realms/x} -> {@code http://host/admin/realms/x}. */
    private String adminBase() {
        String issuer = identity.backchannelIssuer();
        int at = issuer.indexOf("/realms/");
        if (at < 0) {
            throw unavailable();
        }
        return issuer.substring(0, at) + "/admin" + issuer.substring(at);
    }

    private static ApiException unavailable() {
        return new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "identity provider admin access unavailable");
    }

    private synchronized String accessToken() {
        if (!configured()) {
            throw unavailable();
        }
        if (token != null && clock.instant().isBefore(tokenExpiresAt.minusSeconds(30))) {
            return token;
        }
        try {
            String basic = Base64.getEncoder().encodeToString((props.adminClientId() + ":" + props.adminClientSecret()).getBytes(StandardCharsets.UTF_8));
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(identity.backchannel("token")))
                    .header("Authorization", "Basic " + basic).header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(10)).POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials")).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("identity provider admin token request failed with status {}", response.statusCode());
                throw unavailable();
            }
            JsonNode body = json.readTree(response.body());
            token = body.get("access_token").asString();
            tokenExpiresAt = clock.instant().plusSeconds(body.has("expires_in") ? body.get("expires_in").asLong() : 60);
            return token;
        } catch (IOException e) {
            log.warn("identity provider admin token request failed ({})", e.getClass().getSimpleName());
            throw unavailable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    private HttpResponse<String> call(String method, String path, String body) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(adminBase() + path)).timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + accessToken());
            if (body == null) {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
            }
            return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.warn("identity provider admin call failed ({})", e.getClass().getSimpleName());
            throw unavailable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Ids of the user's OTP credentials (type "otp"); nothing else about them is read. */
    public List<String> otpCredentialIds(String subject) {
        HttpResponse<String> response = call("GET", "/users/" + enc(subject) + "/credentials", null);
        if (response.statusCode() == 404) {
            return List.of();
        }
        if (response.statusCode() != 200) {
            log.warn("identity provider credential listing failed with status {}", response.statusCode());
            throw unavailable();
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode credential : json.readTree(response.body())) {
            if (credential.has("type") && "otp".equals(credential.get("type").asString()) && credential.has("id")) {
                ids.add(credential.get("id").asString());
            }
        }
        return ids;
    }

    public void deleteCredential(String subject, String credentialId) {
        HttpResponse<String> response = call("DELETE", "/users/" + enc(subject) + "/credentials/" + enc(credentialId), null);
        if (response.statusCode() != 204 && response.statusCode() != 404) {
            log.warn("identity provider credential removal failed with status {}", response.statusCode());
            throw unavailable();
        }
    }

    /** Ends every session of the user at the provider (refresh tokens stop working; access tokens expire within minutes). */
    public void logout(String subject) {
        HttpResponse<String> response = call("POST", "/users/" + enc(subject) + "/logout", "{}");
        if (response.statusCode() != 204 && response.statusCode() != 404) {
            log.warn("identity provider session termination failed with status {}", response.statusCode());
            throw unavailable();
        }
    }
}
