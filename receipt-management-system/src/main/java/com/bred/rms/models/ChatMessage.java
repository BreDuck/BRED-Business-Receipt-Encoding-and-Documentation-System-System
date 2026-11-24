package com.bred.rms.models;

import java.time.Instant;
import java.util.UUID;

/** A direct, broadcast, or company chat message. Names remain available after account deletion. */
public record ChatMessage(UUID id, UUID senderId, UUID recipientId, String body, Instant sentAt,
                          boolean broadcast, String senderName, String senderEmail, String recipientName,
                          UUID companyId, boolean urgent) {
    public ChatMessage(UUID id, UUID senderId, UUID recipientId, String body, Instant sentAt) {
        this(id, senderId, recipientId, body, sentAt, false, "", "", "", null, false);
    }

    public ChatMessage(UUID id, UUID senderId, UUID recipientId, String body, Instant sentAt,
                       boolean broadcast, String senderName, String recipientName) {
        this(id, senderId, recipientId, body, sentAt, broadcast, senderName, "", recipientName, null, false);
    }

    public ChatMessage(UUID id, UUID senderId, UUID recipientId, String body, Instant sentAt,
                       boolean broadcast, String senderName, String senderEmail, String recipientName) {
        this(id, senderId, recipientId, body, sentAt, broadcast, senderName, senderEmail, recipientName, null, false);
    }

    public ChatMessage {
        senderName = senderName == null ? "" : senderName;
        senderEmail = senderEmail == null ? "" : senderEmail;
        recipientName = recipientName == null ? "" : recipientName;
    }
}
