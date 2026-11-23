package com.bred.rms.supabase;

import java.util.Optional;

/** Holds the signed-in session in memory only and refreshes the access token shortly before it expires. */
public final class SessionManager {
    private final AuthClient auth;
    private Session session;
    private Runnable onExpired;

    /** Creates a session manager instance. */
    public SessionManager(AuthClient auth) { this.auth = auth; }

    /** Stores the current authenticated session in memory. */
    public synchronized void set(Session s) { this.session = s; }
    /** Clears the current authenticated session. */
    public synchronized void clear() { this.session = null; }
    /** Returns the current session, or null when signed out. */
    public synchronized Optional<Session> current() { return Optional.ofNullable(session); }

    /** Registers a callback run (on whatever thread detected it) when the session can no longer be refreshed. */
    public synchronized void onExpired(Runnable callback) { this.onExpired = callback; }

    /** Forces a token refresh (used after the server answered 401). Failure means the user must sign in again. */
    public synchronized void forceRefresh() throws SupabaseException {
        if (session == null) throw new SupabaseException(401, "Not signed in.");
        refreshOrExpire();
    }

    /** @return a valid JWT, or null when nobody is signed in (anonymous/guest requests). */
    public synchronized String accessToken() throws SupabaseException {
        if (session == null) return null;
        if (session.expiresWithin(60)) refreshOrExpire();
        return session.accessToken();
    }

    /** Refreshes the token. A rejected refresh token ends the session; a network failure keeps it. */
    private void refreshOrExpire() throws SupabaseException {
        try {
            session = auth.refresh(session.refreshToken());
        } catch (SupabaseException e) {
            if (e.isNetworkError() || e.status() < 0) throw e;
            session = null;
            if (onExpired != null) onExpired.run();
            throw new SupabaseException(401, "Your session has expired.");
        }
    }
}
