package com.bred.rms.services;

import com.bred.rms.models.ChatMessage;
import com.bred.rms.models.Role;
import com.bred.rms.models.User;
import com.bred.rms.models.ValidationException;
import com.bred.rms.repositories.MessageRepository;

import java.util.List;
import java.util.UUID;

/** Global history and broadcast operations reserved for the Creator. */
public final class MessageService {
    private final MessageRepository messages;
    private final CurrentUser who;

    public MessageService(MessageRepository messages, CurrentUser who) {
        this.messages = messages;
        this.who = who;
    }

    public List<ChatMessage> findHistory() {
        requireCreator();
        return messages.findHistory();
    }

    public int sendBroadcast(List<UUID> recipientIds, String body) {
        requireCreator();
        if (recipientIds == null || recipientIds.isEmpty()) {
            throw new ValidationException("Choose at least one recipient.");
        }
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) throw new ValidationException("Write a message before sending.");
        if (text.length() > 4000) throw new ValidationException("Messages can be up to 4000 characters.");
        return messages.sendBroadcast(recipientIds, text);
    }

    private void requireCreator() {
        User user = who.get();
        if (user == null || !user.active() || user.role() != Role.CREATOR) {
            throw new SecurityException("Only the Creator can view global history or send broadcasts.");
        }
    }
}
