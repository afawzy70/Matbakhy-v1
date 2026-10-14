package com.matbakhy.catalog.api;

import java.net.URI;
import java.time.Duration;
import java.util.OptionalLong;

/**
 * Object storage port (ADR-004). The PRIVATE bucket holds uploads, quarantine and verification documents and
 * is reachable only through short-lived signed URLs; the PUBLIC bucket holds approved, re-encoded variants.
 */
public interface ObjectStorage {

    enum Bucket { PRIVATE, PUBLIC }

    /** Signed PUT bound to the content type and length; the client uploads bytes directly. */
    URI presignUpload(String key, String contentType, long bytes, Duration ttl);

    /** Signed GET of a private object for an authorized reader. */
    URI presignRead(String key, Duration ttl);

    OptionalLong size(Bucket bucket, String key);

    /** Reads at most {@code maxBytes}; larger objects fail rather than being loaded. */
    byte[] read(Bucket bucket, String key, long maxBytes);

    void write(Bucket bucket, String key, byte[] content, String contentType);

    void delete(Bucket bucket, String key);

    /** Public address of an approved variant (CDN or public bucket). */
    URI publicUrl(String key);
}
