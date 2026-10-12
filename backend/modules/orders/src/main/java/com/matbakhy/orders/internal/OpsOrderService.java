package com.matbakhy.orders.internal;

import com.matbakhy.operations.api.AuditLog;
import com.matbakhy.operations.api.AuditLog.Entry;
import com.matbakhy.operations.api.AuditLog.Outcome;
import com.matbakhy.orders.api.OrderCases;
import com.matbakhy.orders.internal.OrderService.Money;
import com.matbakhy.orders.internal.OrderService.OrderView;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * API-074/075: the operations order queue with failure flags (acceptance about to expire, delivery failed, cash
 * disputed, open support case) and the staff order view. Customer contact is included only while the order is being
 * fulfilled; handover codes never.
 */
@Service
public class OpsOrderService {

    static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");
    static final Set<String> EXCEPTIONS = Set.of("OVERDUE_ACCEPTANCE", "DELIVERY_FAILED", "CASH_DISPUTED", "OPEN_CASE");

    public record OpsOrderSummary(UUID id, String publicNumber, String state, String fulfillmentMode, UUID kitchenId, Money total, Instant acceptBy,
                                  Instant createdAt, String cashState, List<String> exceptions, long version) {
    }

    public record OpsOrderPage(List<OpsOrderSummary> items, String nextCursor, boolean hasMore) {
    }

    public record Contact(String name, String phone, Object deliveryAddress) {
    }

    public record OpsOrderView(OrderView order, UUID kitchenId, UUID customerId, Contact customerContact, List<String> exceptions,
                               List<UUID> caseIds) {
    }

    private final JdbcTemplate jdbc;
    private final OrderService orders;
    private final OrderCases cases;
    private final JsonMapper json;
    private final Clock clock;
    private final AuditLog audit;

    OpsOrderService(JdbcTemplate jdbc, OrderService orders, OrderCases cases, JsonMapper json, Clock clock, AuditLog audit) {
        this.audit = audit;
        this.jdbc = jdbc;
        this.orders = orders;
        this.cases = cases;
        this.json = json;
        this.clock = clock;
    }

    public OpsOrderPage list(String state, UUID kitchenId, String from, String to, String exception, String cursor, int limit) {
        if (state != null && !OrderLifecycleService.TERMINAL.contains(state) && !OrderService.ACTIVE.contains(state)) {
            throw invalid("state", "UNKNOWN_STATE");
        }
        if (exception != null && !EXCEPTIONS.contains(exception)) {
            throw invalid("exception", "UNKNOWN_EXCEPTION");
        }
        if (limit < 1 || limit > 100) {
            throw invalid("limit", "RANGE_1_100");
        }
        Instant start = from == null ? Instant.EPOCH : date("from", from).atStartOfDay(CAIRO).toInstant();
        Instant end = to == null ? Instant.parse("9999-12-31T00:00:00Z") : date("to", to).plusDays(1).atStartOfDay(CAIRO).toInstant();
        Instant beforeTime = Instant.parse("9999-12-31T00:00:00Z");
        UUID beforeId = new UUID(-1L, -1L);
        if (cursor != null) {
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", 2);
                beforeTime = Instant.parse(parts[0]);
                beforeId = UUID.fromString(parts[1]);
            } catch (RuntimeException e) {
                throw invalid("cursor", "INVALID");
            }
        }
        Instant soon = clock.instant().plusSeconds(300);
        UUID[] withCases = cases.ordersWithOpenCases().toArray(UUID[]::new);
        List<OpsOrderSummary> rows = jdbc.query("""
                SELECT o.id, o.public_number, o.state, o.fulfillment_mode, o.kitchen_id, o.total_minor, o.accept_by, o.created_at, c.state AS cash_state,
                       o.version
                FROM customer_order o LEFT JOIN cash_obligation c ON c.order_id = o.id
                WHERE (?::text IS NULL OR o.state = ?) AND (?::uuid IS NULL OR o.kitchen_id = ?) AND o.created_at >= ? AND o.created_at < ?
                  AND (?::text IS NULL
                       OR (? = 'OVERDUE_ACCEPTANCE' AND o.state = 'PENDING_ACCEPTANCE' AND o.accept_by <= ?)
                       OR (? = 'DELIVERY_FAILED' AND o.state = 'DELIVERY_FAILED')
                       OR (? = 'CASH_DISPUTED' AND c.state = 'DISPUTED')
                       OR (? = 'OPEN_CASE' AND o.id = ANY (?)))
                  AND (o.created_at, o.id) < (?, ?)
                ORDER BY o.created_at DESC, o.id DESC LIMIT ?""",
                (rs, n) -> summary(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getObject(5, UUID.class),
                        rs.getLong(6), rs.getTimestamp(7).toInstant(), rs.getTimestamp(8).toInstant(), rs.getString(9), rs.getLong(10), soon, withCases),
                state, state, kitchenId, kitchenId, Timestamp.from(start), Timestamp.from(end), exception, exception, Timestamp.from(soon), exception,
                exception, exception, withCases, Timestamp.from(beforeTime), beforeId, limit + 1);
        boolean more = rows.size() > limit;
        List<OpsOrderSummary> page = more ? rows.subList(0, limit) : rows;
        String next = more ? Base64.getUrlEncoder().withoutPadding().encodeToString((page.getLast().createdAt() + "|" + page.getLast().id())
                .getBytes(StandardCharsets.UTF_8)) : null;
        return new OpsOrderPage(page, next, more);
    }

    /** A view that discloses the customer's contact (name, phone, address) is audited; the row holds no contact data (MB-039). */
    public OpsOrderView view(UUID orderId, UUID staffId, String requestId) {
        OrderView order = orders.staffView(orderId);
        Map<String, Object> o = jdbc.queryForMap("""
                SELECT o.kitchen_id, o.customer_id, o.accept_by, o.contact_snapshot::text AS contact, o.address_snapshot::text AS address,
                       c.state AS cash_state FROM customer_order o LEFT JOIN cash_obligation c ON c.order_id = o.id WHERE o.id = ?""", orderId);
        Contact contact = null;
        if (OrderService.CONTACT_STATES.contains(order.state()) || "PENDING_ACCEPTANCE".equals(order.state())) {
            JsonNode c = json.readTree((String) o.get("contact"));
            contact = new Contact(text(c, "displayName"), text(c, "phoneE164"), o.get("address") == null ? null : json.readTree((String) o.get("address")));
            audit.record(new Entry(staffId, "access.order_contact", "order.ops", "order", orderId.toString(), Outcome.ALLOWED,
                    "staff view of customer contact", requestId, Map.of()));
        }
        UUID[] withCases = cases.ordersWithOpenCases().toArray(UUID[]::new);
        OpsOrderSummary s = summary(orderId, order.publicNumber(), order.state(), order.fulfillmentMode(), (UUID) o.get("kitchen_id"),
                order.total().amountMinor(), ((Timestamp) o.get("accept_by")).toInstant(), Instant.EPOCH, (String) o.get("cash_state"), order.version(),
                clock.instant().plusSeconds(300), withCases);
        return new OpsOrderView(order, (UUID) o.get("kitchen_id"), (UUID) o.get("customer_id"), contact, s.exceptions(), cases.caseIds(orderId));
    }

    private static OpsOrderSummary summary(UUID id, String number, String state, String mode, UUID kitchenId, long total, Instant acceptBy,
                                           Instant createdAt, String cashState, long version, Instant soon, UUID[] withCases) {
        List<String> flags = new ArrayList<>();
        if ("PENDING_ACCEPTANCE".equals(state) && !acceptBy.isAfter(soon)) {
            flags.add("OVERDUE_ACCEPTANCE");
        }
        if ("DELIVERY_FAILED".equals(state)) {
            flags.add("DELIVERY_FAILED");
        }
        if ("DISPUTED".equals(cashState)) {
            flags.add("CASH_DISPUTED");
        }
        if (java.util.Arrays.asList(withCases).contains(id)) {
            flags.add("OPEN_CASE");
        }
        return new OpsOrderSummary(id, number, state, mode, kitchenId, new Money(total, "EGP"), acceptBy, createdAt, cashState == null ? "NOT_DUE" : cashState,
                flags, version);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asString();
    }

    private static LocalDate date(String field, String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw invalid(field, "ISO_DATE");
        }
    }

    private static ApiException invalid(String field, String code) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "invalid " + field, List.of(new FieldError(field, code)));
    }
}
