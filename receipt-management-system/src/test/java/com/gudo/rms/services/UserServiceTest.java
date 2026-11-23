package com.gudo.rms.services;

import com.gudo.rms.models.Role;
import com.gudo.rms.models.User;
import com.gudo.rms.models.ValidationException;
import com.gudo.rms.repositories.RepositoryException;
import com.gudo.rms.repositories.UserRepository;
import com.gudo.rms.support.Fakes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserServiceTest {
    static final class MemoryUsers implements UserRepository {
        final List<User> data = new ArrayList<>();
        RuntimeException failUpdateWith;
        @Override public List<User> findAll() { return new ArrayList<>(data); }
        @Override public Optional<User> findById(UUID id) { return data.stream().filter(u -> u.id().equals(id)).findFirst(); }
        @Override public User create(String email, String fullName, Role role, char[] password) {
            User u = new User(UUID.randomUUID(), fullName, email, role, true);
            data.add(u);
            return u;
        }
        @Override public void update(UUID id, String fullName, Role role, boolean active) {
            if (failUpdateWith != null) throw failUpdateWith;
            data.replaceAll(u -> u.id().equals(id) ? new User(id, fullName, u.email(), role, active) : u);
        }
    }

    private final MemoryUsers repo = new MemoryUsers();

    private UserService as(User who) { return new UserService(repo, () -> who); }

    @Test
    void encoderCannotManageUsers() {
        UserService svc = as(Fakes.encoder());
        assertThrows(SecurityException.class, svc::list);
        assertThrows(SecurityException.class, () -> svc.create("New Person", "n@x.test", Role.ENCODER, "secret1".toCharArray()));
        assertThrows(SecurityException.class, () -> svc.update(UUID.randomUUID(), "X", Role.ADMIN, true));
        assertTrue(repo.data.isEmpty());
    }

    @Test
    void adminCanCreateListAndUpdate() {
        UserService svc = as(Fakes.admin());
        User created = svc.create("  New Person ", " n@x.test ", Role.ENCODER, "secret1".toCharArray());
        assertEquals("n@x.test", created.email());
        assertEquals("New Person", created.fullName());
        svc.update(created.id(), "Renamed", Role.ENCODER, false);
        User after = svc.list().get(0);
        assertEquals("Renamed", after.fullName());
        assertEquals(false, after.active());
    }

    @Test
    void createRejectsShortPasswordAndBadEmail() {
        UserService svc = as(Fakes.admin());
        assertThrows(ValidationException.class, () -> svc.create("A", "a@x.test", Role.ENCODER, "12345".toCharArray()));
        assertThrows(ValidationException.class, () -> svc.create("A", "not-an-email", Role.ENCODER, "secret1".toCharArray()));
        assertThrows(ValidationException.class, () -> svc.create("  ", "a@x.test", Role.ENCODER, "secret1".toCharArray()));
        assertTrue(repo.data.isEmpty());
    }

    @Test
    void lastAdminDemoteErrorMessageIsShown() {
        UserService svc = as(Fakes.admin());
        repo.failUpdateWith = new RepositoryException("You cannot remove the last active administrator.", false);
        RepositoryException e = assertThrows(RepositoryException.class, () -> svc.update(UUID.randomUUID(), "Ada", Role.ENCODER, true));
        assertEquals("You cannot remove the last active administrator.", e.getMessage());
    }
}
