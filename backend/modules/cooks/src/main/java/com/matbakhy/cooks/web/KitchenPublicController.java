package com.matbakhy.cooks.web;

import com.matbakhy.cooks.internal.KitchenService;
import com.matbakhy.cooks.internal.KitchenService.KitchenPublic;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** API-005: public kitchen profile. No private location, contact phone or documents (BR-008). */
@RestController
class KitchenPublicController {

    private final KitchenService kitchens;

    KitchenPublicController(KitchenService kitchens) {
        this.kitchens = kitchens;
    }

    @GetMapping("/api/v1/kitchens/{slug}")
    KitchenPublic profile(@PathVariable String slug) {
        if (!slug.matches("[a-z0-9-]{3,80}")) {
            throw new com.matbakhy.shared.error.ApiException(com.matbakhy.shared.error.ErrorCode.NOT_FOUND, "bad slug");
        }
        return kitchens.publicProfile(slug);
    }
}
