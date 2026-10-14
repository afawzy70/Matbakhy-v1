package com.matbakhy.cooks.internal;

import com.matbakhy.catalog.api.PrivateMediaAccess;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Cook verification documents become reviewable once frozen into a submitted revision, and from then on cannot
 * be deleted (revision history is preserved). Draft-only attachments remain private to the applicant.
 */
@Component
class ApplicationDocumentAccess implements PrivateMediaAccess {

    private final JdbcTemplate jdbc;

    ApplicationDocumentAccess(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean reviewable(UUID mediaId) {
        return referenced(mediaId);
    }

    @Override
    public boolean referenced(UUID mediaId) {
        Boolean found = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM cook_application_revision WHERE ? = ANY (document_ids))",
                Boolean.class, mediaId);
        return Boolean.TRUE.equals(found);
    }
}
