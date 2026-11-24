package com.bred.rms.repositories.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.bred.rms.models.ChatMessage;
import com.bred.rms.repositories.MessageRepository;
import com.bred.rms.repositories.RepositoryException;
import com.bred.rms.supabase.RestClient;
import com.bred.rms.supabase.SupabaseException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Direct messages stored in Supabase; row-level security limits reads to participants. */
public final class SupabaseMessageRepository implements MessageRepository {
    private static final int MAX_BODY_LENGTH = 4000;
    private final RestClient rest;

    public SupabaseMessageRepository(RestClient rest) {
        this.rest = rest;
    }

    @Override public List<ChatMessage> findFor(UUID userId) {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            String query = "select=id,sender_id,recipient_id,body,sent_at"
                    + "&or=(sender_id.eq." + userId + ",recipient_id.eq." + userId + ")"
                    + "&order=sent_at.asc,id.asc";
            for (JsonNode row : rest.selectAll("custom_messages", query)) messages.add(map(row));
            return List.copyOf(messages);
        } catch (SupabaseException e) {
            throw SupabaseErrors.translate(e);
        }
    }

    @Override public ChatMessage send(UUID recipientId, String body) {
        String text = validateBody(body);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("recipient_id", recipientId.toString());
        row.put("body", text);
        try {
            JsonNode result = rest.insert("custom_messages", row);
            if (!result.isArray() || result.isEmpty()) {
                throw new RepositoryException("The message was not saved. Please try again.", false);
            }
            return map(result.get(0));
        } catch (SupabaseException e) {
            throw SupabaseErrors.translate(e);
        }
    }

    private static String validateBody(String body) {
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) throw new RepositoryException("Write a message before sending.", false);
        if (text.length() > MAX_BODY_LENGTH) {
            throw new RepositoryException("Messages can be up to " + MAX_BODY_LENGTH + " characters.", false);
        }
        return text;
    }

    private static ChatMessage map(JsonNode row) {
        Instant sentAt = Json.instant(row, "sent_at");
        return new ChatMessage(Json.uuid(row, "id"), Json.uuid(row, "sender_id"),
                Json.uuid(row, "recipient_id"), Json.text(row, "body"), sentAt);
    }
}
