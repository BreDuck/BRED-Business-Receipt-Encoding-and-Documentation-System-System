package com.bred.rms.services;

import com.bred.rms.models.User;

/** What the screens need from sign-in, whether accounts live in Supabase or on this computer. */
public interface AuthApi {
    /** Signs in with email and password; throws a message meant for people on failure. */
    User login(String email, char[] password) throws AuthException;

    /** Submits a public registration request without signing the user in. */
    default void registerAccount(String fullName, String email, com.bred.rms.models.Role role,
                                java.util.UUID companyId, String companyName, char[] password) throws AuthException {
        throw new AuthException("Account registration is not available.");
    }

    /** The signed-in user, or null. */
    User current();

    /** Signs out. */
    void logout();

    /** Changes the signed-in user's own password (minimum 6 characters). */
    void changePassword(String newPassword) throws AuthException;

    /** Registers a callback for when the session can no longer be used and the user must sign in again. */
    void onSessionExpired(Runnable callback);
}
