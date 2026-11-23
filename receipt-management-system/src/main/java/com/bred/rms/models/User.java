package com.bred.rms.models;

import java.util.UUID;

/** A signed-in person: profile row plus role. */
public record User(UUID id, String fullName, String email, Role role, boolean active) { }
