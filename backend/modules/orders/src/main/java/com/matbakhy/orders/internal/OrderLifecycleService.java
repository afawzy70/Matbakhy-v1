package com.matbakhy.orders.internal;

import com.matbakhy.availability.api.SlotInventory;
import com.matbakhy.availability.api.SlotInventory.Line;
import com.matbakhy.availability.api.SlotInventory.Stage;
import com.matbakhy.cooks.api.KitchenDirectory;
import com.matbakhy.cooks.api.KitchenDirectory.KitchenStatus;
import com.matbakhy.operations.api.AuditLog;
import com.matbakhy.operations.api.AuditLog.Entry;
import com.matbakhy.operations.api.AuditLog.Outcome;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import com.matbakhy.shared.idempotency.IdempotentCommands;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Order lifecycle (BR-026..BR-031). Every command locks the order row, checks actor scope, expected version and
 * state, then applies one legal transition with its stock move, event, outbox message and cash effect in the same
 * transaction; terminal orders are immutable (also enforced by a database trigger). Stock moves happen exactly once
 * because allocation rows change state conditionally under the order lock.
 */
@Service
public class OrderLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(OrderLifecycleService.class);

    public static final Set<String> TERMINAL = Set.of("COMPLETED", "REJECTED", "EXPIRED", "CANCELLED", "DELIVERY_FAILED");
    static final Map<String, Set<String>> REASONS = Map.of(
            "reject", Set.of("KITCHEN_BUSY", "INGREDIENT_UNAVAILABLE", "OUTSIDE_CAPACITY", "OTHER"),
            "customer_cancel", Set.of("CHANGED_MIND", "ORDERED_BY_MISTAKE", "TOO_SLOW", "OTHER"),
            "cook_cancel", Set.of("INGREDIENT_UNAVAILABLE", "KITCHEN_ISSUE", "OTHER"),
            "delivery_failure", Set.of("CUSTOMER_UNREACHABLE", "ADDRESS_NOT_FOUND", "REFUSED", "OTHER"),
            "exception_cancel", Set.of("SAFETY", "OPERATIONAL", "OTHER"));

    private record Order(UUID id, UUID customerId, UUID kitchenId, UUID slotId, String state, String mode, Instant acceptBy, long version) {
    }

    private final JdbcTemplate jdbc;
    private final SlotInventory inventory;
    private final KitchenDirectory kitchens;
    private final HandoverCodes codes;
    private final HandoverProperties handover;
    private final IdempotentCommands idempotency;
    private final AuditLog audit;
    private final JsonMapper json;
    private final Clock clock;

    OrderLifecycleService(JdbcTemplate jdbc, SlotInventory inventory, KitchenDirectory kitchens, HandoverCodes codes, HandoverProperties handover,
                          IdempotentCommands idempotency, AuditLog audit, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.inventory = inventory;
        this.kitchens = kitchens;
        this.codes = codes;
        this.handover = handover;
        this.idempotency = idempotency;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    public record Command(UUID orderId, String action, Long expectedVersion, String reasonCode, String note, String reference, String code) {
    }

    // ---- customer ----

    @Transactional
    public UUID customerCancel(UUID customerId, String key, Command c) {
        return idempotency.execute(customerId, "order.customer_cancel", key, c, UUID.class, () -> {
            Order o = lock(c.orderId(), "o.customer_id = ?", customerId);
            guard(o, c);
            if (!o.state().equals("PENDING_ACCEPTANCE")) {
                // After acceptance the customer asks support (BR-028); no unilateral transition.
                throw new ApiException(ErrorCode.STATE_CONFLICT, "customer cannot cancel in " + o.state(),
                        List.of(new FieldError("state", "CANCELLATION_REQUEST_REQUIRED")));
            }
            reason("customer_cancel", c.reasonCode());
            release(o, Stage.RESERVED);
            transition(o, "CANCELLED", "CUSTOMER", customerId, c.reasonCode());
            cash(o, "NOT_DUE");
            return o.id();
        });
    }

    // ---- cook ----

    @Transactional(noRollbackFor = ApiException.class)
    public UUID cook(UUID cookId, String key, Command c) {
        KitchenStatus kitchen = kitchens.ownedBy(cookId).orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "not a cook"));
        if (c.action().equals("handover")) {
            // Wrong codes must be counted even though the command fails, so this path keeps its writes on errors.
            return idempotency.execute(cookId, "order.cook_handover", key, c, UUID.class, () -> {
                Order o = lock(c.orderId(), "o.kitchen_id = ?", kitchen.kitchenId());
                guard(o, c);
                handover(o, cookId, c.code());
                return o.id();
            });
        }
        return rollbackOnError(() -> idempotency.execute(cookId, "order.cook_" + c.action(), key, c, UUID.class, () -> {
            Order o = lock(c.orderId(), "o.kitchen_id = ?", kitchen.kitchenId());
            guard(o, c);
            Instant now = clock.instant();
            switch (c.action()) {
                case "accept" -> {
                    require(o, "PENDING_ACCEPTANCE");
                    if (!kitchen.canAcceptOrders()) {
                        throw new ApiException(ErrorCode.KITCHEN_UNAVAILABLE, "kitchen cannot accept orders");
                    }
                    if (!now.isBefore(o.acceptBy())) {
                        throw new ApiException(ErrorCode.STATE_CONFLICT, "acceptance deadline passed",
                                List.of(new FieldError("acceptBy", "DEADLINE_PASSED")));
                    }
                    move(o, Stage.RESERVED, Stage.ALLOCATED, "RESERVED", "ALLOCATED");
                    transition(o, "ACCEPTED", "COOK", cookId, null);
                }
                case "reject" -> {
                    require(o, "PENDING_ACCEPTANCE");
                    reason("reject", c.reasonCode());
                    release(o, Stage.RESERVED);
                    transition(o, "REJECTED", "COOK", cookId, c.reasonCode());
                    cash(o, "NOT_DUE");
                }
                case "prepare" -> {
                    require(o, "ACCEPTED");
                    fulfillable(kitchen);
                    move(o, Stage.ALLOCATED, Stage.CONSUMED, "ALLOCATED", "CONSUMED");
                    transition(o, "PREPARING", "COOK", cookId, null);
                }
                case "ready" -> {
                    require(o, "PREPARING");
                    fulfillable(kitchen);
                    issueChallenge(o);
                    transition(o, "READY", "COOK", cookId, null);
                }
                case "dispatch" -> {
                    require(o, "READY");
                    if (!o.mode().equals("DELIVERY")) {
                        throw wrongMode();
                    }
                    if (c.reference() == null || c.reference().isBlank() || c.reference().length() > 200) {
                        throw invalid("deliveryReference", "REQUIRED");
                    }
                    jdbc.update("UPDATE customer_order SET dispatch_reference = ? WHERE id = ?", c.reference().strip(), o.id());
                    transition(o, "OUT_FOR_DELIVERY", "COOK", cookId, null);
                }
                case "delivery_failure" -> {
                    if (!o.mode().equals("DELIVERY")) {
                        throw wrongMode();
                    }
                    require(o, "OUT_FOR_DELIVERY");
                    reason("delivery_failure", c.reasonCode());
                    transition(o, "DELIVERY_FAILED", "COOK", cookId, c.reasonCode());
                }
                case "cancel" -> {
                    // Only before preparation; afterwards ops handles exceptions (BR-028/BR-029).
                    require(o, "ACCEPTED");
                    reason("cook_cancel", c.reasonCode());
                    release(o, Stage.ALLOCATED);
                    transition(o, "CANCELLED", "COOK", cookId, c.reasonCode());
                    cash(o, "NOT_DUE");
                }
                default -> throw new IllegalArgumentException("unknown action " + c.action());
            }
            return o.id();
        }));
    }

    // ---- operations ----

    @Transactional
    public UUID opsExceptionCancel(UUID staffId, String key, Command c, String requestId) {
        return idempotency.execute(staffId, "order.ops_exception_cancel", key, c, UUID.class, () -> {
            Order o = lock(c.orderId(), "true OR ?::uuid IS NULL", (Object) null);
            guard(o, c);
            if (!o.state().equals("PREPARING") && !o.state().equals("READY")) {
                throw new ApiException(ErrorCode.STATE_CONFLICT, "exception cancel only from PREPARING or READY");
            }
            reason("exception_cancel", c.reasonCode());
            if (c.reference() == null || c.reference().isBlank()) {
                throw invalid("caseId", "REQUIRED");
            }
            // Consumed stock stays consumed; finance reviews the cash obligation manually (BR-029).
            transition(o, "CANCELLED", "STAFF", staffId, c.reasonCode());
            audit.record(new Entry(staffId, "order.exception_cancel", "order.exception_cancel", "order", o.id().toString(), Outcome.ALLOWED,
                    c.note() == null ? c.reasonCode() : c.note(), requestId, Map.of("reasonCode", c.reasonCode(), "caseRef", c.reference())));
            return o.id();
        });
    }

    @Transactional
    public UUID opsHandoverOverride(UUID staffId, String key, Command c, String requestId) {
        return idempotency.execute(staffId, "order.ops_handover_override", key, c, UUID.class, () -> {
            Order o = lock(c.orderId(), "true OR ?::uuid IS NULL", (Object) null);
            guard(o, c);
            requireHandoverState(o);
            if (c.note() == null || c.note().strip().length() < 10) {
                throw invalid("reason", "MIN_10_CHARS");
            }
            if (c.reference() == null || c.reference().isBlank()) {
                throw invalid("evidenceRef", "REQUIRED");
            }
            jdbc.update("UPDATE handover_challenge SET consumed_at = ? WHERE order_id = ? AND consumed_at IS NULL", Timestamp.from(clock.instant()),
                    o.id());
            transition(o, "COMPLETED", "STAFF", staffId, "HANDOVER_OVERRIDE");
            audit.record(new Entry(staffId, "order.handover_override", "order.handover_override", "order", o.id().toString(), Outcome.ALLOWED,
                    c.note(), requestId, Map.of("evidenceRef", c.reference(), "mode", o.mode())));
            return o.id();
        });
    }

    // ---- system ----

    /** Due pending orders (server time); each is expired by {@link #expireOne} in its own transaction. */
    public List<UUID> dueForExpiry(int limit) {
        return jdbc.queryForList("SELECT id FROM customer_order WHERE state = 'PENDING_ACCEPTANCE' AND accept_by <= ? ORDER BY accept_by LIMIT ?",
                UUID.class, Timestamp.from(clock.instant()), limit);
    }

    @Transactional
    public boolean expireOne(UUID orderId) {
        Order o = jdbc.query("""
                SELECT id, customer_id, kitchen_id, slot_id, state, fulfillment_mode, accept_by, version FROM customer_order o
                WHERE id = ? FOR UPDATE""", (rs, n) -> map(rs), orderId).stream().findFirst().orElse(null);
        if (o == null || !o.state().equals("PENDING_ACCEPTANCE") || clock.instant().isBefore(o.acceptBy())) {
            return false;
        }
        release(o, Stage.RESERVED);
        transition(o, "EXPIRED", "SYSTEM", null, "ACCEPTANCE_DEADLINE");
        cash(o, "NOT_DUE");
        return true;
    }

    // ---- internals ----

    private <T> T rollbackOnError(Supplier<T> body) {
        try {
            return body.get();
        } catch (RuntimeException e) {
            org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            throw e;
        }
    }

    private Order lock(UUID orderId, String scope, Object scopeArg) {
        return jdbc.query("SELECT id, customer_id, kitchen_id, slot_id, state, fulfillment_mode, accept_by, version FROM customer_order o "
                + "WHERE o.id = ? AND (" + scope + ") FOR UPDATE", (rs, n) -> map(rs), orderId, scopeArg).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "order not found"));
    }

    private static Order map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Order(rs.getObject("id", UUID.class), rs.getObject("customer_id", UUID.class), rs.getObject("kitchen_id", UUID.class),
                rs.getObject("slot_id", UUID.class), rs.getString("state"), rs.getString("fulfillment_mode"),
                rs.getTimestamp("accept_by").toInstant(), rs.getLong("version"));
    }

    private static void guard(Order o, Command c) {
        if (c.expectedVersion() == null) {
            throw invalid("expectedVersion", "REQUIRED");
        }
        if (TERMINAL.contains(o.state())) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "order is terminal: " + o.state(), List.of(new FieldError("state", o.state())));
        }
        if (c.expectedVersion() != o.version()) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "stale order version");
        }
    }

    private static void require(Order o, String state) {
        if (!o.state().equals(state)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "expected " + state + " but order is " + o.state(),
                    List.of(new FieldError("state", o.state())));
        }
    }

    private static void requireHandoverState(Order o) {
        boolean pickupReady = o.mode().equals("PICKUP") && o.state().equals("READY");
        boolean deliveryOut = o.mode().equals("DELIVERY") && o.state().equals("OUT_FOR_DELIVERY");
        if (!pickupReady && !deliveryOut) {
            if (o.mode().equals("DELIVERY") && o.state().equals("READY")) {
                throw wrongMode();
            }
            throw new ApiException(ErrorCode.STATE_CONFLICT, "handover not possible in " + o.state(), List.of(new FieldError("state", o.state())));
        }
    }

    private static ApiException wrongMode() {
        return new ApiException(ErrorCode.STATE_CONFLICT, "wrong fulfillment path", List.of(new FieldError("fulfillmentMode", "WRONG_FULFILLMENT_MODE")));
    }

    private static void fulfillable(KitchenStatus kitchen) {
        if (!kitchen.canFulfillAcceptedOrders()) {
            throw new ApiException(ErrorCode.KITCHEN_UNAVAILABLE, "kitchen cannot fulfil");
        }
    }

    private static void reason(String command, String code) {
        if (code == null || !REASONS.get(command).contains(code)) {
            throw invalid("reasonCode", "ALLOWED_" + String.join("|", REASONS.get(command).stream().sorted().toList()));
        }
    }

    private List<Line> lines(Order o, String allocationState) {
        return jdbc.query("SELECT meal_slot_id, quantity FROM stock_allocation WHERE order_id = ? AND state = ? ORDER BY meal_slot_id",
                (rs, n) -> new Line(rs.getObject(1, UUID.class), rs.getInt(2)), o.id(), allocationState);
    }

    private void move(Order o, Stage from, Stage to, String fromState, String toState) {
        List<Line> lines = lines(o, fromState);
        if (lines.isEmpty()) {
            throw new IllegalStateException("no " + fromState + " allocation for order " + o.id());
        }
        inventory.move(o.slotId(), lines, from, to);
        jdbc.update("UPDATE stock_allocation SET state = ? WHERE order_id = ? AND state = ?", toState, o.id(), fromState);
    }

    /** Returns portions exactly once: only allocations still in the expected state are released. */
    private void release(Order o, Stage from) {
        List<Line> lines = lines(o, from.name());
        if (lines.isEmpty()) {
            return;
        }
        inventory.move(o.slotId(), lines, from, null);
        jdbc.update("UPDATE stock_allocation SET state = 'RELEASED', released_at = ? WHERE order_id = ? AND state = ?",
                Timestamp.from(clock.instant()), o.id(), from.name());
    }

    private void cash(Order o, String state) {
        jdbc.update("UPDATE cash_obligation SET state = ?, version = version + 1 WHERE order_id = ? AND state = 'DUE'", state, o.id());
    }

    private void transition(Order o, String to, String actorType, UUID actorId, String reasonCode) {
        Instant now = clock.instant();
        boolean terminal = TERMINAL.contains(to);
        int updated = jdbc.update("""
                UPDATE customer_order SET state = ?, version = version + 1, updated_at = ?, terminal_at = ?
                WHERE id = ? AND version = ? AND state = ?""",
                to, Timestamp.from(now), terminal ? Timestamp.from(now) : null, o.id(), o.version(), o.state());
        if (updated != 1) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "order changed concurrently");
        }
        Integer seq = jdbc.queryForObject("SELECT coalesce(max(sequence), 0) + 1 FROM order_event WHERE order_id = ?", Integer.class, o.id());
        jdbc.update("""
                INSERT INTO order_event (id, order_id, sequence, from_state, to_state, actor_type, actor_id, reason_code, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), o.id(), seq, o.state(), to, actorType, actorId, reasonCode, Timestamp.from(now));
        jdbc.update("""
                INSERT INTO outbox_event (id, aggregate_type, aggregate_id, event_type, payload, available_at, created_at, request_id)
                VALUES (?, 'order', ?, ?, ?::jsonb, ?, ?, ?)""",
                UUID.randomUUID(), o.id(), "ORDER_" + to, json.writeValueAsString(Map.of("orderId", o.id().toString(), "state", to)),
                Timestamp.from(now), Timestamp.from(now), org.slf4j.MDC.get(com.matbakhy.shared.web.RequestContext.REQUEST_ID_MDC_KEY));
        log.info("order {} {} -> {} by {}", o.id(), o.state(), to, actorType);
    }

    private void issueChallenge(Order o) {
        HandoverCodes.Sealed sealed = codes.issue(o.id());
        Instant slotEnd = jdbc.queryForObject("SELECT ends_at FROM cook_slot WHERE id = ?", Timestamp.class, o.slotId()).toInstant();
        Instant now = clock.instant();
        Instant expires = slotEnd.plus(handover.expiryAfterSlotEnd());
        if (expires.isBefore(now.plus(Duration.ofHours(1)))) {
            expires = now.plus(Duration.ofHours(1));
        }
        jdbc.update("""
                INSERT INTO handover_challenge (order_id, code_hash, ciphertext, nonce, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?)""",
                o.id(), sealed.hash(), sealed.ciphertext(), sealed.nonce(), Timestamp.from(expires), Timestamp.from(now));
    }

    private void handover(Order o, UUID actorId, String presented) {
        requireHandoverState(o);
        Instant now = clock.instant();
        Integer recent = jdbc.queryForObject("SELECT count(*) FROM handover_attempt WHERE actor_id = ? AND attempted_at > ?", Integer.class,
                actorId, Timestamp.from(now.minus(Duration.ofHours(1))));
        if (recent != null && recent >= handover.maxAttemptsPerActorPerHour()) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "too many handover attempts");
        }
        Map<String, Object> ch = jdbc.queryForList("SELECT code_hash, attempts, expires_at, locked_at, consumed_at FROM handover_challenge "
                + "WHERE order_id = ? FOR UPDATE", o.id()).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.STATE_CONFLICT, "no handover challenge"));
        if (ch.get("consumed_at") != null) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "code already used", List.of(new FieldError("code", "CODE_CONSUMED")));
        }
        if (ch.get("locked_at") != null) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "handover locked; ops recovery required", List.of(new FieldError("code", "CHALLENGE_LOCKED")));
        }
        if (!now.isBefore(((Timestamp) ch.get("expires_at")).toInstant())) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "handover code expired; ops recovery required", List.of(new FieldError("code", "CHALLENGE_EXPIRED")));
        }
        boolean ok = codes.matches(o.id(), presented, (String) ch.get("code_hash"));
        jdbc.update("INSERT INTO handover_attempt (id, order_id, actor_id, success, attempted_at) VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), o.id(),
                actorId, ok, Timestamp.from(now));
        if (!ok) {
            int attempts = ((Integer) ch.get("attempts")) + 1;
            boolean lock = attempts >= handover.maxAttempts();
            jdbc.update("UPDATE handover_challenge SET attempts = ?, locked_at = ? WHERE order_id = ?", attempts, lock ? Timestamp.from(now) : null,
                    o.id());
            throw new ApiException(ErrorCode.VERIFICATION_FAILED, "wrong handover code",
                    List.of(new FieldError("code", lock ? "CHALLENGE_LOCKED" : "WRONG_CODE")));
        }
        jdbc.update("UPDATE handover_challenge SET consumed_at = ? WHERE order_id = ?", Timestamp.from(now), o.id());
        transition(o, "COMPLETED", "COOK", actorId, null);
    }

    private static ApiException invalid(String field, String code) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "invalid " + field, List.of(new FieldError(field, code)));
    }
}
