package com.matbakhy.app.support;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Keycloak (same pinned image as infra/compose.yaml) with a synthetic test realm. Secrets and
 * passwords are generated per JVM run and never committed. The app port is fixed per run so the
 * provider's exact redirect-URI allowlist can be used (no wildcards).
 */
public final class KeycloakTestServer {

    public static final String IMAGE =
            "keycloak/keycloak:26.8.0@sha256:27b3230fbabb8c9ff3c66c864cf09dcd8a41aca82e5100679d109ef0eeb05741";
    /**
     * Developer convenience, never set in CI: -Dmatbakhy.test.keycloak.url=http://127.0.0.1:18080 uses an already running local
     * Keycloak (no Docker) that was imported with the same realm file; the matching values come from -Dmatbakhy.test.kc.<name>.
     */
    private static final String EXTERNAL = System.getProperty("matbakhy.test.keycloak.url");
    public static final String WEB_SECRET = value("web", random());
    public static final String PASSWORD_A = value("passwordA", random());
    public static final String PASSWORD_B = value("passwordB", random());
    /** Secret of the realm's admin service-account client (used by the API for the audited MFA reset). */
    public static final String ADMIN_SECRET = value("admin", random());
    public static final String MFA_PASSWORD = value("mfaPassword", random());
    /** Per-run synthetic TOTP secret for the dedicated test staff account; never committed. */
    public static final String MFA_TOTP_SECRET = value("totp", random().replace("t-", "").substring(0, 20));
    public static final int APP_PORT = freePort();

    private static final GenericContainer<?> CONTAINER = start();

    private KeycloakTestServer() {
    }

    private static String value(String name, String generated) {
        return EXTERNAL == null ? generated : System.getProperty("matbakhy.test.kc." + name, generated);
    }

    public static String baseUrl() {
        return EXTERNAL != null ? EXTERNAL : "http://localhost:" + CONTAINER.getMappedPort(8080);
    }

    public static String issuer() {
        return baseUrl() + "/realms/matbakhy-test";
    }

    public static Map<String, String> springProperties() {
        return Map.of(
                "matbakhy.identity.issuer", issuer(),
                "matbakhy.identity.web-client-secret", WEB_SECRET,
                "matbakhy.mfa.admin-client-id", "matbakhy-admin",
                "matbakhy.mfa.admin-client-secret", ADMIN_SECRET,
                "matbakhy.identity.post-login-redirect", "http://localhost:5173/",
                "server.port", Integer.toString(APP_PORT));
    }

    private static GenericContainer<?> start() {
        if (EXTERNAL != null) {
            return null;
        }
        String realm;
        try (var in = KeycloakTestServer.class.getResourceAsStream("/keycloak/test-realm.json")) {
            realm = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("${WEB_SECRET}", WEB_SECRET).replace("${PASSWORD_A}", PASSWORD_A)
                    .replace("${PASSWORD_B}", PASSWORD_B).replace("${MFA_PASSWORD}", MFA_PASSWORD).replace("${ADMIN_SECRET}", ADMIN_SECRET)
                    .replace("${MFA_TOTP_SECRET}", MFA_TOTP_SECRET).replace("${APP_PORT}", Integer.toString(APP_PORT));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("keycloak/keycloak"))
                .withCommand("start-dev", "--import-realm")
                .withEnv("KC_HEALTH_ENABLED", "true")
                .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "test-admin")
                .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", random())
                .withCopyToContainer(Transferable.of(realm), "/opt/keycloak/data/import/matbakhy-test-realm.json")
                .withExposedPorts(8080, 9000)
                .waitingFor(Wait.forHttp("/health/ready").forPort(9000).withStartupTimeout(Duration.ofMinutes(3)));
        container.start();
        return container;
    }

    private static String random() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return "t-" + HexFormat.of().formatHex(bytes);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
