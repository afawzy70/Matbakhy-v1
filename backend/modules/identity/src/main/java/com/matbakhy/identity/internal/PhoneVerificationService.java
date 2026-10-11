package com.matbakhy.identity.internal;

import com.matbakhy.identity.api.EgyptianPhone;
import com.matbakhy.identity.api.PhoneVerificationSender;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Phone ownership verification (BR-003, BR-004). Codes are 6 random digits, stored only as an HMAC,
 * single use, time-limited and attempt-limited. Responses never reveal whether a code was wrong,
 * expired or already used. The code is sent only after the challenge row commits.
 */
@Service
public class PhoneVerificationService {

    private static final Logger log = LoggerFactory.getLogger(PhoneVerificationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Challenge(UUID id, Instant expiresAt, Instant resendAvailableAt) {
    }

    private final JdbcTemplate jdbc;
    private final PhoneVerificationSender sender;
    private final PhoneVerificationProperties properties;
    private final Clock clock;

    PhoneVerificationService(JdbcTemplate jdbc, PhoneVerificationSender sender, PhoneVerificationProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.sender = sender;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    Challenge start(UUID accountId, String rawPhone, String locale) {
        String phone = EgyptianPhone.normalize(rawPhone).orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                "phone not an Egyptian mobile number", List.of(new FieldError("phoneE164", "EGYPTIAN_MOBILE"))));
        // Serialize sends per account so concurrent requests cannot bypass the resend interval.
        jdbc.queryForObject("SELECT id FROM app_user WHERE id = ? FOR UPDATE", UUID.class, accountId);
        Instant now = clock.instant();
        Timestamp hourAgo = Timestamp.from(now.minusSeconds(3600));
        Map<String, Object> recent = jdbc.queryForMap("""
                SELECT count(*) AS sends, max(created_at) AS last FROM phone_challenge WHERE user_id = ? AND created_at > ?""",
                accountId, hourAgo);
        Timestamp last = (Timestamp) recent.get("last");
        if (last != null && last.toInstant().plus(properties.resendInterval()).isAfter(now)) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "resend interval");
        }
        long accountSends = ((Number) recent.get("sends")).longValue();
        Long phoneSends = jdbc.queryForObject("SELECT count(*) FROM phone_challenge WHERE phone_e164 = ? AND created_at > ?",
                Long.class, phone, hourAgo);
        if (accountSends >= properties.maxSendsPerAccountPerHour() || phoneSends >= properties.maxSendsPerPhonePerHour()) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "hourly send limit");
        }
        // BR-003: declaring a different number replaces the old one and clears verification until confirmed.
        jdbc.update("""
                UPDATE app_user SET phone_e164 = ?, phone_verified_at = NULL, version = version + 1, updated_at = now()
                WHERE id = ? AND phone_e164 IS DISTINCT FROM ?""", phone, accountId, phone);
        UUID id = UUID.randomUUID();
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant expires = now.plus(properties.codeTtl());
        jdbc.update("INSERT INTO phone_challenge (id, user_id, phone_e164, code_hmac, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, accountId, phone, hmac(id, code), Timestamp.from(now), Timestamp.from(expires));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sender.send(phone, code, locale);
            }
        });
        log.info("Phone verification challenge {} issued", id);
        return new Challenge(id, expires, now.plus(properties.resendInterval()));
    }

    /** Returns normally only when the code matched an unexpired, unused challenge owned by the caller. */
    @Transactional(noRollbackFor = ApiException.class)
    void verify(UUID accountId, UUID challengeId, String code) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT phone_e164, code_hmac, expires_at, attempts, consumed_at FROM phone_challenge
                WHERE id = ? AND user_id = ? FOR UPDATE""", challengeId, accountId);
        if (rows.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "challenge not found for caller");
        }
        Map<String, Object> c = rows.getFirst();
        int attempts = ((Number) c.get("attempts")).intValue();
        if (attempts >= properties.maxAttempts()) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "challenge locked " + challengeId);
        }
        jdbc.update("UPDATE phone_challenge SET attempts = attempts + 1 WHERE id = ?", challengeId);
        boolean expired = !((Timestamp) c.get("expires_at")).toInstant().isAfter(clock.instant());
        boolean used = c.get("consumed_at") != null;
        boolean matches = code != null && code.matches("[0-9]{6}") && MessageDigest.isEqual(
                hmac(challengeId, code).getBytes(StandardCharsets.US_ASCII),
                ((String) c.get("code_hmac")).getBytes(StandardCharsets.US_ASCII));
        if (expired || used || !matches) {
            log.info("Phone verification failed for challenge {}", challengeId);
            throw new ApiException(ErrorCode.VERIFICATION_FAILED, "verification failed " + challengeId);
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("UPDATE phone_challenge SET consumed_at = ? WHERE id = ?", now, challengeId);
        int updated = jdbc.update("""
                UPDATE app_user SET phone_verified_at = ?, version = version + 1, updated_at = now()
                WHERE id = ? AND phone_e164 = ?""", now, accountId, c.get("phone_e164"));
        if (updated == 0) {
            // The account declared another number after this challenge was issued; this code no longer proves it.
            throw new ApiException(ErrorCode.VERIFICATION_FAILED, "number changed since challenge " + challengeId);
        }
        log.info("Phone verified via challenge {}", challengeId);
    }

    private String hmac(UUID challengeId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.codePepper().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((challengeId + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
