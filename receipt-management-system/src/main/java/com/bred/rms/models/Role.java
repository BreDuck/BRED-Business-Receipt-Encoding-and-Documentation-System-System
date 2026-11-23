package com.bred.rms.models;

/** Application roles. Admin does everything; Encoder records receipts and master data. */
public enum Role {
    ADMIN("Administrator"), ENCODER("Encoder");

    private final String label;

    Role(String label) { this.label = label; }

    /** Returns the display label. */
    public String label() { return label; }

    /** Returns the value stored in the database ("admin" / "encoder"). */
    public String dbValue() { return name().toLowerCase(java.util.Locale.ROOT); }

    /** Reads a database value; anything unknown gets the least-privileged role. */
    public static Role fromDb(String value) { return "admin".equalsIgnoreCase(value) ? ADMIN : ENCODER; }
}
