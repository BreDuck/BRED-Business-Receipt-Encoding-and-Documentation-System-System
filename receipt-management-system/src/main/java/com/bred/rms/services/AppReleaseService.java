package com.bred.rms.services;

import com.bred.rms.models.AppRelease;
import com.bred.rms.models.AccountStatus;
import com.bred.rms.models.Role;
import com.bred.rms.models.User;
import com.bred.rms.models.ValidationException;
import com.bred.rms.repositories.AppReleaseRepository;
import com.bred.rms.repositories.RepositoryException;

import java.util.regex.Pattern;

/** Applies application-level validation and Creator permissions for release publishing. */
public final class AppReleaseService {
    private static final Pattern VERSION = Pattern.compile(
            "[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?");

    private final AppReleaseRepository repository;
    private final CurrentUser currentUser;

    public AppReleaseService(AppReleaseRepository repository, CurrentUser currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    public AppRelease current() {
        return repository.current();
    }

    public AppRelease publish(String version) {
        User user = currentUser.get();
        if (user == null || !user.active() || user.accountStatus() != AccountStatus.APPROVED
                || user.role() != Role.CREATOR) {
            throw new RepositoryException("Only an active Creator can publish a new app version.", false);
        }
        String normalized = version == null ? "" : version.trim();
        if (normalized.length() > 64 || !VERSION.matcher(normalized).matches()) {
            throw new ValidationException("Enter a version such as 1.2.3 or 1.2.3-beta.1 (up to 64 characters).");
        }
        return repository.publish(normalized);
    }
}
