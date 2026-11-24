package com.bred.rms.models;

import java.time.Instant;
import java.util.UUID;

/** A bug report submitted by a signed-in company user. */
public record BugReport(UUID id, String companyName, String reporterName, String reporterEmail,
                        String title, String description, String attachmentPath, Instant createdAt) { }
