package com.bred.rms.support;

import com.bred.rms.config.AppConfig;
import com.bred.rms.supabase.AuthClient;
import com.bred.rms.supabase.RestClient;
import com.bred.rms.supabase.Session;
import com.bred.rms.supabase.SessionManager;
import com.bred.rms.supabase.SupabaseHttp;

import java.time.Instant;
import java.util.UUID;

/** Wires the real Supabase client classes to a {@link FakeSupabase}. Test-only. */
public final class TestEnv implements AutoCloseable {
    public final FakeSupabase fake = new FakeSupabase();
    public final SupabaseHttp http = new SupabaseHttp(AppConfig.of(fake.url(), "anon-key"));
    public final SessionManager sessions = new SessionManager(new AuthClient(http));
    public final RestClient rest = new RestClient(http, sessions);

    public TestEnv() {
        sessions.set(new Session("tok1", "ref1", Instant.now().plusSeconds(3600), UUID.randomUUID(), "me@x.test"));
    }

    public static String refreshResponse() {
        return "{\"access_token\":\"tok2\",\"refresh_token\":\"ref2\",\"expires_in\":3600,"
                + "\"user\":{\"id\":\"" + UUID.randomUUID() + "\",\"email\":\"me@x.test\"}}";
    }

    @Override public void close() { fake.close(); }
}
