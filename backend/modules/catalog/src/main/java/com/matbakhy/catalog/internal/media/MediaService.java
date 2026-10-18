package com.matbakhy.catalog.internal.media;

import com.matbakhy.catalog.api.MalwareScanner;
import com.matbakhy.catalog.api.MalwareScanner.Verdict;
import com.matbakhy.catalog.api.MediaLibrary;
import com.matbakhy.catalog.api.ObjectStorage;
import com.matbakhy.catalog.api.ObjectStorage.Bucket;
import com.matbakhy.catalog.api.PrivateMediaAccess;
import com.matbakhy.identity.api.AccessPolicy;
import com.matbakhy.identity.api.Permission;
import com.matbakhy.operations.api.AuditLog;
import com.matbakhy.operations.api.AuditLog.Entry;
import com.matbakhy.operations.api.AuditLog.Outcome;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Media lifecycle (BR-010): PENDING_UPLOAD → QUARANTINED → APPROVED / REJECTED, plus DELETED. Clients upload
 * to a private key through a signed ticket; nothing is usable until the server has verified size, checksum,
 * sniffed type, dimensions and a CLEAN scan. Only approved public-purpose media get a re-encoded public
 * variant; private documents are read only through short-lived signed URLs by their owner or a scoped reviewer.
 */
@Service
public class MediaService implements MediaLibrary {

    static final Duration UPLOAD_TTL = Duration.ofMinutes(10);
    static final Duration READ_TTL = Duration.ofMinutes(5);
    static final int MAX_PENDING = 10;
    static final int MAX_PER_HOUR = 60;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public record TicketRequest(Purpose purpose, String filename, String contentType, long bytes, String sha256) {
    }

    public record UploadTicket(UUID uploadId, String uploadUrl, String method, Map<String, String> requiredHeaders, Instant expiresAt) {
    }

    public record MediaStatus(UUID id, String purpose, String state, String rejectionReason, String contentType, Long bytes,
                              Integer width, Integer height, String publicUrl, String privateReadUrl, Instant privateReadExpiresAt,
                              long version) {
    }

    private record Row(UUID id, UUID ownerId, Purpose purpose, String state, String declaredType, long declaredBytes,
                       String declaredSha256, String privateKey, String contentType, Long bytes, Integer width, Integer height,
                       String publicKey, String rejectionReason, Instant ticketExpiresAt, long version) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectStorage storage;
    private final MalwareScanner scanner;
    private final List<PrivateMediaAccess> access;
    private final AccessPolicy accessPolicy;
    private final AuditLog audit;
    private final MediaCleanup cleanup;
    private final Clock clock;

    MediaService(JdbcTemplate jdbc, ObjectStorage storage, MalwareScanner scanner, List<PrivateMediaAccess> access,
                 AccessPolicy accessPolicy, AuditLog audit, MediaCleanup cleanup, Clock clock) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.scanner = scanner;
        this.access = access;
        this.accessPolicy = accessPolicy;
        this.audit = audit;
        this.cleanup = cleanup;
        this.clock = clock;
    }

    @Transactional
    public UploadTicket createTicket(UUID ownerId, TicketRequest in) {
        MediaPolicy.Limits limits = MediaPolicy.of(in.purpose());
        String filename = sanitize(in.filename());
        if (filename == null) {
            throw invalid("filename", "INVALID_FILENAME");
        }
        if (in.contentType() == null || !limits.contentTypes().contains(in.contentType())) {
            throw invalid("contentType", "UNSUPPORTED_TYPE");
        }
        if (in.bytes() < 1 || in.bytes() > limits.maxBytes()) {
            throw invalid("bytes", "SIZE_LIMIT");
        }
        if (in.sha256() == null || !SHA256.matcher(in.sha256()).matches()) {
            throw invalid("sha256", "INVALID_SHA256");
        }
        Instant now = clock.instant();
        jdbc.queryForObject("SELECT id FROM app_user WHERE id = ? FOR UPDATE", UUID.class, ownerId);
        Integer pending = jdbc.queryForObject("SELECT count(*) FROM media_asset WHERE owner_id = ? AND state = 'PENDING_UPLOAD' "
                + "AND ticket_expires_at > ?", Integer.class, ownerId, Timestamp.from(now));
        Integer recent = jdbc.queryForObject("SELECT count(*) FROM media_asset WHERE owner_id = ? AND created_at > ?", Integer.class,
                ownerId, Timestamp.from(now.minus(Duration.ofHours(1))));
        if (pending >= MAX_PENDING || recent >= MAX_PER_HOUR) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "upload ticket limit");
        }
        UUID id = UUID.randomUUID();
        String key = "uploads/" + id;
        Instant expires = now.plus(UPLOAD_TTL);
        jdbc.update("""
                INSERT INTO media_asset (id, owner_id, purpose, original_filename, declared_content_type, declared_bytes, declared_sha256,
                                         private_key, ticket_expires_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                id, ownerId, in.purpose().name(), filename, in.contentType(), in.bytes(), in.sha256(), key, Timestamp.from(expires),
                Timestamp.from(now), Timestamp.from(now));
        String url = storage.presignUpload(key, in.contentType(), in.bytes(), UPLOAD_TTL).toString();
        return new UploadTicket(id, url, "PUT", Map.of("Content-Type", in.contentType()), expires);
    }

    /** Verifies the uploaded object and decides its state. Idempotent once decided. */
    @Transactional(noRollbackFor = ApiException.class)
    public MediaStatus complete(UUID ownerId, UUID mediaId, String sha256) {
        Row row = lockOwned(ownerId, mediaId);
        if (sha256 != null && !sha256.equals(row.declaredSha256())) {
            throw invalid("sha256", "SHA256_MISMATCH");
        }
        if (row.state().equals("APPROVED") || row.state().equals("REJECTED")) {
            return view(row);
        }
        MediaPolicy.Limits limits = MediaPolicy.of(row.purpose());
        OptionalLong size = storage.size(Bucket.PRIVATE, row.privateKey());
        if (size.isEmpty()) {
            if (clock.instant().isAfter(row.ticketExpiresAt())) {
                return reject(row, "MISSING_OBJECT");
            }
            throw new ApiException(ErrorCode.STATE_CONFLICT, "upload not received yet");
        }
        if (size.getAsLong() != row.declaredBytes() || size.getAsLong() > limits.maxBytes()) {
            return reject(row, "SIZE_MISMATCH");
        }
        byte[] content = storage.read(Bucket.PRIVATE, row.privateKey(), limits.maxBytes());
        if (!sha256(content).equals(row.declaredSha256())) {
            return reject(row, "CHECKSUM_MISMATCH");
        }
        String sniffed = MediaInspector.sniff(content).orElse(null);
        if (sniffed == null || !limits.contentTypes().contains(sniffed)) {
            return reject(row, "UNSUPPORTED_TYPE");
        }
        if (!sniffed.equals(row.declaredType())) {
            return reject(row, "TYPE_MISMATCH");
        }
        Integer width = null;
        Integer height = null;
        try {
            if (sniffed.equals(MediaInspector.PDF)) {
                MediaInspector.inspectPdf(content);
            } else {
                MediaInspector.Image image = MediaInspector.inspectImage(content, limits);
                width = image.width();
                height = image.height();
            }
        } catch (MediaInspector.Rejected r) {
            return reject(row, r.reason);
        }
        jdbc.update("""
                UPDATE media_asset SET state = 'QUARANTINED', content_type = ?, bytes = ?, width = ?, height = ?, updated_at = ?,
                       version = version + 1 WHERE id = ?""",
                sniffed, content.length, width, height, Timestamp.from(clock.instant()), mediaId);
        MalwareScanner.Result scan;
        try {
            scan = scanner.scan(content);
        } catch (RuntimeException e) {
            scan = new MalwareScanner.Result(Verdict.UNKNOWN, "unavailable");
        }
        jdbc.update("UPDATE media_asset SET scan_verdict = ?, scan_engine = ? WHERE id = ?", scan.verdict().name(), scan.engine(), mediaId);
        if (scan.verdict() == Verdict.INFECTED) {
            return reject(lockOwned(ownerId, mediaId), "MALWARE");
        }
        if (scan.verdict() != Verdict.CLEAN) {
            // Fail closed: the object stays quarantined and the client may retry completion later.
            throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "scanner verdict " + scan.verdict());
        }
        String publicKey = null;
        if (limits.publicVariant()) {
            byte[] variant;
            try {
                variant = MediaInspector.publicVariant(content, MediaPolicy.PUBLIC_MAX_SIDE);
            } catch (MediaInspector.Rejected r) {
                return reject(lockOwned(ownerId, mediaId), r.reason);
            }
            publicKey = "media/" + mediaId + ".jpg";
            // If this transaction rolls back the object stays behind; a durable job removes it later unless a committed
            // approval references it (a concurrent retry may have approved the same key).
            onRollbackEnqueue(Bucket.PUBLIC, publicKey, "UNAPPROVED_PUBLIC_VARIANT");
            jdbc.update("UPDATE media_asset SET state = 'APPROVED', public_key = ?, updated_at = ?, version = version + 1 WHERE id = ?",
                    publicKey, Timestamp.from(clock.instant()), mediaId);
            storage.write(Bucket.PUBLIC, publicKey, variant, MediaInspector.JPEG);
        } else {
            // The upload ticket stays valid for its TTL, so approved bytes move to a key no ticket can write (CR-003).
            String sealed = "documents/" + mediaId;
            String uploadKey = row.privateKey();
            // Idempotent, deterministic key: a retry after a rollback rewrites the same verified bytes. It is deliberately not
            // deleted on rollback, because a concurrent retry may already have approved the same key (SECR-003).
            onRollbackEnqueue(Bucket.PRIVATE, sealed, "UNAPPROVED_SEALED_DOCUMENT");
            storage.write(Bucket.PRIVATE, sealed, content, sniffed);
            jdbc.update("UPDATE media_asset SET state = 'APPROVED', private_key = ?, updated_at = ?, version = version + 1 WHERE id = ?",
                    sealed, Timestamp.from(clock.instant()), mediaId);
            // The upload ticket may still be valid: clean the upload key only after it has expired, or a replayed PUT would
            // recreate it after the cleanup (CR3-002).
            cleanup.enqueue(Bucket.PRIVATE, uploadKey, "SEALED_UPLOAD", row.ticketExpiresAt().plus(Duration.ofMinutes(1)));
        }
        return view(lockOwned(ownerId, mediaId));
    }

    /** Owner sees state (and their own approved document via a signed read); a scoped reviewer may read attached documents. */
    @Transactional
    public MediaStatus status(UUID actorId, UUID mediaId) {
        Row row = jdbc.query("SELECT * FROM media_asset WHERE id = ? AND state <> 'DELETED'", MediaService::map, mediaId).stream()
                .findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "media not found"));
        if (!row.ownerId().equals(actorId)) {
            boolean reviewable = row.purpose() == Purpose.COOK_DOCUMENT && access.stream().anyMatch(a -> a.reviewable(mediaId));
            if (!reviewable) {
                throw new ApiException(ErrorCode.NOT_FOUND, "media not visible to caller");
            }
            accessPolicy.requireOwnerOrPermitted(row.ownerId(), Permission.COOK_REVIEW, "media", mediaId.toString());
        }
        return view(row);
    }

    /** Scoped delete: only the owner, never media frozen into a preserved record. */
    @Transactional
    public void delete(UUID ownerId, UUID mediaId) {
        Row row = lockOwned(ownerId, mediaId);
        Boolean inMeal = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM meal_media mm JOIN meal m ON m.id = mm.meal_id "
                + "WHERE mm.media_id = ? AND m.status <> 'ARCHIVED')", Boolean.class, mediaId);
        if (Boolean.TRUE.equals(inMeal) || access.stream().anyMatch(a -> a.referenced(mediaId))) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "media referenced by a preserved record");
        }
        jdbc.update("""
                UPDATE media_asset SET state = 'DELETED', public_key = NULL, rejection_reason = NULL, updated_at = ?, version = version + 1
                WHERE id = ?""", Timestamp.from(clock.instant()), mediaId);
        storage.delete(Bucket.PRIVATE, row.privateKey());
        if (row.purpose() == Purpose.COOK_DOCUMENT) {
            // A sealed copy may exist under its deterministic key even if approval never committed.
            cleanup.enqueue(Bucket.PRIVATE, "documents/" + mediaId, "DELETED_DOCUMENT");
        }
        if (row.publicKey() != null) {
            storage.delete(Bucket.PUBLIC, row.publicKey());
        }
    }

    /** Public URL of an approved public variant, if any. */
    public java.util.Optional<java.net.URI> publicUrl(UUID mediaId) {
        return jdbc.query("SELECT public_key FROM media_asset WHERE id = ? AND state = 'APPROVED' AND public_key IS NOT NULL",
                (rs, n) -> rs.getString(1), mediaId).stream().findFirst().map(storage::publicUrl);
    }

    @Override
    public boolean approvedFor(UUID ownerId, UUID mediaId, Purpose purpose) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM media_asset WHERE id = ? AND owner_id = ? AND purpose = ? AND state = 'APPROVED'",
                Integer.class, mediaId, ownerId, purpose.name());
        return n != null && n > 0;
    }

    private MediaStatus reject(Row row, String reason) {
        jdbc.update("""
                UPDATE media_asset SET state = 'REJECTED', rejection_reason = ?, public_key = NULL, updated_at = ?, version = version + 1
                WHERE id = ?""", reason, Timestamp.from(clock.instant()), row.id());
        // Rejected bytes are not retained: deleted now, or by the durable cleanup job if storage is unavailable.
        try {
            storage.delete(Bucket.PRIVATE, row.privateKey());
        } catch (RuntimeException e) {
            cleanup.enqueue(Bucket.PRIVATE, row.privateKey(), "REJECTED_UPLOAD");
        }
        if (reason.equals("MALWARE") || reason.equals("ACTIVE_CONTENT") || reason.equals("TYPE_MISMATCH")) {
            audit.record(new Entry(row.ownerId(), "media.rejected", null, "media", row.id().toString(), Outcome.DENIED, reason, null,
                    Map.of("purpose", row.purpose().name())));
        }
        return view(lockOwned(row.ownerId(), row.id()));
    }

    /** Callers have already established that the actor is the owner or a scoped reviewer. */
    private MediaStatus view(Row row) {
        String publicUrl = row.state().equals("APPROVED") && row.publicKey() != null ? storage.publicUrl(row.publicKey()).toString() : null;
        String readUrl = null;
        Instant readExpires = null;
        if (row.purpose() == Purpose.COOK_DOCUMENT && row.state().equals("APPROVED")) {
            readUrl = storage.presignRead(row.privateKey(), READ_TTL).toString();
            readExpires = clock.instant().plus(READ_TTL);
        }
        return new MediaStatus(row.id(), row.purpose().name(), row.state(), row.rejectionReason(), row.contentType(), row.bytes(),
                row.width(), row.height(), publicUrl, readUrl, readExpires, row.version());
    }

    private Row lockOwned(UUID ownerId, UUID mediaId) {
        return jdbc.query("SELECT * FROM media_asset WHERE id = ? AND owner_id = ? AND state <> 'DELETED' FOR UPDATE", MediaService::map,
                mediaId, ownerId).stream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "media not found"));
    }

    /**
     * Only on rollback (the rare path, so the second connection is not held on every completion): record the written
     * object for the guarded durable cleanup. Nothing is deleted here, so a concurrent retry that committed is unharmed.
     */
    private void onRollbackEnqueue(Bucket bucket, String key, String reason) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        try {
                            cleanup.enqueueUnlessApproved(bucket, key, reason, Duration.ofMinutes(5));
                        } catch (RuntimeException e) {
                            org.slf4j.LoggerFactory.getLogger(MediaService.class).warn("could not record unapproved object for cleanup ({})", e.getClass().getSimpleName());
                        }
                    }
                }
            });
        }
    }

    static String sanitize(String filename) {
        if (filename == null) {
            return null;
        }
        String base = filename.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).strip();
        if (base.isEmpty() || base.length() > 120 || base.chars().anyMatch(c -> c < 0x20 || c == 0x7f) || base.equals(".")
                || base.equals("..")) {
            return null;
        }
        return base;
    }

    private static ApiException invalid(String field, String code) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "invalid upload " + field, List.of(new FieldError(field, code)));
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Row map(ResultSet rs, int n) throws SQLException {
        Timestamp expires = rs.getTimestamp("ticket_expires_at");
        return new Row(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class), Purpose.valueOf(rs.getString("purpose")),
                rs.getString("state"), rs.getString("declared_content_type"), rs.getLong("declared_bytes"), rs.getString("declared_sha256"),
                rs.getString("private_key"), rs.getString("content_type"), (Long) rs.getObject("bytes"), (Integer) rs.getObject("width"),
                (Integer) rs.getObject("height"), rs.getString("public_key"), rs.getString("rejection_reason"), expires.toInstant(),
                rs.getLong("version"));
    }
}
