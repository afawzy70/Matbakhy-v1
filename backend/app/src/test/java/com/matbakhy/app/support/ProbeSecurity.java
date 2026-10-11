package com.matbakhy.app.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Test-only: lets error-handling probes under /api/v1/test-probe and /api/v1/test-money run without login. */
@TestConfiguration(proxyBeanMethods = false)
public class ProbeSecurity {

    @Bean
    @Order(0)
    SecurityFilterChain probeChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/test-probe/**", "/api/v1/test-probe", "/api/v1/test-money")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
