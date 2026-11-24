package com.bred.rms.models;

import java.time.Instant;
import java.util.UUID;

/** A direct message exchanged by two application users. */
public record ChatMessage(UUID id, UUID senderId, UUID recipientId, String body, Instant sentAt) { }
