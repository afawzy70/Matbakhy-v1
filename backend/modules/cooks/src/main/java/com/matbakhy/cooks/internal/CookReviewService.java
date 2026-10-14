package com.matbakhy.cooks.internal;

import com.matbakhy.cooks.internal.CookApplications.Application;
import com.matbakhy.cooks.internal.CookApplications.Revision;
import com.matbakhy.identity.api.Permission;
import com.matbakhy.operations.api.AuditLog;
import com.matbakhy.operations.api.AuditLog.Entry;
import com.matbakhy.operations.api.AuditLog.Outcome;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import com.matbakhy.shared.idempotency.IdempotentCommands;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reviewer side of the cook workflow (BR-008). Requires cook.review (checked by the web layer and audited
 * here); a reviewer can never decide on their own application. Every decision names the revision it
 * concerns and carries a reason; approval creates the kitchen from the approved revision.
 */
@Service
public class CookReviewService {

    public enum Decision { START_REVIEW, APPROVE, REQUEST_CHANGES, REJECT }

    public record DecisionRequest(UUID applicationId, Decision decision, String reason, int revision, long expectedVersion) {
    }

    public record ApplicationSummary(UUID id, String state, String publicName, int currentRevision, Instant submittedAt,
                                     Instant updatedAt, long version) {
    }

    public record ApplicationPage(List<ApplicationSummary> items, String nextCursor, boolean hasMore) {
    }

    /** Reviewer view: submitted revisions with private fields. Draft edits in progress are not shown. */
    /** kitchen is the staff view of the approved kitchen (operating state, version, active-order impact), if any. */
    public record ApplicationReview(UUID id, UUID applicantId, String state, int currentRevision, List<Revision> revisions,
                                    UUID kitchenId, long version, KitchenService.KitchenAdmin kitchen) {
    }

    private static final Set<String> STATES = Set.of("DRAFT", "SUBMITTED", "UNDER_REVIEW", "APPROVED", "CHANGES_REQUESTED", "REJECTED");

    private final JdbcTemplate jdbc;
    private final CookApplications applications;
    private final KitchenService kitchens;
    private final IdempotentCommands idempotency;
    private final AuditLog audit;
    private final Clock clock;

    CookReviewService(JdbcTemplate jdbc, CookApplications applications, KitchenService kitchens, IdempotentCommands idempotency,
                      AuditLog audit, Clock clock) {
        this.jdbc = jdbc;
        this.applications = applications;
        this.kitchens = kitchens;
        this.idempotency = idempotency;
        this.audit = audit;
        this.clock = clock;
    }

    public ApplicationPage queue(String state, String q, String cursor, int limit) {
        String search = q == null || q.isBlank() ? null : q.strip();
        if (search != null && search.length() > 80) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "q too long", List.of(new FieldError("q", "MAX_80")));
        }
        String like = search == null ? null : "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        if (state != null && !STATES.contains(state)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "unknown state", List.of(new FieldError("state", "UNKNOWN_STATE")));
        }
        if (limit < 1 || limit > 100) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "limit", List.of(new FieldError("limit", "RANGE_1_100")));
        }
        Instant afterTime = Instant.EPOCH;
        UUID afterId = new UUID(0, 0);
        if (cursor != null) {
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", 2);
                afterTime = Instant.parse(parts[0]);
                afterId = UUID.fromString(parts[1]);
            } catch (RuntimeException e) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "bad cursor", List.of(new FieldError("cursor", "INVALID")));
            }
        }
        List<Object[]> rows = jdbc.query("""
                SELECT a.id, a.state, r.public_name, a.current_revision, r.submitted_at, a.updated_at, a.version, a.created_at
                FROM cook_application a
                LEFT JOIN cook_application_revision r ON r.application_id = a.id AND r.revision = a.current_revision
                WHERE (?::text IS NULL OR a.state = ?) AND (?::text IS NULL OR r.public_name ILIKE ?) AND a.state <> 'DRAFT'
                  AND (a.created_at, a.id) > (?, ?)
                ORDER BY a.created_at, a.id LIMIT ?""",
                (rs, n) -> new Object[] {
                    new ApplicationSummary(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getInt(4),
                            CookApplications.instant(rs, "submitted_at"), CookApplications.instant(rs, "updated_at"), rs.getLong(7)),
                    CookApplications.instant(rs, "created_at")},
                state, state, like, like, Timestamp.from(afterTime), afterId, limit + 1);
        boolean more = rows.size() > limit;
        List<Object[]> page = more ? rows.subList(0, limit) : rows;
        String next = null;
        if (more) {
            Object[] last = page.getLast();
            String raw = last[1] + "|" + ((ApplicationSummary) last[0]).id();
            next = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
        return new ApplicationPage(page.stream().map(r -> (ApplicationSummary) r[0]).toList(), next, more);
    }

    /** Reading private application data is an audited staff access (BR-006/BR-007). */
    @Transactional
    public ApplicationReview review(UUID actorId, UUID applicationId, String requestId) {
        Application app = applications.byId(applicationId, false)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "application not found"));
        audit.record(new Entry(actorId, "cook.application.view", Permission.COOK_REVIEW.code(), "cook_application",
                applicationId.toString(), Outcome.ALLOWED, "reviewer opened private application", requestId, Map.of()));
        return view(app);
    }

    /** Refuses (and audits) a reviewer acting on their own application. Separate so the denial audit survives. */
    @Transactional(noRollbackFor = ApiException.class)
    public void refuseSelfReview(UUID actorId, UUID applicationId, Decision decision, String requestId) {
        UUID applicant = applications.byId(applicationId, false).map(Application::userId).orElse(null);
        if (actorId.equals(applicant)) {
            audit.record(new Entry(actorId, "cook.application.decision", Permission.COOK_REVIEW.code(), "cook_application",
                    applicationId.toString(), Outcome.DENIED, "reviewer cannot decide own application", requestId,
                    Map.of("decision", decision.name())));
            throw new ApiException(ErrorCode.FORBIDDEN, "self review");
        }
    }

    @Transactional
    public ApplicationReview decide(UUID actorId, String idempotencyKey, DecisionRequest request, String requestId) {
        return idempotency.execute(actorId, "cook.application.decision", idempotencyKey, request, ApplicationReview.class,
                () -> apply(actorId, request, requestId));
    }

    private ApplicationReview apply(UUID actorId, DecisionRequest request, String requestId) {
        Application app = applications.byId(request.applicationId(), true)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "application not found"));
        if (actorId.equals(app.userId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "self review");
        }
        if (request.expectedVersion() != app.version()) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "stale application version");
        }
        if (request.revision() != app.currentRevision() || app.currentRevision() == 0) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "decision targets a stale revision");
        }
        Instant now = clock.instant();
        String reason = request.reason() == null ? null : request.reason().strip();
        if (request.decision() == Decision.START_REVIEW) {
            requireState(app, "SUBMITTED");
            jdbc.update("UPDATE cook_application SET state = 'UNDER_REVIEW', updated_at = ?, version = version + 1 WHERE id = ?",
                    Timestamp.from(now), app.id());
        } else {
            requireState(app, "UNDER_REVIEW");
            if (reason == null || reason.length() < 3 || reason.length() > 1000) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "reason required",
                        List.of(new FieldError("reason", "LENGTH_3_1000")));
            }
            String outcome = switch (request.decision()) {
                case APPROVE -> "APPROVED";
                case REQUEST_CHANGES -> "CHANGES_REQUESTED";
                case REJECT -> "REJECTED";
                case START_REVIEW -> throw new IllegalStateException();
            };
            jdbc.update("""
                    UPDATE cook_application_revision SET decision = ?, decision_reason = ?, reviewer_id = ?, decided_at = ?
                    WHERE application_id = ? AND revision = ?""",
                    outcome, reason, actorId, Timestamp.from(now), app.id(), app.currentRevision());
            jdbc.update("UPDATE cook_application SET state = ?, updated_at = ?, version = version + 1 WHERE id = ?",
                    outcome, Timestamp.from(now), app.id());
            if (request.decision() == Decision.APPROVE) {
                Revision approved = applications.revisions(app.id()).getLast();
                kitchens.createFromApproval(app.id(), app.userId(), approved);
            }
        }
        audit.record(new Entry(actorId, "cook.application.decision", Permission.COOK_REVIEW.code(), "cook_application",
                app.id().toString(), Outcome.ALLOWED, reason == null ? "review started" : reason, requestId,
                Map.of("decision", request.decision().name(), "revision", app.currentRevision())));
        return view(applications.byId(app.id(), false).orElseThrow());
    }

    private static void requireState(Application app, String expected) {
        if (!expected.equals(app.state())) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "application is " + app.state() + ", expected " + expected);
        }
    }

    private ApplicationReview view(Application app) {
        UUID kitchenId = jdbc.query("SELECT id FROM kitchen WHERE application_id = ?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, app.id());
        return new ApplicationReview(app.id(), app.userId(), app.state(), app.currentRevision(), applications.revisions(app.id()),
                kitchenId, app.version(), kitchenId == null ? null : kitchens.admin(kitchenId));
    }
}
