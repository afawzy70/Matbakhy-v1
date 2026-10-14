package com.matbakhy.shared.config;

/**
 * The deployment the API runs in, published to clients (API-001) so every client can label a test environment. {@code demo}
 * is true everywhere except production: data is synthetic or test data, deposits and payments are simulated, and nothing
 * real is sent to anyone (owner decision 2026-10-14).
 */
public record RuntimeEnvironment(String name, boolean demo) {
}
