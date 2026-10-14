package com.matbakhy.app.support;

import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal browser for tests: keeps cookies, does not follow redirects automatically and submits the
 * provider's real login form. Used to exercise genuine authorization-code flows end to end.
 */
public final class OidcBrowser {

    private static final Pattern FORM_ACTION = Pattern.compile("<form[^>]*id=\"kc-form-login\"[^>]*action=\"([^\"]+)\"");

    private final LocalCookieJar cookies = new LocalCookieJar();
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies)
            .followRedirects(HttpClient.Redirect.NEVER).build();

    /**
     * Test-only cookie jar for http://localhost: unlike java.net.CookieManager it also returns cookies
     * marked Secure (browsers treat localhost as a secure context). Honors cookie path and deletion.
     */
    static final class LocalCookieJar extends java.net.CookieHandler {
        private final java.util.Map<String, HttpCookie> store = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public java.util.Map<String, java.util.List<String>> get(URI uri, java.util.Map<String, java.util.List<String>> headers) {
            String path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
            String value = store.values().stream()
                    .filter(c -> path.startsWith(c.getPath() == null ? "/" : c.getPath()))
                    .map(c -> c.getName() + "=" + c.getValue())
                    .collect(java.util.stream.Collectors.joining("; "));
            return value.isEmpty() ? java.util.Map.of() : java.util.Map.of("Cookie", java.util.List.of(value));
        }

        @Override
        public void put(URI uri, java.util.Map<String, java.util.List<String>> headers) {
            headers.forEach((name, values) -> {
                if (name != null && name.equalsIgnoreCase("Set-Cookie")) {
                    for (String header : values) {
                        for (HttpCookie cookie : HttpCookie.parse(header)) {
                            String key = cookie.getName() + "|" + (cookie.getPath() == null ? "/" : cookie.getPath());
                            if (cookie.getMaxAge() == 0 || cookie.getValue().isEmpty()) {
                                store.remove(key);
                            } else {
                                store.put(key, cookie);
                            }
                        }
                    }
                }
            });
        }

        java.util.Optional<String> value(String name) {
            return store.values().stream().filter(c -> c.getName().equals(name)).map(HttpCookie::getValue).findFirst();
        }
    }

    public HttpResponse<String> get(String url) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** Follows redirects until the location starts with {@code stopPrefix} or a non-redirect response. */
    public String followUntil(String url, String stopPrefix) throws IOException, InterruptedException {
        String current = url;
        for (int i = 0; i < 10; i++) {
            if (current.startsWith(stopPrefix)) {
                return current;
            }
            HttpResponse<String> response = get(current);
            Optional<String> location = response.headers().firstValue("Location");
            if (location.isEmpty()) {
                return current;
            }
            current = URI.create(current).resolve(location.get()).toString();
        }
        throw new IllegalStateException("Too many redirects");
    }

    /** Opens the authorization URL, submits credentials and returns the final redirect location. */
    public String login(String authorizationUrl, String username, String password) throws IOException, InterruptedException {
        HttpResponse<String> page = get(authorizationUrl);
        if (page.statusCode() != 200) {
            throw new IllegalStateException("Login page not shown: " + page.statusCode() + " " + page.headers().firstValue("Location"));
        }
        Matcher action = FORM_ACTION.matcher(page.body());
        if (!action.find()) {
            throw new IllegalStateException("Login form not found");
        }
        String form = "username=" + enc(username) + "&password=" + enc(password) + "&credentialId=";
        HttpResponse<String> submitted = http.send(HttpRequest.newBuilder(URI.create(action.group(1).replace("&amp;", "&")))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        return submitted.headers().firstValue("Location")
                .orElseThrow(() -> new IllegalStateException("Login rejected: " + submitted.statusCode()));
    }

    public Optional<String> cookie(String name) {
        return cookies.value(name);
    }

    public static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static String randomVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String queryParam(String url, String name) {
        Matcher m = Pattern.compile("[?&#]" + Pattern.quote(name) + "=([^&]*)").matcher(url);
        return m.find() ? java.net.URLDecoder.decode(m.group(1), StandardCharsets.UTF_8) : null;
    }
}
