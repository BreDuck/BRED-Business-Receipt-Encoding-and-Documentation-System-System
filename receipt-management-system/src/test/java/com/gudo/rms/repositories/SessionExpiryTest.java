package com.gudo.rms.repositories;

import com.gudo.rms.supabase.Session;
import com.gudo.rms.supabase.SupabaseException;
import com.gudo.rms.support.TestEnv;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionExpiryTest {
    private static final String DEAD = "{\"error\":\"invalid_grant\",\"error_description\":\"Invalid Refresh Token: Already Used\"}";

    @Test
    void deadRefreshTokenOnProactiveRefreshEndsTheSessionAndSignalsExpiry() {
        try (TestEnv env = new TestEnv()) {
            env.sessions.set(new Session("old", "dead", Instant.now().plusSeconds(5), UUID.randomUUID(), "me@x.test"));
            AtomicInteger expired = new AtomicInteger();
            env.sessions.onExpired(expired::incrementAndGet);
            env.fake.on("POST", "/auth/v1/token", 400, DEAD);
            SupabaseException e = assertThrows(SupabaseException.class, env.sessions::accessToken);
            assertEquals(401, e.status());
            assertEquals("Your session has expired.", e.getMessage());
            assertTrue(env.sessions.current().isEmpty());
            assertEquals(1, expired.get());
        }
    }

    @Test
    void repositoryShowsPlainExpiredMessageNotGoTrueText() {
        try (TestEnv env = new TestEnv()) {
            env.sessions.set(new Session("old", "dead", Instant.now().plusSeconds(5), UUID.randomUUID(), "me@x.test"));
            env.fake.on("POST", "/auth/v1/token", 400, DEAD);
            RepositoryException e = assertThrows(RepositoryException.class, () -> new com.gudo.rms.repositories.supabase.SupabaseSupplierRepository(env.rest).findAll());
            assertEquals("Your session has expired. Please sign in again.", e.getMessage());
        }
    }

    @Test
    void networkFailureDuringRefreshKeepsTheSession() {
        try (TestEnv env = new TestEnv()) {
            env.sessions.set(new Session("old", "ok", Instant.now().plusSeconds(5), UUID.randomUUID(), "me@x.test"));
            AtomicInteger expired = new AtomicInteger();
            env.sessions.onExpired(expired::incrementAndGet);
            env.fake.close();
            assertThrows(SupabaseException.class, env.sessions::accessToken);
            assertTrue(env.sessions.current().isPresent());
            assertEquals(0, expired.get());
        }
    }
}
