package com.bred.rms.models;

import java.time.Instant;
import java.util.UUID;

/** A signed-in person: profile row plus role. */
public record User(UUID id, String fullName, String email, Role role, boolean active,
                   AccountStatus accountStatus, Instant createdAt, String companyName, UUID companyId) {
    public User {
        accountStatus = accountStatus == null ? AccountStatus.APPROVED : accountStatus;
        createdAt = createdAt == null ? Instant.EPOCH : createdAt;
        companyName = companyName == null ? "" : companyName;
    }

    /** Compatibility constructor for local accounts and existing test fixtures. */
    public User(UUID id, String fullName, String email, Role role, boolean active) {
        this(id, fullName, email, role, active, AccountStatus.APPROVED, Instant.EPOCH, "", null);
    }

    /** Compatibility constructor for callers without company profile data. */
    public User(UUID id, String fullName, String email, Role role, boolean active,
                AccountStatus accountStatus, Instant createdAt) {
        this(id, fullName, email, role, active, accountStatus, createdAt, "", null);
    }

    public User(UUID id, String fullName, String email, Role role, boolean active,
                AccountStatus accountStatus, Instant createdAt, String companyName) {
        this(id, fullName, email, role, active, accountStatus, createdAt, companyName, null);
    }
}
