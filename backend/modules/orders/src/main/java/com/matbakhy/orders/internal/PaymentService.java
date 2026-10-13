package com.matbakhy.orders.internal;

import com.matbakhy.cooks.api.KitchenDirectory;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MB-051 (owner decision D16): eligible cash on delivery and manual deposits paid by the customer directly to the
 * kitchen's approved recipient. Matbakhy never holds the money. The payment state is kept apart from the order and its
 * fulfilment. A customer's transfer report is not payment. The kitchen's acknowledgement, given after it checks its
 * own account, is recorded as reported by the kitchen and never as bank verification. Preparation of a deposit order
 * waits for that report. Closing an order after a reported transfer leads to reconciliation, never to a silent close.
 */
@Service
public class PaymentService {

    public enum Method { COD, DEPOSIT }

    public record Money(long amountMinor, String currency) {
    }

    public record Recipient(String method, String identifier, String name) {
    }

    /** Payment terms in a quote. */
    public record Terms(String method, int depositBps, Money deposit, Money remainder, Recipient recipient) {
    }

    public record PaymentView(String method, String state, Money deposit, Money remainder, Recipient recipient, Instant depositExpiresAt,
                              long version) {
    }

    record Profile(int depositBps, Long codMaxTotalMinor, Recipient recipient) {
    }

    private static final Set<String> AFTER_TRANSFER = Set.of("CUSTOMER_TRANSFER_REPORTED", "KITCHEN_RECEIPT_REPORTED", "DISPUTED");

    private final JdbcTemplate jdbc;
    private final KitchenDirectory kitchens;
    private final Clock clock;

    PaymentService(JdbcTemplate jdbc, KitchenDirectory kitchens, Clock clock) {
        this.jdbc = jdbc;
        this.kitchens = kitchens;
        this.clock = clock;
    }

    /** The kitchen's current terms; {@code lock} takes a share lock so a change serializes with a placement (as in D13). */
    Profile profile(UUID kitchenId, boolean lock) {
        return jdbc.query("""
                SELECT deposit_bps, cod_max_total_minor, recipient_method, recipient_identifier, recipient_name, recipient_status
                FROM kitchen_payment_profile WHERE kitchen_id = ?""" + (lock ? " FOR SHARE" : ""), rs -> {
            if (!rs.next()) {
                return new Profile(0, null, null);
            }
            Recipient r = "APPROVED".equals(rs.getString(6))
                    ? new Recipient(rs.getString(3), rs.getString(4), rs.getString(5)) : null;
            return new Profile(rs.getInt(1), (Long) rs.getObject(2), r);
        }, kitchenId);
    }

    /** Server-derived terms for a quote: integer EGP, deposit rounded half up, the rest due at hand-over. */
    Terms terms(Profile p, Method method, long totalMinor) {
        if (method == Method.COD) {
            if (p.codMaxTotalMinor() != null && totalMinor > p.codMaxTotalMinor()) {
                throw invalid("paymentMethod", "COD_NOT_ELIGIBLE");
            }
            return new Terms("COD", 0, egp(0), egp(totalMinor), null);
        }
        if (p.depositBps() == 0 || p.recipient() == null) {
            throw invalid("paymentMethod", "DEPOSIT_NOT_OFFERED");
        }
        long deposit = Math.max(1, Math.floorDiv(Math.addExact(Math.multiplyExact(totalMinor, (long) p.depositBps()), 5_000L), 10_000L));
        return new Terms("DEPOSIT", p.depositBps(), egp(deposit), egp(totalMinor - deposit), p.recipient());
    }

    /** Writes the immutable payment record of a new order; the deposit is due by the acceptance deadline. */
    void createFor(UUID orderId, UUID customerId, String method, long totalMinor, long depositMinor, Recipient r, Instant acceptBy, Instant now) {
        boolean deposit = "DEPOSIT".equals(method);
        String state = deposit ? "WAITING_FOR_DEPOSIT" : "NOT_REQUIRED";
        jdbc.update("""
                INSERT INTO order_payment (order_id, method, total_minor, deposit_minor, remainder_minor, recipient_method, recipient_identifier,
                                           recipient_name, deposit_expires_at, state, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                orderId, method, totalMinor, depositMinor, totalMinor - depositMinor, deposit ? r.method() : null, deposit ? r.identifier() : null,
                deposit ? r.name() : null, deposit ? Timestamp.from(acceptBy) : null, state, Timestamp.from(now));
        if (deposit) {
            event(orderId, null, state, "CUSTOMER", customerId, depositMinor, null, null, now);
        }
    }

    public Optional<PaymentView> view(UUID orderId) {
        return jdbc.query("""
                SELECT method, state, deposit_minor, remainder_minor, recipient_method, recipient_identifier, recipient_name, deposit_expires_at, version
                FROM order_payment WHERE order_id = ?""", (rs, n) -> new PaymentView(rs.getString(1), rs.getString(2), egp(rs.getLong(3)),
                egp(rs.getLong(4)), rs.getString(6) == null ? null : new Recipient(rs.getString(5), rs.getString(6), rs.getString(7)),
                rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant(), rs.getLong(9)), orderId).stream().findFirst();
    }

    /**
     * The customer says the deposit was sent. Idempotent: a repeated or concurrent report changes nothing more. The
     * report is information for the kitchen, not payment.
     */
    @Transactional
    public PaymentView reportTransfer(UUID customerId, UUID orderId, String reference) {
        Map<String, Object> p = lockForCustomer(customerId, orderId);
        String state = (String) p.get("state");
        if ("CUSTOMER_TRANSFER_REPORTED".equals(state) || "KITCHEN_RECEIPT_REPORTED".equals(state)) {
            return view(orderId).orElseThrow();
        }
        if (!"WAITING_FOR_DEPOSIT".equals(state)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "no deposit is waiting", List.of(new FieldError("payment", "NO_DEPOSIT_WAITING")));
        }
        move(orderId, state, "CUSTOMER_TRANSFER_REPORTED", (Long) p.get("version"), "CUSTOMER", customerId, null, reference, null);
        return view(orderId).orElseThrow();
    }

    /**
     * The kitchen reports that the deposit arrived in its own account. Recorded as the kitchen's statement, not as a
     * bank confirmation. The amount must equal the deposit; a different amount or "not received" is MB-052.
     */
    @Transactional
    public PaymentView kitchenReceipt(UUID cookId, UUID orderId, long expectedVersion, long amountMinor, String reference) {
        UUID kitchen = kitchens.ownedBy(cookId).map(KitchenDirectory.KitchenStatus::kitchenId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "not a cook"));
        Map<String, Object> p = jdbc.queryForList("""
                SELECT p.state, p.version, p.deposit_minor FROM order_payment p JOIN customer_order o ON o.id = p.order_id
                WHERE p.order_id = ? AND o.kitchen_id = ? FOR UPDATE OF p""", orderId, kitchen).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "order not found"));
        String state = (String) p.get("state");
        if (!"WAITING_FOR_DEPOSIT".equals(state) && !"CUSTOMER_TRANSFER_REPORTED".equals(state)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "no deposit is waiting", List.of(new FieldError("payment", "NO_DEPOSIT_WAITING")));
        }
        if ((Long) p.get("version") != expectedVersion) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "payment changed");
        }
        if (amountMinor != (Long) p.get("deposit_minor")) {
            throw invalid("amountMinor", "DOES_NOT_MATCH_DEPOSIT");
        }
        move(orderId, state, "KITCHEN_RECEIPT_REPORTED", expectedVersion, "COOK", cookId, amountMinor, reference, null);
        return view(orderId).orElseThrow();
    }

    /** No preparation of a deposit order before the kitchen has reported receipt (TC-159). */
    void requireReadyForPreparation(UUID orderId) {
        String state = jdbc.query("SELECT state FROM order_payment WHERE order_id = ?", rs -> rs.next() ? rs.getString(1) : null, orderId);
        if (state != null && !"NOT_REQUIRED".equals(state) && !"KITCHEN_RECEIPT_REPORTED".equals(state)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "deposit not reported received by the kitchen",
                    List.of(new FieldError("payment", "DEPOSIT_NOT_RECEIVED")));
        }
    }

    /**
     * The order ended without fulfilment (expired, rejected or cancelled). A deposit nobody reported expires; once a
     * transfer was reported by either side, the payment needs reconciliation (refund or confirmation that nothing
     * arrived), never a silent close (TC-160).
     */
    void orderClosed(UUID orderId, String actorType, UUID actorId, String reasonCode) {
        Map<String, Object> p = jdbc.queryForList("SELECT state, version FROM order_payment WHERE order_id = ? FOR UPDATE", orderId).stream()
                .findFirst().orElse(null);
        if (p == null) {
            return;
        }
        String state = (String) p.get("state");
        String to = "WAITING_FOR_DEPOSIT".equals(state) ? "EXPIRED" : AFTER_TRANSFER.contains(state) ? "RECONCILE_PENDING" : null;
        if (to != null) {
            move(orderId, state, to, (Long) p.get("version"), actorType, actorId, null, null, reasonCode);
        }
    }

    private Map<String, Object> lockForCustomer(UUID customerId, UUID orderId) {
        return jdbc.queryForList("""
                SELECT p.state, p.version FROM order_payment p JOIN customer_order o ON o.id = p.order_id
                WHERE p.order_id = ? AND o.customer_id = ? FOR UPDATE OF p""", orderId, customerId).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "order not found"));
    }

    private void move(UUID orderId, String from, String to, long version, String actorType, UUID actorId, Long amount, String reference,
                      String reasonCode) {
        Instant now = clock.instant();
        int updated = jdbc.update("UPDATE order_payment SET state = ?, version = version + 1, updated_at = ? WHERE order_id = ? AND version = ? AND state = ?",
                to, Timestamp.from(now), orderId, version, from);
        if (updated != 1) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "payment changed concurrently");
        }
        event(orderId, from, to, actorType, actorId, amount, reference, reasonCode, now);
    }

    private void event(UUID orderId, String from, String to, String actorType, UUID actorId, Long amount, String reference, String reasonCode,
                       Instant at) {
        jdbc.update("""
                INSERT INTO payment_event (id, order_id, from_state, to_state, actor_type, actor_id, amount_minor, reference, reason_code, request_id,
                                           occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), orderId, from, to, actorType, actorId, amount, reference == null || reference.isBlank() ? null : reference.strip(),
                reasonCode, org.slf4j.MDC.get(com.matbakhy.shared.web.RequestContext.REQUEST_ID_MDC_KEY), Timestamp.from(at));
    }

    private static Money egp(long minor) {
        return new Money(minor, "EGP");
    }

    private static ApiException invalid(String field, String code) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "invalid " + field, List.of(new FieldError(field, code)));
    }
}
