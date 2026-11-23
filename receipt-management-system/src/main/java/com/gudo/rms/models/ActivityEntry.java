package com.gudo.rms.models;

import java.time.Instant;
import java.util.UUID;

/** One row of the activity log. */
public record ActivityEntry(Instant at, UUID userId, String userName, String action, String detail) { }
