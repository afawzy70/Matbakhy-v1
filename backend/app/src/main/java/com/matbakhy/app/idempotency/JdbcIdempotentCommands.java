package com.matbakhy.app.idempotency;

import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ErrorCode;
import com.matbakhy.shared.idempotency.IdempotentCommands;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * PostgreSQL-backed idempotency. The insert of the scope row blocks on a concurrent uncommitted insert of
 * the same scope, so two racing requests with one key execute the command once; the loser then reads the
 * committed result (or retries the command if the winner rolled back).
 */
@Component
class JdbcIdempotentCommands implements IdempotentCommands {

    static final Duration RETENTION = Duration.ofHours(24);
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9-]{8,128}");

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;

    JdbcIdempotentCommands(JdbcTemplate jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T execute(UUID actorId, String command, String idempotencyKey, Object request, Class<T> resultType, Supplier<T> action) {
        if (idempotencyKey == null || !KEY.matcher(idempotencyKey).matches()) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Idempotency-Key missing or malformed",
                    List.of(new ApiException.FieldError("Idempotency-Key", "REQUIRED")));
        }
        String hash = sha256(json.writeValueAsString(request));
        Timestamp now = Timestamp.from(clock.instant());
        int inserted = jdbc.update("""
                INSERT INTO idempotency_record (actor_id, command, idempotency_key, request_hash, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING""",
                actorId, command, idempotencyKey, hash, now, Timestamp.from(now.toInstant().plus(RETENTION)));
        if (inserted == 0) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT request_hash, response_snapshot::text AS snapshot FROM idempotency_record
                    WHERE actor_id = ? AND command = ? AND idempotency_key = ? FOR UPDATE""", actorId, command, idempotencyKey);
            if (rows.isEmpty()) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, "idempotency record vanished");
            }
            if (!hash.equals(rows.getFirst().get("request_hash"))) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency-Key reused with a different request");
            }
            Object snapshot = rows.getFirst().get("snapshot");
            if (snapshot == null) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, "original request has no stored result");
            }
            return json.readValue(snapshot.toString(), resultType);
        }
        T result;
        try {
            result = action.get();
        } catch (RuntimeException failure) {
            // A failed command stores no outcome, even if the caller's transaction does not roll back. If the database
            // already aborted the transaction the rollback removes the record anyway; the original failure is kept.
            try {
                jdbc.update("DELETE FROM idempotency_record WHERE actor_id = ? AND command = ? AND idempotency_key = ?",
                        actorId, command, idempotencyKey);
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        jdbc.update("""
                UPDATE idempotency_record SET result_status = 200, response_snapshot = ?::jsonb
                WHERE actor_id = ? AND command = ? AND idempotency_key = ?""",
                json.writeValueAsString(result), actorId, command, idempotencyKey);
        return result;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
