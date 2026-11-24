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
        assertFalse(mods.contains(Module.COMPANIES));
        assertFalse(mods.contains(Module.HISTORY));
        assertTrue(mods.contains(Module.MESSAGES));
        assertTrue(mods.contains(Module.RECEIPTS) && mods.contains(Module.REPORTS) && mods.contains(Module.SETTINGS));
    }

    @Test
    void adminHasEveryModuleInMenuOrder() {
        assertFalse(AccessPolicy.modulesFor(Role.ADMIN).contains(Module.COMPANIES));
        assertFalse(AccessPolicy.modulesFor(Role.ADMIN).contains(Module.HISTORY));
        assertEquals(java.util.List.of(Module.values()), AccessPolicy.modulesFor(Role.CREATOR));
        assertTrue(AccessPolicy.modulesFor(Role.CREATOR).contains(Module.COMPANIES));
        assertTrue(AccessPolicy.modulesFor(Role.CREATOR).contains(Module.HISTORY));
        assertEquals(Module.DASHBOARD, Module.values()[0]);
    }
}
