package com.bred.rms.ui;

import com.bred.rms.config.AppVersion;
import com.bred.rms.models.Role;
import com.bred.rms.updates.UpdateCheckException;
import com.bred.rms.updates.UpdateInfo;
import com.bred.rms.updates.UpdateMode;
import com.bred.rms.updates.UpdatePreferences;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsUpdateSectionTest {
    @Test
    void updateModeAndAutomaticCheckSettingPersistAcrossPanelInstances() throws Exception {
        Preferences node = newPreferencesNode();
        SettingsPanel[] panels = new SettingsPanel[2];
        try {
            UpdatePreferences firstPreferences = new UpdatePreferences(node);
            assertEquals(UpdateMode.ASK, firstPreferences.mode());
            assertTrue(firstPreferences.automaticallyCheckForUpdates());
            SwingUtilities.invokeAndWait(() ->
                    panels[0] = new SettingsPanel(TestContexts.withUser(Role.ENCODER), firstPreferences,
                            () -> CompletableFuture.completedFuture(Optional.empty())));

            SwingUtilities.invokeAndWait(() -> {
                radio(panels[0], "automaticUpdates").doClick();
                checkBox(panels[0], "automaticallyCheck").doClick();
            });
            assertEquals(UpdateMode.AUTOMATIC, firstPreferences.mode());
            assertFalse(firstPreferences.automaticallyCheckForUpdates());

            UpdatePreferences afterRestart = new UpdatePreferences(node);
            SwingUtilities.invokeAndWait(() ->
                    panels[1] = new SettingsPanel(TestContexts.withUser(Role.ENCODER), afterRestart,
                            () -> CompletableFuture.completedFuture(Optional.empty())));
            assertTrue(radio(panels[1], "automaticUpdates").isSelected());
            assertFalse(checkBox(panels[1], "automaticallyCheck").isSelected());

            SwingUtilities.invokeAndWait(() -> {
                radio(panels[1], "askBeforeInstalling").doClick();
                checkBox(panels[1], "automaticallyCheck").doClick();
            });
            assertEquals(UpdateMode.ASK, afterRestart.mode());
            assertTrue(afterRestart.automaticallyCheckForUpdates());
        } finally {
            for (SettingsPanel panel : panels) if (panel != null) panel.removeNotify();
            node.removeNode();
            Preferences.userRoot().flush();
        }
    }

    @Test
    void manualCheckRunsAsynchronouslyAndDisplaysNoUpdateOrAvailableVersion() throws Exception {
        Preferences node = newPreferencesNode();
        SettingsPanel[] panel = new SettingsPanel[1];
        CompletableFuture<Optional<UpdateInfo>> pending = new CompletableFuture<>();
        AtomicInteger checks = new AtomicInteger();
        CountDownLatch checkStarted = new CountDownLatch(1);
        try {
            SwingUtilities.invokeAndWait(() -> panel[0] = new SettingsPanel(
                    TestContexts.withUser(Role.ENCODER), new UpdatePreferences(node), () -> {
                        checks.incrementAndGet();
                        checkStarted.countDown();
                        return pending;
                    }));

            SwingUtilities.invokeAndWait(() -> {
                button(panel[0], "checkForUpdates").doClick();
                assertFalse(button(panel[0], "checkForUpdates").isEnabled());
                assertEquals("Checking for updates…", label(panel[0], "updateStatus").getText());
            });
            assertTrue(checkStarted.await(2, TimeUnit.SECONDS));
            assertEquals(1, checks.get());
            CountDownLatch noUpdateShown = watchStatus(panel[0],
                    "You're up to date (version " + AppVersion.current() + ").");
            pending.complete(Optional.empty());
            assertTrue(noUpdateShown.await(2, TimeUnit.SECONDS));
            flushEdt();
            assertTrue(button(panel[0], "checkForUpdates").isEnabled());
            assertEquals("You're up to date (version " + AppVersion.current() + ").",
                    label(panel[0], "updateStatus").getText());

            CompletableFuture<Optional<UpdateInfo>> available = CompletableFuture.completedFuture(
                    Optional.of(new UpdateInfo("1.0.0", "1.1.0", "BRED 1.1.0", "Notes",
                            URI.create("https://github.com/Clarkisbred/BRED-Business-Receipt-Encoding-and-Documentation-System-System/releases/tag/v1.1.0"),
                            URI.create("https://github.com/Clarkisbred/BRED-Business-Receipt-Encoding-and-Documentation-System-System/releases/download/v1.1.0/BRED-Setup-1.1.0.msi"),
                            "BRED-Setup-1.1.0.msi", Optional.of(Instant.parse("2026-11-25T10:00:00Z")))));
            SwingUtilities.invokeAndWait(() -> {
                panel[0].removeNotify();
                panel[0] = new SettingsPanel(TestContexts.withUser(Role.ENCODER),
                        new UpdatePreferences(node), () -> available);
            });
            CountDownLatch updateShown = watchStatus(panel[0], "Version 1.1.0 is available.");
            SwingUtilities.invokeAndWait(() -> button(panel[0], "checkForUpdates").doClick());
            assertTrue(updateShown.await(2, TimeUnit.SECONDS));
            flushEdt();
            assertEquals("Version 1.1.0 is available.", label(panel[0], "updateStatus").getText());
        } finally {
            if (panel[0] != null) panel[0].removeNotify();
            node.removeNode();
            Preferences.userRoot().flush();
        }
    }

    @Test
    void manualCheckDisplaysNetworkErrorsWithoutBlockingSettings() throws Exception {
        Preferences node = newPreferencesNode();
        SettingsPanel[] panel = new SettingsPanel[1];
        CompletableFuture<Optional<UpdateInfo>> pending = new CompletableFuture<>();
        CountDownLatch checkStarted = new CountDownLatch(1);
        try {
            SwingUtilities.invokeAndWait(() -> panel[0] = new SettingsPanel(
                    TestContexts.withUser(Role.ENCODER), new UpdatePreferences(node), () -> {
                        checkStarted.countDown();
                        return pending;
                    }));
            CountDownLatch errorShown = watchStatus(panel[0], "Could not reach GitHub to check for updates.");
            SwingUtilities.invokeAndWait(() -> button(panel[0], "checkForUpdates").doClick());
            assertFalse(button(panel[0], "checkForUpdates").isEnabled());
            assertTrue(checkStarted.await(2, TimeUnit.SECONDS));
            pending.completeExceptionally(new UpdateCheckException("Could not reach GitHub to check for updates."));
            assertTrue(errorShown.await(2, TimeUnit.SECONDS));
            flushEdt();

            assertTrue(button(panel[0], "checkForUpdates").isEnabled());
            assertEquals("Could not reach GitHub to check for updates.", label(panel[0], "updateStatus").getText());
        } finally {
            if (panel[0] != null) panel[0].removeNotify();
            node.removeNode();
            Preferences.userRoot().flush();
        }
    }

    private static Preferences newPreferencesNode() throws Exception {
        return Preferences.userRoot().node("com/bred/rms/test-update-settings/" + UUID.randomUUID());
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static CountDownLatch watchStatus(SettingsPanel panel, String expected) {
        CountDownLatch changed = new CountDownLatch(1);
        JLabel status = label(panel, "updateStatus");
        PropertyChangeListener listener = event -> {
            if (expected.equals(event.getNewValue())) changed.countDown();
        };
        status.addPropertyChangeListener("text", listener);
        return changed;
    }

    private static JButton button(SettingsPanel panel, String name) {
        return field(panel, name, JButton.class);
    }

    private static JCheckBox checkBox(SettingsPanel panel, String name) {
        return field(panel, name, JCheckBox.class);
    }

    private static JRadioButton radio(SettingsPanel panel, String name) {
        return field(panel, name, JRadioButton.class);
    }

    private static JLabel label(SettingsPanel panel, String name) {
        return field(panel, name, JLabel.class);
    }

    private static <T> T field(SettingsPanel panel, String name, Class<T> type) {
        try {
            Field field = SettingsPanel.class.getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(panel));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not read " + name, e);
        }
    }
}
