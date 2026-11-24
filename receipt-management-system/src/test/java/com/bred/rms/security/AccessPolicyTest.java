package com.bred.rms.security;

import com.bred.rms.models.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessPolicyTest {
    @Test
    void encoderModulesExcludeUsersAndActivityLog() {
        var mods = AccessPolicy.modulesFor(Role.ENCODER);
        assertFalse(mods.contains(Module.USERS));
        assertFalse(mods.contains(Module.ACTIVITY_LOG));
        assertTrue(mods.contains(Module.RECEIPTS) && mods.contains(Module.REPORTS) && mods.contains(Module.SETTINGS));
    }

    @Test
    void adminHasEveryModuleInMenuOrder() {
        assertEquals(java.util.List.of(Module.values()), AccessPolicy.modulesFor(Role.ADMIN));
        assertEquals(java.util.List.of(Module.values()), AccessPolicy.modulesFor(Role.CREATOR));
        assertEquals(Module.DASHBOARD, Module.values()[0]);
    }
}
