package com.gudo.rms.ui;

import com.gudo.rms.config.AppConfig;
import com.gudo.rms.models.Role;
import com.gudo.rms.security.Module;
import com.gudo.rms.ui.auth.LoginPanel;
import com.gudo.rms.ui.auth.SetupPanel;
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
}
