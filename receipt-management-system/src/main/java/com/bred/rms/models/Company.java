package com.bred.rms.models;

import java.util.UUID;

/** An organization that accepts BRED account applications. */
public record Company(UUID id, String name) { }
