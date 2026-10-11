package com.matbakhy.catalog.web;

import com.matbakhy.catalog.api.MediaLibrary.Purpose;
import com.matbakhy.catalog.internal.media.MediaService;
import com.matbakhy.catalog.internal.media.MediaService.MediaStatus;
import com.matbakhy.catalog.internal.media.MediaService.TicketRequest;
import com.matbakhy.catalog.internal.media.MediaService.UploadTicket;
import com.matbakhy.identity.api.CurrentAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API-039..041: signed private uploads, server-side verification and scoped status/reads. */
@RestController
class UploadController {

    private final MediaService media;

    UploadController(MediaService media) {
        this.media = media;
    }

    record TicketBody(@NotNull Purpose purpose, @NotBlank @Size(max = 255) String filename, @NotBlank @Size(max = 100) String contentType,
                      @NotNull @Positive Long bytes, @NotBlank @Size(max = 64) String sha256) {
    }

    record CompleteBody(@Size(max = 64) String sha256) {
    }

    @PostMapping("/api/v1/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    UploadTicket ticket(@AuthenticationPrincipal CurrentAccount actor, @Valid @RequestBody TicketBody body) {
        return media.createTicket(actor.id(), new TicketRequest(body.purpose(), body.filename(), body.contentType(), body.bytes(),
                body.sha256()));
    }

    @PostMapping("/api/v1/uploads/{uploadId}/complete")
    MediaStatus complete(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID uploadId, @Valid @RequestBody CompleteBody body) {
        return media.complete(actor.id(), uploadId, body.sha256());
    }

    @GetMapping("/api/v1/uploads/{uploadId}")
    MediaStatus status(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID uploadId) {
        return media.status(actor.id(), uploadId);
    }
}
