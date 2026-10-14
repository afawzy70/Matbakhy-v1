package com.matbakhy.identity.web;

import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.identity.internal.AccountLifecycleService.DeletionRequest;
import com.matbakhy.identity.internal.AddressService.Address;
import com.matbakhy.identity.internal.AddressService.AddressInput;
import com.matbakhy.identity.internal.PhoneVerificationService.Challenge;
import com.matbakhy.identity.internal.ProfileFacade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API-011..API-017: phone verification, own addresses and deletion intake. All routes act on the caller only. */
@RestController
class ContactController {

    private final ProfileFacade facade;

    ContactController(ProfileFacade facade) {
        this.facade = facade;
    }

    record PhoneChallengeRequest(@NotBlank @Size(max = 32) String phoneE164) {
    }

    record VerifyRequest(@NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
    }

    record VerificationResult(boolean phoneVerified) {
    }

    record AddressRequest(@Size(min = 1, max = 40) String label, @Size(min = 1, max = 120) String recipientName,
                          @Size(max = 32) String phone, @Size(min = 1, max = 80) String governorate,
                          @Size(min = 1, max = 80) String city, UUID zoneId, @Size(min = 1, max = 200) String street,
                          @Size(min = 1, max = 40) String building, @Size(min = 1, max = 20) String floor,
                          @Size(min = 1, max = 20) String unit, @Size(min = 1, max = 200) String landmark,
                          @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
                          @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
                          @Min(0) Long expectedVersion) {

        AddressInput input() {
            return new AddressInput(label, recipientName, phone, governorate, city, zoneId, street, building, floor, unit,
                    landmark, latitude, longitude);
        }

        @AssertTrue(message = "REQUIRED")
        boolean isCompleteForCreate() {
            return expectedVersion != null || (label != null && recipientName != null && phone != null && governorate != null
                    && city != null && zoneId != null && street != null && building != null);
        }
    }

    record AddressPage(List<Address> items, String nextCursor, boolean hasMore) {
    }

    record DeletionCommand(@NotNull @AssertTrue Boolean confirm, @NotBlank @Size(max = 40) String policyVersion) {
    }

    @PostMapping("/api/v1/me/phone/challenges")
    @ResponseStatus(HttpStatus.CREATED)
    Challenge startPhone(@AuthenticationPrincipal CurrentAccount account, @Valid @RequestBody PhoneChallengeRequest request) {
        return facade.startPhoneVerification(account.id(), request.phoneE164(), account.locale());
    }

    @PostMapping("/api/v1/me/phone/challenges/{challengeId}/verify")
    VerificationResult verify(@AuthenticationPrincipal CurrentAccount account, @PathVariable UUID challengeId,
                              @Valid @RequestBody VerifyRequest request) {
        facade.verifyPhone(account.id(), challengeId, request.code());
        return new VerificationResult(true);
    }

    @GetMapping("/api/v1/me/addresses")
    AddressPage addresses(@AuthenticationPrincipal CurrentAccount account) {
        return new AddressPage(facade.addresses(account.id()), null, false);
    }

    @PostMapping("/api/v1/me/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    Address create(@AuthenticationPrincipal CurrentAccount account, @Valid @RequestBody AddressRequest request) {
        if (request.expectedVersion() != null) {
            throw new com.matbakhy.shared.error.ApiException(com.matbakhy.shared.error.ErrorCode.MALFORMED_REQUEST, "version on create");
        }
        return facade.createAddress(account.id(), request.input());
    }

    @PatchMapping("/api/v1/me/addresses/{addressId}")
    Address update(@AuthenticationPrincipal CurrentAccount account, @PathVariable UUID addressId,
                   @Valid @RequestBody AddressRequest request) {
        if (request.expectedVersion() == null) {
            throw new com.matbakhy.shared.error.ApiException(com.matbakhy.shared.error.ErrorCode.VALIDATION_FAILED, "expectedVersion",
                    List.of(new com.matbakhy.shared.error.ApiException.FieldError("expectedVersion", "NotNull")));
        }
        return facade.updateAddress(account.id(), addressId, request.expectedVersion(), request.input());
    }

    @DeleteMapping("/api/v1/me/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@AuthenticationPrincipal CurrentAccount account, @PathVariable UUID addressId) {
        facade.archiveAddress(account.id(), addressId);
    }

    @PostMapping("/api/v1/me/deletion-requests")
    DeletionRequest requestDeletion(@AuthenticationPrincipal CurrentAccount account, @Valid @RequestBody DeletionCommand command) {
        return facade.requestDeletion(account.id(), command.policyVersion());
    }
}
