package com.matbakhy.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void synthetic_order_total_matches_data_model_example() {
        Money subtotal = Money.of(12_000, Money.EGP).times(2);
        Money total = subtotal.plus(Money.of(3_000, Money.EGP));
        assertThat(subtotal.amountMinor()).isEqualTo(24_000);
        assertThat(total).isEqualTo(Money.of(27_000, Money.EGP));
    }

    @Test
    void mixing_currencies_is_rejected() {
        assertThatThrownBy(() -> Money.of(100, "EGP").plus(Money.of(100, "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overflow_is_rejected_instead_of_wrapping() {
        assertThatThrownBy(() -> Money.of(Long.MAX_VALUE, "EGP").plus(Money.of(1, "EGP")))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> Money.of(Long.MAX_VALUE / 2 + 1, "EGP").times(2))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void currency_must_be_iso_code() {
        assertThatThrownBy(() -> Money.of(1, "egp")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(1, "EGPX")).isInstanceOf(IllegalArgumentException.class);
    }
}
