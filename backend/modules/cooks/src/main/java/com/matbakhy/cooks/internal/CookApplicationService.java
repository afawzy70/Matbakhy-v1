package com.matbakhy.cooks.internal;

import com.matbakhy.catalog.api.MediaLibrary;
import com.matbakhy.catalog.api.MediaLibrary.Purpose;
import com.matbakhy.cooks.internal.CookApplications.Application;
import com.matbakhy.cooks.internal.CookApplications.Draft;
import com.matbakhy.cooks.internal.CookApplications.Revision;
import com.matbakhy.identity.api.EgyptianPhone;
import com.matbakhy.identity.api.ServiceZoneLookup;
import com.matbakhy.operations.api.AuditLog;
import com.matbakhy.operations.api.AuditLog.Entry;
import com.matbakhy.operations.api.AuditLog.Outcome;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The applicant's side of the cook workflow (BR-008): one application per account, a private working draft,
 * and submission of a complete draft as an immutable revision. Public registration never grants cook status;
 * only an approval by a scoped reviewer creates a kitchen.
 */
@Service
public class CookApplicationService {

    static final int MAX_ZONES = 20;
    static final int MAX_DOCUMENTS = 10;
    private static final Set<String> EDITABLE = Set.of("DRAFT", "CHANGES_REQUESTED");

    /** The applicant's own view: draft, state and decision history (reviewer identity is internal). */
    public record CookApplication(UUID id, String state, Draft draft, int currentRevision, List<HistoryEntry> history, long version) {
    }

    public record HistoryEntry(int revision, Instant submittedAt, String decision, String decisionReason, Instant decidedAt) {
    }

    public record DraftInput(String publicName, String bio, String contactPhone, String privateLocation, Boolean pickupEnabled,
                             List<UUID> zoneIds, List<UUID> documentIds) {
    }

    private final JdbcTemplate jdbc;
    private final CookApplications applications;
    private final ServiceZoneLookup zones;
    private final MediaLibrary media;
    private final AuditLog audit;
    private final Clock clock;

    CookApplicationService(JdbcTemplate jdbc, CookApplications applications, ServiceZoneLookup zones, MediaLibrary media, AuditLog audit,
                           Clock clock) {
        this.jdbc = jdbc;
        this.applications = applications;
        this.zones = zones;
        this.media = media;
        this.audit = audit;
        this.clock = clock;
    }

    public CookApplication get(UUID userId) {
        return applications.byUser(userId, false).map(this::view)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "no cook application"));
    }

    @Transactional
    public CookApplication saveDraft(UUID userId, Long expectedVersion, DraftInput in) {
        Draft draft = normalize(userId, in);
        Timestamp now = Timestamp.from(clock.instant());
        var existing = applications.byUser(userId, true);
        if (existing.isEmpty()) {
            if (expectedVersion != null && expectedVersion != 0) {
                throw new ApiException(ErrorCode.VERSION_CONFLICT, "application does not exist yet");
            }
            jdbc.queryForObject("SELECT id FROM app_user WHERE id = ? FOR UPDATE", UUID.class, userId);
            int inserted = jdbc.update("""
                    INSERT INTO cook_application (id, user_id, public_name, bio, contact_phone, private_location, pickup_enabled,
                                                 zone_ids, document_ids, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?::uuid[], ?::uuid[], ?, ?) ON CONFLICT (user_id) DO NOTHING""",
                    UUID.randomUUID(), userId, draft.publicName(), draft.bio(), draft.contactPhone(), draft.privateLocation(),
                    draft.pickupEnabled(), CookApplications.uuidArray(draft.zoneIds()), CookApplications.uuidArray(draft.documentIds()), now, now);
            if (inserted == 0) {
                throw new ApiException(ErrorCode.VERSION_CONFLICT, "application created concurrently");
            }
            return get(userId);
        }
        Application app = existing.get();
        requireVersion(app, expectedVersion);
        if (!EDITABLE.contains(app.state())) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "application not editable in " + app.state());
        }
        jdbc.update("""
                UPDATE cook_application SET public_name = ?, bio = ?, contact_phone = ?, private_location = ?, pickup_enabled = ?,
                       zone_ids = ?::uuid[], document_ids = ?::uuid[], updated_at = ?, version = version + 1
                WHERE id = ?""",
                draft.publicName(), draft.bio(), draft.contactPhone(), draft.privateLocation(), draft.pickupEnabled(),
                CookApplications.uuidArray(draft.zoneIds()), CookApplications.uuidArray(draft.documentIds()), now, app.id());
        return get(userId);
    }

    @Transactional
    public CookApplication submit(UUID userId, Long expectedVersion, String requestId) {
        Application app = applications.byUser(userId, true)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "no cook application"));
        requireVersion(app, expectedVersion);
        if (!EDITABLE.contains(app.state())) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "application cannot be submitted in " + app.state());
        }
        requireComplete(userId, app.draft());
        Draft d = app.draft();
        int revision = app.currentRevision() + 1;
        Instant now = clock.instant();
        jdbc.update("""
                INSERT INTO cook_application_revision (application_id, revision, public_name, bio, contact_phone, private_location,
                                                       pickup_enabled, zone_ids, document_ids, submitted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::uuid[], ?::uuid[], ?)""",
                app.id(), revision, d.publicName(), d.bio(), d.contactPhone(), d.privateLocation(), d.pickupEnabled(),
                CookApplications.uuidArray(d.zoneIds()), CookApplications.uuidArray(d.documentIds()), Timestamp.from(now));
        jdbc.update("""
                UPDATE cook_application SET state = 'SUBMITTED', current_revision = ?, updated_at = ?, version = version + 1
                WHERE id = ?""", revision, Timestamp.from(now), app.id());
        audit.record(new Entry(userId, "cook.application.submit", null, "cook_application", app.id().toString(), Outcome.ALLOWED,
                "applicant submitted revision", requestId, Map.of("revision", revision)));
        return get(userId);
    }

    private void requireComplete(UUID userId, Draft d) {
        List<FieldError> missing = new ArrayList<>();
        if (d.publicName() == null) {
            missing.add(new FieldError("publicName", "REQUIRED"));
        }
        if (d.bio() == null) {
            missing.add(new FieldError("bio", "REQUIRED"));
        }
        if (d.contactPhone() == null) {
            missing.add(new FieldError("contactPhone", "REQUIRED"));
        }
        if (d.privateLocation() == null) {
            missing.add(new FieldError("privateLocation", "REQUIRED"));
        }
        if (!d.pickupEnabled() && d.zoneIds().isEmpty()) {
            missing.add(new FieldError("zoneIds", "PICKUP_OR_ZONE_REQUIRED"));
        }
        for (UUID zone : d.zoneIds()) {
            if (!zones.isActive(zone)) {
                missing.add(new FieldError("zoneIds", "ZONE_NOT_SERVICEABLE"));
                break;
            }
        }
        if (d.documentIds().stream().anyMatch(doc -> !media.approvedFor(userId, doc, Purpose.COOK_DOCUMENT))) {
            missing.add(new FieldError("documentIds", "DOCUMENT_NOT_APPROVED"));
        }
        if (!missing.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "incomplete cook application", missing);
        }
    }

    private Draft normalize(UUID userId, DraftInput in) {
        List<FieldError> errors = new ArrayList<>();
        String name = text(in.publicName(), "publicName", 2, 80, errors);
        String bio = text(in.bio(), "bio", 1, 1000, errors);
        String location = text(in.privateLocation(), "privateLocation", 5, 300, errors);
        String phone = null;
        if (in.contactPhone() != null && !in.contactPhone().isBlank()) {
            phone = EgyptianPhone.normalize(in.contactPhone()).orElse(null);
            if (phone == null) {
                errors.add(new FieldError("contactPhone", "INVALID_EGYPTIAN_MOBILE"));
            }
        }
        List<UUID> zoneIds = in.zoneIds() == null ? List.of() : List.copyOf(new LinkedHashSet<>(in.zoneIds()));
        if (zoneIds.size() > MAX_ZONES) {
            errors.add(new FieldError("zoneIds", "MAX_20"));
        } else if (zoneIds.stream().anyMatch(z -> !zones.isActive(z))) {
            errors.add(new FieldError("zoneIds", "ZONE_NOT_SERVICEABLE"));
        }
        List<UUID> documentIds = in.documentIds() == null ? List.of() : List.copyOf(new LinkedHashSet<>(in.documentIds()));
        if (documentIds.size() > MAX_DOCUMENTS) {
            errors.add(new FieldError("documentIds", "MAX_10"));
        } else if (documentIds.stream().anyMatch(doc -> !media.approvedFor(userId, doc, Purpose.COOK_DOCUMENT))) {
            errors.add(new FieldError("documentIds", "DOCUMENT_NOT_APPROVED"));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "invalid cook application draft", errors);
        }
        return new Draft(name, bio, phone, location, Boolean.TRUE.equals(in.pickupEnabled()), zoneIds, documentIds);
    }

    private static String text(String value, String field, int min, int max, List<FieldError> errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() < min || trimmed.length() > max) {
            errors.add(new FieldError(field, "LENGTH_" + min + "_" + max));
        }
        return trimmed;
    }

    private static void requireVersion(Application app, Long expectedVersion) {
        if (expectedVersion == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "expectedVersion required",
                    List.of(new FieldError("expectedVersion", "REQUIRED")));
        }
        if (expectedVersion != app.version()) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "stale application version");
        }
    }

    private CookApplication view(Application app) {
        List<HistoryEntry> history = applications.revisions(app.id()).stream()
                .map((Revision r) -> new HistoryEntry(r.revision(), r.submittedAt(), r.decision(), r.decisionReason(), r.decidedAt()))
                .toList();
        return new CookApplication(app.id(), app.state(), app.draft(), app.currentRevision(), history, app.version());
    }
}
