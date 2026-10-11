package com.matbakhy.orders.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Six-digit handover codes: cryptographically random, verified by an HMAC bound to the order, displayed to the
 * order owner by decrypting an AES-GCM ciphertext (associated data = order id). The plain code is never stored.
 */
@Component
@EnableConfigurationProperties(HandoverProperties.class)
public class HandoverCodes {

    public record Sealed(String hash, byte[] ciphertext, byte[] nonce, String code) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] hashKey;
    private final SecretKeySpec encryptionKey;

    HandoverCodes(HandoverProperties props) {
        this.hashKey = props.hashKey().getBytes(StandardCharsets.UTF_8);
        byte[] key = props.encryptionKey().startsWith(HandoverProperties.DEV_PREFIX)
                ? sha256(props.encryptionKey().getBytes(StandardCharsets.UTF_8)) : Base64.getDecoder().decode(props.encryptionKey());
        if (key.length != 32) {
            throw new IllegalStateException("HANDOVER_ENCRYPTION_KEY must decode to 32 bytes");
        }
        this.encryptionKey = new SecretKeySpec(key, "AES");
    }

    public Sealed issue(UUID orderId) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(orderId.toString().getBytes(StandardCharsets.UTF_8));
            return new Sealed(hash(orderId, code), cipher.doFinal(code.getBytes(StandardCharsets.UTF_8)), nonce, code);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("handover code encryption failed", e);
        }
    }

    public String reveal(UUID orderId, byte[] ciphertext, byte[] nonce) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(orderId.toString().getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("handover code decryption failed", e);
        }
    }

    /** Constant-time comparison of a presented code against the stored hash. */
    public boolean matches(UUID orderId, String presented, String storedHash) {
        if (presented == null || !presented.matches("[0-9]{6}")) {
            return false;
        }
        return MessageDigest.isEqual(hash(orderId, presented).getBytes(StandardCharsets.US_ASCII), storedHash.getBytes(StandardCharsets.US_ASCII));
    }

    private String hash(UUID orderId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((orderId + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
