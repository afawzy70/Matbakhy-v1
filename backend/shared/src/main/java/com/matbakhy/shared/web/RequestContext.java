package com.matbakhy.shared.web;

/** Names shared between the composition layer and modules for per-request correlation. */
public final class RequestContext {

    /** Servlet request attribute holding the validated request ID (set by the app's RequestIdFilter). */
    public static final String REQUEST_ID_ATTRIBUTE = "matbakhy.requestId";

    private RequestContext() {
    }
}
