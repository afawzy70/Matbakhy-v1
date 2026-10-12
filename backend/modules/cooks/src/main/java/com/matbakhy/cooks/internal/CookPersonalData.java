package com.matbakhy.cooks.internal;

import com.matbakhy.shared.privacy.PersonalDataEraser;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Cook accounts are not erased automatically (MB-039): applications, kitchens, verification documents and settlement
 * obligations need staff review under the food-business and retention policy (O06), so the request is put on hold.
 */
@Component
class CookPersonalData implements PersonalDataEraser {

    private final JdbcTemplate jdbc;

    CookPersonalData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String area() {
        return "cooks";
    }

    @Override
    public Optional<Blocker> blocker(UUID accountId) {
        Integer cookRecords = jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM cook_application WHERE user_id = ?) + (SELECT count(*) FROM kitchen WHERE owner_id = ?)""",
                Integer.class, accountId, accountId);
        return cookRecords != null && cookRecords > 0 ? Optional.of(new Blocker("COOK_ACCOUNT_REVIEW", false)) : Optional.empty();
    }

    @Override
    public Map<String, Integer> erase(UUID accountId) {
        return Map.of();
    }
}
