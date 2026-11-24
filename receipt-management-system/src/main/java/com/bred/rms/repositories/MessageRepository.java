package com.bred.rms.repositories;

import com.bred.rms.models.ChatMessage;

import java.util.List;
import java.util.UUID;

/** Stores private, one-to-one messages for the signed-in user. */
public interface MessageRepository {
    /** Returns messages sent or received by {@code userId}, oldest first. */
    List<ChatMessage> findFor(UUID userId);

    /** Sends a message to another user. */
    ChatMessage send(UUID recipientId, String body);
}
