package com.matbakhy.identity.internal;

import com.matbakhy.shared.privacy.PersonalDataEraser;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Account erasure for addresses and phone challenges (MB-039); address rows stay (quotes reference them) but empty. */
@Component
class ContactPersonalData implements PersonalDataEraser {

    private final JdbcTemplate jdbc;

    ContactPersonalData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String area() {
        return "contact";
    }

    @Override
    public Map<String, Integer> erase(UUID accountId) {
        int addresses = jdbc.update("""
                UPDATE address SET recipient_name = NULL, phone_e164 = NULL, street = NULL, building = NULL, floor = NULL, unit = NULL,
                       landmark = NULL, latitude = NULL, longitude = NULL, erased_at = now(), archived_at = coalesce(archived_at, now()),
                       updated_at = now(), version = version + 1
                WHERE user_id = ? AND erased_at IS NULL""", accountId);
        int challenges = jdbc.update("DELETE FROM phone_challenge WHERE user_id = ?", accountId);
        return Map.of("addresses", addresses, "phoneChallenges", challenges);
    }
}
