package com.matbakhy.catalog.web;

import com.matbakhy.catalog.internal.meal.MealService;
import com.matbakhy.catalog.internal.meal.MealService.Action;
import com.matbakhy.catalog.internal.meal.MealService.Allergen;
import com.matbakhy.catalog.internal.meal.MealService.CookMeal;
import com.matbakhy.catalog.internal.meal.MealService.CookMealPage;
import com.matbakhy.catalog.internal.meal.MealService.Declaration;
import com.matbakhy.catalog.internal.meal.MealService.MealInput;
import com.matbakhy.catalog.internal.meal.MealService.Photo;
import com.matbakhy.catalog.internal.meal.MealService.MealDetail;
import com.matbakhy.identity.api.CurrentAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API-004 (public meal) and API-046..049 (the caller's own kitchen meals). */
@RestController
class MealController {

    private final MealService meals;

    MealController(MealService meals) {
        this.meals = meals;
    }

    record PhotoBody(@NotNull UUID mediaId, @Size(max = 200) String altAr, @Size(max = 200) String altEn) {
    }

    record MealBody(@Size(max = 200) String titleAr, @Size(max = 200) String titleEn, @Size(max = 4000) String descriptionAr,
                    @Size(max = 4000) String descriptionEn, @Size(max = 200) String portionAr, @Size(max = 200) String portionEn,
                    @Size(max = 4000) String ingredientsAr, @Size(max = 4000) String ingredientsEn, Declaration allergenDeclaration,
                    @Size(max = 14) List<@NotNull Allergen> allergenCodes, @Size(max = 1000) String allergenNote, Long priceMinor,
                    @Size(max = 10) List<@NotNull @Valid PhotoBody> photos, @Min(0) Long expectedVersion) {

        MealInput input() {
            return new MealInput(titleAr, titleEn, descriptionAr, descriptionEn, portionAr, portionEn, ingredientsAr, ingredientsEn,
                    allergenDeclaration, allergenCodes, allergenNote, priceMinor,
                    photos == null ? null : photos.stream().map(p -> new Photo(p.mediaId(), p.altAr(), p.altEn())).toList());
        }
    }

    record PublicationBody(@NotNull Action action, @NotNull @Min(0) Long expectedVersion) {
    }

    /** Arabic by default; English when the client prefers it and the meal has English text. */
    @GetMapping("/api/v1/meals/{mealId}")
    ResponseEntity<MealDetail> publicMeal(@PathVariable UUID mealId,
                                          @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        MealDetail meal = meals.publicMeal(mealId, "en".equals(preferredLanguage(acceptLanguage)));
        return ResponseEntity.ok().header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE).body(meal);
    }

    static String preferredLanguage(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return "ar";
        }
        try {
            String tag = Locale.lookupTag(Locale.LanguageRange.parse(acceptLanguage), List.of("ar", "en"));
            return tag == null ? "ar" : tag;
        } catch (IllegalArgumentException e) {
            return "ar";
        }
    }

    @GetMapping("/api/v1/cook/meals")
    CookMealPage list(@AuthenticationPrincipal CurrentAccount actor, @RequestParam(required = false) String status,
                      @RequestParam(required = false) String q, @RequestParam(required = false) String cursor,
                      @RequestParam(defaultValue = "20") int limit) {
        return meals.list(actor.id(), status, q, cursor, limit);
    }

    @PostMapping("/api/v1/cook/meals")
    @ResponseStatus(HttpStatus.CREATED)
    CookMeal create(@AuthenticationPrincipal CurrentAccount actor, @Valid @RequestBody MealBody body) {
        return meals.create(actor.id(), body.input());
    }

    @PatchMapping("/api/v1/cook/meals/{mealId}")
    CookMeal update(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID mealId, @Valid @RequestBody MealBody body) {
        return meals.update(actor.id(), mealId, body.expectedVersion(), body.input());
    }

    @PostMapping("/api/v1/cook/meals/{mealId}/publication")
    CookMeal publication(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID mealId, @Valid @RequestBody PublicationBody body) {
        return meals.changePublication(actor.id(), mealId, body.expectedVersion(), body.action());
    }
}
