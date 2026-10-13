package com.matbakhy.orders.internal;

import com.matbakhy.cooks.api.KitchenDirectory;
import com.matbakhy.operations.api.AuditLog;
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

    /** The kitchen's own view of its payment terms; the recipient is offered to customers only once APPROVED. */
    public record ProfileView(int depositBps, Long codMaxTotalMinor, Recipient recipient, String recipientStatus, long version) {
    }

    public enum Problem { NOT_RECEIVED, AMOUNT_MISMATCH }

    /** Staff outcomes on an ended order. Deliberately no outcome that marks money as received. */
    public enum Decision { REFUND_OWED, CLOSED_NO_FUNDS }

    public record QueueItem(UUID orderId, String publicNumber, UUID kitchenId, String orderState, String state, Money deposit,
                            String lastReason, Instant updatedAt, long version) {
    }

    private static final Set<String> AFTER_TRANSFER = Set.of("CUSTOMER_TRANSFER_REPORTED", "KITCHEN_RECEIPT_REPORTED", "DISPUTED");

    private final JdbcTemplate jdbc;
    private final KitchenDirectory kitchens;
    private final AuditLog audit;
    private final Clock clock;

    PaymentService(JdbcTemplate jdbc, KitchenDirectory kitchens, AuditLog audit, Clock clock) {
        this.jdbc = jdbc;
        this.kitchens = kitchens;
        this.audit = audit;
        this.clock = clock;
    }

    private static final Set<String> ENDED = Set.of("COMPLETED", "REJECTED", "EXPIRED", "CANCELLED", "DELIVERY_FAILED");

    private UUID ownKitchen(UUID cookId) {
        return kitchens.ownedBy(cookId).map(KitchenDirectory.KitchenStatus::kitchenId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "not a cook"));
    }

    // ---- MB-052: the kitchen's payment profile and its approval ----

    public ProfileView profileOf(UUID cookId) {
        return profileView(ownKitchen(cookId));
    }

    private ProfileView profileView(UUID kitchenId) {
        return jdbc.query("""
                SELECT deposit_bps, cod_max_total_minor, recipient_method, recipient_identifier, recipient_name, recipient_status, version
                FROM kitchen_payment_profile WHERE kitchen_id = ?""", rs -> rs.next()
                ? new ProfileView(rs.getInt(1), (Long) rs.getObject(2),
                        rs.getString(4) == null ? null : new Recipient(rs.getString(3), rs.getString(4), rs.getString(5)), rs.getString(6), rs.getLong(7))
                : new ProfileView(0, null, null, "NONE", 0), kitchenId);
    }

    /**
     * The kitchen sets its deposit share, its COD cap and the account customers pay. A new or changed recipient is not
     * offered to customers until staff approve it again.
     */
    @Transactional
    public ProfileView updateProfile(UUID cookId, long expectedVersion, int depositBps, Long codMaxTotalMinor, Recipient r) {
        UUID kitchen = ownKitchen(cookId);
        if (depositBps < 0 || depositBps > 10_000) {
            throw invalid("depositBps", "RANGE_0_10000");
        }
        if (codMaxTotalMinor != null && codMaxTotalMinor < 0) {
            throw invalid("codMaxTotalMinor", "NOT_NEGATIVE");
        }
        if (depositBps > 0 && r == null) {
            throw invalid("recipient", "REQUIRED_FOR_DEPOSIT");
        }
        if (r != null && (r.method() == null || !Set.of("INSTAPAY", "MOBILE_WALLET", "BANK_TRANSFER").contains(r.method())
                || r.identifier() == null || r.identifier().strip().length() < 3 || r.identifier().strip().length() > 64
                || r.name() == null || r.name().strip().length() < 2 || r.name().strip().length() > 80)) {
            throw invalid("recipient", "INVALID_RECIPIENT");
        }
        ProfileView current = profileView(kitchen);
        if (current.version() != expectedVersion) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "payment profile changed");
        }
        Recipient next = r == null ? null : new Recipient(r.method(), r.identifier().strip(), r.name().strip());
        boolean sameRecipient = java.util.Objects.equals(next, current.recipient());
        String status = sameRecipient && next != null ? current.recipientStatus() : "NONE";
        int n = jdbc.update("""
                INSERT INTO kitchen_payment_profile (kitchen_id, deposit_bps, cod_max_total_minor, recipient_method, recipient_identifier, recipient_name,
                                                     recipient_status, version)
                VALUES (?, ?, ?, ?, ?, ?, 'NONE', 1)
                ON CONFLICT (kitchen_id) DO UPDATE SET deposit_bps = EXCLUDED.deposit_bps, cod_max_total_minor = EXCLUDED.cod_max_total_minor,
                    recipient_method = EXCLUDED.recipient_method, recipient_identifier = EXCLUDED.recipient_identifier,
                    recipient_name = EXCLUDED.recipient_name, recipient_status = ?,
                    approved_by = CASE WHEN ? = 'APPROVED' THEN kitchen_payment_profile.approved_by END,
                    approved_at = CASE WHEN ? = 'APPROVED' THEN kitchen_payment_profile.approved_at END,
                    version = kitchen_payment_profile.version + 1
                WHERE kitchen_payment_profile.version = ?""",
                kitchen, depositBps, codMaxTotalMinor, next == null ? null : next.method(), next == null ? null : next.identifier(),
                next == null ? null : next.name(), status, status, status, expectedVersion);
        if (n != 1) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "payment profile changed");
        }
        audit.record(new AuditLog.Entry(cookId, "payment.profile_update", null, "kitchen", kitchen.toString(), AuditLog.Outcome.ALLOWED, null,
                requestId(), Map.of("depositBps", depositBps, "recipientChanged", !sameRecipient)));
        return profileView(kitchen);
    }

    /** Staff (finance.verify, recent MFA) approve the recipient after checking it against the approved cook. Audited. */
    @Transactional
    public ProfileView approveRecipient(UUID staffId, UUID kitchenId, long expectedVersion, String reason) {
        if (reason == null || reason.strip().length() < 10 || reason.strip().length() > 500) {
            throw invalid("reason", "LENGTH_10_500");
        }
        if (kitchens.ownedBy(staffId).map(KitchenDirectory.KitchenStatus::kitchenId).filter(kitchenId::equals).isPresent()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "cannot approve your own kitchen's recipient");
        }
        ProfileView current = profileView(kitchenId);
        if (current.recipient() == null || current.version() == 0) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "no recipient to approve", List.of(new FieldError("recipient", "MISSING")));
        }
        int n = jdbc.update("""
                UPDATE kitchen_payment_profile SET recipient_status = 'APPROVED', approved_by = ?, approved_at = ?, version = version + 1
                WHERE kitchen_id = ? AND version = ?""", staffId, Timestamp.from(clock.instant()), kitchenId, expectedVersion);
        if (n != 1) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "payment profile changed");
        }
        audit.record(new AuditLog.Entry(staffId, "payment.recipient_approve", "finance.verify", "kitchen", kitchenId.toString(),
                AuditLog.Outcome.ALLOWED, reason.strip(), requestId(), Map.of("method", current.recipient().method())));
        return profileView(kitchenId);
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
        UUID kitchen = ownKitchen(cookId);
        Map<String, Object> p = jdbc.queryForList("""
                SELECT p.state, p.version, p.deposit_minor, o.state AS order_state FROM order_payment p JOIN customer_order o ON o.id = p.order_id
                WHERE p.order_id = ? AND o.kitchen_id = ? FOR UPDATE OF p""", orderId, kitchen).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "order not found"));
        String state = (String) p.get("state");
        if (ENDED.contains((String) p.get("order_state"))) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "order ended; report a refund instead", List.of(new FieldError("payment", "ORDER_ENDED")));
        }
        if (!Set.of("WAITING_FOR_DEPOSIT", "CUSTOMER_TRANSFER_REPORTED", "DISPUTED").contains(state)) {
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

    /** The kitchen reports that nothing arrived, or a different amount (TC-161). Processing pauses; ops see it in their queue. */
    @Transactional
    public PaymentView reportProblem(UUID cookId, UUID orderId, long expectedVersion, Problem kind, Long amountSeen, String reference) {
        if (kind == Problem.AMOUNT_MISMATCH && (amountSeen == null || amountSeen < 0)) {
            throw invalid("amountMinor", "REQUIRED_FOR_MISMATCH");
        }
        Map<String, Object> p = lockForKitchen(cookId, orderId);
        String state = (String) p.get("state");
        if (!Set.of("WAITING_FOR_DEPOSIT", "CUSTOMER_TRANSFER_REPORTED", "RECONCILE_PENDING").contains(state)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "nothing to dispute", List.of(new FieldError("payment", "NOTHING_TO_DISPUTE")));
        }
        checkVersion(p, expectedVersion);
        move(orderId, state, "DISPUTED", expectedVersion, "COOK", cookId, kind == Problem.AMOUNT_MISMATCH ? amountSeen : null, reference, kind.name());
        return view(orderId).orElseThrow();
    }

    /** Payments that need ops attention, newest first (finance.verify). */
    public List<QueueItem> queue(String state) {
        if (!Set.of("DISPUTED", "RECONCILE_PENDING", "REFUND_OWED").contains(state)) {
            throw invalid("state", "ALLOWED_DISPUTED|RECONCILE_PENDING|REFUND_OWED");
        }
        return jdbc.query("""
                SELECT p.order_id, o.public_number, o.kitchen_id, o.state, p.state, p.deposit_minor, p.updated_at, p.version,
                       (SELECT e.reason_code FROM payment_event e WHERE e.order_id = p.order_id AND e.reason_code IS NOT NULL
                        ORDER BY e.occurred_at DESC LIMIT 1)
                FROM order_payment p JOIN customer_order o ON o.id = p.order_id WHERE p.state = ? ORDER BY p.updated_at DESC LIMIT 100""",
                (rs, n) -> new QueueItem(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5),
                        egp(rs.getLong(6)), rs.getString(9), rs.getTimestamp(7).toInstant(), rs.getLong(8)), state);
    }

    /**
     * Staff decide on an ended order's open payment, with a reason (audited): the kitchen owes a refund, or the matter
     * is closed without funds. There is no decision that marks money as received.
     */
    @Transactional
    public PaymentView decide(UUID staffId, UUID orderId, long expectedVersion, Decision outcome, String reason) {
        if (reason == null || reason.strip().length() < 10 || reason.strip().length() > 500) {
            throw invalid("reason", "LENGTH_10_500");
        }
        Map<String, Object> p = jdbc.queryForList("""
                SELECT p.state, p.version, o.state AS order_state FROM order_payment p JOIN customer_order o ON o.id = p.order_id
                WHERE p.order_id = ? FOR UPDATE OF p""", orderId).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "order not found"));
        if (!ENDED.contains((String) p.get("order_state"))) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "decide once the order has ended", List.of(new FieldError("orderId", "ORDER_ACTIVE")));
        }
        String state = (String) p.get("state");
        if (!Set.of("DISPUTED", "RECONCILE_PENDING").contains(state)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "nothing to decide", List.of(new FieldError("payment", "NOTHING_TO_DECIDE")));
        }
        checkVersion(p, expectedVersion);
        move(orderId, state, outcome.name(), expectedVersion, "STAFF", staffId, null, null, outcome.name());
        audit.record(new AuditLog.Entry(staffId, "payment.decision", "finance.verify", "order", orderId.toString(), AuditLog.Outcome.ALLOWED,
                reason.strip(), requestId(), Map.of("outcome", outcome.name(), "from", state)));
        return view(orderId).orElseThrow();
    }

    /** The kitchen reports the refund it sent, with the transfer reference (its statement, not a bank confirmation). */
    @Transactional
    public PaymentView reportRefund(UUID cookId, UUID orderId, long expectedVersion, String reference) {
        if (reference == null || reference.isBlank() || reference.strip().length() > 64) {
            throw invalid("reference", "REQUIRED");
        }
        Map<String, Object> p = lockForKitchen(cookId, orderId);
        String state = (String) p.get("state");
        if (!Set.of("REFUND_OWED", "RECONCILE_PENDING").contains(state)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "no refund due", List.of(new FieldError("payment", "NO_REFUND_DUE")));
        }
        checkVersion(p, expectedVersion);
        move(orderId, state, "REFUNDED", expectedVersion, "COOK", cookId, (Long) p.get("deposit_minor"), reference, null);
        return view(orderId).orElseThrow();
    }

    private Map<String, Object> lockForKitchen(UUID cookId, UUID orderId) {
        UUID kitchen = ownKitchen(cookId);
        return jdbc.queryForList("""
                SELECT p.state, p.version, p.deposit_minor FROM order_payment p JOIN customer_order o ON o.id = p.order_id
                WHERE p.order_id = ? AND o.kitchen_id = ? FOR UPDATE OF p""", orderId, kitchen).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "order not found"));
    }

    private static void checkVersion(Map<String, Object> p, long expected) {
        if ((Long) p.get("version") != expected) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "payment changed");
        }
    }

    private static String requestId() {
        return org.slf4j.MDC.get(com.matbakhy.shared.web.RequestContext.REQUEST_ID_MDC_KEY);
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
