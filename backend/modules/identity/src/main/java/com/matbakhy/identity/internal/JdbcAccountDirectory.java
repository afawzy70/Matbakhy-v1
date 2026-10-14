package com.matbakhy.identity.internal;

import com.matbakhy.identity.api.AccountDirectory;
import com.matbakhy.identity.api.AccountStatus;
import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.identity.api.ExternalIdentity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class JdbcAccountDirectory implements AccountDirectory {

    static final String COLUMNS = "id, status, display_name, locale, email_verified, phone_verified_at, version";

    private final JdbcTemplate jdbc;

    JdbcAccountDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public CurrentAccount resolve(ExternalIdentity identity) {
        Optional<CurrentAccount> existing = jdbc.query("SELECT " + COLUMNS + " FROM app_user WHERE issuer = ? AND subject = ?",
                JdbcAccountDirectory::map, identity.issuer(), identity.subject()).stream().findFirst();
        if (existing.isPresent() && existing.get().emailVerified() == identity.emailVerified()) {
            return existing.get();
        }
        // Insert-or-refresh in one statement so concurrent first requests cannot create duplicates.
        // Display name and locale are only taken from the provider on creation; the user owns them afterwards.
        return jdbc.queryForObject("""
                INSERT INTO app_user (id, issuer, subject, email_verified, display_name, locale)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (issuer, subject) DO UPDATE
                    SET email_verified = EXCLUDED.email_verified,
                        updated_at = CASE WHEN app_user.email_verified <> EXCLUDED.email_verified THEN now() ELSE app_user.updated_at END
                RETURNING %s""".formatted(COLUMNS),
                JdbcAccountDirectory::map,
                UUID.randomUUID(), identity.issuer(), identity.subject(), identity.emailVerified(),
                sanitizeName(identity.displayName()), "en".equals(identity.locale()) ? "en" : "ar");
    }

    @Override
    public Optional<CurrentAccount> find(UUID accountId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM app_user WHERE id = ?", JdbcAccountDirectory::map, accountId)
                .stream().findFirst();
    }

    static CurrentAccount map(ResultSet rs, int row) throws SQLException {
        return new CurrentAccount(rs.getObject("id", UUID.class), AccountStatus.valueOf(rs.getString("status")),
                rs.getString("display_name"), rs.getString("locale"), rs.getBoolean("email_verified"),
                rs.getTimestamp("phone_verified_at") != null, rs.getLong("version"));
    }

    private static String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.strip();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }
}
