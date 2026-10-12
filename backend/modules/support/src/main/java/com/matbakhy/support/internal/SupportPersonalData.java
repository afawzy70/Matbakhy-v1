package com.matbakhy.support.internal;

import com.matbakhy.shared.privacy.PersonalDataEraser;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Account erasure for support (MB-039): the customer's own case descriptions and messages are replaced by an erasure
 * marker. Case references, categories, states, resolutions and staff records stay for operations and audit.
 */
@Component
class SupportPersonalData implements PersonalDataEraser {

    static final String ERASED = "[erased on account deletion]";

    private final JdbcTemplate jdbc;

    SupportPersonalData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String area() {
        return "support";
    }

    @Override
    public Map<String, Integer> erase(UUID accountId) {
        int cases = jdbc.update("UPDATE support_case SET description = ?, cancellation_reason = NULL, updated_at = now() WHERE customer_id = ? AND description <> ?",
                ERASED, accountId, ERASED);
        int messages = jdbc.update("UPDATE case_message SET body = ? WHERE author_id = ? AND author_role = 'CUSTOMER' AND body <> ?", ERASED, accountId, ERASED);
        return Map.of("caseDescriptions", cases, "customerMessages", messages);
    }
}
