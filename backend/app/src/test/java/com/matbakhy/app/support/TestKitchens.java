package com.matbakhy.app.support;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test fixture: an approved kitchen for an existing account, inserted directly (the review workflow itself is
 * covered by CookWorkflowIntegrationTest). Synthetic data only.
 */
public final class TestKitchens {

    private TestKitchens() {
    }

    public static UUID approvedKitchen(JdbcTemplate jdbc, UUID ownerId, UUID reviewerId) {
        UUID application = UUID.randomUUID();
        UUID kitchen = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cook_application (id, user_id, state, public_name, bio, contact_phone, private_location, pickup_enabled,
                                              current_revision)
                VALUES (?, ?, 'APPROVED', 'Synthetic Kitchen', 'Synthetic fixture kitchen', '+201001234567', 'Synthetic fixture location', true, 1)""",
                application, ownerId);
        jdbc.update("""
                INSERT INTO cook_application_revision (application_id, revision, public_name, bio, contact_phone, private_location,
                                                       pickup_enabled, zone_ids, submitted_at, decision, decision_reason, reviewer_id, decided_at)
                VALUES (?, 1, 'Synthetic Kitchen', 'Synthetic fixture kitchen', '+201001234567', 'Synthetic fixture location', true, '{}',
                        now(), 'APPROVED', 'test fixture', ?, now())""", application, reviewerId);
        jdbc.update("""
                INSERT INTO kitchen (id, owner_id, application_id, approved_revision, public_slug, display_name, description, pickup_enabled,
                                     contact_phone, private_location)
                VALUES (?, ?, ?, 1, ?, 'Synthetic Kitchen', 'Synthetic fixture kitchen', true, '+201001234567', 'Synthetic fixture location')""",
                kitchen, ownerId, application, "kitchen-" + kitchen.toString().substring(0, 8));
        return kitchen;
    }
}
