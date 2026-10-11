package com.matbakhy.identity.internal;

import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ErrorCode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProfileService {

    private final JdbcTemplate jdbc;

    ProfileService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Optimistic update: only the caller's own row, only when the expected version still matches. */
    @Transactional
    CurrentAccount update(UUID accountId, long expectedVersion, String displayName, String locale) {
        return jdbc.query("""
                UPDATE app_user SET display_name = COALESCE(?, display_name), locale = COALESCE(?, locale),
                       version = version + 1, updated_at = now()
                WHERE id = ? AND version = ? AND status = 'ACTIVE'
                RETURNING %s""".formatted(JdbcAccountDirectory.COLUMNS),
                JdbcAccountDirectory::map, displayName, locale, accountId, expectedVersion)
                .stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.VERSION_CONFLICT, "profile version conflict"));
    }
}
