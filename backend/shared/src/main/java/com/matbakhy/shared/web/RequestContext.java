package com.matbakhy.shared.web;

/** Names shared between the composition layer and modules for per-request correlation. */
public final class RequestContext {

    /** Servlet request attribute holding the validated request ID (set by the app's RequestIdFilter). */
    public static final String REQUEST_ID_ATTRIBUTE = "matbakhy.requestId";

    /** Logging MDC key holding the same validated request ID while a request (or work traced to one) runs. */
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private RequestContext() {
    }
}
