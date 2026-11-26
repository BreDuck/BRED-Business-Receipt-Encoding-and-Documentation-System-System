package com.bred.rms.updates;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateInfoTest {
    @Test
    void rejectsReleaseVersionsThatAreNotNewerThanInstalledVersion() {
        assertThrows(IllegalArgumentException.class, () -> update("1.1.0", "v1.1.0"));
        assertThrows(IllegalArgumentException.class, () -> update("1.1.0", "1.0.9"));
    }

    private static UpdateInfo update(String currentVersion, String latestVersion) {
        return new UpdateInfo(currentVersion, latestVersion, "BRED release", "", URI.create("https://github.com"),
                URI.create("https://github.com"), "BRED-Setup-" + latestVersion + ".msi", Optional.empty());
    }
}
