package com.matbakhy.orders.internal;

import com.matbakhy.availability.api.SlotInventory;
import com.matbakhy.availability.api.SlotInventory.Reservation;
import com.matbakhy.catalog.api.MealCatalog;
import com.matbakhy.cooks.api.KitchenDirectory;
import com.matbakhy.cooks.api.KitchenDirectory.FulfillmentContact;
import com.matbakhy.identity.api.AddressDirectory;
import com.matbakhy.identity.api.AddressDirectory.OwnedAddress;
import com.matbakhy.identity.api.CheckoutContactPolicy;
import com.matbakhy.identity.api.CheckoutContactPolicy.VerifiedContact;
import com.matbakhy.orders.api.OrderCases;
import com.matbakhy.orders.api.QuoteDirectory;
import com.matbakhy.orders.api.QuoteDirectory.QuotedItem;
import com.matbakhy.orders.api.QuoteDirectory.ValidQuote;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import com.matbakhy.shared.idempotency.IdempotentCommands;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Order placement (BR-022..BR-025) and the customer's order views. Placement runs in one transaction: quote
 * revalidation, stock reservation with deterministic locks, order/item/address/contact snapshots, the first event,
 * an outbox message and the cash obligation. Duplicates are prevented twice: by the idempotency record (actor +
 * command + key, 24 h) and permanently by the unique customer + clientRequestId.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    public static final Set<String> ACTIVE = Set.of("PENDING_ACCEPTANCE", "ACCEPTED", "PREPARING", "READY", "OUT_FOR_DELIVERY");

    public record PlaceRequest(UUID quoteId, UUID clientRequestId, String termsVersion, String cancellationPolicyVersion,
                               String fulfillmentNote) {
    }

    public record Money(long amountMinor, String currency) {
    }

    public record OrderLine(UUID mealId, String title, String allergenStatement, int quantity, Money unitPrice, Money lineTotal) {
    }

    public record SlotView(UUID id, Instant startsAt, Instant endsAt, String timezone, Instant cutoffAt, int availablePortions,
                           List<String> fulfillmentModes) {
    }

    public record OrderEvent(int sequence, String fromState, String toState, Instant occurredAt, String actorType, String reasonCode) {
    }

    public record OrderView(UUID id, String publicNumber, long version, String state, String fulfillmentMode, List<OrderLine> items,
                            Money subtotal, Money deliveryFee, Money tax, Money total, SlotView slot, Instant acceptBy,
                            List<OrderEvent> events, List<String> allowedActions, String cashState, List<UUID> supportCaseIds,
                            String handoverCode, KitchenContact contact, PaymentService.PaymentView payment) {
    }

    /** Kitchen contact for the order owner after acceptance; the pickup location only for pickup orders. */
    public record KitchenContact(String kitchenName, String phone, String pickupLocation) {
    }

    /** Minimum fulfillment contact for the cook while the order is active; never the handover code. */
    public record CustomerContact(String name, String phone, Object deliveryAddress) {
    }

    public record CookOrderView(UUID id, String publicNumber, long version, String state, String fulfillmentMode, List<OrderLine> items,
                                Money subtotal, Money deliveryFee, Money tax, Money total, SlotView slot, Instant acceptBy,
                                List<OrderEvent> events, List<String> allowedActions, String cashState, String fulfillmentNote,
                                String dispatchReference, CustomerContact contact, PaymentService.PaymentView payment) {
    }

    public record CookOrderPage(List<CookOrderView> items, String nextCursor, boolean hasMore) {
    }

    public record OrderPage(List<OrderView> items, String nextCursor, boolean hasMore) {
    }

    public record OrderEventPage(List<OrderEvent> items, String nextCursor, boolean hasMore) {
    }

    private final JdbcTemplate jdbc;
    private final QuoteDirectory quotes;
    private final SlotInventory inventory;
    private final MealCatalog meals;
    private final AddressDirectory addresses;
    private final CheckoutContactPolicy contact;
    private final IdempotentCommands idempotency;
    private final JsonMapper json;
    private final KitchenDirectory kitchens;
    private final OrderCases cases;
    private final HandoverCodes codes;
    private final PaymentService payments;
    private final Clock clock;

    OrderService(JdbcTemplate jdbc, QuoteDirectory quotes, SlotInventory inventory, MealCatalog meals, AddressDirectory addresses,
                 CheckoutContactPolicy contact, IdempotentCommands idempotency, JsonMapper json, KitchenDirectory kitchens,
                 HandoverCodes codes, OrderCases cases, PaymentService payments, Clock clock) {
        this.jdbc = jdbc;
        this.quotes = quotes;
        this.inventory = inventory;
        this.meals = meals;
        this.addresses = addresses;
        this.contact = contact;
        this.idempotency = idempotency;
        this.json = json;
        this.kitchens = kitchens;
        this.codes = codes;
        this.cases = cases;
        this.payments = payments;
        this.clock = clock;
    }

    @Transactional
    public OrderView place(UUID customerId, String idempotencyKey, PlaceRequest in, boolean english) {
        UUID orderId = idempotency.execute(customerId, "order.place", idempotencyKey, in, UUID.class, () -> placeOnce(customerId, in));
        return view(customerId, orderId, english);
    }

    /**
     * Canonical, versioned digest of the placement request: the same logical request always yields the same value and
     * different requests never share one (every field is length-prefixed, so no value can imitate a delimiter).
     */
    static String fingerprint(PlaceRequest in) {
        String note = in.fulfillmentNote() == null || in.fulfillmentNote().isBlank() ? "" : in.fulfillmentNote().strip();
        StringBuilder canonical = new StringBuilder("v1");
        for (String field : new String[] {String.valueOf(in.quoteId()), String.valueOf(in.clientRequestId()), String.valueOf(in.termsVersion()),
                String.valueOf(in.cancellationPolicyVersion()), note}) {
            canonical.append('|').append(field.length()).append(':').append(field);
        }
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private UUID placeOnce(UUID customerId, PlaceRequest in) {
        // Serialize attempts carrying the same permanent request reference.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> null, "order-request:" + customerId + ":" + in.clientRequestId());
        String fingerprint = fingerprint(in);
        Map<String, Object> existing = jdbc.queryForList("SELECT id, quote_id, request_fingerprint FROM customer_order WHERE customer_id = ? AND client_request_id = ?",
                customerId, in.clientRequestId()).stream().findFirst().orElse(null);
        if (existing != null) {
            // Orders placed before V29 have no stored fingerprint and keep the quote-only check.
            Object stored = existing.get("request_fingerprint");
            boolean same = in.quoteId().equals(existing.get("quote_id")) && (stored == null || fingerprint.equals(stored));
            if (same) {
                return (UUID) existing.get("id");
            }
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, "clientRequestId already used for a different request",
                    List.of(new FieldError("clientRequestId", "ALREADY_USED")));
        }
        ValidQuote q = quotes.requireValid(customerId, in.quoteId());
        if (!q.termsVersion().equals(in.termsVersion()) || !q.cancellationPolicyVersion().equals(in.cancellationPolicyVersion())) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "current policies not accepted",
                    List.of(new FieldError("acceptedPolicyVersions", "POLICY_NOT_ACCEPTED")));
        }
        Boolean used = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM customer_order WHERE quote_id = ?)", Boolean.class, in.quoteId());
        if (Boolean.TRUE.equals(used)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "quote already used", List.of(new FieldError("quoteId", "QUOTE_ALREADY_USED")));
        }
        VerifiedContact who = contact.verifiedContact(customerId);
        Map<UUID, Integer> wanted = new LinkedHashMap<>();
        q.items().forEach(i -> wanted.put(i.mealId(), i.quantity()));
        List<Reservation> reservations = inventory.reserve(q.slotId(), wanted);

        String addressSnapshot = null;
        if (q.addressId() != null) {
            OwnedAddress a = addresses.owned(customerId, q.addressId())
                    .orElseThrow(() -> new ApiException(ErrorCode.STATE_CONFLICT, "address gone", List.of(new FieldError("quoteId", "ADDRESS_CHANGED"))));
            addressSnapshot = json.writeValueAsString(a);
        }
        Instant now = clock.instant();
        UUID orderId = UUID.randomUUID();
        Long number = jdbc.queryForObject("SELECT nextval('order_public_number_seq')", Long.class);
        long commission = Math.floorDiv(Math.addExact(Math.multiplyExact(q.subtotalMinor(), (long) q.commissionRateBps()), 5_000L), 10_000L);
        String note = in.fulfillmentNote() == null || in.fulfillmentNote().isBlank() ? null : in.fulfillmentNote().strip();
        jdbc.update("""
                INSERT INTO customer_order (id, public_number, customer_id, client_request_id, request_fingerprint, quote_id, kitchen_id, slot_id, state, fulfillment_mode,
                                            address_snapshot, contact_snapshot, fulfillment_note, subtotal_minor, delivery_fee_minor, tax_minor,
                                            total_minor, policy_version, commission_bps_snapshot, commission_minor, cancellation_policy_version,
                                            terms_version, accept_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_ACCEPTANCE', ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                orderId, "MB-" + number, customerId, in.clientRequestId(), fingerprint, in.quoteId(), q.kitchenId(), q.slotId(), q.fulfillmentMode(),
                addressSnapshot, json.writeValueAsString(who), note, q.subtotalMinor(), q.deliveryFeeMinor(), q.taxMinor(), q.totalMinor(),
                q.policyVersion(), q.commissionRateBps(), commission, q.cancellationPolicyVersion(), q.termsVersion(), Timestamp.from(q.acceptBy()),
                Timestamp.from(now), Timestamp.from(now));
        for (QuotedItem item : q.items()) {
            String snapshot = meals.revisionSnapshot(item.mealId(), item.mealRevision())
                    .orElseThrow(() -> new IllegalStateException("missing meal revision " + item.mealId() + "/" + item.mealRevision()));
            jdbc.update("""
                    INSERT INTO order_item (id, order_id, meal_id, meal_revision, meal_snapshot, quantity, unit_price_minor, line_total_minor)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)""",
                    UUID.randomUUID(), orderId, item.mealId(), item.mealRevision(), snapshot, item.quantity(), item.unitPriceMinor(), item.lineTotalMinor());
        }
        for (Reservation r : reservations) {
            jdbc.update("INSERT INTO stock_allocation (id, order_id, meal_slot_id, quantity, state, created_at) VALUES (?, ?, ?, ?, 'RESERVED', ?)",
                    UUID.randomUUID(), orderId, r.mealSlotId(), r.quantity(), Timestamp.from(now));
        }
        jdbc.update("""
                INSERT INTO order_event (id, order_id, sequence, from_state, to_state, actor_type, actor_id, occurred_at)
                VALUES (?, ?, 1, NULL, 'PENDING_ACCEPTANCE', 'CUSTOMER', ?, ?)""", UUID.randomUUID(), orderId, customerId, Timestamp.from(now));
        jdbc.update("""
                INSERT INTO outbox_event (id, aggregate_type, aggregate_id, event_type, payload, available_at, created_at, request_id)
                VALUES (?, 'order', ?, 'ORDER_PLACED', ?::jsonb, ?, ?, ?)""",
                UUID.randomUUID(), orderId, json.writeValueAsString(Map.of("orderId", orderId.toString(), "kitchenId", q.kitchenId().toString())),
                Timestamp.from(now), Timestamp.from(now), org.slf4j.MDC.get(com.matbakhy.shared.web.RequestContext.REQUEST_ID_MDC_KEY));
        // MB-051: the payment terms are snapshotted with the order; cash due at hand-over is what the deposit does not cover.
        payments.createFor(orderId, customerId, q.paymentMethod(), q.totalMinor(), q.depositMinor(),
                q.recipientIdentifier() == null ? null : new PaymentService.Recipient(q.recipientMethod(), q.recipientIdentifier(), q.recipientName()),
                q.acceptBy(), now);
        jdbc.update("INSERT INTO cash_obligation (id, order_id, amount_minor, state, created_at) VALUES (?, ?, ?, 'DUE', ?)",
                UUID.randomUUID(), orderId, q.totalMinor() - q.depositMinor(), Timestamp.from(now));
        log.info("order {} recorded in its placement transaction", orderId);
        return orderId;
    }

    // ---- customer reads ----

    public OrderView get(UUID customerId, UUID orderId, boolean english) {
        return view(customerId, orderId, english);
    }

    public OrderView byRequest(UUID customerId, UUID clientRequestId, boolean english) {
        UUID id = jdbc.query("SELECT id FROM customer_order WHERE customer_id = ? AND client_request_id = ?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, customerId, clientRequestId);
        if (id == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "no order for this request");
        }
        return view(customerId, id, english);
    }

    public OrderPage list(UUID customerId, String stateGroup, String cursor, int limit, boolean english) {
        if (stateGroup != null && !Set.of("ACTIVE", "PAST").contains(stateGroup)) {
            throw invalid("stateGroup", "ACTIVE_OR_PAST");
        }
        if (limit < 1 || limit > 100) {
            throw invalid("limit", "RANGE_1_100");
        }
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
        String group = stateGroup == null ? "" : stateGroup.equals("ACTIVE")
                ? " AND state IN ('PENDING_ACCEPTANCE','ACCEPTED','PREPARING','READY','OUT_FOR_DELIVERY')"
                : " AND state NOT IN ('PENDING_ACCEPTANCE','ACCEPTED','PREPARING','READY','OUT_FOR_DELIVERY')";
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, created_at FROM customer_order WHERE customer_id = ?" + group
                + " AND (created_at, id) < (?, ?) ORDER BY created_at DESC, id DESC LIMIT ?",
                customerId, Timestamp.from(beforeTime), beforeId, limit + 1);
        boolean more = rows.size() > limit;
        List<Map<String, Object>> page = more ? rows.subList(0, limit) : rows;
        String next = null;
        if (more) {
            Map<String, Object> last = page.getLast();
            next = Base64.getUrlEncoder().withoutPadding().encodeToString((((Timestamp) last.get("created_at")).toInstant() + "|" + last.get("id"))
                    .getBytes(StandardCharsets.UTF_8));
        }
        return new OrderPage(page.stream().map(r -> view(customerId, (UUID) r.get("id"), english)).toList(), next, more);
    }

    public OrderEventPage events(UUID customerId, UUID orderId, int afterSequence, int limit) {
        requireOwned(customerId, orderId);
        if (limit < 1 || limit > 100 || afterSequence < 0) {
            throw invalid("limit", "RANGE_1_100");
        }
        List<OrderEvent> events = eventsAfter(orderId, afterSequence, limit + 1);
        boolean more = events.size() > limit;
        List<OrderEvent> page = more ? events.subList(0, limit) : events;
        return new OrderEventPage(page, more ? Integer.toString(page.getLast().sequence()) : null, more);
    }

    private void requireOwned(UUID customerId, UUID orderId) {
        Boolean owned = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM customer_order WHERE id = ? AND customer_id = ?)", Boolean.class,
                orderId, customerId);
        if (!Boolean.TRUE.equals(owned)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "order not found");
        }
    }

    private List<OrderEvent> eventsAfter(UUID orderId, int afterSequence, int limit) {
        return jdbc.query("""
                SELECT sequence, from_state, to_state, occurred_at, actor_type, reason_code FROM order_event
                WHERE order_id = ? AND sequence > ? ORDER BY sequence LIMIT ?""",
                (rs, n) -> new OrderEvent(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant(), rs.getString(5),
                        rs.getString(6)), orderId, afterSequence, limit);
    }

    /** Customer view; another customer's order is indistinguishable from a missing one (404). */
    private OrderView view(UUID customerId, UUID orderId, boolean english) {
        Map<String, Object> o = jdbc.queryForList("""
                SELECT o.*, s.starts_at, s.ends_at, s.cutoff_at, s.max_portions - s.reserved - s.allocated - s.consumed AS slot_available,
                       c.state AS cash_state
                FROM customer_order o JOIN cook_slot s ON s.id = o.slot_id LEFT JOIN cash_obligation c ON c.order_id = o.id
                WHERE o.id = ? AND o.customer_id = ?""", orderId, customerId).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "order not found"));
        List<OrderLine> lines = jdbc.query("""
                SELECT meal_id, meal_snapshot::text, quantity, unit_price_minor, line_total_minor FROM order_item WHERE order_id = ? ORDER BY meal_id""",
                (rs, n) -> {
                    JsonNode snap = json.readTree(rs.getString(2));
                    String title = text(snap, english ? "titleEn" : "titleAr");
                    if (title == null) {
                        title = text(snap, "titleAr");
                    }
                    return new OrderLine(rs.getObject(1, UUID.class), title == null ? "" : title, allergenStatement(snap), rs.getInt(3),
                            egp(rs.getLong(4)), egp(rs.getLong(5)));
                }, orderId);
        String state = (String) o.get("state");
        SlotView slot = new SlotView((UUID) o.get("slot_id"), ((Timestamp) o.get("starts_at")).toInstant(), ((Timestamp) o.get("ends_at")).toInstant(),
                "Africa/Cairo", ((Timestamp) o.get("cutoff_at")).toInstant(), Math.max(0, ((Number) o.get("slot_available")).intValue()),
                List.of((String) o.get("fulfillment_mode")));
        // After acceptance the customer can only ask (a support case); staff decide (BR-028).
        List<String> actions = "PENDING_ACCEPTANCE".equals(state) ? List.of("CANCEL")
                : CONTACT_STATES.contains(state) ? List.of("REQUEST_CANCELLATION") : List.of();
        String mode = (String) o.get("fulfillment_mode");
        KitchenContact kitchenContact = null;
        if (CONTACT_STATES.contains(state)) {
            FulfillmentContact k = kitchens.fulfillmentContact((UUID) o.get("kitchen_id")).orElse(null);
            if (k != null) {
                kitchenContact = new KitchenContact(k.displayName(), k.contactPhone(), "PICKUP".equals(mode) ? k.pickupLocation() : null);
            }
        }
        return new OrderView(orderId, (String) o.get("public_number"), (Long) o.get("version"), state, mode, lines,
                egp((Long) o.get("subtotal_minor")), egp((Long) o.get("delivery_fee_minor")), egp((Long) o.get("tax_minor")),
                egp((Long) o.get("total_minor")), slot, ((Timestamp) o.get("accept_by")).toInstant(), eventsAfter(orderId, 0, 1000), actions,
                o.get("cash_state") == null ? "NOT_DUE" : (String) o.get("cash_state"), cases.caseIds(orderId), handoverCode(orderId, state), kitchenContact,
                payments.view(orderId).orElse(null));
    }

    static final Set<String> CONTACT_STATES = Set.of("ACCEPTED", "PREPARING", "READY", "OUT_FOR_DELIVERY");

    /** Only for the order owner, while READY or out for delivery and the challenge is usable. */
    private String handoverCode(UUID orderId, String state) {
        if (!"READY".equals(state) && !"OUT_FOR_DELIVERY".equals(state)) {
            return null;
        }
        return jdbc.query("SELECT ciphertext, nonce FROM handover_challenge "
                + "WHERE order_id = ? AND consumed_at IS NULL AND locked_at IS NULL AND expires_at > ?",
                rs -> rs.next() ? codes.reveal(orderId, rs.getBytes(1), rs.getBytes(2)) : null, orderId, Timestamp.from(clock.instant()));
    }

    /** Staff view after an ops command: never the customer's handover code or contact details. */
    public OrderView staffView(UUID orderId) {
        UUID customer = jdbc.query("SELECT customer_id FROM customer_order WHERE id = ?", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, orderId);
        if (customer == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "order not found");
        }
        OrderView v = view(customer, orderId, false);
        return new OrderView(v.id(), v.publicNumber(), v.version(), v.state(), v.fulfillmentMode(), v.items(), v.subtotal(), v.deliveryFee(), v.tax(),
                v.total(), v.slot(), v.acceptBy(), v.events(), List.of(), v.cashState(), v.supportCaseIds(), null, null, v.payment());
    }

    // ---- cook reads ----

    private UUID cookKitchen(UUID cookId) {
        return kitchens.ownedBy(cookId).map(KitchenDirectory.KitchenStatus::kitchenId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "not a cook"));
    }

    public CookOrderPage cookOrders(UUID cookId, String state, String cursor, int limit, boolean english) {
        UUID kitchen = cookKitchen(cookId);
        if (state != null && !OrderLifecycleService.TERMINAL.contains(state) && !ACTIVE.contains(state)) {
            throw invalid("state", "UNKNOWN_STATE");
        }
        if (limit < 1 || limit > 100) {
            throw invalid("limit", "RANGE_1_100");
        }
        Instant afterTime = Instant.EPOCH;
        UUID afterId = new UUID(0, 0);
        if (cursor != null) {
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", 2);
                afterTime = Instant.parse(parts[0]);
                afterId = UUID.fromString(parts[1]);
            } catch (RuntimeException e) {
                throw invalid("cursor", "INVALID");
            }
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, created_at FROM customer_order WHERE kitchen_id = ? "
                + "AND (?::text IS NULL OR state = ?) AND (created_at, id) > (?, ?) ORDER BY created_at, id LIMIT ?",
                kitchen, state, state, Timestamp.from(afterTime), afterId, limit + 1);
        boolean more = rows.size() > limit;
        List<Map<String, Object>> page = more ? rows.subList(0, limit) : rows;
        String next = null;
        if (more) {
            Map<String, Object> last = page.getLast();
            next = Base64.getUrlEncoder().withoutPadding().encodeToString((((Timestamp) last.get("created_at")).toInstant() + "|" + last.get("id"))
                    .getBytes(StandardCharsets.UTF_8));
        }
        return new CookOrderPage(page.stream().map(r -> cookView(kitchen, (UUID) r.get("id"), english)).toList(), next, more);
    }

    public CookOrderView cookOrder(UUID cookId, UUID orderId, boolean english) {
        return cookView(cookKitchen(cookId), orderId, english);
    }

    /** Cook view scoped to the kitchen (another kitchen's order is 404); contact only while the order is active. */
    private CookOrderView cookView(UUID kitchenId, UUID orderId, boolean english) {
        UUID customer = jdbc.query("SELECT customer_id FROM customer_order WHERE id = ? AND kitchen_id = ?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, orderId, kitchenId);
        if (customer == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "order not found");
        }
        OrderView v = view(customer, orderId, english);
        Map<String, Object> o = jdbc.queryForMap("SELECT contact_snapshot::text AS contact, address_snapshot::text AS address, "
                + "fulfillment_note, dispatch_reference FROM customer_order WHERE id = ?", orderId);
        CustomerContact contact = null;
        if (CONTACT_STATES.contains(v.state())) {
            JsonNode c = json.readTree((String) o.get("contact"));
            Object address = o.get("address") == null ? null : json.readTree((String) o.get("address"));
            contact = new CustomerContact(text(c, "displayName"), text(c, "phoneE164"), address);
        }
        boolean showNote = CONTACT_STATES.contains(v.state()) || "PENDING_ACCEPTANCE".equals(v.state());
        return new CookOrderView(v.id(), v.publicNumber(), v.version(), v.state(), v.fulfillmentMode(), v.items(), v.subtotal(), v.deliveryFee(),
                v.tax(), v.total(), v.slot(), v.acceptBy(), v.events(), cookActions(v.state(), v.fulfillmentMode()), v.cashState(),
                showNote ? (String) o.get("fulfillment_note") : null, (String) o.get("dispatch_reference"), contact, v.payment());
    }

    private static List<String> cookActions(String state, String mode) {
        return switch (state) {
            case "PENDING_ACCEPTANCE" -> List.of("ACCEPT", "REJECT");
            case "ACCEPTED" -> List.of("PREPARE", "CANCEL");
            case "PREPARING" -> List.of("READY");
            case "READY" -> "PICKUP".equals(mode) ? List.of("HANDOVER") : List.of("DISPATCH");
            case "OUT_FOR_DELIVERY" -> List.of("HANDOVER", "DELIVERY_FAILURE");
            default -> List.of();
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asString();
    }

    /** Snapshot of the declaration at order time: codes for CONTAINS, otherwise NONE_DECLARED or UNKNOWN, plus the cook's note. */
    private static String allergenStatement(JsonNode snap) {
        String declaration = text(snap, "allergenDeclaration");
        List<String> codes = new ArrayList<>();
        JsonNode list = snap.get("allergenCodes");
        if (list != null) {
            list.forEach(c -> codes.add(c.asString()));
        }
        String base = "CONTAINS".equals(declaration) ? String.join(", ", codes) : declaration == null ? "UNKNOWN" : declaration;
        String note = text(snap, "allergenNote");
        return note == null ? base : base + " — " + note;
    }

    private static Money egp(long minor) {
        return new Money(minor, "EGP");
    }

    private static ApiException invalid(String field, String code) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "invalid " + field, List.of(new FieldError(field, code)));
    }
}
