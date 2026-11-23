package com.gudo.rms.services;

import com.gudo.rms.models.Role;
import com.gudo.rms.models.User;

/** Shared permission and text helpers for services. The database enforces the same rules; this gives fast, clear errors. */
final class Guard {
    private Guard() { }

    static void requireAdmin(CurrentUser who, String message) {
        User u = who.get();
        if (u == null || u.role() != Role.ADMIN || !u.active()) throw new SecurityException(message);
    }

    static void requireSignedIn(CurrentUser who) {
        if (who.get() == null || !who.get().active()) throw new SecurityException("Please sign in first.");
    }

    /** Lower-cased, trimmed text for comparisons; null becomes "". */
    static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(java.util.Locale.ROOT); }

    static boolean blank(String s) { return s == null || s.isBlank(); }
}
