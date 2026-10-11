package com.matbakhy.operations.api;

import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit trail (BR-007). Details are redacted before storage: secrets, codes, tokens,
 * phone numbers and email addresses never reach the audit table.
 */
public interface AuditLog {

    enum Outcome { ALLOWED, DENIED }

    record Entry(UUID actorId, String action, String permission, String objectType, String objectId, Outcome outcome,
                 String reason, String requestId, Map<String, Object> details) {
    }

    void record(Entry entry);
}
