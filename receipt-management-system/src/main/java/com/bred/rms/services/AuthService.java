package com.bred.rms.services;

import com.bred.rms.models.User;
import com.bred.rms.models.AccountStatus;
import com.bred.rms.repositories.RepositoryException;
import com.bred.rms.repositories.UserRepository;
import com.bred.rms.supabase.AuthClient;
import com.bred.rms.supabase.Session;
import com.bred.rms.supabase.SessionManager;
import com.bred.rms.supabase.SupabaseException;

import java.util.regex.Pattern;

/** Signs users in with Supabase Auth (email + password) and loads their profile and role. */
public final class AuthService implements AuthApi {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private final AuthClient auth;
    private final SessionManager sessions;
    private final UserRepository users;
    private volatile User current;

    public AuthService(AuthClient auth, SessionManager sessions, UserRepository users) {
        this.auth = auth;
        this.sessions = sessions;
        this.users = users;
    }

    public User login(String email, char[] password) throws AuthException {
        if (email == null || email.isBlank() || password == null || password.length == 0) {
            throw new AuthException("Enter your email and password.");
        }
        Session session;
        try {
            session = auth.signIn(email.trim(), new String(password));
        } catch (SupabaseException e) {
            throw new AuthException(signInMessage(e));
        }
        sessions.set(session);
        try {
            User u = users.findById(session.userId()).orElse(null);
            if (u == null) throw fail("This account has no profile yet. Ask an administrator to set it up.");
            if (u.accountStatus() == AccountStatus.PENDING) throw fail("Your registration is pending administrator approval.");
            if (u.accountStatus() == AccountStatus.REJECTED) throw fail("Your registration was rejected. Please contact an administrator.");
            if (!u.active()) throw fail("This account is inactive.");
            current = u;
            return u;
        } catch (RepositoryException e) {
            throw fail(e.getMessage());
        }
    }

    /** Validates and submits a registration request without creating a local sign-in session. */
    @Override public void registerAccount(String fullName, String email, com.bred.rms.models.Role role,
                                          java.util.UUID companyId, String companyName, char[] password) throws AuthException {
        if (fullName == null || fullName.isBlank()) throw new AuthException("Full name is required.");
        if (email == null || !EMAIL.matcher(email.trim()).matches()) throw new AuthException("Enter a valid email address.");
        if (role == null || role == com.bred.rms.models.Role.CREATOR) throw new AuthException("Choose a valid registration type.");
        if (role == com.bred.rms.models.Role.ENCODER && companyId == null) throw new AuthException("Choose a company to apply to.");
        if (role == com.bred.rms.models.Role.ADMIN && (companyName == null || companyName.isBlank())) {
            throw new AuthException("Enter the company name to create.");
        }
        if (password == null || password.length < 8) throw new AuthException("Password must be at least 8 characters.");
        boolean letter = false;
        boolean digit = false;
        for (char c : password) {
            letter |= Character.isLetter(c);
            digit |= Character.isDigit(c);
        }
        if (!letter || !digit) throw new AuthException("Password must contain at least one letter and one number.");
        try {
            users.registerAccount(fullName.trim(), email.trim(), role, companyId,
                    companyName == null ? null : companyName.trim(), password);
        } catch (RuntimeException e) {
            throw new AuthException(e.getMessage() == null ? "Registration could not be submitted." : e.getMessage());
        }
    }

    /** Registers a callback for when the session can no longer be refreshed (the user must sign in again). */
    public void onSessionExpired(Runnable callback) { sessions.onExpired(callback); }

    /** Changes the signed-in user's own password (minimum 6 characters). */
    public void changePassword(String newPassword) throws AuthException {
        if (newPassword == null || newPassword.length() < 6) throw new AuthException("The new password must be at least 6 characters.");
        try {
            auth.updatePassword(sessions.accessToken(), newPassword);
        } catch (SupabaseException e) {
            if (e.status() == 0) throw new AuthException("Can't reach the server. Check your internet connection.");
            if (e.status() == 401 || e.status() == 403) throw new AuthException("Your session has expired. Please sign in again.");
            throw new AuthException(e.getMessage() == null || e.getMessage().isBlank() ? "Could not change the password." : e.getMessage());
        }
    }

    private AuthException fail(String message) {
        sessions.clear();
        current = null;
        return new AuthException(message);
    }

    /** The signed-in user, or null. */
    public User current() { return current; }

    public void logout() {
        try {
            String token = sessions.current().map(Session::accessToken).orElse(null);
            if (token != null) auth.signOut(token);
        } catch (SupabaseException ignored) { /* signing out locally is what matters */ }
        sessions.clear();
        current = null;
    }

    private static String signInMessage(SupabaseException e) {
        if (e.status() == 0) return "Can't reach the server. Check your internet connection.";
        if (e.status() < 0) return e.getMessage();
        if (e.status() == 400 || e.status() == 401 || e.status() == 422) return "Invalid email or password.";
        if (e.status() == 429) return "Too many attempts. Please wait a moment and try again.";
        return "Sign-in failed (HTTP " + e.status() + "). Please try again.";
    }
}
