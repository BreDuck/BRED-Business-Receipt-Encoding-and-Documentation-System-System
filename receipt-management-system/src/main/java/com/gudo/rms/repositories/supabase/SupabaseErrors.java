package com.gudo.rms.repositories.supabase;

import com.gudo.rms.models.ValidationException;
import com.gudo.rms.repositories.RepositoryException;
import com.gudo.rms.supabase.SupabaseException;

/** Turns raw Supabase failures into messages a person can act on. */
final class SupabaseErrors {
    static final String NETWORK = "Can't reach the server. Check your internet connection.";

    private SupabaseErrors() { }

    static RuntimeException translate(SupabaseException e) {
        int s = e.status();
        String m = e.getMessage() == null ? "" : e.getMessage();
        String lower = m.toLowerCase();
        if (s == 0) return new RepositoryException(NETWORK, true);
        if (s < 0) return new RepositoryException(m, false);
        if (s == 401) return new RepositoryException("Your session has expired. Please sign in again.", false);
        if (lower.contains("violates foreign key")) {
            return new ValidationException("This record is still used by other records, so it can't be deleted.");
        }
        if (s == 403 || lower.contains("permission denied") || lower.contains("row-level security")) {
            return new RepositoryException("You don't have permission to do that.", false);
        }
        if (lower.contains("duplicate key")) {
            return new ValidationException("That already exists. Check the number, TIN or code.");
        }
        if (s == 400 || s == 409 || s == 422) return new ValidationException(m);
        return new RepositoryException("The server had a problem (HTTP " + s + "). Please try again.", false);
    }
}
