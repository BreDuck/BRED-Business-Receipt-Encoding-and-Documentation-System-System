package com.bred.rms.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppVersionTest {
    @Test
    void prefersJpackageVersionForInstalledBuilds() {
        String original = System.getProperty("jpackage.app-version");
        try {
            System.setProperty("jpackage.app-version", "1.0.5");
            assertEquals("1.0.5", AppVersion.current());
        } finally {
            if (original == null) System.clearProperty("jpackage.app-version");
            else System.setProperty("jpackage.app-version", original);
        }
    }
}
