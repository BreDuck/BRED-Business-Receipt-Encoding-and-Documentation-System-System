package com.bred.rms.models;

import java.time.Instant;

/** The version currently published for all BRED installations. */
public record AppRelease(String version, Instant updatedAt) {
    public AppRelease {
        if (version == null || version.isBlank()) throw new IllegalArgumentException("version");
        updatedAt = updatedAt == null ? Instant.EPOCH : updatedAt;
    }
}
