package com.matbakhy.app.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.matbakhy.identity.api.EgyptianPhone;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EgyptianPhoneTest {

    @ParameterizedTest
    @ValueSource(strings = {"01012345678", "+201012345678", "00201012345678", "201012345678", "010 1234 5678", "010-1234-5678",
            "٠١٠١٢٣٤٥٦٧٨", "+20 (10) 1234-5678"})
    void accepts_egyptian_mobile_forms(String raw) {
        assertThat(EgyptianPhone.normalize(raw)).hasValue("+201012345678");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0223456789", "+20223456789", "01312345678", "0101234567", "010123456789", "+965 9999 9999",
            "01012345678x", "", "++201012345678", "0101234567٨٨"})
    void rejects_landlines_foreign_and_malformed(String raw) {
        assertThat(EgyptianPhone.normalize(raw)).isEmpty();
    }
}
