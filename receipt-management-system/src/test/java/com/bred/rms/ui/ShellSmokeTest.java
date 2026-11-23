package com.bred.rms.ui;

import com.bred.rms.config.AppConfig;
import com.bred.rms.models.Role;
import com.bred.rms.security.Module;
import com.bred.rms.ui.auth.LoginPanel;
import com.bred.rms.ui.auth.SetupPanel;
import com.bred.rms.ui.theme.ThemeManager;
import javax.swing.JComboBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellSmokeTest {
    @Test
    void loginAndSetupPanelsBuildHeadless() {
        assertNotNull(new LoginPanel((email, password, done) -> { }));
        assertNotNull(new SetupPanel());
    }

    @Test
    void shellForEncoderOffersNoUsersOrActivityLog() {
        var ctx = TestContexts.withUser(Role.ENCODER);
        Shell shell = new Shell(ctx, () -> { });
        assertFalse(shell.visibleModules().contains(Module.USERS));
        assertFalse(shell.visibleModules().contains(Module.ACTIVITY_LOG));
        assertTrue(shell.visibleModules().contains(Module.RECEIPTS));
        assertDoesNotThrow(() -> shell.show(Module.SETTINGS));
        assertDoesNotThrow(() -> shell.show(Module.REPORTS));
        assertDoesNotThrow(() -> shell.show(Module.DASHBOARD));
    }

    @Test
    void shellForAdminOffersEverything() {
        Shell shell = new Shell(TestContexts.withUser(Role.ADMIN), () -> { });
        assertEquals(java.util.List.of(Module.values()), shell.visibleModules());
    }

    @Test
    void settingsOffersTheSignatureAppearance() {
        SettingsPanel settings = new SettingsPanel(TestContexts.withUser(Role.ENCODER));
        JComboBox<?> appearances = java.util.Arrays.stream(settings.getComponents())
                .filter(JComboBox.class::isInstance)
                .map(JComboBox.class::cast)
                .findFirst()
                .orElseThrow();

        assertEquals(ThemeManager.Appearance.values().length, appearances.getItemCount());
        assertEquals(ThemeManager.Appearance.BREDUCK_SIGNATURE, appearances.getItemAt(1));
        assertEquals(appearances.getPreferredSize(), appearances.getMaximumSize());
    }
}
