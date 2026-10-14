package com.matbakhy.notifications.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Push tokens: AES-GCM ciphertext for sending, keyed hash for uniqueness lookups. */
@Component
@EnableConfigurationProperties(NotificationProperties.class)
class TokenVault {

    record Sealed(String hash, byte[] ciphertext, byte[] nonce) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] hashKey;
    private final SecretKeySpec key;

    TokenVault(NotificationProperties props) {
        this.hashKey = props.tokenHashKey().getBytes(StandardCharsets.UTF_8);
        byte[] k;
        try {
            k = props.tokenEncryptionKey().startsWith(NotificationProperties.DEV_PREFIX)
                    ? MessageDigest.getInstance("SHA-256").digest(props.tokenEncryptionKey().getBytes(StandardCharsets.UTF_8))
                    : Base64.getDecoder().decode(props.tokenEncryptionKey());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
        if (k.length != 32) {
            throw new IllegalStateException("PUSH_TOKEN_ENCRYPTION_KEY must decode to 32 bytes");
        }
        this.key = new SecretKeySpec(k, "AES");
    }

    String hash(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    Sealed seal(String token) {
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            return new Sealed(hash(token), c.doFinal(token.getBytes(StandardCharsets.UTF_8)), nonce);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    String open(byte[] ciphertext, byte[] nonce) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            return new String(c.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
