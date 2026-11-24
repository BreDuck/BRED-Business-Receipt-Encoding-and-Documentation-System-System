package com.bred.rms.services;

import com.bred.rms.models.Role;
import com.bred.rms.models.User;
import com.bred.rms.repositories.supabase.SupabaseUserRepository;
import com.bred.rms.supabase.AuthClient;
import com.bred.rms.support.TestEnv;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {
    private static final String UID = "dddddddd-0000-0000-0000-000000000009";
    private static final UUID COMPANY_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");

    private static String tokenResponse() {
        return "{\"access_token\":\"tokA\",\"refresh_token\":\"refA\",\"expires_in\":3600,\"user\":{\"id\":\"" + UID + "\",\"email\":\"ada@x.test\"}}";
    }

    private static String profile(boolean active) {
        return "[{\"id\":\"" + UID + "\",\"full_name\":\"Ada Admin\",\"email\":\"ada@x.test\",\"role\":\"admin\",\"is_active\":"
                + active + ",\"account_status\":\"APPROVED\",\"created_at\":\"2026-01-01T00:00:00Z\"}]";
    }

    private static String profile(String status, boolean active) {
        return "[{\"id\":\"" + UID + "\",\"full_name\":\"Ada\",\"email\":\"ada@x.test\",\"role\":\"encoder\",\"is_active\":"
                + active + ",\"account_status\":\"" + status + "\",\"created_at\":\"2026-01-01T00:00:00Z\"}]";
    }

    private static AuthService service(TestEnv env) {
        return new AuthService(new AuthClient(env.http), env.sessions, new SupabaseUserRepository(env.rest));
    }

    @Test
    void loginSignsInAndLoadsProfile() throws Exception {
        try (TestEnv env = new TestEnv()) {
            env.sessions.clear();
            env.fake.on("POST", "/auth/v1/token", 200, tokenResponse());
            env.fake.on("GET", "/rest/v1/profiles", 200, profile(true));
            AuthService auth = service(env);
            User u = auth.login(" ada@x.test ", "pw123456".toCharArray());
            assertEquals(Role.ADMIN, u.role());
            assertEquals(UUID.fromString(UID), auth.current().id());
            assertTrue(env.fake.requests("POST", "/auth/v1/token").get(0).bodyString().contains("\"email\":\"ada@x.test\""));
            auth.logout();
            assertEquals(null, auth.current());
            assertTrue(env.sessions.current().isEmpty());
        }
    }

    @Test
    void wrongPasswordGivesFriendlyMessage() {
        try (TestEnv env = new TestEnv()) {
            env.sessions.clear();
            env.fake.on("POST", "/auth/v1/token", 400, "{\"error\":\"invalid_grant\",\"error_description\":\"Invalid login credentials\"}");
            AuthException e = assertThrows(AuthException.class, () -> service(env).login("ada@x.test", "bad".toCharArray()));
            assertEquals("Invalid email or password.", e.getMessage());
        }
    }

    @Test
    void inactiveAccountIsRejectedAndSessionCleared() {
        try (TestEnv env = new TestEnv()) {
            env.sessions.clear();
            env.fake.on("POST", "/auth/v1/token", 200, tokenResponse());
            env.fake.on("GET", "/rest/v1/profiles", 200, profile(false));
            AuthException e = assertThrows(AuthException.class, () -> service(env).login("ada@x.test", "pw123456".toCharArray()));
            assertEquals("This account is inactive.", e.getMessage());
            assertTrue(env.sessions.current().isEmpty());
        }
    }

    @Test
    void pendingRegistrationIsRejectedAndSessionCleared() {
        try (TestEnv env = new TestEnv()) {
            env.sessions.clear();
            env.fake.on("POST", "/auth/v1/token", 200, tokenResponse());
            env.fake.on("GET", "/rest/v1/profiles", 200, profile("PENDING", false));
            AuthException e = assertThrows(AuthException.class,
                    () -> service(env).login("ada@x.test", "pw123456".toCharArray()));
            assertEquals("Your registration is pending administrator approval.", e.getMessage());
            assertTrue(env.sessions.current().isEmpty());
        }
    }

    @Test
    void rejectedRegistrationIsRejectedAndSessionCleared() {
        try (TestEnv env = new TestEnv()) {
            env.sessions.clear();
            env.fake.on("POST", "/auth/v1/token", 200, tokenResponse());
            env.fake.on("GET", "/rest/v1/profiles", 200, profile("REJECTED", false));
            AuthException e = assertThrows(AuthException.class,
                    () -> service(env).login("ada@x.test", "pw123456".toCharArray()));
            assertEquals("Your registration was rejected. Please contact an administrator.", e.getMessage());
            assertTrue(env.sessions.current().isEmpty());
        }
    }

    @Test
    void registrationSubmitsRequestWithoutSigningIn() throws Exception {
        try (TestEnv env = new TestEnv()) {
            env.sessions.clear();
            env.fake.on("POST", "/functions/v1/register-account", 201,
                    "{\"message\":\"Your registration has been submitted successfully.\"}");
            service(env).registerAccount("Ada Lovelace", "ada@x.test", Role.ENCODER, COMPANY_ID, null,
                    "Password123".toCharArray());
            var request = env.fake.requests("POST", "/functions/v1/register-account").get(0);
            assertTrue(request.bodyString().contains("\"full_name\":\"Ada Lovelace\""));
            assertTrue(request.bodyString().contains("\"email\":\"ada@x.test\""));
            assertTrue(request.bodyString().contains("\"password\":\"Password123\""));
            assertTrue(request.bodyString().contains("\"company_id\":\"" + COMPANY_ID + "\""));
            assertTrue(env.sessions.current().isEmpty());
        }
    }

    @Test
    void registrationRejectsWeakPasswordBeforeRequest() {
        try (TestEnv env = new TestEnv()) {
            AuthException e = assertThrows(AuthException.class,
                    () -> service(env).registerAccount("Ada", "ada@x.test", Role.ENCODER, COMPANY_ID, null,
                            "12345678".toCharArray()));
            assertTrue(e.getMessage().contains("letter and one number"));
            assertTrue(env.fake.requests().isEmpty());
        }
    }

    @Test
    void adminRegistrationCreatesCompanyWithoutSelectingAnExistingOne() throws Exception {
        try (TestEnv env = new TestEnv()) {
            env.sessions.clear();
            env.fake.on("POST", "/functions/v1/register-account", 201,
                    "{\"message\":\"Your company and administrator account are ready.\"}");
            service(env).registerAccount("Ada Admin", "ada@x.test", Role.ADMIN, null, "Acme Ltd",
                    "Password123".toCharArray());
            var request = env.fake.requests("POST", "/functions/v1/register-account").get(0);
            assertTrue(request.bodyString().contains("\"role\":\"admin\""));
            assertTrue(request.bodyString().contains("\"company_name\":\"Acme Ltd\""));
            assertTrue(!request.bodyString().contains("company_id"));
            assertTrue(env.sessions.current().isEmpty());
        }
    }

    @Test
    void adminRegistrationRequiresCompanyName() {
        try (TestEnv env = new TestEnv()) {
            AuthException e = assertThrows(AuthException.class,
                    () -> service(env).registerAccount("Ada", "ada@x.test", Role.ADMIN, null, " ",
                            "Password123".toCharArray()));
            assertTrue(e.getMessage().contains("company name"));
            assertTrue(env.fake.requests().isEmpty());
        }
    }

    @Test
    void missingProfileIsRejected() {
        try (TestEnv env = new TestEnv()) {
            env.sessions.clear();
            env.fake.on("POST", "/auth/v1/token", 200, tokenResponse());
            env.fake.on("GET", "/rest/v1/profiles", 200, "[]");
            AuthException e = assertThrows(AuthException.class, () -> service(env).login("ada@x.test", "pw123456".toCharArray()));
            assertTrue(e.getMessage().contains("administrator"), e.getMessage());
            assertTrue(env.sessions.current().isEmpty());
        }
    }

    @Test
    void offlineGivesFriendlyMessage() {
        TestEnv env = new TestEnv();
        env.sessions.clear();
        env.close();
        AuthException e = assertThrows(AuthException.class, () -> service(env).login("ada@x.test", "pw123456".toCharArray()));
        assertEquals("Can't reach the server. Check your internet connection.", e.getMessage());
    }

    @Test
    void blankEmailOrPasswordIsRejectedWithoutARequest() {
        try (TestEnv env = new TestEnv()) {
            assertThrows(AuthException.class, () -> service(env).login("  ", "x".toCharArray()));
            assertThrows(AuthException.class, () -> service(env).login("a@b.c", new char[0]));
            assertEquals(0, env.fake.requests().size());
        }
    }

    @Test
    void changePasswordRejectsShortPassword() {
        try (TestEnv env = new TestEnv()) {
            AuthException e = assertThrows(AuthException.class, () -> service(env).changePassword("12345"));
            assertEquals("The new password must be at least 6 characters.", e.getMessage());
            assertTrue(env.fake.requests("PUT", "/auth/v1/user").isEmpty());
        }
    }

    @Test
    void changePasswordSendsPutWithSessionToken() throws Exception {
        try (TestEnv env = new TestEnv()) {
            env.fake.on("PUT", "/auth/v1/user", 200, "{\"id\":\"" + UID + "\"}");
            service(env).changePassword("brand-new-pw");
            var req = env.fake.requests("PUT", "/auth/v1/user").get(0);
            assertTrue(req.bodyString().contains("\"password\":\"brand-new-pw\""), req.bodyString());
            assertTrue(req.headers().toString().toLowerCase().contains("bearer "), req.headers().toString());
        }
    }
}
