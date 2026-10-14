package com.matbakhy.identity.internal;

import com.matbakhy.operations.api.AuditLog;
import com.matbakhy.operations.api.AuditLog.Entry;
import com.matbakhy.operations.api.AuditLog.Outcome;
import com.matbakhy.shared.privacy.PersonalDataEraser;
import com.matbakhy.shared.privacy.PersonalDataEraser.Blocker;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executes due account deletion requests (MB-039, TC-055) under the PROPOSED retention default, pending O06: after the
 * grace period, and only when no module blocks it, every module erases its personal data and the account itself loses
 * its name, phone, sessions and sign-in link (a new sign-in creates a new empty account). All of this happens in one
 * transaction with an audit record of counts only. Balanced finance records and order amounts are kept.
 */
@Service
public class AccountErasureService {

    public record RunResult(int erased, int waiting, int onHold) {
    }

    private final JdbcTemplate jdbc;
    private final List<PersonalDataEraser> erasers;
    private final AuditLog audit;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final Duration grace;

    AccountErasureService(JdbcTemplate jdbc, List<PersonalDataEraser> erasers, AuditLog audit, Clock clock, TransactionTemplate tx,
                          @Value("${matbakhy.privacy.deletion-grace:P14D}") Duration grace) {
        this.jdbc = jdbc;
        this.erasers = erasers;
        this.audit = audit;
        this.clock = clock;
        this.tx = tx;
        this.grace = grace;
    }

    public RunResult eraseDue() {
        List<Map<String, Object>> due = jdbc.queryForList("""
                SELECT id, user_id, policy_version FROM deletion_request WHERE state = 'RECEIVED' AND requested_at <= ? ORDER BY requested_at LIMIT 50""",
                Timestamp.from(clock.instant().minus(grace)));
        int erased = 0;
        int waiting = 0;
        int onHold = 0;
        for (Map<String, Object> request : due) {
            String result = tx.execute(status -> eraseOne((UUID) request.get("id"), (UUID) request.get("user_id"), (String) request.get("policy_version")));
            switch (result == null ? "" : result) {
                case "ERASED" -> erased++;
                case "ON_HOLD" -> onHold++;
                default -> waiting++;
            }
        }
        return new RunResult(erased, waiting, onHold);
    }

    private String eraseOne(UUID requestId, UUID accountId, String policyVersion) {
        // Lock the request so two runs (or two instances) cannot erase the same account concurrently.
        if (jdbc.queryForList("SELECT id FROM deletion_request WHERE id = ? AND state = 'RECEIVED' FOR UPDATE SKIP LOCKED", requestId).isEmpty()) {
            return "SKIPPED";
        }
        for (PersonalDataEraser eraser : erasers) {
            Optional<Blocker> blocker = eraser.blocker(accountId);
            if (blocker.isPresent()) {
                if (blocker.get().retryLater()) {
                    return "WAITING";
                }
                jdbc.update("UPDATE deletion_request SET state = 'ON_HOLD', legal_hold_reason = ? WHERE id = ?", blocker.get().reason(), requestId);
                audit.record(new Entry(null, "account.erasure", null, "account", accountId.toString(), Outcome.DENIED, blocker.get().reason(), null,
                        Map.of("request", requestId.toString())));
                return "ON_HOLD";
            }
        }
        Map<String, Object> counts = new LinkedHashMap<>();
        for (PersonalDataEraser eraser : erasers) {
            eraser.erase(accountId).forEach((kind, n) -> counts.put(eraser.area() + "." + kind, n));
        }
        String subject = jdbc.queryForObject("SELECT subject FROM app_user WHERE id = ?", String.class, accountId);
        // Web sessions are indexed by the account id (AccountAuthentication#getName); the subject form is covered too.
        counts.put("account.sessions", jdbc.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME IN (?, ?)", accountId.toString(), subject));
        jdbc.update("""
                UPDATE app_user SET subject = ?, display_name = NULL, phone_e164 = NULL, phone_verified_at = NULL, email_verified = false,
                       status = 'DELETED', updated_at = now(), version = version + 1 WHERE id = ?""", "erased:" + accountId, accountId);
        jdbc.update("UPDATE deletion_request SET state = 'COMPLETED', completed_at = ? WHERE id = ?", Timestamp.from(clock.instant()), requestId);
        counts.put("policyVersion", policyVersion);
        audit.record(new Entry(null, "account.erasure", null, "account", accountId.toString(), Outcome.ALLOWED, "deletion request " + requestId,
                null, counts));
        return "ERASED";
    }
}
