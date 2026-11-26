package com.bred.rms.updates;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Verified metadata for a stable GitHub release newer than the installed BRED version. */
public record UpdateInfo(String currentVersion, String latestVersion, String releaseName, String releaseNotes,
                         URI releasePageUrl, URI installerDownloadUrl, String installerFilename,
                         Optional<Instant> publishedAt, OptionalLong installerSize,
                         Optional<String> installerSha256) {
    public UpdateInfo(String currentVersion, String latestVersion, String releaseName, String releaseNotes,
                      URI releasePageUrl, URI installerDownloadUrl, String installerFilename,
                      Optional<Instant> publishedAt) {
        this(currentVersion, latestVersion, releaseName, releaseNotes, releasePageUrl, installerDownloadUrl,
                installerFilename, publishedAt, OptionalLong.empty(), Optional.empty());
    }

    public UpdateInfo {
        currentVersion = requireText(currentVersion, "currentVersion");
        latestVersion = requireText(latestVersion, "latestVersion");
        releaseName = requireText(releaseName, "releaseName");
        releaseNotes = Objects.requireNonNull(releaseNotes, "releaseNotes");
        releasePageUrl = Objects.requireNonNull(releasePageUrl, "releasePageUrl");
        installerDownloadUrl = Objects.requireNonNull(installerDownloadUrl, "installerDownloadUrl");
        installerFilename = requireText(installerFilename, "installerFilename");
        publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
        installerSize = Objects.requireNonNull(installerSize, "installerSize");
        installerSha256 = Objects.requireNonNull(installerSha256, "installerSha256");
        if (installerSize.isPresent() && installerSize.getAsLong() < 0) {
            throw new IllegalArgumentException("installerSize");
        }
        installerSha256.ifPresent(value -> {
            if (!value.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("installerSha256");
        });
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field);
        return value;
    }
}
