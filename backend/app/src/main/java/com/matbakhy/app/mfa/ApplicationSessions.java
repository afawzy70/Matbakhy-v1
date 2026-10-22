package com.matbakhy.app.mfa;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ends the application's own web sessions (Spring Session JDBC) of an account. Ending the provider sessions is not enough: a web
 * session keeps the login it was created from. Sessions are indexed by the provider subject (the OIDC principal name).
 */
@Component
public class ApplicationSessions {

    private final JdbcTemplate jdbc;

    ApplicationSessions(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Attributes are removed by the foreign key's cascade; returns how many sessions were ended. */
    @Transactional
    public int endAllFor(String subject) {
        return jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", subject);
    }
}
