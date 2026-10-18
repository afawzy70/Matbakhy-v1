package com.matbakhy.orders.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.matbakhy.orders.internal.OrderService.PlaceRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** QA-002 / CR3-001: the request fingerprint is canonical and collision-free across field boundaries. */
class OrderFingerprintTest {

    private static final UUID QUOTE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static PlaceRequest req(String terms, String cancellation, String note) {
        return new PlaceRequest(QUOTE, REQUEST, terms, cancellation, note);
    }

    @Test
    void the_same_logical_request_has_one_fingerprint_whitespace_and_blank_notes_normalised() {
        assertThat(OrderService.fingerprint(req("t1", "c1", "Ring twice"))).isEqualTo(OrderService.fingerprint(req("t1", "c1", "  Ring twice ")));
        assertThat(OrderService.fingerprint(req("t1", "c1", null))).isEqualTo(OrderService.fingerprint(req("t1", "c1", "   ")));
    }

    @Test
    void values_cannot_move_across_field_boundaries_to_collide() {
        assertThat(OrderService.fingerprint(req("t1", "c1\nx", "")))
                .isNotEqualTo(OrderService.fingerprint(req("t1", "c1", "x")));
        assertThat(OrderService.fingerprint(req("t1|2:c1", "", "n"))).isNotEqualTo(OrderService.fingerprint(req("t1", "c1", "n")));
        assertThat(OrderService.fingerprint(req("a", "bc", "d"))).isNotEqualTo(OrderService.fingerprint(req("ab", "c", "d")));
    }

    @Test
    void any_material_difference_changes_the_fingerprint() {
        String base = OrderService.fingerprint(req("t1", "c1", "n"));
        assertThat(OrderService.fingerprint(req("t2", "c1", "n"))).isNotEqualTo(base);
        assertThat(OrderService.fingerprint(req("t1", "c2", "n"))).isNotEqualTo(base);
        assertThat(OrderService.fingerprint(req("t1", "c1", "m"))).isNotEqualTo(base);
        assertThat(OrderService.fingerprint(new PlaceRequest(UUID.randomUUID(), REQUEST, "t1", "c1", "n"))).isNotEqualTo(base);
    }
}
