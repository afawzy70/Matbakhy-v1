package com.matbakhy.support.internal;

import com.matbakhy.identity.api.Permission;
import com.matbakhy.operations.api.AuditLog;
import com.matbakhy.operations.api.AuditLog.Entry;
import com.matbakhy.operations.api.AuditLog.Outcome;
import com.matbakhy.orders.api.OrderCases;
import com.matbakhy.orders.api.OrderDirectory;
import com.matbakhy.orders.api.OrderDirectory.Status;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import com.matbakhy.shared.idempotency.IdempotentCommands;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order-scoped support cases (BR-041). Customers see only their own cases and only PUBLIC messages; STAFF_ONLY notes
 * never leave the staff views. Food-safety cases are CRITICAL and escalated at creation. A cancellation request after
 * acceptance (BR-028) is a case: it never changes the order; staff resolve it by an exception cancellation or a decline.
 * SLA first-response targets are proposed defaults (CRITICAL 30 min, HIGH 2 h, NORMAL 24 h) until configuration (MB-026).
 */
@Service
public class SupportService implements OrderCases {

    public static final Set<String> CUSTOMER_CATEGORIES = Set.of("LATE", "MISSING_ITEM", "QUALITY", "FOOD_SAFETY", "CASH", "HANDOVER", "OTHER");
    static final Set<String> CANCELLATION_REASONS = Set.of("CHANGED_MIND", "ORDERED_BY_MISTAKE", "TOO_SLOW", "OTHER");
    static final Set<String> RESOLUTIONS = Set.of("INFORMATION_PROVIDED", "ORDER_CANCELLED", "CANCELLATION_DECLINED", "REFUND_REQUESTED",
            "KITCHEN_FOLLOW_UP", "DUPLICATE", "NO_ACTION", "OTHER");
    static final Set<String> CANCELLABLE_BY_REQUEST = Set.of("ACCEPTED", "PREPARING", "READY", "OUT_FOR_DELIVERY");
    private static final Set<String> CLOSED = Set.of("RESOLVED", "CLOSED");
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Message(UUID id, String authorRole, String visibility, String body, Instant createdAt) {
    }

    /** Customer view: no assignee, SLA or staff notes. */
    public record SupportCase(UUID id, String reference, UUID orderId, String orderNumber, String category, String state, String description,
                              String resolutionCode, Instant createdAt, Instant updatedAt, List<Message> messages, long version) {
    }

    public record StaffCase(UUID id, String reference, UUID orderId, String orderNumber, UUID customerId, String category, String severity,
                            String state, String description, String cancellationReason, UUID assigneeId, Instant slaDueAt, boolean overdue,
                            Instant firstResponseAt, Instant escalatedAt, String resolutionCode, Instant resolvedAt, Instant createdAt,
                            Instant updatedAt, List<Message> messages, long version) {
    }

    public record CasePage<T>(List<T> items, String nextCursor, boolean hasMore) {
    }

    public record StaffUpdate(Boolean assignToMe, String state, String resolutionCode, String reason, Long expectedVersion) {
    }

    private final JdbcTemplate jdbc;
    private final OrderDirectory orders;
    private final IdempotentCommands idempotency;
    private final AuditLog audit;
    private final Clock clock;

    SupportService(JdbcTemplate jdbc, OrderDirectory orders, IdempotentCommands idempotency, AuditLog audit, Clock clock) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.idempotency = idempotency;
        this.audit = audit;
        this.clock = clock;
    }

    // ---- customer ----

    @Transactional
    public SupportCase open(UUID customerId, UUID orderId, String category, String description) {
        if (category == null || !CUSTOMER_CATEGORIES.contains(category)) {
            throw invalid("category", "ALLOWED_" + String.join("|", CUSTOMER_CATEGORIES.stream().sorted().toList()));
        }
        Status order = ownOrder(customerId, orderId);
        UUID id = insertCase(order, category, text("description", description, 10, 4000), null);
        return customerCase(customerId, id);
    }

    /** API-028: after acceptance the customer asks; the order itself is not changed here. */
    @Transactional
    public SupportCase requestCancellation(UUID customerId, String key, UUID orderId, Long expectedVersion, String reasonCode, String note) {
        UUID id = idempotency.execute(customerId, "order.cancellation_request", key, Map.of("orderId", orderId.toString(),
                "expectedVersion", String.valueOf(expectedVersion), "reasonCode", String.valueOf(reasonCode), "note", String.valueOf(note)), UUID.class, () -> {
            Status order = ownOrder(customerId, orderId);
            if (expectedVersion == null || expectedVersion != order.version()) {
                throw new ApiException(ErrorCode.VERSION_CONFLICT, "stale order version");
            }
            if ("PENDING_ACCEPTANCE".equals(order.state())) {
                throw new ApiException(ErrorCode.STATE_CONFLICT, "pending orders are cancelled directly", List.of(new FieldError("state", order.state())));
            }
            if (!CANCELLABLE_BY_REQUEST.contains(order.state())) {
                throw new ApiException(ErrorCode.STATE_CONFLICT, "order is " + order.state(), List.of(new FieldError("state", order.state())));
            }
            if (reasonCode == null || !CANCELLATION_REASONS.contains(reasonCode)) {
                throw invalid("reasonCode", "ALLOWED_" + String.join("|", CANCELLATION_REASONS.stream().sorted().toList()));
            }
            String description = note == null || note.isBlank() ? "Cancellation requested: " + reasonCode : text("note", note, 1, 3900);
            if (description.length() < 10) {
                description = "Cancellation requested: " + description;
            }
            try {
                return insertCase(order, "CANCELLATION_REQUEST", description, reasonCode);
            } catch (DuplicateKeyException e) {
                throw new ApiException(ErrorCode.STATE_CONFLICT, "a cancellation request is already open",
                        List.of(new FieldError("orderId", "OPEN_CANCELLATION_REQUEST")));
            }
        });
        return customerCase(customerId, id);
    }

    public CasePage<SupportCase> ownCases(UUID customerId, String cursor, int limit) {
        Keyset k = keyset(cursor, limit);
        List<UUID> ids = jdbc.query("""
                SELECT id FROM support_case WHERE customer_id = ? AND (created_at, id) < (?, ?) ORDER BY created_at DESC, id DESC LIMIT ?""",
                (rs, n) -> rs.getObject(1, UUID.class), customerId, Timestamp.from(k.time()), k.id(), limit + 1);
        boolean more = ids.size() > limit;
        List<SupportCase> page = (more ? ids.subList(0, limit) : ids).stream().map(id -> withoutMessages(customerCase(customerId, id))).toList();
        return new CasePage<>(page, more ? cursorOf(page.getLast().createdAt(), page.getLast().id()) : null, more);
    }

    /** Another customer's case is indistinguishable from a missing one. */
    public SupportCase customerCase(UUID customerId, UUID caseId) {
        Row r = row(caseId).filter(x -> x.customerId().equals(customerId)).orElseThrow(SupportService::notFound);
        List<Message> messages = messages(caseId, false);
        return new SupportCase(r.id(), r.reference(), r.orderId(), r.orderNumber(), r.category(), r.state(), r.description(), r.resolutionCode(),
                r.createdAt(), r.updatedAt(), messages, r.version());
    }

    @Transactional
    public Message customerMessage(UUID customerId, String key, UUID caseId, String body) {
        UUID id = idempotency.execute(customerId, "case.message", key, Map.of("caseId", caseId.toString(), "body", String.valueOf(body)), UUID.class, () -> {
            Row r = lock(caseId).filter(x -> x.customerId().equals(customerId)).orElseThrow(SupportService::notFound);
            if ("CLOSED".equals(r.state())) {
                throw new ApiException(ErrorCode.STATE_CONFLICT, "case is closed");
            }
            UUID m = insertMessage(caseId, customerId, "CUSTOMER", "PUBLIC", text("body", body, 1, 4000));
            // A customer reply puts a waiting or resolved case back in the queue.
            String next = Set.of("WAITING_ON_CUSTOMER", "RESOLVED").contains(r.state()) ? "OPEN" : r.state();
            jdbc.update("UPDATE support_case SET state = ?, resolution_code = CASE WHEN ? = 'OPEN' THEN NULL ELSE resolution_code END, "
                    + "resolved_at = CASE WHEN ? = 'OPEN' THEN NULL ELSE resolved_at END, updated_at = ?, version = version + 1 WHERE id = ?",
                    next, next, next, Timestamp.from(clock.instant()), caseId);
            return m;
        });
        return message(id);
    }

    // ---- staff ----

    public CasePage<StaffCase> queue(UUID staffId, String state, String category, String severity, boolean mine, boolean overdueOnly,
                                     String cursor, int limit) {
        if (state != null && !Set.of("OPEN", "IN_PROGRESS", "WAITING_ON_CUSTOMER", "RESOLVED", "CLOSED", "ACTIVE").contains(state)) {
            throw invalid("state", "UNKNOWN_STATE");
        }
        if (severity != null && !Set.of("NORMAL", "HIGH", "CRITICAL").contains(severity)) {
            throw invalid("severity", "UNKNOWN_SEVERITY");
        }
        if (category != null && !CUSTOMER_CATEGORIES.contains(category) && !"CANCELLATION_REQUEST".equals(category)) {
            throw invalid("category", "UNKNOWN_CATEGORY");
        }
        Keyset k = keyset(cursor, limit);
        Instant now = clock.instant();
        // Most urgent first: severity, then SLA due time; the cursor walks (rank, due, id).
        List<UUID> ids = jdbc.query("""
                SELECT id, rank, sla_due_at FROM (
                  SELECT id, sla_due_at, CASE severity WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 ELSE 2 END AS rank FROM support_case
                  WHERE (?::text IS NULL OR (? = 'ACTIVE' AND state NOT IN ('RESOLVED', 'CLOSED')) OR state = ?)
                    AND (?::text IS NULL OR category = ?) AND (?::text IS NULL OR severity = ?)
                    AND (NOT ? OR assignee_id = ?) AND (NOT ? OR (first_response_at IS NULL AND sla_due_at < ? AND state NOT IN ('RESOLVED', 'CLOSED')))
                ) q WHERE (rank, sla_due_at, id) > (?, ?, ?) ORDER BY rank, sla_due_at, id LIMIT ?""",
                (rs, n) -> rs.getObject(1, UUID.class), state, state, state, category, category, severity, severity, mine, staffId, overdueOnly,
                Timestamp.from(now), k.rank(), Timestamp.from(k.time()), k.id(), limit + 1);
        boolean more = ids.size() > limit;
        List<StaffCase> page = (more ? ids.subList(0, limit) : ids).stream().map(id -> staffCase(id, false)).toList();
        String next = null;
        if (more) {
            StaffCase last = page.getLast();
            int rank = "CRITICAL".equals(last.severity()) ? 0 : "HIGH".equals(last.severity()) ? 1 : 2;
            next = Base64.getUrlEncoder().withoutPadding().encodeToString((last.slaDueAt() + "|" + last.id() + "|" + rank).getBytes(StandardCharsets.UTF_8));
        }
        return new CasePage<>(page, next, more);
    }

    /** A staff view of one case (customer description and conversation) is audited; the row holds no case content (MB-039). */
    public StaffCase viewStaffCase(UUID staffId, UUID caseId, String requestId) {
        StaffCase c = staffCase(caseId, true);
        audit.record(new Entry(staffId, "access.support_case", "support.case", "support_case", caseId.toString(), Outcome.ALLOWED,
                "staff view of a support case", requestId, Map.of()));
        return c;
    }

    public StaffCase staffCase(UUID caseId, boolean withMessages) {
        Row r = row(caseId).orElseThrow(SupportService::notFound);
        Instant now = clock.instant();
        boolean overdue = r.firstResponseAt() == null && !CLOSED.contains(r.state()) && r.slaDueAt().isBefore(now);
        return new StaffCase(r.id(), r.reference(), r.orderId(), r.orderNumber(), r.customerId(), r.category(), r.severity(), r.state(),
                r.description(), r.cancellationReason(), r.assigneeId(), r.slaDueAt(), overdue, r.firstResponseAt(), r.escalatedAt(),
                r.resolutionCode(), r.resolvedAt(), r.createdAt(), r.updatedAt(), withMessages ? messages(caseId, true) : List.of(), r.version());
    }

    @Transactional
    public Message staffMessage(UUID staffId, String key, UUID caseId, String body, String visibility, String requestId) {
        if (!Set.of("PUBLIC", "STAFF_ONLY").contains(visibility)) {
            throw invalid("visibility", "PUBLIC_OR_STAFF_ONLY");
        }
        UUID id = idempotency.execute(staffId, "case.staff_message", key, Map.of("caseId", caseId.toString(), "body", String.valueOf(body),
                "visibility", visibility), UUID.class, () -> {
            Row r = lock(caseId).orElseThrow(SupportService::notFound);
            if ("CLOSED".equals(r.state())) {
                throw new ApiException(ErrorCode.STATE_CONFLICT, "case is closed");
            }
            UUID m = insertMessage(caseId, staffId, "STAFF", visibility, text("body", body, 1, 4000));
            Instant now = clock.instant();
            jdbc.update("""
                    UPDATE support_case SET first_response_at = CASE WHEN ? = 'PUBLIC' THEN coalesce(first_response_at, ?) ELSE first_response_at END,
                           state = CASE WHEN state = 'OPEN' THEN 'IN_PROGRESS' ELSE state END, updated_at = ?, version = version + 1 WHERE id = ?""",
                    visibility, Timestamp.from(now), Timestamp.from(now), caseId);
            audit.record(new Entry(staffId, "case.message", Permission.SUPPORT_CASE.code(), "support_case", caseId.toString(), Outcome.ALLOWED, null,
                    requestId, Map.of("visibility", visibility)));
            return m;
        });
        return message(id);
    }

    @Transactional
    public StaffCase update(UUID staffId, UUID caseId, StaffUpdate in, String requestId) {
        Row r = lock(caseId).orElseThrow(SupportService::notFound);
        if (in.expectedVersion() == null || in.expectedVersion() != r.version()) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "stale case version");
        }
        if ("CLOSED".equals(r.state())) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "case is closed");
        }
        String state = in.state() == null ? r.state() : in.state();
        if (!Set.of("OPEN", "IN_PROGRESS", "WAITING_ON_CUSTOMER", "RESOLVED", "CLOSED").contains(state)) {
            throw invalid("state", "UNKNOWN_STATE");
        }
        if ("CLOSED".equals(state) && !"RESOLVED".equals(r.state())) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "only resolved cases can be closed");
        }
        String resolution = r.resolutionCode();
        if ("RESOLVED".equals(state) && !"RESOLVED".equals(r.state())) {
            if (in.resolutionCode() == null || !RESOLUTIONS.contains(in.resolutionCode())) {
                throw invalid("resolutionCode", "REQUIRED");
            }
            resolution = in.resolutionCode();
            if ("ORDER_CANCELLED".equals(resolution)) {
                // The order must actually have been cancelled (exception cancellation with this case reference).
                String orderState = orders.status(r.orderId()).map(Status::state).orElse("");
                if (!"CANCELLED".equals(orderState)) {
                    throw new ApiException(ErrorCode.STATE_CONFLICT, "order is " + orderState, List.of(new FieldError("resolutionCode", "ORDER_NOT_CANCELLED")));
                }
            }
        } else if (!CLOSED.contains(state)) {
            resolution = null;
        }
        String reason = in.reason() == null || in.reason().isBlank() ? null : text("reason", in.reason(), 3, 1000);
        if ("RESOLVED".equals(state) && reason == null) {
            throw invalid("reason", "REQUIRED");
        }
        UUID assignee = Boolean.TRUE.equals(in.assignToMe()) ? staffId : r.assigneeId();
        Instant now = clock.instant();
        jdbc.update("""
                UPDATE support_case SET state = ?, resolution_code = ?, resolved_at = CASE WHEN ? = 'RESOLVED' THEN coalesce(resolved_at, ?)
                       WHEN ? IN ('OPEN', 'IN_PROGRESS', 'WAITING_ON_CUSTOMER') THEN NULL ELSE resolved_at END,
                       assignee_id = ?, updated_at = ?, version = version + 1 WHERE id = ?""",
                state, resolution, state, Timestamp.from(now), state, assignee, Timestamp.from(now), caseId);
        audit.record(new Entry(staffId, "case.update", Permission.SUPPORT_CASE.code(), "support_case", caseId.toString(), Outcome.ALLOWED, reason,
                requestId, Map.of("from", r.state(), "to", state, "resolutionCode", String.valueOf(resolution), "assigned", String.valueOf(assignee))));
        return staffCase(caseId, true);
    }

    // ---- OrderCases port ----

    @Override
    public List<UUID> caseIds(UUID orderId) {
        return jdbc.queryForList("SELECT id FROM support_case WHERE order_id = ? ORDER BY created_at, id", UUID.class, orderId);
    }

    @Override
    public Set<UUID> ordersWithOpenCases() {
        return new HashSet<>(jdbc.queryForList("SELECT DISTINCT order_id FROM support_case WHERE state NOT IN ('RESOLVED', 'CLOSED')", UUID.class));
    }

    /**
     * The description is system text: no recipient account, transfer reference or amount (those stay in the payment
     * history, visible to the parties and finance). The customer sees it in their own case list.
     */
    @Override
    public UUID openPaymentCase(UUID orderId, String paymentState) {
        Status order = orders.status(orderId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "order not found"));
        String description = "Deposit needs reconciliation (" + paymentState + "). Staff will follow up with you and the kitchen. "
                + "العربون يحتاج مراجعة، وسيتواصل فريق الدعم معك ومع المطبخ.";
        Instant now = clock.instant();
        byte[] ref = new byte[4];
        RANDOM.nextBytes(ref);
        jdbc.update("""
                INSERT INTO support_case (id, reference, order_id, customer_id, category, severity, description, sla_due_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'PAYMENT', 'HIGH', ?, ?, ?, ?)
                ON CONFLICT (order_id) WHERE category = 'PAYMENT' AND state NOT IN ('RESOLVED', 'CLOSED') DO NOTHING""",
                UUID.randomUUID(), "CS-" + HexFormat.of().withUpperCase().formatHex(ref), order.orderId(), order.customerId(), description,
                Timestamp.from(now.plus(Duration.ofHours(2))), Timestamp.from(now), Timestamp.from(now));
        return jdbc.queryForObject("SELECT id FROM support_case WHERE order_id = ? AND category = 'PAYMENT' AND state NOT IN ('RESOLVED', 'CLOSED')",
                UUID.class, orderId);
    }

    // ---- internals ----

    private record Row(UUID id, String reference, UUID orderId, String orderNumber, UUID customerId, String category, String severity, String state,
                       String description, String cancellationReason, UUID assigneeId, Instant slaDueAt, Instant firstResponseAt,
                       Instant escalatedAt, String resolutionCode, Instant resolvedAt, Instant createdAt, Instant updatedAt, long version) {
    }

    private record Keyset(int rank, Instant time, UUID id) {
    }

    private static final String SELECT = "SELECT * FROM support_case WHERE id = ?";

    /** Order numbers come from the orders module's directory, never from its tables. */
    private java.util.Optional<Row> row(UUID caseId) {
        return jdbc.query(SELECT, this::map, caseId).stream().findFirst();
    }

    private java.util.Optional<Row> lock(UUID caseId) {
        return jdbc.query(SELECT + " FOR UPDATE", this::map, caseId).stream().findFirst();
    }

    private Status ownOrder(UUID customerId, UUID orderId) {
        return orders.status(orderId).filter(o -> o.customerId().equals(customerId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "order not found"));
    }

    private UUID insertCase(Status order, String category, String description, String cancellationReason) {
        String severity = "FOOD_SAFETY".equals(category) ? "CRITICAL"
                : Set.of("CANCELLATION_REQUEST", "LATE", "HANDOVER").contains(category) ? "HIGH" : "NORMAL";
        Duration sla = switch (severity) {
            case "CRITICAL" -> Duration.ofMinutes(30);
            case "HIGH" -> Duration.ofHours(2);
            default -> Duration.ofHours(24);
        };
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        byte[] ref = new byte[4];
        RANDOM.nextBytes(ref);
        jdbc.update("""
                INSERT INTO support_case (id, reference, order_id, customer_id, category, severity, description, cancellation_reason, sla_due_at,
                                          escalated_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                id, "CS-" + HexFormat.of().withUpperCase().formatHex(ref), order.orderId(), order.customerId(), category, severity, description,
                cancellationReason, Timestamp.from(now.plus(sla)), "CRITICAL".equals(severity) ? Timestamp.from(now) : null, Timestamp.from(now),
                Timestamp.from(now));
        return id;
    }

    private UUID insertMessage(UUID caseId, UUID authorId, String role, String visibility, String body) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO case_message (id, case_id, author_id, author_role, visibility, body, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, caseId, authorId, role, visibility, body, Timestamp.from(clock.instant()));
        return id;
    }

    private List<Message> messages(UUID caseId, boolean includeStaffOnly) {
        return jdbc.query("SELECT id, author_role, visibility, body, created_at FROM case_message WHERE case_id = ? AND (? OR visibility = 'PUBLIC') "
                + "ORDER BY created_at, id", (rs, n) -> new Message(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getTimestamp(5).toInstant()), caseId, includeStaffOnly);
    }

    private Message message(UUID id) {
        return jdbc.query("SELECT id, author_role, visibility, body, created_at FROM case_message WHERE id = ?", (rs, n) -> new Message(
                rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant()), id).getFirst();
    }

    private static SupportCase withoutMessages(SupportCase c) {
        return new SupportCase(c.id(), c.reference(), c.orderId(), c.orderNumber(), c.category(), c.state(), c.description(), c.resolutionCode(),
                c.createdAt(), c.updatedAt(), List.of(), c.version());
    }

    private Row map(ResultSet rs, int n) throws SQLException {
        UUID orderId = rs.getObject("order_id", UUID.class);
        String orderNumber = orders.parties(orderId).map(OrderDirectory.Parties::publicNumber).orElse(null);
        return new Row(rs.getObject("id", UUID.class), rs.getString("reference"), orderId, orderNumber,
                rs.getObject("customer_id", UUID.class), rs.getString("category"), rs.getString("severity"), rs.getString("state"),
                rs.getString("description"), rs.getString("cancellation_reason"), rs.getObject("assignee_id", UUID.class), instant(rs, "sla_due_at"),
                instant(rs, "first_response_at"), instant(rs, "escalated_at"), rs.getString("resolution_code"), instant(rs, "resolved_at"),
                instant(rs, "created_at"), instant(rs, "updated_at"), rs.getLong("version"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }

    private static Keyset keyset(String cursor, int limit) {
        if (limit < 1 || limit > 100) {
            throw invalid("limit", "RANGE_1_100");
        }
        if (cursor == null) {
            return new Keyset(-1, Instant.parse("9999-12-31T00:00:00Z"), new UUID(-1L, -1L));
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", 3);
            return new Keyset(parts.length > 2 ? Integer.parseInt(parts[2]) : -1, Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException e) {
            throw invalid("cursor", "INVALID");
        }
    }

    private static String cursorOf(Instant time, UUID id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((time + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    private static String text(String field, String value, int min, int max) {
        String v = value == null ? "" : value.strip();
        if (v.length() < min || v.length() > max) {
            throw invalid(field, "LENGTH_" + min + "_" + max);
        }
        return v;
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, "case not found");
    }

    private static ApiException invalid(String field, String code) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "invalid " + field, List.of(new FieldError(field, code)));
    }
}
