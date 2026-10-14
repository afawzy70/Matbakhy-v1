package com.matbakhy.catalog.internal;

import com.matbakhy.shared.privacy.PersonalDataEraser;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Account erasure for favorites (MB-039): the list is personal and is removed. */
@Component
class FavoritePersonalData implements PersonalDataEraser {

    private final JdbcTemplate jdbc;

    FavoritePersonalData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String area() {
        return "favorites";
    }

    @Override
    public Map<String, Integer> erase(UUID accountId) {
        return Map.of("favorites", jdbc.update("DELETE FROM favorite WHERE user_id = ?", accountId));
    }
}
