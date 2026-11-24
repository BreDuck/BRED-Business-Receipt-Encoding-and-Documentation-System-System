package com.bred.rms.models;

import java.time.Instant;
import java.util.UUID;

/** A direct or broadcast message. Names remain available when an account is deleted. */
public record ChatMessage(UUID id, UUID senderId, UUID recipientId, String body, Instant sentAt,
                          boolean broadcast, String senderName, String senderEmail, String recipientName) {
    public ChatMessage(UUID id, UUID senderId, UUID recipientId, String body, Instant sentAt) {
        this(id, senderId, recipientId, body, sentAt, false, "", "", "");
    }

    public ChatMessage(UUID id, UUID senderId, UUID recipientId, String body, Instant sentAt,
                       boolean broadcast, String senderName, String recipientName) {
        this(id, senderId, recipientId, body, sentAt, broadcast, senderName, "", recipientName);
    }

    public ChatMessage {
        senderName = senderName == null ? "" : senderName;
        senderEmail = senderEmail == null ? "" : senderEmail;
        recipientName = recipientName == null ? "" : recipientName;
    }
}
