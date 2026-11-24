package com.bred.rms.repositories;

import com.bred.rms.models.ChatMessage;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

/** Stores private, one-to-one messages for the signed-in user. */
public interface MessageRepository {
    /** Returns messages sent or received by {@code userId}, oldest first. */
    List<ChatMessage> findFor(UUID userId);

    /** Sends a message to another user. */
    ChatMessage send(UUID recipientId, String body);

    /** Returns the company chat for a company, oldest first. */
    default List<ChatMessage> findCompanyChat(UUID companyId) {
        throw new RepositoryException("Company chat is not available.", false);
    }

    /** Sends a message to the company chat. */
    default ChatMessage sendCompanyChat(UUID companyId, String body, boolean urgent) {
        throw new RepositoryException("Company chat is not available.", false);
    }

    /** Returns all messages across companies; implementations must restrict this to the Creator. */
    default List<ChatMessage> findHistory() {
        throw new RepositoryException("Global message history is not available.", false);
    }

    /** Sends and stores a broadcast for every distinct recipient. */
    default int sendBroadcast(List<UUID> recipientIds, String body) {
        throw new RepositoryException("Broadcast messaging is not available.", false);
    }

    /** Returns new broadcasts delivered to a user since the supplied timestamp. */
    default List<ChatMessage> findBroadcastsFor(UUID userId, Instant since) {
        return List.of();
    }
}
