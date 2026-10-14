package com.matbakhy.app.web;

/**
 * A copy of a failure for logs that keeps exception types and stack frames but drops every message. Driver and
 * framework messages can quote the values involved (for example a constraint violation echoing a phone number), so
 * unexpected failures are logged through this copy (MB-039, BR-056).
 */
public final class RedactedThrowable extends RuntimeException {

    private RedactedThrowable(Throwable original, Throwable cause) {
        super(original.getClass().getName() + " (message redacted)", cause, false, true);
        setStackTrace(original.getStackTrace());
    }

    public static RedactedThrowable of(Throwable original) {
        return of(original, 0);
    }

    private static RedactedThrowable of(Throwable original, int depth) {
        Throwable cause = original.getCause();
        boolean follow = cause != null && cause != original && depth < 20;
        return new RedactedThrowable(original, follow ? of(cause, depth + 1) : null);
    }

    @Override
    public String toString() {
        return getMessage();
    }
}
