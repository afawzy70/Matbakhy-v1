package com.matbakhy.orders.internal;

import com.matbakhy.availability.api.SlotDirectory;
import com.matbakhy.availability.api.SlotDirectory.SlotStatus;
import com.matbakhy.catalog.api.MealCatalog;
import com.matbakhy.catalog.api.MealCatalog.PricedMeal;
import com.matbakhy.catalog.api.SellerDirectory;
import com.matbakhy.catalog.api.SellerDirectory.Seller;
import com.matbakhy.identity.api.AddressDirectory;
import com.matbakhy.identity.api.AddressDirectory.OwnedAddress;
import com.matbakhy.identity.api.CheckoutContactPolicy;
import com.matbakhy.orders.api.QuoteDirectory;
import com.matbakhy.orders.internal.CommercePolicies.Policy;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authoritative quotes (BR-018..BR-021). The server derives every amount from current published meal revisions,
 * the zone/kitchen delivery fee and the active policy, in integer piastres; the request carries no amounts. A
 * quote is immutable, expires (default 5 minutes) and holds no stock.
 */
@Service
public class QuoteService implements QuoteDirectory {

    public enum Mode { PICKUP, DELIVERY }

    public record ItemRequest(UUID mealId, int quantity) {
    }

    public record QuoteRequest(List<ItemRequest> items, UUID slotId, Mode fulfillmentMode, UUID addressId) {
    }

    public record Money(long amountMinor, String currency) {
    }

    public record QuoteLine(UUID mealId, int mealRevision, String title, int quantity, Money unitPrice, Money lineTotal) {
    }

    public record QuoteResponse(UUID quoteId, Instant expiresAt, UUID kitchenId, List<QuoteLine> items, Money subtotal, Money deliveryFee,
                                Money tax, Money total, String cancellationPolicyVersion, Instant acceptanceDeadlinePreview,
                                List<String> warnings) {
    }

    static final int MAX_ITEMS = 20;

    private final JdbcTemplate jdbc;
    private final CommercePolicies policies;
    private final MealCatalog meals;
    private final SellerDirectory sellers;
    private final SlotDirectory slots;
    private final AddressDirectory addresses;
    private final CheckoutContactPolicy contact;
    private final Clock clock;

    QuoteService(JdbcTemplate jdbc, CommercePolicies policies, MealCatalog meals, SellerDirectory sellers, SlotDirectory slots,
                 AddressDirectory addresses, CheckoutContactPolicy contact, Clock clock) {
        this.jdbc = jdbc;
        this.policies = policies;
        this.meals = meals;
        this.sellers = sellers;
        this.slots = slots;
        this.addresses = addresses;
        this.contact = contact;
        this.clock = clock;
    }

    private record Priced(PricedMeal meal, int quantity, long lineTotal) {
    }

    private record Terms(Seller seller, SlotStatus slot, List<Priced> lines, long subtotal, long deliveryFee, long tax, long total,
                         OwnedAddress address, Instant acceptBy) {
    }

    @Transactional
    public QuoteResponse create(UUID customerId, QuoteRequest in, boolean english) {
        contact.requireVerifiedContact(customerId);
        Policy policy = policies.active();
        if (in.items() == null || in.items().isEmpty() || in.items().size() > MAX_ITEMS) {
            throw invalid("items", "COUNT_1_TO_20");
        }
        Set<UUID> seen = new HashSet<>();
        for (ItemRequest item : in.items()) {
            if (item.mealId() == null || !seen.add(item.mealId())) {
                throw invalid("items", "DUPLICATE_OR_MISSING_MEAL");
            }
            if (item.quantity() < 1 || item.quantity() > policy.maxItemQuantity()) {
                throw invalid("items.quantity", "RANGE_1_TO_" + policy.maxItemQuantity());
            }
        }
        if (in.slotId() == null || in.fulfillmentMode() == null) {
            throw invalid(in.slotId() == null ? "slotId" : "fulfillmentMode", "REQUIRED");
        }
        Instant now = clock.instant();
        Terms t = terms(customerId, in.items(), in.slotId(), in.fulfillmentMode(), in.addressId(), policy, now);

        UUID id = UUID.randomUUID();
        Instant expires = now.plus(Duration.ofSeconds(policy.quoteTtlSeconds()));
        boolean delivery = in.fulfillmentMode() == Mode.DELIVERY;
        jdbc.update("""
                INSERT INTO quote (id, customer_id, kitchen_id, slot_id, fulfillment_mode, address_id, address_version, zone_id, policy_version,
                                   subtotal_minor, delivery_fee_minor, tax_minor, total_minor, accept_by_preview, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                id, customerId, t.seller().kitchenId(), in.slotId(), in.fulfillmentMode().name(), delivery ? t.address().id() : null,
                delivery ? t.address().version() : null, delivery ? t.address().zoneId() : null, policy.version(), t.subtotal(), t.deliveryFee(),
                t.tax(), t.total(), Timestamp.from(t.acceptBy()), Timestamp.from(expires), Timestamp.from(now));
        List<QuoteLine> lines = new ArrayList<>();
        for (Priced p : t.lines()) {
            jdbc.update("""
                    INSERT INTO quote_item (quote_id, meal_id, meal_revision, quantity, unit_price_minor, line_total_minor)
                    VALUES (?, ?, ?, ?, ?, ?)""", id, p.meal().mealId(), p.meal().revision(), p.quantity(), p.meal().priceMinor(), p.lineTotal());
            String title = english && p.meal().titleEn() != null ? p.meal().titleEn() : p.meal().titleAr();
            lines.add(new QuoteLine(p.meal().mealId(), p.meal().revision(), title, p.quantity(), egp(p.meal().priceMinor()), egp(p.lineTotal())));
        }
        List<String> warnings = new ArrayList<>();
        if (policy.synthetic()) {
            warnings.add("SYNTHETIC_POLICY");
        }
        return new QuoteResponse(id, expires, t.seller().kitchenId(), lines, egp(t.subtotal()), egp(t.deliveryFee()), egp(t.tax()), egp(t.total()),
                policy.cancellationPolicyVersion(), t.acceptBy(), warnings);
    }

    @Override
    @Transactional(readOnly = true)
    public ValidQuote requireValid(UUID customerId, UUID quoteId) {
        Map<String, Object> q = jdbc.queryForList("SELECT * FROM quote WHERE id = ? AND customer_id = ?", quoteId, customerId).stream()
                .findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "quote not found"));
        Instant now = clock.instant();
        Instant expires = ((Timestamp) q.get("expires_at")).toInstant();
        if (!now.isBefore(expires)) {
            throw new ApiException(ErrorCode.QUOTE_EXPIRED, "quote expired", List.of(new FieldError("quoteId", "QUOTE_EXPIRED")));
        }
        contact.requireVerifiedContact(customerId);
        Policy policy = policies.active();
        if (policy.version() != (Integer) q.get("policy_version")) {
            throw changed(ErrorCode.PRICE_CHANGED, "POLICY_CHANGED");
        }
        List<QuotedItem> items = jdbc.query("""
                SELECT meal_id, meal_revision, quantity, unit_price_minor, line_total_minor FROM quote_item WHERE quote_id = ? ORDER BY meal_id""",
                (rs, n) -> new QuotedItem(rs.getObject(1, UUID.class), rs.getInt(2), rs.getInt(3), rs.getLong(4), rs.getLong(5)), quoteId);
        Mode mode = Mode.valueOf((String) q.get("fulfillment_mode"));
        UUID addressId = (UUID) q.get("address_id");
        Terms t = terms(customerId, items.stream().map(i -> new ItemRequest(i.mealId(), i.quantity())).toList(), (UUID) q.get("slot_id"), mode,
                addressId, policy, now);
        for (QuotedItem item : items) {
            Priced current = t.lines().stream().filter(p -> p.meal().mealId().equals(item.mealId())).findFirst().orElseThrow();
            if (current.meal().revision() != item.mealRevision() || current.meal().priceMinor() != item.unitPriceMinor()) {
                throw changed(ErrorCode.PRICE_CHANGED, "MEAL_CHANGED");
            }
        }
        if (addressId != null && (t.address().version() != (Long) q.get("address_version") || !t.address().zoneId().equals(q.get("zone_id")))) {
            throw changed(ErrorCode.STATE_CONFLICT, "ADDRESS_CHANGED");
        }
        if (t.deliveryFee() != (Long) q.get("delivery_fee_minor") || t.total() != (Long) q.get("total_minor")) {
            throw changed(ErrorCode.PRICE_CHANGED, "TOTAL_CHANGED");
        }
        return new ValidQuote(quoteId, customerId, t.seller().kitchenId(), (UUID) q.get("slot_id"), mode.name(), addressId,
                (Long) q.get("address_version"), (UUID) q.get("zone_id"), policy.version(), t.subtotal(), t.deliveryFee(), t.tax(), t.total(),
                ((Timestamp) q.get("accept_by_preview")).toInstant(), expires, items);
    }

    /** Recomputes the authoritative terms from current data; used for creation and revalidation. */
    private Terms terms(UUID customerId, List<ItemRequest> items, UUID slotId, Mode mode, UUID addressId, Policy policy, Instant now) {
        SlotStatus slot = slots.slot(slotId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "slot not found"));
        Seller seller = sellers.byKitchen(slot.kitchenId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "kitchen not found"));
        if (!seller.canSell()) {
            throw new ApiException(ErrorCode.KITCHEN_UNAVAILABLE, "kitchen not selling");
        }
        if (!slot.orderableAt(now)) {
            throw new ApiException(ErrorCode.SLOT_CLOSED, "slot not orderable", List.of(new FieldError("slotId", "SLOT_CLOSED")));
        }
        Instant acceptBy = now.plus(Duration.ofMinutes(policy.acceptanceWindowMinutes()));
        if (slot.cutoffAt().isBefore(acceptBy)) {
            acceptBy = slot.cutoffAt();
        }
        if (Duration.between(now, acceptBy).compareTo(Duration.ofMinutes(policy.minAcceptanceMinutes())) < 0) {
            throw new ApiException(ErrorCode.SLOT_CLOSED, "too close to cutoff", List.of(new FieldError("slotId", "ACCEPTANCE_WINDOW_TOO_SHORT")));
        }
        List<Priced> lines = new ArrayList<>();
        long subtotal = 0;
        int portions = 0;
        for (ItemRequest item : items) {
            PricedMeal meal = meals.current(item.mealId()).filter(PricedMeal::published)
                    .orElseThrow(() -> new ApiException(ErrorCode.STOCK_CHANGED, "meal not available",
                            List.of(new FieldError("items", "MEAL_NOT_AVAILABLE"))));
            if (!meal.kitchenId().equals(slot.kitchenId())) {
                // One cook and one slot per basket (BR-018); never split or convert silently.
                throw invalid("items", "MIXED_KITCHENS_OR_SLOT");
            }
            if (!slot.mealAvailablePortions().containsKey(meal.mealId())) {
                throw invalid("items", "MEAL_NOT_OFFERED_IN_SLOT");
            }
            if (item.quantity() > slot.availableFor(meal.mealId())) {
                throw new ApiException(ErrorCode.STOCK_CHANGED, "not enough portions", List.of(new FieldError("items.quantity", "INSUFFICIENT_PORTIONS")));
            }
            long line = Math.multiplyExact(meal.priceMinor(), (long) item.quantity());
            subtotal = Math.addExact(subtotal, line);
            portions += item.quantity();
            lines.add(new Priced(meal, item.quantity(), line));
        }
        if (portions > slot.availablePortions()) {
            throw new ApiException(ErrorCode.STOCK_CHANGED, "not enough portions in slot", List.of(new FieldError("items.quantity", "INSUFFICIENT_PORTIONS")));
        }
        long fee = 0;
        OwnedAddress address = null;
        if (mode == Mode.PICKUP) {
            if (addressId != null) {
                throw invalid("addressId", "NOT_ALLOWED_FOR_PICKUP");
            }
            if (!seller.pickupEnabled()) {
                throw invalid("fulfillmentMode", "PICKUP_NOT_OFFERED");
            }
        } else {
            if (addressId == null) {
                throw invalid("addressId", "REQUIRED_FOR_DELIVERY");
            }
            address = addresses.owned(customerId, addressId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "address not found"));
            OptionalLong zoneFee = meals.deliveryFee(seller.kitchenId(), address.zoneId());
            if (zoneFee.isEmpty()) {
                throw invalid("addressId", "NOT_SERVICEABLE");
            }
            fee = zoneFee.getAsLong();
        }
        long base = Math.addExact(subtotal, fee);
        // Tax rounds half up to whole piastres (synthetic 0 bps until O04 decides tax treatment).
        long tax = Math.floorDiv(Math.addExact(Math.multiplyExact(base, (long) policy.taxRateBps()), 5_000L), 10_000L);
        long total = Math.addExact(base, tax);
        return new Terms(seller, slot, lines, subtotal, fee, tax, total, address, acceptBy);
    }

    private static Money egp(long minor) {
        return new Money(minor, "EGP");
    }

    private static ApiException changed(ErrorCode code, String reason) {
        return new ApiException(code, "quote no longer valid: " + reason, List.of(new FieldError("quoteId", reason)));
    }

    private static ApiException invalid(String field, String code) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "invalid quote " + field, List.of(new FieldError(field, code)));
    }
}
