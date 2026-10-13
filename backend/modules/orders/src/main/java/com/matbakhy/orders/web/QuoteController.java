package com.matbakhy.orders.web;

import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.orders.internal.QuoteService;
import com.matbakhy.orders.internal.QuoteService.ItemRequest;
import com.matbakhy.orders.internal.QuoteService.Mode;
import com.matbakhy.orders.internal.PaymentService;
import com.matbakhy.orders.internal.QuoteService.QuoteRequest;
import com.matbakhy.orders.internal.QuoteService.QuoteResponse;
import com.matbakhy.shared.web.Languages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API-021. The body has no amounts; unknown fields such as forged totals are rejected (strict JSON, 400). */
@RestController
class QuoteController {

    private final QuoteService quotes;

    QuoteController(QuoteService quotes) {
        this.quotes = quotes;
    }

    record Item(@NotNull UUID mealId, @NotNull Integer quantity) {
    }

    record Body(@NotNull @Size(min = 1, max = 20) List<@NotNull @Valid Item> items, @NotNull UUID slotId, @NotNull Mode fulfillmentMode,
                UUID addressId, PaymentService.Method paymentMethod) {
    }

    @PostMapping("/api/v1/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    QuoteResponse quote(@AuthenticationPrincipal CurrentAccount actor, @Valid @RequestBody Body body,
                        @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        boolean english = Languages.prefersEnglish(acceptLanguage);
        return quotes.create(actor.id(), new QuoteRequest(body.items().stream().map(i -> new ItemRequest(i.mealId(), i.quantity())).toList(),
                body.slotId(), body.fulfillmentMode(), body.addressId(), body.paymentMethod()), english);
    }
}
