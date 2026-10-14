package com.matbakhy.app.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL (production major version, digest-pinned in backend/pom.xml) for integration
 * tests. H2 or mocks are not substitutes for migration, constraint or concurrency checks.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

    public static DockerImageName image() {
        String image = System.getProperty("matbakhy.test.postgres-image");
        if (image == null || image.isBlank()) {
            throw new IllegalStateException("matbakhy.test.postgres-image is not set; run tests through Maven");
        }
        return DockerImageName.parse(image).asCompatibleSubstituteFor("postgres");
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(image());
    }
}
