package com.matbakhy.catalog.web;

import com.matbakhy.catalog.internal.FavoriteService;
import com.matbakhy.catalog.internal.FavoriteService.Favorite;
import com.matbakhy.catalog.internal.FavoriteService.FavoritePage;
import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.shared.web.Languages;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API-018..020: the caller's own favorites. */
@RestController
class FavoriteController {

    private final FavoriteService favorites;

    FavoriteController(FavoriteService favorites) {
        this.favorites = favorites;
    }

    @GetMapping("/api/v1/me/favorites")
    FavoritePage list(@AuthenticationPrincipal CurrentAccount actor, @RequestParam(defaultValue = "50") int limit,
                      @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
        return favorites.list(actor.id(), Languages.prefersEnglish(language), limit);
    }

    @PutMapping("/api/v1/me/favorites/{mealId}")
    Favorite add(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID mealId,
                 @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
        return favorites.add(actor.id(), mealId, Languages.prefersEnglish(language));
    }

    @DeleteMapping("/api/v1/me/favorites/{mealId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID mealId) {
        favorites.remove(actor.id(), mealId);
    }
}
