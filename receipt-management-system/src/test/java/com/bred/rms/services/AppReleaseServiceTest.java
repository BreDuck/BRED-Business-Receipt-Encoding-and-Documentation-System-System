package com.bred.rms.services;

import com.bred.rms.models.AppRelease;
import com.bred.rms.models.AccountStatus;
import com.bred.rms.models.Role;
import com.bred.rms.models.User;
import com.bred.rms.models.ValidationException;
import com.bred.rms.repositories.AppReleaseRepository;
import com.bred.rms.repositories.RepositoryException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppReleaseServiceTest {
    @Test
    void onlyActiveCreatorCanPublishANormalizedVersion() {
        User creator = new User(UUID.randomUUID(), "Creator", "creator@example.test", Role.CREATOR, true);
        AtomicReference<String> published = new AtomicReference<>();
        AppReleaseService service = new AppReleaseService(repository(published), () -> creator);

        assertEquals("1.2.3-beta.1", service.publish(" 1.2.3-beta.1 ").version());
        assertEquals("1.2.3-beta.1", published.get());
    }

    @Test
    void rejectsNonCreatorInactiveAndInvalidVersion() {
        User encoder = new User(UUID.randomUUID(), "Encoder", "encoder@example.test", Role.ENCODER, true);
        AppReleaseService nonCreator = new AppReleaseService(repository(new AtomicReference<>()), () -> encoder);
        AppReleaseService inactiveCreator = new AppReleaseService(repository(new AtomicReference<>()),
                () -> new User(UUID.randomUUID(), "Creator", "creator@example.test", Role.CREATOR, false));
        AppReleaseService pendingCreator = new AppReleaseService(repository(new AtomicReference<>()),
                () -> new User(UUID.randomUUID(), "Creator", "creator@example.test", Role.CREATOR, true,
                        AccountStatus.PENDING, Instant.EPOCH));
        AppReleaseService creator = new AppReleaseService(repository(new AtomicReference<>()),
                () -> new User(UUID.randomUUID(), "Creator", "creator@example.test", Role.CREATOR, true));

        assertThrows(RepositoryException.class, () -> nonCreator.publish("1.2.3"));
        assertThrows(RepositoryException.class, () -> inactiveCreator.publish("1.2.3"));
        assertThrows(RepositoryException.class, () -> pendingCreator.publish("1.2.3"));
        assertThrows(ValidationException.class, () -> creator.publish("latest"));
    }

    private static AppReleaseRepository repository(AtomicReference<String> published) {
        return new AppReleaseRepository() {
            @Override public AppRelease current() {
                return new AppRelease("0.1.0-SNAPSHOT", Instant.EPOCH);
            }

            @Override public AppRelease publish(String version) {
                published.set(version);
                return new AppRelease(version, Instant.EPOCH);
            }
        };
    }
}
