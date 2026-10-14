package com.matbakhy.identity.internal;

import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.identity.internal.AccountLifecycleService.DeletionRequest;
import com.matbakhy.identity.internal.AddressService.Address;
import com.matbakhy.identity.internal.AddressService.AddressInput;
import com.matbakhy.identity.internal.PhoneVerificationService.Challenge;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Module-internal entry point for the identity web layer. */
@Component
public class ProfileFacade {

    private final ProfileService profiles;
    private final PhoneVerificationService phones;
    private final AddressService addresses;
    private final AccountLifecycleService lifecycle;

    ProfileFacade(ProfileService profiles, PhoneVerificationService phones, AddressService addresses, AccountLifecycleService lifecycle) {
        this.profiles = profiles;
        this.phones = phones;
        this.addresses = addresses;
        this.lifecycle = lifecycle;
    }

    public CurrentAccount update(UUID accountId, long expectedVersion, String displayName, String locale) {
        return profiles.update(accountId, expectedVersion, displayName, locale);
    }

    public Challenge startPhoneVerification(UUID accountId, String phone, String locale) {
        return phones.start(accountId, phone, locale);
    }

    public void verifyPhone(UUID accountId, UUID challengeId, String code) {
        phones.verify(accountId, challengeId, code);
    }

    public List<Address> addresses(UUID accountId) {
        return addresses.list(accountId);
    }

    public Address createAddress(UUID accountId, AddressInput input) {
        return addresses.create(accountId, input);
    }

    public Address updateAddress(UUID accountId, UUID addressId, long expectedVersion, AddressInput input) {
        return addresses.update(accountId, addressId, expectedVersion, input);
    }

    public void archiveAddress(UUID accountId, UUID addressId) {
        addresses.archive(accountId, addressId);
    }

    public DeletionRequest requestDeletion(UUID accountId, String policyVersion) {
        return lifecycle.requestDeletion(accountId, policyVersion);
    }
}
