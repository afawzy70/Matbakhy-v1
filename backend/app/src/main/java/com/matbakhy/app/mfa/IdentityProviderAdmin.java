package com.matbakhy.app.mfa;

import java.util.List;

/** The few identity-provider administration calls MFA recovery needs; never exposes credential secrets. */
public interface IdentityProviderAdmin {

    boolean configured();

    /** Ids of the user's OTP credentials (nothing else about them is read). */
    List<String> otpCredentialIds(String subject);

    void deleteCredential(String subject, String credentialId);

    /** Ends every provider session of the user. */
    void logout(String subject);
}
