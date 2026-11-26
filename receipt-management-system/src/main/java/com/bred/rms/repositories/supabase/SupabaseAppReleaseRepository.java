package com.bred.rms.repositories.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.bred.rms.models.AppRelease;
import com.bred.rms.repositories.AppReleaseRepository;
import com.bred.rms.repositories.RepositoryException;
import com.bred.rms.supabase.RestClient;
import com.bred.rms.supabase.SupabaseException;
import com.bred.rms.supabase.SupabaseHttp;

import java.util.Map;

/** Reads the public release version and publishes updates through Creator-protected RLS. */
public final class SupabaseAppReleaseRepository implements AppReleaseRepository {
    private static final String RELEASE_QUERY = "release_key=eq.current&select=version,updated_at";
    private final SupabaseHttp http;
    private final RestClient rest;

    public SupabaseAppReleaseRepository(SupabaseHttp http, RestClient rest) {
        this.http = http;
        this.rest = rest;
    }

    @Override
    public AppRelease current() {
        try {
            JsonNode result = http.send("GET", "/rest/v1/app_releases?" + RELEASE_QUERY, null, null, null);
            return readRelease(result);
        } catch (SupabaseException e) {
            if (e.status() == 404) {
                throw new RepositoryException("Apply database migration 013 to enable app release versions.", false);
            }
            throw SupabaseErrors.translate(e);
        }
    }

    @Override
    public AppRelease publish(String version) {
        try {
            JsonNode result = rest.patch("app_releases", "release_key=eq.current",
                    Map.of("version", version));
            return readRelease(result);
        } catch (SupabaseException e) {
            if (e.status() == 403) {
                throw new RepositoryException(
                        "Only the Creator can publish a version. Confirm database migration 013 is applied.", false);
            }
            throw SupabaseErrors.translate(e);
        }
    }

    private static AppRelease readRelease(JsonNode result) {
        if (!result.isArray() || result.isEmpty()) {
            throw new RepositoryException("No current app release is configured. Apply database migration 013.", false);
        }
        JsonNode row = result.get(0);
        String version = Json.text(row, "version");
        return new AppRelease(version, Json.instant(row, "updated_at"));
    }
}
