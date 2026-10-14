package com.matbakhy.cooks.web;

import com.matbakhy.shared.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;

final class Requests {

    private Requests() {
    }

    static String id(HttpServletRequest request) {
        Object id = request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
        return id == null ? null : id.toString();
    }
}
