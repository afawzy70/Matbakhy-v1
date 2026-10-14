package com.matbakhy.availability.internal;

import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Local Cairo wall-clock times to instants (BR-014). A time skipped by a daylight-saving gap is rejected; a
 * repeated time is accepted only with an explicit UTC offset choosing one of its two instants.
 */
public final class CairoTime {

    public static final ZoneId ZONE = ZoneId.of("Africa/Cairo");

    private CairoTime() {
    }

    public static Instant resolve(LocalDate date, LocalTime time, String offset, String field) {
        LocalDateTime local = LocalDateTime.of(date, time);
        List<ZoneOffset> valid = ZONE.getRules().getValidOffsets(local);
        if (valid.isEmpty()) {
            throw error(field, "NONEXISTENT_LOCAL_TIME");
        }
        ZoneOffset chosen;
        if (offset == null || offset.isBlank()) {
            if (valid.size() > 1) {
                throw error(field, "AMBIGUOUS_LOCAL_TIME");
            }
            chosen = valid.getFirst();
        } else {
            try {
                chosen = ZoneOffset.of(offset);
            } catch (RuntimeException e) {
                throw error(field + "Offset", "INVALID_OFFSET");
            }
            if (!valid.contains(chosen)) {
                throw error(field + "Offset", "OFFSET_NOT_VALID_FOR_LOCAL_TIME");
            }
        }
        return local.toInstant(chosen);
    }

    private static ApiException error(String field, String code) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "local time " + code, List.of(new FieldError(field, code)));
    }
}
