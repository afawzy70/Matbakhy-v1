package com.matbakhy.finance.web;

import com.matbakhy.finance.internal.StatementService;
import com.matbakhy.finance.internal.StatementService.StatementPage;
import com.matbakhy.identity.api.CurrentAccount;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-064: the cook's own statement (own kitchen only). */
@RestController
class StatementController {

    private final StatementService statements;

    StatementController(StatementService statements) {
        this.statements = statements;
    }

    @GetMapping("/api/v1/cook/statements")
    StatementPage statement(@AuthenticationPrincipal CurrentAccount actor, @RequestParam(required = false) String from,
                            @RequestParam(required = false) String to, @RequestParam(required = false) String cursor,
                            @RequestParam(defaultValue = "20") int limit) {
        return statements.statement(actor.id(), from, to, cursor, limit);
    }
}
