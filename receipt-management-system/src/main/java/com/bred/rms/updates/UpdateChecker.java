package com.bred.rms.updates;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Compares the installed version with GitHub and records local check history. */
public final class UpdateChecker {
    private final String currentVersion;
    private final GitHubReleaseClient releases;
    private final UpdatePreferences preferences;
    private final Clock clock;

    public UpdateChecker(String currentVersion, GitHubReleaseClient releases, UpdatePreferences preferences) {
        this(currentVersion, releases, preferences, Clock.systemUTC());
    }

    UpdateChecker(String currentVersion, GitHubReleaseClient releases, UpdatePreferences preferences, Clock clock) {
        this.currentVersion = VersionComparator.normalize(currentVersion)
                .orElseThrow(() -> new IllegalArgumentException("Invalid installed version: " + currentVersion));
        this.releases = releases;
        this.preferences = preferences;
        this.clock = clock;
    }

    /** Manually checks for a newer stable release, regardless of the automatic-check preference. */
    public CompletableFuture<Optional<UpdateInfo>> checkForUpdate() {
        return check();
    }

    /** Checks in the background only when automatic checking is enabled in local preferences. */
    public CompletableFuture<Optional<UpdateInfo>> checkAutomatically() {
        if (!preferences.automaticallyCheckForUpdates()) return CompletableFuture.completedFuture(Optional.empty());
        return check();
    }

    private CompletableFuture<Optional<UpdateInfo>> check() {
        preferences.recordAttemptedCheck(Instant.now(clock));
        return releases.latestStableRelease().thenApply(release -> {
            preferences.recordSuccessfulCheck(Instant.now(clock));
            if (!VersionComparator.isNewer(release.version(), currentVersion)) return Optional.empty();
            return Optional.of(new UpdateInfo(currentVersion, release.version(), release.name(), release.notes(),
                    release.pageUrl(), release.installerUrl(), release.installerFilename(), release.publishedAt(),
                    release.installerSize(), release.installerSha256()));
        });
    }

    public boolean markUpdateNotificationShown() {
        return preferences.markUpdateNotificationShown();
    }

    public boolean updateNotificationShownThisSession() {
        return preferences.updateNotificationShownThisSession();
    }
}
