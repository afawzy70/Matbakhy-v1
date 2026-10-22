package com.matbakhy.app.mfa;

import static org.assertj.core.api.Assertions.assertThat;

import com.matbakhy.app.mfa.MfaPolicyService.Category;
import com.matbakhy.app.mfa.MfaPolicyService.Decision;
import com.matbakhy.app.mfa.MfaPolicyService.Mode;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** D18 precedence rules without a database: policy per category, most protective applicable mode wins, preference only adds. */
class MfaPolicyResolveTest {

    private static Map<Category, Mode> policy(Mode customer, Mode kitchen, Mode staff) {
        Map<Category, Mode> p = new EnumMap<>(Category.class);
        p.put(Category.CUSTOMER, customer);
        p.put(Category.KITCHEN, kitchen);
        p.put(Category.STAFF, staff);
        return p;
    }

    private static Decision decide(Set<Category> categories, Map<Category, Mode> policy, boolean preference) {
        return MfaPolicyService.resolve(categories, policy, preference);
    }

    @Test
    void the_preview_default_optional_requires_mfa_only_for_users_who_opted_in() {
        Map<Category, Mode> optional = policy(Mode.OPTIONAL, Mode.OPTIONAL, Mode.OPTIONAL);
        assertThat(decide(EnumSet.of(Category.CUSTOMER), optional, false).required()).isFalse();
        assertThat(decide(EnumSet.of(Category.CUSTOMER), optional, true).required()).isTrue();
        assertThat(decide(EnumSet.of(Category.CUSTOMER, Category.KITCHEN, Category.STAFF), optional, false).required()).isFalse();
        assertThat(decide(EnumSet.of(Category.STAFF), optional, true).available()).isTrue();
    }

    @Test
    void required_cannot_be_overridden_by_the_users_preference() {
        Map<Category, Mode> staffRequired = policy(Mode.OPTIONAL, Mode.OPTIONAL, Mode.REQUIRED);
        Decision staff = decide(EnumSet.of(Category.CUSTOMER, Category.STAFF), staffRequired, false);
        assertThat(staff.required()).as("preference off does not relax REQUIRED").isTrue();
        assertThat(staff.effectiveMode()).isEqualTo(Mode.REQUIRED);
        assertThat(decide(EnumSet.of(Category.CUSTOMER), staffRequired, false).required()).as("other categories are unaffected").isFalse();
    }

    @Test
    void disabled_makes_mfa_unavailable_and_ignores_a_stored_preference_without_erasing_it() {
        Map<Category, Mode> off = policy(Mode.DISABLED, Mode.DISABLED, Mode.DISABLED);
        Decision d = decide(EnumSet.of(Category.CUSTOMER), off, true);
        assertThat(d.available()).isFalse();
        assertThat(d.required()).isFalse();
        assertThat(d.preference()).as("the stored preference is reported, not silently removed").isTrue();
    }

    @Test
    void the_most_protective_applicable_category_wins() {
        Map<Category, Mode> mixed = policy(Mode.DISABLED, Mode.OPTIONAL, Mode.REQUIRED);
        assertThat(decide(EnumSet.of(Category.CUSTOMER, Category.KITCHEN), mixed, false).effectiveMode()).isEqualTo(Mode.OPTIONAL);
        assertThat(decide(EnumSet.of(Category.CUSTOMER, Category.KITCHEN), mixed, false).available()).isTrue();
        assertThat(decide(EnumSet.of(Category.CUSTOMER, Category.KITCHEN), mixed, true).required()).isTrue();
        assertThat(decide(EnumSet.of(Category.CUSTOMER, Category.KITCHEN, Category.STAFF), mixed, false).required()).isTrue();
        assertThat(decide(EnumSet.of(Category.CUSTOMER), mixed, true).available()).as("a customer-only account stays DISABLED").isFalse();
    }

    @Test
    void a_missing_policy_fails_safe_to_required_never_to_disabled() {
        Decision d = decide(EnumSet.of(Category.CUSTOMER), new EnumMap<>(Category.class), false);
        assertThat(d.effectiveMode()).isEqualTo(Mode.REQUIRED);
        assertThat(d.required()).isTrue();
    }

    @Test
    void an_assertion_older_than_the_last_reset_does_not_satisfy_the_policy() {
        java.time.Instant reset = java.time.Instant.parse("2026-10-18T12:00:00Z");
        Decision base = MfaPolicyService.resolve(EnumSet.of(Category.CUSTOMER), policy(Mode.REQUIRED, Mode.OPTIONAL, Mode.OPTIONAL), false);
        Decision afterReset = new Decision(base.categories(), base.modes(), base.effectiveMode(), base.preference(), base.required(), base.available(), reset);
        java.util.List<String> acr = java.util.List.of("mfa", "2");
        Map<String, Object> before = Map.of("acr", "2", "auth_time", reset.minusSeconds(1).getEpochSecond());
        Map<String, Object> after = Map.of("acr", "2", "auth_time", reset.getEpochSecond());
        assertThat(base.satisfiedBy(before, acr)).as("no reset recorded").isTrue();
        assertThat(afterReset.satisfiedBy(before, acr)).isFalse();
        assertThat(afterReset.satisfiedBy(after, acr)).isTrue();
        assertThat(afterReset.satisfiedBy(Map.of("acr", "2"), acr)).as("no auth_time, cannot prove it is newer").isFalse();
        assertThat(afterReset.satisfiedBy(Map.of("acr", "1", "auth_time", reset.plusSeconds(60).getEpochSecond()), acr)).isFalse();
    }
}
