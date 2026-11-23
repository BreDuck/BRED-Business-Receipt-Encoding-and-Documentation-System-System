package com.gudo.rms.support;

import com.gudo.rms.config.AppConfig;
import com.gudo.rms.supabase.AuthClient;
import com.gudo.rms.supabase.RestClient;
import com.gudo.rms.supabase.Session;
import com.gudo.rms.supabase.SessionManager;
import com.gudo.rms.supabase.SupabaseHttp;

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
