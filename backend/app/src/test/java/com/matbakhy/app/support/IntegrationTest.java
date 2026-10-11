package com.matbakhy.app.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Full application context on a random port against real PostgreSQL and S3-emulator containers. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"matbakhy.environment=CI", "matbakhy.orders.expiry-job.enabled=false"})
@Import({PostgresTestConfiguration.class, StorageTestConfiguration.class})
public @interface IntegrationTest {
}
