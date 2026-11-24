package com.bred.rms.models;

/** Application roles. Creator has global access; Admin manages one company; Encoder records company data. */
public enum Role {
    CREATOR("Creator"), ADMIN("Administrator"), ENCODER("Encoder");

    private final String label;

    Role(String label) { this.label = label; }

    /** Returns the display label. */
    public String label() { return label; }

    /** Returns the value stored in the database. */
    public String dbValue() { return name().toLowerCase(java.util.Locale.ROOT); }

    /** Reads a database value; anything unknown gets the least-privileged role. */
    public static Role fromDb(String value) {
        if (value == null) return ENCODER;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ENCODER;
        }
    }

    public boolean canManageCompany() { return this == ADMIN || this == CREATOR; }
}
