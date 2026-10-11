package com.matbakhy.app.providers;

import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Object storage settings. {@code endpoint} is how the API reaches storage; {@code clientEndpoint} is the address
 * embedded in signed URLs for browsers and apps (they differ inside container networks).
 */
@Validated
@ConfigurationProperties("matbakhy.storage")
public record StorageProperties(URI endpoint, URI clientEndpoint, @NotBlank String region, String accessKey, String secretKey,
                                @NotBlank String privateBucket, @NotBlank String publicBucket, URI publicBaseUrl) {

    URI signingEndpoint() {
        return present(clientEndpoint) ? clientEndpoint : endpoint;
    }

    boolean hasPublicBaseUrl() {
        return present(publicBaseUrl);
    }

    private static boolean present(URI uri) {
        return uri != null && !uri.toString().isBlank();
    }
}
