package com.bred.rms.updates;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.net.URI;
import java.util.UUID;
import java.net.http.HttpClient;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdatePreferencesTest {
    @Test
    void storesUserPreferencesAndCheckTimesAcrossInstances() throws Exception {
        Preferences node = Preferences.userRoot().node("com/bred/rms/test-updates/" + UUID.randomUUID());
        try {
            UpdatePreferences first = new UpdatePreferences(node);
            assertEquals(UpdateMode.ASK, first.mode());
            assertTrue(first.automaticallyCheckForUpdates());
            assertNull(first.lastSuccessfulCheck());
            assertNull(first.lastAttemptedCheck());

            Instant attempted = Instant.parse("2026-11-26T01:00:00Z");
            Instant successful = Instant.parse("2026-11-26T01:01:00Z");
            first.setMode(UpdateMode.AUTOMATIC);
            first.setAutomaticallyCheckForUpdates(false);
            first.recordAttemptedCheck(attempted);
            first.recordSuccessfulCheck(successful);

            UpdatePreferences afterRestart = new UpdatePreferences(node);
            assertEquals(UpdateMode.AUTOMATIC, afterRestart.mode());
            assertFalse(afterRestart.automaticallyCheckForUpdates());
            assertEquals(attempted, afterRestart.lastAttemptedCheck());
            assertEquals(successful, afterRestart.lastSuccessfulCheck());
        } finally {
            node.removeNode();
            Preferences.userRoot().flush();
        }
    }

    @Test
    void notificationShownStateIsLimitedToOnePreferencesInstance() throws Exception {
        Preferences node = Preferences.userRoot().node("com/bred/rms/test-updates/" + UUID.randomUUID());
        try {
            UpdatePreferences currentSession = new UpdatePreferences(node);
            assertTrue(currentSession.markUpdateNotificationShown());
            assertFalse(currentSession.markUpdateNotificationShown());
            assertTrue(currentSession.updateNotificationShownThisSession());
            assertFalse(new UpdatePreferences(node).updateNotificationShownThisSession());
        } finally {
            node.removeNode();
            Preferences.userRoot().flush();
        }
    }

    @Test
    void disabledAutomaticChecksDoNotMakeNetworkRequests() throws Exception {
        Preferences node = Preferences.userRoot().node("com/bred/rms/test-updates/" + UUID.randomUUID());
        try {
            UpdatePreferences preferences = new UpdatePreferences(node);
            preferences.setAutomaticallyCheckForUpdates(false);
            GitHubReleaseClient client = new GitHubReleaseClient(HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:1/latest"));

            assertTrue(new UpdateChecker("1.0.0", client, preferences).checkAutomatically().join().isEmpty());
            assertNull(preferences.lastAttemptedCheck());
        } finally {
            node.removeNode();
            Preferences.userRoot().flush();
        }
    }
}
