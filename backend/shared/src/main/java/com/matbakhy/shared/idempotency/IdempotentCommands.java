package com.matbakhy.shared.idempotency;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Idempotent command execution scoped by actor + command + key (docs/engineering/03-api-contract.md).
 * Must run inside the caller's transaction: the record and the command's writes commit or roll back
 * together. Same key and same request returns the stored result; same key with a different request
 * fails with IDEMPOTENCY_CONFLICT.
 */
public interface IdempotentCommands {

    <T> T execute(UUID actorId, String command, String idempotencyKey, Object request, Class<T> resultType, Supplier<T> action);
}
