package com.bred.rms.services;

import com.bred.rms.models.ChatMessage;
import com.bred.rms.models.Role;
import com.bred.rms.models.User;
import com.bred.rms.models.ValidationException;
import com.bred.rms.repositories.MessageRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageServiceTest {
    private static final class MemoryMessages implements MessageRepository {
        int broadcasts;
        @Override public List<ChatMessage> findFor(UUID userId) { return List.of(); }
        @Override public ChatMessage send(UUID recipientId, String body) { throw new UnsupportedOperationException(); }
        @Override public List<ChatMessage> findHistory() {
            return List.of(new ChatMessage(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    "hello", Instant.EPOCH));
        }
        @Override public int sendBroadcast(List<UUID> recipientIds, String body) {
            broadcasts++;
            return recipientIds.size();
        }
    }

    @Test
    void globalHistoryAndBroadcastAreCreatorOnly() {
        MemoryMessages repository = new MemoryMessages();
        MessageService creator = new MessageService(repository, () ->
                new User(UUID.randomUUID(), "Creator", "creator@example.com", Role.CREATOR, true));
        MessageService admin = new MessageService(repository, () ->
                new User(UUID.randomUUID(), "Admin", "admin@example.com", Role.ADMIN, true));

        assertEquals(1, creator.findHistory().size());
        UUID recipient = UUID.randomUUID();
        assertEquals(1, creator.sendBroadcast(List.of(recipient), "  Notice  "));
        assertThrows(SecurityException.class, admin::findHistory);
        assertThrows(SecurityException.class, () -> admin.sendBroadcast(List.of(recipient), "Notice"));
        assertThrows(ValidationException.class, () -> creator.sendBroadcast(List.of(recipient), " "));
        assertThrows(ValidationException.class, () -> creator.sendBroadcast(List.of(), "Notice"));
        assertEquals(1, repository.broadcasts);
    }
}
