package com.matbakhy.availability.web;

import com.matbakhy.availability.internal.SlotService;
import com.matbakhy.availability.internal.SlotService.CookSlot;
import com.matbakhy.availability.internal.SlotService.CookSlotPage;
import com.matbakhy.availability.internal.SlotService.CreateSlot;
import com.matbakhy.availability.internal.SlotService.MealCapacity;
import com.matbakhy.availability.internal.SlotService.SlotPage;
import com.matbakhy.availability.internal.SlotService.UpdateSlot;
import com.matbakhy.identity.api.CurrentAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API-006 (public kitchen slots) and API-050..052 (the caller's own kitchen slots). */
@RestController
class SlotController {

    private final SlotService slots;

    SlotController(SlotService slots) {
        this.slots = slots;
    }

    record MealCapacityBody(@NotNull UUID mealId, @NotNull @Min(0) Integer capacity) {
        MealCapacity value() {
            return new MealCapacity(mealId, capacity);
        }
    }

    record CreateBody(@NotNull LocalDate localDate, @NotNull LocalTime localStart, @NotNull LocalTime localEnd, String timezone,
                      @Size(max = 6) String startOffset, @Size(max = 6) String endOffset, @NotNull Integer maxPortions, Integer leadMinutes,
                      @Size(max = 50) List<@NotNull @Valid MealCapacityBody> mealCapacities) {
    }

    record UpdateBody(Integer maxPortions, @Size(max = 50) List<@NotNull @Valid MealCapacityBody> mealCapacities,
                      @Size(max = 20) String state, @NotNull @Min(0) Long expectedVersion) {
    }

    private static List<MealCapacity> meals(List<MealCapacityBody> bodies) {
        return bodies == null ? null : bodies.stream().map(MealCapacityBody::value).toList();
    }

    @GetMapping("/api/v1/kitchens/{kitchenId}/slots")
    SlotPage publicSlots(@PathVariable UUID kitchenId, @RequestParam(required = false) LocalDate date,
                         @RequestParam(required = false) String fulfillmentMode, @RequestParam(required = false) UUID zoneId,
                         @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        return slots.publicSlots(kitchenId, date, fulfillmentMode, zoneId, cursor, limit);
    }

    @GetMapping("/api/v1/cook/slots")
    CookSlotPage list(@AuthenticationPrincipal CurrentAccount actor, @RequestParam(required = false) LocalDate from,
                      @RequestParam(required = false) LocalDate to, @RequestParam(required = false) String cursor,
                      @RequestParam(defaultValue = "20") int limit) {
        return slots.list(actor.id(), from, to, cursor, limit);
    }

    @PostMapping("/api/v1/cook/slots")
    @ResponseStatus(HttpStatus.CREATED)
    CookSlot create(@AuthenticationPrincipal CurrentAccount actor, @Valid @RequestBody CreateBody body) {
        return slots.create(actor.id(), new CreateSlot(body.localDate(), body.localStart(), body.localEnd(), body.timezone(), body.startOffset(),
                body.endOffset(), body.maxPortions(), body.leadMinutes(), meals(body.mealCapacities())));
    }

    @PatchMapping("/api/v1/cook/slots/{slotId}")
    CookSlot update(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID slotId, @Valid @RequestBody UpdateBody body) {
        return slots.update(actor.id(), slotId, body.expectedVersion(), new UpdateSlot(body.maxPortions(), meals(body.mealCapacities()),
                body.state()));
    }
}
