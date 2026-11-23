package com.gudo.rms.supabase;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** PostgREST + Storage access. Row Level Security applies because every call carries the user's JWT. */
public final class RestClient {
    /** PostgREST returns at most this many rows per request on Supabase by default. */
    private static final int PAGE = 1000;

    private final SupabaseHttp http;
    private final SessionManager sessions;

    @FunctionalInterface
    private interface Call<T> { T run(String token) throws SupabaseException; }

    /** Creates a rest client instance. */
    public RestClient(SupabaseHttp http, SessionManager sessions) {
        this.http = http;
        this.sessions = sessions;
    }

    /** Runs a call with the current token; on a 401 refreshes the token once and retries once. */
    private <T> T authed(Call<T> call) throws SupabaseException {
        try {
            return call.run(sessions.accessToken());
        } catch (SupabaseException e) {
            if (e.status() != 401 || sessions.current().isEmpty()) throw e;
            sessions.forceRefresh();
            return call.run(sessions.accessToken());
        }
    }

    private static String q(String query) { return query == null || query.isEmpty() ? "" : "?" + query; }

    /** @param query PostgREST query string without the leading "?", e.g. "select=*&order=name.asc" */
    public JsonNode select(String table, String query) throws SupabaseException {
        return authed(t -> http.send("GET", "/rest/v1/" + table + q(query), null, t, null));
    }

    /** Like {@link #select} but follows the server's row cap by paging until everything is read. */
    public List<JsonNode> selectAll(String table, String query) throws SupabaseException {
        List<JsonNode> all = new ArrayList<>();
        for (int offset = 0; ; offset += PAGE) {
            JsonNode page = select(table, query + "&limit=" + PAGE + "&offset=" + offset);
            if (!page.isArray()) break;
            page.forEach(all::add);
            if (page.size() < PAGE) break;
        }
        return all;
    }

    /** Calls a Supabase RPC with the supplied JSON arguments. */
    public JsonNode rpc(String function, Object args) throws SupabaseException {
        String body = args == null ? "{}" : SupabaseHttp.toJson(args);
        return authed(t -> http.send("POST", "/rest/v1/rpc/" + function, body, t, null));
    }

    /** Invokes a Supabase Edge Function with the supplied request body. */
    public JsonNode invokeFunction(String function, Object args) throws SupabaseException {
        String body = args == null ? "{}" : SupabaseHttp.toJson(args);
        return authed(t -> http.send("POST", "/functions/v1/" + function, body, t, null));
    }

    /** Inserts a row and returns the stored row(s). */
    public JsonNode insert(String table, Object row) throws SupabaseException {
        String body = SupabaseHttp.toJson(row);
        return authed(t -> http.send("POST", "/rest/v1/" + table, body, t, Map.of("Prefer", "return=representation")));
    }

    /** PATCH (update) rows matching the query filter and returns them. */
    public JsonNode patch(String table, String query, Object body) throws SupabaseException {
        String json = SupabaseHttp.toJson(body);
        return authed(t -> http.send("PATCH", "/rest/v1/" + table + q(query), json, t, Map.of("Prefer", "return=representation")));
    }

    /** Deletes rows matching the query filter and returns the deleted records. */
    public JsonNode delete(String table, String query) throws SupabaseException {
        return authed(t -> http.send("DELETE", "/rest/v1/" + table + q(query), null, t, Map.of("Prefer", "return=representation")));
    }

    /** Uploads a file to Storage (never overwrites an existing object). */
    public void uploadObject(String bucket, String path, byte[] data, String contentType) throws SupabaseException {
        authed(t -> http.sendBytes("POST", "/storage/v1/object/" + bucket + "/" + path, data, contentType, t, Map.of("x-upsert", "false")));
    }

    /** Downloads a file from a private Storage bucket. */
    public byte[] downloadObject(String bucket, String path) throws SupabaseException {
        return authed(t -> http.sendBytes("GET", "/storage/v1/object/authenticated/" + bucket + "/" + path, null, null, t, null));
    }

    /** Deletes a file from Storage. */
    public void deleteObject(String bucket, String path) throws SupabaseException {
        authed(t -> http.sendBytes("DELETE", "/storage/v1/object/" + bucket + "/" + path, null, null, t, null));
    }
}
