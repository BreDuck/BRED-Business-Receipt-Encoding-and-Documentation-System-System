package com.bred.rms.repositories.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.bred.rms.models.ActivityEntry;
import com.bred.rms.repositories.ActivityLogRepository;
import com.bred.rms.supabase.RestClient;
import com.bred.rms.supabase.SupabaseException;

import java.util.ArrayList;
import java.util.List;

/** Reads the activity log (admin only through RLS). Shows the latest 1000 entries. */
public final class SupabaseActivityLogRepository implements ActivityLogRepository {
    private final RestClient rest;

    public SupabaseActivityLogRepository(RestClient rest) { this.rest = rest; }

    @Override public List<ActivityEntry> findAll() {
        try {
            JsonNode rows = rest.select("activity_log", "select=*&order=at.desc,id.desc&limit=1000");
            List<ActivityEntry> out = new ArrayList<>();
            for (JsonNode n : rows) {
                out.add(new ActivityEntry(Json.instant(n, "at"), Json.uuid(n, "user_id"), Json.text(n, "user_name"),
                        Json.text(n, "action"), Json.text(n, "detail")));
            }
            return out;
        } catch (SupabaseException e) { throw SupabaseErrors.translate(e); }
    }
}
