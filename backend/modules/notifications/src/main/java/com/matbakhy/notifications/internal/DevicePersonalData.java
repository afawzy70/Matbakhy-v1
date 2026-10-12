package com.matbakhy.notifications.internal;

import com.matbakhy.shared.privacy.PersonalDataEraser;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Account erasure for notifications (MB-039): device registrations (encrypted push tokens) are deleted and pending
 * deliveries to the account are cancelled. Inbox rows carry only order numbers and states and remain for order history.
 */
@Component
class DevicePersonalData implements PersonalDataEraser {

    private final JdbcTemplate jdbc;

    DevicePersonalData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String area() {
        return "notifications";
    }

    @Override
    public Map<String, Integer> erase(UUID accountId) {
        int pending = jdbc.update("""
                UPDATE notification_delivery SET status = 'SKIPPED', last_error = 'ACCOUNT_ERASED', lease_owner = NULL, leased_until = NULL
                WHERE status = 'PENDING' AND notification_id IN (SELECT id FROM notification WHERE recipient_id = ?)""", accountId);
        int devices = jdbc.update("DELETE FROM device_registration WHERE user_id = ?", accountId);
        return Map.of("devices", devices, "pendingDeliveries", pending);
    }
}
