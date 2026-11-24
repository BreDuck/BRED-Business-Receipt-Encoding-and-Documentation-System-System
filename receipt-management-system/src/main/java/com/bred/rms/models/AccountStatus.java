package com.bred.rms.models;

/** Approval state recorded on a Supabase profile. */
public enum AccountStatus {
    PENDING,
    APPROVED,
    REJECTED;

    /** Parses the database value, treating profiles created before approval workflow as approved. */
    public static AccountStatus fromDb(String value) {
        if (value == null || value.isBlank()) return APPROVED;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return APPROVED;
        }
    }

    public String dbValue() { return name(); }
}
