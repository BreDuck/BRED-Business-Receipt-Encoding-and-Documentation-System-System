package com.bred.rms.local;

import com.bred.rms.models.User;
import com.bred.rms.services.AuthApi;
import com.bred.rms.services.AuthException;

import java.util.Arrays;

/** Sign-in against the accounts stored on this computer. */
public final class LocalAuth implements AuthApi {
    private final LocalDatabase db;
    private volatile User current;

    /** Creates the sign-in service over the local database. */
    public LocalAuth(LocalDatabase db) { this.db = db; }

    @Override
    public User login(String email, char[] password) throws AuthException {
        if (email == null || email.isBlank() || password == null || password.length == 0) {
            throw new AuthException("Enter your email and password.");
        }
        try {
            User u = db.authenticate(email, password).orElseThrow(() -> new AuthException("Invalid email or password."));
            if (!u.active()) throw new AuthException("This account is inactive.");
            current = u;
            return u;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @Override public User current() { return current; }

    @Override public void logout() { current = null; }

    @Override
    public void changePassword(String newPassword) throws AuthException {
        if (newPassword == null || newPassword.length() < 6) throw new AuthException("The new password must be at least 6 characters.");
        User me = current;
        if (me == null) throw new AuthException("Please sign in again.");
        db.setPassword(me.id(), newPassword);
    }

    @Override public void onSessionExpired(Runnable callback) { /* local sessions never expire */ }
}
